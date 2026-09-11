package com.naury.trubbi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naury.trubbi.data.JourneyShareRequest
import com.naury.trubbi.data.SharedPlaceParser
import com.naury.trubbi.data.auth.AuthRepository
import com.naury.trubbi.data.repo.JourneyRepository
import com.naury.trubbi.data.settings.SettingsRepository
import com.naury.trubbi.data.settings.ThemeMode
import com.naury.trubbi.domain.tracking.JourneyTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 시작 화면에서 무엇이 앱 진입을 막고 있는지. */
enum class AuthGate {
    /** 로그인 전 — 로그인 화면으로. */
    SIGNED_OUT,

    /** 이메일 가입 후 인증 대기 — 인증 안내 화면으로. */
    NEEDS_VERIFICATION,

    /** 통과 — 본 화면으로. */
    READY,
}

/**
 * 앱 전체에 걸치는 상태를 모아 두는 뷰모델.
 *
 * 화면 하나에 속하지 않는 것들만 여기 있다 — 시작 게이트, 테마, 딥링크 처리, 프로필 동기화.
 * 화면별 상태는 각자의 뷰모델에 두고, 이쪽으로 끌어오지 않는다.
 *
 * @property settingsRepo 테마·프로필·첫 실행 플래그
 * @property journeyRepo 진행 중 여정 확인과 촬영 귀속 예약
 * @param tracker 앱이 다시 뜰 때 추적 서비스를 되살리는 데만 쓴다
 * @property shareRequest 외부에서 공유된 장소를 홈으로 넘기는 통로
 * @property authRepo 로그인·인증 상태
 * @property controlSignal 앱이 앞에 있는지를 플로팅 쪽에 알려 주는 신호
 * @property ambient 자동 테마에 쓰는 날씨 신호
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val journeyRepo: JourneyRepository,
    tracker: JourneyTracker,
    private val shareRequest: JourneyShareRequest,
    private val authRepo: AuthRepository,
    private val controlSignal: com.naury.trubbi.data.JourneyControlSignal,
    private val ambient: com.naury.trubbi.data.AmbientSignal,
) : ViewModel() {

    /** 자동 테마가 참고하는 최신 날씨. 아직 모르면 null. */
    val ambientWeather = ambient.weather
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * 앱이 보이는지 여부를 알린다. 앱과 플로팅이 동시에 말하거나 떨리지 않게 하는 신호다.
     *
     * @param on 앱이 앞에 나와 있으면 true
     */
    fun setAppForeground(on: Boolean) = controlSignal.setForeground(on)

    init {
        // Firebase 계정의 이름/사진을 로컬 프로필로 동기화 → 기기·로그인이 바뀌어도 일관.
        viewModelScope.launch {
            authRepo.currentUser.collect { u ->
                if (u == null) return@collect
                val cur = settingsRepo.profile.firstOrNull()
                u.displayName?.trim()?.takeIf { it.isNotBlank() && it != cur?.name }?.let { settingsRepo.setUserName(it) }
                u.photoUrl?.takeIf { it.isNotBlank() && it != cur?.photoUri }?.let { settingsRepo.setProfilePhoto(it) }
            }
        }
    }

    /** 첫 실행 환영 화면을 봤는지. 읽는 동안은 null 이고, 그 사이는 스플래시가 덮는다. */
    val welcomeSeen = settingsRepo.welcomeSeen
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    fun markWelcomeSeen() = viewModelScope.launch { settingsRepo.setWelcomeSeen() }

    /** 빠른 실행 플로팅 버블 사용 여부 — 앱 시작 시 켜져 있으면 서비스를 다시 띄운다(재부팅 후 복구). */
    val floatingLauncher = settingsRepo.floatingLauncher
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 빠른 기록 알림(알림창 액션) 사용 여부 — 앱 시작 시 켜져 있으면 다시 게시. */
    val quickNotification = settingsRepo.quickNotification
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 약관에 동의했는지. 읽는 동안은 null. 로그인 뒤 동의 화면을 띄울지 결정한다. */
    val termsAgreed = settingsRepo.termsAgreed
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * 시작 시 인증 게이트.
     *
     * FirebaseAuth 가 세션을 기기에 캐시하므로 최초 로그인 이후에는 오프라인에서도 유효하다.
     */
    val authGate = authRepo.currentUser
        .map { gateOf(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, gateOf(authRepo.currentUserNow()))

    private fun gateOf(user: com.naury.trubbi.data.auth.TrubbiUser?): AuthGate = when {
        user == null -> AuthGate.SIGNED_OUT
        authRepo.requiresVerification(user) -> AuthGate.NEEDS_VERIFICATION
        else -> AuthGate.READY
    }

    /**
     * 다른 앱에서 공유해 온 텍스트(지도 앱의 장소 공유 등)를 정차지 후보로 바꿔 홈에 넘긴다.
     *
     * @param text 공유된 원문. 장소를 못 알아보면 아무 일도 하지 않는다
     */
    fun offerSharedText(text: String?) {
        SharedPlaceParser.parse(text)?.let { shareRequest.place.value = it }
    }

    /** 알림의 촬영 버튼용 — 다음에 저장되는 기록 한 건을 진행 중 여정에 자동으로 붙인다. */
    fun armJourneyCapture() = journeyRepo.armJourneyCapture()

    /** 진행 중 여정 id. 알림에서 여정 상세로 바로 열 때 쓴다. */
    suspend fun activeJourneyId(): String? = journeyRepo.activeJourneyOnce()?.journey?.journeyId
    val themeMode = settingsRepo.settings
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    /** 저장된 프로필. 읽는 동안은 null 이라 첫 실행 안내를 띄울지 판단을 미룰 수 있다. */
    val profile = settingsRepo.profile
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // 여정이 진행 중인데 앱이 다시 뜬 경우(프로세스 종료·재부팅) 추적 서비스를 되살린다.
        // 이게 없으면 그 사이 이동 경로가 통째로 비어 나중에 대기 시간을 증명할 수 없다.
        viewModelScope.launch {
            journeyRepo.activeJourneyOnce()?.let { tracker.start(it.journey.journeyId) }
        }
    }
}

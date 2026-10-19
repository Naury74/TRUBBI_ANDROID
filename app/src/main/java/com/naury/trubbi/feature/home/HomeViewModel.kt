package com.naury.trubbi.feature.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naury.trubbi.R
import com.naury.trubbi.core.file.MediaStorage
import com.naury.trubbi.core.ui.StringProvider
import com.naury.trubbi.core.weather.Weather
import com.naury.trubbi.core.weather.WeatherRepository
import com.naury.trubbi.data.AlbumEntryRequest
import com.naury.trubbi.data.AmbientSignal
import com.naury.trubbi.data.auth.AuthRepository
import com.naury.trubbi.data.settings.SettingsRepository
import com.naury.trubbi.data.settings.UserProfile
import com.naury.trubbi.domain.journey.JourneyAmbient
import com.naury.trubbi.domain.journey.JourneyFlowController
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 홈 화면의 진입점.
 *
 * 홈 한 화면이 여정 진행과 기록 목록을 함께 다루므로, 두 흐름은 각각
 * [JourneyFlowController] 와 [HomeLibraryController] 가 맡는다. 이 뷰모델은 그 둘을
 * 화면 수명에 맞춰 만들고, 어느 쪽에도 속하지 않는 계정·날씨·인사말만 직접 들고 있다.
 *
 * @property strings 인사말·안내 문구의 다국어 처리
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val mediaStorage: MediaStorage,
    private val entryRequest: AlbumEntryRequest,
    private val weatherRepo: WeatherRepository,
    private val ambient: AmbientSignal,
    private val strings: StringProvider,
    authRepo: AuthRepository,
    journeyFactory: JourneyFlowController.Factory,
    libraryFactory: HomeLibraryController.Factory,
) : ViewModel() {

    /** 진행 중인 여정과 단계 전환. */
    val journey = journeyFactory.create(viewModelScope) {
        JourneyAmbient(profile.value.name.takeIf { it.isNotBlank() }, weather.value)
    }

    /** 기록 목록, 필터, 다중 선택, 공유. */
    val library = libraryFactory.create(viewModelScope)

    /** 홈 목록의 다중 선택 상태 — 화면이 자주 읽어 바로 꺼내 둔다. */
    val selection get() = library.selection

    // ── 계정 ──────────────────────────────────────────────────────────────────

    /** 로그인 여부 — 홈의 로그인·백업 입구를 켜고 끈다. */
    val signedIn = authRepo.currentUser
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_KEEP_MS), authRepo.isLoggedIn())

    /** 프로필 팝업이 보여 줄 계정 정보. */
    val account = authRepo.currentUser
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_KEEP_MS), null)

    val profile = settingsRepo.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_KEEP_MS), UserProfile())

    /** 홈 용도 칩에 쓰는 사용자 용도 목록. */
    val purposes = settingsRepo.purposes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_KEEP_MS), emptyList())

    /** 홈에서 프로필 사진을 바꾸고 저장한다. 복사에 실패하면 기존 사진을 그대로 둔다. */
    fun changeProfilePhoto(uri: Uri) {
        viewModelScope.launch {
            val saved = runCatching { mediaStorage.saveProfilePhoto(uri) }.getOrNull() ?: return@launch
            settingsRepo.setProfilePhoto(saved)
        }
    }

    // ── 날씨 ──────────────────────────────────────────────────────────────────

    /** 홈 히어로에 쓰는 현재 날씨. 측위와 조회가 모두 되기 전까지 null. */
    val weather = MutableStateFlow<Weather?>(null)

    /** 날씨 한 번 갱신. 위치 권한을 막 받은 뒤 다시 시도할 때도 쓴다. */
    fun refreshWeather() = viewModelScope.launch {
        runCatching { weatherRepo.current() }.getOrNull()?.let {
            weather.value = it
            settingsRepo.saveWeather(it)
        }
    }

    // ── 탭 바가 맡기는 일 ───────────────────────────────────────────────────────

    /**
     * 하단 탭 바의 "+" 메뉴가 홈 화면에 맡긴 일. 홈 탭이 받아 처리하고 비운다.
     *
     * 경로 화면과 여정 시작 오버레이가 홈 화면 안에 있어서, 다른 탭에서 고른 동작도 홈으로 넘겨 처리한다.
     */
    val pendingAction = MutableStateFlow<HomeAction?>(null)

    // ── 인사말 ────────────────────────────────────────────────────────────────

    /** 홈에 한 번 뜨는 인사 배너. */
    val greeting = MutableStateFlow<String?>(null)

    fun clearGreeting() {
        greeting.value = null
    }

    // ── 앨범 탭 진입 요청 ─────────────────────────────────────────────────────

    /** 앨범 탭이 열릴 때 미리 걸어 둘 종류 필터. */
    fun requestAlbumFilter(name: String) {
        entryRequest.requestedFilter.value = name
    }

    /** 앨범 탭이 열릴 때 미리 걸어 둘 용도 필터. */
    fun requestAlbumPurpose(purpose: String) {
        entryRequest.requestedPurpose.value = purpose
    }

    /** 앨범 탭이 열릴 때 검색창을 펼치도록 요청. */
    fun requestAlbumSearch() {
        entryRequest.openSearch.value = true
    }

    // ── 초기화 ────────────────────────────────────────────────────────────────

    init {
        // 날씨 조건을 앱 전역 신호로 흘려보낸다(배경 애니메이션이 구독한다).
        viewModelScope.launch { weather.collect { ambient.weather.value = it?.condition } }
        // 새 측위가 끝나기 전까지는 마지막으로 저장해 둔 날씨를 보여 준다.
        viewModelScope.launch {
            if (weather.value != null) return@launch
            settingsRepo.lastWeather.firstOrNull()?.let { if (weather.value == null) weather.value = it }
        }
        viewModelScope.launch {
            while (isActive) {
                refreshWeather().join()
                delay(WEATHER_REFRESH_MS)
            }
        }
        viewModelScope.launch { showGreetingIfSignedIn(authRepo) }
    }

    /** 로그인 상태로 홈에 진입하면 인사말을 한 번 표시한다. 이름을 저장해 두었으면 호칭을 붙인다. */
    private suspend fun showGreetingIfSignedIn(authRepo: AuthRepository) {
        if (!authRepo.isLoggedIn()) return
        val name = settingsRepo.profile.firstOrNull()?.name?.takeIf { it.isNotBlank() }
        val part = strings[timeOfDayGreetingRes()]
        greeting.value = if (name != null) {
            strings[R.string.greeting_with_name, name, part]
        } else {
            strings[R.string.greeting_plain, part]
        }
    }

    private fun timeOfDayGreetingRes(): Int = when (LocalTime.now().hour) {
        in 5..11 -> R.string.greeting_morning
        in 12..17 -> R.string.greeting_afternoon
        else -> R.string.greeting_evening
    }

    private companion object {
        const val WEATHER_REFRESH_MS = 30 * 60 * 1000L
        const val SUBSCRIBE_KEEP_MS = 5_000L
    }
}

/** 탭 바에서 홈 화면으로 넘기는 동작. */
enum class HomeAction {
    /** 경로 화면을 연다. 여정 중이면 정차지 추가로 연다. */
    PLAN_ROUTE,

    /** 갤러리에서 사진을 골라 기록으로 들여온다. */
    IMPORT_PHOTO,
}

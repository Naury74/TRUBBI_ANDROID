package com.naury.trubbi.feature.ads

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.naury.trubbi.BuildConfig
import com.naury.trubbi.core.log.TrubbiLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 광고 동의와 SDK 시작을 맡는다.
 *
 * 개인정보 동의가 필요한 지역(EEA·영국 등)에서는 Google UMP 동의 창을 먼저 띄우고, 광고를 요청해도 된다고 확인된
 * 뒤에만 SDK 를 시작한다. 광고 단위 ID 가 없는 빌드(release 에 실제 ID 를 넣지 않은 경우)에서는 아무것도 하지 않는다.
 *
 * 어디에 광고를 띄울지는 화면 쪽 [LocalAdsAllowed] 가 정한다(여정 중에는 띄우지 않는다).
 */
@Singleton
class AdsController @Inject constructor(@ApplicationContext private val context: Context) {

    /** 네이티브(기록 카드 모양) 광고 단위. 비어 있으면 그 광고는 쓰지 않는다. */
    val nativeUnit: String = BuildConfig.ADMOB_NATIVE_UNIT

    /** 목록 끝 배너 광고 단위. */
    val bannerUnit: String = BuildConfig.ADMOB_BANNER_UNIT

    private val enabled = nativeUnit.isNotBlank() || bannerUnit.isNotBlank()
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _ready = MutableStateFlow(false)

    /** 동의를 마치고 SDK 가 시작돼 광고를 요청해도 되는지. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)

    /** 사용자가 동의를 다시 고를 수 있는 "광고 개인정보 설정" 메뉴를 보여야 하는 지역인지. */
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    /**
     * 앱을 열 때마다 부른다. 동의 상태를 새로 확인하고, 필요하면 동의 창을 띄운 뒤 SDK 를 시작한다.
     * 지난번에 이미 동의했으면 확인을 기다리지 않고 바로 시작한다.
     */
    fun gatherConsent(activity: Activity) {
        if (!enabled) return
        val info = UserMessagingPlatform.getConsentInformation(activity)
        info.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    if (error != null) TrubbiLog.warn(TAG, "동의 창 실패: ${error.errorCode}")
                    _privacyOptionsRequired.value =
                        info.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                    if (info.canRequestAds()) start()
                }
            },
            { error -> TrubbiLog.warn(TAG, "동의 상태 확인 실패: ${error.errorCode}") },
        )
        if (info.canRequestAds()) start()
    }

    /** 설정의 "광고 개인정보 설정" — 동의를 다시 고르게 한다. */
    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) TrubbiLog.warn(TAG, "개인정보 설정 창 실패: ${error.errorCode}")
        }
    }

    private fun start() {
        if (!started.compareAndSet(false, true)) return
        // 초기화는 메인 스레드를 막지 않도록 백그라운드에서 한다.
        scope.launch {
            MobileAds.initialize(context) { _ready.value = true }
        }
    }

    private companion object {
        const val TAG = "Ads"
    }
}

/**
 * 지금 화면에 광고를 띄워도 되는지. 동의·SDK 준비가 끝났고 여정이 진행 중이 아닐 때만 true 다.
 *
 * 운전 중에는 화면을 보는 시간을 늘리는 것을 두지 않는다(안전, Google Play 운전자 주의 분산 정책).
 */
val LocalAdsAllowed = staticCompositionLocalOf { false }

/** 광고 단위 ID 를 꺼내는 통로. 앱 밖(미리보기)에서는 null. */
val LocalAdsController = staticCompositionLocalOf<AdsController?> { null }

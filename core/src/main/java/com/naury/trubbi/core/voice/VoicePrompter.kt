package com.naury.trubbi.core.voice

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 여정 음성 안내. 합성은 [CloudTts] 하나만 쓴다.
 *
 * 기기 내장 TTS 로 폴백하지 않는다. 두 엔진을 함께 쓰면 안내마다 음색이 달라지고,
 * 늦게 도착한 클라우드 응답이 기기 음성과 겹쳐 재생된다.
 * 오프라인이거나 합성이 실패하면 무음으로 끝나며, 같은 문구가 화면에도 표시된다.
 *
 * @property context 네트워크 상태 확인용 애플리케이션 컨텍스트
 * @property cloudTts 실제 합성·재생을 맡는 클라이언트
 * @property settings 사용자가 클라우드 음성을 껐는지 확인
 * @property appScope [speak] 의 fire-and-forget 재생을 태우는 앱 수명 스코프
 */
@Singleton
class VoicePrompter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cloudTts: CloudTts,
    private val preferences: VoicePreferences,
    private val appScope: CoroutineScope,
) {
    // 세션 토큰. [stop] 또는 새 발화 시 증가하며, 진행 중이던 요청은 이 값으로 무효를 판단한다.
    @Volatile private var session = 0

    /** 재생 완료를 기다리지 않고 발화를 시작한다. */
    fun speak(text: String) {
        appScope.launch { speakAndWait(text) }
    }

    /**
     * 발화가 끝날 때까지 suspend 한다. 마이크를 여는 호출부는 반환 이후에 시작해야 자기 음성이
     * 인식에 섞이지 않는다.
     *
     * @param text 읽을 안내문. 화면 표시용 기호는 [sanitizeForSpeech] 가 제거한다
     */
    suspend fun speakAndWait(text: String) {
        val clean = sanitizeForSpeech(text)
        if (clean.isBlank()) return
        ++session
        if (!useCloud()) return // 오프라인/설정 OFF → 무음 (기기 TTS 폴백 없음)
        runCatching { cloudTts.speak(clean) } // 실패해도 폴백하지 않고 조용히 끝낸다
    }

    /**
     * 화면 표시용 기호를 발화용 문자열에서 제거·치환한다.
     *
     * 자리표시자 `○○` 는 TTS 가 "원원" 으로 읽으므로 낱말로 바꾼다. 화면 문구는 변경하지 않는다.
     */
    private fun sanitizeForSpeech(t: String): String =
        t.replace("○○", "어디").replace("△△", "거기").replace("□□", "")
            .replace(Regex("[○●◯◦△▲▽□■◆◇◈※·•‘’“”\"']"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** 진행 중인 안내를 중단한다. */
    fun stop() {
        session++
        cloudTts.stop()
    }

    private suspend fun useCloud(): Boolean {
        if (!isOnline()) return false
        return runCatching { preferences.useCloudVoice() }.getOrDefault(true)
    }

    private fun isOnline(): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(false)
}

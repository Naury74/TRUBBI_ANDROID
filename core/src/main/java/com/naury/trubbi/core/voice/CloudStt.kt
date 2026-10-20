package com.naury.trubbi.core.voice

import android.content.Context
import android.util.Base64
import com.google.firebase.functions.FirebaseFunctions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 클라우드 받아쓰기 클라이언트 — `transcribeSpeech` Cloud Function 을 통해 Google Cloud STT 를 부른다.
 *
 * 녹음을 마친 뒤 한 번에 업로드하는 방식이라 발화 중 부분 인식 결과는 제공하지 않는다.
 * 실시간 자막이 필요한 구간은 [SpeechInputState] 의 기기 내 경로가 담당한다.
 *
 * 오프라인·타임아웃·응답 오류에서 예외를 던지지 않고 빈 리스트를 반환한다.
 *
 * @property context 네트워크 상태 확인용 애플리케이션 컨텍스트 (Hilt 주입)
 */
@Singleton
class CloudStt @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val functions by lazy { FirebaseFunctions.getInstance() }

    /** 인터넷 연결이 있는지. 인식 경로를 고르는 판단에 쓰인다. */
    fun isOnline(): Boolean = context.hasValidatedNetwork()

    /**
     * 녹음 바이트를 텍스트 후보로 바꾼다.
     *
     * @param pcm 모노 LINEAR16 PCM 바이트. 헤더 없는 원시 샘플이어야 한다
     * @param sampleRate 녹음 샘플레이트(Hz). Cloud STT 요청에 그대로 전달된다
     * @param hints 지금 나올 법한 낱말. 서버가 phrase hint 로 가중치를 준다(예전 서버는 무시한다)
     * @return 신뢰도 순 후보 목록(첫 번째가 최선). 오프라인·타임아웃·실패면 빈 리스트
     */
    suspend fun transcribe(pcm: ByteArray, sampleRate: Int = 16000, hints: List<String> = emptyList()): List<String> {
        if (pcm.isEmpty() || !isOnline()) return emptyList()
        return runCatching {
            val b64 = Base64.encodeToString(pcm, Base64.NO_WRAP)
            val res = withTimeoutOrNull(10000) {
                functions.getHttpsCallable("transcribeSpeech")
                    .call(mapOf("audio" to b64, "sampleRate" to sampleRate, "hints" to hints.take(MAX_HINTS)))
                    .await()
            } ?: return emptyList()

            @Suppress("UNCHECKED_CAST")
            val data = res.data as? Map<String, Any?> ?: return emptyList()
            (data["alternatives"] as? List<*>)
                ?.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotBlank) }
                ?.distinct()
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    private companion object {
        /** 서버가 받는 힌트 수 상한과 맞춘다. */
        const val MAX_HINTS = 60
    }
}

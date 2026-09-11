package com.naury.trubbi.core.voice

import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import com.google.firebase.functions.FirebaseFunctions
import com.naury.trubbi.core.log.logFailure
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 클라우드 음성 합성 클라이언트 — `synthesizeSpeech` Cloud Function 을 통해 Google Cloud TTS 를 부른다.
 *
 * 응답의 base64 MP3 를 `(음성 이름 + 문구)` 해시로 캐시에 저장하고 [MediaPlayer] 로 재생한다.
 * 캐시가 있으면 네트워크를 타지 않는다.
 *
 * 오프라인, 함수 미배포, API 비활성 등 어떤 실패에서도 예외를 던지지 않고 false 를 반환한다.
 *
 * @property context 캐시 디렉터리를 얻기 위한 애플리케이션 컨텍스트 (Hilt 주입)
 */
@Singleton
class CloudTts @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val functions by lazy { FirebaseFunctions.getInstance() }
    private val cacheDir: File by lazy { File(context.cacheDir, "tts").apply { mkdirs() } }

    @Volatile private var player: MediaPlayer? = null

    // 재생 세대. [stop] 또는 새 [speak] 호출 시 증가한다.
    // 합성·재생 도중 값이 바뀌면 해당 요청은 중단된다.
    @Volatile private var gen = 0

    /**
     * 합성(또는 캐시 재사용) 후 재생하고, 재생이 끝날 때까지 기다린다.
     *
     * @param text 읽을 문구. 화면용 기호는 [VoicePrompter] 에서 이미 걷어 낸 상태로 들어온다
     * @param voice Google Cloud TTS 음성 이름. 캐시 키에 포함된다
     * @return 끝까지 재생했으면 true. 합성 실패·중간에 [stop] 이나 다음 [speak] 로 교체되면 false
     */
    suspend fun speak(text: String, voice: String = "ko-KR-Chirp3-HD-Aoede"): Boolean {
        val mine = ++gen
        stopPlayer() // 진행 중이던 클라우드 음성 즉시 정지(겹침 방지)
        val clip = synthesize(text, voice) ?: return false
        if (gen != mine) return false // 합성 도중 중단/교체됨 → 재생하지 않음
        return playAndAwait(clip, mine)
    }

    /** 재생을 중단한다. 합성 대기 중이던 요청도 세대가 밀려 재생되지 않는다. */
    fun stop() {
        gen++
        stopPlayer()
    }

    private fun stopPlayer() {
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
    }

    private suspend fun synthesize(text: String, voice: String): File? {
        val key = md5("$voice|$text")
        val file = File(cacheDir, "$key.mp3")
        if (file.exists() && file.length() > 0) return file
        return runCatching {
            val res = withTimeoutOrNull(4500) {
                functions.getHttpsCallable("synthesizeSpeech")
                    .call(mapOf("text" to text, "voice" to voice))
                    .await()
            } ?: return null

            @Suppress("UNCHECKED_CAST")
            val data = res.data as? Map<String, Any?> ?: return null
            val b64 = data["audio"] as? String ?: return null
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            if (bytes.isEmpty()) return null
            file.writeBytes(bytes)
            file
        }.logFailure(TAG, "음성 합성 실패").getOrNull()
    }

    private suspend fun playAndAwait(file: File, mine: Int): Boolean = withContext(Dispatchers.Main) {
        if (gen != mine) return@withContext false
        withTimeoutOrNull(20000) {
            suspendCancellableCoroutine { cont ->
                val mp = MediaPlayer()
                player = mp
                fun finish(ok: Boolean) {
                    runCatching { mp.release() }
                    if (player === mp) player = null
                    if (cont.isActive) cont.resume(ok)
                }
                mp.setOnCompletionListener { finish(true) }
                mp.setOnErrorListener { _, _, _ ->
                    finish(false)
                    true
                }
                cont.invokeOnCancellation { runCatching { mp.release() } }
                runCatching {
                    mp.setDataSource(file.path)
                    mp.setOnPreparedListener {
                        // 준비 완료 시점에 이미 다른 음성으로 교체/중단됐으면 재생하지 않는다.
                        if (gen != mine) finish(false) else it.start()
                    }
                    mp.prepareAsync()
                }.onFailure { finish(false) }
            }
        } ?: false
    }

    private companion object {
        const val TAG = "CloudTts"
    }

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}

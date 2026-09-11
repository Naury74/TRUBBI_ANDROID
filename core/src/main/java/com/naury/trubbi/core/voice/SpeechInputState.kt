package com.naury.trubbi.core.voice

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 앱 안에서 도는 음성 입력 상태.
 *
 * 시스템 받아쓰기 다이얼로그 대신 인식 상태를 Compose 상태로 노출해, 화면이 직접 듣기 UI 를 그린다.
 *
 * 인식 경로는 두 가지이며 [start] 에서 결정된다.
 *  - [cloudTranscribe] 가 있고 온라인이면 [AudioRecord] 로 직접 녹음해 클라우드로 전송한다.
 *    무음이 이어지면 녹음을 자동 종료한다. 부분 인식 결과는 제공되지 않는다.
 *  - 그 외에는 기기 내 [SpeechRecognizer] 를 쓴다. 발화 중 부분 결과가 [partial] 로 들어온다.
 *
 * 두 경로 모두 `RECORD_AUDIO` 권한이 허용된 상태를 전제한다. 권한 요청은 호출부가 처리한다.
 *
 * @property context 인식기·오디오 장치·네트워크 상태 접근용
 * @property cloudTranscribe (PCM, 샘플레이트) → 후보 텍스트. null 이면 기기 인식만 쓴다
 */
@Stable
class SpeechInputState(
    private val context: Context,
    private val cloudTranscribe: (suspend (ByteArray, Int) -> List<String>)? = null,
) {
    val available: Boolean
        get() = cloudTranscribe != null || SpeechRecognizer.isRecognitionAvailable(context)

    /** 마이크가 열려 있는 동안 true. */
    var listening by mutableStateOf(false)
        private set

    /** 녹음을 마치고 클라우드 응답을 기다리는 동안 true. */
    var processing by mutableStateOf(false)
        private set

    /** 발화 중 부분 인식 결과. 기기 내 경로에서만 채워진다. */
    var partial by mutableStateOf("")
        private set

    /** 0~1 로 정규화한 입력 음량. */
    var level by mutableStateOf(0f)
        private set

    /** 차순위 인식 후보들. 화면이 대안 선택지를 띄우는 데 쓴다. */
    var alternatives by mutableStateOf<List<String>>(emptyList())
        private set

    /** 인식이 실패했거나 결과가 비었을 때 true. */
    var error by mutableStateOf(false)
        private set

    private var recognizer: SpeechRecognizer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var recording = false

    @Volatile private var audioRecord: AudioRecord? = null

    // 세션 토큰. 취소·재시작 시 증가하며, 늦게 도착한 결과는 이 값이 다르면 폐기된다.
    @Volatile private var session = 0

    // 효과음. 사인파를 직접 합성해 `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` 스트림으로 재생한다.
    /** 마이크가 열렸음을 알리는 신호음. */
    private fun earconReady() = playEarcon(listOf(440.00 to 130, 587.33 to 180))

    /** 입력을 받았음을 알리는 신호음. */
    private fun earconDone() = playEarcon(listOf(587.33 to 120, 440.00 to 180))

    /** 인식에 실패했음을 알리는 신호음. */
    private fun earconFail() = playEarcon(listOf(392.00 to 140, 261.63 to 230))

    /** `(주파수Hz, 길이ms)` 시퀀스를 합성해 한 번 재생한다. */
    private fun playEarcon(notes: List<Pair<Double, Int>>) {
        scope.launch {
            runCatching {
                val sr = 44100
                val total = notes.sumOf { it.second * sr / 1000 }
                if (total <= 0) return@launch
                val buf = ShortArray(total)
                var idx = 0
                for ((freq, ms) in notes) {
                    val n = ms * sr / 1000
                    val attack = (n * 0.12).toInt().coerceAtLeast(1)
                    val release = (n * 0.55).toInt().coerceAtLeast(1)
                    for (i in 0 until n) {
                        val env = when {
                            i < attack -> i.toFloat() / attack
                            i > n - release -> {
                                val r = (n - i).toFloat() / release
                                r * r
                            } // 둥근(부드러운) 꼬리
                            else -> 1f
                        }
                        val t = i.toDouble() / sr
                        val wave = Math.sin(2.0 * Math.PI * freq * t) + 0.18 * Math.sin(2.0 * Math.PI * 2.0 * freq * t)
                        val s = wave * env * 0.62
                        if (idx < buf.size) buf[idx++] = (s * 32767).toInt().toShort()
                    }
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            // 내비게이션 안내음 속성. 미디어 재생 중에도 함께 라우팅된다.
                            .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sr)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(buf.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(buf, 0, buf.size)
                runCatching { track.setVolume(AudioTrack.getMaxVolume()) }
                track.play()
                delay((notes.sumOf { it.second } + 160).toLong())
                runCatching {
                    track.stop()
                    track.release()
                }
            }
        }
    }

    /**
     * 듣기를 시작한다. 이미 듣는 중이면 아무 일도 하지 않는다.
     *
     * 클라우드 콜백이 있고 온라인이면 녹음 방식, 아니면 기기 인식기를 쓴다.
     *
     * @param onResult 최종 텍스트로 **한 번만** 불린다. 인식 실패 시에는 불리지 않고
     *   [error] 가 true 가 된다. 메인 스레드에서 호출할 것
     */
    fun start(onResult: (String) -> Unit) {
        if (listening) return
        releaseRecognizer()
        stopRecording()
        error = false
        alternatives = emptyList()
        partial = ""
        val s = ++session
        val cloud = cloudTranscribe
        if (cloud != null && isOnline()) {
            startCloud(onResult, cloud, s)
        } else {
            startOnDevice(onResult)
        }
    }

    /** 듣기를 중단한다. 이후 도착하는 결과는 세션 번호가 달라 폐기된다. */
    fun cancel() {
        session++
        releaseRecognizer()
        stopRecording()
        listening = false
        processing = false
        partial = ""
        level = 0f
        error = false
    }

    /** 컴포지션 종료 시 호출한다. 녹음 코루틴 스코프까지 취소한다. */
    fun release() {
        cancel()
        runCatching { scope.cancel() }
    }

    // ───────────────────────── Cloud 경로 (AudioRecord + Cloud STT) ─────────────────────────

    private val mainHandler = Handler(Looper.getMainLooper())
    private fun postMain(block: () -> Unit) = mainHandler.post(block)

    /**
     * 녹음하면서 무음·최대 길이를 동시에 감시하는 상태 기계.
     *
     * 종료 조건(무음 지속, 최대 길이, 세션 무효화)이 서로 얽혀 있어 한 함수에 모아 둔다.
     */
    @Suppress("CyclomaticComplexMethod")
    private fun startCloud(onResult: (String) -> Unit, transcribe: suspend (ByteArray, Int) -> List<String>, sess: Int) {
        recording = true
        scope.launch {
            val sr = 16000
            val minBuf = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuf <= 0) {
                recording = false
                postMain { if (sess == session) startOnDevice(onResult) }
                return@launch
            }
            val rec = runCatching {
                @Suppress("MissingPermission")
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sr,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBuf * 4, sr),
                )
            }.getOrNull()
            if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
                runCatching { rec?.release() }
                recording = false
                postMain { if (sess == session) startOnDevice(onResult) }
                return@launch
            }
            audioRecord = rec
            // 준비음 재생 후 간격을 두고 녹음을 시작한다. 준비음이 마이크에 섞이는 것을 막는다.
            earconReady()
            delay(340)
            if (sess != session) {
                cleanupRecord()
                recording = false
                return@launch
            }

            val out = ByteArrayOutputStream()
            val buf = ShortArray(minBuf)
            val bytes = ByteArray(buf.size * 2)
            val started = runCatching { rec.startRecording() }.isSuccess &&
                rec.recordingState == AudioRecord.RECORDSTATE_RECORDING
            if (!started) {
                cleanupRecord()
                recording = false
                postMain { if (sess == session) startOnDevice(onResult) }
                return@launch
            }
            // 마이크가 실제로 열린 뒤에 표시를 켜 화면과 입력 시점을 맞춘다.
            // 메인 스레드로 전환하지 않고 직접 갱신한다. Compose 스냅샷 쓰기는 스레드 안전하다.
            partial = ""
            error = false
            alternatives = emptyList()
            processing = false
            listening = true

            val startAt = SystemClock.elapsedRealtime()
            var lastVoice = 0L
            var firstVoice = 0L
            var spoke = false
            val voiceThreshold = 280.0 // 이보다 크면 '말하는 중'(조용히 말해도 잡히게 낮춤)
            val silenceMs = 1500L // 발화 뒤 이만큼 조용하면 종료(끊김 방지와 반응속도 절충)
            val preSpeechMs = 7000L // 시작 후 이만큼 무발화면 종료
            val minSpeechMs = 450L // 너무 짧은 잡음은 발화로 보지 않음
            val maxMs = 15000L
            var tick = 0
            try {
                while (recording) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    var sum = 0.0
                    for (i in 0 until n) {
                        val s = buf[i].toInt()
                        sum += (s * s).toDouble()
                        val b = i * 2
                        bytes[b] = (s and 0xff).toByte()
                        bytes[b + 1] = ((s shr 8) and 0xff).toByte()
                    }
                    out.write(bytes, 0, n * 2)
                    val rms = Math.sqrt(sum / n)
                    if (tick++ % 2 == 0) level = (rms / 4500.0).coerceIn(0.0, 1.0).toFloat()
                    val now = SystemClock.elapsedRealtime()
                    if (rms > voiceThreshold) {
                        if (!spoke) {
                            spoke = true
                            firstVoice = now
                        }
                        lastVoice = now
                    }
                    if (spoke && now - firstVoice >= minSpeechMs && now - lastVoice > silenceMs) break
                    if (!spoke && now - startAt > preSpeechMs) break
                    if (now - startAt > maxMs) break
                }
            } catch (_: Throwable) {}
            cleanupRecord()
            // 취소/재시작되었으면(다른 세션) 결과를 버린다.
            if (sess != session) return@launch
            recording = false
            level = 0f
            listening = false

            val pcm = out.toByteArray()
            if (!spoke || pcm.size < sr / 2) { // 발화 없음/너무 짧음 → 실패음 + 에러(위저드가 다시 안내).
                earconFail()
                processing = false
                error = true
                return@launch
            }
            // 입력을 받아 듣기를 멈춤 → 확인음 후 받아쓰기 진행.
            earconDone()
            processing = true
            val alts = runCatching { transcribe(pcm, sr) }.getOrDefault(emptyList())
            if (sess != session) {
                processing = false
                return@launch
            } // 받아쓰는 사이 취소됨
            if (alts.isEmpty()) { // 클라우드 받아쓰기 실패 → 기기 내로 폴백(재시도)
                processing = false
                postMain { if (sess == session) startOnDevice(onResult) }
                return@launch
            }
            alternatives = alts.take(4)
            processing = false
            val text = alts.first()
            postMain { if (sess == session) onResult(text) }
        }
    }

    private fun cleanupRecord() {
        val r = audioRecord
        runCatching { r?.stop() }
        runCatching { r?.release() }
        audioRecord = null
    }

    private fun stopRecording() {
        recording = false
        cleanupRecord()
    }

    // ───────────────────────── 기기 내 경로 (SpeechRecognizer) ─────────────────────────

    private fun startOnDevice(onResult: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            error = true
            listening = false
            return
        }
        releaseRecognizer()
        error = false
        alternatives = emptyList()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listening = true
                partial = ""
                earconReady()
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                level = 0f
            }
            override fun onError(error: Int) {
                listening = false
                partial = ""
                level = 0f
                this@SpeechInputState.error = true
                earconFail()
            }
            override fun onResults(results: Bundle?) {
                listening = false
                level = 0f
                partial = ""
                val all = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.mapNotNull { it.trim().takeIf(String::isNotBlank) }
                    ?.distinct().orEmpty()
                alternatives = all.take(4)
                val text = all.firstOrNull()
                if (!text.isNullOrBlank()) {
                    earconDone()
                    onResult(text)
                } else {
                    this@SpeechInputState.error = true
                    earconFail()
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.let { partial = it }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        runCatching {
            r.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 6)
                    putExtra("android.speech.extra.GET_AUDIO", false)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
                },
            )
            listening = true
        }.onFailure { listening = false }
    }

    private fun releaseRecognizer() {
        runCatching {
            recognizer?.cancel()
            recognizer?.destroy()
        }
        recognizer = null
    }

    private fun isOnline(): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(false)
}

/**
 * 컴포지션에 묶인 [SpeechInputState] 를 만든다. 화면이 사라지면 자동으로 정리된다.
 *
 * @param cloudTranscribe 클라우드 받아쓰기 콜백. 주면 온라인일 때 그쪽을 우선한다
 */
@Composable
fun rememberSpeechInput(
    cloudTranscribe: (suspend (ByteArray, Int) -> List<String>)? = null,
): SpeechInputState {
    val context = LocalContext.current
    val state = remember { SpeechInputState(context, cloudTranscribe) }
    DisposableEffect(Unit) { onDispose { state.release() } }
    return state
}

package com.naury.trubbi.core.voice

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
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
 * 인식률은 두 가지로 끌어올린다. 지금 화면에서 나올 법한 말(후보 장소 이름, 명령어)을 [start] 의 `hints` 로
 * 받아 인식기에 미리 알려 주고(클라우드는 phrase hint, 기기 인식은 Android 13+ biasing), 1순위 문장만이 아니라
 * 차순위 후보([alternatives])까지 남겨 호출부가 장소 검색 결과와 맞춰 보며 고르게 한다.
 *
 * @property cloudTranscribe (PCM, 샘플레이트, 힌트) → 후보 텍스트. null 이면 기기 인식만 쓴다
 */
@Stable
class SpeechInputState(
    private val context: Context,
    private val cloudTranscribe: (suspend (ByteArray, Int, List<String>) -> List<String>)? = null,
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

    /** 마지막 실패의 종류. 화면이 "다시 말해 주세요"와 "권한이 필요해요"를 구분해 안내하는 데 쓴다. */
    var failure by mutableStateOf<SpeechFailure?>(null)
        private set

    private var recognizer: SpeechRecognizer? = null
    private var hints: List<String> = emptyList()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var recording = false

    @Volatile private var audioRecord: AudioRecord? = null

    @Volatile private var effects: List<AudioEffect> = emptyList()

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
     * @param hints 지금 나올 법한 낱말(화면의 후보 이름, 명령어). 인식기가 비슷한 소리를 이쪽으로 맞춘다
     * @param onResult 최종 텍스트로 **한 번만** 불린다. 차순위 후보는 이때 [alternatives] 에 들어 있다. 인식 실패
     *   시에는 불리지 않고 [error] 가 true 가 된다. 메인 스레드에서 호출할 것
     */
    fun start(hints: List<String> = emptyList(), onResult: (String) -> Unit) {
        if (listening) return
        releaseRecognizer()
        stopRecording()
        error = false
        failure = null
        alternatives = emptyList()
        partial = ""
        val s = ++session
        this.hints = hints.map(String::trim).filter(String::isNotEmpty).distinct().take(MAX_HINTS)
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
        failure = null
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
    private fun startCloud(onResult: (String) -> Unit, transcribe: suspend (ByteArray, Int, List<String>) -> List<String>, sess: Int) {
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
            effects = attachVoiceEffects(rec.audioSessionId)
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
                failure = SpeechFailure.NO_SPEECH
                error = true
                return@launch
            }
            // 입력을 받아 듣기를 멈춤 → 확인음 후 받아쓰기 진행.
            earconDone()
            processing = true
            val alts = runCatching { transcribe(pcm, sr, hints) }.getOrDefault(emptyList())
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

    /**
     * 화물차 실내는 엔진·노면 소음이 크다. 기기가 지원하면 잡음 억제와 자동 음량을 켜 녹음 품질을 올린다.
     *
     * @return 정리할 때 함께 해제할 효과들
     */
    private fun attachVoiceEffects(sessionId: Int): List<AudioEffect> = buildList {
        if (NoiseSuppressor.isAvailable()) {
            runCatching { NoiseSuppressor.create(sessionId) }.getOrNull()?.let {
                it.enabled = true
                add(it)
            }
        }
        if (AutomaticGainControl.isAvailable()) {
            runCatching { AutomaticGainControl.create(sessionId) }.getOrNull()?.let {
                it.enabled = true
                add(it)
            }
        }
    }

    private fun cleanupRecord() {
        effects.forEach { runCatching { it.release() } }
        effects = emptyList()
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

    private fun startOnDevice(onResult: (String) -> Unit, retriesLeft: Int = 1) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            fail(SpeechFailure.UNAVAILABLE, sound = false)
            return
        }
        releaseRecognizer()
        error = false
        failure = null
        alternatives = emptyList()
        val sess = session
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (sess != session) return
                listening = true
                partial = ""
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                if (sess == session) level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                level = 0f
            }
            override fun onError(error: Int) {
                // 취소한 뒤 늦게 오는 ERROR_CLIENT 같은 것은 이미 끝난 세션의 소식이라 버린다.
                if (sess != session) return
                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY && retriesLeft > 0) {
                    // 직전 세션이 아직 정리되는 중이다. 잠깐 기다렸다 한 번만 다시 연다.
                    releaseRecognizer()
                    mainHandler.postDelayed({ if (sess == session) startOnDevice(onResult, retriesLeft - 1) }, BUSY_RETRY_MS)
                    return
                }
                fail(failureOf(error), sound = true)
            }
            override fun onResults(results: Bundle?) {
                if (sess != session) return
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
                    fail(SpeechFailure.NO_MATCH, sound = true)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                if (sess != session) return
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.let { partial = it }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        // 준비음을 먼저 울리고 끝난 뒤에 마이크를 연다. 듣는 중에 울리면 신호음이 첫 음절을 덮거나 인식된다.
        // 기다리는 동안에도 화면은 바로 '듣는 중'으로 바꿔 버튼이 두 번 눌리지 않게 한다.
        listening = true
        earconReady()
        mainHandler.postDelayed({
            if (sess != session || recognizer !== r) return@postDelayed
            runCatching { r.startListening(recognizerIntent()) }
                .onFailure { fail(SpeechFailure.UNAVAILABLE, sound = true) }
        }, EARCON_LEAD_MS)
    }

    private fun recognizerIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 6)
        putExtra("android.speech.extra.GET_AUDIO", false)
        if (hints.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(hints))
        }
        // 짧은 대답("네", "두 번째")이 대부분이라 최소 길이는 두지 않는다. 예전 5초 하한은 "네" 한마디에도
        // 5초를 붙잡아 운전 중 답답했다. 침묵 기준만 짧게 줘 말이 끝나면 곧바로 닫는다.
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
    }

    private fun fail(kind: SpeechFailure, sound: Boolean) {
        listening = false
        partial = ""
        level = 0f
        failure = kind
        error = true
        if (sound) earconFail()
    }

    private fun releaseRecognizer() {
        runCatching {
            recognizer?.cancel()
            recognizer?.destroy()
        }
        recognizer = null
    }

    private fun isOnline(): Boolean = context.hasValidatedNetwork()

    private companion object {
        /** 준비음(약 310ms)이 끝나고 마이크를 여는 시점. */
        const val EARCON_LEAD_MS = 360L

        /** 인식기에 넘기는 힌트 수 상한. 너무 많으면 힌트끼리 경쟁해 오히려 흐려진다. */
        const val MAX_HINTS = 60

        /** 인식기가 바쁠 때 다시 열기까지 기다리는 시간. */
        const val BUSY_RETRY_MS = 400L

        fun failureOf(code: Int): SpeechFailure = when (code) {
            SpeechRecognizer.ERROR_NO_MATCH -> SpeechFailure.NO_MATCH
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechFailure.NO_SPEECH
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechFailure.NO_PERMISSION
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER,
            -> SpeechFailure.NETWORK
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
            -> SpeechFailure.LANGUAGE
            else -> SpeechFailure.UNAVAILABLE
        }
    }
}

/** 음성 입력이 실패한 이유. */
enum class SpeechFailure {
    /** 아무 말도 들리지 않았다. */
    NO_SPEECH,

    /** 들렸지만 알아듣지 못했다. */
    NO_MATCH,

    /** 마이크 권한이 없다. */
    NO_PERMISSION,

    /** 인식 서버에 닿지 못했다. */
    NETWORK,

    /** 한국어 인식 모델이 없거나 지원되지 않는다. */
    LANGUAGE,

    /** 이 기기에서 음성 인식을 쓸 수 없다. */
    UNAVAILABLE,
}

/**
 * 컴포지션에 묶인 [SpeechInputState] 를 만든다. 화면이 사라지면 자동으로 정리된다.
 *
 * @param cloudTranscribe 클라우드 받아쓰기 콜백. 주면 온라인일 때 그쪽을 우선한다
 */
@Composable
fun rememberSpeechInput(
    cloudTranscribe: (suspend (ByteArray, Int, List<String>) -> List<String>)? = null,
): SpeechInputState {
    val context = LocalContext.current
    val state = remember { SpeechInputState(context, cloudTranscribe) }
    DisposableEffect(Unit) { onDispose { state.release() } }
    return state
}

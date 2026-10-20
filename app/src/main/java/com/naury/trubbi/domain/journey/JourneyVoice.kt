package com.naury.trubbi.domain.journey

import com.naury.trubbi.core.voice.CloudStt
import com.naury.trubbi.core.voice.VoiceContext
import com.naury.trubbi.core.voice.VoiceIntent
import com.naury.trubbi.core.voice.VoiceNlu
import com.naury.trubbi.core.voice.VoicePrompter
import com.naury.trubbi.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * 여정 화면의 음성 입출력 창구.
 *
 * 안내 음성은 설정으로 끌 수 있고, 그 판단을 호출부마다 반복하지 않기 위해 여기서 한 번만 한다.
 * 인식은 클라우드를 먼저 쓰고 실패하면 호출부가 기기 인식으로 내려간다.
 *
 * @property prompter 실제 TTS 재생
 * @property nlu 발화 → 의도 해석
 * @property stt 클라우드 받아쓰기
 */
@Singleton
class JourneyVoice @Inject constructor(
    private val prompter: VoicePrompter,
    private val nlu: VoiceNlu,
    private val stt: CloudStt,
    settingsRepo: SettingsRepository,
    appScope: CoroutineScope,
) {
    /**
     * 음성 안내 설정값. false 면 [speak] 와 [speakAwait] 는 아무 일도 하지 않는다.
     *
     * 앱 스코프에서 즉시 구독한다. 화면 진입 직후 첫 안내가 기본값으로 나가면 안 되기 때문이다.
     */
    val enabled: StateFlow<Boolean> =
        settingsRepo.voiceGuide.stateIn(appScope, SharingStarted.Eagerly, true)

    /** 안내를 읽는다. 설정이 꺼져 있거나 문구가 비면 무시한다. */
    fun speak(text: String) {
        if (enabled.value && text.isNotBlank()) prompter.speak(text)
    }

    /**
     * 안내가 끝날 때까지 기다린다.
     *
     * 곧바로 마이크를 열면 TTS 꼬리가 그대로 인식되므로, 마이크를 여는 흐름은 이쪽을 쓴다.
     */
    suspend fun speakAwait(text: String) {
        if (enabled.value && text.isNotBlank()) prompter.speakAndWait(text)
    }

    /** 재생 중인 안내를 즉시 끊는다. 위저드를 닫거나 다시 시작할 때 멘트가 겹치는 것을 막는다. */
    fun stop() = prompter.stop()

    /**
     * 발화를 의도로 해석한다.
     *
     * @param text 인식된 문장
     * @param ctx 지금 무엇을 묻고 있는지 — 같은 말도 단계에 따라 다르게 읽는다
     */
    suspend fun interpret(text: String, ctx: VoiceContext): VoiceIntent = nlu.interpret(text, ctx)

    /**
     * 녹음한 PCM 을 클라우드로 받아쓴다.
     *
     * @param pcm 16-bit little-endian 모노 PCM
     * @param sampleRate 녹음 샘플레이트(Hz)
     * @param hints 지금 나올 법한 낱말. 인식기가 비슷한 소리를 이쪽으로 맞춘다
     * @return 신뢰도 순 후보 문장. 오프라인이면 빈 리스트이고, 호출부가 기기 인식으로 내려간다
     */
    suspend fun transcribe(pcm: ByteArray, sampleRate: Int, hints: List<String>): List<String> =
        stt.transcribe(pcm, sampleRate, hints)
}

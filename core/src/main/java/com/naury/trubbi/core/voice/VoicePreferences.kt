package com.naury.trubbi.core.voice

/**
 * 음성 출력이 참고하는 사용자 설정.
 *
 * [VoicePrompter] 는 설정을 어디에 저장하는지 알 필요가 없고, 읽기만 하면 된다.
 * 설정 저장소를 직접 참조하면 음성 계층이 저장 계층에 묶여 재사용도 테스트도 어려워진다.
 */
interface VoicePreferences {

    /**
     * 클라우드 TTS 를 쓸지.
     *
     * false 면 기기 내장 음성으로 읽는다. 오프라인 판단은 호출부가 따로 하므로,
     * 여기서는 사용자가 켜 뒀는지만 답하면 된다.
     */
    suspend fun useCloudVoice(): Boolean
}

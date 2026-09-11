package com.naury.trubbi.core.voice

/**
 * 음성 비서가 주고받는 값들.
 *
 * 클라우드 NLU 와 기기 내 규칙 기반 폴백이 같은 타입을 반환하므로, 호출부는 결과의 출처를
 * 구분하지 않고 [VoiceIntent] 만 다룬다. 출처가 필요하면 [VoiceIntent.fromCloud] 를 본다.
 */

/** 음성 비서가 이해하는 의도 종류. */
enum class VoiceAction {
    /** 장소를 말함 → 검색. */
    PLACE,

    /** 후보 목록에서 N번째/이거 선택. */
    SELECT,

    /** 직전 결과를 부정·정정("아니 그거 말고 …"). place/ordinal 이 있으면 그쪽으로 교체. */
    CORRECT,

    /** 현재 위치 사용. */
    CURRENT,

    /** 경유지 추가. */
    ADD_STOP,

    /** 나중에/건너뛰기. */
    SKIP,

    /** 여정 시작. */
    START,

    /** 취소. */
    CANCEL,

    /** 이전 단계. */
    BACK,

    /** 다시 말해달라/안내 반복. */
    REPEAT,

    /** 정차지 순서 이동("○○ 먼저", "○○ 가는 걸로"). place=대상 정차지명. */
    MOVE,

    /** 의미 없음. */
    NONE,
}

enum class VoiceStopKind { PICKUP, DROPOFF }

/** correct 의 정정 대상. */
enum class CorrectTarget { PLACE, ORDINAL, STEP }

/** 구조화된 음성 의도. Cloud(Claude Haiku) 또는 로컬 휴리스틱이 생성. */
data class VoiceIntent(
    val action: VoiceAction,
    val place: String? = null,
    val ordinal: Int? = null,
    val kind: VoiceStopKind? = null,
    val correctTarget: CorrectTarget? = null,
    /** 사용자에게 들려줄 짧은 확인/되묻기 멘트(선택). */
    val reply: String? = null,
    val confidence: Float = 0f,
    val raw: String = "",
    /** 클라우드 NLU가 만든 결과인지(로컬 폴백과 구분). */
    val fromCloud: Boolean = false,
)

/** NLU 에 함께 보내는 대화 맥락. step 으로 단계를 구분한다. */
data class VoiceContext(
    /** 지금 어느 단계인지 — `dest` · `origin` · `extra` · `pick` · `origin_yesno`. */
    val step: String,
    /** 화면에 보이는 후보 이름들(순서 = 1번,2번…). */
    val candidates: List<String> = emptyList(),
    /** 이미 확정한 곳들. */
    val decided: List<String> = emptyList(),
    /** 직전 비서 멘트. */
    val lastPrompt: String? = null,
)

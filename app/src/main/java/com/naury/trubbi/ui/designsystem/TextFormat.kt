package com.naury.trubbi.ui.designsystem

/**
 * 한눈에 읽기 쉽게 — 문장이 끝나는 곳(`.` `!` `?`)에서 줄을 바꾼다.
 *
 * 긴 안내 문구가 한 줄로 주르륵 이어지면 가독성이 떨어지므로, 종결부호 뒤에 공백이 오면 줄바꿈으로
 * 치환한다. 단 다음은 줄바꿈하지 않는다:
 *  - 소수점/버전 같은 숫자 사이의 점 (예: `1.5`, `4.8`)
 *  - 줄임표 `...` (그대로 둔다)
 *
 * 예) "여정을 시작할게요. 먼저 어디로 가시는지 말씀해 주세요."
 *   → "여정을 시작할게요.\n먼저 어디로 가시는지 말씀해 주세요."
 */
fun String.sentenceLines(): String {
    if (isBlank()) return this
    val re = Regex("(?<![0-9])([.!?]+)[ \\t]+")
    return replace(re) { m ->
        val punct = m.groupValues[1]
        // 줄임표(.. , ...)는 줄바꿈하지 않고 원래대로 둔다.
        if (punct.length >= 2 && punct.all { it == '.' }) {
            m.value
        } else {
            "$punct\n"
        }
    }.trim()
}

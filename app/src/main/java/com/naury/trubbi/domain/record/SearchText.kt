package com.naury.trubbi.domain.record

/**
 * 기록 검색의 글자 맞추기 규칙.
 *
 * 운전 중 짧게 치는 검색어를 너그럽게 받는다.
 * - 띄어쓰기를 무시한다: "세종 대로" 로 "세종대로" 를, "세종대로" 로 "세종 대로" 를 찾는다.
 * - 초성만 쳐도 찾는다: "ㅅㅇㅅㅊ" 로 "서울시청" 을 찾는다.
 * - 검색어를 띄어 쓰면 낱말마다 어딘가에 들어 있기만 하면 된다(AND).
 *
 * 화면·코루틴과 무관한 순수 함수라 단위 테스트로 규칙을 고정한다.
 */
object SearchText {

    /**
     * [haystack] 이 [query] 에 걸리는지.
     *
     * @param haystack 기록의 검색 대상 글자를 공백으로 이어 붙인 것
     * @param query 사용자가 친 검색어. 비어 있으면 항상 true
     */
    fun matches(haystack: String, query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        val hay = Haystack(haystack)
        val tokens = q.split(WHITESPACE).filter { it.isNotEmpty() }
        if (tokens.all { hay.has(it) }) return true
        // 낱말로 나눠서는 안 걸려도 붙여 쓴 형태로는 걸릴 수 있다("세종 대로" → "세종대로").
        return tokens.size > 1 && hay.has(tokens.joinToString(""))
    }

    /** 한글 음절을 초성으로 바꾼다. 한글이 아닌 글자는 그대로 둔다. */
    fun choseong(text: String): String = buildString(text.length) {
        for (ch in text) {
            if (ch in HANGUL_FIRST..HANGUL_LAST) append(CHOSEONG[(ch - HANGUL_FIRST) / SYLLABLES_PER_INITIAL]) else append(ch)
        }
    }

    /** 초성(ㄱ~ㅎ)만으로 이루어진 검색어인지. */
    fun isChoseongQuery(token: String): Boolean = token.isNotEmpty() && token.all { it in CHOSEONG }

    private class Haystack(raw: String) {
        private val plain = raw.lowercase()
        private val compact = plain.replace(WHITESPACE, "")
        private val initials by lazy { choseong(compact) }

        fun has(token: String): Boolean = when {
            isChoseongQuery(token) -> initials.contains(token)
            else -> plain.contains(token) || compact.contains(token)
        }
    }

    private val WHITESPACE = Regex("\\s+")
    private const val CHOSEONG = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
    private const val HANGUL_FIRST = '가'
    private const val HANGUL_LAST = '힣'
    private const val SYLLABLES_PER_INITIAL = 21 * 28
}

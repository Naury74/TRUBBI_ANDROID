package com.naury.trubbi.core.location

/**
 * 주소를 화면·음성용 짧은 이름으로 줄이는 규칙.
 *
 * 예전에는 화면마다 축약 함수가 따로 있어(앞 두 토막, 뒤 두 토막, 구+동…) 같은 주소가 화면마다 다르게 보였다.
 * 여기 한 곳에서 두 가지 모양만 만든다.
 *  - [region]: "중구 명동"처럼 동네 이름. 홈 헤더·안내 음성·저장명 제안에 쓴다.
 *  - [street]: "한강대로 405"처럼 찾아갈 수 있는 이름. 출발지·정차지 이름에 쓴다.
 *
 * 도로명 주소에는 '동'이 없어 [region] 이 '중구'만 남는다. 그래서 위치를 잡을 때 행정동을 따로 받아
 * [CapturedLocation.region] 에 넣고, 그게 있으면 그걸 먼저 쓴다.
 */
object PlaceNames {

    /** 동네 이름(구·군 + 동·읍·면). 알 수 없으면 null. */
    fun region(address: String?): String? {
        val tokens = tokens(address)
        if (tokens.isEmpty()) return null
        val dong = tokens.lastOrNull { it.endsWith("동") || it.endsWith("읍") || it.endsWith("면") || (it.endsWith("가") && it.any(Char::isDigit)) }
        val gu = tokens.lastOrNull { it.endsWith("구") || it.endsWith("군") }
        val si = tokens.drop(1).firstOrNull { it.endsWith("시") }
        return when {
            gu != null && dong != null -> "$gu $dong"
            gu != null -> gu
            si != null && dong != null -> "$si $dong"
            dong != null -> dong
            si != null -> si
            else -> tokens.first()
        }
    }

    /**
     * 찾아갈 수 있는 짧은 이름. 시·도와 구·군을 떼고 도로명(또는 동·리) + 번지를 남긴다.
     *
     * 예: "서울특별시 중구 한강대로 405" → "한강대로 405", "경남 양산시 동면 금산리 1457-14" → "금산리 1457-14".
     */
    fun street(address: String?): String? {
        val tokens = tokens(address)
        if (tokens.isEmpty()) return null
        if (tokens.size <= 2) return tokens.joinToString(" ")
        val anchor = tokens.indexOfLast { t ->
            t.endsWith("로") || t.endsWith("길") || t.endsWith("동") || t.endsWith("리") || t.endsWith("가")
        }
        return if (anchor > 0) tokens.drop(anchor).joinToString(" ") else tokens.takeLast(2).joinToString(" ")
    }

    private fun tokens(address: String?): List<String> =
        address?.trim()?.removePrefix("대한민국")?.split(Regex("[\\s,]+"))?.filter { it.isNotBlank() }.orEmpty()
}

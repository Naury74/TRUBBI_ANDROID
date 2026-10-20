package com.naury.trubbi.core.voice

/**
 * 장소를 말한 한 마디를 검색어로 바꾸는 규칙.
 *
 * 받아쓰기 결과에는 "부산 신항으로 가 줘" 처럼 동작과 조사가 붙어 온다. 검색에는 "부산 신항"만 있어야 장소가
 * 잡힌다. 음성 비서([VoiceNlu])와 경로 화면의 음성 검색이 같은 규칙을 쓰도록 여기 모은다.
 *
 * 여기 있는 한국어 낱말들은 번역 대상이 아니라 한국어 발화를 알아듣기 위한 규칙이다.
 */
object PlaceUtterance {

    /** 장소 검색 칸에서 한 말의 뜻. */
    sealed interface Meaning {
        /** 현재 위치를 쓰겠다. */
        data object Here : Meaning

        /** 검색을 그만두겠다. */
        data object Cancel : Meaning

        /** 이 검색어로 찾는다. */
        data class Search(val query: String) : Meaning
    }

    /**
     * 검색 칸에서 한 말을 해석한다.
     *
     * @return 알아들을 수 없으면(군말만 있으면) null
     */
    fun meaning(spoken: String): Meaning? {
        val compact = spoken.replace(" ", "")
        if (compact.isEmpty()) return null
        if (compact in CANCEL_ALONE) return Meaning.Cancel
        if (HERE_WORDS.any(compact::contains) || compact in HERE_ALONE) return Meaning.Here
        return query(spoken).takeIf { it.length >= 2 }?.let { Meaning.Search(it) }
    }

    /**
     * 발화에서 장소 이름만 남긴다.
     *
     * 낱말 단위로 지운다. 문장 전체에서 "로"·"요" 같은 글자를 바꿔치기하면 "세종대로"가 "세종대"로,
     * "신촌로터리"가 "신촌 터리"로 망가진다. 동작·군말 낱말은 통째로 빼고, 조사는 낱말 끝에서만 뗀다.
     */
    fun query(spoken: String): String = spoken.trim().split(Regex("\\s+"))
        .filter { it.isNotEmpty() && it !in STOP_TOKENS && STOP_PREFIXES.none(it::startsWith) }
        .map { word ->
            val particle = PARTICLES.firstOrNull { word.endsWith(it) && word.length - it.length >= 2 }
            if (particle != null) word.dropLast(particle.length) else word
        }
        .joinToString(" ")
        .trim()

    /**
     * 받아쓰기 후보 여러 개를 검색어 후보로 바꾼다. 같은 검색어가 되는 후보는 하나로 합친다.
     *
     * 인식기가 1순위로 내놓은 문장이 틀리고 2·3순위가 맞는 일이 흔하다(특히 고유명사). 그래서 1순위만 쓰지 않고
     * 후보를 모두 검색어로 바꿔 둔다.
     */
    fun queries(alternatives: List<String>): List<String> = alternatives
        .map(::query)
        .filter { it.length >= 2 }
        .distinctBy { it.replace(" ", "") }

    /** 이 말만 했을 때 현재 위치로 보는 대답. */
    internal val HERE_ALONE = setOf("여기", "여기요", "여기서", "현장", "현장이요", "지금여기")

    /** 들어 있기만 하면 현재 위치로 보는 표현. */
    internal val HERE_WORDS = listOf("현재위치", "현위치", "내위치", "지금위치", "여기서출발")

    private val CANCEL_ALONE = setOf("취소", "취소해", "취소해줘", "그만", "닫기", "닫아", "됐어", "안할래")

    /** 장소 이름이 아닌 낱말. 통째로 뺀다. */
    private val STOP_TOKENS = setOf(
        "아니", "아니요", "아니야", "말고", "그거", "그걸", "그게", "아니라", "아니고", "틀렸어", "틀렸다", "잘못", "바꿔서", "바꿔", "다시",
        "상차지", "하차지", "상차", "하차", "픽업", "경유지", "경유", "출발지", "도착지", "목적지", "정차지",
        "추가", "들를곳", "들를데", "싣고", "실으러", "실어", "내리러", "내려서", "배송", "납품", "하러",
        "넣어줘", "넣어주세요", "넣어", "해줘", "해주세요", "할게요", "할게", "할래", "갈게요", "갈게", "갈래", "가자", "가요",
        "가줘", "가주세요", "가", "찾아줘", "찾아", "검색", "검색해줘", "안내해줘", "안내", "길안내", "어디", "있어", "있나",
        "좀", "그리고", "또", "먼저", "부터", "제일", "가는", "걸로", "가는걸로", "순서", "이번엔", "이번에",
        "가기", "전에", "갈", "거야", "거예요",
    )

    /** 이걸로 시작하는 낱말은 동작 표현("상차해서", "추가해줘", "들렀다가")이라 뺀다. */
    private val STOP_PREFIXES = listOf("상차해", "하차해", "픽업해", "추가해", "추가하", "들렀", "들러", "거쳐", "가는걸", "검색해", "안내해")

    /** 낱말 끝에서 떼는 조사. 남는 부분이 두 글자 이상일 때만 뗀다. */
    private val PARTICLES = listOf("에서", "으로", "까지", "이요")
}

package com.naury.trubbi.feature.journey.planner

import com.naury.trubbi.core.location.PlaceCandidate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * 음성 검색 결과 하나.
 *
 * @property query 검색창에 넣을 검색어
 * @property results 그 검색어로 찾은 장소들
 * @property others 다른 인식 후보. "혹시 이곳인가요?" 칩으로 보여 준다
 */
internal data class VoicePick(val query: String, val results: List<PlaceCandidate>, val others: List<String>)

/**
 * 받아쓰기 후보들 가운데 실제 장소가 가장 잘 잡히는 검색어를 고른다.
 *
 * 티맵처럼 장소 데이터와 맞춰 보는 단계다. 인식기는 "평택항"을 "평택 앙"으로, "신항"을 "시낭"으로 듣는 식으로
 * 고유명사에 약하다. 하지만 2·3순위 후보에 맞는 말이 섞여 있는 일이 많다. 그래서 앞쪽 후보 몇 개를 함께 검색한다.
 * 결과가 있고, 1등 장소 이름이 검색어와 겹치는 후보를 고른다. 인식기 순위도 점수에 넣어 엇비슷하면 1순위를 따른다.
 *
 * @param queries 검색어로 바꾼 인식 후보(인식기 순위대로)
 * @param search 검색어 → 장소들
 * @return 고를 것이 없으면(후보가 없으면) null. 모든 후보에 결과가 없으면 1순위를 결과 없이 돌려준다
 */
internal suspend fun pickVoiceQuery(
    queries: List<String>,
    search: suspend (String) -> List<PlaceCandidate>,
): VoicePick? {
    val tried = queries.take(MAX_TRIED)
    if (tried.isEmpty()) return null
    val found = coroutineScope { tried.map { q -> async { q to runCatching { search(q) }.getOrDefault(emptyList()) } }.awaitAll() }
    val best = found.withIndex().maxByOrNull { (rank, pair) -> voiceScore(rank, pair.first, pair.second) }!!.value
    return VoicePick(best.first, best.second, queries.filter { it != best.first }.take(MAX_OTHERS))
}

/** 인식 순위와 장소 검색 결과로 매긴 점수. 클수록 그럴듯하다. */
internal fun voiceScore(rank: Int, query: String, results: List<PlaceCandidate>): Int {
    if (results.isEmpty()) return -RANK_WEIGHT * (rank + 1)
    val q = compact(query)
    val top = compact(results.first().name)
    val nameHit = when {
        top == q -> EXACT_NAME
        top.contains(q) || q.contains(top) -> PARTIAL_NAME
        results.take(3).any { compact(it.name).contains(q) } -> NEAR_NAME
        else -> 0
    }
    return HAS_RESULTS + nameHit - RANK_WEIGHT * rank
}

private fun compact(s: String) = s.replace(" ", "").lowercase()

private const val MAX_TRIED = 3
private const val MAX_OTHERS = 3
private const val HAS_RESULTS = 10
private const val EXACT_NAME = 10
private const val PARTIAL_NAME = 6
private const val NEAR_NAME = 3
private const val RANK_WEIGHT = 3

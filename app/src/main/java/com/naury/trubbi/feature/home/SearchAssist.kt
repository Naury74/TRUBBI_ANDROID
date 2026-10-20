package com.naury.trubbi.feature.home

import android.content.Context
import androidx.core.content.edit
import com.naury.trubbi.core.location.PlaceCandidate
import com.naury.trubbi.core.location.PlaceNames
import com.naury.trubbi.data.local.RecordWithMedia
import com.naury.trubbi.domain.record.NearPlace
import com.naury.trubbi.domain.record.SearchText
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 검색창 아래에 띄우는 도움말 묶음.
 *
 * @property recent 최근 검색어(최근 것부터)
 * @property frequentPlaces 내 기록에 자주 나오는 동네와 그 기록 수
 * @property tags 검색어가 들어간 태그
 * @property places 검색어로 찾은 실제 장소 중 그 주변에 내 기록이 있는 것
 */
data class SearchAssist(
    val recent: List<String> = emptyList(),
    val frequentPlaces: List<Pair<String, Int>> = emptyList(),
    val tags: List<String> = emptyList(),
    val places: List<PlaceHit> = emptyList(),
)

/**
 * 장소 검색 결과 하나와 그 주변에서 남긴 내 기록 수.
 *
 * @property place 카카오 장소 검색 결과
 * @property count [NearPlace.DEFAULT_RADIUS_M] 안의 내 기록 수
 */
data class PlaceHit(val place: PlaceCandidate, val count: Int) {
    fun toNear(): NearPlace = NearPlace(place.name, place.latitude, place.longitude)
}

/** 검색 도움말을 만드는 규칙. 기록 목록만 받아 계산하는 순수 함수들이다. */
object SearchAssistRules {

    /**
     * 기록에 자주 나오는 동네(많은 순). 동네 칸이 비어 있는 옛 기록은 주소에서 동네를 뽑는다.
     *
     * @param limit 최대 개수
     */
    fun frequentPlaces(records: List<RecordWithMedia>, limit: Int = FREQUENT_LIMIT): List<Pair<String, Int>> =
        records
            .mapNotNull { rec -> rec.record.locationRegion?.ifBlank { null } ?: PlaceNames.region(rec.record.locationName) }
            .groupingBy { it }
            .eachCount()
            .entries
            .filter { it.value >= MIN_PLACE_COUNT }
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key to it.value }

    /** 검색어가 들어간 태그(자주 쓴 순). 검색어와 똑같은 태그는 뺀다. */
    fun tags(records: List<RecordWithMedia>, query: String, limit: Int = TAG_LIMIT): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return records.flatMap { it.record.tags }
            .filter { it.isNotBlank() && !it.equals(q, ignoreCase = true) && SearchText.matches(it, q) }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
    }

    /** 장소 검색 결과마다 그 주변의 내 기록을 세고, 하나도 없는 곳은 뺀다. */
    fun placeHits(records: List<RecordWithMedia>, found: List<PlaceCandidate>, limit: Int = PLACE_LIMIT): List<PlaceHit> =
        found.asSequence()
            .map { p -> PlaceHit(p, records.count { NearPlace(p.name, p.latitude, p.longitude).contains(it.record.latitude, it.record.longitude) }) }
            .filter { it.count > 0 }
            .take(limit)
            .toList()

    private const val FREQUENT_LIMIT = 8
    private const val TAG_LIMIT = 6
    private const val PLACE_LIMIT = 3
    private const val MIN_PLACE_COUNT = 2
}

/**
 * 최근 검색어. 기기 안에만 두고 백업하지 않는다.
 *
 * 순서가 중요하고 개수가 적어서 줄바꿈으로 이은 문자열 하나로 저장한다.
 */
@Singleton
class SearchHistory @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())

    /** 최근 검색어(최근 것부터). */
    val recent: StateFlow<List<String>> = state.asStateFlow()

    /** 검색어를 맨 앞에 넣는다. 같은 검색어가 있으면 앞으로 옮긴다. */
    fun add(query: String) {
        val q = query.trim().replace('\n', ' ')
        if (q.length < MIN_LENGTH) return
        save(listOf(q) + state.value.filterNot { it.equals(q, ignoreCase = true) })
    }

    fun remove(query: String) = save(state.value - query)

    fun clear() = save(emptyList())

    private fun save(list: List<String>) {
        val kept = list.take(MAX)
        state.value = kept
        prefs.edit { putString(KEY, kept.joinToString("\n")) }
    }

    private fun load(): List<String> =
        prefs.getString(KEY, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    private companion object {
        const val PREFS = "search_history"
        const val KEY = "recent"
        const val MAX = 10
        const val MIN_LENGTH = 1
    }
}

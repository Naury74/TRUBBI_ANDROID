package com.naury.trubbi.domain.record

import com.naury.trubbi.core.i18n.purposeLabel
import com.naury.trubbi.core.ui.StringProvider
import com.naury.trubbi.data.local.RecordWithMedia
import com.naury.trubbi.domain.memo.MemoContent
import com.naury.trubbi.domain.model.RecordType
import javax.inject.Inject
import javax.inject.Singleton

/** 오늘 저장된 기록을 종류별로 센 값 — 홈 상단 요약 카드에 쓴다. */
data class TodaySummary(
    val photos: Int = 0,
    val videos: Int = 0,
    val documents: Int = 0,
    val barcodes: Int = 0,
    val memos: Int = 0,
    val total: Int = 0,
)

/** 홈 타임라인의 하루치 묶음. */
data class HomeDateSection(
    val dateIso: String,
    val items: List<RecordWithMedia>,
)

/** 홈 화면이 한 번에 그리는 데이터 뭉치. */
data class HomeState(
    val summary: TodaySummary = TodaySummary(),
    val recents: List<RecordWithMedia> = emptyList(),
    val favorites: List<RecordWithMedia> = emptyList(),
    val dateSections: List<HomeDateSection> = emptyList(),
    /** 달력 점 표시용 — 업무일별 기록 수. */
    val counts: Map<String, Int> = emptyMap(),
)

/** 홈 타임라인에 걸 수 있는 조건. 각 필드가 null/공백이면 그 조건은 적용하지 않는다. */
data class RecordFilter(
    val query: String = "",
    val type: String? = null,
    val purpose: String? = null,
    /** 업무일 범위 (시작, 끝). 양끝을 포함한다. */
    val dateRange: Pair<String, String>? = null,
) {
    val isEmpty: Boolean
        get() = query.isBlank() && type == null && purpose == null && dateRange == null
}

/**
 * 기록 목록을 홈 화면이 그릴 형태로 좁히고 묶는다.
 *
 * 흐름·코루틴 없이 리스트 하나를 받아 결과 하나를 돌려주므로 규칙만 단위 테스트로 검증할 수 있다.
 *
 * @property strings 검색 대상에 포함할 용도 라벨을 읽는다
 */
@Singleton
class HomeTimelineBuilder @Inject constructor(
    private val strings: StringProvider,
) {
    /**
     * 필터를 적용해 홈 상태를 만든다.
     *
     * 필터는 날짜별 타임라인에만 적용한다. 오늘 요약·최근·즐겨찾기 섹션은 전체 기록 기준으로
     * 유지하고, 화면에서 필요할 때만 감춘다. 필터를 걸었다고 상단 요약 숫자가 바뀌면
     * 사용자가 오늘 몇 건을 남겼는지 확인할 방법이 없어진다.
     *
     * @param records 삭제되지 않은 전체 기록 (최신순 가정)
     * @param filter 타임라인에 적용할 조건
     * @param today 오늘 요약 계산 기준 업무일 (yyyy-MM-dd)
     */
    fun build(
        records: List<RecordWithMedia>,
        filter: RecordFilter,
        today: String,
    ): HomeState {
        val todays = records.filter { it.record.jobDate == today }
        val timeline = records.filter { matches(it, filter) }
        return HomeState(
            summary = summarize(todays),
            recents = records.take(RECENT_LIMIT),
            favorites = records.filter { it.record.isFavorite }.take(FAVORITE_LIMIT),
            dateSections = timeline
                .groupBy { it.record.jobDate }
                .toSortedMap(compareByDescending { it })
                .map { (date, items) ->
                    HomeDateSection(date, items.sortedByDescending { it.record.createdAt })
                },
            counts = records.groupingBy { it.record.jobDate }.eachCount(),
        )
    }

    /** 기록 하나가 필터를 모두 통과하는지. 조건이 비어 있으면 항상 true. */
    fun matches(rec: RecordWithMedia, filter: RecordFilter): Boolean {
        if (filter.type != null && !matchesType(rec, filter.type)) return false
        if (filter.purpose != null && rec.record.purpose != filter.purpose) return false
        filter.dateRange?.let { (from, to) ->
            if (rec.record.jobDate < from || rec.record.jobDate > to) return false
        }
        return filter.query.isBlank() || matchesQuery(rec, filter.query)
    }

    /**
     * 공백으로 나눈 검색어가 모두 들어 있어야 통과하는 AND 검색.
     *
     * 제목·용도·메모 본문·바코드 값·장소명·태그를 한 문자열로 이어 붙여 훑는다.
     * 메모는 서식 정보를 뺀 평문만 본다.
     */
    fun matchesQuery(rec: RecordWithMedia, query: String): Boolean {
        val r = rec.record
        val hay = buildString {
            append(r.title).append(' ')
            append(purposeLabel(strings, r.purpose)).append(' ')
            r.memo?.let { append(MemoContent.plainText(MemoContent.decode(it))).append(' ') }
            r.barcodeValue?.let { append(it).append(' ') }
            r.locationName?.let { append(it).append(' ') }
            append(r.tags.joinToString(" "))
        }.lowercase()
        return query.lowercase().split(" ").filter { it.isNotBlank() }.all { hay.contains(it) }
    }

    /**
     * 종류 칩 대조.
     *
     * "FAVORITE" 는 종류가 아니라 별표 여부를 본다. [RecordType] 에 없는 값이 들어오면
     * 조건 자체를 걸지 않은 것으로 보고 통과시킨다.
     */
    fun matchesType(rec: RecordWithMedia, type: String): Boolean = when {
        type == FAVORITE_FILTER -> rec.record.isFavorite
        RecordType.entries.any { it.name == type } -> rec.record.type == type
        else -> true
    }

    private fun summarize(todays: List<RecordWithMedia>) = TodaySummary(
        photos = todays.count { it.record.type == RecordType.PHOTO.name },
        videos = todays.count { it.record.type == RecordType.VIDEO.name },
        documents = todays.count { it.record.type == RecordType.DOCUMENT.name },
        barcodes = todays.count { it.record.type == RecordType.BARCODE.name },
        memos = todays.count { it.record.type == RecordType.MEMO.name },
        total = todays.size,
    )

    companion object {
        /** 종류 칩 중 유일하게 [RecordType] 이 아닌 값. */
        const val FAVORITE_FILTER = "FAVORITE"
        private const val RECENT_LIMIT = 10
        private const val FAVORITE_LIMIT = 12
    }
}

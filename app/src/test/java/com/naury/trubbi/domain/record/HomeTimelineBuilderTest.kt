package com.naury.trubbi.domain.record

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.naury.trubbi.core.ui.StringProvider
import com.naury.trubbi.data.local.RecordEntity
import com.naury.trubbi.data.local.RecordWithMedia
import com.naury.trubbi.domain.model.RecordType
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 홈 타임라인 필터·요약 규칙 테스트.
 *
 * 검색·필터는 타임라인에만 걸고 오늘 요약은 전체 기준으로 유지한다는 것이 이 화면의 규칙이다.
 * 필터를 걸었다고 상단 숫자가 같이 줄면 사용자가 오늘 몇 건을 남겼는지 확인할 방법이 없어진다.
 * 그 경계와 검색어 대조 규칙을 고정해 둔다.
 */
@RunWith(RobolectricTestRunner::class)
class HomeTimelineBuilderTest {

    private val strings = StringProvider(ApplicationProvider.getApplicationContext())
    private val builder = HomeTimelineBuilder(strings)

    private val today = "2026-09-09"

    private fun record(
        id: String,
        type: RecordType = RecordType.PHOTO,
        title: String = "기록 $id",
        purpose: String = "배송",
        jobDate: String = today,
        createdAt: Long = 1L,
        favorite: Boolean = false,
        memo: String? = null,
        barcode: String? = null,
        location: String? = null,
        tags: List<String> = emptyList(),
    ) = RecordWithMedia(
        record = RecordEntity(
            recordId = id,
            type = type.name,
            title = title,
            purpose = purpose,
            memo = memo,
            jobDate = jobDate,
            createdAt = createdAt,
            updatedAt = createdAt,
            latitude = null,
            longitude = null,
            locationName = location,
            tags = tags,
            barcodeValue = barcode,
            barcodeFormat = null,
            isFavorite = favorite,
        ),
        media = emptyList(),
    )

    private fun HomeState.timelineIds() = dateSections.flatMap { it.items }.map { it.record.recordId }

    // ── 요약 ──────────────────────────────────────────────────────────────────

    @Test
    fun `오늘 요약은 종류별로 센다`() {
        val state = builder.build(
            listOf(
                record("a", RecordType.PHOTO),
                record("b", RecordType.PHOTO),
                record("c", RecordType.VIDEO),
                record("d", RecordType.MEMO),
                record("e", jobDate = "2026-09-08"),
            ),
            RecordFilter(),
            today,
        )

        assertThat(state.summary.photos).isEqualTo(2)
        assertThat(state.summary.videos).isEqualTo(1)
        assertThat(state.summary.memos).isEqualTo(1)
        assertThat(state.summary.total).isEqualTo(4)
    }

    @Test
    fun `필터를 걸어도 오늘 요약은 전체 기준을 유지한다`() {
        val records = listOf(record("a", RecordType.PHOTO), record("b", RecordType.VIDEO))
        val state = builder.build(records, RecordFilter(type = RecordType.VIDEO.name), today)

        assertThat(state.summary.total).isEqualTo(2)
        assertThat(state.timelineIds()).containsExactly("b")
    }

    @Test
    fun `즐겨찾기와 최근 섹션도 필터의 영향을 받지 않는다`() {
        val records = listOf(record("a", favorite = true), record("b", RecordType.VIDEO))
        val state = builder.build(records, RecordFilter(query = "없는말"), today)

        assertThat(state.favorites.map { it.record.recordId }).containsExactly("a")
        assertThat(state.recents).hasSize(2)
        assertThat(state.timelineIds()).isEmpty()
    }

    // ── 묶기·정렬 ─────────────────────────────────────────────────────────────

    @Test
    fun `타임라인은 업무일 최신순으로 묶고 안에서는 저장 시각 최신순으로 정렬한다`() {
        val state = builder.build(
            listOf(
                record("old", jobDate = "2026-09-07", createdAt = 10),
                record("new1", jobDate = "2026-09-09", createdAt = 20),
                record("new2", jobDate = "2026-09-09", createdAt = 30),
            ),
            RecordFilter(),
            today,
        )

        assertThat(state.dateSections.map { it.dateIso })
            .containsExactly("2026-09-09", "2026-09-07").inOrder()
        assertThat(state.dateSections[0].items.map { it.record.recordId })
            .containsExactly("new2", "new1").inOrder()
    }

    @Test
    fun `달력 점 표시용 집계는 업무일별 전체 건수다`() {
        val state = builder.build(
            listOf(
                record("a", jobDate = "2026-09-09"),
                record("b", jobDate = "2026-09-09"),
                record("c", jobDate = "2026-09-08"),
            ),
            RecordFilter(type = RecordType.VIDEO.name),
            today,
        )

        assertThat(state.counts).containsExactly("2026-09-09", 2, "2026-09-08", 1)
    }

    // ── 필터 ──────────────────────────────────────────────────────────────────

    @Test
    fun `즐겨찾기 칩은 종류가 아니라 별표를 본다`() {
        val records = listOf(record("a", favorite = true), record("b", RecordType.VIDEO))
        val state = builder.build(records, RecordFilter(type = HomeTimelineBuilder.FAVORITE_FILTER), today)

        assertThat(state.timelineIds()).containsExactly("a")
    }

    @Test
    fun `업무일 범위는 양끝을 포함한다`() {
        val records = listOf(
            record("a", jobDate = "2026-09-07"),
            record("b", jobDate = "2026-09-08"),
            record("c", jobDate = "2026-09-09"),
        )
        val state = builder.build(records, RecordFilter(dateRange = "2026-09-07" to "2026-09-08"), today)

        assertThat(state.timelineIds()).containsExactly("a", "b")
    }

    @Test
    fun `조건 여러 개는 모두 만족해야 남는다`() {
        val records = listOf(
            record("a", RecordType.PHOTO, purpose = "배송"),
            record("b", RecordType.PHOTO, purpose = "정비"),
            record("c", RecordType.VIDEO, purpose = "배송"),
        )
        val state = builder.build(
            records,
            RecordFilter(type = RecordType.PHOTO.name, purpose = "배송"),
            today,
        )

        assertThat(state.timelineIds()).containsExactly("a")
    }

    // ── 검색 ──────────────────────────────────────────────────────────────────

    @Test
    fun `검색어는 공백으로 나눠 모두 들어 있어야 한다`() {
        val records = listOf(
            record("a", title = "부산 하차 사진"),
            record("b", title = "부산 상차 사진"),
        )

        assertThat(builder.build(records, RecordFilter(query = "부산 하차"), today).timelineIds())
            .containsExactly("a")
        assertThat(builder.build(records, RecordFilter(query = "부산 사진"), today).timelineIds())
            .containsExactly("a", "b")
    }

    @Test
    fun `제목 말고 바코드 장소 태그도 검색 대상이다`() {
        val records = listOf(
            record("a", title = "무제", barcode = "8801234567890"),
            record("b", title = "무제", location = "인천항 물류센터"),
            record("c", title = "무제", tags = listOf("긴급", "냉장")),
            record("d", title = "무제"),
        )

        assertThat(builder.build(records, RecordFilter(query = "880123"), today).timelineIds())
            .containsExactly("a")
        assertThat(builder.build(records, RecordFilter(query = "인천항"), today).timelineIds())
            .containsExactly("b")
        assertThat(builder.build(records, RecordFilter(query = "냉장"), today).timelineIds())
            .containsExactly("c")
    }

    @Test
    fun `검색은 대소문자를 가리지 않는다`() {
        val records = listOf(record("a", title = "Busan Port"))

        assertThat(builder.build(records, RecordFilter(query = "busan"), today).timelineIds())
            .containsExactly("a")
    }

    @Test
    fun `조건이 비어 있으면 아무것도 걸러내지 않는다`() {
        val records = listOf(record("a"), record("b", jobDate = "2020-01-01"))
        val state = builder.build(records, RecordFilter(), today)

        assertThat(state.timelineIds()).containsExactly("a", "b")
        assertThat(RecordFilter().isEmpty).isTrue()
        assertThat(RecordFilter(query = "x").isEmpty).isFalse()
    }
}

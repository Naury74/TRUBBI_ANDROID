package com.naury.trubbi.domain.record

import com.google.common.truth.Truth.assertThat
import com.naury.trubbi.data.local.RecordEntity
import com.naury.trubbi.data.local.RecordWithMedia
import org.junit.Test

/** 상세 화면 "관련 자료"와 "이 장소 주변" 조건 규칙. */
class RelatedRecordsTest {

    private fun rec(
        id: String,
        lat: Double? = null,
        lng: Double? = null,
        journey: String? = null,
        tags: List<String> = emptyList(),
        barcode: String? = null,
        createdAt: Long = 0L,
    ) = RecordWithMedia(
        RecordEntity(
            recordId = id, type = "PHOTO", title = id, purpose = "배송", memo = null, jobDate = "2026-10-20",
            createdAt = createdAt, updatedAt = createdAt, latitude = lat, longitude = lng, locationName = null,
            tags = tags, barcodeValue = barcode, barcodeFormat = null, journeyId = journey,
        ),
        emptyList(),
    )

    // 서울시청 부근. 위도 0.001도 ≈ 111m.
    private val lat = 37.5665
    private val lng = 126.9780

    @Test
    fun `주변 조건은 반경 안의 좌표만 통과시킨다`() {
        val near = NearPlace("시청", lat, lng, radiusM = 300)
        assertThat(near.contains(lat + 0.002, lng)).isTrue() // ≈ 222m
        assertThat(near.contains(lat + 0.004, lng)).isFalse() // ≈ 445m
        assertThat(near.contains(null, null)).isFalse()
    }

    @Test
    fun `같은 여정 같은 장소 같은 태그로 나누고 한 기록은 한 번만 나온다`() {
        val me = rec("me", lat, lng, journey = "j1", tags = listOf("인수증"))
        val all = listOf(
            me,
            rec("sameJourneyAndPlace", lat, lng, journey = "j1", createdAt = 2),
            rec("placeOnly", lat + 0.001, lng, createdAt = 3),
            rec("far", lat + 0.05, lng, createdAt = 4),
            rec("tagged", tags = listOf("인수증"), createdAt = 5),
            rec("unrelated", createdAt = 6),
        )

        val related = RelatedRecords.of(me, all)

        assertThat(related.sameJourney.map { it.record.recordId }).containsExactly("sameJourneyAndPlace")
        assertThat(related.samePlace.map { it.record.recordId }).containsExactly("placeOnly")
        assertThat(related.linked.map { it.record.recordId }).containsExactly("tagged")
        assertThat(related.nearbyCount).isEqualTo(2)
    }

    @Test
    fun `같은 바코드 값이나 사진 속 글자에 그 값이 있으면 이어진다`() {
        val me = rec("me", barcode = "8801234567890")
        val doc = RecordWithMedia(
            rec("doc").record.copy(ocrText = "송장 8801234567890 서명"),
            emptyList(),
        )
        val related = RelatedRecords.of(me, listOf(me, rec("scan", barcode = "8801234567890"), doc, rec("other", barcode = "1")))
        assertThat(related.linked.map { it.record.recordId }).containsExactly("scan", "doc")
    }
}

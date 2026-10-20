package com.naury.trubbi.domain.record

import com.naury.trubbi.data.local.RecordWithMedia

/**
 * 기록 하나와 이어지는 다른 기록들. 상세 화면 아래 "관련 자료"에 보인다.
 *
 * 한 기록은 한 묶음에만 나온다. 앞 묶음에 이미 나온 기록은 뒤 묶음에서 뺀다.
 *
 * @property sameJourney 같은 여정에서 남긴 것(시간순)
 * @property samePlace 가까운 곳([SAME_PLACE_RADIUS_M] 안)에서 남긴 것(최신순). 다른 날 같은 거래처에 갔을 때 지난 증빙을 찾는다
 * @property linked 같은 바코드 값이나 같은 태그를 가진 것(최신순)
 * @property nearbyCount 장소 조건([NearPlace.DEFAULT_RADIUS_M])으로 걸리는 다른 기록 수. "이 근처 기록 모두 보기"에 쓴다
 */
data class RelatedRecords(
    val sameJourney: List<RecordWithMedia> = emptyList(),
    val samePlace: List<RecordWithMedia> = emptyList(),
    val linked: List<RecordWithMedia> = emptyList(),
    val nearbyCount: Int = 0,
) {
    val isEmpty: Boolean get() = sameJourney.isEmpty() && samePlace.isEmpty() && linked.isEmpty()

    companion object {
        const val SAME_PLACE_RADIUS_M = 200
        const val LIMIT = 12

        /**
         * [target] 과 이어지는 기록을 [all] 에서 고른다.
         *
         * @param all 삭제되지 않은 전체 기록
         */
        fun of(target: RecordWithMedia, all: List<RecordWithMedia>): RelatedRecords {
            val me = target.record
            val others = all.filter { it.record.recordId != me.recordId }
            val used = HashSet<String>()
            fun List<RecordWithMedia>.claim(): List<RecordWithMedia> = filter { used.add(it.record.recordId) }.take(LIMIT)

            val journey = me.journeyId?.let { id ->
                others.filter { it.record.journeyId == id }.sortedBy { it.record.createdAt }.claim()
            }.orEmpty()

            val lat = me.latitude
            val lng = me.longitude
            val placeZone = if (lat != null && lng != null) NearPlace("", lat, lng, SAME_PLACE_RADIUS_M) else null
            val place = placeZone?.let { zone ->
                others.filter { zone.contains(it.record.latitude, it.record.longitude) }.sortedByDescending { it.record.createdAt }.claim()
            }.orEmpty()

            val code = me.barcodeValue?.takeIf { it.isNotBlank() }
            val tags = me.tags.filter { it.isNotBlank() }.toSet()
            val linked = if (code == null && tags.isEmpty()) {
                emptyList()
            } else {
                others.filter { o ->
                    (code != null && (o.record.barcodeValue == code || o.record.ocrText?.contains(code) == true)) ||
                        o.record.tags.any { it in tags }
                }.sortedByDescending { it.record.createdAt }.claim()
            }

            val nearbyZone = if (lat != null && lng != null) NearPlace("", lat, lng) else null
            val nearby = nearbyZone?.let { zone -> others.count { zone.contains(it.record.latitude, it.record.longitude) } } ?: 0

            return RelatedRecords(journey, place, linked, nearby)
        }
    }
}

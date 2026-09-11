package com.naury.trubbi.feature.share

import com.naury.trubbi.R
import com.naury.trubbi.core.i18n.purposeLabel
import com.naury.trubbi.core.ui.StringProvider
import com.naury.trubbi.core.util.DateUtils
import com.naury.trubbi.data.local.RecordWithMedia
import com.naury.trubbi.domain.memo.MemoContent

/**
 * 기록을 다른 앱으로 보낼 때 붙는 텍스트를 만든다.
 *
 * 브랜딩 문구는 넣지 않는다. 받는 쪽(화주·차주)에게 필요한 건 언제·어디서·무슨 일이었는지뿐이다.
 */
object ShareUtils {

    /** 공유 본문 — 시간·용도·바코드·위치·메모·태그 중 값이 있는 것만. */
    fun buildText(
        strings: StringProvider,
        item: RecordWithMedia,
        includeLocation: Boolean = true,
    ): String {
        val record = item.record
        return buildString {
            appendLine(DateUtils.formatDateTime(record.createdAt))
            appendLine(strings[R.string.share_field_purpose, purposeLabel(strings, record.purpose)])
            record.barcodeValue?.takeIf { it.isNotBlank() }
                ?.let { appendLine(strings[R.string.share_field_barcode, it]) }
            if (includeLocation) {
                record.locationName?.takeIf { it.isNotBlank() }
                    ?.let { appendLine(strings[R.string.share_field_location, it]) }
            }
            // 리치 텍스트 메모는 HTML 이라 그대로 붙이면 태그가 보인다.
            record.memo?.takeIf { it.isNotBlank() && !MemoContent.isRich(it) }
                ?.let { appendLine(strings[R.string.share_field_memo, it]) }
            if (record.tags.isNotEmpty()) {
                appendLine(strings[R.string.share_field_tags, record.tags.joinToString(", ") { "#$it" }])
            }
        }.trim()
    }

    /**
     * 사진에 새길 워터마크 줄.
     *
     * 받는 앱이 본문 텍스트와 이미지를 함께 전달하지 못하는 경우가 많아, 최소한의 정보는
     * 사진 자체에 남겨 둔다.
     */
    fun watermarkLines(item: RecordWithMedia, includeLocation: Boolean = true): List<String> {
        val record = item.record
        return buildList {
            add(DateUtils.formatDateTime(record.createdAt))
            if (includeLocation) {
                val place = record.locationName?.takeIf { it.isNotBlank() }
                val lat = record.latitude
                val lng = record.longitude
                when {
                    place != null -> add(place)
                    lat != null && lng != null -> add("%.5f, %.5f".format(lat, lng))
                }
            }
        }
    }
}

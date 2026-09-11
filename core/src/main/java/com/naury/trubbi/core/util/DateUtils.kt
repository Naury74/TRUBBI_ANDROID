package com.naury.trubbi.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 앱 전체가 같은 형식으로 날짜를 쓰기 위한 모음.
 *
 * 저장은 항상 ISO(yyyy-MM-dd) 나 epoch millis 로 하고, 사람이 보는 문자열은 여기서만 만든다.
 */
object DateUtils {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    val isoDate: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun todayIso(): String = LocalDate.now(zone).format(isoDate)

    fun now(): Long = System.currentTimeMillis()

    fun formatDateTime(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone)
            .format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm", Locale.getDefault()))

    fun formatDate(iso: String): String = iso.replace('-', '.')

    /** 기본 파일명을 만들 때 쓰는 짧은 "MM.dd HH:mm". */
    fun shortStamp(epochMillis: Long = now()): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone)
            .format(DateTimeFormatter.ofPattern("MM.dd HH:mm", Locale.getDefault()))
}

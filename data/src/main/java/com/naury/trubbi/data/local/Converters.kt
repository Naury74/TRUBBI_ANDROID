package com.naury.trubbi.data.local

import androidx.room.TypeConverter

/**
 * Room 타입 컨버터.
 *
 * 태그 목록은 별도 테이블 없이 줄바꿈으로 이어 한 컬럼에 저장한다. 태그 단위 조인 조회가
 * 없고 기록당 개수도 적어 정규화 이득이 없다.
 */
class Converters {

    @TypeConverter
    fun fromTags(value: List<String>): String =
        value.map { it.trim() }.filter { it.isNotBlank() }.joinToString("\n")

    @TypeConverter
    fun toTags(value: String): List<String> =
        if (value.isBlank()) {
            emptyList()
        } else {
            value.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        }
}

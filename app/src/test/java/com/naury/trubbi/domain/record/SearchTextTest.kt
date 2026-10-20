package com.naury.trubbi.domain.record

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 기록 검색의 글자 맞추기 규칙 — 띄어쓰기 무시, 초성, 낱말별 AND. */
class SearchTextTest {

    private val hay = "서울특별시청 서울 중구 세종대로 110 중구 명동 인수증"

    @Test
    fun `빈 검색어는 모두 걸린다`() {
        assertThat(SearchText.matches(hay, "  ")).isTrue()
    }

    @Test
    fun `띄어쓰기가 달라도 찾는다`() {
        assertThat(SearchText.matches(hay, "세종 대로")).isTrue()
        assertThat(SearchText.matches("세종 대로 110", "세종대로")).isTrue()
    }

    @Test
    fun `낱말마다 어딘가에 있으면 걸리고 하나라도 없으면 안 걸린다`() {
        assertThat(SearchText.matches(hay, "명동 인수증")).isTrue()
        assertThat(SearchText.matches(hay, "명동 영수증")).isFalse()
    }

    @Test
    fun `초성으로 찾는다`() {
        assertThat(SearchText.matches(hay, "ㅅㅇㅌㅂㅅㅊ")).isTrue()
        assertThat(SearchText.matches(hay, "ㅁㄷ")).isTrue()
        assertThat(SearchText.matches(hay, "ㅂㅅ")).isTrue() // 특별시 → ㅌㅂㅅ 안의 ㅂㅅ
        assertThat(SearchText.matches(hay, "ㄱㄴㄷ")).isFalse()
    }

    @Test
    fun `대소문자를 가리지 않는다`() {
        assertThat(SearchText.matches("CJ대한통운 PoD", "cj pod")).isTrue()
    }

    @Test
    fun `초성 변환`() {
        assertThat(SearchText.choseong("서울 A1")).isEqualTo("ㅅㅇ A1")
        assertThat(SearchText.isChoseongQuery("ㅅㅇ")).isTrue()
        assertThat(SearchText.isChoseongQuery("서ㅇ")).isFalse()
    }
}

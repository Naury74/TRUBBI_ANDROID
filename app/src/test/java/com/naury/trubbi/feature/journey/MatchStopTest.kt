package com.naury.trubbi.feature.journey

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MatchStopTest {

    private val stops = listOf("평택 물류센터", "부산신항", "인천항 3부두")

    @Test
    fun `띄어쓰기가 달라도 찾는다`() {
        assertThat(matchStop(stops, "부산 신항") { it }).isEqualTo(1)
        assertThat(matchStop(stops, "평택물류센터") { it }).isEqualTo(0)
    }

    @Test
    fun `이름 일부만 말해도 찾는다`() {
        assertThat(matchStop(stops, "인천항") { it }).isEqualTo(2)
    }

    @Test
    fun `꼬리가 다르게 들려도 앞부분으로 찾는다`() {
        assertThat(matchStop(stops, "평택물류창고") { it }).isEqualTo(0)
    }

    @Test
    fun `한 글자나 없는 이름은 찾지 않는다`() {
        assertThat(matchStop(stops, "항") { it }).isNull()
        assertThat(matchStop(stops, "광주") { it }).isNull()
    }
}

package com.naury.trubbi.core.location

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaceNamesTest {

    @Test
    fun `지번 주소에서 구와 동을 뽑는다`() {
        assertThat(PlaceNames.region("서울 강남구 역삼동 737")).isEqualTo("강남구 역삼동")
        assertThat(PlaceNames.region("경기 성남시 분당구 삼평동 123")).isEqualTo("분당구 삼평동")
    }

    @Test
    fun `구가 없는 시는 시와 읍면동을 쓴다`() {
        assertThat(PlaceNames.region("경남 양산시 동면 금산리 1457-14")).isEqualTo("양산시 동면")
    }

    @Test
    fun `도로명 주소는 구만 남는다`() {
        // 그래서 위치를 잡을 때 행정동을 따로 받아 둔다.
        assertThat(PlaceNames.region("서울 중구 세종대로 110")).isEqualTo("중구")
    }

    @Test
    fun `찾아갈 이름은 도로명과 번지다`() {
        assertThat(PlaceNames.street("서울특별시 중구 한강대로 405")).isEqualTo("한강대로 405")
        assertThat(PlaceNames.street("경남 양산시 동면 금산리 1457-14")).isEqualTo("금산리 1457-14")
        assertThat(PlaceNames.street("명동")).isEqualTo("명동")
    }

    @Test
    fun `빈 주소는 null`() {
        assertThat(PlaceNames.region(null)).isNull()
        assertThat(PlaceNames.street("  ")).isNull()
    }
}

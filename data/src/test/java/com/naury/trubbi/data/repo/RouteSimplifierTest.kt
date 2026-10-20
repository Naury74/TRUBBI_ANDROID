package com.naury.trubbi.data.repo

import com.google.common.truth.Truth.assertThat
import com.naury.trubbi.data.local.RoutePointEntity
import org.junit.Test

class RouteSimplifierTest {

    // 위도 0.001도 ≈ 111m.
    private fun p(id: Long, lat: Double, lng: Double, sec: Long = id * 10) = RoutePointEntity(id, "j", 37.0 + lat, 127.0 + lng, sec * 1000)

    @Test
    fun `직선 위의 점은 지운다`() {
        val line = (0L..10L).map { p(it, it * 0.001, 0.0) }

        assertThat(RouteSimplifier.keep(line)).containsExactly(0L, 10L)
    }

    @Test
    fun `꺾이는 점은 남긴다`() {
        val corner = listOf(p(0, 0.0, 0.0), p(1, 0.001, 0.0), p(2, 0.002, 0.0), p(3, 0.002, 0.001), p(4, 0.002, 0.002))

        assertThat(RouteSimplifier.keep(corner)).containsExactly(0L, 2L, 4L)
    }

    @Test
    fun `시간이 크게 빈 곳의 앞뒤 점은 남긴다`() {
        val points = listOf(p(0, 0.0, 0.0, 0), p(1, 0.001, 0.0, 10), p(2, 0.002, 0.0, 1000), p(3, 0.003, 0.0, 1010))

        assertThat(RouteSimplifier.keep(points)).containsExactly(0L, 1L, 2L, 3L)
    }

    @Test
    fun `점이 둘 이하면 그대로`() {
        assertThat(RouteSimplifier.keep(listOf(p(0, 0.0, 0.0)))).containsExactly(0L)
    }
}

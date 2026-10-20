package com.naury.trubbi.domain.tracking

import com.google.common.truth.Truth.assertThat
import com.naury.trubbi.core.location.Fix
import org.junit.Test

class FixFilterTest {

    // 위도 0.001도 ≈ 111m.
    private fun fix(lat: Double, sec: Long, acc: Float? = 10f) = Fix(37.0 + lat, 127.0, acc, sec * 1000)

    @Test
    fun `정확도가 나쁜 좌표는 버린다`() {
        val filter = FixFilter()

        assertThat(filter.accept(fix(0.0, 0, acc = 400f), ageMs = 0)).isFalse()
        assertThat(filter.accept(fix(0.0, 0, acc = 20f), ageMs = 0)).isTrue()
    }

    @Test
    fun `오래된 좌표는 버린다`() {
        assertThat(FixFilter().accept(fix(0.0, 0), ageMs = 120_000)).isFalse()
    }

    @Test
    fun `순간이동한 좌표는 버린다`() {
        val filter = FixFilter()
        filter.accept(fix(0.0, 0), 0)

        // 10초에 1.1km → 약 400km/h.
        assertThat(filter.accept(fix(0.01, 10), 0)).isFalse()
        // 10초에 222m → 약 80km/h.
        assertThat(filter.accept(fix(0.002, 10), 0)).isTrue()
    }

    @Test
    fun `직전 점이 틀렸으면 연달아 버린 뒤 새 점을 받아들인다`() {
        val filter = FixFilter(maxRejectRun = 3)
        filter.accept(fix(0.0, 0), 0)

        // 실제 위치가 5km 떨어진 곳이었다면 계속 순간이동으로 보인다.
        repeat(3) { assertThat(filter.accept(fix(0.05, 1L + it), 0)).isFalse() }
        assertThat(filter.accept(fix(0.05, 4), 0)).isTrue()
    }

    @Test
    fun `거리 계산이 맞다`() {
        // 위도 1도 ≈ 111.2km.
        assertThat(distanceM(37.0, 127.0, 38.0, 127.0)).isWithin(200f).of(111_195f)
    }
}

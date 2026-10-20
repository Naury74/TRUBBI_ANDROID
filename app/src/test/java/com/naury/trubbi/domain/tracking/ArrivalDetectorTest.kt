package com.naury.trubbi.domain.tracking

import com.google.common.truth.Truth.assertThat
import com.naury.trubbi.core.location.Fix
import com.naury.trubbi.domain.tracking.ArrivalDetector.Event
import org.junit.Test

class ArrivalDetectorTest {

    private fun fix(sec: Long, kmh: Float? = 40f, acc: Float = 10f) =
        Fix(37.0, 127.0, acc, sec * 1000, speedMps = kmh?.div(3.6f))

    @Test
    fun `반경을 스쳐 지나가면 도착으로 보지 않는다`() {
        val d = ArrivalDetector()

        assertThat(d.update("a", 800f, 300, fix(0))).isEqualTo(Event.APPROACHING)
        assertThat(d.update("a", 250f, 300, fix(10, kmh = 60f))).isEqualTo(Event.NONE)
        assertThat(d.update("a", 400f, 300, fix(20, kmh = 60f))).isEqualTo(Event.NONE)
    }

    @Test
    fun `반경 안에서 멈추면 도착이다`() {
        val d = ArrivalDetector()
        d.update("a", 800f, 300, fix(0))

        assertThat(d.update("a", 120f, 300, fix(10, kmh = 3f))).isEqualTo(Event.NONE)
        assertThat(d.update("a", 110f, 300, fix(20, kmh = 2f))).isEqualTo(Event.ARRIVED)
    }

    @Test
    fun `속도를 몰라도 반경 안에 오래 머물면 도착이다`() {
        val d = ArrivalDetector(dwellMs = 45_000)

        assertThat(d.update("a", 100f, 300, fix(0, kmh = null))).isEqualTo(Event.NONE)
        assertThat(d.update("a", 100f, 300, fix(50, kmh = null))).isEqualTo(Event.ARRIVED)
    }

    @Test
    fun `처음부터 반경 안이면 곧 도착 예고를 하지 않는다`() {
        val d = ArrivalDetector()

        assertThat(d.update("a", 100f, 300, fix(0, kmh = 0f))).isEqualTo(Event.NONE)
        assertThat(d.update("a", 100f, 300, fix(10, kmh = 0f))).isEqualTo(Event.ARRIVED)
    }

    @Test
    fun `정확도가 나쁘면 판단을 미룬다`() {
        val d = ArrivalDetector()

        repeat(5) { assertThat(d.update("a", 100f, 300, fix(it * 10L, kmh = 0f, acc = 500f))).isEqualTo(Event.NONE) }
    }

    @Test
    fun `멀리 벗어났다가 돌아오면 다시 알린다`() {
        val d = ArrivalDetector()
        d.update("a", 100f, 300, fix(0, kmh = 0f))
        assertThat(d.update("a", 100f, 300, fix(10, kmh = 0f))).isEqualTo(Event.ARRIVED)

        d.update("a", 600f, 300, fix(100))
        d.update("a", 100f, 300, fix(200, kmh = 0f))
        assertThat(d.update("a", 100f, 300, fix(210, kmh = 0f))).isEqualTo(Event.ARRIVED)
    }

    @Test
    fun `정차지가 바뀌면 새로 판단한다`() {
        val d = ArrivalDetector()
        d.update("a", 800f, 300, fix(0))

        assertThat(d.update("b", 800f, 300, fix(10))).isEqualTo(Event.APPROACHING)
    }
}

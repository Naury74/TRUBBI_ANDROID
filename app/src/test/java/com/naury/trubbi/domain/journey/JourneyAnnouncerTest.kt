package com.naury.trubbi.domain.journey

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.naury.trubbi.R
import com.naury.trubbi.core.location.CapturedLocation
import com.naury.trubbi.core.ui.StringProvider
import com.naury.trubbi.core.weather.Weather
import com.naury.trubbi.core.weather.WeatherCondition
import com.naury.trubbi.core.weather.weatherFromCode
import com.naury.trubbi.data.repo.JourneyRepository.StopDraft
import com.naury.trubbi.domain.model.StopKind
import java.time.LocalTime
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 여정 안내 문구 조합 테스트.
 *
 * 운전 중에 듣는 유일한 안내라 조각이 빠지거나 겹치면 바로 티가 난다. 값이 없을 때 그 조각만
 * 빠지는지, 시간대·날씨 조건이 의도한 우선순위대로 걸리는지를 본다. 실제 문자열 리소스를
 * 그대로 써서 서식 인자 개수가 어긋나는 것도 함께 잡는다.
 */
@RunWith(RobolectricTestRunner::class)
class JourneyAnnouncerTest {

    private val strings = StringProvider(ApplicationProvider.getApplicationContext())
    private val announcer = JourneyAnnouncer(strings)

    private fun weather(condition: WeatherCondition) =
        Weather(temperatureC = 12, code = 0, condition = condition, isDay = true, labelRes = weatherFromCode(0, isDay = true, tempC = 12).labelRes)

    private fun stop(kind: StopKind, name: String) = StopDraft(kind, name)

    // ── 안전 문구 ─────────────────────────────────────────────────────────────

    @Test
    fun `조건이 없으면 안전 문구를 붙이지 않는다`() {
        val tip = announcer.safetyTip(weather = null, at = LocalTime.of(14, 0))
        assertThat(tip).isEmpty()
    }

    @Test
    fun `날씨 조건은 시간대보다 먼저 걸린다`() {
        // 새벽이면서 비 → 졸음이 아니라 빗길 문구가 나와야 한다.
        val tip = announcer.safetyTip(weather(WeatherCondition.RAIN), at = LocalTime.of(3, 0))
        assertThat(tip).isEqualTo(strings[R.string.safety_rain])
    }

    @Test
    fun `날씨가 없으면 시간대로 판단한다`() {
        assertThat(announcer.safetyTip(null, LocalTime.of(3, 0)))
            .isEqualTo(strings[R.string.safety_late_night])
        assertThat(announcer.safetyTip(null, LocalTime.of(23, 0)))
            .isEqualTo(strings[R.string.safety_night])
    }

    // ── 시작 인사 ─────────────────────────────────────────────────────────────

    @Test
    fun `이름을 저장해 두면 호칭을 붙인다`() {
        val withName = announcer.startMessage("김기사", null, LocalTime.of(14, 0))
        val without = announcer.startMessage(null, null, LocalTime.of(14, 0))

        assertThat(withName).contains("김기사")
        assertThat(without).doesNotContain("김기사")
    }

    @Test
    fun `이름이 공백이면 호칭 없이 읽는다`() {
        val blank = announcer.startMessage("   ", null, LocalTime.of(14, 0))
        val none = announcer.startMessage(null, null, LocalTime.of(14, 0))
        assertThat(blank).isEqualTo(none)
    }

    // ── 시작 안내 ─────────────────────────────────────────────────────────────

    @Test
    fun `출발지와 도착지가 모두 있으면 양쪽을 읽는다`() {
        val text = announcer.startAnnouncement(
            stops = listOf(stop(StopKind.PICKUP, "인천항"), stop(StopKind.DROPOFF, "부산항")),
            driverName = null,
            weather = null,
            here = null,
        )
        assertThat(text).contains("인천항")
        assertThat(text).contains("부산항")
    }

    @Test
    fun `도착지만 있으면 도착지만 읽는다`() {
        val text = announcer.startAnnouncement(
            stops = listOf(stop(StopKind.DROPOFF, "부산항")),
            driverName = null,
            weather = null,
            here = null,
        )
        assertThat(text).contains("부산항")
        assertThat(text).contains(strings[R.string.announce_route_to, "부산항"])
    }

    @Test
    fun `가운데 정차지는 경유지로 읽는다`() {
        val text = announcer.startAnnouncement(
            stops = listOf(
                stop(StopKind.PICKUP, "인천항"),
                stop(StopKind.DROPOFF, "대전물류"),
                stop(StopKind.DROPOFF, "부산항"),
            ),
            driverName = null,
            weather = null,
            here = null,
        )
        assertThat(text).contains(strings[R.string.announce_via, "대전물류"])
    }

    @Test
    fun `정차지가 없어도 안내가 성립한다`() {
        val text = announcer.startAnnouncement(emptyList(), null, null, null)
        assertThat(text).isNotEmpty()
        assertThat(text).contains(strings[R.string.announce_start, "", "", "", ""])
    }

    @Test
    fun `위치와 날씨가 없으면 그 조각만 빠진다`() {
        val bare = announcer.startAnnouncement(listOf(stop(StopKind.DROPOFF, "부산항")), null, null, null)
        val full = announcer.startAnnouncement(
            stops = listOf(stop(StopKind.DROPOFF, "부산항")),
            driverName = null,
            weather = weather(WeatherCondition.CLEAR),
            // 도로명 주소에는 동이 없어, 위치를 잡을 때 받아 둔 행정동 이름을 읽는다.
            here = CapturedLocation(37.5, 127.0, "서울특별시 중구 세종대로 110", region = "중구 명동"),
        )

        val locationPart = strings[R.string.announce_location, "중구 명동"]
        assertThat(bare).doesNotContain(locationPart)
        assertThat(full).contains(locationPart)
        assertThat(full).contains("12")
    }

    @Test
    fun `이름이 빈 정차지는 경로에서 뺀다`() {
        val text = announcer.startAnnouncement(
            stops = listOf(stop(StopKind.PICKUP, "  "), stop(StopKind.DROPOFF, "부산항")),
            driverName = null,
            weather = null,
            here = null,
        )
        assertThat(text).contains(strings[R.string.announce_route_to, "부산항"])
    }

    // ── 주소 줄이기 ───────────────────────────────────────────────────────────

    @Test
    fun `긴 주소는 구와 동만 읽는다`() {
        assertThat(announcer.shortAddress("경기도 성남시 분당구 삼평동 123")).isEqualTo("분당구 삼평동")
    }

    @Test
    fun `동이 없는 주소는 구만, 한 토막은 그대로 읽는다`() {
        assertThat(announcer.shortAddress("서울 중구 을지로")).isEqualTo("중구")
        assertThat(announcer.shortAddress("부산항")).isEqualTo("부산항")
    }
}

package com.naury.trubbi.data.local

import com.google.common.truth.Truth.assertThat
import com.naury.trubbi.domain.model.StopKind
import com.naury.trubbi.domain.model.StopPhase
import org.junit.Test

/**
 * 정차지 상태 계산 테스트.
 *
 * 진행 상태를 컬럼으로 저장하지 않고 도착/작업/완료 시각에서 매번 계산하기 때문에,
 * 이 유도 규칙이 틀어지면 화면·알림·위젯이 한꺼번에 어긋난다.
 */
class JourneyStopStateTest {

    @Test
    fun `시각이 채워진 순서대로 단계가 올라간다`() {
        assertThat(stop().phase).isEqualTo(StopPhase.MOVING)
        assertThat(stop(arrivedAt = 100).phase).isEqualTo(StopPhase.WAITING)
        assertThat(stop(arrivedAt = 100, workStartedAt = 200).phase).isEqualTo(StopPhase.WORKING)
        assertThat(stop(arrivedAt = 100, workStartedAt = 200, completedAt = 300).phase)
            .isEqualTo(StopPhase.DONE)
    }

    @Test
    fun `완료 시각이 있으면 앞 단계가 비어 있어도 완료로 본다`() {
        // 알림에서 곧바로 완료를 누르는 동선이 있어 중간 시각이 빌 수 있다.
        assertThat(stop(completedAt = 300).phase).isEqualTo(StopPhase.DONE)
    }

    @Test
    fun `대기 시간은 도착과 작업 시작 사이로 잰다`() {
        val waited = stop(arrivedAt = 1_000, workStartedAt = 61_000).waitingMs

        assertThat(waited).isEqualTo(60_000)
    }

    @Test
    fun `작업을 시작하기 전에는 대기 시간을 알 수 없다`() {
        assertThat(stop(arrivedAt = 1_000).waitingMs).isNull()
    }

    @Test
    fun `기기 시계가 뒤로 밀려도 대기 시간은 음수가 되지 않는다`() {
        val waited = stop(arrivedAt = 5_000, workStartedAt = 1_000).waitingMs

        assertThat(waited).isEqualTo(0)
    }

    @Test
    fun `이름이 없으면 주소를 표시명으로 쓴다`() {
        assertThat(stop(name = null, address = "부산 강서구 신항로 1").rawName)
            .isEqualTo("부산 강서구 신항로 1")
    }

    @Test
    fun `이름도 주소도 없으면 표시명이 없다`() {
        // 이 경우의 기본 문구는 core.i18n 에서 언어에 맞춰 채운다.
        assertThat(stop(name = " ", address = "").rawName).isNull()
    }

    @Test
    fun `현재 정차지는 아직 완료되지 않은 첫 번째다`() {
        val journey = journeyWith(
            stop(stopId = "a", seq = 0, completedAt = 10),
            stop(stopId = "c", seq = 2),
            stop(stopId = "b", seq = 1),
        )

        assertThat(journey.currentStop?.stopId).isEqualTo("b")
    }

    @Test
    fun `모든 정차지가 끝나야 여정을 완료할 수 있다`() {
        val running = journeyWith(stop(stopId = "a", completedAt = 10), stop(stopId = "b"))
        val finished = journeyWith(stop(stopId = "a", completedAt = 10), stop(stopId = "b", completedAt = 20))

        assertThat(running.allStopsDone).isFalse()
        assertThat(finished.allStopsDone).isTrue()
    }

    @Test
    fun `정차지가 하나도 없으면 완료 상태로 보지 않는다`() {
        // 빈손으로 시작한 여정이 곧바로 완료 가능해 보이면 안 된다.
        assertThat(journeyWith().allStopsDone).isFalse()
    }

    private fun stop(
        stopId: String = "stop",
        seq: Int = 0,
        kind: StopKind = StopKind.PICKUP,
        name: String? = "부산 신항",
        address: String? = null,
        arrivedAt: Long? = null,
        workStartedAt: Long? = null,
        completedAt: Long? = null,
    ) = JourneyStopEntity(
        stopId = stopId,
        journeyId = "journey",
        seq = seq,
        kind = kind.name,
        name = name,
        address = address,
        arrivedAt = arrivedAt,
        workStartedAt = workStartedAt,
        completedAt = completedAt,
        createdAt = 0,
    )

    private fun journeyWith(vararg stops: JourneyStopEntity) = JourneyWithStops(
        journey = JourneyEntity(journeyId = "journey", createdAt = 0, startedAt = 0),
        stops = stops.toList(),
    )
}

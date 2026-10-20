package com.naury.trubbi.feature.journey.planner

import com.google.common.truth.Truth.assertThat
import com.naury.trubbi.domain.model.StopKind
import org.junit.Test

/**
 * 경로 입력 상태 테스트.
 *
 * 여정 저장소는 첫 정차지가 ORIGIN 이면 도착·완료를 바로 찍는다. 그래서 출발지를 현재 위치로 뒀는지,
 * 다른 곳을 골랐는지에 따라 정차지 종류가 정확히 갈려야 한다.
 */
class RoutePlanTest {

    private val here = PlannedPlace("현재 위치", "서울 중구 한강대로 405", 37.5547, 126.9707)
    private val center = PlannedPlace("성남 물류센터", "경기 성남시 중원구", 37.43, 127.13)
    private val port = PlannedPlace("부산신항", "부산 강서구", 35.08, 128.82)

    @Test
    fun `출발지를 비워 두면 현재 위치가 ORIGIN 으로 들어간다`() {
        val drafts = RoutePlan(here = here, destination = port).toDrafts()

        assertThat(drafts.map { it.kind }).containsExactly(StopKind.ORIGIN, StopKind.DROPOFF).inOrder()
        assertThat(drafts.first().latitude).isEqualTo(here.latitude)
    }

    @Test
    fun `다른 출발지를 고르면 먼저 가서 싣는 상차지가 된다`() {
        val drafts = RoutePlan(here = here).put(PlanField.Origin, center).put(PlanField.Destination, port).toDrafts()

        assertThat(drafts.map { it.kind }).containsExactly(StopKind.PICKUP, StopKind.DROPOFF).inOrder()
        assertThat(drafts.first().name).isEqualTo("성남 물류센터")
    }

    @Test
    fun `현재 위치를 모르면 출발지 없이 목적지만 넣는다`() {
        val drafts = RoutePlan(here = null, destination = port).toDrafts()

        assertThat(drafts.map { it.kind }).containsExactly(StopKind.DROPOFF)
    }

    @Test
    fun `경유지는 목적지 앞에 순서대로 들어가고 종류를 바꿀 수 있다`() {
        val plan = RoutePlan(here = here, destination = port)
            .put(PlanField.Via(null), center)
            .toggleWaypointKind(0)

        assertThat(plan.toDrafts().map { it.kind }).containsExactly(StopKind.ORIGIN, StopKind.DROPOFF, StopKind.DROPOFF).inOrder()
        assertThat(plan.toDrafts()[1].name).isEqualTo("성남 물류센터")
    }

    @Test
    fun `경유지 칸을 다시 고르면 그 자리만 바뀐다`() {
        val plan = RoutePlan(destination = port)
            .put(PlanField.Via(null), center)
            .put(PlanField.Via(0), here)

        assertThat(plan.waypoints.map { it.place }).containsExactly(here)
    }

    @Test
    fun `바꾸기는 현재 위치를 목적지로 보낸다`() {
        val swapped = RoutePlan(here = here, destination = port).swap()

        assertThat(swapped.origin).isEqualTo(port)
        assertThat(swapped.destination).isEqualTo(here)
    }

    @Test
    fun `현재 위치도 목적지도 없으면 바꾸지 않는다`() {
        val plan = RoutePlan(here = null, destination = port)

        assertThat(plan.swap()).isEqualTo(plan)
        assertThat(RoutePlan(here = here).swap()).isEqualTo(RoutePlan(here = here))
    }

    @Test
    fun `추가 모드는 출발지를 넣지 않고 목적지 종류를 따른다`() {
        val drafts = RoutePlan(addMode = true, here = here, destination = center)
            .toggleDestinationKind()
            .toDrafts()

        assertThat(drafts.map { it.kind }).containsExactly(StopKind.PICKUP)
    }

    @Test
    fun `지도에는 좌표 있는 곳만 순서대로 찍는다`() {
        val nameOnly = PlannedPlace("이름만 아는 곳", null, null, null)
        val points = RoutePlan(here = here, destination = port).put(PlanField.Via(null), nameOnly).mapPoints()

        assertThat(points).containsExactly(here, port).inOrder()
    }

    @Test
    fun `거리 표기는 1km 미만은 m, 그 위는 km`() {
        assertThat(formatDistance(850)).isEqualTo("850m")
        assertThat(formatDistance(12_345)).isEqualTo("12.3km")
        assertThat(formatDistance(325_000)).isEqualTo("325km")
    }

    @Test
    fun `목적지가 있어야 출발할 수 있다`() {
        assertThat(RoutePlan(here = here).ready).isFalse()
        assertThat(RoutePlan(here = here, destination = port).ready).isTrue()
    }
}

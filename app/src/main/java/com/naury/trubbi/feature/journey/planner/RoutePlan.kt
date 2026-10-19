package com.naury.trubbi.feature.journey.planner

import com.naury.trubbi.data.repo.JourneyRepository.StopDraft
import com.naury.trubbi.domain.model.StopKind

/**
 * 경로에 넣은 장소 하나.
 *
 * @property latitude 좌표가 없으면(이름만 아는 곳) null. 여정 저장 때 주소로 다시 찾는다
 */
data class PlannedPlace(
    val name: String,
    val address: String?,
    val latitude: Double?,
    val longitude: Double?,
)

/** 경유지 하나. 상차/하차를 고른다. */
data class Waypoint(val place: PlannedPlace, val kind: StopKind = StopKind.PICKUP)

/** 지금 장소를 고르고 있는 칸. */
sealed interface PlanField {
    data object Origin : PlanField

    data object Destination : PlanField

    /** 경유지 칸. [index] 가 null 이면 새로 끼워 넣는다(목적지 바로 앞). */
    data class Via(val index: Int?) : PlanField
}

/**
 * 티맵식 경로 입력의 상태. 화면과 떼어 둬 그대로 단위 테스트한다.
 *
 * 출발지는 비워 두면 "현재 위치"다. 정차지를 추가하는 모드([addMode])에서는 출발지가 없고, 목적지 칸이
 * "추가할 곳"이 된다.
 *
 * @property origin 사용자가 고른 출발지. null 이면 현재 위치
 * @property here 현재 위치(측위가 끝나야 채워진다). 출발지가 현재 위치일 때 그 좌표·주소로 쓴다
 * @property destinationKind 목적지를 상차지/하차지 중 무엇으로 넣을지. 새 여정은 하차지가 기본이다
 */
data class RoutePlan(
    val addMode: Boolean = false,
    val origin: PlannedPlace? = null,
    val here: PlannedPlace? = null,
    val waypoints: List<Waypoint> = emptyList(),
    val destination: PlannedPlace? = null,
    val destinationKind: StopKind = StopKind.DROPOFF,
) {
    /** 출발 버튼을 누를 수 있는지. 목적지가 있어야 한다. */
    val ready: Boolean get() = destination != null

    /** [field] 칸에 [place] 를 넣는다. */
    fun put(field: PlanField, place: PlannedPlace): RoutePlan = when (field) {
        PlanField.Origin -> copy(origin = place)
        PlanField.Destination -> copy(destination = place)
        is PlanField.Via -> {
            val i = field.index
            if (i != null && i in waypoints.indices) {
                copy(waypoints = waypoints.toMutableList().also { it[i] = it[i].copy(place = place) })
            } else {
                copy(waypoints = waypoints + Waypoint(place))
            }
        }
    }

    /** 출발지를 현재 위치로 되돌린다. */
    fun useHereAsOrigin(): RoutePlan = copy(origin = null)

    fun toggleDestinationKind(): RoutePlan =
        copy(destinationKind = if (destinationKind == StopKind.DROPOFF) StopKind.PICKUP else StopKind.DROPOFF)

    fun removeWaypoint(index: Int): RoutePlan = copy(waypoints = waypoints.filterIndexed { i, _ -> i != index })

    fun toggleWaypointKind(index: Int): RoutePlan = copy(
        waypoints = waypoints.mapIndexed { i, w ->
            if (i == index) w.copy(kind = if (w.kind == StopKind.PICKUP) StopKind.DROPOFF else StopKind.PICKUP) else w
        },
    )

    /**
     * 출발지와 목적지를 맞바꾼다. 출발지가 현재 위치였으면 현재 위치가 목적지가 된다.
     * 현재 위치를 아직 모르면 바꾸지 않는다.
     */
    fun swap(): RoutePlan {
        val dest = destination ?: return this
        val from = origin ?: here ?: return this
        return copy(origin = dest, destination = from)
    }

    /** 지도에 찍을 순서대로의 장소(출발 → 경유 → 도착). 좌표 없는 곳은 빠진다. */
    fun mapPoints(): List<PlannedPlace> = buildList {
        if (!addMode) (origin ?: here)?.let(::add)
        waypoints.forEach { add(it.place) }
        destination?.let(::add)
    }.filter { it.latitude != null && it.longitude != null }

    /**
     * 여정 저장용 정차지 목록.
     *
     * 출발지가 현재 위치면 ORIGIN(도착·완료가 바로 찍힌다)이고, 다른 곳을 골랐으면 먼저 가서 실어야 하는 곳이므로
     * PICKUP 이다. 목적지는 DROPOFF 다. 추가 모드에서는 출발지를 넣지 않는다.
     */
    fun toDrafts(): List<StopDraft> = buildList {
        if (!addMode) {
            val o = origin
            when {
                o != null -> add(o.toDraft(StopKind.PICKUP))
                here != null -> add(here.toDraft(StopKind.ORIGIN))
            }
        }
        waypoints.forEach { add(it.place.toDraft(it.kind)) }
        destination?.let { add(it.toDraft(destinationKind)) }
    }

    private fun PlannedPlace.toDraft(kind: StopKind) = StopDraft(kind, name, address, latitude, longitude)
}

package com.naury.trubbi.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.naury.trubbi.domain.model.StopKind
import com.naury.trubbi.domain.model.StopPhase

/**
 * 여정 — 첫 상차부터 마지막 하차까지의 운송 한 건.
 *
 * 날짜에 묶이지 않는다. [startedAt] 부터 [completedAt] 까지 이어지며, 자정을 넘겨도 한 여정이다.
 *
 * @property journeyId UUID
 * @property freightAmount 운임(원). 나중에 순수익 요약에 쓴다
 * @property totalDistanceM 추적 서비스가 누적한 총 주행 거리(m)
 */
@Entity(tableName = "journeys")
data class JourneyEntity(
    @PrimaryKey val journeyId: String,
    val title: String? = null,
    val cargoName: String? = null,
    val vehicleNumber: String? = null,
    val clientName: String? = null,
    val freightAmount: Long? = null,
    val memo: String? = null,
    val createdAt: Long,
    val startedAt: Long,
    val completedAt: Long? = null,
    val isCanceled: Boolean = false,
    val totalDistanceM: Long = 0L,
) {
    /** 아직 진행 중인 여정인지. */
    val isActive: Boolean get() = completedAt == null && !isCanceled
}

/**
 * 여정 안의 정차지 하나 — 출발지·상차지·하차지.
 *
 * 한 여정에 개수 제한 없이 [seq] 순서로 붙고, 진행 중에도 언제든 끼워 넣을 수 있다.
 * 진행 상태는 저장하지 않고 도착/작업/완료 시각에서 계산한다([phase]).
 *
 * @property stopId UUID
 * @property kind [StopKind] 이름 (ORIGIN / PICKUP / DROPOFF)
 * @property seq 여정 내 순서 (0부터)
 */
@Entity(tableName = "journey_stops", indices = [Index("journeyId")])
data class JourneyStopEntity(
    @PrimaryKey val stopId: String,
    val journeyId: String,
    val seq: Int,
    val kind: String,
    val name: String? = null,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val plannedAt: Long? = null,
    val arrivedAt: Long? = null,
    val workStartedAt: Long? = null,
    val completedAt: Long? = null,
    val memo: String? = null,
    val createdAt: Long,
) {
    val kindEnum: StopKind get() = StopKind.fromName(kind)

    val phase: StopPhase
        get() = when {
            completedAt != null -> StopPhase.DONE
            workStartedAt != null -> StopPhase.WORKING
            arrivedAt != null -> StopPhase.WAITING
            else -> StopPhase.MOVING
        }

    /** 대기 시간(도착 → 작업 시작, ms). 아직 잴 수 없으면 null. */
    val waitingMs: Long?
        get() {
            val a = arrivedAt ?: return null
            val s = workStartedAt ?: return null
            return (s - a).coerceAtLeast(0)
        }

    /**
     * 이름 또는 주소. 둘 다 비어 있으면 null 이고, 이때 표시 문구는
     * `JourneyStopEntity.displayName()` (core.i18n) 이 "상차지" 같은 기본값으로 채운다.
     */
    val rawName: String?
        get() = name?.takeIf { it.isNotBlank() } ?: address?.takeIf { it.isNotBlank() }
}

/**
 * 즐겨찾기 장소 — 홈과 여정 위저드에서 최근 정차지와 함께 바로 고를 수 있는 목적지.
 *
 * 특정 여정에 매이지 않는 독립 데이터다.
 */
@Entity(tableName = "favorite_places")
data class FavoritePlaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val createdAt: Long,
)

/**
 * 여정 추적 서비스가 남기는 GPS 자취 한 점.
 *
 * 지도 경로선과 위젯 미니맵이 이 점들을 이어 그린다.
 */
@Entity(tableName = "journey_route_points", indices = [Index("journeyId")])
data class RoutePointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val journeyId: String,
    val latitude: Double,
    val longitude: Double,
    val at: Long,
    val speedKmh: Float? = null,
)

/**
 * 여정 중 발생한 비용(주유·톨비·주차…).
 *
 * @property category [com.naury.trubbi.domain.model.ExpenseCategory] 이름
 * @property receiptRecordId 근거가 되는 영수증 기록 id (있을 때만)
 */
@Entity(tableName = "expenses", indices = [Index("journeyId")])
data class ExpenseEntity(
    @PrimaryKey val expenseId: String,
    val journeyId: String,
    val category: String,
    val amount: Long,
    val usedAt: Long,
    val vendorName: String? = null,
    val paymentMethod: String? = null,
    val receiptRecordId: String? = null,
    val memo: String? = null,
    val createdAt: Long,
)

/**
 * 여정 중 발생한 문제(파손·지연·분쟁…).
 *
 * @property type [com.naury.trubbi.domain.model.IssueType] 이름
 * @property status [com.naury.trubbi.domain.model.IssueStatus] 이름
 */
@Entity(tableName = "journey_issues", indices = [Index("journeyId")])
data class IssueEntity(
    @PrimaryKey val issueId: String,
    val journeyId: String,
    val stopId: String? = null,
    val type: String,
    val status: String,
    val occurredAt: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val description: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * 타임라인 한 줄 — 여정에서 의미 있는 순간이 생길 때마다 자동으로 쌓인다.
 *
 * @property type [com.naury.trubbi.domain.model.JourneyEventType] 이름
 * @property title 저장 시점 언어로 굳어진 표시 문구
 */
@Entity(tableName = "journey_events", indices = [Index("journeyId")])
data class JourneyEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val journeyId: String,
    val type: String,
    val at: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    /**
     * 버전 7 이전에 저장된 완성 문구.
     *
     * 그때는 저장 시점의 언어로 번역해 넣었기 때문에, 앱 언어를 바꿔도 지난 기록은 옛 언어로
     * 남는다. 새 행은 비워 두고 [titleArg]·[titleAmount] 와 [type] 으로 표시할 때 만든다.
     */
    val title: String,
    /**
     * 제목 서식에 넣을 열거형 값의 이름 — 번역하지 않은 원본.
     *
     * 종류에 따라 읽는 열거형이 다르다: 정차지 이벤트는 StopKind, 이슈는 IssueType,
     * 비용은 ExpenseCategory, 기록 연결은 EvidenceCategory.
     */
    val titleArg: String? = null,
    /** 열거형이 아닌 인자. 지금은 비용 이벤트의 금액에만 쓴다. */
    val titleAmount: Long? = null,
    val description: String? = null,
    val relatedStopId: String? = null,
    val relatedRecordId: String? = null,
    val relatedExpenseId: String? = null,
    val relatedIssueId: String? = null,
)

/**
 * 여정 + 순서대로 정렬된 정차지 — 대부분의 화면이 관찰하는 단위.
 */
data class JourneyWithStops(
    @Embedded val journey: JourneyEntity,
    @Relation(parentColumn = "journeyId", entityColumn = "journeyId")
    val stops: List<JourneyStopEntity>,
) {
    val orderedStops: List<JourneyStopEntity> get() = stops.sortedBy { it.seq }

    /** 지금 향하고 있는(또는 작업 중인) 정차지 — 아직 완료되지 않은 첫 번째. */
    val currentStop: JourneyStopEntity? get() = orderedStops.firstOrNull { it.completedAt == null }

    /** 모든 정차지가 끝났는지 — 여정을 완료할 수 있는 상태. */
    val allStopsDone: Boolean get() = stops.isNotEmpty() && stops.all { it.completedAt != null }
}

package com.naury.trubbi.data.repo

import com.naury.trubbi.core.location.LocationProvider
import com.naury.trubbi.core.ui.StringProvider
import com.naury.trubbi.data.DataChangeNotifier
import com.naury.trubbi.data.i18n.displayName
import com.naury.trubbi.data.local.ExpenseEntity
import com.naury.trubbi.data.local.FavoritePlaceEntity
import com.naury.trubbi.data.local.IssueEntity
import com.naury.trubbi.data.local.JourneyDao
import com.naury.trubbi.data.local.JourneyEntity
import com.naury.trubbi.data.local.JourneyEventEntity
import com.naury.trubbi.data.local.JourneyStopEntity
import com.naury.trubbi.data.local.JourneyWithStops
import com.naury.trubbi.data.local.RecordWithMedia
import com.naury.trubbi.data.local.RoutePointEntity
import com.naury.trubbi.domain.model.EvidenceCategory
import com.naury.trubbi.domain.model.ExpenseCategory
import com.naury.trubbi.domain.model.IssueStatus
import com.naury.trubbi.domain.model.IssueType
import com.naury.trubbi.domain.model.JourneyEventType
import com.naury.trubbi.domain.model.MissingEvidence
import com.naury.trubbi.domain.model.StopKind
import com.naury.trubbi.domain.model.StopPhase
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 여정(운송 한 건)의 모든 업무 규칙을 담당한다.
 *
 * 시작/완료 수명주기, 정차지의 도착 → 작업 → 완료 사이클, 타임라인 이벤트, 비용, 이슈,
 * 기록 연결까지. 진행 상태는 따로 저장하지 않고 언제나 타임스탬프에서 계산한다.
 *
 * @property dao 여정 관련 Room DAO
 * @property location 정차지 좌표를 채우는 현재 위치/지오코딩
 * @property appScope 화면 수명주기와 무관하게 살아야 하는 후속 작업용 스코프
 * @property notifier 저장이 끝난 뒤 바탕화면 위젯에 갱신을 알린다
 * @property strings 타임라인 이벤트 제목처럼 저장되는 문구의 다국어 처리
 */
@Singleton
class JourneyRepository @Inject constructor(
    private val dao: JourneyDao,
    private val location: LocationProvider,
    private val appScope: CoroutineScope,
    private val notifier: DataChangeNotifier,
    private val strings: StringProvider,
) {
    val activeJourney: Flow<JourneyWithStops?> = dao.observeActiveJourney()

    fun observeJourney(id: String): Flow<JourneyWithStops?> = dao.observeJourney(id)
    fun observeJourneys(): Flow<List<JourneyWithStops>> = dao.observeJourneys()
    fun observeEvents(journeyId: String) = dao.observeEvents(journeyId)
    fun observeExpenses(journeyId: String) = dao.observeExpenses(journeyId)
    fun observeIssues(journeyId: String) = dao.observeIssues(journeyId)
    fun observeJourneyRecords(journeyId: String): Flow<List<RecordWithMedia>> =
        dao.observeJourneyRecords(journeyId)
    fun observeTotalExpense(journeyId: String) = dao.observeTotalExpense(journeyId)

    suspend fun activeJourneyOnce(): JourneyWithStops? = dao.activeJourneyOnce()
    suspend fun recentStops(limit: Int = 8): List<JourneyStopEntity> = dao.recentStops(limit)

    /** 위저드 추천 칩 — 자주 가는 곳을 앞에 두고 최근 방문지로 채운다. */
    suspend fun suggestedStops(limit: Int = 10): List<JourneyStopEntity> {
        val frequent = dao.frequentStops(4)
        val recent = dao.recentStops(limit)
        return (frequent + recent).distinctBy { it.name }.take(limit)
    }

    fun observeRoutePoints(journeyId: String) = dao.observeRoutePoints(journeyId)
    suspend fun routePointsOnce(journeyId: String) = dao.routePointsOnce(journeyId)
    suspend fun lastRoutePoint(journeyId: String) = dao.lastRoutePoint(journeyId)

    // ── 즐겨찾기 장소 ──────────────────────────────────────────────────────────

    val favorites: Flow<List<FavoritePlaceEntity>> = dao.observeFavorites()
    suspend fun favoritesOnce() = dao.favoritesOnce()

    suspend fun isFavorite(name: String, lat: Double?, lng: Double?): Boolean =
        dao.favoriteByKey(name, lat, lng) != null

    suspend fun addFavorite(name: String, address: String?, lat: Double?, lng: Double?) {
        if (name.isBlank() || dao.favoriteByKey(name, lat, lng) != null) return
        dao.upsertFavorite(
            FavoritePlaceEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                address = address,
                latitude = lat,
                longitude = lng,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun removeFavorite(name: String, lat: Double?, lng: Double?) {
        dao.favoriteByKey(name, lat, lng)?.let { dao.deleteFavorite(it.id) }
    }

    /** 별표 토글. 반환값은 토글 후 상태. */
    suspend fun toggleFavorite(name: String, address: String?, lat: Double?, lng: Double?): Boolean {
        val existing = dao.favoriteByKey(name, lat, lng)
        return if (existing != null) {
            dao.deleteFavorite(existing.id)
            false
        } else {
            addFavorite(name, address, lat, lng)
            true
        }
    }

    // ── 주행 경로 ─────────────────────────────────────────────────────────────

    suspend fun addRoutePoint(
        journeyId: String,
        latitude: Double,
        longitude: Double,
        speedKmh: Float?,
        deltaM: Long,
    ) {
        dao.insertRoutePoint(
            RoutePointEntity(
                journeyId = journeyId,
                latitude = latitude,
                longitude = longitude,
                at = System.currentTimeMillis(),
                speedKmh = speedKmh,
            ),
        )
        if (deltaM > 0) dao.addDistance(journeyId, deltaM)

        // GPS 포인트마다 위젯을 다시 그리면 배터리·렌더 비용이 커서 15초로 제한한다.
        val now = System.currentTimeMillis()
        if (now - lastWidgetSync > WIDGET_SYNC_INTERVAL_MS) {
            lastWidgetSync = now
            syncWidgets()
        }
    }

    @Volatile
    private var lastWidgetSync = 0L

    // ── 여정 수명주기 ─────────────────────────────────────────────────────────

    /** 여정을 시작할 때 미리 등록해 두는 정차지. */
    data class StopDraft(
        val kind: StopKind,
        val name: String? = null,
        val address: String? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
    )

    /**
     * 새 여정을 시작한다.
     *
     * @param stops 비어 있어도 된다. 이 경우 도착을 기록할 때마다 정차지가 생성된다.
     * @return 생성된 여정 id
     */
    suspend fun startJourney(
        stops: List<StopDraft> = emptyList(),
        title: String? = null,
        cargoName: String? = null,
    ): String {
        val now = System.currentTimeMillis()
        val journeyId = UUID.randomUUID().toString()
        dao.upsertJourney(
            JourneyEntity(
                journeyId = journeyId,
                title = title?.takeIf { it.isNotBlank() },
                cargoName = cargoName?.takeIf { it.isNotBlank() },
                createdAt = now,
                startedAt = now,
            ),
        )
        stops.forEachIndexed { i, draft ->
            // 지도앱에서 공유받은 주소는 좌표가 없다. 도착 알림과 지도 핀을 위해 먼저 지오코딩한다.
            val geo = if (draft.latitude == null && !draft.address.isNullOrBlank()) {
                location.geocode(draft.address)
            } else {
                null
            }
            // 출발지는 '지금 떠나는 곳'이라 대기/완료 사이클이 없다. 시작과 동시에 완료로 둔다.
            val originDone = if (draft.kind.isOrigin) now else null
            dao.upsertStop(
                JourneyStopEntity(
                    stopId = UUID.randomUUID().toString(),
                    journeyId = journeyId,
                    seq = i,
                    kind = draft.kind.name,
                    name = draft.name?.takeIf { it.isNotBlank() },
                    address = draft.address ?: geo?.address,
                    latitude = draft.latitude ?: geo?.latitude,
                    longitude = draft.longitude ?: geo?.longitude,
                    arrivedAt = originDone,
                    workStartedAt = originDone,
                    completedAt = originDone,
                    createdAt = now,
                ),
            )
        }
        event(journeyId, JourneyEventType.JOURNEY_STARTED, now)
        syncWidgets()
        return journeyId
    }

    /** 정차지를 여정 맨 뒤에 붙인다. */
    suspend fun addStop(
        journeyId: String,
        kind: StopKind,
        name: String? = null,
        address: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
    ): String {
        val now = System.currentTimeMillis()
        val stopId = UUID.randomUUID().toString()
        val seq = dao.maxStopSeq(journeyId) + 1
        val geo = if (latitude == null && !address.isNullOrBlank()) location.geocode(address) else null
        dao.upsertStop(
            JourneyStopEntity(
                stopId = stopId,
                journeyId = journeyId,
                seq = seq,
                kind = kind.name,
                name = name?.takeIf { it.isNotBlank() },
                address = address ?: geo?.address,
                latitude = latitude ?: geo?.latitude,
                longitude = longitude ?: geo?.longitude,
                createdAt = now,
            ),
        )
        event(
            journeyId,
            JourneyEventType.STOP_ADDED,
            now,
            titleArg = kind.name,
            description = name ?: address,
            stopId = stopId,
        )
        syncWidgets()
        return stopId
    }

    suspend fun updateStop(stop: JourneyStopEntity) = dao.upsertStop(stop)

    suspend fun removeStop(stopId: String) = dao.deleteStop(stopId)

    /**
     * 정차지 순서를 [orderedStopIds] 대로 다시 매긴다.
     *
     * 이미 도착·완료한 정차지는 호출부에서 고정하고 아직 가지 않은 것만 넘기는 걸 전제로 한다.
     */
    suspend fun reorderStops(journeyId: String, orderedStopIds: List<String>) {
        val stops = dao.journeyWithStopsOnce(journeyId)?.stops ?: return
        val byId = stops.associateBy { it.stopId }
        orderedStopIds.forEachIndexed { i, id ->
            byId[id]?.let { if (it.seq != i) dao.upsertStop(it.copy(seq = i)) }
        }
    }

    /**
     * 도착 기록.
     *
     * GPS 를 기다리지 않고 곧바로 쓴다. 화면이 즉시 반응해야 하고, 좌표는 없어도
     * 도착 사실 자체가 더 중요하기 때문이다. 좌표/장소명은 뒤이어 백그라운드에서 채운다.
     */
    suspend fun arriveAtStop(stopId: String) {
        val stop = dao.stopOnce(stopId) ?: return
        if (stop.arrivedAt != null) return
        val now = System.currentTimeMillis()
        dao.upsertStop(stop.copy(arrivedAt = now))
        event(
            stop.journeyId,
            JourneyEventType.ARRIVED,
            now,
            titleArg = stop.kindEnum.name,
            description = stop.name,
            stopId = stopId,
        )
        if (stop.latitude == null) appScope.launch { fillStopLocation(stopId) }
    }

    private suspend fun fillStopLocation(stopId: String) {
        val stop = dao.stopOnce(stopId) ?: return
        if (stop.latitude != null) return
        val fix = location.capture() ?: return
        dao.upsertStop(
            stop.copy(
                latitude = fix.latitude,
                longitude = fix.longitude,
                address = stop.address ?: fix.address,
                name = stop.name ?: fix.address?.let(::shortPlaceName),
            ),
        )
    }

    /** 등록된 정차지 없이 도착했을 때 — 현재 위치로 정차지를 만들고 곧바로 도착까지 찍는다. */
    suspend fun arriveAtNewStop(journeyId: String, kind: StopKind): String {
        val stopId = addStop(journeyId, kind)
        arriveAtStop(stopId)
        return stopId
    }

    /** 작업 시작 — 대기 시간 측정을 끝낸다. */
    suspend fun startWork(stopId: String) {
        val stop = dao.stopOnce(stopId) ?: return
        if (stop.workStartedAt != null) return
        val now = System.currentTimeMillis()
        // 도착을 건너뛰고 바로 작업을 시작했다면 도착도 같이 찍는다.
        dao.upsertStop(stop.copy(arrivedAt = stop.arrivedAt ?: now, workStartedAt = now))
        event(
            stop.journeyId,
            JourneyEventType.WORK_STARTED,
            now,
            titleArg = stop.kindEnum.name,
            description = stop.displayName(strings),
            stopId = stopId,
        )
    }

    /** 작업 완료 — 건너뛴 앞 단계가 있으면 함께 채워 넣는다. */
    suspend fun completeStop(stopId: String) {
        val stop = dao.stopOnce(stopId) ?: return
        if (stop.completedAt != null) return
        val now = System.currentTimeMillis()
        dao.upsertStop(
            stop.copy(
                arrivedAt = stop.arrivedAt ?: now,
                workStartedAt = stop.workStartedAt ?: now,
                completedAt = now,
            ),
        )
        event(
            stop.journeyId,
            JourneyEventType.STOP_COMPLETED,
            now,
            titleArg = stop.kindEnum.name,
            description = stop.displayName(strings),
            stopId = stopId,
        )
    }

    /**
     * 현재 정차지를 한 단계만 진행시키고 기록된 단계를 돌려준다.
     *
     * 인자로 받은 스냅샷이 아니라 DB 를 다시 읽어 판단한다. 오래된 화면 상태나 연타로
     * 단계가 역행하지 않는다. 각 전이는 멱등하다.
     */
    suspend fun advanceStop(stop: JourneyStopEntity): StopPhase {
        val fresh = dao.stopOnce(stop.stopId) ?: return StopPhase.DONE
        val next = when (fresh.phase) {
            StopPhase.MOVING -> {
                arriveAtStop(fresh.stopId)
                StopPhase.WAITING
            }
            StopPhase.WAITING -> {
                startWork(fresh.stopId)
                StopPhase.WORKING
            }
            StopPhase.WORKING -> {
                completeStop(fresh.stopId)
                StopPhase.DONE
            }
            StopPhase.DONE -> StopPhase.DONE
        }
        syncWidgets()
        return next
    }

    /**
     * 여정을 끝내기 전 빠진 증빙을 찾는다. 빈 목록이면 바로 완료해도 된다.
     *
     * 상/하차 정차지가 있으면 그 작업 사진이 최소 한 장은 있어야 하고,
     * 이슈를 등록했다면 파손·오염 증빙 사진을 권한다.
     */
    suspend fun missingEvidence(journeyId: String): List<MissingEvidence> {
        val journey = dao.journeyWithStopsOnce(journeyId) ?: return emptyList()
        val captured = dao.journeyRecordsOnce(journeyId)
            .mapNotNull { it.record.evidenceCategory }
            .toSet()

        return buildList {
            if (journey.stops.any { it.kindEnum == StopKind.PICKUP } &&
                EvidenceCategory.PICKUP_COMPLETED.name !in captured &&
                EvidenceCategory.PICKUP_IN_PROGRESS.name !in captured
            ) {
                add(MissingEvidence.PICKUP_PHOTO)
            }

            if (journey.stops.any { it.kindEnum == StopKind.DROPOFF } &&
                EvidenceCategory.DROPOFF_COMPLETED.name !in captured &&
                EvidenceCategory.DROPOFF_IN_PROGRESS.name !in captured
            ) {
                add(MissingEvidence.DROPOFF_PHOTO)
            }

            if (dao.issuesOnce(journeyId).isNotEmpty() &&
                EvidenceCategory.DAMAGE.name !in captured
            ) {
                add(MissingEvidence.ISSUE_PHOTO)
            }
        }
    }

    suspend fun completeJourney(journeyId: String) {
        val now = System.currentTimeMillis()
        dao.completeJourney(journeyId, now)
        event(journeyId, JourneyEventType.JOURNEY_COMPLETED, now)
        syncWidgets()
    }

    suspend fun cancelJourney(journeyId: String) {
        dao.cancelJourney(journeyId)
        syncWidgets()
    }

    suspend fun updateJourney(journey: JourneyEntity) = dao.upsertJourney(journey)

    // ── 비용 ──────────────────────────────────────────────────────────────────

    suspend fun addExpense(
        journeyId: String,
        category: ExpenseCategory,
        amount: Long,
        usedAt: Long = System.currentTimeMillis(),
        vendorName: String? = null,
        paymentMethod: String? = null,
        receiptRecordId: String? = null,
        memo: String? = null,
    ): String {
        val now = System.currentTimeMillis()
        val expenseId = UUID.randomUUID().toString()
        dao.upsertExpense(
            ExpenseEntity(
                expenseId = expenseId,
                journeyId = journeyId,
                category = category.name,
                amount = amount,
                usedAt = usedAt,
                vendorName = vendorName?.takeIf { it.isNotBlank() },
                paymentMethod = paymentMethod,
                receiptRecordId = receiptRecordId,
                memo = memo?.takeIf { it.isNotBlank() },
                createdAt = now,
            ),
        )
        event(
            journeyId,
            JourneyEventType.EXPENSE_ADDED,
            usedAt,
            titleArg = category.name,
            titleAmount = amount,
            description = vendorName,
            expenseId = expenseId,
        )
        return expenseId
    }

    suspend fun deleteExpense(id: String) = dao.deleteExpense(id)

    // ── 이슈 ──────────────────────────────────────────────────────────────────

    suspend fun addIssue(
        journeyId: String,
        type: IssueType,
        description: String,
        stopId: String? = null,
        occurredAt: Long = System.currentTimeMillis(),
    ): String {
        val now = System.currentTimeMillis()
        val issueId = UUID.randomUUID().toString()
        val fix = location.capture()
        dao.upsertIssue(
            IssueEntity(
                issueId = issueId,
                journeyId = journeyId,
                stopId = stopId,
                type = type.name,
                status = IssueStatus.OPEN.name,
                occurredAt = occurredAt,
                latitude = fix?.latitude,
                longitude = fix?.longitude,
                address = fix?.address,
                description = description,
                createdAt = now,
                updatedAt = now,
            ),
        )
        event(
            journeyId, JourneyEventType.ISSUE_ADDED, occurredAt,
            titleArg = type.name,
            description = description,
            issueId = issueId,
            lat = fix?.latitude, lng = fix?.longitude, address = fix?.address,
        )
        return issueId
    }

    suspend fun updateIssue(issue: IssueEntity) =
        dao.upsertIssue(issue.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteIssue(id: String) = dao.deleteIssue(id)

    // ── 기록 연결 ─────────────────────────────────────────────────────────────

    suspend fun linkRecord(
        recordId: String,
        journeyId: String,
        stopId: String? = null,
        category: EvidenceCategory? = null,
        title: String? = null,
    ) {
        val now = System.currentTimeMillis()
        dao.linkRecord(recordId, journeyId, stopId, category?.name, now)
        event(
            journeyId,
            JourneyEventType.RECORD_LINKED,
            now,
            titleArg = category?.name,
            description = title,
            recordId = recordId,
            stopId = stopId,
        )
    }

    suspend fun unlinkRecord(recordId: String) =
        dao.linkRecord(recordId, null, null, null, System.currentTimeMillis())

    // ── 촬영 맥락과 "여정에 포함할까요?" 확인 ─────────────────────────────────

    /** 방금 저장됐고 아직 여정 포함 여부를 묻지 않은 기록. */
    data class PendingLink(val recordId: String, val title: String)

    /** 여정 진행 중에 단독 기록이 저장되면 채워진다. 홈이 이걸 보고 확인 시트를 띄운다. */
    val pendingAsk = MutableStateFlow<PendingLink?>(null)

    private var journeyCaptureArmed = false
    private var armedCategory: EvidenceCategory? = null

    /**
     * 여정 화면에서 촬영으로 넘어가기 직전에 호출한다.
     *
     * 이렇게 들어온 촬영은 묻지 않고 바로 여정에 연결된다.
     *
     * @param category 영수증 촬영처럼 분류가 이미 정해진 경우에만 지정
     */
    fun armJourneyCapture(category: EvidenceCategory? = null) {
        journeyCaptureArmed = true
        armedCategory = category
    }

    fun disarmJourneyCapture() {
        journeyCaptureArmed = false
        armedCategory = null
    }

    /**
     * 기록이 하나 생길 때마다 [RecordRepository] 가 호출한다.
     *
     * 여정 화면에서 시작된 촬영이면 분류를 스스로 정해 조용히 연결하고,
     * 그 밖의 저장은 "여정에 포함할까요?"를 한 번 묻는다.
     */
    suspend fun onRecordSaved(recordId: String, title: String?) {
        val active = dao.activeJourneyOnce() ?: run {
            disarmJourneyCapture()
            return
        }
        val armed = journeyCaptureArmed
        val fixedCategory = armedCategory
        disarmJourneyCapture()
        if (armed) {
            linkRecord(
                recordId = recordId,
                journeyId = active.journey.journeyId,
                stopId = active.currentStop?.stopId,
                category = fixedCategory ?: deriveCategory(active),
                title = title,
            )
        } else {
            pendingAsk.value = PendingLink(recordId, title.orEmpty())
        }
    }

    suspend fun resolvePendingAsk(include: Boolean) {
        val pending = pendingAsk.value ?: return
        pendingAsk.value = null
        if (!include) return
        val active = dao.activeJourneyOnce() ?: return
        linkRecord(
            recordId = pending.recordId,
            journeyId = active.journey.journeyId,
            stopId = active.currentStop?.stopId,
            category = deriveCategory(active),
            title = pending.title.ifBlank { null },
        )
    }

    /** 지금 찍은 사진에 붙일 증빙 분류 — 현재 정차지의 종류와 단계에서 유도한다. */
    fun deriveCategory(journey: JourneyWithStops): EvidenceCategory {
        val stop = journey.currentStop ?: return EvidenceCategory.ETC
        val pickup = stop.kindEnum == StopKind.PICKUP
        return when (stop.phase) {
            StopPhase.MOVING, StopPhase.WAITING ->
                if (pickup) EvidenceCategory.PICKUP_BEFORE else EvidenceCategory.DROPOFF_BEFORE
            StopPhase.WORKING ->
                if (pickup) EvidenceCategory.PICKUP_IN_PROGRESS else EvidenceCategory.DROPOFF_IN_PROGRESS
            StopPhase.DONE ->
                if (pickup) EvidenceCategory.PICKUP_COMPLETED else EvidenceCategory.DROPOFF_COMPLETED
        }
    }

    /**
     * 타임라인에 이벤트 한 줄을 남긴다.
     *
     * 제목은 저장하지 않는다. 종류와 [titleArg]·[titleAmount] 만 남기고, 표시할 때
     * `displayTitle` 이 현재 언어로 조립한다. 예전에는 번역된 문구를 그대로 넣어서
     * 앱 언어를 바꿔도 지난 기록이 옛 언어로 남았다.
     *
     * @param titleArg 제목 서식에 넣을 열거형 값의 이름(번역 전 원본)
     * @param titleAmount 열거형이 아닌 인자. 지금은 비용 금액에만 쓴다
     */
    private suspend fun event(
        journeyId: String,
        type: JourneyEventType,
        at: Long,
        titleArg: String? = null,
        titleAmount: Long? = null,
        description: String? = null,
        stopId: String? = null,
        recordId: String? = null,
        expenseId: String? = null,
        issueId: String? = null,
        lat: Double? = null,
        lng: Double? = null,
        address: String? = null,
    ) {
        dao.insertEvent(
            JourneyEventEntity(
                journeyId = journeyId,
                type = type.name,
                at = at,
                latitude = lat,
                longitude = lng,
                address = address,
                // 제목은 저장하지 않는다. 표시할 때 종류와 인자로 만든다.
                title = "",
                titleArg = titleArg,
                titleAmount = titleAmount,
                description = description,
                relatedStopId = stopId,
                relatedRecordId = recordId,
                relatedExpenseId = expenseId,
                relatedIssueId = issueId,
            ),
        )
    }

    /** "경기도 성남시 분당구 삼평동 123" → "삼평동 123" 정도의 짧은 표시명. */
    private fun shortPlaceName(address: String): String {
        val parts = address.split(" ").filter { it.isNotBlank() }
        return if (parts.size <= 2) address else parts.takeLast(2).joinToString(" ")
    }

    private fun syncWidgets() {
        runCatching { notifier.journeyChanged() }
    }

    private companion object {
        const val WIDGET_SYNC_INTERVAL_MS = 15_000L
    }
}

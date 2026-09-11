package com.naury.trubbi.domain.journey

import com.naury.trubbi.R
import com.naury.trubbi.core.i18n.label
import com.naury.trubbi.core.location.CapturedLocation
import com.naury.trubbi.core.location.LocationProvider
import com.naury.trubbi.core.location.PlaceCandidate
import com.naury.trubbi.core.ui.StringProvider
import com.naury.trubbi.core.weather.Weather
import com.naury.trubbi.data.JourneyControlSignal
import com.naury.trubbi.data.JourneyShareRequest
import com.naury.trubbi.data.i18n.displayName
import com.naury.trubbi.data.i18n.routeLabel
import com.naury.trubbi.data.local.JourneyStopEntity
import com.naury.trubbi.data.local.RecordWithMedia
import com.naury.trubbi.data.repo.JourneyRepository
import com.naury.trubbi.domain.model.JourneyEventType
import com.naury.trubbi.domain.model.MissingEvidence
import com.naury.trubbi.domain.model.StopKind
import com.naury.trubbi.domain.model.StopPhase
import com.naury.trubbi.domain.tracking.JourneyTracker
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 배웅 오버레이가 무엇을 축하하는지 — 아이콘 애니메이션을 고른다. */
enum class SendOffKind { START, ARRIVE, DONE, COMPLETE }

/** 여정 시작·도착·완료 순간의 전체 화면 연출. */
data class SendOff(
    val title: String,
    val subtitle: String?,
    val voice: String,
    val kind: SendOffKind = SendOffKind.START,
)

/** 도착 직후 바로 시작할지 묻는 팝업에 쓰는 정보. */
data class ArrivalPrompt(val kindLabel: String, val stopName: String)

/** 단계가 바뀐 직후 화면 가운데에 잠깐 띄우는 확인 문구. */
data class StageConfirm(val message: String, val started: Boolean)

/** 안내 문구에 필요한, 여정 밖에서 오는 값. */
data class JourneyAmbient(val driverName: String?, val weather: Weather?)

/** [JourneyAmbient] 를 호출 시점마다 새로 읽기 위한 공급자. */
fun interface JourneyAmbientSource {
    fun current(): JourneyAmbient
}

/**
 * 진행 중인 여정 하나의 상태와 단계 전환을 담당한다.
 *
 * 홈 화면, 플로팅 버튼, 알림이 모두 같은 여정을 건드리므로 전환 판단을 한 곳에 모았다.
 * 화면 분기는 항상 DB 에 기록된 단계로만 하고, 이 클래스는 그 결과를 화면이 그릴 수 있는
 * 오버레이·물음·확인 문구로 옮긴다.
 *
 * 수명은 생성자로 받은 [scope] 를 따른다. 뷰모델이 viewModelScope 를 넘기므로 화면이
 * 사라지면 구독과 진행 중 작업이 함께 정리된다.
 *
 * @property scope 상태 구독과 단계 전환 작업을 돌릴 스코프
 * @property ambient 안내 문구에 필요한 운전자 이름·날씨 (매 호출 시점의 값을 읽는다)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JourneyFlowController @AssistedInject constructor(
    @Assisted private val scope: CoroutineScope,
    @Assisted private val ambient: JourneyAmbientSource,
    private val journeyRepo: JourneyRepository,
    private val tracker: JourneyTracker,
    /** 위저드·정렬 시트가 직접 안내를 읽고 발화를 해석하는 데 쓴다. */
    val voice: JourneyVoice,
    private val announcer: JourneyAnnouncer,
    private val locationProvider: LocationProvider,
    private val controlSignal: JourneyControlSignal,
    private val shareRequest: JourneyShareRequest,
    private val strings: StringProvider,
) {
    /** 스코프를 인자로 받아야 하므로 Hilt 가 직접 만들지 못한다. 뷰모델이 이 팩토리로 만든다. */
    @AssistedFactory
    interface Factory {
        fun create(scope: CoroutineScope, ambient: JourneyAmbientSource): JourneyFlowController
    }

    // ── 진행 중 여정 ──────────────────────────────────────────────────────────

    /** 진행 중인 여정 하나. 홈의 단계 카드가 이걸 그린다. */
    val activeJourney = journeyRepo.activeJourney
        .stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIBE_KEEP_MS), null)

    /** 진행 중 여정에 붙은 기록 — 카드 요약 줄에 쓴다. */
    val records = journeyRepo.activeJourney
        .flatMapLatest { j ->
            if (j == null) flowOf(emptyList()) else journeyRepo.observeJourneyRecords(j.journey.journeyId)
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIBE_KEEP_MS), emptyList<RecordWithMedia>())

    /** 진행 중 여정의 총 비용. */
    val expenseTotal = journeyRepo.activeJourney
        .flatMapLatest { j ->
            if (j == null) flowOf(0L) else journeyRepo.observeTotalExpense(j.journey.journeyId)
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIBE_KEEP_MS), 0L)

    /** 완료한 여정 수 — 홈의 지난 여정 입구에 쓴다. */
    val completedCount = journeyRepo.observeJourneys()
        .map { list -> list.count { it.journey.completedAt != null } }
        .stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIBE_KEEP_MS), 0)

    /** 최근 완료 여정 몇 건 — 지난 여정 카드 안에서 바로 진입. */
    val recentJourneys = journeyRepo.observeJourneys()
        .map { list ->
            list.filter { it.journey.completedAt != null }
                .sortedByDescending { it.journey.completedAt }
                .take(RECENT_JOURNEY_LIMIT)
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIBE_KEEP_MS), emptyList())

    // ── 화면 연출 ─────────────────────────────────────────────────────────────

    /** 배웅 오버레이. 화면이 잠깐 보여 준 뒤 [clearSendOff] 를 부른다. */
    val sendOff = MutableStateFlow<SendOff?>(null)

    /** 도착 직후 띄우는 물음. null 이면 물음 없음. */
    val arrivalPrompt = MutableStateFlow<ArrivalPrompt?>(null)

    /** 단계 전환을 기록하는 동안 true — 연타를 막고 로딩 표시를 켠다. */
    val advancing = MutableStateFlow(false)

    /** 화면 가운데에 잠깐 뜨는 확인 문구. */
    val confirm = MutableStateFlow<StageConfirm?>(null)

    /** 빠진 증빙 목록. 비어 있지 않으면 홈이 그래도 완료할지 다시 묻는다. */
    val completeCheck = MutableStateFlow<List<MissingEvidence>?>(null)

    /** 오버레이만 닫는다. 클라우드 TTS 는 지연이 있어 함께 끊으면 말이 도중에 잘린다. */
    fun clearSendOff() {
        sendOff.value = null
    }

    fun clearConfirm() {
        confirm.value = null
    }

    fun dismissCompleteCheck() {
        completeCheck.value = null
    }

    // ── 시작·종료 ─────────────────────────────────────────────────────────────

    /**
     * 여정을 시작하고 GPS 추적을 켠다.
     *
     * 오버레이를 먼저 띄우고 DB 저장·위치 조회는 뒤로 미룬다. 시작 버튼을 누른 순간과
     * 화면이 반응하는 순간 사이에 위치 조회 지연이 끼지 않게 하기 위해서다.
     *
     * @param stops 미리 정한 정차지. 비어 있어도 된다(빈손 시작)
     * @param title 여정 제목. null 이면 리포지토리가 경로에서 만든다
     */
    fun startJourney(stops: List<JourneyRepository.StopDraft> = emptyList(), title: String? = null) {
        showSendOff(
            title = strings[R.string.journey_start_title],
            subtitle = null,
            speech = "",
            kind = SendOffKind.START,
        )
        scope.launch {
            val id = journeyRepo.startJourney(stops, title)
            tracker.start(id)
            val now = ambient.current()
            voice.speak(announcer.startAnnouncement(stops, now.driverName, now.weather, hereOrNull()))
        }
    }

    /**
     * 여정을 완료한다.
     *
     * @param force true 면 빠진 증빙을 무시하고 바로 완료한다. false 로 불렀는데 빠진 게 있으면
     *   완료하지 않고 [completeCheck] 에 목록을 채워 화면이 다시 묻게 한다
     */
    fun completeJourney(force: Boolean = false) {
        val journey = activeJourney.value ?: return
        scope.launch {
            if (!force) {
                val missing = journeyRepo.missingEvidence(journey.journey.journeyId)
                if (missing.isNotEmpty()) {
                    completeCheck.value = missing
                    return@launch
                }
            }
            completeCheck.value = null
            showSendOff(
                title = strings[R.string.journey_complete_title],
                subtitle = journey.routeLabel(strings),
                speech = strings[R.string.voice_journey_complete],
                kind = SendOffKind.COMPLETE,
            )
            journeyRepo.completeJourney(journey.journey.journeyId)
            tracker.stop()
        }
    }

    fun cancelJourney() {
        val journey = activeJourney.value ?: return
        scope.launch {
            journeyRepo.cancelJourney(journey.journey.journeyId)
            tracker.stop()
        }
    }

    // ── 단계 전환 ─────────────────────────────────────────────────────────────

    /**
     * 홈 카드의 주 동작 — 현재 정차지를 한 단계만 진행한다.
     *
     * 연타로 두 단계가 넘어가지 않도록 [advancing] 으로 잠근다. 전환 후 단계는 리포지토리가
     * 돌려준 값으로만 판단한다: 도착(WAITING)이면 작업 시작 여부를 묻고, 작업 시작·완료는
     * 확인 문구를 띄운다.
     */
    fun advanceCurrentStop() {
        if (advancing.value) return
        val journey = activeJourney.value ?: return
        val stop = journey.currentStop ?: return
        val lastIncomplete = journey.orderedStops.count { it.completedAt == null } == 1
        advancing.value = true
        scope.launch {
            try {
                when (journeyRepo.advanceStop(stop)) {
                    StopPhase.WAITING -> askArrival()
                    StopPhase.WORKING -> confirm.value = workStartedConfirm(stop.kindEnum.label(strings))
                    StopPhase.DONE -> announceStopDone(stop.kindEnum.label(strings), lastIncomplete)
                    else -> Unit
                }
            } finally {
                advancing.value = false
            }
        }
    }

    /** 등록된 정차지 없이 도착했을 때 — 현재 위치로 정차지를 만들고 바로 시작할지 묻는다. */
    fun arriveAtNewStop(kind: StopKind) {
        if (advancing.value) return
        val journey = activeJourney.value ?: return
        advancing.value = true
        scope.launch {
            try {
                journeyRepo.arriveAtNewStop(journey.journey.journeyId, kind)
                askArrival()
            } finally {
                advancing.value = false
            }
        }
    }

    /**
     * 도착 물음에 답한다.
     *
     * @param start true 면 곧바로 작업을 시작하고, false 면 물음만 닫고 대기 상태로 둔다
     */
    fun resolveArrival(start: Boolean) {
        val stop = activeJourney.value?.currentStop
        arrivalPrompt.value = null
        if (!start || stop == null) return
        scope.launch {
            journeyRepo.startWork(stop.stopId)
            confirm.value = workStartedConfirm(stop.kindEnum.label(strings))
        }
    }

    // ── 정차지 편집 ───────────────────────────────────────────────────────────

    /** 홈 카드의 + 노드로 정차지를 하나 붙인다. */
    fun addStop(kind: StopKind, name: String? = null) {
        val journey = activeJourney.value ?: return
        scope.launch { journeyRepo.addStop(journey.journey.journeyId, kind, name) }
    }

    /** 위저드 추가 모드가 음성·검색으로 고른 정차지들을 진행 중 여정에 붙인다. */
    fun addStops(drafts: List<JourneyRepository.StopDraft>) {
        val journey = activeJourney.value ?: return
        if (drafts.isEmpty()) return
        scope.launch {
            drafts.forEach { d ->
                journeyRepo.addStop(journey.journey.journeyId, d.kind, d.name, d.address, d.latitude, d.longitude)
            }
            val names = drafts.mapNotNull { it.name }.joinToString(", ")
            voice.speak(
                if (names.isNotBlank()) {
                    strings[R.string.voice_stops_added, names]
                } else {
                    strings[R.string.voice_stops_added_plain]
                },
            )
        }
    }

    /** 정차지 순서를 통째로 다시 매긴다. 완료/도착한 곳의 고정은 호출부가 책임진다. */
    fun reorderStops(orderedStopIds: List<String>) {
        val journey = activeJourney.value ?: return
        scope.launch { journeyRepo.reorderStops(journey.journey.journeyId, orderedStopIds) }
    }

    /**
     * 아직 도착하지 않은 정차지만 [orderedPendingIds] 순서로 재배치한다.
     *
     * 이미 도착·완료한 정차지는 원래 순서대로 앞에 고정한다. 지나온 경로가 뒤섞이면
     * 타임라인과 실제 이동 순서가 어긋나기 때문이다.
     */
    fun reorderPendingStops(orderedPendingIds: List<String>) {
        val journey = activeJourney.value ?: return
        val fixed = journey.orderedStops.filter { it.arrivedAt != null }.map { it.stopId }
        scope.launch { journeyRepo.reorderStops(journey.journey.journeyId, fixed + orderedPendingIds) }
    }

    /** 아직 시작 안 한 정차지 삭제(완료/도착한 정차지는 호출부에서 막는다). */
    fun removeStop(stopId: String) {
        scope.launch { journeyRepo.removeStop(stopId) }
    }

    // ── 장소 ──────────────────────────────────────────────────────────────────

    /** 위저드 추천 칩 — 자주 가는 곳과 최근 방문지. */
    suspend fun recentStops(): List<JourneyStopEntity> = journeyRepo.suggestedStops()

    /** 장소 즐겨찾기 — 홈/위저드에서 빠른 선택으로 노출. */
    val favorites = journeyRepo.favorites
        .stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIBE_KEEP_MS), emptyList())

    /** 이름과 좌표를 키로 별표를 토글한다. */
    fun toggleFavorite(name: String, address: String?, lat: Double?, lng: Double?) {
        scope.launch { journeyRepo.toggleFavorite(name, address, lat, lng) }
    }

    /** 입력·음성으로 들어온 장소명을 실제 후보로 바꾼다. */
    suspend fun searchPlaces(query: String): List<PlaceCandidate> = locationProvider.searchPlaces(query)

    /** 현재 위치와 주소. */
    suspend fun currentPlace(): CapturedLocation? = locationProvider.capture()

    // ── 기록 연결 ─────────────────────────────────────────────────────────────

    /** 방금 저장한 기록을 여정에 포함할지 묻는 요청. 리포지토리가 채운다. */
    val journeyAsk = journeyRepo.pendingAsk
        .stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIBE_KEEP_MS), null)

    fun resolveJourneyAsk(include: Boolean) {
        scope.launch { journeyRepo.resolvePendingAsk(include) }
    }

    /** 여정 카드에서 촬영으로 넘어가기 직전에 걸어 둔다. 저장된 기록이 자동으로 여정에 붙는다. */
    fun armJourneyCapture() = journeyRepo.armJourneyCapture()

    /** 홈에 돌아오면 남아 있는 플래그를 푼다. 카메라를 취소하고 나온 경우를 대비한 안전장치다. */
    fun disarmJourneyCapture() = journeyRepo.disarmJourneyCapture()

    // ── 외부에서 공유돼 들어온 장소 ───────────────────────────────────────────

    /** 지도앱·메신저에서 공유돼 들어와 정차지가 되기를 기다리는 장소. */
    val sharedPlace = shareRequest.place
        .stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * 공유받은 장소를 처리한다.
     *
     * 진행 중 여정이 있으면 정차지로 붙이고, 없으면 그 장소를 첫 정차지로 새 여정을 시작한다.
     *
     * @param kind null 이면 사용자가 닫은 것 — 대기 중인 장소만 비우고 끝낸다
     */
    fun resolveSharedPlace(kind: StopKind?) {
        val place = shareRequest.place.value ?: return
        shareRequest.place.value = null
        if (kind == null) return
        scope.launch {
            val active = journeyRepo.activeJourneyOnce()
            if (active != null) {
                journeyRepo.addStop(active.journey.journeyId, kind, place.name, place.address)
                return@launch
            }
            val id = journeyRepo.startJourney(
                listOf(JourneyRepository.StopDraft(kind, place.name, place.address)),
            )
            tracker.start(id)
            val now = ambient.current()
            showSendOff(
                title = strings[R.string.journey_start_title],
                subtitle = place.name,
                speech = announcer.startMessage(now.driverName, now.weather),
            )
        }
    }

    // ── 앱 밖에서 진행한 단계 ─────────────────────────────────────────────────

    private var lastSignalId = controlSignal.advance.value?.id ?: 0L

    init {
        // 플로팅 버튼·알림에서 진행한 단계도, 앱이 전면에 있으면 앱 안과 같은 확인 화면을 띄운다.
        scope.launch {
            controlSignal.advance.collect { ev ->
                if (ev == null || ev.id == lastSignalId) return@collect
                lastSignalId = ev.id
                // 백그라운드였다면 플로팅이 이미 알렸다. 복귀 시 지난 오버레이를 다시 띄우지 않는다.
                if (!controlSignal.appForeground.value) return@collect
                when (ev.phase) {
                    StopPhase.WAITING -> arrivalPrompt.value = ArrivalPrompt(ev.kindLabel, ev.stopName)
                    StopPhase.WORKING -> confirm.value = workStartedConfirm(ev.kindLabel)
                    StopPhase.DONE -> announceStopDone(ev.kindLabel, ev.lastOfJourney)
                    else -> Unit
                }
            }
        }
    }

    // ── 내부 ──────────────────────────────────────────────────────────────────

    private fun showSendOff(
        title: String,
        subtitle: String?,
        speech: String,
        kind: SendOffKind = SendOffKind.START,
    ) {
        sendOff.value = SendOff(title, subtitle, speech, kind)
        voice.speak(speech)
    }

    /** 전환 후 현재 정차지를 다시 읽어 물음을 만든다. 전환 도중 현재 정차지가 바뀌기 때문이다. */
    private suspend fun askArrival() {
        val current = journeyRepo.activeJourneyOnce()?.currentStop ?: return
        arrivalPrompt.value = ArrivalPrompt(current.kindEnum.label(strings), current.displayName(strings))
    }

    private fun workStartedConfirm(kindLabel: String) =
        StageConfirm(strings[R.string.confirm_work_started, kindLabel], started = true)

    private fun announceStopDone(kindLabel: String, lastStop: Boolean) {
        confirm.value = StageConfirm(strings[R.string.confirm_work_done, kindLabel], started = false)
        if (!lastStop) return
        showSendOff(
            title = strings[JourneyEventType.STOP_COMPLETED.titleRes, kindLabel],
            subtitle = strings[R.string.journey_all_stops_done_sub],
            speech = strings[R.string.voice_all_stops_done, kindLabel],
            kind = SendOffKind.DONE,
        )
    }

    /** 안내에 붙일 현재 위치. 제한 시간 안에 오지 않으면 위치 없이 안내한다. */
    private suspend fun hereOrNull(): CapturedLocation? =
        runCatching { withTimeoutOrNull(LOCATION_TIMEOUT_MS) { locationProvider.capture() } }.getOrNull()

    private companion object {
        /** 화면이 잠깐 사라져도 흐름을 유지할 시간. 탭 전환마다 다시 조회하지 않기 위한 값. */
        const val SUBSCRIBE_KEEP_MS = 5_000L
        const val LOCATION_TIMEOUT_MS = 3_000L
        const val RECENT_JOURNEY_LIMIT = 3
    }
}

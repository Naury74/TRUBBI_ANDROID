package com.naury.trubbi.feature.journey

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naury.trubbi.R
import com.naury.trubbi.core.i18n.label
import com.naury.trubbi.core.location.LocationProvider
import com.naury.trubbi.core.ui.StringProvider
import com.naury.trubbi.data.JourneyControlSignal
import com.naury.trubbi.data.i18n.displayName
import com.naury.trubbi.data.local.JourneyStopEntity
import com.naury.trubbi.data.repo.JourneyRepository
import com.naury.trubbi.domain.journey.ArrivalPrompt
import com.naury.trubbi.domain.journey.JourneyVoice
import com.naury.trubbi.domain.journey.StageConfirm
import com.naury.trubbi.domain.model.EvidenceCategory
import com.naury.trubbi.domain.model.ExpenseCategory
import com.naury.trubbi.domain.model.IssueType
import com.naury.trubbi.domain.model.MissingEvidence
import com.naury.trubbi.domain.model.StopKind
import com.naury.trubbi.domain.model.StopPhase
import com.naury.trubbi.domain.report.JourneyReportGenerator
import com.naury.trubbi.domain.tracking.JourneyTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 여정 상세 화면 상태 — 정차지 진행, 비용, 이슈, 기록, 리포트.
 *
 * 홈 카드와 같은 진행 동작을 제공하되, 이쪽은 지난 여정도 열어 볼 수 있어야 해서
 * 진행 중 여정만 다루는 홈과 별도로 둔다.
 */
@HiltViewModel
class JourneyDetailViewModel @Inject constructor(
    private val repo: JourneyRepository,
    private val tracker: JourneyTracker,
    private val reportGen: JourneyReportGenerator,
    /** 위저드·정렬 시트가 직접 쓰는 음성 창구. */
    val voice: JourneyVoice,
    private val controlSignal: JourneyControlSignal,
    private val location: LocationProvider,
    private val strings: StringProvider,
    savedState: SavedStateHandle,
) : ViewModel() {

    val journeyId: String = savedState.get<String>("journeyId").orEmpty()

    val journey = repo.observeJourney(journeyId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val events = repo.observeEvents(journeyId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val records = repo.observeJourneyRecords(journeyId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenseTotal = repo.observeTotalExpense(journeyId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    /** 추적 서비스가 남긴 GPS 자취 — 지도 경로선. */
    val routePoints = repo.observeRoutePoints(journeyId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Itemised expenses of this journey (비용 탭). */
    val expenses = repo.observeExpenses(journeyId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 이슈(파손/지연 등) — 지도에 위치 마커로 표시. */
    val issues = repo.observeIssues(journeyId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addExpense(
        category: ExpenseCategory,
        amount: Long,
        vendorName: String?,
        memo: String?,
        receiptRecordId: String? = null,
    ) {
        viewModelScope.launch {
            repo.addExpense(
                journeyId = journeyId,
                category = category,
                amount = amount,
                vendorName = vendorName,
                memo = memo,
                receiptRecordId = receiptRecordId,
            )
        }
    }

    fun deleteExpense(expenseId: String) {
        viewModelScope.launch { repo.deleteExpense(expenseId) }
    }

    /** 운송료 — drives the 예상 순수익 row. Null clears it. */
    fun setFreight(amount: Long?) {
        val j = journey.value?.journey ?: return
        viewModelScope.launch { repo.updateJourney(j.copy(freightAmount = amount)) }
    }

    fun addIssue(type: IssueType, description: String) {
        viewModelScope.launch {
            repo.addIssue(
                journeyId = journeyId,
                type = type,
                description = description,
                stopId = journey.value?.currentStop?.stopId,
            )
        }
    }

    /** "OO 작업을 바로 시작하시나요?" — 도착 직후 물음. */
    val arrivalPrompt = kotlinx.coroutines.flow.MutableStateFlow<ArrivalPrompt?>(null)
    val advancing = kotlinx.coroutines.flow.MutableStateFlow(false)
    val confirm = kotlinx.coroutines.flow.MutableStateFlow<StageConfirm?>(null)
    fun clearConfirm() {
        confirm.value = null
    }

    // 플로팅/알림에서 진행한 단계도 상세 화면이 떠 있으면 동일한 확인/물음을 띄운다.
    private var lastSignalId = controlSignal.advance.value?.id ?: 0L
    init {
        viewModelScope.launch {
            controlSignal.advance.collect { ev ->
                if (ev == null || ev.id == lastSignalId) return@collect
                lastSignalId = ev.id
                if (!controlSignal.appForeground.value) return@collect
                when (ev.phase) {
                    StopPhase.WAITING -> arrivalPrompt.value = ArrivalPrompt(ev.kindLabel, ev.stopName)
                    StopPhase.WORKING -> confirm.value = workStartedConfirm(ev.kindLabel)
                    StopPhase.DONE -> confirm.value = workDoneConfirm(ev.kindLabel)
                    else -> Unit
                }
            }
        }
    }

    fun advanceCurrentStop() {
        if (advancing.value) return
        val stop = journey.value?.currentStop ?: return
        advancing.value = true
        viewModelScope.launch {
            try {
                when (repo.advanceStop(stop)) {
                    StopPhase.WAITING ->
                        repo.activeJourneyOnce()?.currentStop?.let { current ->
                            arrivalPrompt.value =
                                ArrivalPrompt(current.kindEnum.label(strings), current.displayName(strings))
                        }

                    StopPhase.WORKING -> confirm.value = workStartedConfirm(stop.kindEnum.label(strings))
                    StopPhase.DONE -> confirm.value = workDoneConfirm(stop.kindEnum.label(strings))
                    else -> Unit
                }
            } finally {
                advancing.value = false
            }
        }
    }

    fun arriveAtNewStop(kind: StopKind) {
        if (advancing.value) return
        advancing.value = true
        viewModelScope.launch {
            try {
                repo.arriveAtNewStop(journeyId, kind)
                repo.activeJourneyOnce()?.currentStop?.let { current ->
                    arrivalPrompt.value =
                        ArrivalPrompt(current.kindEnum.label(strings), current.displayName(strings))
                }
            } finally {
                advancing.value = false
            }
        }
    }

    fun resolveArrival(start: Boolean) {
        val stop = journey.value?.currentStop
        arrivalPrompt.value = null
        if (start && stop != null) {
            viewModelScope.launch {
                repo.startWork(stop.stopId)
                confirm.value = workStartedConfirm(stop.kindEnum.label(strings))
            }
        }
    }

    private fun workStartedConfirm(kindLabel: String) =
        StageConfirm(strings[R.string.confirm_work_started, kindLabel], started = true)

    private fun workDoneConfirm(kindLabel: String) =
        StageConfirm(strings[R.string.confirm_work_done, kindLabel], started = false)

    fun addStop(kind: StopKind, name: String? = null) {
        viewModelScope.launch { repo.addStop(journeyId, kind, name) }
    }

    /** 위저드에서 고른 정차지들을 이 여정에 붙인다. 홈과 같은 흐름이다. */
    fun addStops(drafts: List<JourneyRepository.StopDraft>) {
        if (drafts.isEmpty()) return
        viewModelScope.launch {
            drafts.forEach { repo.addStop(journeyId, it.kind, it.name, it.address, it.latitude, it.longitude) }
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

    // ── 정차지 추가 화면용 — 홈과 같은 검색·위치·즐겨찾기 경로를 그대로 쓴다 ──────

    suspend fun searchPlaces(query: String, nearLat: Double?, nearLng: Double?) =
        location.searchPlaces(query, max = SEARCH_MAX, nearLat = nearLat, nearLng = nearLng)

    val favorites = repo.favorites.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleFavorite(name: String, address: String?, lat: Double?, lng: Double?) {
        viewModelScope.launch { repo.toggleFavorite(name, address, lat, lng) }
    }

    suspend fun currentPlace() = location.capture()

    /** 위저드 추천 칩 — 자주 가는 곳과 최근 방문지. */
    suspend fun suggestedStops() = repo.suggestedStops()

    fun renameStop(stop: JourneyStopEntity, name: String) {
        viewModelScope.launch { repo.updateStop(stop.copy(name = name.ifBlank { null })) }
    }

    fun toggleStopKind(stop: JourneyStopEntity) {
        val next = if (stop.kindEnum == StopKind.PICKUP) StopKind.DROPOFF else StopKind.PICKUP
        viewModelScope.launch { repo.updateStop(stop.copy(kind = next.name)) }
    }

    /** 아직 도착하지 않은 정차지만 지운다. */
    fun removeStop(stop: JourneyStopEntity) {
        if (stop.arrivedAt != null) return
        viewModelScope.launch { repo.removeStop(stop.stopId) }
    }

    fun renameJourney(title: String) {
        val j = journey.value?.journey ?: return
        viewModelScope.launch { repo.updateJourney(j.copy(title = title.ifBlank { null })) }
    }

    /** 카메라로 넘어가기 직전에 걸어 둔다. 저장된 기록이 이 여정에 자동으로 붙는다. */
    fun armJourneyCapture(category: EvidenceCategory? = null) = repo.armJourneyCapture(category)

    /** 화면에 들어오면 남아 있는 플래그를 푼다. 촬영을 취소하고 돌아온 경우를 위한 안전장치다. */
    fun disarmJourneyCapture() = repo.disarmJourneyCapture()

    /** 빠진 증빙 목록. 비어 있지 않으면 "그래도 완료할까요?"를 묻는다. */
    val completeCheck = MutableStateFlow<List<MissingEvidence>?>(null)
    fun dismissCompleteCheck() {
        completeCheck.value = null
    }

    fun completeJourney(force: Boolean = false) {
        viewModelScope.launch {
            if (!force) {
                val missing = repo.missingEvidence(journeyId)
                if (missing.isNotEmpty()) {
                    completeCheck.value = missing
                    return@launch
                }
            }
            completeCheck.value = null
            repo.completeJourney(journeyId)
            tracker.stop()
        }
    }

    fun cancelJourney() {
        viewModelScope.launch {
            repo.cancelJourney(journeyId)
            tracker.stop()
        }
    }

    // ── 리포트

    /** 리포트나 워터마크 묶음을 만드는 중인지. 사진이 많으면 수십 초가 걸려 연타를 막아야 한다. */
    val reportBusy = MutableStateFlow(false)

    /**
     * PDF 리포트를 만들고 공유 인텐트를 돌려준다.
     *
     * @param onReady 생성이 끝나면 공유 인텐트로 불린다. 실패하면 null
     */
    fun sharePdf(onReady: (Intent?) -> Unit) {
        if (reportBusy.value) return
        viewModelScope.launch {
            reportBusy.value = true
            val uri = reportGen.generatePdf(journeyId)
            reportBusy.value = false
            onReady(uri?.let { shareIntent(listOf(it), "application/pdf") })
        }
    }

    /**
     * 여정의 모든 사진에 워터마크를 굽고 여러 장 공유 인텐트를 돌려준다.
     *
     * @param onReady 생성이 끝나면 공유 인텐트로 불린다. 사진이 없으면 null
     */
    fun shareWatermarkedPhotos(onReady: (Intent?) -> Unit) {
        if (reportBusy.value) return
        viewModelScope.launch {
            reportBusy.value = true
            val uris = reportGen.watermarkedShareUris(journeyId)
            reportBusy.value = false
            onReady(if (uris.isEmpty()) null else shareIntent(uris, "image/jpeg"))
        }
    }

    private fun shareIntent(uris: List<Uri>, mime: String): Intent =
        if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uris.first())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mime
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
}

/** 장소 검색 한 번에 보여 줄 후보 수. 홈 경로 화면과 같다. */
private const val SEARCH_MAX = 12

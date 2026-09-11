package com.naury.trubbi.feature.journey

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.naury.trubbi.R
import com.naury.trubbi.core.i18n.label
import com.naury.trubbi.core.location.CapturedLocation
import com.naury.trubbi.core.location.PlaceCandidate
import com.naury.trubbi.core.voice.SpeechInputState
import com.naury.trubbi.core.voice.VoiceAction
import com.naury.trubbi.core.voice.VoiceContext
import com.naury.trubbi.core.voice.VoiceIntent
import com.naury.trubbi.core.voice.VoiceStopKind
import com.naury.trubbi.core.voice.rememberSpeechInput
import com.naury.trubbi.data.repo.JourneyRepository
import com.naury.trubbi.domain.model.StopKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 마법사가 묻는 단계. */
internal enum class WizardStep { DEST, ORIGIN, EXTRA }

/**
 * 마법사에서 고른 장소. 검색 후보일 수도, 직접 입력한 이름일 수도 있다.
 *
 * 좌표 없이도 정차지로 쓸 수 있다. 검색에 잡히지 않는 장소는 입력한 이름만으로 등록된다.
 */
internal data class PickedPlace(
    val name: String,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/**
 * 여정 시작 마법사의 상태와 음성 처리.
 *
 * 화면에서 분리한 이유는 이 클래스가 다루는 것이 화면 배치가 아니라 **대화 흐름**이기 때문이다.
 * 인식 → 의도 해석 → 검색 → 후보 확인 → 확정으로 이어지는 경로에 재질문·카운트다운·정정이
 * 얽혀 있어, 컴포저블 안에 두면 UI 트리와 상태 전이를 함께 읽어야 한다.
 *
 * 콜백은 [callbacks] 로 매 컴포지션마다 최신 값을 받는다. 객체 자체는 [remember] 로 유지되므로
 * 생성자에 람다를 직접 담으면 첫 컴포지션의 것이 그대로 굳는다.
 *
 * @property speech 인식기. 화면이 듣기 상태를 그리는 데도 함께 쓴다
 * @property addMode 진행 중 여정에 정차지만 추가하는 모드
 */
@Stable
internal class JourneyWizardState(
    private val context: Context,
    private val scope: CoroutineScope,
    val speech: SpeechInputState,
    val addMode: Boolean,
    initialDestination: JourneyRepository.StopDraft?,
    private val callbacks: () -> Callbacks,
) {
    /**
     * 마법사 밖으로 나가는 동작들.
     *
     * @property searchPlaces 장소 검색
     * @property currentPlace 현재 위치 캡처
     * @property interpret 발화를 의도로 해석
     * @property speakAwait 안내를 읽고 끝날 때까지 대기
     * @property stopVoice 진행 중 안내를 즉시 중단
     * @property onStart 시작 확정
     * @property onDismiss 닫기
     */
    class Callbacks(
        val searchPlaces: suspend (String) -> List<PlaceCandidate>,
        val currentPlace: suspend () -> CapturedLocation?,
        val interpret: suspend (String, VoiceContext) -> VoiceIntent,
        val speakAwait: suspend (String) -> Unit,
        val stopVoice: () -> Unit,
        val onStart: (List<JourneyRepository.StopDraft>) -> Unit,
        val onDismiss: () -> Unit,
    )

    /** 지금 묻고 있는 단계. */
    var step by mutableStateOf(
        when {
            addMode -> WizardStep.EXTRA
            initialDestination != null -> WizardStep.ORIGIN
            else -> WizardStep.DEST
        },
    )
        private set

    /** 확정된 정차지들. 화면에 보이는 순서가 곧 여정 순서다. */
    val planned = mutableStateListOf<Pair<StopKind, PickedPlace>>().apply {
        if (!addMode && initialDestination != null) {
            add(
                StopKind.DROPOFF to PickedPlace(
                    initialDestination.name ?: context.getString(R.string.wizard_default_destination),
                    initialDestination.address,
                    initialDestination.latitude,
                    initialDestination.longitude,
                ),
            )
        }
    }

    /** 경유지 단계에서 마지막으로 추가한 항목. 다음 안내 문구에 이름을 넣는 데 쓴다. */
    var lastAdded by mutableStateOf<Pair<StopKind, PickedPlace>?>(null)
        private set

    /** 경유지 단계에서 고른 상차·하차 구분. */
    var extraKind by mutableStateOf(StopKind.DROPOFF)

    /** 입력창 문자열. */
    var query by mutableStateOf("")

    /** 검색 결과. null 은 아직 검색하지 않은 상태, 빈 리스트는 결과 없음. */
    var candidates by mutableStateOf<List<PlaceCandidate>?>(null)
        private set

    /** 검색 진행 중. */
    var searching by mutableStateOf(false)
        private set

    /** 현재 위치를 잡는 중. */
    var locating by mutableStateOf(false)
        private set

    /** 음성으로 검색했는지. 결과가 나오면 몇 번째인지 음성으로 다시 묻는다. */
    var voiceMode by mutableStateOf(false)
        private set

    /** 후보 선택 질문을 이미 했는지. 같은 결과에 두 번 묻지 않는다. */
    var pickPrompted by mutableStateOf(false)

    /** 상차지 인식에 실패해 현재 위치 사용 여부를 묻는 중. */
    var askHere by mutableStateOf(false)
        private set

    /** 의도 해석 응답을 기다리는 중. */
    var thinking by mutableStateOf(false)
        private set

    /** 자동 시작 카운트다운이 도는 중. */
    var counting by mutableStateOf(false)
        private set

    /** 자동 시작까지 남은 초. */
    var countSec by mutableStateOf(0)
        private set

    /** 현재 위치로 자동 출발하기까지 남은 초. */
    var originCountSec by mutableStateOf(0)
        private set

    /** 지도에서 위치를 확인 중인 후보. null 이면 확인 단계가 아니다. */
    var confirmTarget by mutableStateOf<PickedPlace?>(null)

    /** 음성으로 읽어 준 것과 같은 안내 문구. 화면에도 함께 표시한다. */
    var guide by mutableStateOf("")
        private set

    /** 다른 안내가 떠 있지 않은 상태. 재시도 배너를 띄울지 판단한다. */
    fun idleForRetry(): Boolean =
        !speech.listening && !speech.processing && !askHere && !counting && !thinking

    /** 마지막 하차지. 안내 문구를 만들 때 쓴다. */
    fun destPlace(): PickedPlace? = planned.lastOrNull { it.first == StopKind.DROPOFF }?.second

    /** 출발지. 현재 위치(ORIGIN)이거나 첫 상차지(PICKUP)다. */
    fun originPlace(): PickedPlace? =
        planned.firstOrNull { it.first == StopKind.ORIGIN || it.first == StopKind.PICKUP }?.second

    // ── 검색·확정 ────────────────────────────────────────────────────────────

    /** [query] 로 장소를 검색한다. 이미 검색 중이면 무시한다. */
    fun runSearch() {
        val q = query.trim()
        if (q.isEmpty() || searching) return
        scope.launch {
            searching = true
            pickPrompted = false
            candidates = callbacks().searchPlaces(q)
            searching = false
        }
    }

    /** 입력·검색 상태를 초기화한다. 한 곳을 확정한 뒤 다음 단계로 넘어갈 때 호출한다. */
    fun resetInput() {
        query = ""
        candidates = null
        searching = false
        voiceMode = false
        pickPrompted = false
        askHere = false
    }

    /**
     * 장소를 확정하고 다음 단계로 넘어간다.
     *
     * 경유지는 마지막 하차지 **앞에** 끼운다. 최종 목적지가 항상 마지막이어야 순서가 맞다.
     */
    fun pick(place: PickedPlace) {
        speech.cancel()
        when (step) {
            WizardStep.DEST -> {
                planned.add(StopKind.DROPOFF to place)
                step = WizardStep.ORIGIN
            }

            WizardStep.ORIGIN -> {
                planned.add(0, StopKind.PICKUP to place)
                step = WizardStep.EXTRA
            }

            WizardStep.EXTRA -> {
                val pair = extraKind to place
                if (addMode || destPlace() == null) {
                    planned.add(pair)
                } else {
                    planned.add((planned.size - 1).coerceAtLeast(0), pair)
                }
                lastAdded = pair
            }
        }
        resetInput()
    }

    /** 좌표가 있으면 지도 확인 단계로 보내고, 이름만 있으면 곧장 확정한다. */
    fun requestConfirm(place: PickedPlace) {
        speech.cancel()
        if (place.latitude != null && place.longitude != null) {
            confirmTarget = place
        } else {
            pick(place)
        }
    }

    /** 목록에서 정차지를 뺀다. */
    fun remove(item: Pair<StopKind, PickedPlace>) {
        planned.remove(item)
        lastAdded = null
    }

    /** 드래그로 바뀐 순서를 그대로 반영한다. */
    fun reorder(newOrder: List<Pair<StopKind, PickedPlace>>) {
        planned.clear()
        planned.addAll(newOrder)
    }

    /** 현재 위치를 출발지로 쓴다. 출발지는 항상 맨 앞에 하나만 둔다. */
    fun useHere() {
        if (locating) return
        askHere = false
        originCountSec = 0
        scope.launch {
            speech.cancel()
            locating = true
            val here = callbacks().currentPlace()
            locating = false
            val place = here?.let {
                PickedPlace(
                    it.address?.let(::shortName) ?: context.getString(R.string.wizard_current_location),
                    it.address,
                    it.latitude,
                    it.longitude,
                )
            } ?: PickedPlace(context.getString(R.string.wizard_current_location))
            planned.removeAll { it.first == StopKind.ORIGIN }
            planned.add(0, StopKind.ORIGIN to place)
            if (step == WizardStep.ORIGIN) step = WizardStep.EXTRA
            resetInput()
        }
    }

    /** 마법사를 닫는다. 진행 중인 안내와 마이크를 먼저 끊어 닫은 뒤에도 이어지지 않게 한다. */
    fun dismiss() {
        counting = false
        runCatching { speech.cancel() }
        callbacks().stopVoice()
        callbacks().onDismiss()
    }

    /** 화면에 보이는 순서 그대로 여정을 시작한다. */
    fun startJourney() {
        runCatching { speech.cancel() }
        callbacks().stopVoice()
        callbacks().onStart(
            planned.map { (kind, p) ->
                JourneyRepository.StopDraft(kind, p.name, p.address, p.latitude, p.longitude)
            },
        )
    }

    /** 하차지 단계를 건너뛰고 정차지 없이 시작한다. */
    fun startWithoutStops() = callbacks().onStart(emptyList())

    /** 상차지 단계를 건너뛰고 경유지 단계로 넘어간다. */
    fun skipToExtra() {
        step = WizardStep.EXTRA
    }

    /** 현재 위치 사용 질문을 닫고 다시 듣기로 돌아간다. */
    fun cancelAskHere() {
        askHere = false
        originCountSec = 0
    }

    /** 자동 시작 카운트다운을 멈춘다. */
    fun cancelCountdown() {
        counting = false
    }

    /** 뒤로가기. 이전 단계로만 이동하고 첫 단계에서는 그대로 둔다. */
    fun stepBack() {
        step = when (step) {
            WizardStep.ORIGIN -> WizardStep.DEST
            WizardStep.EXTRA -> if (addMode) WizardStep.EXTRA else WizardStep.ORIGIN
            WizardStep.DEST -> WizardStep.DEST
        }
    }

    /** 사용자가 직접 입력했을 때. 이전 검색 결과와 음성 모드를 지운다. */
    fun onQueryTyped(text: String) {
        query = text
        candidates = null
        voiceMode = false
    }

    /** 차순위 인식 후보를 골랐을 때. 그 문장으로 다시 검색한다. */
    fun retryWith(alternative: String) {
        query = alternative
        pickPrompted = false
        runSearch()
    }

    /** 이전 화면에서 재생 중이던 안내를 끊는다. */
    fun stopPreviousVoice() = callbacks().stopVoice()

    // ── 음성 ─────────────────────────────────────────────────────────────────

    private fun stepKey(): String = when (step) {
        WizardStep.DEST -> "dest"
        WizardStep.ORIGIN -> "origin"
        WizardStep.EXTRA -> "extra"
    }

    private fun decidedNames(): List<String> =
        planned.map { (kind, place) -> "${kind.label(context)}: ${place.name}" }

    private fun applyKind(k: VoiceStopKind?) {
        if (step == WizardStep.EXTRA && k != null) {
            extraKind = if (k == VoiceStopKind.PICKUP) StopKind.PICKUP else StopKind.DROPOFF
        }
    }

    /** 의도 해석을 [thinking] 표시로 감싼다. */
    private suspend fun nlu(spoken: String, ctx: VoiceContext): VoiceIntent {
        thinking = true
        return try {
            callbacks().interpret(spoken, ctx)
        } finally {
            thinking = false
        }
    }

    /** 같은 문장을 화면에 띄우고 음성으로 읽는다. */
    private suspend fun say(text: String) {
        guide = text
        callbacks().speakAwait(text)
    }

    private fun toSearch(place: String, kind: VoiceStopKind?, onTooShort: () -> Unit) {
        applyKind(kind)
        if (place.length >= 2) {
            voiceMode = true
            query = place
            runSearch()
        } else {
            onTooShort()
        }
    }

    /** 현재 단계의 질문을 다시 읽고 마이크를 연다. */
    private fun reask() {
        scope.launch {
            speech.cancel()
            say(
                when (step) {
                    WizardStep.DEST -> context.getString(R.string.wizard_reask_dest)
                    WizardStep.ORIGIN -> context.getString(R.string.wizard_prompt_origin)
                    WizardStep.EXTRA -> context.getString(
                        if (extraKind == StopKind.PICKUP) {
                            R.string.wizard_reask_extra_pickup
                        } else {
                            R.string.wizard_reask_extra_dropoff
                        },
                    )
                },
            )
            delay(REASK_DELAY_MS)
            speech.start(::onSpoken)
        }
    }

    /**
     * 장소 입력 단계에서 한 마디를 듣고 의도대로 처리한다.
     *
     * @param spoken 받아쓴 문장
     */
    fun onSpoken(spoken: String) {
        scope.launch {
            val intent = nlu(spoken, VoiceContext(step = stepKey(), decided = decidedNames()))
            when (intent.action) {
                VoiceAction.CANCEL -> dismiss()
                VoiceAction.START -> startJourney()
                VoiceAction.BACK -> step = when (step) {
                    WizardStep.ORIGIN -> WizardStep.DEST
                    WizardStep.EXTRA -> WizardStep.ORIGIN
                    WizardStep.DEST -> WizardStep.DEST
                }

                VoiceAction.REPEAT -> reask()
                VoiceAction.SKIP -> when (step) {
                    WizardStep.DEST -> startWithoutStops()
                    WizardStep.ORIGIN -> skipToExtra()
                    WizardStep.EXTRA -> startJourney()
                }

                VoiceAction.CURRENT -> if (step == WizardStep.ORIGIN) useHere() else reask()
                VoiceAction.ADD_STOP -> {
                    applyKind(intent.kind)
                    val spot = intent.place?.takeIf { it.isNotBlank() }
                    when {
                        step != WizardStep.EXTRA -> step = WizardStep.EXTRA
                        spot != null -> toSearch(spot, intent.kind, ::reask)
                        else -> reask()
                    }
                }

                VoiceAction.CORRECT -> {
                    val spot = intent.place?.takeIf { it.isNotBlank() }
                    if (spot != null) toSearch(spot, intent.kind, ::reask) else reask()
                }

                VoiceAction.MOVE -> moveStopFirst(intent)
                VoiceAction.PLACE, VoiceAction.SELECT, VoiceAction.NONE ->
                    toSearch(intent.place ?: stripStopWords(spoken), intent.kind, ::reask)
            }
        }
    }

    /** "○○ 먼저" — 이름이 일치하는 정차지를 맨 앞으로 옮긴다. 목록에 없으면 새 장소로 검색한다. */
    private suspend fun moveStopFirst(intent: VoiceIntent) {
        val target = intent.place?.trim()
        val idx = if (!target.isNullOrBlank()) {
            planned.indexOfFirst { it.second.name.contains(target) || target.contains(it.second.name) }
        } else {
            -1
        }
        when {
            idx > 0 -> {
                val moved = planned.removeAt(idx)
                planned.add(0, moved)
                say(context.getString(R.string.wizard_say_move_first, moved.second.name))
            }

            idx == 0 -> say(context.getString(R.string.wizard_say_already_first, planned[0].second.name))
            !target.isNullOrBlank() && target.length >= 2 -> toSearch(target, intent.kind, ::reask)
            else -> reask()
        }
    }

    /**
     * 후보 목록에서 "몇 번째인가요?" 에 대한 응답을 해석한다.
     *
     * 순번, 긍정("네/맞아"), 지명 재입력 세 가지를 받는다. 긍정은 NLU 를 거치지 않고
     * 바로 1번을 고른다 — 왕복 지연 없이 응답한다.
     *
     * @param spoken 받아쓴 문장
     */
    fun onPickSpoken(spoken: String) {
        scope.launch {
            val list = candidates
            if (!list.isNullOrEmpty() && isYesWord(spoken)) {
                if (step == WizardStep.EXTRA) applyKind(kindFromText(spoken))
                val cand = list[0]
                pick(PickedPlace(cand.name, cand.address, cand.latitude, cand.longitude))
                return@launch
            }
            val ctx = VoiceContext(
                step = "pick",
                candidates = list?.take(MAX_SPOKEN_CANDIDATES)?.map { it.name }.orEmpty(),
                decided = decidedNames(),
            )
            handlePickIntent(nlu(spoken, ctx), list, spoken)
        }
    }

    private suspend fun handlePickIntent(intent: VoiceIntent, list: List<PlaceCandidate>?, spoken: String) {
        suspend fun reaskPick() {
            say(context.getString(R.string.wizard_say_which_one_retry))
            delay(SHORT_DELAY_MS)
            if (!candidates.isNullOrEmpty()) speech.start(::onPickSpoken)
        }

        fun reSearch(place: String) {
            voiceMode = true
            pickPrompted = false
            query = place
            runSearch()
        }

        fun choose(n: Int) {
            if (step == WizardStep.EXTRA) applyKind(intent.kind ?: kindFromText(spoken))
            val candidate = list?.getOrNull(n - 1) ?: return
            pick(PickedPlace(candidate.name, candidate.address, candidate.latitude, candidate.longitude))
        }

        when (intent.action) {
            VoiceAction.CANCEL -> dismiss()
            VoiceAction.START -> startJourney()
            VoiceAction.CURRENT -> if (step == WizardStep.ORIGIN) useHere() else reaskPick()
            VoiceAction.SELECT, VoiceAction.CORRECT -> {
                val n = intent.ordinal
                val size = list?.size ?: 0
                val spot = intent.place?.takeIf { it.isNotBlank() }
                when {
                    spot != null && spot.length >= 2 -> reSearch(spot)
                    list != null && n != null && n in 1..size -> choose(n)
                    // 결과가 하나면 순번 없이 긍정만으로도 확정한다.
                    list != null && size == 1 && n == null -> choose(1)
                    n != null && size > 0 -> {
                        say(context.getString(R.string.wizard_say_no_such_number, n, size))
                        delay(SHORT_DELAY_MS)
                        if (!candidates.isNullOrEmpty()) speech.start(::onPickSpoken)
                    }

                    else -> reaskPick()
                }
            }

            VoiceAction.PLACE -> {
                val place = intent.place ?: spoken.trim()
                if (place.length >= 2) reSearch(place) else reaskPick()
            }

            else -> reaskPick()
        }
    }

    /**
     * 현재 위치 사용 질문에 대한 응답을 해석한다.
     *
     * @param spoken 받아쓴 문장
     */
    fun onOriginYesNo(spoken: String) {
        scope.launch {
            val intent = nlu(spoken, VoiceContext(step = "origin_yesno", decided = decidedNames()))
            suspend fun reaskHere() {
                say(context.getString(R.string.wizard_say_use_current_q))
                delay(ASK_HERE_DELAY_MS)
                if (askHere) speech.start(::onOriginYesNo)
            }
            when (intent.action) {
                VoiceAction.CURRENT -> useHere()
                VoiceAction.CANCEL -> askHere = false
                VoiceAction.PLACE, VoiceAction.CORRECT -> {
                    val place = intent.place ?: spoken.trim()
                    if (place.length >= 2) {
                        askHere = false
                        voiceMode = true
                        query = place
                        runSearch()
                    } else {
                        reaskHere()
                    }
                }

                else -> reaskHere()
            }
        }
    }

    /** 출발지 인식·검색이 모두 실패했을 때. 카운트다운이 끝나면 현재 위치로 출발한다. */
    fun askUseHere() {
        if (askHere || locating) return
        askHere = true
        originCountSec = ORIGIN_COUNTDOWN_SEC
        scope.launch {
            speech.cancel()
            say(context.getString(R.string.wizard_say_origin_missed))
            while (askHere && originCountSec > 0) {
                delay(ONE_SECOND_MS)
                if (askHere) originCountSec -= 1
            }
            if (askHere && originCountSec <= 0) useHere()
        }
    }

    /** 경유지 단계에서 응답이 없을 때. 요약을 읽고 카운트다운이 끝나면 자동으로 시작한다. */
    fun startCountdown() {
        if (counting) return
        counting = true
        countSec = START_COUNTDOWN_SEC
        scope.launch {
            speech.cancel()
            val origin = originPlace()?.name
            val dest = destPlace()?.name
            val summary = when {
                origin != null && dest != null -> context.getString(R.string.wizard_summary_from_to, origin, dest)
                dest != null -> context.getString(R.string.wizard_summary_to, dest)
                else -> context.getString(R.string.wizard_summary_this)
            }
            say(context.getString(R.string.wizard_say_start_with, summary))
            while (counting && countSec > 0) {
                delay(ONE_SECOND_MS)
                if (counting) countSec -= 1
            }
            if (counting) {
                counting = false
                startJourney()
            }
        }
    }

    /** 단계에 진입할 때 읽어 줄 안내 문구. */
    fun stepPrompt(): String = when (step) {
        WizardStep.DEST -> context.getString(R.string.wizard_prompt_dest)
        WizardStep.ORIGIN -> destPlace()?.name
            ?.let { context.getString(R.string.wizard_prompt_origin_after_dest, it) }
            ?: context.getString(R.string.wizard_prompt_origin)

        WizardStep.EXTRA -> {
            val end = context.getString(
                if (addMode) R.string.wizard_word_done else R.string.wizard_word_none,
            )
            when {
                lastAdded != null ->
                    context.getString(R.string.wizard_prompt_extra_added, lastAdded?.second?.name.orEmpty(), end)

                addMode -> context.getString(R.string.wizard_prompt_extra_add_mode)
                else -> context.getString(R.string.wizard_prompt_extra, end)
            }
        }
    }

    /** 검색 결과가 나왔을 때 읽어 줄 안내 문구. */
    fun resultPrompt(count: Int): String =
        if (count == 1) {
            context.getString(R.string.wizard_say_one_found)
        } else {
            context.getString(R.string.wizard_say_n_found, count)
        }

    /** 화면 말풍선만 갱신한다. 음성 없이 문구를 먼저 보여 줄 때 쓴다. */
    fun showGuide(text: String) {
        guide = text
    }

    /** 안내를 읽어 준다. 화면 말풍선도 함께 갱신된다. */
    suspend fun speakGuide(text: String) = say(text)

    /** 검색 결과가 없을 때의 안내. */
    suspend fun sayNoResults() = say(context.getString(R.string.wizard_say_no_results))

    private companion object {
        const val REASK_DELAY_MS = 300L
        const val SHORT_DELAY_MS = 200L
        const val ASK_HERE_DELAY_MS = 250L
        const val ONE_SECOND_MS = 1000L
        const val ORIGIN_COUNTDOWN_SEC = 5
        const val START_COUNTDOWN_SEC = 6
        const val MAX_SPOKEN_CANDIDATES = 8
    }
}

/**
 * 컴포지션에 묶인 [JourneyWizardState] 를 만든다.
 *
 * 콜백은 매 컴포지션마다 최신 값으로 갱신되지만 상태 객체 자체는 유지된다.
 */
@Composable
internal fun rememberJourneyWizardState(
    searchPlaces: suspend (String) -> List<PlaceCandidate>,
    currentPlace: suspend () -> CapturedLocation?,
    interpret: suspend (String, VoiceContext) -> VoiceIntent,
    speakAwait: suspend (String) -> Unit,
    stopVoice: () -> Unit,
    onStart: (List<JourneyRepository.StopDraft>) -> Unit,
    onDismiss: () -> Unit,
    initialDestination: JourneyRepository.StopDraft?,
    addMode: Boolean,
): JourneyWizardState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val speech = rememberSpeechInput()
    val callbacks by rememberUpdatedState(
        JourneyWizardState.Callbacks(
            searchPlaces = searchPlaces,
            currentPlace = currentPlace,
            interpret = interpret,
            speakAwait = speakAwait,
            stopVoice = stopVoice,
            onStart = onStart,
            onDismiss = onDismiss,
        ),
    )
    return remember {
        JourneyWizardState(
            context = context,
            scope = scope,
            speech = speech,
            addMode = addMode,
            initialDestination = initialDestination,
            callbacks = { callbacks },
        )
    }
}

/** 발화에서 명령어 토막을 떼어내 장소명만 남긴다. NLU 가 장소를 못 뽑았을 때의 폴백이다. */
internal fun stripStopWords(text: String): String {
    var s = " $text "
    listOf(
        // 종류·역할
        "상차지", "하차지", "상차해서", "하차해서", "상차하는", "하차하는", "상차", "하차", "픽업해서", "픽업",
        "경유지", "경유", "출발지", "도착지", "목적지", "정차지",
        // 동작·추가
        "추가해줘", "추가해주세요", "추가하자", "추가해", "추가", "들렀다가", "들렀다", "들를곳", "들를데", "들러서", "들러", "거쳐서", "거쳐",
        "싣고", "실으러", "실어", "내리러", "내려서", "배송", "납품", "하러",
        // 부탁·꼬리말
        "넣어줘", "넣어주세요", "넣어", "해줘", "해주세요", "할게요", "할게", "할래", "갈게요", "갈게", "갈래", "가자", "좀", "그리고", "또", "이요", "요",
    ).forEach { w -> s = s.replace(w, " ") }
    return s.trim().replace(Regex("\\s+"), " ")
}

/** 긍정 응답인지. "네번째"(=4번)는 순번이므로 제외한다. */
internal fun isYesWord(text: String): Boolean {
    val t = text.trim().replace(" ", "")
    if (t.contains("번째") || Regex("\\d").containsMatchIn(t)) return false
    return t in setOf(
        "네", "넵", "넹", "네네", "예", "예예", "응", "어", "그래", "그래요", "그렇지", "그치",
        "맞아", "맞아요", "맞어", "맞다", "맞네", "맞습니다", "맞아맞아", "맞지",
        "좋아", "좋아요", "좋습니다", "오케이", "오키", "콜", "웅", "넹넹", "네맞아", "네맞아요",
    )
}

/** 발화에서 상·하차 역할만 뽑는다. "두번째 상차"처럼 후보 선택과 함께 말할 때 쓴다. */
internal fun kindFromText(text: String): VoiceStopKind? = when {
    Regex("상차|싣|실어|실으|적재|픽업").containsMatchIn(text) -> VoiceStopKind.PICKUP
    Regex("하차|내리|내려|하역|배송|납품").containsMatchIn(text) -> VoiceStopKind.DROPOFF
    else -> null
}

/** "경기도 성남시 분당구 삼평동 123" → "삼평동 123". 표시용 짧은 이름. */
internal fun shortName(address: String): String {
    val parts = address.split(" ").filter { it.isNotBlank() }
    return if (parts.size <= 2) address else parts.takeLast(2).joinToString(" ")
}

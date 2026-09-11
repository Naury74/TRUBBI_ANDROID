package com.naury.trubbi.feature.journey

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.naury.trubbi.core.location.CapturedLocation
import com.naury.trubbi.core.location.PlaceCandidate
import com.naury.trubbi.core.voice.SpeechInputState
import com.naury.trubbi.core.voice.VoiceAction
import com.naury.trubbi.core.voice.VoiceContext
import com.naury.trubbi.core.voice.VoiceIntent
import com.naury.trubbi.core.voice.VoiceStopKind
import com.naury.trubbi.data.repo.JourneyRepository
import com.naury.trubbi.domain.model.StopKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 여정 시작 마법사의 대화 흐름 테스트.
 *
 * 이 클래스는 앱에서 분기가 가장 많은 곳이다. 인식 → 의도 해석 → 검색 → 후보 확인 →
 * 확정으로 이어지는 경로에 단계 이동·정정·건너뛰기가 얽혀 있고, 잘못 끼면 정차지 순서가
 * 뒤집혀 여정 전체가 어긋난다. 화면 없이 상태 전이만 검증한다.
 *
 * 바깥 동작(검색·위치·NLU·안내)은 전부 콜백이라 가짜로 갈아 끼운다. 인식기는 실제
 * [SpeechInputState] 를 쓰되 마이크를 여는 경로는 테스트하지 않는다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class JourneyWizardStateTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** NLU 가 돌려줄 의도. 테스트마다 바꿔 끼운다. */
    private var intent = VoiceIntent(VoiceAction.NONE)

    /** 검색이 돌려줄 후보. */
    private var searchResult = listOf<PlaceCandidate>()

    /** 현재 위치. null 이면 측위 실패. */
    private var here: CapturedLocation? = CapturedLocation(37.5, 127.0, "서울특별시 중구 세종대로 110")

    private var started: List<JourneyRepository.StopDraft>? = null
    private var dismissed = false
    private val spoken = mutableListOf<String>()

    private fun newState(
        scope: TestScope,
        addMode: Boolean = false,
        initialDestination: JourneyRepository.StopDraft? = null,
    ) = JourneyWizardState(
        context = context,
        scope = scope,
        speech = SpeechInputState(context),
        addMode = addMode,
        initialDestination = initialDestination,
        callbacks = {
            JourneyWizardState.Callbacks(
                searchPlaces = { searchResult },
                currentPlace = { here },
                interpret = { _: String, _: VoiceContext -> intent },
                speakAwait = { spoken += it },
                stopVoice = {},
                onStart = { started = it },
                onDismiss = { dismissed = true },
            )
        },
    )

    private fun place(name: String) = PickedPlace(name)

    private fun candidate(name: String) = PlaceCandidate(name, "$name 주소", 37.0, 127.0)

    // ── 시작 단계 ─────────────────────────────────────────────────────────────

    @Test
    fun `아무 정보 없이 열면 하차지부터 묻는다`() = runTest {
        val state = newState(this)
        assertThat(state.step).isEqualTo(WizardStep.DEST)
        assertThat(state.planned).isEmpty()
    }

    @Test
    fun `목적지를 미리 받으면 그것을 하차지로 두고 출발지를 묻는다`() = runTest {
        val state = newState(
            this,
            initialDestination = JourneyRepository.StopDraft(StopKind.DROPOFF, "부산항"),
        )
        assertThat(state.step).isEqualTo(WizardStep.ORIGIN)
        assertThat(state.planned).hasSize(1)
        assertThat(state.planned[0].first).isEqualTo(StopKind.DROPOFF)
        assertThat(state.planned[0].second.name).isEqualTo("부산항")
    }

    @Test
    fun `추가 모드는 경유지 단계에서 시작하고 기존 목적지를 넣지 않는다`() = runTest {
        val state = newState(
            this,
            addMode = true,
            initialDestination = JourneyRepository.StopDraft(StopKind.DROPOFF, "부산항"),
        )
        assertThat(state.step).isEqualTo(WizardStep.EXTRA)
        assertThat(state.planned).isEmpty()
    }

    // ── 확정 순서 ─────────────────────────────────────────────────────────────

    @Test
    fun `하차지 다음에 고른 상차지는 목록 맨 앞에 온다`() = runTest {
        val state = newState(this)
        state.pick(place("부산항"))
        assertThat(state.step).isEqualTo(WizardStep.ORIGIN)
        state.pick(place("인천항"))

        assertThat(state.step).isEqualTo(WizardStep.EXTRA)
        assertThat(state.planned.map { it.first to it.second.name }).containsExactly(
            StopKind.PICKUP to "인천항",
            StopKind.DROPOFF to "부산항",
        ).inOrder()
    }

    @Test
    fun `경유지는 마지막 하차지 앞에 끼워 최종 목적지가 끝에 남는다`() = runTest {
        val state = newState(this)
        state.pick(place("부산항"))
        state.pick(place("인천항"))
        state.extraKind = StopKind.DROPOFF
        state.pick(place("대전물류"))

        assertThat(state.planned.map { it.second.name })
            .containsExactly("인천항", "대전물류", "부산항").inOrder()
    }

    @Test
    fun `추가 모드에서는 고른 순서 그대로 뒤에 붙인다`() = runTest {
        val state = newState(this, addMode = true)
        state.pick(place("대전물류"))
        state.pick(place("대구터미널"))

        assertThat(state.planned.map { it.second.name })
            .containsExactly("대전물류", "대구터미널").inOrder()
        assertThat(state.lastAdded?.second?.name).isEqualTo("대구터미널")
    }

    @Test
    fun `시작하면 화면에 보이는 순서 그대로 정차지를 넘긴다`() = runTest {
        val state = newState(this)
        state.pick(place("부산항"))
        state.pick(place("인천항"))
        state.startJourney()

        assertThat(started!!.map { it.kind to it.name }).containsExactly(
            StopKind.PICKUP to "인천항",
            StopKind.DROPOFF to "부산항",
        ).inOrder()
    }

    // ── 현재 위치 ─────────────────────────────────────────────────────────────

    @Test
    fun `현재 위치는 출발지로 맨 앞에 하나만 남는다`() = runTest {
        val state = newState(this)
        state.pick(place("부산항"))
        state.useHere()
        advanceUntilIdle()
        state.useHere()
        advanceUntilIdle()

        assertThat(state.planned.count { it.first == StopKind.ORIGIN }).isEqualTo(1)
        assertThat(state.planned[0].first).isEqualTo(StopKind.ORIGIN)
        assertThat(state.step).isEqualTo(WizardStep.EXTRA)
    }

    @Test
    fun `측위가 실패해도 출발지를 만들어 흐름을 막지 않는다`() = runTest {
        here = null
        val state = newState(this)
        state.pick(place("부산항"))
        state.useHere()
        advanceUntilIdle()

        assertThat(state.planned[0].first).isEqualTo(StopKind.ORIGIN)
        assertThat(state.planned[0].second.name).isNotEmpty()
        assertThat(state.planned[0].second.latitude).isNull()
    }

    // ── 뒤로가기 ──────────────────────────────────────────────────────────────

    @Test
    fun `뒤로가기는 첫 단계에서 더 물러나지 않는다`() = runTest {
        val state = newState(this)
        state.stepBack()
        assertThat(state.step).isEqualTo(WizardStep.DEST)
    }

    @Test
    fun `추가 모드의 뒤로가기는 단계를 바꾸지 않는다`() = runTest {
        val state = newState(this, addMode = true)
        state.stepBack()
        assertThat(state.step).isEqualTo(WizardStep.EXTRA)
    }

    // ── 검색 ──────────────────────────────────────────────────────────────────

    @Test
    fun `직접 입력하면 이전 검색 결과와 음성 모드를 지운다`() = runTest {
        searchResult = listOf(candidate("부산항"))
        val state = newState(this)
        state.query = "부산"
        state.runSearch()
        advanceUntilIdle()
        assertThat(state.candidates).hasSize(1)

        state.onQueryTyped("인천")
        assertThat(state.candidates).isNull()
        assertThat(state.voiceMode).isFalse()
    }

    @Test
    fun `차순위 인식 후보를 고르면 그 문장으로 다시 검색한다`() = runTest {
        searchResult = listOf(candidate("인천항"))
        val state = newState(this)
        state.retryWith("인천항")
        advanceUntilIdle()

        assertThat(state.query).isEqualTo("인천항")
        assertThat(state.candidates!!.map { it.name }).containsExactly("인천항")
    }

    // ── 음성: 장소 입력 단계 ──────────────────────────────────────────────────

    @Test
    fun `장소를 말하면 그 이름으로 검색한다`() = runTest {
        intent = VoiceIntent(VoiceAction.PLACE, place = "부산항")
        searchResult = listOf(candidate("부산항"))
        val state = newState(this)
        state.onSpoken("부산항으로 가주세요")
        advanceUntilIdle()

        assertThat(state.query).isEqualTo("부산항")
        assertThat(state.voiceMode).isTrue()
        assertThat(state.candidates!!.map { it.name }).containsExactly("부산항")
    }

    @Test
    fun `취소라고 말하면 마법사를 닫는다`() = runTest {
        intent = VoiceIntent(VoiceAction.CANCEL)
        val state = newState(this)
        state.onSpoken("취소")
        advanceUntilIdle()

        assertThat(dismissed).isTrue()
        assertThat(started).isNull()
    }

    @Test
    fun `하차지 단계에서 건너뛰면 정차지 없이 시작한다`() = runTest {
        intent = VoiceIntent(VoiceAction.SKIP)
        val state = newState(this)
        state.onSpoken("나중에")
        advanceUntilIdle()

        assertThat(started).isEmpty()
    }

    @Test
    fun `출발지 단계에서 건너뛰면 경유지 단계로 넘어간다`() = runTest {
        intent = VoiceIntent(VoiceAction.SKIP)
        val state = newState(this)
        state.pick(place("부산항"))
        state.onSpoken("나중에")
        advanceUntilIdle()

        assertThat(state.step).isEqualTo(WizardStep.EXTRA)
        assertThat(started).isNull()
    }

    @Test
    fun `출발지 단계에서 현재 위치라고 말하면 그 자리를 출발지로 쓴다`() = runTest {
        intent = VoiceIntent(VoiceAction.CURRENT)
        val state = newState(this)
        state.pick(place("부산항"))
        state.onSpoken("지금 여기")
        advanceUntilIdle()

        assertThat(state.planned[0].first).isEqualTo(StopKind.ORIGIN)
    }

    @Test
    fun `경유지 추가에서 상차라고 하면 구분이 상차로 바뀐다`() = runTest {
        intent = VoiceIntent(VoiceAction.ADD_STOP, place = "대전물류", kind = VoiceStopKind.PICKUP)
        searchResult = listOf(candidate("대전물류"))
        val state = newState(this, addMode = true)
        state.onSpoken("대전물류에서 상차")
        advanceUntilIdle()

        assertThat(state.extraKind).isEqualTo(StopKind.PICKUP)
        assertThat(state.query).isEqualTo("대전물류")
    }

    @Test
    fun `먼저 가자고 말한 정차지를 목록 맨 앞으로 옮긴다`() = runTest {
        val state = newState(this, addMode = true)
        state.pick(place("대전물류"))
        state.pick(place("대구터미널"))
        intent = VoiceIntent(VoiceAction.MOVE, place = "대구터미널")
        state.onSpoken("대구터미널 먼저")
        advanceUntilIdle()

        assertThat(state.planned.map { it.second.name })
            .containsExactly("대구터미널", "대전물류").inOrder()
    }

    // ── 음성: 후보 고르기 ─────────────────────────────────────────────────────

    @Test
    fun `후보가 있을 때 긍정하면 NLU 없이 첫 번째를 고른다`() = runTest {
        searchResult = listOf(candidate("부산항"), candidate("부산신항"))
        intent = VoiceIntent(VoiceAction.CANCEL) // 불리면 안 되는 의도를 심어 둔다
        val state = newState(this)
        state.query = "부산"
        state.runSearch()
        advanceUntilIdle()

        state.onPickSpoken("네")
        advanceUntilIdle()

        assertThat(dismissed).isFalse()
        assertThat(state.planned.map { it.second.name }).containsExactly("부산항")
        assertThat(state.step).isEqualTo(WizardStep.ORIGIN)
    }

    @Test
    fun `순번을 말하면 그 번째 후보를 고른다`() = runTest {
        searchResult = listOf(candidate("부산항"), candidate("부산신항"))
        val state = newState(this)
        state.query = "부산"
        state.runSearch()
        advanceUntilIdle()

        intent = VoiceIntent(VoiceAction.SELECT, ordinal = 2)
        state.onPickSpoken("두 번째")
        advanceUntilIdle()

        assertThat(state.planned.map { it.second.name }).containsExactly("부산신항")
    }

    @Test
    fun `고르는 중에 다른 지명을 말하면 그 이름으로 다시 검색한다`() = runTest {
        searchResult = listOf(candidate("부산항"))
        val state = newState(this)
        state.query = "부산"
        state.runSearch()
        advanceUntilIdle()

        searchResult = listOf(candidate("인천항"))
        intent = VoiceIntent(VoiceAction.SELECT, place = "인천항")
        state.onPickSpoken("아니 인천항")
        advanceUntilIdle()

        assertThat(state.query).isEqualTo("인천항")
        assertThat(state.candidates!!.map { it.name }).containsExactly("인천항")
        assertThat(state.planned).isEmpty()
    }

    // ── 음성: 현재 위치 확인 ──────────────────────────────────────────────────

    @Test
    fun `현재 위치를 쓰겠다고 답하면 출발지가 채워진다`() = runTest {
        intent = VoiceIntent(VoiceAction.CURRENT)
        val state = newState(this)
        state.pick(place("부산항"))
        state.onOriginYesNo("네")
        advanceUntilIdle()

        assertThat(state.planned[0].first).isEqualTo(StopKind.ORIGIN)
        assertThat(state.askHere).isFalse()
    }

    @Test
    fun `현재 위치 대신 다른 곳을 말하면 그 이름으로 검색한다`() = runTest {
        intent = VoiceIntent(VoiceAction.PLACE, place = "인천항")
        searchResult = listOf(candidate("인천항"))
        val state = newState(this)
        state.pick(place("부산항"))
        state.onOriginYesNo("아니요 인천항이요")
        advanceUntilIdle()

        assertThat(state.askHere).isFalse()
        assertThat(state.query).isEqualTo("인천항")
        assertThat(state.planned.none { it.first == StopKind.ORIGIN }).isTrue()
    }
}

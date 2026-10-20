package com.naury.trubbi.core.voice

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 오프라인 음성 해석 테스트.
 *
 * 터널·산간처럼 신호가 없는 곳에서도 여정 등록은 되어야 한다. 여기서 검증하는 건
 * 클라우드 없이 기기 안에서만 도는 규칙 기반 해석이며, 아래 문장들은 모두 로컬에서
 * 결론이 나므로 네트워크를 타지 않는다.
 */
@RunWith(RobolectricTestRunner::class)
class VoiceNluLocalTest {

    private val nlu = VoiceNlu(ApplicationProvider.getApplicationContext())

    @Test
    fun `취소 표현은 어미가 달라도 취소로 읽는다`() = runTest {
        listOf("취소", "그만할래", "안할래", "닫기").forEach { spoken ->
            assertThat(nlu.interpret(spoken, VoiceContext(step = "dest")).action).isEqualTo(VoiceAction.CANCEL)
        }
    }

    @Test
    fun `현재 위치 표현을 알아듣는다`() = runTest {
        listOf("현재 위치", "여기서 출발", "내 위치").forEach { spoken ->
            assertThat(nlu.interpret(spoken, VoiceContext(step = "dest")).action).isEqualTo(VoiceAction.CURRENT)
        }
    }

    @Test
    fun `순서 이동은 시작보다 먼저 잡힌다`() = runTest {
        // "출발"이 들어 있어도 '먼저'가 있으면 여정 시작이 아니라 순서 변경이다.
        val intent = nlu.interpret("부산 신항 먼저 가는 걸로", VoiceContext(step = "extra"))

        assertThat(intent.action).isEqualTo(VoiceAction.MOVE)
        assertThat(intent.place).contains("부산")
    }

    @Test
    fun `정정 표현에서 순번을 읽어낸다`() = runTest {
        val intent = nlu.interpret("아니 두 번째 말고", VoiceContext(step = "pick", candidates = listOf("A", "B")))

        assertThat(intent.action).isEqualTo(VoiceAction.CORRECT)
        assertThat(intent.correctTarget).isEqualTo(CorrectTarget.ORDINAL)
        assertThat(intent.ordinal).isEqualTo(2)
    }

    @Test
    fun `후보 선택 단계에서 순번을 고른다`() = runTest {
        val context = VoiceContext(step = "pick", candidates = listOf("A", "B", "C"))

        assertThat(nlu.interpret("세 번째", context).ordinal).isEqualTo(3)
        assertThat(nlu.interpret("2번", context).ordinal).isEqualTo(2)
    }

    @Test
    fun `후보 선택 단계의 긍정 응답은 첫 번째를 고른다`() = runTest {
        val intent = nlu.interpret("네", VoiceContext(step = "pick", candidates = listOf("A", "B")))

        assertThat(intent.action).isEqualTo(VoiceAction.SELECT)
        assertThat(intent.ordinal).isEqualTo(1)
    }

    @Test
    fun `장소 이름에서 조사와 동작 표현을 걷어낸다`() = runTest {
        val intent = nlu.interpret("부산 신항에서 상차해서 갈게요", VoiceContext(step = "dest"))

        assertThat(intent.action).isEqualTo(VoiceAction.PLACE)
        assertThat(intent.place).doesNotContain("상차")
        assertThat(intent.place).doesNotContain("갈게")
        assertThat(intent.kind).isEqualTo(VoiceStopKind.PICKUP)
    }

    @Test
    fun `빈 문장에는 아무 의도도 만들지 않는다`() = runTest {
        assertThat(nlu.interpret("   ", VoiceContext(step = "dest")).action).isEqualTo(VoiceAction.NONE)
    }

    @Test
    fun `목적지와 함께 말한 가자는 시작이 아니라 장소다`() = runTest {
        val intent = nlu.interpret("부산 신항 가자", VoiceContext(step = "dest"))

        assertThat(intent.action).isEqualTo(VoiceAction.PLACE)
        assertThat(intent.place).isEqualTo("부산 신항")
    }

    @Test
    fun `가자 한마디는 여정 시작이다`() = runTest {
        assertThat(nlu.interpret("가자", VoiceContext(step = "extra")).action).isEqualTo(VoiceAction.START)
    }

    @Test
    fun `현장이 들어간 장소 이름을 현재 위치로 오인하지 않는다`() = runTest {
        val intent = nlu.interpret("평택 현장 사무소", VoiceContext(step = "dest"))

        assertThat(intent.action).isEqualTo(VoiceAction.PLACE)
        assertThat(intent.place).isEqualTo("평택 현장 사무소")
    }

    @Test
    fun `장소 이름 속 글자를 조사로 오인해 지우지 않는다`() = runTest {
        assertThat(nlu.interpret("세종대로", VoiceContext(step = "dest")).place).isEqualTo("세종대로")
        assertThat(nlu.interpret("신촌로터리로 가요", VoiceContext(step = "dest")).place).isEqualTo("신촌로터리로")
        assertThat(nlu.interpret("광화문으로 갈게요", VoiceContext(step = "dest")).place).isEqualTo("광화문")
    }

    @Test
    fun `이번엔 으로 시작하는 말을 두 번째로 읽지 않는다`() = runTest {
        val context = VoiceContext(step = "pick", candidates = listOf("A", "B", "C"))

        val intent = nlu.interpret("이번엔 인천항", context)

        assertThat(intent.action).isEqualTo(VoiceAction.PLACE)
        assertThat(intent.place).isEqualTo("인천항")
    }

    @Test
    fun `정정할 때 지시어를 장소 이름에 남기지 않는다`() = runTest {
        val intent = nlu.interpret("아니 그거 말고 부산 신항", VoiceContext(step = "dest"))

        assertThat(intent.action).isEqualTo(VoiceAction.CORRECT)
        assertThat(intent.place).isEqualTo("부산 신항")
    }
}

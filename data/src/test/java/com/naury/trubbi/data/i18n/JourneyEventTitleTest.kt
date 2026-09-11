package com.naury.trubbi.data.i18n

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.naury.trubbi.core.i18n.label
import com.naury.trubbi.core.ui.StringProvider
import com.naury.trubbi.data.local.JourneyEventEntity
import com.naury.trubbi.domain.model.EvidenceCategory
import com.naury.trubbi.domain.model.ExpenseCategory
import com.naury.trubbi.domain.model.IssueType
import com.naury.trubbi.domain.model.JourneyEventType
import com.naury.trubbi.domain.model.StopKind
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 타임라인 이벤트 제목 조립 테스트.
 *
 * 버전 6까지는 "상차지 도착" 같은 완성 문구를 저장 시점 언어로 DB 에 넣었다. 앱 언어를 바꿔도
 * 지난 기록이 옛 언어로 남는 문제가 있어, 지금은 종류와 번역 전 인자만 저장하고 표시할 때
 * 조립한다. 이미 저장된 행은 되살릴 원본이 없으므로 저장된 문구를 그대로 쓴다.
 */
@RunWith(RobolectricTestRunner::class)
class JourneyEventTitleTest {

    private val strings = StringProvider(ApplicationProvider.getApplicationContext())

    private fun event(
        type: JourneyEventType,
        title: String = "",
        titleArg: String? = null,
        titleAmount: Long? = null,
    ) = JourneyEventEntity(
        journeyId = "j1",
        type = type.name,
        at = 0L,
        title = title,
        titleArg = titleArg,
        titleAmount = titleAmount,
    )

    @Test
    fun `버전 7 이전에 저장된 문구는 그대로 쓴다`() {
        val old = event(JourneyEventType.ARRIVED, title = "상차지 도착")

        assertThat(old.displayTitle(strings)).isEqualTo("상차지 도착")
    }

    @Test
    fun `인자가 없는 종류는 라벨이 곧 제목이다`() {
        val started = event(JourneyEventType.JOURNEY_STARTED)

        assertThat(started.displayTitle(strings))
            .isEqualTo(strings[JourneyEventType.JOURNEY_STARTED.labelRes])
    }

    @Test
    fun `정차지 이벤트는 저장된 종류 이름을 지금 언어로 읽는다`() {
        val arrived = event(JourneyEventType.ARRIVED, titleArg = StopKind.PICKUP.name)

        assertThat(arrived.displayTitle(strings))
            .isEqualTo(strings[JourneyEventType.ARRIVED.titleRes, StopKind.PICKUP.label(strings)])
    }

    @Test
    fun `같은 인자라도 종류가 다르면 다른 서식으로 읽는다`() {
        val started = event(JourneyEventType.WORK_STARTED, titleArg = StopKind.DROPOFF.name)
        val done = event(JourneyEventType.STOP_COMPLETED, titleArg = StopKind.DROPOFF.name)

        assertThat(started.displayTitle(strings)).isNotEqualTo(done.displayTitle(strings))
    }

    @Test
    fun `비용 이벤트는 분류와 금액을 함께 넣는다`() {
        val expense = event(
            JourneyEventType.EXPENSE_ADDED,
            titleArg = ExpenseCategory.FUEL.name,
            titleAmount = 120_000L,
        )

        val text = expense.displayTitle(strings)
        assertThat(text).contains(ExpenseCategory.FUEL.label(strings))
        // 천 단위 구분이 붙어야 운전 중에 읽힌다.
        assertThat(text).contains("120,000")
    }

    @Test
    fun `이슈 이벤트는 이슈 종류로 읽는다`() {
        val issue = event(JourneyEventType.ISSUE_ADDED, titleArg = IssueType.DAMAGE.name)

        assertThat(issue.displayTitle(strings)).contains(IssueType.DAMAGE.label(strings))
    }

    @Test
    fun `기록 연결은 서식 없이 증빙 종류만 보여 준다`() {
        val linked = event(JourneyEventType.RECORD_LINKED, titleArg = EvidenceCategory.RECEIPT.name)

        assertThat(linked.displayTitle(strings)).isEqualTo(EvidenceCategory.RECEIPT.label(strings))
    }

    @Test
    fun `기록 연결에 증빙 종류가 없으면 라벨로 돌아간다`() {
        val linked = event(JourneyEventType.RECORD_LINKED)

        assertThat(linked.displayTitle(strings))
            .isEqualTo(strings[JourneyEventType.RECORD_LINKED.labelRes])
    }

    @Test
    fun `열거형에서 사라진 인자가 들어와도 제목이 통째로 비지 않는다`() {
        val gone = event(JourneyEventType.ARRIVED, titleArg = "SOMETHING_REMOVED")

        assertThat(gone.displayTitle(strings)).contains("SOMETHING_REMOVED")
    }
}

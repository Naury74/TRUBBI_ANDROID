package com.naury.trubbi.core.ml

import com.google.common.truth.Truth.assertThat
import com.naury.trubbi.domain.model.ExpenseCategory
import org.junit.Test

/**
 * 영수증 OCR 해석 테스트.
 *
 * 실제 영수증은 줄이 밀리고 글자가 깨져서 들어온다. 그래도 금액·상호·분류만은
 * 웬만하면 맞혀야 기사가 비용 입력을 다시 타이핑하지 않는다.
 */
class ReceiptParserTest {

    @Test
    fun `합계 줄의 금액을 가장 큰 숫자보다 우선한다`() {
        val ocr = """
            대한주유소
            휘발유 1,234원/L
            수량 45.0L
            공급가액 50,000
            합계 55,500
        """.trimIndent()

        val parsed = ReceiptParser.parse(ocr)

        assertThat(parsed.amount).isEqualTo(55_500)
    }

    @Test
    fun `합계 표시가 없으면 가장 큰 금액을 쓴다`() {
        val parsed = ReceiptParser.parse("주차장\n입차 09:12\n출차 11:40\n3,000")

        assertThat(parsed.amount).isEqualTo(3_000)
    }

    @Test
    fun `주유 영수증은 주유비로 분류한다`() {
        val parsed = ReceiptParser.parse("GS칼텍스 셀프\n경유 45L\n합계 88,000")

        assertThat(parsed.category).isEqualTo(ExpenseCategory.FUEL)
    }

    @Test
    fun `요소수는 주유비가 아니라 요소수로 분류한다`() {
        // 요소수 영수증에는 주유소 이름이 함께 찍히는 경우가 많아 우선순위가 중요하다.
        val parsed = ReceiptParser.parse("오일뱅크 셀프\n요소수 10L\n합계 12,000")

        assertThat(parsed.category).isEqualTo(ExpenseCategory.UREA)
    }

    @Test
    fun `상호는 숫자 줄이 아닌 첫 글자 줄에서 뽑는다`() {
        val parsed = ReceiptParser.parse("한밭식당\n2026-09-08\n백반 2인\n합계 18,000")

        assertThat(parsed.vendor).isEqualTo("한밭식당")
    }

    @Test
    fun `금액이 터무니없이 크면 버린다`() {
        // 사업자등록번호나 카드번호가 금액으로 잡히면 안 된다.
        val parsed = ReceiptParser.parse("사업자 1234567890123\n영수증")

        assertThat(parsed.amount).isNull()
    }

    @Test
    fun `읽어낸 글자가 없으면 전부 비운다`() {
        val parsed = ReceiptParser.parse(null)

        assertThat(parsed.amount).isNull()
        assertThat(parsed.vendor).isNull()
        assertThat(parsed.category).isNull()
    }
}

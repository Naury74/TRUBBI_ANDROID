package com.naury.trubbi.core.ml

import com.naury.trubbi.domain.model.ExpenseCategory

/**
 * 영수증 OCR 텍스트에서 금액·상호·비용 분류를 뽑아내는 규칙 모음.
 *
 * 입력은 [com.naury.trubbi.core.ml.MediaAnalyzer] 가 검색 색인용으로 이미 추출해 둔 OCR 텍스트다.
 * 결과는 비용 입력 폼의 초기값으로만 쓰이며, 사용자가 그대로 수정할 수 있다.
 *
 * 여기 정의된 한국어 낱말은 UI 문구가 아니라 한국 영수증 판독 규칙이므로 리소스로 분리하지 않는다.
 */
object ReceiptParser {

    /**
     * 영수증에서 건진 값들. 못 찾은 항목은 null 이고, 화면은 빈칸으로 둔다.
     *
     * @property amount 결제 금액(원)
     * @property vendor 상호명
     * @property category 추천 비용 분류
     */
    data class Parsed(
        val amount: Long?,
        val vendor: String?,
        val category: ExpenseCategory?,
    )

    private val numberPattern = Regex("([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{3,9})")
    private val totalKeywords = listOf("합계", "합 계", "총액", "총 액", "결제금액", "결제 금액", "승인금액", "받을금액", "받은금액", "판매금액", "총합계")

    private val categoryKeywords: List<Pair<ExpenseCategory, List<String>>> = listOf(
        ExpenseCategory.UREA to listOf("요소수"),
        ExpenseCategory.FUEL to listOf("주유", "경유", "휘발유", "리터", "LPG", "충전소", "셀프", "오일뱅크", "GS칼텍스", "S-OIL", "SK에너지"),
        ExpenseCategory.TOLL to listOf("통행료", "톨게이트", "하이패스", "영업소", "고속도로", "도로공사"),
        ExpenseCategory.PARKING to listOf("주차", "입차", "출차"),
        ExpenseCategory.MAINTENANCE to listOf("정비", "타이어", "엔진오일", "수리", "부품", "공업사", "카센터"),
        ExpenseCategory.CAR_WASH to listOf("세차"),
        ExpenseCategory.LODGING_SHOWER to listOf("숙박", "모텔", "찜질방", "샤워", "수면실"),
        ExpenseCategory.MEAL to listOf("식당", "음식", "편의점", "휴게소", "분식", "김밥", "국밥", "백반", "커피", "마트"),
    )

    /**
     * OCR 결과 한 덩어리를 훑어 값을 뽑는다.
     *
     * @param ocrText 영수증 사진에서 읽은 글자 전체. null·공백이면 전부 null 인 결과
     */
    fun parse(ocrText: String?): Parsed {
        if (ocrText.isNullOrBlank()) return Parsed(null, null, null)
        val lines = ocrText.lines().map { it.trim() }.filter { it.isNotBlank() }

        // 금액은 '합계·결제' 키워드가 있는 줄에서 먼저 찾고, 없으면 가장 큰 값을 쓴다.
        // 영수증에는 단가·수량·부가세가 함께 인쇄되므로 단순 최댓값만으로는 오차가 크다.
        val keywordAmounts = lines
            .filter { line -> totalKeywords.any { line.contains(it) } }
            .flatMap { extractAmounts(it) }
        val allAmounts = lines.flatMap { extractAmounts(it) }
        val amount = (keywordAmounts.maxOrNull() ?: allAmounts.maxOrNull())
            ?.takeIf { it in 100..99_999_999 }

        // 상호는 숫자·키워드 줄을 제외하고 문자 비율이 높은 첫 줄로 본다.
        val vendor = lines.firstOrNull { line ->
            line.length in 2..24 &&
                line.count { it.isLetter() } >= line.length / 2 &&
                totalKeywords.none { line.contains(it) } &&
                !line.contains("영수증") &&
                !line.contains("사업자")
        }

        // 분류는 텍스트 어디서든 처음 걸리는 키워드 묶음으로 정한다.
        val category = categoryKeywords.firstOrNull { (_, words) ->
            words.any { ocrText.contains(it, ignoreCase = true) }
        }?.first

        return Parsed(amount, vendor, category)
    }

    private fun extractAmounts(line: String): List<Long> =
        numberPattern.findAll(line)
            .mapNotNull { it.value.replace(",", "").toLongOrNull() }
            .filter { it >= 100 }
            .toList()
}

package com.naury.trubbi.core.ml

/**
 * ML Kit 이미지 라벨러의 영어 라벨을 대응하는 한국어 낱말로 확장한다.
 *
 * ML Kit 이미지 라벨러는 영어 라벨만 반환한다. 영어 라벨만 색인하면 한국어 질의로 검색되지
 * 않으므로, 라벨 하나를 대응 한국어 낱말들로 펼쳐 함께 저장한다.
 *
 * 여기 정의된 한국어 낱말은 UI 문구가 아니라 검색 규칙이므로 리소스로 분리하지 않는다.
 */
object SynonymDict {
    private val map: Map<String, List<String>> = mapOf(
        "tire" to listOf("타이어", "바퀴"),
        "wheel" to listOf("타이어", "바퀴", "휠"),
        "lock" to listOf("자물쇠", "잠금"),
        "padlock" to listOf("자물쇠"),
        "truck" to listOf("트럭", "차량"),
        "vehicle" to listOf("차량"),
        "car" to listOf("차량"),
        "box" to listOf("박스", "상자"),
        "carton" to listOf("박스", "상자"),
        "package" to listOf("택배", "화물"),
        "pallet" to listOf("파렛트", "팔레트"),
        "label" to listOf("라벨", "운송장"),
        "receipt" to listOf("영수증"),
        "document" to listOf("문서", "서류"),
        "paper" to listOf("문서", "서류"),
        "text" to listOf("문서"),
        "engine" to listOf("엔진", "정비"),
        "machine" to listOf("기계", "장비"),
        "road" to listOf("도로"),
        "warehouse" to listOf("창고"),
        "building" to listOf("건물"),
        "container" to listOf("컨테이너"),
    )

    /**
     * 영어 라벨 하나를 대응하는 한국어 낱말들로 펼친다.
     *
     * @param label ML Kit 이 준 영어 라벨. 대소문자·앞뒤 공백은 알아서 정리한다
     * @return 사전에 있으면 한국어 낱말들, 없으면 빈 리스트
     */
    fun expand(label: String): List<String> {
        val key = label.lowercase().trim()
        return map[key].orEmpty()
    }
}

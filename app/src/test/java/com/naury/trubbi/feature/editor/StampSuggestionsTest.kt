package com.naury.trubbi.feature.editor

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 편집기 워터마크 추천 문구 테스트.
 *
 * FrameKit 워터마크는 한 줄 60자, 추천 8개까지만 받고 그보다 길거나 줄바꿈이 있으면 설정 오류로 편집기가 열리지
 * 않는다. 기록의 정보 줄이 어떻게 생겨도 그 한계 안에 드는지 본다.
 */
class StampSuggestionsTest {

    @Test
    fun `정보 줄이 없으면 추천하지 않는다`() {
        assertThat(StampSuggestions.from(emptyList())).isNull()
        assertThat(StampSuggestions.from(listOf(" ", ""))).isNull()
    }

    @Test
    fun `첫 문구는 모든 줄을 가운뎃점으로 잇고 각 줄도 따로 고를 수 있다`() {
        val stamp = StampSuggestions.from(listOf("2026.10.20 14:32", "서울 강남구 역삼동"))!!

        assertThat(stamp.primary).isEqualTo("2026.10.20 14:32 · 서울 강남구 역삼동")
        assertThat(stamp.suggestions).containsExactly(
            "2026.10.20 14:32 · 서울 강남구 역삼동",
            "2026.10.20 14:32",
            "서울 강남구 역삼동",
        ).inOrder()
    }

    @Test
    fun `줄바꿈이 섞여도 한 줄로 만든다`() {
        val stamp = StampSuggestions.from(listOf("물류센터\n3번 도크"))!!

        assertThat(stamp.primary).isEqualTo("물류센터 3번 도크")
        assertThat(stamp.suggestions.none { '\n' in it }).isTrue()
    }

    @Test
    fun `긴 문구와 많은 줄도 한계 안에 든다`() {
        val lines = List(12) { "아주 긴 장소 이름이 이어지는 줄 번호 $it 입니다 정말로 길게 씁니다" }
        val stamp = StampSuggestions.from(lines)!!

        assertThat(stamp.primary.length).isAtMost(StampSuggestions.MAX_LENGTH)
        assertThat(stamp.suggestions.size).isAtMost(StampSuggestions.MAX_SUGGESTIONS)
        assertThat(stamp.suggestions.all { it.length <= StampSuggestions.MAX_LENGTH }).isTrue()
    }

    @Test
    fun `같은 문구는 한 번만 추천한다`() {
        val stamp = StampSuggestions.from(listOf("역삼동"))!!

        assertThat(stamp.suggestions).containsExactly("역삼동")
    }
}

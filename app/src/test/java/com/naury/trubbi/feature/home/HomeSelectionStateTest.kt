package com.naury.trubbi.feature.home

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 홈 목록 다중 선택 전이 테스트.
 *
 * 사진 여러 장이 기록 하나에 묶여 저장되기 때문에 선택이 기록 단위와 미디어 단위 두 층으로
 * 나뉜다. 묶음을 탭했을 때 전체 선택인지 전체 해제인지, 길게 눌렀을 때 선택을 유지하는지가
 * 화면에서 가장 헷갈리는 부분이라 그 전이를 고정해 둔다.
 */
class HomeSelectionStateTest {

    private val state = HomeSelectionState()

    @Test
    fun `처음에는 아무것도 고르지 않은 상태다`() {
        assertThat(state.isEmpty).isTrue()
        assertThat(state.records.value).isEmpty()
        assertThat(state.media.value).isEmpty()
        assertThat(state.expandedBatch.value).isNull()
    }

    @Test
    fun `같은 기록을 두 번 누르면 선택이 풀린다`() {
        state.toggleRecord("r1")
        assertThat(state.records.value).containsExactly("r1")

        state.toggleRecord("r1")
        assertThat(state.records.value).isEmpty()
        assertThat(state.isEmpty).isTrue()
    }

    @Test
    fun `기록 선택과 미디어 선택은 서로를 건드리지 않는다`() {
        state.toggleRecord("r1")
        state.toggleMedia("m1")

        assertThat(state.records.value).containsExactly("r1")
        assertThat(state.media.value).containsExactly("m1")
    }

    @Test
    fun `묶음을 펼쳤다 다시 누르면 접히고 선택은 그대로다`() {
        state.toggleMedia("m1")
        state.toggleExpand("r1")
        assertThat(state.expandedBatch.value).isEqualTo("r1")

        state.toggleExpand("r1")
        assertThat(state.expandedBatch.value).isNull()
        assertThat(state.media.value).containsExactly("m1")
    }

    @Test
    fun `다른 묶음을 펼치면 이전 묶음은 닫힌다`() {
        state.toggleExpand("r1")
        state.toggleExpand("r2")
        assertThat(state.expandedBatch.value).isEqualTo("r2")
    }

    @Test
    fun `묶음을 탭하면 전체 선택하고 펼친다`() {
        state.toggleBatch("r1", listOf("m1", "m2", "m3"))

        assertThat(state.media.value).containsExactly("m1", "m2", "m3")
        assertThat(state.expandedBatch.value).isEqualTo("r1")
    }

    @Test
    fun `일부만 골라 둔 묶음을 탭하면 나머지가 채워진다`() {
        state.toggleMedia("m1")
        state.toggleBatch("r1", listOf("m1", "m2", "m3"))

        assertThat(state.media.value).containsExactly("m1", "m2", "m3")
    }

    @Test
    fun `이미 전부 고른 묶음을 탭하면 전체 해제하고 접는다`() {
        state.toggleBatch("r1", listOf("m1", "m2"))
        state.toggleBatch("r1", listOf("m1", "m2"))

        assertThat(state.media.value).isEmpty()
        assertThat(state.expandedBatch.value).isNull()
    }

    @Test
    fun `전부 고른 묶음을 해제해도 다른 묶음 선택은 남는다`() {
        state.toggleMedia("other")
        state.toggleBatch("r1", listOf("m1", "m2"))
        state.toggleBatch("r1", listOf("m1", "m2"))

        assertThat(state.media.value).containsExactly("other")
    }

    @Test
    fun `빈 묶음을 탭하면 해제가 아니라 펼치기로 처리한다`() {
        state.toggleBatch("r1", emptyList())

        assertThat(state.expandedBatch.value).isEqualTo("r1")
        assertThat(state.media.value).isEmpty()
    }

    @Test
    fun `길게 누르면 전체가 선택된 채로 펼쳐진다`() {
        state.enterBatch("r1", listOf("m1", "m2"))

        assertThat(state.media.value).containsExactly("m1", "m2")
        assertThat(state.expandedBatch.value).isEqualTo("r1")
    }

    @Test
    fun `펼쳐 둔 묶음을 다시 길게 누르면 접기만 하고 선택은 유지한다`() {
        state.enterBatch("r1", listOf("m1", "m2"))
        state.enterBatch("r1", listOf("m1", "m2"))

        assertThat(state.expandedBatch.value).isNull()
        assertThat(state.media.value).containsExactly("m1", "m2")
    }

    @Test
    fun `비우면 선택과 펼친 묶음이 모두 사라진다`() {
        state.toggleRecord("r1")
        state.enterBatch("r2", listOf("m1"))

        state.clear()

        assertThat(state.isEmpty).isTrue()
        assertThat(state.expandedBatch.value).isNull()
    }
}

package com.naury.trubbi.ui.designsystem

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * 앱 공통 드래그 순서 변경 리스트 — **핸들을 잡고 끌면 여러 단계도 한 번에** 이동한다.
 *
 * 드래그 중에는 잡은 행만 떠서 움직이고(제스처 끊김 방지), **손을 떼는 순간** 전체 이동량으로 목표
 * 위치를 계산해 한 번에 재배치한 뒤 [onReorder]로 새 순서를 알려준다. [rowContent]에 넘겨주는
 * `dragHandle` Modifier를 손잡이 아이콘에 붙이면 그 부분에서만 드래그가 시작된다.
 *
 * @param items 외부 소스 순서(드래그가 끝나면 [onReorder]로 새 순서를 받아 갱신).
 * @param keyOf 각 항목의 안정적 키(드래그 제스처가 항목을 따라가도록).
 * @param rowHeight 각 행의 고정 높이(이동 칸 수 계산에 사용).
 * @param onReorder 드래그가 끝나면 새 순서로 불린다.
 * @param modifier 바깥 배치용.
 * @param gap 행 사이 간격.
 * @param rowContent 행 하나를 그린다. `dragHandle` 을 손잡이에 붙여야 그 자리에서만 드래그가 시작된다.
 */
@Composable
fun <T> DragReorderColumn(
    items: List<T>,
    keyOf: (T) -> Any,
    rowHeight: Dp,
    onReorder: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    gap: Dp = 8.dp,
    rowContent: @Composable (index: Int, item: T, dragHandle: Modifier, dragging: Boolean) -> Unit,
) {
    val local = remember { mutableStateListOf<T>().also { it.addAll(items) } }
    var dragging by remember { mutableIntStateOf(-1) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val stepPx = with(density) { (rowHeight + gap).toPx() }
    // 드래그 중이 아닐 때만 외부 순서로 재동기화(추가/삭제/커밋 반영). 드래그 중엔 로컬만 움직인다.
    val sig = items.map(keyOf)
    LaunchedEffect(sig) {
        if (dragging < 0) {
            local.clear()
            local.addAll(items)
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        local.forEachIndexed { index, item ->
            val isDragging = index == dragging
            val handle = Modifier.pointerInput(keyOf(item)) {
                detectDragGestures(
                    onDragStart = {
                        dragging = index
                        offsetY = 0f
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        offsetY += drag.y
                    },
                    onDragEnd = {
                        val from = dragging
                        if (from in local.indices) {
                            val target = (from + (offsetY / stepPx).roundToInt()).coerceIn(0, local.lastIndex)
                            if (target != from) {
                                local.add(target, local.removeAt(from))
                                onReorder(local.toList())
                            }
                        }
                        dragging = -1
                        offsetY = 0f
                    },
                    onDragCancel = {
                        dragging = -1
                        offsetY = 0f
                    },
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragging) offsetY else 0f },
            ) {
                rowContent(index, item, handle, isDragging)
            }
        }
    }
}

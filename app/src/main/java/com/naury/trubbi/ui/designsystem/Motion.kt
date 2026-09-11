package com.naury.trubbi.ui.designsystem

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 애니메이션 토큰. 화면마다 제각각인 움직임을 막으려고 세 가지로 못 박았다.
 *
 * 지속 시간(`tween`)이 아니라 스프링을 쓴다. 값이 얼마나 멀리 바뀌든 같은 ‘탄성’으로 도착해서,
 * 작은 토글과 큰 시트가 같은 성격으로 움직인다.
 */
object TrubbiMotion {
    /** 목표를 살짝 지나쳤다 돌아온다 — 선택·배지·주인공 등장. */
    fun <T> bouncy() = spring<T>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)

    /** 빠르되 튐이 조금 남는다 — 누름·토글. */
    fun <T> snappy() = spring<T>(dampingRatio = 0.7f, stiffness = 420f)

    /** 부드럽게 가라앉는다 — 배치 이동·색 전환. */
    fun <T> gentle() = spring<T>(dampingRatio = 0.9f, stiffness = 260f)
}

/**
 * 처음 나타날 때 한 번만 도는 등장 효과 — 페이드 + 아래에서 올라오며 커진다.
 *
 * @param delayMs 시작을 늦출 시간. 목록 항목마다 조금씩 늘리면 차례로 펼쳐진다
 */
@Composable
fun Modifier.appearOnce(delayMs: Int = 0): Modifier {
    var shown by remember { mutableStateOf(false) }
    val p by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 260f),
        label = "appear",
    )
    LaunchedEffect(Unit) {
        if (delayMs > 0) delay(delayMs.toLong())
        shown = true
    }
    return this.graphicsLayer {
        alpha = p
        val s = 0.94f + 0.06f * p
        scaleX = s
        scaleY = s
        translationY = (1f - p) * 22.dp.toPx()
    }
}

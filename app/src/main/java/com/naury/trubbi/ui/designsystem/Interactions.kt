package com.naury.trubbi.ui.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.naury.trubbi.ui.theme.LocalTrubbiColors

/**
 * 터치 반응과 화면 배경 수정자.
 */

/**
 * 누르면 축소되는 클릭 수정자. 리플 대신 쓴다 — 리플은 손가락에 가려 눌린 것이 확인되지 않는다.
 *
 * @param enabled false 면 눌리지 않고 크기도 변하지 않는다
 * @param scaleDown 눌렸을 때 줄어들 비율
 * @param interactionSource 호출부에서 눌림 상태를 함께 관찰할 때 전달한다
 * @param onClick 눌렀을 때
 */
fun Modifier.pressable(
    enabled: Boolean = true,
    scaleDown: Float = 0.96f,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) scaleDown else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 750f),
        label = "pressScale",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}

/** 로그인·환영 화면 전용 배경. 테마 설정과 무관하게 고정된다. */
@Composable
fun AuthHeroBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF242428), Color(0xFF0B0B0D))))
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.06f), Color.Transparent),
                        center = Offset(size.width * 0.2f, size.height * 0.12f),
                        radius = size.maxDimension * 0.55f,
                    ),
                )
            },
    ) { content() }
}

/**
 * 앱 전체 배경. 위에 올라가는 표면들이 블러 처리할 원본이 된다.
 *
 * @param modifier 바깥 배치용
 * @param content 배경 위에 그릴 내용
 */
@Composable
fun TrubbiBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = LocalTrubbiColors.current
    val bg = if (c.isDark) {
        MaterialTheme.colorScheme.background
    } else {
        com.naury.trubbi.ui.theme.MonoCanvasLight
    }
    val primary = MaterialTheme.colorScheme.primary
    val violet = c.accent2
    val teal = MaterialTheme.colorScheme.secondary
    val aTop = if (c.isDark) 0.30f else 0.06f
    val aMid = if (c.isDark) 0.24f else 0.05f
    val aBot = if (c.isDark) 0.20f else 0.04f
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .matchParentSize()
                .background(bg)
                .drawBehind {
                    val r = size.maxDimension
                    drawRect(
                        Brush.radialGradient(
                            listOf(primary.copy(alpha = aTop), Color.Transparent),
                            center = Offset(size.width * 0.08f, size.height * -0.02f),
                            radius = r * 0.62f,
                        ),
                    )
                    drawRect(
                        Brush.radialGradient(
                            listOf(violet.copy(alpha = aMid), Color.Transparent),
                            center = Offset(size.width * 1.02f, size.height * 0.12f),
                            radius = r * 0.55f,
                        ),
                    )
                    drawRect(
                        Brush.radialGradient(
                            listOf(teal.copy(alpha = aBot), Color.Transparent),
                            center = Offset(size.width * 0.95f, size.height * 0.98f),
                            radius = r * 0.5f,
                        ),
                    )
                },
        ) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val orb = if (c.isDark) 0.34f else 0.07f
                Box(
                    Modifier.align(Alignment.TopStart).offset(x = (-60).dp, y = (-50).dp)
                        .size(240.dp).blur(100.dp).background(primary.copy(alpha = orb), CircleShape),
                )
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 70.dp, y = 30.dp)
                        .size(200.dp).blur(110.dp).background(violet.copy(alpha = orb), CircleShape),
                )
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(x = 40.dp, y = 60.dp)
                        .size(220.dp).blur(120.dp).background(teal.copy(alpha = orb * 0.8f), CircleShape),
                )
            }
        }
        content()
    }
}

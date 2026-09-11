package com.naury.trubbi.ui.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.trubbi.R
import com.naury.trubbi.ui.theme.LocalTrubbiColors

/**
 * 하단 독에 들어가는 빠른 동작 하나.
 *
 * @property label 아이콘 옆에 붙는 문구
 * @property icon 동작 아이콘
 * @property accent 아이콘 색
 * @property onClick 눌렀을 때. 누르면 독은 자동으로 닫힌다
 */
/**
 * 화면 하단에 떠 있는 빠른 동작 독.
 */

data class DockAction(
    val label: String,
    val icon: ImageVector,
    val accent: Color,
    val onClick: () -> Unit,
)

/**
 * 화면 하단에 떠 있는 독. 가운데 ‘+’ 를 누르면 빠른 동작들이 위로 펼쳐진다.
 *
 * 하단 탭 바 대신 이 독이 화면 이동과 촬영 진입을 모두 담당한다.
 * 화면 내용 위에 겹쳐 배치한다(전체 크기 Box 안에서 하단 정렬).
 *
 * @param actions 펼쳤을 때 표시할 동작들. 목록 순서대로 위에서 아래로 쌓인다
 * @param modifier 바깥 배치용
 * @param onCapture 지정하면 ‘+’ 옆에 촬영 버튼이 함께 표시된다
 * @param onPlaceSearch 지정하면 ‘+’ 오른쪽에 장소 검색 버튼이 함께 표시된다
 */
@Composable
fun QuickDock(
    actions: List<DockAction>,
    modifier: Modifier = Modifier,
    onCapture: (() -> Unit)? = null,
    onPlaceSearch: (() -> Unit)? = null,
) {
    val c = LocalTrubbiColors.current
    var open by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (open) 45f else 0f,
        animationSpec = TrubbiMotion.bouncy(),
        label = "dockFab",
    )
    // 닫혀 있는 동안 4.6초마다 두 번 부풀었다 쉰다. 화면이 멈춰 있어도 눌러도 되는 자리라는 걸 알린다.
    val breathTransition = rememberInfiniteTransition(label = "fabBreath")
    val breath by breathTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4600
                1f at 0
                1.08f at 480 using FastOutSlowInEasing
                1f at 960 using FastOutSlowInEasing
                1.05f at 1380 using FastOutSlowInEasing
                1f at 1800 using FastOutSlowInEasing
                1f at 4600
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "fabBreathScale",
    )
    // 같은 주기로 물결 두 개가 퍼져 나간다.
    val ripple1 by breathTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4600
                0f at 0
                1f at 1500 using FastOutSlowInEasing
                1f at 4600
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "fabRipple1",
    )
    val ripple2 by breathTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4600
                0f at 0
                0f at 420
                1f at 1920 using FastOutSlowInEasing
                1f at 4600
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "fabRipple2",
    )

    Box(modifier.fillMaxSize()) {
        // 펼친 상태의 딤. 아무 데나 누르면 닫힌다.
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { open = false },
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnimatedVisibility(
                visible = open,
                enter = fadeIn() + slideInVertically { it / 2 } + scaleIn(initialScale = 0.7f),
                exit = fadeOut() + slideOutVertically { it / 2 } + scaleOut(targetScale = 0.7f),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    actions.forEach { action ->
                        DockItem(action) {
                            action.onClick()
                            open = false
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // 강조 카메라 버튼(메뉴 열려 있을 땐 숨김).
                if (onCapture != null) {
                    AnimatedVisibility(
                        visible = !open,
                        enter = fadeIn() + scaleIn(initialScale = 0.7f),
                        exit = fadeOut() + scaleOut(targetScale = 0.7f),
                    ) {
                        EmphasisFab(AppIcons.CameraAlt, stringResource(R.string.floating_capture), onCapture)
                    }
                }

                // 가운데 + / × 버튼.
                Box(contentAlignment = Alignment.Center) {
                    RippleRing(progress = ripple1, visible = !open)
                    RippleRing(progress = ripple2, visible = !open)
                    Box(
                        Modifier
                            .graphicsLayer {
                                val s = if (open) 1f else breath
                                scaleX = s
                                scaleY = s
                            }
                            .shadow(14.dp, RoundedCornerShape(percent = 50), clip = false)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(Brush.linearGradient(c.gradient))
                            .pressable { open = !open }
                            .padding(18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            AppIcons.Add,
                            contentDescription = if (open) stringResource(R.string.action_close) else stringResource(R.string.quickdock_title),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(30.dp).rotate(rotation),
                        )
                    }
                }

                // 강조 장소검색 버튼(오른쪽, 메뉴 열려 있을 땐 숨김).
                if (onPlaceSearch != null) {
                    AnimatedVisibility(
                        visible = !open,
                        enter = fadeIn() + scaleIn(initialScale = 0.7f),
                        exit = fadeOut() + scaleOut(targetScale = 0.7f),
                    ) {
                        EmphasisFab(AppIcons.LocationOn, stringResource(R.string.action_place_search), onPlaceSearch)
                    }
                }
            }
        }
    }
}

/** 강조용 원형 FAB — 브랜드 색 채움 + 흰 아이콘(촬영·장소검색 등 핵심 동작). */
@Composable
private fun EmphasisFab(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .shadow(12.dp, RoundedCornerShape(percent = 50), clip = false)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.primary)
            .pressable { onClick() }
            .padding(15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(26.dp),
        )
    }
}

/** 버튼 뒤로 퍼지며 사라지는 고리 하나. */
@Composable
private fun BoxScope.RippleRing(progress: Float, visible: Boolean) {
    val ringColor = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .matchParentSize()
            .graphicsLayer {
                val s = 1f + progress * 0.95f
                scaleX = s
                scaleY = s
                alpha = if (!visible || progress <= 0.01f) 0f else (1f - progress) * 0.45f
            }
            .border(1.5.dp, ringColor, RoundedCornerShape(percent = 50)),
    )
}

@Composable
private fun DockItem(action: DockAction, onClick: () -> Unit) {
    val c = LocalTrubbiColors.current
    Row(
        Modifier
            .glass(RoundedCornerShape(percent = 50), elevation = 10.dp)
            .pressable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            action.label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(action.accent.copy(alpha = if (c.isDark) 0.28f else 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(action.icon, contentDescription = null, tint = action.accent, modifier = Modifier.size(21.dp))
        }
    }
}

package com.naury.trubbi.feature.journey

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.trubbi.domain.journey.SendOff
import com.naury.trubbi.domain.journey.SendOffKind
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.pressable
import com.naury.trubbi.ui.theme.LocalTrubbiColors
import kotlinx.coroutines.delay

/**
 * 단계가 바뀐 순간 화면을 덮는 연출.
 *
 * 운전 중에 화면을 오래 보지 못하므로, 확인 문구는 짧게 띄우고 스스로 사라진다.
 */
/**
 * 단계가 바뀐 직후 화면 가운데에 잠깐 뜨는 확인 카드.
 *
 * 사용자 입력 없이 일정 시간 뒤 자동으로 사라진다.
 */
@Composable
fun StageConfirmOverlay(confirm: com.naury.trubbi.domain.journey.StageConfirm?, onDone: () -> Unit) {
    AnimatedVisibility(visible = confirm != null, enter = fadeIn(), exit = fadeOut()) {
        val c = LocalTrubbiColors.current
        var shown by remember { mutableStateOf(confirm) }
        if (confirm != null) shown = confirm
        LaunchedEffect(confirm) {
            if (confirm != null) {
                delay(1500)
                onDone()
            }
        }

        val pop = remember(confirm) { androidx.compose.animation.core.Animatable(0.6f) }
        LaunchedEffect(confirm) { if (confirm != null) pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 380f)) }

        Box(
            Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.32f))
                .pressable(onClick = onDone),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                        alpha = pop.value
                    }
                    .clip(RoundedCornerShape(26.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 28.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(66.dp).clip(RoundedCornerShape(percent = 50)).background(Brush.linearGradient(c.gradient)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (shown?.started == true) AppIcons.PlayArrow else AppIcons.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(34.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    shown?.message.orEmpty(),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

// ── 도착 직후 물음 ───────────────────────────────────────────────────────────

/** 여정 시작·도착·완료 순간에 잠깐 뜨는 전체 화면 연출. */
@Composable
fun SendOffOverlay(sendOff: SendOff?, onDone: () -> Unit) {
    AnimatedVisibility(visible = sendOff != null, enter = fadeIn(), exit = fadeOut()) {
        val c = LocalTrubbiColors.current
        // 퇴장 애니메이션 중에도 내용이 필요하므로 마지막 non-null 값을 유지한다.
        var shown by remember { mutableStateOf(sendOff) }
        if (sendOff != null) shown = sendOff
        LaunchedEffect(sendOff) {
            if (sendOff != null) {
                delay(3200)
                onDone()
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDone() },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                // 상황별 아이콘 애니메이션 (시작=트럭 주행, 도착=핀 드롭, 완료=체크 팝, 여정완료=깃발).
                SendOffBadge(kind = shown?.kind ?: SendOffKind.START)
                Spacer(Modifier.height(22.dp))
                Text(
                    shown?.title.orEmpty(),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                shown?.subtitle?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.secondaryLabel,
                    )
                }
            }
        }
    }
}

/**
 * 배웅 오버레이의 상황별 아이콘: 그라디언트 원 + 퍼지는 링 위에서
 * 시작=트럭 주행, 도착=핀 드롭(바운스), 작업완료=체크 팝, 여정완료=깃발 펄럭.
 */
@Composable
private fun SendOffBadge(kind: SendOffKind) {
    val c = LocalTrubbiColors.current
    val primary = MaterialTheme.colorScheme.primary

    // 등장 스프링 팝 (kind가 바뀌면 다시 튀어나온다).
    val pop = remember(kind) { androidx.compose.animation.core.Animatable(0.55f) }
    LaunchedEffect(kind) {
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 360f))
    }
    val inf = rememberInfiniteTransition(label = "sendOffBadge")
    // 바깥으로 잔잔히 퍼지는 링.
    val ripple by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "sendOffRipple",
    )
    // kind별 반복 모션 소스 (왕복 0..1).
    val motion by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sendOffMotion",
    )

    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        // 퍼지는 링.
        Box(
            Modifier
                .size(116.dp)
                .graphicsLayer {
                    val s = 1f + ripple * 0.45f
                    scaleX = s
                    scaleY = s
                    alpha = (1f - ripple) * 0.28f
                }
                .clip(RoundedCornerShape(percent = 50))
                .background(primary),
        )
        // 본체.
        Box(
            Modifier
                .size(116.dp)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                }
                .clip(RoundedCornerShape(percent = 50))
                .background(Brush.linearGradient(c.gradient)),
            contentAlignment = Alignment.Center,
        ) {
            val iconMod = Modifier.size(52.dp)
            when (kind) {
                // 트럭이 좌우로 부드럽게 주행.
                SendOffKind.START -> Icon(
                    AppIcons.Truck,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = iconMod.graphicsLayer {
                        translationX = (motion - 0.5f) * 16f
                        translationY = (motion - 0.5f) * 4f
                    },
                )
                // 핀이 위에서 떨어지며 바운스 (스프링 오버슈트).
                SendOffKind.ARRIVE -> Icon(
                    AppIcons.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = iconMod.graphicsLayer {
                        translationY = (1f - pop.value) * -90f
                    },
                )
                // 체크가 팝! 하고 커진다.
                SendOffKind.DONE -> Icon(
                    AppIcons.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = iconMod.graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                    },
                )
                // 깃발이 깃대를 축으로 펄럭.
                SendOffKind.COMPLETE -> Icon(
                    AppIcons.Flag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = iconMod.graphicsLayer {
                        rotationZ = (motion - 0.5f) * 18f
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.2f, 0.9f)
                    },
                )
            }
        }
    }
}

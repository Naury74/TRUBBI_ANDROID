package com.naury.trubbi.feature.journey

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.trubbi.R
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.PillButton
import com.naury.trubbi.ui.designsystem.pressable
import com.naury.trubbi.ui.designsystem.sentenceLines
import com.naury.trubbi.ui.theme.LocalTrubbiColors

/**
 * 마법사가 듣고 있는 동안 보여 주는 화면 조각들.
 *
 * 인식 중·처리 중·자동 시작 카운트다운은 서로 다른 상태라 한 번에 하나만 뜬다.
 */
/**
 * 음성 인식 전용 바텀시트 — 말하는 동안 실시간 자막을 크게 보여준다.
 * 뒤 화면을 가려 지금 음성 입력 중임을 알리고, 탭이나 취소로 닫는다.
 */
@Composable
internal fun VoiceListeningSheet(
    listening: Boolean,
    partial: String,
    level: Float,
    guide: String,
    hint: String,
    onCancel: () -> Unit,
) {
    val c = LocalTrubbiColors.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val enter by animateFloatAsState(targetValue = if (shown) 1f else 0f, animationSpec = tween(220), label = "voiceSheet")
    val density = androidx.compose.ui.platform.LocalDensity.current
    val slide = with(density) { 80.dp.toPx() }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f * enter))
                .pressable(onClick = onCancel),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = (1f - enter) * slide
                    alpha = enter
                }
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(percent = 50)).background(c.separator))
            Spacer(Modifier.height(14.dp))
            if (guide.isNotBlank()) {
                Text(
                    guide.sentenceLines(),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
            }
            if (listening) {
                ListeningPanel(partial = partial, level = level, hint = hint, onCancel = onCancel)
            } else {
                ProcessingPanel(stringResource(R.string.journey_receipt_reading))
            }
            Spacer(Modifier.height(14.dp))
            PillButton(
                text = stringResource(R.string.action_cancel),
                icon = AppIcons.Close,
                onClick = onCancel,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            )
        }
    }
}

/** 받아쓰기/의도 파악 중 로딩 — 마이크가 꺼진 뒤의 빈 시간을 채운다. */
@Composable
private fun ProcessingPanel(text: String) {
    val c = LocalTrubbiColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (c.isDark) 0.10f else 0.06f))
            .padding(vertical = 18.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        com.naury.kinetica.ui.KLoadingIndicator(size = 20.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** EXTRA 무발화: "N초 후 시작" 카운트다운 + [지금 시작]/[더 추가]. */
@Composable
internal fun CountdownStartCard(seconds: Int, onStartNow: () -> Unit, onAddMore: () -> Unit) {
    val c = LocalTrubbiColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (c.isDark) 0.16f else 0.09f))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(percent = 50)).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text("$seconds", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.wizard_countdown_start, seconds),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(text = stringResource(R.string.wizard_start_now), icon = AppIcons.Truck, onClick = onStartNow, modifier = Modifier.weight(1f))
            PillButton(
                text = stringResource(R.string.wizard_add_more),
                icon = AppIcons.Mic,
                onClick = onAddMore,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 인앱 듣기 패널: 마이크 주위 링이 목소리 크기에 반응하고,
 * 말하는 내용이 실시간 자막으로 보인다. 탭하면 취소.
 */
@Composable
private fun ListeningPanel(
    partial: String,
    level: Float,
    hint: String,
    onCancel: () -> Unit,
) {
    val c = LocalTrubbiColors.current
    val primary = MaterialTheme.colorScheme.primary
    val ring by animateFloatAsState(
        targetValue = 1f + level * 0.9f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 240f),
        label = "micRing",
    )
    val idle = rememberInfiniteTransition(label = "micIdle")
    val breathe by idle.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "micBreathe",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(primary.copy(alpha = if (c.isDark) 0.10f else 0.06f))
            .pressable(onClick = onCancel)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(58.dp)
                    .graphicsLayer {
                        scaleX = ring * breathe
                        scaleY = ring * breathe
                        alpha = 0.22f
                    }
                    .clip(RoundedCornerShape(percent = 50))
                    .background(primary),
            )
            Box(
                Modifier
                    .size(58.dp)
                    .graphicsLayer {
                        scaleX = 1f + level * 0.35f
                        scaleY = 1f + level * 0.35f
                        alpha = 0.30f
                    }
                    .clip(RoundedCornerShape(percent = 50))
                    .background(primary),
            )
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Brush.linearGradient(c.gradient)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        AnimatedContent(targetState = partial.ifBlank { hint }, label = "micCaption") { text ->
            Text(
                text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = if (partial.isBlank()) FontWeight.Medium else FontWeight.Bold,
                ),
                color = if (partial.isBlank()) c.secondaryLabel else MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.wizard_listening_hint),
            style = MaterialTheme.typography.labelSmall,
            color = c.secondaryLabel,
        )
    }
}

/** 작게 뛰는 점. 무거운 스피너 대신 쓰는 ‘찾는 중’ 표시. */
@Composable
internal fun PulsingDot() {
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    Box(
        Modifier
            .size(8.dp)
            .graphicsLayer { alpha = a }
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.primary),
    )
}

/** 음성 인식 실패 시: 안내 + 다시 말하기 (상용앱 공통 패턴). */
@Composable
internal fun RetryBanner(message: String, onRetry: () -> Unit) {
    val c = LocalTrubbiColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.red.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Mic, contentDescription = null, tint = c.red, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        RetryChip(onRetry)
    }
}

/** 인식 결과를 보여주고 — 수정/다시 말하기/대안 선택으로 오인식을 바로잡게 한다. */
@Composable
internal fun HeardBanner(
    heard: String,
    alternatives: List<String>,
    onRetry: () -> Unit,
    onPickAlt: (String) -> Unit,
) {
    val c = LocalTrubbiColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (c.isDark) 0.4f else 0.55f))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.wizard_heard), style = MaterialTheme.typography.labelMedium, color = c.secondaryLabel)
                Text(
                    "“$heard”",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            RetryChip(onRetry)
        }
        if (alternatives.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.wizard_maybe_this), style = MaterialTheme.typography.labelMedium, color = c.secondaryLabel)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(alternatives) { alt ->
                    QuickChipSmall(icon = null, label = alt) { onPickAlt(alt) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.wizard_heard_hint),
            style = MaterialTheme.typography.labelSmall,
            color = c.secondaryLabel,
        )
    }
}

@Composable
private fun RetryChip(onRetry: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.primary)
            .pressable(onClick = onRetry)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            stringResource(R.string.wizard_say_again),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

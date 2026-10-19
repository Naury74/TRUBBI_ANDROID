package com.naury.trubbi.feature.journey

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.trubbi.R
import com.naury.trubbi.data.i18n.routeLabel
import com.naury.trubbi.data.local.JourneyWithStops
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.glass
import com.naury.trubbi.ui.designsystem.pressable
import com.naury.trubbi.ui.theme.LocalTrubbiColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 진행 중 여정이 없을 때 홈에 놓이는 카드 — 시작 입구와 지난 여정 입구.
 */
/** 진행 중인 여정이 없을 때 보이는 카드. 한 번 누르면 시작 시트가 열린다. */
@Composable
fun JourneyStartCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalTrubbiColors.current
    val primary = MaterialTheme.colorScheme.primary
    val glow = rememberInfiniteTransition(label = "startGlow")
    val pulse by glow.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "startGlowPulse",
    )
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                val s = 1f + pulse * 0.012f
                scaleX = s
                scaleY = s
            }
            .shadow(elevation = (6 + pulse * 12).dp, shape = shape, clip = false, ambientColor = primary, spotColor = primary)
            .glass(shape)
            .border(width = (1 + pulse * 1.5f).dp, color = primary.copy(alpha = 0.25f + pulse * 0.45f), shape = shape)
            .pressable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 시작 카드의 트럭은 주기적으로 까딱이며 출발을 권한다.
        val invite = rememberInfiniteTransition(label = "startTruck")
        val wiggle by invite.animateFloat(
            initialValue = 0f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 3600
                    0f at 0
                    -9f at 220 using FastOutSlowInEasing
                    8f at 440 using FastOutSlowInEasing
                    -5f at 640 using FastOutSlowInEasing
                    0f at 840 using FastOutSlowInEasing
                    0f at 3600
                },
                repeatMode = RepeatMode.Restart,
            ),
            label = "startTruckWiggle",
        )
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Brush.linearGradient(c.gradient)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                AppIcons.Truck,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(23.dp).graphicsLayer { rotationZ = wiggle },
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.journey_start_cta),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.journey_start_cta_sub),
                style = MaterialTheme.typography.bodySmall,
                color = c.secondaryLabel,
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(40.dp)
                .graphicsLayer {
                    val s = 1f + pulse * 0.06f
                    scaleX = s
                    scaleY = s
                }
                .clip(RoundedCornerShape(percent = 50))
                .background(Brush.linearGradient(c.gradient)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * 지난 여정 카드 — 헤더(전체 리스트 진입) + 최근 완료 여정 3건(각각 바로 진입).
 */
@Composable
fun PastJourneysCard(
    journeys: List<JourneyWithStops>,
    totalCount: Int,
    onOpenAll: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalTrubbiColors.current
    val datePattern = stringResource(R.string.date_month_day_pattern)
    val fmt = remember(datePattern) { SimpleDateFormat(datePattern, Locale.getDefault()) }
    Column(modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().pressable(onClick = onOpenAll).padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.journey_history_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(pluralStringResource(R.plurals.count_items, totalCount, totalCount), style = MaterialTheme.typography.labelLarge, color = c.secondaryLabel)
            Spacer(Modifier.width(4.dp))
            Icon(AppIcons.ChevronRight, contentDescription = null, tint = c.secondaryLabel, modifier = Modifier.size(18.dp))
        }
        if (journeys.isNotEmpty()) {
            androidx.compose.material3.HorizontalDivider(color = c.separator, modifier = Modifier.padding(horizontal = 14.dp))
            journeys.forEach { j ->
                Row(
                    Modifier.fillMaxWidth().pressable { onOpen(j.journey.journeyId) }.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(percent = 50)).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(AppIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            j.routeLabel(),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        j.journey.completedAt?.let {
                            Text(
                                stringResource(R.string.journey_completed_at, fmt.format(Date(it))),
                                style = MaterialTheme.typography.labelMedium,
                                color = c.secondaryLabel,
                            )
                        }
                    }
                    Icon(AppIcons.ChevronRight, contentDescription = null, tint = c.secondaryLabel, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

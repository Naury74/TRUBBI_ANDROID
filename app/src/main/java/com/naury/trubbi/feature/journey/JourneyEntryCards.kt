package com.naury.trubbi.feature.journey

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
/**
 * 진행 중인 여정이 없을 때 보이는 카드. 한 번 누르면 시작 시트가 열린다.
 *
 * 홈에서 가장 중요한 동작이라 브랜드 색으로 채운 카드로 둔다. 예전에는 흰 반투명 카드에 테두리가 깜박여
 * 눈에는 띄었지만 값싸 보였다. 오른쪽에는 도로를 닮은 곡선을 옅게 깔아 앱 아이콘과 같은 인상을 준다.
 */
@Composable
fun JourneyStartCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalTrubbiColors.current
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(26.dp)
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
        modifier
            .fillMaxWidth()
            .shadow(elevation = 14.dp, shape = shape, clip = false, ambientColor = primary, spotColor = primary)
            .clip(shape)
            .background(Brush.linearGradient(c.gradient))
            .drawBehind { drawRoadCurves() }
            .pressable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    AppIcons.Truck,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(25.dp).graphicsLayer { rotationZ = wiggle },
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.journey_start_cta),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    stringResource(R.string.journey_start_cta_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.82f),
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(percent = 50)).background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.KeyboardArrowRight, contentDescription = null, tint = primary, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** 카드 오른쪽에 옅게 깔리는 두 갈래 도로 곡선. */
private fun DrawScope.drawRoadCurves() {
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = h * 0.10f, cap = StrokeCap.Round)
    val dash = Stroke(width = h * 0.018f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(h * 0.08f, h * 0.08f)))
    val road = Path().apply {
        moveTo(w * 0.62f, h * 1.15f)
        cubicTo(w * 0.74f, h * 0.55f, w * 0.86f, h * 0.45f, w * 1.08f, -h * 0.1f)
    }
    drawPath(road, Color.White.copy(alpha = 0.10f), style = stroke)
    drawPath(road, Color.White.copy(alpha = 0.22f), style = dash)
    drawCircle(Color.White.copy(alpha = 0.06f), radius = h * 0.9f, center = Offset(w * 1.02f, h * 0.05f))
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

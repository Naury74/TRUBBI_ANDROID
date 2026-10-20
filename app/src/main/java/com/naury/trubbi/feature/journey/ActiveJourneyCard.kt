package com.naury.trubbi.feature.journey

import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.video.videoFramePercent
import com.naury.trubbi.R
import com.naury.trubbi.core.i18n.label
import com.naury.trubbi.data.i18n.displayName
import com.naury.trubbi.data.i18n.routeLabel
import com.naury.trubbi.data.local.JourneyStopEntity
import com.naury.trubbi.data.local.JourneyWithStops
import com.naury.trubbi.data.local.RecordWithMedia
import com.naury.trubbi.domain.model.RecordType
import com.naury.trubbi.domain.model.StopKind
import com.naury.trubbi.domain.model.StopPhase
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.PillButton
import com.naury.trubbi.ui.designsystem.glass
import com.naury.trubbi.ui.designsystem.pressable
import com.naury.trubbi.ui.theme.LocalTrubbiColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * 진행 중 여정 카드 — 홈 상단에서 현재 정차지와 다음 동작을 보여 준다.
 */
/**
 * 홈의 ‘지금 이 여정’ 카드.
 *
 * 다음 단계 하나만 주 버튼으로 노출하고 나머지 동작은 보조 영역에 둔다.
 * 단계 진행은 되돌리기가 번거로워 동시에 여러 선택지를 두지 않는다.
 */
@Composable
fun ActiveJourneyCard(
    journey: JourneyWithStops,
    records: List<RecordWithMedia>,
    expenseTotal: Long,
    onAdvance: () -> Unit,
    onArriveNew: (StopKind) -> Unit,
    onAddStops: () -> Unit,
    onManageStops: () -> Unit,
    onComplete: () -> Unit,
    onCapture: () -> Unit,
    onOpenDetail: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalTrubbiColors.current
    val stops = journey.orderedStops
    val current = journey.currentStop

    Column(
        modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(28.dp))
            .padding(20.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LiveBadge()
            Spacer(Modifier.width(8.dp))
            DrivingTruckMini()
            Spacer(Modifier.weight(1f))
            Icon(
                AppIcons.Close,
                contentDescription = stringResource(R.string.action_cancel_journey),
                tint = c.secondaryLabel,
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .pressable(onClick = onCancel)
                    .padding(6.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onOpenDetail() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                journey.routeLabel(),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
        }

        PreciseLocationNotice(Modifier.padding(top = 12.dp))

        Spacer(Modifier.height(18.dp))

        if (stops.isEmpty()) {
            Text(
                stringResource(R.string.journey_stop_auto_note),
                style = MaterialTheme.typography.bodySmall,
                color = c.secondaryLabel,
            )
        } else {
            val currentIndex = stops.indexOfFirst { it.completedAt == null }.let { if (it < 0) stops.size else it }
            val movingSeg = if (current?.phase == StopPhase.MOVING && currentIndex in 1..stops.lastIndex) currentIndex - 1 else -1
            JourneySequence(stops = stops, currentIndex = currentIndex, movingSegment = movingSeg)
            // 남은 정차지가 둘 이상일 때만 순서를 바꿀 이유가 있다.
            if (stops.count { it.arrivedAt == null } >= 2) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .pressable(onClick = onManageStops)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.ArrowUpward, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.action_reorder_stops),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        current?.let { StatusLine(it) }

        Spacer(Modifier.height(18.dp))

        // 주 동작은 언제나 하나뿐이다 — 지금 눌러야 할 것만 크게 보여 준다.
        when {
            stops.isEmpty() -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(
                    text = stringResource(R.string.action_arrive_pickup),
                    icon = AppIcons.Pickup,
                    onClick = { onArriveNew(StopKind.PICKUP) },
                    modifier = Modifier.weight(1f).height(58.dp),
                )
                PillButton(
                    text = stringResource(R.string.action_arrive_dropoff),
                    icon = AppIcons.Dropoff,
                    onClick = { onArriveNew(StopKind.DROPOFF) },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).height(58.dp),
                )
            }
            current == null -> PillButton(
                text = stringResource(R.string.action_complete_journey),
                icon = AppIcons.Flag,
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth().height(58.dp),
            )
            else -> PillButton(
                text = when (current.phase) {
                    StopPhase.MOVING -> stringResource(R.string.action_arrive_at, current.displayName())
                    StopPhase.WAITING -> stringResource(R.string.action_start_kind, current.kindEnum.label())
                    StopPhase.WORKING -> stringResource(R.string.action_finish_kind, current.kindEnum.label())
                    StopPhase.DONE -> stringResource(R.string.action_go_next_stop)
                },
                icon = when (current.phase) {
                    StopPhase.MOVING -> AppIcons.LocationOn
                    else -> if (current.kindEnum == StopKind.PICKUP) AppIcons.Pickup else AppIcons.Dropoff
                },
                onClick = onAdvance,
                modifier = Modifier.fillMaxWidth().height(58.dp),
            )
        }

        Spacer(Modifier.height(10.dp))

        // 촬영을 가장 크게 둔다. 여정 중 가장 자주 누르는 버튼이다.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            QuickChip(AppIcons.PhotoCamera, stringResource(R.string.notif_action_capture), Modifier.weight(1.5f), accent = true, onClick = onCapture)
            QuickChip(AppIcons.Add, stringResource(R.string.action_stops), Modifier.weight(1f)) { onAddStops() }
            QuickChip(AppIcons.Description, stringResource(R.string.action_detail), Modifier.weight(1f), onClick = onOpenDetail)
        }

        val recentMedia = records
            .sortedByDescending { it.record.createdAt }
            .flatMap { it.media }
            .take(6)
        if (recentMedia.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                recentMedia.forEach { m -> JourneyMiniThumb(m, onClick = onOpenDetail) }
            }
        }

        val photos = records.count { it.record.type == RecordType.PHOTO.name || it.record.type == RecordType.VIDEO.name }
        if (photos > 0 || expenseTotal > 0 || records.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onOpenDetail() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SummaryItem(AppIcons.PhotoLibrary, pluralStringResource(R.plurals.photo_count, photos, photos))
                if (expenseTotal > 0) {
                    SummaryItem(AppIcons.Wallet, stringResource(R.string.amount_krw, "%,d".format(expenseTotal)))
                }
                SummaryItem(AppIcons.Description, pluralStringResource(R.plurals.record_count, records.size, records.size))
                Spacer(Modifier.weight(1f))
                Icon(
                    AppIcons.KeyboardArrowRight,
                    contentDescription = null,
                    tint = c.secondaryLabel,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** 여정에 붙은 사진의 작은 정사각 썸네일. 영상은 프레임 위에 재생 배지를 얹는다. */
@Composable
private fun JourneyMiniThumb(media: com.naury.trubbi.data.local.MediaEntity, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pressable(onClick = onClick),
    ) {
        val model = if (media.isVideo) {
            // 영상은 항상 원본 프레임에서(예전 영상 썸네일도 안전).
            coil3.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(media.uri)
                .videoFramePercent(0.1)
                .size(150)
                .build()
        } else {
            media.thumbnailUri ?: media.uri
        }
        coil3.compose.AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (media.isVideo) {
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(AppIcons.PlayArrow, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun SummaryItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    val c = LocalTrubbiColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, contentDescription = null, tint = c.secondaryLabel, modifier = Modifier.size(15.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = c.secondaryLabel)
    }
}

/** 현재 정차지의 대기·작업 시간을 살아 있는 값으로 보여 준다. */
@Composable
private fun StatusLine(stop: JourneyStopEntity) {
    val c = LocalTrubbiColors.current
    val context = LocalContext.current
    // 30초마다만 갱신한다. 분 단위 표시라 그보다 자주 돌 이유가 없다.
    val now by produceState(System.currentTimeMillis(), stop.stopId, stop.phase) {
        while (true) {
            value = System.currentTimeMillis()
            delay(30_000)
        }
    }
    val text = when (stop.phase) {
        StopPhase.WAITING -> stop.arrivedAt?.let {
            stringResource(R.string.stop_waiting_since, formatDuration(context, now - it), formatClock(it))
        }

        StopPhase.WORKING -> stop.workStartedAt?.let { startedAt ->
            buildString {
                append(
                    stringResource(
                        R.string.stop_working_for,
                        stop.kindEnum.label(),
                        formatDuration(context, now - startedAt),
                    ),
                )
                stop.waitingMs?.takeIf { it > 60_000 }?.let {
                    append(stringResource(R.string.stop_waited_suffix, formatDuration(context, it)))
                }
            }
        }

        else -> null
    } ?: return
    Spacer(Modifier.height(8.dp))
    Text(text, style = MaterialTheme.typography.labelMedium, color = c.secondaryLabel)
}

private fun formatDuration(context: Context, ms: Long): String {
    val totalMin = (ms / 60_000).coerceAtLeast(0)
    val hours = totalMin / 60
    val minutes = totalMin % 60
    return if (hours > 0) {
        context.getString(R.string.duration_hour_minute, hours.toInt(), minutes.toInt())
    } else {
        context.getString(R.string.duration_minute, minutes.toInt())
    }
}

private fun formatClock(at: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(at))

@Composable
private fun QuickChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val c = LocalTrubbiColors.current
    val bg = if (accent) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (c.isDark) 0.55f else 0.8f)
    }
    val fg = if (accent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .pressable(onClick = onClick)
            .padding(vertical = if (accent) 13.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(if (accent) 19.dp else 16.dp))
        Text(
            label,
            style = (if (accent) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelMedium)
                .copy(fontWeight = FontWeight.Bold),
            color = fg,
        )
    }
}

// ── 정차지 순서 표시(줄바꿈되는 곡선 배치)

/** 진행 중임을 알리는 뱃지 — 점이 은은하게 깜빡인다. */
@Composable
private fun LiveBadge() {
    val c = LocalTrubbiColors.current
    val primary = MaterialTheme.colorScheme.primary
    val t = rememberInfiniteTransition(label = "live")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "liveA")
    Row(
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(primary.copy(alpha = if (c.isDark) 0.24f else 0.13f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).graphicsLayer { alpha = a }.clip(RoundedCornerShape(percent = 50)).background(primary))
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.badge_in_progress),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = primary,
        )
    }
}

/** 진행중 뱃지 옆에서 달리는 작은 트럭. */
@Composable
private fun DrivingTruckMini() {
    val primary = MaterialTheme.colorScheme.primary
    val drive = rememberInfiniteTransition(label = "hdrTruck")
    val bob by drive.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1600
                0f at 0
                -2f at 400 using FastOutSlowInEasing
                0f at 800 using FastOutSlowInEasing
                2f at 1200 using FastOutSlowInEasing
                0f at 1600
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "hdrBob",
    )
    Icon(
        AppIcons.Truck,
        contentDescription = null,
        tint = primary,
        modifier = Modifier.size(22.dp).graphicsLayer { translationX = bob * 2.5f },
    )
}

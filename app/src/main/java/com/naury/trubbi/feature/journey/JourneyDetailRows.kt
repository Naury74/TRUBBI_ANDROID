package com.naury.trubbi.feature.journey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.request.videoFramePercent
import com.naury.trubbi.R
import com.naury.trubbi.core.i18n.label
import com.naury.trubbi.data.i18n.displayName
import com.naury.trubbi.data.i18n.displayTitle
import com.naury.trubbi.data.local.JourneyEventEntity
import com.naury.trubbi.data.local.JourneyStopEntity
import com.naury.trubbi.domain.model.ExpenseCategory
import com.naury.trubbi.domain.model.JourneyEventType
import com.naury.trubbi.domain.model.StopKind
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.glass
import com.naury.trubbi.ui.designsystem.pressable
import com.naury.trubbi.ui.theme.LocalTrubbiColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 여정 상세 목록에 놓이는 행들 — 지출, 정차지, 타임라인.
 */
/**
 * 비용 한 줄 — 분류 칩과 금액, 상호·시각, 영수증 링크, 삭제.
 *
 * @param expense 보여 줄 비용
 * @param editable false 면 삭제 버튼을 숨긴다(완료된 여정)
 * @param onOpenReceipt 영수증 사진으로 갈 때. 영수증이 없으면 null
 * @param onDelete 삭제를 누를 때
 */
@Composable
internal fun ExpenseRow(
    expense: com.naury.trubbi.data.local.ExpenseEntity,
    editable: Boolean,
    onOpenReceipt: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    val timeFmt = remember { SimpleDateFormat("M.d HH:mm", Locale.KOREA) }
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Text(
                ExpenseCategory.fromName(expense.category).label(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.amount_krw, "%,d".format(expense.amount)),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            val caption = buildList {
                expense.vendorName?.let { add(it) }
                add(timeFmt.format(Date(expense.usedAt)))
            }.joinToString(" · ")
            Text(caption, style = MaterialTheme.typography.labelSmall, color = LocalTrubbiColors.current.secondaryLabel, maxLines = 1)
        }
        if (onOpenReceipt != null) {
            Icon(
                AppIcons.ImageIcon,
                contentDescription = stringResource(R.string.journey_view_receipt),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp).pressable(onClick = onOpenReceipt),
            )
            Spacer(Modifier.width(10.dp))
        }
        if (editable) {
            Icon(
                AppIcons.Delete,
                contentDescription = stringResource(R.string.action_delete),
                tint = LocalTrubbiColors.current.secondaryLabel,
                modifier = Modifier.size(18.dp).pressable(onClick = onDelete),
            )
        }
    }
}

@Composable
internal fun StopRow(
    stop: JourneyStopEntity,
    editable: Boolean,
    onRename: () -> Unit,
    onToggleKind: () -> Unit,
    onDelete: () -> Unit,
) {
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.KOREA) }
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Kind badge — tap to flip 상차/하차 while still editable.
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(
                    if (stop.kindEnum == StopKind.PICKUP) {
                        MaterialTheme.colorScheme.primary.copy(alpha = if (LocalTrubbiColors.current.isDark) 0.3f else 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                )
                .then(if (editable && stop.arrivedAt == null) Modifier.pressable(onClick = onToggleKind) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (stop.kindEnum == StopKind.PICKUP) AppIcons.Pickup else AppIcons.Dropoff,
                contentDescription = stop.kindEnum.label(),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(
            Modifier
                .weight(1f)
                .then(if (editable) Modifier.pressable(onClick = onRename) else Modifier),
        ) {
            Text(
                "${stop.kindEnum.label()} · ${stop.displayName()}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            val parts = buildList {
                stop.arrivedAt?.let { add(stringResource(R.string.report_stop_arrived, timeFmt.format(Date(it)))) }
                stop.waitingMs?.takeIf { it > 60_000 }
                    ?.let { add(stringResource(R.string.report_stop_waited, (it / 60_000).toInt())) }
                stop.completedAt?.let { add(stringResource(R.string.report_stop_completed, timeFmt.format(Date(it)))) }
            }
            Text(
                if (parts.isEmpty()) stop.phase.label() else parts.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = LocalTrubbiColors.current.secondaryLabel,
                maxLines = 1,
            )
        }
        if (editable && stop.arrivedAt == null) {
            Spacer(Modifier.width(8.dp))
            Icon(
                AppIcons.Delete,
                contentDescription = stringResource(R.string.action_delete),
                tint = LocalTrubbiColors.current.secondaryLabel,
                modifier = Modifier.size(18.dp).pressable(onClick = onDelete),
            )
        }
    }
}

@Composable
internal fun SummaryChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.glass(RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = LocalTrubbiColors.current.secondaryLabel, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = LocalTrubbiColors.current.secondaryLabel)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * 연속된 RECORD_LINKED 이벤트 중 같은 정차지(relatedStopId)·같은 용도(title)인 것들을 한 묶음으로 모은다.
 * 그 외 이벤트는 단독 묶음(size 1). 결과 순서는 입력 순서를 보존.
 */
internal fun groupTimeline(events: List<JourneyEventEntity>): List<List<JourneyEventEntity>> {
    val out = ArrayList<List<JourneyEventEntity>>()
    var i = 0
    while (i < events.size) {
        val e = events[i]
        val isRecord = JourneyEventType.fromName(e.type) == JourneyEventType.RECORD_LINKED && e.relatedRecordId != null
        // 정차지(단계)가 없는 기록은 묶지 않고 단독 표시(서로 다른 맥락이 한 줄로 뭉치는 것 방지).
        if (isRecord && !e.relatedStopId.isNullOrBlank()) {
            val grp = arrayListOf(e)
            var j = i + 1
            while (j < events.size) {
                val n = events[j]
                val nRecord = JourneyEventType.fromName(n.type) == JourneyEventType.RECORD_LINKED && n.relatedRecordId != null
                // 같은 정차지에서 같은 증빙 종류로 연달아 남긴 기록만 한 줄로 묶는다.
                if (nRecord &&
                    n.relatedStopId.orEmpty() == e.relatedStopId.orEmpty() &&
                    n.titleArg == e.titleArg &&
                    n.title == e.title
                ) {
                    grp.add(n)
                    j++
                } else {
                    break
                }
            }
            out.add(grp)
            i = j
        } else {
            out.add(listOf(e))
            i++
        }
    }
    return out
}

@Composable
internal fun TimelineRow(
    event: JourneyEventEntity,
    record: com.naury.trubbi.data.local.RecordWithMedia?,
    onOpenRecord: (String) -> Unit,
) {
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val type = JourneyEventType.fromName(event.type)
    val accent = when (type) {
        JourneyEventType.JOURNEY_STARTED, JourneyEventType.JOURNEY_COMPLETED -> MaterialTheme.colorScheme.primary
        JourneyEventType.ISSUE_ADDED -> LocalTrubbiColors.current.red
        JourneyEventType.EXPENSE_ADDED -> LocalTrubbiColors.current.orange
        else -> LocalTrubbiColors.current.secondaryLabel
    }
    val icon = when (type) {
        JourneyEventType.ARRIVED -> AppIcons.LocationOn
        JourneyEventType.RECORD_LINKED -> AppIcons.PhotoLibrary
        JourneyEventType.EXPENSE_ADDED -> AppIcons.Wallet
        JourneyEventType.ISSUE_ADDED -> AppIcons.Warning
        JourneyEventType.JOURNEY_STARTED -> AppIcons.Truck
        JourneyEventType.JOURNEY_COMPLETED -> AppIcons.Flag
        else -> null
    }
    // 이 이벤트에 걸린 기록의 사진·영상을 타임라인 안에 바로 깔아 준다.
    val media = record?.media?.filter { it.thumbnailUri != null || !it.isVideo || it.uri.isNotBlank() }.orEmpty()

    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Text(
            timeFmt.format(Date(event.at)),
            style = MaterialTheme.typography.labelSmall,
            color = LocalTrubbiColors.current.secondaryLabel,
            modifier = Modifier.width(42.dp).padding(top = 10.dp),
        )
        // 시각 옆으로 이어지는 세로 레일과 노드 점.
        Box(Modifier.width(22.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(LocalTrubbiColors.current.separator),
            )
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .size(20.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (icon != null) accent else MaterialTheme.colorScheme.surface)
                    .then(if (icon == null) Modifier.padding(5.dp) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(12.dp))
                } else {
                    Box(Modifier.size(9.dp).clip(RoundedCornerShape(percent = 50)).background(accent))
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        // 본문. 연결된 기록이 있으면 눌러서 열 수 있다.
        Column(
            Modifier
                .weight(1f)
                .padding(bottom = 14.dp, top = 6.dp)
                .then(if (record != null) Modifier.pressable { onOpenRecord(record.record.recordId) } else Modifier),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    event.displayTitle(LocalContext.current),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (record != null) {
                    Icon(
                        AppIcons.KeyboardArrowRight,
                        contentDescription = null,
                        tint = LocalTrubbiColors.current.secondaryLabel,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            event.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = LocalTrubbiColors.current.secondaryLabel, maxLines = 1)
            }
            // 한 줄로 늘어놓는 썸네일.
            if (media.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    media.take(4).forEach { m ->
                        TimelineThumb(m)
                    }
                    if (media.size > 4) {
                        Box(
                            Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "+${media.size - 4}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = LocalTrubbiColors.current.secondaryLabel,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Small rounded thumbnail for a timeline media item (video → frame + play badge). 시간 라벨/탭 선택 옵션. */
@Composable
private fun TimelineThumb(
    media: com.naury.trubbi.data.local.MediaEntity,
    time: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier),
        ) {
            val model = if (media.isVideo) {
                // 영상은 항상 원본에서 프레임을 뽑는다(예전 .mp4 이름 썸네일도 안전하게 표시).
                coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(media.uri)
                    .videoFramePercent(0.1)
                    .size(160)
                    .build()
            } else {
                media.thumbnailUri ?: media.uri
            }
            coil.compose.AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (media.isVideo) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
        if (time != null) {
            Spacer(Modifier.height(2.dp))
            Text(time, style = MaterialTheme.typography.labelSmall, color = LocalTrubbiColors.current.secondaryLabel)
        }
    }
}

/** 같은 단계·같은 용도로 연속 촬영한 기록을 한 줄로 묶어 보여준다(썸네일마다 촬영 시각 표시). */
@Composable
internal fun TimelineGroupRow(
    events: List<JourneyEventEntity>,
    recordsById: Map<String, com.naury.trubbi.data.local.RecordWithMedia>,
    onOpenRecord: (String) -> Unit,
) {
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val first = events.first()
    // (media, 촬영시각, recordId) 셀들.
    val cells = events.flatMap { ev ->
        val rec = ev.relatedRecordId?.let { recordsById[it] }
        val t = timeFmt.format(Date(ev.at))
        rec?.media
            ?.filter { it.thumbnailUri != null || !it.isVideo || it.uri.isNotBlank() }
            ?.map { Triple(it, t, rec.record.recordId) }
            .orEmpty()
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Text(
            timeFmt.format(Date(first.at)),
            style = MaterialTheme.typography.labelSmall,
            color = LocalTrubbiColors.current.secondaryLabel,
            modifier = Modifier.width(42.dp).padding(top = 10.dp),
        )
        Box(Modifier.width(22.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.width(2.dp).fillMaxHeight().background(LocalTrubbiColors.current.separator))
            Box(
                Modifier.padding(top = 6.dp).size(20.dp).clip(RoundedCornerShape(percent = 50)).background(LocalTrubbiColors.current.secondaryLabel),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(12.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(bottom = 14.dp, top = 6.dp)) {
            Text(
                stringResource(R.string.journey_group_photos, first.title, cells.size),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            cells.chunked(4).forEach { rowCells ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                    rowCells.forEach { (m, t, rid) ->
                        TimelineThumb(m, time = t, onClick = { onOpenRecord(rid) })
                    }
                }
            }
        }
    }
}

@Composable
internal fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = LocalTrubbiColors.current.secondaryLabel)
    }
}

package com.naury.trubbi.feature.journey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.trubbi.R
import com.naury.trubbi.core.ml.ReceiptParser
import com.naury.trubbi.data.local.JourneyEventEntity
import com.naury.trubbi.domain.model.EvidenceCategory
import com.naury.trubbi.ui.common.RecordGridCell
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.PillButton
import com.naury.trubbi.ui.designsystem.glass
import com.naury.trubbi.ui.designsystem.pressable
import com.naury.trubbi.ui.theme.LocalTrubbiColors
import java.text.SimpleDateFormat
import java.util.Date

/**
 * 여정 상세의 탭별 본문.
 *
 * 탭마다 목록 구성이 달라 LazyListScope 확장으로 나눈다. 한 LazyColumn 안에서 탭만 바꾸므로
 * 스크롤 위치와 애니메이션이 탭 전환에도 유지된다.
 */
/**
 * 선택된 탭의 본문을 그린다.
 *
 * @param tab 0=타임라인, 1=기록, 2=비용, 3=지도, 4=리포트
 */
internal fun LazyListScope.journeyTabContent(
    tab: Int,
    data: JourneyTabData,
    viewModel: JourneyDetailViewModel,
    sheets: JourneyDetailSheets,
    timelineDayFmt: SimpleDateFormat,
    context: android.content.Context,
    actions: JourneyTabActions,
) {
    when (tab) {
        4 -> reportTab(data, viewModel, context)
        3 -> mapTab(data, actions.onOpenMap, actions.onOpenRecord)
        2 -> expenseTab(data, viewModel, sheets, actions.onCapture, actions.onOpenRecord)
        0 -> timelineTab(data, sheets, timelineDayFmt, actions.onOpenRecord)
        else -> recordsTab(data, viewModel, actions.onCapture, actions.onOpenRecord)
    }
}

/** 리포트 탭 — PDF 리포트와 워터마크 사진 묶음 공유. */
internal fun LazyListScope.reportTab(
    data: JourneyTabData,
    viewModel: JourneyDetailViewModel,
    context: android.content.Context,
) {
    // ── Report: PDF + watermarked photo bundle (원본은 보존, 적용본만 공유).
    item(key = "report") {
        Column(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val reportChooserTitle = stringResource(R.string.journey_report_share)
            val photosChooserTitle = stringResource(R.string.journey_photos_share_title)
            Text(
                stringResource(R.string.journey_report_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.journey_report_desc),
                style = MaterialTheme.typography.bodySmall,
                color = LocalTrubbiColors.current.secondaryLabel,
            )
            PillButton(
                text = stringResource(if (data.reportBusy) R.string.journey_report_building else R.string.journey_report_make),
                icon = AppIcons.Description,
                onClick = {
                    viewModel.sharePdf { intent ->
                        intent?.let {
                            context.startActivity(android.content.Intent.createChooser(it, reportChooserTitle))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            PillButton(
                text = stringResource(if (data.reportBusy) R.string.journey_report_building else R.string.journey_photos_share),
                icon = AppIcons.PhotoLibrary,
                onClick = {
                    viewModel.shareWatermarkedPhotos { intent ->
                        intent?.let {
                            context.startActivity(android.content.Intent.createChooser(it, photosChooserTitle))
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.journey_share_note),
                style = MaterialTheme.typography.labelSmall,
                color = LocalTrubbiColors.current.secondaryLabel,
            )
        }
    }
}

/** 지도 탭 — 경로 선, 정차지 핀, 기록 썸네일 마커. */
internal fun LazyListScope.mapTab(
    data: JourneyTabData,
    onOpenMap: () -> Unit,
    onOpenRecord: (String) -> Unit,
) {
    // ── 지도: 경로 선 + 정차지 핀 + 기록 썸네일 마커
    val hasAnything = data.routePoints.isNotEmpty() ||
        data.journey.orderedStops.any { it.latitude != null } ||
        data.records.any { it.record.latitude != null }
    if (!hasAnything) {
        item { EmptyHint(stringResource(R.string.journey_map_empty)) }
    } else {
        item(key = "map") {
            Box {
                JourneyMapCard(
                    stops = data.journey.orderedStops,
                    routePoints = data.routePoints,
                    records = data.records,
                    onRecordClick = onOpenRecord,
                    onExpand = onOpenMap,
                    issues = data.issues,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(430.dp)
                        .clip(RoundedCornerShape(24.dp)),
                )
                // "전체화면" 힌트 칩.
                Row(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .pressable(onClick = onOpenMap)
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        AppIcons.Fullscreen,
                        contentDescription = stringResource(R.string.action_fullscreen),
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        stringResource(R.string.detail_enlarge),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                }
            }
        }
        if (data.journey.journey.totalDistanceM > 0) {
            item(key = "distance") {
                SummaryChip(
                    AppIcons.Truck,
                    stringResource(R.string.journey_distance),
                    if (data.journey.journey.totalDistanceM >= 1000) {
                        "%.1f km".format(data.journey.journey.totalDistanceM / 1000f)
                    } else {
                        "${data.journey.journey.totalDistanceM} m"
                    },
                    Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 비용 탭 — 운송료·총비용·순수익 요약과 비용 목록, 영수증 전환 대기 줄. */
internal fun LazyListScope.expenseTab(
    data: JourneyTabData,
    viewModel: JourneyDetailViewModel,
    sheets: JourneyDetailSheets,
    onCapture: () -> Unit,
    onOpenRecord: (String) -> Unit,
) {
    // ── 비용: 요약(운송료/총비용/순수익) + 영수증 전환 대기 + 비용 목록.
    val freight = data.journey.journey.freightAmount
    item(key = "exp-summary") {
        Column(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (data.active) Modifier.pressable { sheets.showFreight = true } else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.journey_freight),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalTrubbiColors.current.secondaryLabel,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    freight?.let { stringResource(R.string.amount_krw, "%,d".format(it)) } ?: stringResource(R.string.journey_tap_to_enter),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (freight != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.journey_total_expense),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalTrubbiColors.current.secondaryLabel,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.amount_krw, "%,d".format(data.expenseTotal)),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (freight != null) {
                val net = freight - data.expenseTotal
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.journey_net_profit),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.amount_krw, "%,d".format(net)),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (net >= 0) MaterialTheme.colorScheme.primary else LocalTrubbiColors.current.red,
                    )
                }
            }
            val noReceipt = data.expenses.count { it.receiptRecordId == null }
            if (noReceipt > 0) {
                Text(
                    pluralStringResource(R.plurals.journey_no_receipt_count, noReceipt, noReceipt),
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalTrubbiColors.current.secondaryLabel,
                )
            }
        }
    }

    if (data.active) {
        item(key = "exp-actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(
                    stringResource(R.string.journey_add_expense),
                    {
                        sheets.expensePrefill = null
                        sheets.expenseReceiptId = null
                        sheets.showExpenseSheet = true
                    },
                    Modifier.weight(1f),
                    icon = AppIcons.Wallet,
                )
                PillButton(
                    stringResource(R.string.journey_capture_receipt),
                    {
                        viewModel.armJourneyCapture(EvidenceCategory.RECEIPT)
                        onCapture()
                    },
                    Modifier.weight(1f),
                    icon = AppIcons.PhotoCamera,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }

    // 영수증으로 찍었지만 아직 비용으로 등록 안 된 기록 → OCR 자동 입력 제안.
    val usedReceipts = data.expenses.mapNotNull { it.receiptRecordId }.toSet()
    val pendingReceipts = data.records.filter {
        it.record.evidenceCategory == EvidenceCategory.RECEIPT.name &&
            it.record.recordId !in usedReceipts
    }
    if (pendingReceipts.isNotEmpty()) {
        item(key = "rcpt-header") {
            Text(
                stringResource(R.string.journey_receipt_pending),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        items(pendingReceipts, key = { "rcpt-${it.record.recordId}" }) { rec ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Description, contentDescription = null, tint = LocalTrubbiColors.current.secondaryLabel, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column(
                    Modifier.weight(1f).pressable { onOpenRecord(rec.record.recordId) },
                ) {
                    Text(
                        rec.record.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        stringResource(
                            if (rec.record.ocrText.isNullOrBlank()) {
                                R.string.journey_receipt_reading
                            } else {
                                R.string.journey_receipt_read
                            },
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalTrubbiColors.current.secondaryLabel,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(MaterialTheme.colorScheme.primary)
                        .pressable {
                            sheets.expensePrefill = ReceiptParser.parse(rec.record.ocrText)
                            sheets.expenseReceiptId = rec.record.recordId
                            sheets.showExpenseSheet = true
                        }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        stringResource(R.string.action_register),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }

    // 비용 목록
    if (data.expenses.isEmpty()) {
        item { EmptyHint(stringResource(R.string.journey_no_expense)) }
    } else {
        items(data.expenses, key = { it.expenseId }) { e ->
            ExpenseRow(
                expense = e,
                editable = data.active,
                onOpenReceipt = e.receiptRecordId?.let { id -> { onOpenRecord(id) } },
                onDelete = { viewModel.deleteExpense(e.expenseId) },
            )
        }
    }
}

/** 타임라인 탭 — 날짜 구분과 이벤트 목록, 이슈 등록 입구. */
internal fun LazyListScope.timelineTab(
    data: JourneyTabData,
    sheets: JourneyDetailSheets,
    timelineDayFmt: SimpleDateFormat,
    onOpenRecord: (String) -> Unit,
) {
    // ── 타임라인
    if (data.events.isEmpty()) {
        item { EmptyHint(stringResource(R.string.journey_no_events)) }
    } else {
        val recordsById = data.records.associateBy { it.record.recordId }
        // 같은 단계·용도로 연속 촬영한 RECORD_LINKED 이벤트는 한 묶음으로. (O(n), 가벼움)
        val grouped = groupTimeline(data.events)
        var lastDay = ""
        grouped.forEachIndexed { gi, group ->
            val first = group.first()
            val day = timelineDayFmt.format(Date(first.at))
            if (day != lastDay) {
                lastDay = day
                item(key = "day-${first.id}") {
                    Text(
                        day,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = LocalTrubbiColors.current.secondaryLabel,
                        modifier = Modifier.padding(start = 6.dp, top = 4.dp),
                    )
                }
            }
            if (group.size > 1) {
                item(key = "grp-${first.id}") {
                    TimelineGroupRow(
                        events = group,
                        recordsById = recordsById,
                        onOpenRecord = onOpenRecord,
                    )
                }
            } else {
                item(key = "ev-${first.id}") {
                    TimelineRow(
                        event = first,
                        record = first.relatedRecordId?.let { recordsById[it] },
                        onOpenRecord = onOpenRecord,
                    )
                }
            }
        }
    }
    if (data.active) {
        item(key = "issue-btn") {
            PillButton(
                stringResource(R.string.journey_add_issue),
                { sheets.showIssueSheet = true },
                Modifier.fillMaxWidth(),
                icon = AppIcons.Warning,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 기록 탭 — 이 여정에서 저장한 기록 격자. */
internal fun LazyListScope.recordsTab(
    data: JourneyTabData,
    viewModel: JourneyDetailViewModel,
    onCapture: () -> Unit,
    onOpenRecord: (String) -> Unit,
) {
    // ── 기록 격자
    if (data.records.isEmpty()) {
        item { EmptyHint(stringResource(R.string.journey_no_records)) }
    } else {
        item(key = "records-grid") {
            // LazyColumn 안에 격자를 중첩하면 높이 계산이 충돌한다. 행을 직접 배치한다.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                data.records.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { rec ->
                            RecordGridCell(
                                record = rec,
                                onClick = { onOpenRecord(rec.record.recordId) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
    if (data.active) {
        item(key = "capture") {
            PillButton(
                stringResource(R.string.action_capture_now),
                {
                    viewModel.armJourneyCapture()
                    onCapture()
                },
                Modifier.fillMaxWidth(),
                icon = AppIcons.PhotoCamera,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * 탭 본문이 함께 읽는 여정 데이터.
 *
 * 탭마다 필요한 조각이 조금씩 달라 개별 파라미터로 넘기면 목록이 길어진다.
 * 한 번에 묶어 넘기고 각 탭이 필요한 것만 꺼내 쓴다.
 *
 * @property journey 여정과 정차지들
 * @property data.records 이 여정에 묶인 기록
 * @property data.events 타임라인 이벤트
 * @property data.expenses 비용 목록
 * @property data.issues 이슈 목록
 * @property data.expenseTotal 비용 합계(원)
 * @property data.routePoints 주행 경로 점
 * @property data.reportBusy 리포트·워터마크 생성 중인지
 * @property data.active 진행 중인 여정인지. 완료된 여정은 편집 동작을 감춘다
 */
internal data class JourneyTabData(
    val journey: com.naury.trubbi.data.local.JourneyWithStops,
    val records: List<com.naury.trubbi.data.local.RecordWithMedia>,
    val events: List<com.naury.trubbi.data.local.JourneyEventEntity>,
    val expenses: List<com.naury.trubbi.data.local.ExpenseEntity>,
    val issues: List<com.naury.trubbi.data.local.IssueEntity>,
    val expenseTotal: Long,
    val routePoints: List<com.naury.trubbi.data.local.RoutePointEntity>,
    val reportBusy: Boolean,
    val active: Boolean,
)

/**
 * 탭 본문에서 화면 밖으로 나가는 동작들.
 *
 * @property onCapture 촬영 화면으로. 찍은 것은 이 여정에 붙는다
 * @property onOpenMap 전체화면 지도로
 * @property onOpenRecord 기록 상세로
 */
internal data class JourneyTabActions(
    val onCapture: () -> Unit,
    val onOpenMap: () -> Unit,
    val onOpenRecord: (String) -> Unit,
)

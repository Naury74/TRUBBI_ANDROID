package com.naury.trubbi.feature.journey

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.naury.trubbi.R
import com.naury.trubbi.core.i18n.label
import com.naury.trubbi.core.ml.ReceiptParser
import com.naury.trubbi.data.i18n.displayName
import com.naury.trubbi.data.i18n.routeLabel
import com.naury.trubbi.data.local.JourneyStopEntity
import com.naury.trubbi.domain.model.EvidenceCategory
import com.naury.trubbi.domain.model.JourneyEventType
import com.naury.trubbi.domain.model.StopKind
import com.naury.trubbi.domain.model.StopPhase
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.GlassIconButton
import com.naury.trubbi.ui.designsystem.GlassTopBar
import com.naury.trubbi.ui.designsystem.PillButton
import com.naury.trubbi.ui.designsystem.SegmentedControl
import com.naury.trubbi.ui.designsystem.TrubbiConfirmDialog
import com.naury.trubbi.ui.designsystem.TrubbiInputDialog
import com.naury.trubbi.ui.designsystem.glass
import com.naury.trubbi.ui.designsystem.pressable
import com.naury.trubbi.ui.theme.LocalTrubbiColors
import java.text.SimpleDateFormat
/**
 * 여정 상세 화면.
 *
 * 위쪽은 지금 어느 단계인지와 다음에 누를 버튼 하나, 아래는 타임라인·기록·비용·지도·리포트 탭이다.
 *
 * 정차지별 대기 시간과 촬영 기록이 시간순으로 쌓이며, 리포트 탭에서 PDF 로 내보낼 수 있다.
 *
 * @param onBack 뒤로 갈 때
 * @param onOpenRecord 기록 상세로 (기록 id)
 * @param onCapture 촬영 화면으로. 찍은 것은 이 여정에 자동으로 붙는다
 * @param onOpenMap 전체화면 지도로
 * @param viewModel 여정 상세 상태
 */
@Composable
fun JourneyDetailScreen(
    onBack: () -> Unit,
    onOpenRecord: (String) -> Unit,
    onCapture: () -> Unit,
    onOpenMap: () -> Unit,
    viewModel: JourneyDetailViewModel = hiltViewModel(),
) {
    val c = LocalTrubbiColors.current
    val journey by viewModel.journey.collectAsState()
    val events by viewModel.events.collectAsState()
    val records by viewModel.records.collectAsState()
    val expenseTotal by viewModel.expenseTotal.collectAsState()
    val routePoints by viewModel.routePoints.collectAsState()
    val completeCheck by viewModel.completeCheck.collectAsState()
    val arrivalPrompt by viewModel.arrivalPrompt.collectAsState()
    val confirm by viewModel.confirm.collectAsState()
    val reportBusy by viewModel.reportBusy.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val issues by viewModel.issues.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    var tab by remember { mutableStateOf(0) }
    val sheets = remember { JourneyDetailSheets() }

    // 촬영을 예약해 두고 카메라에서 취소하면 예약만 남는다. 이 화면으로 돌아오면 정리한다.
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.disarmJourneyCapture() }

    val j = journey ?: return
    val active = j.journey.isActive

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        GlassTopBar(
            title = j.routeLabel(),
            onBack = onBack,
            actions = {
                if (active) {
                    GlassIconButton(AppIcons.Close, { sheets.confirmCancel = true }, stringResource(R.string.action_close), size = 36.dp)
                }
            },
        )

        // 앱 안에서 언어를 바꾸면 날짜 형식도 따라 바뀌도록 구성(Configuration)의 언어를 읽는다.
        val timelinePattern = stringResource(R.string.journey_timeline_date_pattern)
        val locale = LocalConfiguration.current.locales[0]
        val timelineDayFmt = remember(timelinePattern, locale) { SimpleDateFormat(timelinePattern, locale) }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ── 단계 표시와 주 동작 카드
            item(key = "stepper") {
                Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(16.dp)) {
                    if (j.orderedStops.isEmpty()) {
                        Text(
                            stringResource(R.string.journey_stop_auto_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.secondaryLabel,
                        )
                    } else {
                        // 메인 카드와 동일한 시퀀스 그래프로 통일.
                        val stops = j.orderedStops
                        val cur = j.currentStop
                        val currentIndex = stops.indexOfFirst { it.completedAt == null }.let { if (it < 0) stops.size else it }
                        val movingSeg = if (cur?.phase == StopPhase.MOVING && currentIndex in 1..stops.lastIndex) currentIndex - 1 else -1
                        JourneySequence(stops = stops, currentIndex = currentIndex, movingSegment = movingSeg)
                        if (active) {
                            Spacer(Modifier.height(10.dp))
                            Row(
                                Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .clip(RoundedCornerShape(percent = 50))
                                    .pressable { sheets.showAddStop = true }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(AppIcons.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.action_add_stop),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    if (active) {
                        Spacer(Modifier.height(14.dp))
                        val current = j.currentStop
                        when {
                            j.orderedStops.isEmpty() -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                PillButton(stringResource(R.string.action_arrive_pickup), {
                                    viewModel.arriveAtNewStop(StopKind.PICKUP)
                                }, Modifier.weight(1f), icon = AppIcons.Pickup)
                                PillButton(
                                    stringResource(R.string.action_arrive_dropoff),
                                    { viewModel.arriveAtNewStop(StopKind.DROPOFF) },
                                    Modifier.weight(1f),
                                    icon = AppIcons.Dropoff,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            current == null -> PillButton(
                                stringResource(R.string.action_complete_journey),
                                { sheets.confirmComplete = true },
                                Modifier.fillMaxWidth(),
                                icon = AppIcons.Flag,
                            )

                            else -> PillButton(
                                text = when (current.phase) {
                                    StopPhase.MOVING -> stringResource(R.string.action_arrive_at, current.displayName())
                                    StopPhase.WAITING -> stringResource(JourneyEventType.WORK_STARTED.titleRes, current.kindEnum.label())
                                    StopPhase.WORKING -> stringResource(JourneyEventType.STOP_COMPLETED.titleRes, current.kindEnum.label())
                                    StopPhase.DONE -> stringResource(R.string.action_next_stop)
                                },
                                onClick = viewModel::advanceCurrentStop,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(R.string.journey_completed_badge),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = c.secondaryLabel,
                        )
                    }
                }
            }

            // ── 정차지 목록 (이름·종류 변경, 아직 안 들른 곳 삭제)
            if (j.orderedStops.isNotEmpty()) {
                item(key = "stops-header") {
                    Text(
                        stringResource(R.string.action_stops),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                items(j.orderedStops, key = { it.stopId }) { stop ->
                    StopRow(
                        stop = stop,
                        editable = active,
                        onRename = { sheets.renameTarget = stop },
                        onToggleKind = { viewModel.toggleStopKind(stop) },
                        onDelete = { sheets.deleteTarget = stop },
                    )
                }
            }

            // ── 요약 칩
            item(key = "summary") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryChip(
                        AppIcons.PhotoLibrary,
                        stringResource(R.string.journey_tab_records),
                        stringResource(R.string.count_items, records.size),
                        Modifier.weight(1f),
                    )
                    SummaryChip(
                        AppIcons.Wallet,
                        stringResource(R.string.journey_tab_expenses),
                        stringResource(R.string.amount_krw, "%,d".format(expenseTotal)),
                        Modifier.weight(1f),
                    )
                }
            }

            // ── 탭
            item(key = "tabs") {
                SegmentedControl(
                    options = listOf(
                        stringResource(R.string.journey_tab_timeline),
                        stringResource(R.string.journey_tab_records),
                        stringResource(R.string.journey_tab_expenses),
                        stringResource(R.string.journey_tab_map),
                        stringResource(R.string.journey_tab_report),
                    ),
                    selectedIndex = tab,
                    onSelected = { tab = it },
                )
            }

            journeyTabContent(
                tab = tab,
                data = JourneyTabData(j, records, events, expenses, issues, expenseTotal, routePoints, reportBusy, active),
                viewModel = viewModel,
                sheets = sheets,
                timelineDayFmt = timelineDayFmt,
                context = context,
                actions = JourneyTabActions(onCapture, onOpenMap, onOpenRecord),
            )

            item { Spacer(Modifier.height(40.dp)) }
        }
    }

    JourneyDetailDialogs(
        sheets = sheets,
        viewModel = viewModel,
        journey = j,
        onCapture = onCapture,
        onBack = onBack,
    )
}

/**
 * 여정 상세 위에 뜨는 시트와 팝업들.
 *
 * 정차지 이름 변경·삭제, 여정 완료·취소 확인, 정차지 추가, 비용·이슈 입력, 운송료 입력이다.
 * 표시 여부는 모두 [sheets] 가 들고 있다.
 *
 * @param sheets 어떤 시트를 띄울지 담은 상태
 * @param journey 대상 여정
 * @param onCapture 영수증 촬영으로 이동
 */
@Composable
private fun JourneyDetailDialogs(
    sheets: JourneyDetailSheets,
    viewModel: JourneyDetailViewModel,
    journey: com.naury.trubbi.data.local.JourneyWithStops,
    onCapture: () -> Unit,
    onBack: () -> Unit,
) {
    val j = journey
    val active = j.journey.isActive
    val completeCheck by viewModel.completeCheck.collectAsState()
    val arrivalPrompt by viewModel.arrivalPrompt.collectAsState()
    val confirm by viewModel.confirm.collectAsState()
    // ── 팝업들
    sheets.renameTarget?.let { stop ->
        TrubbiInputDialog(
            title = stringResource(R.string.journey_stop_name),
            initial = stop.name.orEmpty(),
            onDismiss = { sheets.renameTarget = null },
            onConfirm = {
                viewModel.renameStop(stop, it)
                sheets.renameTarget = null
            },
            placeholder = stringResource(R.string.stop_name_placeholder),
        )
    }
    sheets.deleteTarget?.let { stop ->
        TrubbiConfirmDialog(
            title = stringResource(R.string.stop_delete_title),
            message = stringResource(R.string.stop_delete_message, stop.displayName()),
            onDismiss = { sheets.deleteTarget = null },
            onConfirm = {
                viewModel.removeStop(stop)
                sheets.deleteTarget = null
            },
            confirmText = stringResource(R.string.action_delete),
            destructive = true,
        )
    }
    if (sheets.confirmComplete) {
        TrubbiConfirmDialog(
            title = stringResource(R.string.journey_complete_title),
            message = stringResource(R.string.journey_complete_message),
            onDismiss = { sheets.confirmComplete = false },
            onConfirm = {
                sheets.confirmComplete = false
                viewModel.completeJourney()
            },
            confirmText = stringResource(R.string.action_done),
            icon = AppIcons.Flag,
        )
    }
    completeCheck?.let { missing ->
        TrubbiConfirmDialog(
            title = stringResource(R.string.missing_evidence_title),
            message = stringResource(
                R.string.missing_evidence_message,
                missing.map { it.label() }.joinToString("\n") { "· $it" },
            ),
            onDismiss = { viewModel.dismissCompleteCheck() },
            onConfirm = { viewModel.completeJourney(force = true) },
            confirmText = stringResource(R.string.action_complete_anyway),
            dismissText = stringResource(R.string.action_add_later),
        )
    }
    if (sheets.confirmCancel) {
        TrubbiConfirmDialog(
            title = stringResource(R.string.action_cancel_journey),
            message = stringResource(R.string.journey_cancel_message),
            onDismiss = { sheets.confirmCancel = false },
            onConfirm = {
                sheets.confirmCancel = false
                viewModel.cancelJourney()
                onBack()
            },
            confirmText = stringResource(R.string.action_cancel_journey),
            destructive = true,
        )
    }
    if (sheets.showAddStop) {
        // 홈과 동일한 대화형(음성/검색/지도) 추가 위저드 — 추가 모드.
        var suggestions by remember { mutableStateOf<List<JourneyStopEntity>>(emptyList()) }
        androidx.compose.runtime.LaunchedEffect(Unit) { suggestions = viewModel.suggestedStops() }
        JourneyStartWizard(
            recentStops = suggestions,
            searchPlaces = viewModel::searchPlaces,
            currentPlace = viewModel::currentPlace,
            onStart = { drafts ->
                sheets.showAddStop = false
                viewModel.addStops(drafts)
            },
            onDismiss = { sheets.showAddStop = false },
            speak = viewModel.voice::speak,
            speakAwait = viewModel.voice::speakAwait,
            stopVoice = viewModel.voice::stop,
            interpret = viewModel.voice::interpret,
            addMode = true,
        )
    }
    arrivalPrompt?.let { prompt ->
        ArrivalPromptSheet(
            kindLabel = prompt.kindLabel,
            stopName = prompt.stopName,
            speak = viewModel.voice::speak,
            speakAwait = viewModel.voice::speakAwait,
            onStart = { viewModel.resolveArrival(true) },
            onWait = { viewModel.resolveArrival(false) },
        )
    }
    // 단계 시작/완료를 중앙에 또렷하게 확인.
    StageConfirmOverlay(confirm, viewModel::clearConfirm)
    if (sheets.showExpenseSheet) {
        ExpenseSheet(
            prefill = sheets.expensePrefill,
            receiptRecordId = sheets.expenseReceiptId,
            onSave = { cat, amount, vendor, memo, receiptId ->
                viewModel.addExpense(cat, amount, vendor, memo, receiptId)
                sheets.showExpenseSheet = false
            },
            onCaptureReceipt = if (active && sheets.expenseReceiptId == null) {
                {
                    sheets.showExpenseSheet = false
                    viewModel.armJourneyCapture(EvidenceCategory.RECEIPT)
                    onCapture()
                }
            } else {
                null
            },
            onDismiss = { sheets.showExpenseSheet = false },
        )
    }
    if (sheets.showIssueSheet) {
        IssueSheet(
            onSave = { type, description ->
                viewModel.addIssue(type, description)
                sheets.showIssueSheet = false
            },
            onDismiss = { sheets.showIssueSheet = false },
        )
    }
    if (sheets.showFreight) {
        TrubbiInputDialog(
            title = stringResource(R.string.journey_freight),
            initial = j.journey.freightAmount?.toString().orEmpty(),
            onDismiss = { sheets.showFreight = false },
            onConfirm = { input ->
                viewModel.setFreight(input.filter { it.isDigit() }.toLongOrNull())
                sheets.showFreight = false
            },
            label = stringResource(R.string.journey_freight_amount),
            placeholder = stringResource(R.string.journey_freight_hint),
        )
    }
}

/**
 * 여정 상세에서 열리는 시트·팝업의 표시 상태.
 *
 * 플래그가 열 개라 화면 함수 안에 늘어놓으면 어떤 값이 무엇을 띄우는지 흩어진다.
 * 한 객체로 묶어 [JourneyDetailDialogs] 에 그대로 넘긴다.
 */
@Stable
internal class JourneyDetailSheets {
    /** 이름을 바꿀 정차지. */
    var renameTarget by mutableStateOf<JourneyStopEntity?>(null)

    /** 삭제할 정차지. 아직 도착하지 않은 곳만 대상이 된다. */
    var deleteTarget by mutableStateOf<JourneyStopEntity?>(null)

    /** 여정 완료 확인. */
    var confirmComplete by mutableStateOf(false)

    /** 여정 취소 확인. */
    var confirmCancel by mutableStateOf(false)

    /** 정차지 추가 마법사. */
    var showAddStop by mutableStateOf(false)

    /** 비용 입력 시트. */
    var showExpenseSheet by mutableStateOf(false)

    /** 영수증 OCR 로 미리 채운 값. 없으면 빈 폼으로 시작한다. */
    var expensePrefill by mutableStateOf<ReceiptParser.Parsed?>(null)

    /** 비용의 근거가 된 영수증 기록 id. */
    var expenseReceiptId by mutableStateOf<String?>(null)

    /** 이슈 등록 시트. */
    var showIssueSheet by mutableStateOf(false)

    /** 운송료 입력 팝업. */
    var showFreight by mutableStateOf(false)
}

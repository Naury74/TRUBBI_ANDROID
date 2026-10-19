package com.naury.trubbi.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naury.trubbi.R
import com.naury.trubbi.core.i18n.label
import com.naury.trubbi.data.i18n.routeLabel
import com.naury.trubbi.domain.journey.ArrivalPrompt
import com.naury.trubbi.feature.journey.planner.PlannedPlace
import com.naury.trubbi.feature.journey.planner.RoutePlanner
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.PillButton
import com.naury.trubbi.ui.designsystem.TrubbiConfirmDialog
import com.naury.trubbi.ui.designsystem.TrubbiSheet
import com.naury.trubbi.ui.theme.LocalTrubbiColors

/**
 * 홈 위에 겹쳐 뜨는 시트와 팝업.
 *
 * 인셋을 제대로 받으려면 최상위 Box 가 그려야 해서, 상태는 화면이 들고 이벤트만 올려 받는다.
 */
/**
 * 홈 위에 전체화면으로 겹치는 시트들.
 *
 * 여정 시작 마법사 · 공유 갤러리 · 정차지 추가 · 정차지 순서 변경 네 가지다.
 * 목록이 아니라 루트 Box 가 그려야 화면 인셋이 정확해진다.
 *
 * @param showStartSheet 여정 시작 마법사를 띄울지
 * @param startDestDraft 목적지를 미리 채운 채 시작할 때의 값
 * @param showAddStopSheet 진행 중 여정에 정차지를 추가하는 마법사를 띄울지
 * @param showManageStops 정차지 순서 변경 시트를 띄울지
 * @param shareItems 공유 갤러리에 넘길 항목들. 비어 있으면 띄우지 않는다
 */
@Composable
internal fun HomeOverlays(
    viewModel: HomeViewModel,
    activeJourney: com.naury.trubbi.data.local.JourneyWithStops?,
    showStartSheet: Boolean,
    startDestDraft: com.naury.trubbi.data.repo.JourneyRepository.StopDraft?,
    showAddStopSheet: Boolean,
    showManageStops: Boolean,
    shareItems: List<com.naury.trubbi.feature.share.ShareComposerItem>,
    onCloseStartSheet: () -> Unit,
    onCloseAddStop: () -> Unit,
    onCloseManageStops: () -> Unit,
    onCloseShare: () -> Unit,
    onOpenAddStop: () -> Unit,
) {
    val context = LocalContext.current
    val shareChooserTitle = stringResource(R.string.action_share)
    val homeShareItems = shareItems
    val orderedStops = activeJourney?.orderedStops.orEmpty()
    // ── 여정 시작 — 지도 위에서 출발·도착을 고르는 전체 화면. 루트 Box 안에서 모든 것 위에 그려 인셋이 정확하다.
    if (showStartSheet) {
        var recent by remember { mutableStateOf<List<com.naury.trubbi.data.local.JourneyStopEntity>>(emptyList()) }
        LaunchedEffect(Unit) { recent = viewModel.journey.recentStops() }
        val favorites by viewModel.journey.favorites.collectAsStateWithLifecycle()
        RoutePlanner(
            addMode = false,
            recentStops = recent,
            favorites = favorites,
            searchPlaces = viewModel.journey::searchPlaces,
            currentPlace = viewModel.journey::currentPlace,
            onToggleFavorite = viewModel.journey::toggleFavorite,
            onConfirm = { drafts ->
                onCloseStartSheet()
                viewModel.journey.startJourney(drafts)
            },
            onDismiss = onCloseStartSheet,
            initialDestination = startDestDraft?.let { d ->
                d.name?.let { PlannedPlace(it, d.address, d.latitude, d.longitude) }
            },
        )
    }

    // ── 홈 다중선택 공유 갤러리(워터마크 화면) — 앨범과 동일.
    if (homeShareItems.isNotEmpty()) {
        val homeWmDefaults by viewModel.library.watermarkDefaults.collectAsStateWithLifecycle()
        val homePurposes by viewModel.purposes.collectAsStateWithLifecycle()
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { onCloseShare() },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            com.naury.trubbi.feature.share.ShareComposerScreen(
                items = homeShareItems,
                defaults = homeWmDefaults,
                purposes = homePurposes,
                onClose = { onCloseShare() },
                onShare = { sels ->
                    viewModel.library.shareWithWatermark(sels) { intent ->
                        context.startActivity(android.content.Intent.createChooser(intent, shareChooserTitle))
                    }
                    onCloseShare()
                },
            )
        }
    }

    // ── 진행 중 여정에 정차지 추가 — 시작과 같은 화면을 추가 모드로 연다.
    if (showAddStopSheet) {
        var recent by remember { mutableStateOf<List<com.naury.trubbi.data.local.JourneyStopEntity>>(emptyList()) }
        LaunchedEffect(Unit) { recent = viewModel.journey.recentStops() }
        val favorites by viewModel.journey.favorites.collectAsStateWithLifecycle()
        RoutePlanner(
            addMode = true,
            recentStops = recent,
            favorites = favorites,
            searchPlaces = viewModel.journey::searchPlaces,
            currentPlace = viewModel.journey::currentPlace,
            onToggleFavorite = viewModel.journey::toggleFavorite,
            onConfirm = { drafts ->
                onCloseAddStop()
                viewModel.journey.addStops(drafts)
            },
            onDismiss = onCloseAddStop,
        )
    }

    // ── 정차지 순서 변경 시트 (아직 안 간 정차지만, 음성 '○○ 먼저' 지원).
    if (showManageStops) {
        activeJourney?.let { j ->
            com.naury.trubbi.feature.journey.StopReorderSheet(
                stops = j.orderedStops,
                onApply = { ids -> viewModel.journey.reorderPendingStops(ids) },
                onRemove = { id -> viewModel.journey.removeStop(id) },
                onAddStops = {
                    onCloseManageStops()
                    onOpenAddStop()
                },
                onDismiss = { onCloseManageStops() },
                speakAwait = viewModel.journey.voice::speakAwait,
                interpret = viewModel.journey.voice::interpret,
            )
        }
    }
}

/**
 * 홈 위에 뜨는 확인 팝업들.
 *
 * 도착 확인 · 여정 취소 · 증빙 누락 경고 · 공유받은 장소 · 기록 귀속 질문 다섯 가지다.
 * 각 상태가 null 이 아닐 때만 나타나며 서로 배타적이지 않다.
 *
 * @param confirmCancelJourney 여정 취소 확인을 띄울지
 * @param onDismissCancel 취소 확인을 닫을 때
 */
@Composable
internal fun HomeDialogs(
    viewModel: HomeViewModel,
    activeJourney: com.naury.trubbi.data.local.JourneyWithStops?,
    arrivalPrompt: ArrivalPrompt?,
    completeCheck: List<com.naury.trubbi.domain.model.MissingEvidence>?,
    sharedPlace: com.naury.trubbi.data.SharedPlace?,
    journeyAsk: com.naury.trubbi.data.repo.JourneyRepository.PendingLink?,
    confirmCancelJourney: Boolean,
    onDismissCancel: () -> Unit,
) {
    // ── 도착 직후 "바로 시작?" 물음 (음성/버튼).
    arrivalPrompt?.let { prompt ->
        com.naury.trubbi.feature.journey.ArrivalPromptSheet(
            kindLabel = prompt.kindLabel,
            stopName = prompt.stopName,
            speak = viewModel.journey.voice::speak,
            speakAwait = viewModel.journey.voice::speakAwait,
            onStart = { viewModel.journey.resolveArrival(true) },
            onWait = { viewModel.journey.resolveArrival(false) },
        )
    }

    // ── 여정 취소 확인.
    if (confirmCancelJourney) {
        com.naury.trubbi.ui.designsystem.TrubbiConfirmDialog(
            title = stringResource(R.string.action_cancel_journey),
            message = stringResource(R.string.home_cancel_journey_message),
            onDismiss = { onDismissCancel() },
            onConfirm = {
                onDismissCancel()
                viewModel.journey.cancelJourney()
            },
            confirmText = stringResource(R.string.action_cancel_journey),
            destructive = true,
        )
    }

    completeCheck?.let { missing ->
        TrubbiConfirmDialog(
            title = stringResource(R.string.missing_evidence_title),
            message = stringResource(
                R.string.missing_evidence_message,
                missing.map { it.label() }.joinToString("\n") { "· $it" },
            ),
            onDismiss = { viewModel.journey.dismissCompleteCheck() },
            onConfirm = { viewModel.journey.completeJourney(force = true) },
            confirmText = stringResource(R.string.action_complete_anyway),
            dismissText = stringResource(R.string.action_add_later),
        )
    }

    // ── 다른 앱에서 공유받은 장소 → 정차지로 추가 / 새 여정 시작.
    sharedPlace?.let { place ->
        SharedPlaceSheet(
            place = place,
            hasActiveJourney = activeJourney != null,
            onPick = { kind -> viewModel.journey.resolveSharedPlace(kind) },
            onDismiss = { viewModel.journey.resolveSharedPlace(null) },
        )
    }

    // ── "방금 기록을 여정에 포함할까요?" — 단일 기록 저장 후 1회 질문.
    journeyAsk?.let { ask ->
        val journeyLabel = activeJourney?.routeLabel()
        if (journeyLabel == null) {
            // 묻는 사이에 여정이 종료됐으면 단일 기록으로 확정한다.
            LaunchedEffect(ask.recordId) { viewModel.journey.resolveJourneyAsk(false) }
        } else {
            JourneyAskSheet(
                recordTitle = ask.title,
                journeyLabel = journeyLabel,
                onInclude = { viewModel.journey.resolveJourneyAsk(true) },
                onStandalone = { viewModel.journey.resolveJourneyAsk(false) },
            )
        }
    }
}

/**
 * 지도 앱에서 공유해 온 장소를 한 번에 정차지로 만드는 시트.
 *
 * @param place 공유 텍스트에서 뽑아낸 장소
 * @param hasActiveJourney 진행 중 여정이 있으면 정차지 추가로, 없으면 새 여정 시작으로 동작한다
 * @param onPick 상차·하차 중 하나를 고를 때
 * @param onDismiss 닫을 때
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SharedPlaceSheet(
    place: com.naury.trubbi.data.SharedPlace,
    hasActiveJourney: Boolean,
    onPick: (com.naury.trubbi.domain.model.StopKind) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalTrubbiColors.current
    TrubbiSheet(onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 30.dp)) {
            Text(
                place.name,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
            place.address?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = c.secondaryLabel, maxLines = 2)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (hasActiveJourney) {
                    stringResource(R.string.home_shared_add_to_journey)
                } else {
                    stringResource(R.string.home_shared_start_journey)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = c.secondaryLabel,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(
                    stringResource(R.string.home_as_pickup),
                    { onPick(com.naury.trubbi.domain.model.StopKind.PICKUP) },
                    Modifier.weight(1f),
                    icon = AppIcons.Pickup,
                )
                PillButton(
                    stringResource(R.string.home_as_dropoff),
                    { onPick(com.naury.trubbi.domain.model.StopKind.DROPOFF) },
                    Modifier.weight(1f),
                    icon = AppIcons.Dropoff,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * 방금 저장한 기록을 진행 중 여정에 넣을지 한 번 묻는 시트.
 *
 * 자동 귀속은 여정과 무관한 기록까지 증빙에 포함시킨다. 저장 직후 한 번만 묻고,
 * 이후 같은 여정에서는 그 선택을 재사용한다.
 *
 * @param recordTitle 방금 저장한 기록 제목
 * @param journeyLabel 진행 중 여정 이름
 * @param onInclude 여정에 포함할 때
 * @param onStandalone 단일 기록으로 둘 때
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun JourneyAskSheet(
    recordTitle: String,
    journeyLabel: String,
    onInclude: () -> Unit,
    onStandalone: () -> Unit,
) {
    val c = LocalTrubbiColors.current
    TrubbiSheet(onDismiss = onStandalone) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 30.dp)) {
            Text(
                stringResource(R.string.home_include_in_journey_q),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                buildString {
                    if (recordTitle.isNotBlank()) append("'$recordTitle' — ")
                    append(journeyLabel)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = c.secondaryLabel,
                maxLines = 2,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(stringResource(R.string.home_include), onInclude, Modifier.weight(1f), icon = AppIcons.Truck)
                PillButton(
                    stringResource(R.string.home_keep_standalone),
                    onStandalone,
                    Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

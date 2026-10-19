package com.naury.trubbi.feature.album

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.naury.trubbi.R
import com.naury.trubbi.core.i18n.label
import com.naury.trubbi.core.util.DateUtils
import com.naury.trubbi.data.local.RecordWithMedia
import com.naury.trubbi.feature.detail.DetailContent
import com.naury.trubbi.feature.detail.DetailViewModel
import com.naury.trubbi.ui.common.RecordGridCell
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.GlassIconButton
import com.naury.trubbi.ui.designsystem.TrubbiLargeTitle
import com.naury.trubbi.ui.theme.LocalTrubbiColors
import java.time.LocalDate
import java.time.YearMonth
/**
 * 앨범 화면 — 저장한 기록을 격자로 훑어보는 곳.
 *
 * 날짜·용도·종류로 필터링하고 상단 달력에서 기간을 지정한다. 홈이 최근 기록 위주인 것과 달리
 * 전체 기록 탐색을 담당한다.
 *
 * @param expanded 넓은 화면인지. 격자 열 수 자동 계산에 영향을 준다
 * @param onRecordClick 기록 상세로 (기록 id)
 * @param onEditImage 이미지 편집으로 (기록 id, 미디어 uri)
 * @param onOpenImage 이미지 뷰어로 (기록 id)
 * @param onEditMemo 메모 편집으로 (기록 id)
 * @param onBack 뒤로 갈 때
 * @param contentPadding 시스템 바를 피하기 위한 여백
 */
@Composable
fun AlbumScreen(
    expanded: Boolean,
    onRecordClick: (String) -> Unit,
    onEditImage: (recordId: String, mediaUri: String) -> Unit,
    onOpenImage: (recordId: String) -> Unit,
    onEditMemo: (recordId: String) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    if (expanded) {
        val detailVm: DetailViewModel = hiltViewModel()
        var selectedId by remember { mutableStateOf<String?>(null) }
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(0.44f)) {
                AlbumBody(
                    viewModel = viewModel,
                    contentPadding = contentPadding,
                    onClick = { id ->
                        selectedId = id
                        detailVm.setRecordId(id)
                    },
                    onBack = onBack,
                )
            }
            Box(
                Modifier
                    .weight(0.56f)
                    .fillMaxSize()
                    .padding(8.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.background),
            ) {
                if (selectedId == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.album_pick_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    DetailPane(detailVm, onEditImage, onOpenImage, onEditMemo, contentPadding)
                }
            }
        }
    } else {
        AlbumBody(viewModel = viewModel, contentPadding = contentPadding, onClick = onRecordClick, onBack = onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun AlbumBody(
    viewModel: AlbumViewModel,
    contentPadding: PaddingValues,
    onClick: (String) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val purposeFilter by viewModel.purposeFilter.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    val purposes by viewModel.purposes.collectAsStateWithLifecycle()
    val columns by viewModel.columns.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val dateRange by viewModel.dateRange.collectAsStateWithLifecycle()
    val month by viewModel.month.collectAsStateWithLifecycle()
    val monthCounts by viewModel.monthCounts.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedMedia by viewModel.selectedMedia.collectAsStateWithLifecycle()
    val expandedBatch by viewModel.expandedBatch.collectAsStateWithLifecycle()
    val hasActiveFilter by viewModel.hasActiveFilter.collectAsStateWithLifecycle()
    val searchBreakdown by viewModel.searchBreakdown.collectAsStateWithLifecycle()
    val requestOpenSearch by viewModel.requestOpenSearch.collectAsStateWithLifecycle()
    val requestedFilterName by viewModel.requestedFilterName.collectAsStateWithLifecycle()
    val requestedPurposeName by viewModel.requestedPurposeName.collectAsStateWithLifecycle()
    var filtersOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var calExpanded by remember { mutableStateOf(false) }
    val shareChooserTitle = stringResource(R.string.action_share)
    // 공유 갤러리(워터마크를 지정하는 화면)에 넘길 항목. 비어 있지 않으면 전체화면으로 띄운다.
    var shareItems by remember { mutableStateOf<List<com.naury.trubbi.feature.share.ShareComposerItem>>(emptyList()) }
    val wmDefaults by viewModel.watermarkDefaults.collectAsStateWithLifecycle()

    // 검색 진입 시 필터 패널도 함께 펼친다.
    LaunchedEffect(searchOpen) { if (searchOpen) filtersOpen = true }

    LaunchedEffect(requestedFilterName) { if (requestedFilterName != null) viewModel.applyRequestedFilter() }
    LaunchedEffect(requestedPurposeName) { if (requestedPurposeName != null) viewModel.applyRequestedFilter() }
    LaunchedEffect(requestOpenSearch) {
        if (requestOpenSearch) {
            searchOpen = true
            filtersOpen = true
            viewModel.consumeOpenSearch()
        }
    }
    val selectionMode = selection.isNotEmpty() || selectedMedia.isNotEmpty() || expandedBatch != null
    val gridState = rememberLazyGridState()
    val context = LocalContext.current

    // 다중 선택 중에는 뒤로가기가 화면을 벗어나지 않고 선택만 해제한다.
    androidx.activity.compose.BackHandler(enabled = selectionMode) { viewModel.clearSelection() }

    // 열 수는 설정값이 있으면 그대로 쓰고, 없으면 화면 너비에서 계산한다.
    val configuration = LocalConfiguration.current
    val autoColumns = (configuration.screenWidthDp / 120).coerceIn(2, 6)
    val effectiveColumns = if (columns > 0) columns else autoColumns

    // 날짜로 그룹핑한다. 기본은 최신순이고, 오래된 순 정렬에서는 역순이 된다.
    val groups = remember(records, sortMode) {
        records.groupBy { it.record.jobDate }.entries
            .sortedByDescending { it.key }
            .let { if (sortMode == SortMode.OLDEST) it.reversed() else it }
            .map { it.key to it.value }
    }

    // 주간 달력은 화면 최상단 항목의 날짜를 따라간다.
    val keyToDate = remember(records) { records.associate { it.record.recordId to it.record.jobDate } }
    val focusedIso by remember(keyToDate) {
        derivedStateOf {
            val key = gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.key as? String
            when {
                key == null -> null
                key.startsWith("h_") -> key.removePrefix("h_")
                else -> keyToDate[key]
            }
        }
    }
    var weekAnchor by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(focusedIso, calExpanded) {
        if (!calExpanded) {
            val d = focusedIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            if (d != null) {
                // 주 단위가 바뀔 때만 갱신한다. 스크롤 델타마다 갱신하면 달력이 매 프레임 recomposition 된다.
                val newWeek = d.minusDays((d.dayOfWeek.value % 7).toLong())
                val curWeek = weekAnchor.minusDays((weekAnchor.dayOfWeek.value % 7).toLong())
                if (newWeek != curWeek) {
                    weekAnchor = d
                    viewModel.setMonth(YearMonth.from(d))
                }
            }
        }
    }

    // 첫 탭은 단일 날짜, 두 번째 탭은 두 날짜 사이 기간, 세 번째 탭은 선택 초기화.
    val onPickDay: (String) -> Unit = { iso ->
        val r = dateRange
        if (r == null || r.first != r.second) {
            viewModel.setDateRange(iso, iso)
        } else {
            viewModel.setDateRange(r.first, iso)
        }
    }

    // 공유 갤러리. 전체화면 다이얼로그로 띄우고 항목별 워터마크를 지정한다.
    if (shareItems.isNotEmpty()) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { shareItems = emptyList() },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            com.naury.trubbi.feature.share.ShareComposerScreen(
                items = shareItems,
                defaults = wmDefaults,
                purposes = purposes,
                onClose = { shareItems = emptyList() },
                onShare = { selections ->
                    viewModel.shareWithWatermark(selections) { intent ->
                        context.startActivity(Intent.createChooser(intent, shareChooserTitle))
                    }
                    shareItems = emptyList()
                },
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (selectionMode) {
            SelectionBar(
                count = selection.size + selectedMedia.size,
                onClose = viewModel::clearSelection,
                onShare = {
                    val items = viewModel.shareComposerItems()
                    if (items.isNotEmpty()) {
                        shareItems = items
                    } else {
                        viewModel.shareSelectedIntent()?.let { context.startActivity(Intent.createChooser(it, shareChooserTitle)) }
                    }
                },
                onFavorite = viewModel::favoriteSelected,
                onDelete = viewModel::deleteSelected,
                topPadding = contentPadding.calculateTopPadding(),
            )
        } else {
            val backButton: (@Composable () -> Unit)? = onBack?.let { back ->
                { GlassIconButton(AppIcons.ArrowBackIos, back, stringResource(R.string.action_back), size = 40.dp) }
            }
            TrubbiLargeTitle(
                title = stringResource(R.string.album_title),
                subtitle = dateRange?.let { rangeLabel(it) } ?: sortMode.label(),
                modifier = Modifier.padding(top = contentPadding.calculateTopPadding()),
                leading = backButton,
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassIconButton(
                            AppIcons.Search,
                            {
                                searchOpen = !searchOpen
                                if (!searchOpen) viewModel.setSearchQuery("")
                            },
                            stringResource(R.string.action_search),
                        )
                        GlassIconButton(AppIcons.Tune, { filtersOpen = !filtersOpen }, stringResource(R.string.action_filter))
                    }
                },
            )
        }

        // 필터 패널을 접어도 적용 중인 필터는 계속 표시한다.
        if (!selectionMode && !filtersOpen && hasActiveFilter) {
            ActiveFilterBar(
                filter = filter,
                purposeFilter = purposeFilter,
                dateRange = dateRange,
                onOpenFilters = { filtersOpen = true },
                onClear = {
                    viewModel.setFilter(AlbumFilter.ALL)
                    viewModel.setPurposeFilter(null)
                    viewModel.clearDateRange()
                },
            )
        }

        // 제목 아래 고정 달력. 기본은 주간이고 아래로 당기면 월간으로 펼쳐진다.
        if (!selectionMode && !searchOpen) {
            AlbumCalendar(
                expanded = calExpanded,
                weekAnchor = weekAnchor,
                month = month,
                counts = monthCounts,
                dateRange = dateRange,
                onToggleExpand = {
                    calExpanded = !calExpanded
                    if (calExpanded) viewModel.setMonth(YearMonth.from(weekAnchor))
                },
                onPrevMonth = viewModel::previousMonth,
                onNextMonth = viewModel::nextMonth,
                onShiftWeek = { delta ->
                    weekAnchor = weekAnchor.plusWeeks(delta)
                    viewModel.setMonth(YearMonth.from(weekAnchor))
                },
                onClearRange = { viewModel.clearDateRange() },
                onPickDay = onPickDay,
            )
        }

        AnimatedVisibility(
            visible = searchOpen && !selectionMode,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            SearchBar(
                query = searchQuery,
                purposes = purposes,
                onQuery = viewModel::setSearchQuery,
            )
        }

        AnimatedVisibility(
            visible = filtersOpen && !selectionMode,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            FilterPanel(
                filter = filter,
                purposeFilter = purposeFilter,
                sortMode = sortMode,
                purposes = purposes,
                columns = columns,
                dateRange = dateRange,
                onFilter = viewModel::setFilter,
                onPurpose = viewModel::setPurposeFilter,
                onSort = viewModel::setSort,
                onColumns = viewModel::setColumns,
                onClearDate = { viewModel.clearDateRange() },
            )
        }

        // 검색 결과 요약. 어느 항목(제목·메모·OCR 등)에서 몇 건이 일치했는지 표시한다.
        if (!selectionMode && searchQuery.isNotBlank()) {
            SearchBreakdownBar(total = records.size, breakdown = searchBreakdown)
        }

        if (records.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(if (searchQuery.isNotBlank()) R.string.search_no_results else R.string.album_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // 포인터가 둘 이상일 때만 처리한다. 단일 터치까지 소비하면 스크롤이 동작하지 않는다.
            val pinch = Modifier.pointerInput(effectiveColumns) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var total = 1f
                    var handled = false
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size >= 2) {
                            total *= event.calculateZoom()
                            if (!handled) {
                                if (total > 1.25f) {
                                    viewModel.setColumns((effectiveColumns - 1).coerceIn(2, 6))
                                    handled = true
                                } else if (total < 0.8f) {
                                    viewModel.setColumns((effectiveColumns + 1).coerceIn(2, 6))
                                    handled = true
                                }
                            }
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(effectiveColumns),
                state = gridState,
                modifier = pinch,
                contentPadding = PaddingValues(
                    start = 10.dp,
                    end = 10.dp,
                    top = 4.dp,
                    bottom = contentPadding.calculateBottomPadding() + 24.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // 최상단 기록의 확대 카드. 검색·다중 선택 중에는 표시하지 않는다.
                if (!selectionMode && searchQuery.isBlank()) {
                    records.firstOrNull()?.let { hero ->
                        item(span = { GridItemSpan(maxLineSpan) }, key = "album_hero", contentType = "hero") {
                            com.naury.trubbi.ui.common.FeaturedRecordCard(
                                record = hero,
                                onClick = { onClick(hero.record.recordId) },
                                ratio = 16f / 9f,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                groups.forEach { (date, list) ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = "h_$date", contentType = "header") {
                        DateHeader(date, list.size)
                    }
                    list.forEach { record ->
                        val id = record.record.recordId
                        val isBatch = record.media.size > 1
                        item(key = id, contentType = "cell") {
                            RecordGridCell(
                                record = record,
                                modifier = Modifier.animateItem(),
                                onClick = {
                                    when {
                                        !selectionMode -> onClick(id)
                                        isBatch -> viewModel.toggleExpand(id)
                                        else -> viewModel.toggleSelect(id)
                                    }
                                },
                                onLongClick = { if (isBatch) viewModel.toggleExpand(id) else viewModel.toggleSelect(id) },
                                selected = id in selection,
                                selectionMode = selectionMode,
                                batchTotal = if (isBatch) record.media.size else 0,
                                batchSelected = if (isBatch) record.media.count { it.mediaId in selectedMedia } else 0,
                            )
                        }
                        if (isBatch && expandedBatch == id) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "exp_$id", contentType = "exp") {
                                BatchExpansion(
                                    record = record,
                                    selectedMedia = selectedMedia,
                                    onToggleMedia = viewModel::toggleMedia,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    topPadding: Dp,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = topPadding)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(AppIcons.Close, onClose, stringResource(R.string.action_close), size = 40.dp)
        Text(
            stringResource(R.string.selected_count, count),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        GlassIconButton(AppIcons.Share, onShare, stringResource(R.string.action_share), size = 40.dp)
        Spacer(Modifier.width(8.dp))
        GlassIconButton(AppIcons.Star, onFavorite, stringResource(R.string.action_favorite), size = 40.dp, tint = LocalTrubbiColors.current.orange)
        Spacer(Modifier.width(8.dp))
        GlassIconButton(AppIcons.Delete, onDelete, stringResource(R.string.action_delete), size = 40.dp, tint = LocalTrubbiColors.current.red)
    }
}

/**
 * 묶음 기록 아래로 펼쳐지는 패널. 묶음 안의 사진을 개별 선택할 수 있다.
 *
 * @param record 펼친 묶음 기록
 * @param selectedMedia 지금 고른 미디어 id 들
 */
@Composable
private fun BatchExpansion(
    record: RecordWithMedia,
    selectedMedia: Set<String>,
    onToggleMedia: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalTrubbiColors.current
    val media = remember(record) { record.media.sortedBy { it.createdAt } }
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.feed_batch_panel_title, record.record.title, media.size),
            style = MaterialTheme.typography.labelMedium,
            color = c.secondaryLabel,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(media, key = { it.mediaId }) { m ->
                val sel = m.mediaId in selectedMedia
                Box(
                    Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onToggleMedia(m.mediaId) },
                ) {
                    AsyncImage(
                        model = m.thumbnailUri ?: m.uri,
                        contentDescription = m.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (sel) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)))
                    }
                    Icon(
                        if (sel) AppIcons.CheckCircle else AppIcons.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (sel) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(20.dp)
                            .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(percent = 50)),
                    )
                }
            }
        }
    }
}

@Composable
private fun DateHeader(dateIso: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            DateUtils.formatDate(dateIso),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Text(
            pluralStringResource(R.plurals.count_items, count, count),
            style = MaterialTheme.typography.labelMedium,
            color = LocalTrubbiColors.current.secondaryLabel,
        )
    }
}

@Composable
private fun DetailPane(
    detailVm: DetailViewModel,
    onEditImage: (recordId: String, mediaUri: String) -> Unit,
    onOpenImage: (recordId: String) -> Unit,
    onEditMemo: (recordId: String) -> Unit,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val record by detailVm.record.collectAsStateWithLifecycle()
    val item: RecordWithMedia = record ?: return
    val isBarcode = !item.record.barcodeValue.isNullOrBlank()
    val shareChooserTitle = stringResource(R.string.action_share)
    var shareItems by remember { mutableStateOf<List<com.naury.trubbi.feature.share.ShareComposerItem>>(emptyList()) }
    val wmDefaults by detailVm.watermarkDefaults.collectAsStateWithLifecycle()
    val sharePurposes by detailVm.purposes.collectAsStateWithLifecycle()
    if (shareItems.isNotEmpty()) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { shareItems = emptyList() },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            com.naury.trubbi.feature.share.ShareComposerScreen(
                items = shareItems,
                defaults = wmDefaults,
                purposes = sharePurposes,
                onClose = { shareItems = emptyList() },
                onShare = { sels ->
                    detailVm.shareWithWatermark(sels) { intent -> context.startActivity(Intent.createChooser(intent, shareChooserTitle)) }
                    shareItems = emptyList()
                },
            )
        }
    }
    DetailContent(
        item = item,
        onBack = null,
        onToggleFavorite = { detailVm.toggleFavorite(item) },
        onShare = {
            val items = if (isBarcode && item.media.isEmpty()) emptyList() else detailVm.shareComposerItems(item)
            if (items.isNotEmpty()) {
                shareItems = items
            } else {
                val intent = if (isBarcode && item.media.isEmpty()) {
                    detailVm.shareBarcodeIntent(item) ?: detailVm.shareIntent(item)
                } else {
                    detailVm.shareIntent(item)
                }
                context.startActivity(Intent.createChooser(intent, shareChooserTitle))
            }
        },
        onShareBarcode = {
            detailVm.shareBarcodeIntent(item)?.let { context.startActivity(Intent.createChooser(it, shareChooserTitle)) }
        },
        onDelete = { detailVm.delete(item.record.recordId) {} },
        onEditImage = { uri -> onEditImage(item.record.recordId, uri) },
        onOpenImage = { onOpenImage(item.record.recordId) },
        onEditMemo = { onEditMemo(item.record.recordId) },
        onSaveMemo = { detailVm.saveMemo(item.record.recordId, it) },
        onSaveTags = { detailVm.saveTags(item.record.recordId, it) },
        contentPadding = contentPadding,
    )
}

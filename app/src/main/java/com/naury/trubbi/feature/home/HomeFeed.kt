package com.naury.trubbi.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naury.trubbi.R
import com.naury.trubbi.core.i18n.purposeLabel
import com.naury.trubbi.core.util.DateUtils
import com.naury.trubbi.data.local.RecordWithMedia
import com.naury.trubbi.domain.model.RecordType
import com.naury.trubbi.domain.record.HomeState
import com.naury.trubbi.feature.journey.ActiveJourneyCard
import com.naury.trubbi.feature.journey.JourneyStartCard
import com.naury.trubbi.ui.common.DateFeed
import com.naury.trubbi.ui.common.RecordGridCell
import com.naury.trubbi.ui.common.TrubbiCalendar
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.StatPill
import com.naury.trubbi.ui.designsystem.appearOnce
import com.naury.trubbi.ui.designsystem.glass
import com.naury.trubbi.ui.designsystem.pressable
import com.naury.trubbi.ui.theme.LocalTrubbiColors

/**
 * 홈 목록 본문 — 오늘 요약, 여정 카드, 날짜별 타임라인.
 */
/**
 * 홈의 세로 목록.
 *
 * 위에서부터 여정 카드 · 지난 여정 · 필터 · 로그인 안내 · 오늘 요약 · 즐겨찾기 · 최근 저장 ·
 * 달력 · 날짜별 타임라인 순이다. 다중 선택 중이거나 필터가 걸리면 상단 구역들은 표시하지 않는다.
 *
 * 시트를 여는 플래그는 `on…` 콜백으로 올려 보낸다. 시트 자체는 목록 위에 겹쳐야 해서
 * [HomeScreen] 의 루트 Box 가 그린다.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun HomeFeed(
    state: HomeState,
    viewModel: HomeViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState,
    purposeRowState: androidx.compose.foundation.lazy.LazyListState,
    headerNestedScroll: androidx.compose.ui.input.nestedscroll.NestedScrollConnection,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    selectionMode: Boolean,
    filtering: Boolean,
    searchMode: Boolean,
    signedIn: Boolean,
    typeFilter: String?,
    purposeFilter: String?,
    purposes: List<String>,
    dateRange: Pair<String, String>?,
    selection: Set<String>,
    selectedMedia: Set<String>,
    expandedBatch: String?,
    activeJourney: com.naury.trubbi.data.local.JourneyWithStops?,
    journeyRecords: List<RecordWithMedia>,
    journeyExpenseTotal: Long,
    completedJourneyCount: Int,
    recentJourneys: List<com.naury.trubbi.data.local.JourneyWithStops>,
    calExpanded: Boolean,
    weekAnchor: java.time.LocalDate,
    calMonth: java.time.YearMonth,
    onCellClick: (RecordWithMedia) -> Unit,
    onPickDay: (String) -> Unit,
    onToggleCalendar: () -> Unit,
    onShiftWeek: (Long) -> Unit,
    onStartJourney: () -> Unit,
    onPrefillDestination: (com.naury.trubbi.data.repo.JourneyRepository.StopDraft?) -> Unit,
    onAddStop: () -> Unit,
    onManageStops: () -> Unit,
    onCancelJourney: () -> Unit,
    onCamera: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenJourney: (String) -> Unit,
    onOpenJourneyHistory: () -> Unit,
) {
    val c = LocalTrubbiColors.current
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth().nestedScroll(headerNestedScroll),
        contentPadding = PaddingValues(
            top = 14.dp,
            bottom = contentPadding.calculateBottomPadding() + 110.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (!selectionMode) {
            // ── 여정 카드 — 필터 칩보다 위에 가장 먼저. 진행 중이면 스테퍼, 없으면 시작 진입점.
            if (!filtering) {
                item(key = "journey") {
                    Box(Modifier.appearOnce(30).padding(horizontal = 16.dp)) {
                        val j = activeJourney
                        val favs by viewModel.journey.favorites.collectAsStateWithLifecycle()
                        androidx.compose.foundation.layout.Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (j != null) {
                                ActiveJourneyCard(
                                    journey = j,
                                    records = journeyRecords,
                                    expenseTotal = journeyExpenseTotal,
                                    onAdvance = viewModel.journey::advanceCurrentStop,
                                    onArriveNew = viewModel.journey::arriveAtNewStop,
                                    onAddStops = { onAddStop() },
                                    onManageStops = { onManageStops() },
                                    onComplete = { viewModel.journey.completeJourney() },
                                    onCapture = {
                                        viewModel.journey.armJourneyCapture()
                                        onCamera()
                                    },
                                    onOpenDetail = { onOpenJourney(j.journey.journeyId) },
                                    onCancel = { onCancelJourney() },
                                )
                                // 진행 중 — 즐겨찾기를 누르면 이 여정에 정차지로 추가.
                                FavoritePlacesRow(favs) { f ->
                                    viewModel.journey.addStops(
                                        listOf(
                                            com.naury.trubbi.data.repo.JourneyRepository.StopDraft(
                                                com.naury.trubbi.domain.model.StopKind.DROPOFF,
                                                f.name,
                                                f.address,
                                                f.latitude,
                                                f.longitude,
                                            ),
                                        ),
                                    )
                                }
                            } else {
                                JourneyStartCard(onClick = { onStartJourney() })
                                // 여정이 없을 때 — 즐겨찾기를 누르면 그 곳을 목적지로 여정 시작 과정 진입.
                                FavoritePlacesRow(favs) { f ->
                                    onPrefillDestination(
                                        com.naury.trubbi.data.repo.JourneyRepository.StopDraft(
                                            com.naury.trubbi.domain.model.StopKind.DROPOFF,
                                            f.name,
                                            f.address,
                                            f.latitude,
                                            f.longitude,
                                        ),
                                    )
                                    onStartJourney()
                                }
                            }
                        }
                    }
                }
            }
            // 지난 여정 — 여정 카드 바로 아래. 카드로 노출 + 최근 3건 바로 진입.
            if (!filtering && completedJourneyCount > 0) {
                item(key = "pastJourneys") {
                    Box(Modifier.appearOnce(35).padding(horizontal = 16.dp)) {
                        com.naury.trubbi.feature.journey.PastJourneysCard(
                            journeys = recentJourneys,
                            totalCount = completedJourneyCount,
                            onOpenAll = onOpenJourneyHistory,
                            onOpen = { id -> onOpenJourney(id) },
                        )
                    }
                }
            }
            if (searchMode) {
                // 검색 중에는 필터 줄을 상단에 고정한다.
                stickyHeader(key = "filters") {
                    FilterStickyRow(
                        typeFilter = typeFilter,
                        purposeFilter = purposeFilter,
                        purposes = purposes,
                        onType = viewModel.library::setTypeFilter,
                        onPurpose = viewModel.library::setPurposeFilter,
                    )
                }
            } else {
                item {
                    LazyRow(
                        state = purposeRowState,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(purposes, key = { it }) { p ->
                            CategoryChip(
                                label = purposeLabel(p),
                                selected = purposeFilter == p,
                                onClick = { viewModel.library.setPurposeFilter(p) },
                            )
                        }
                    }
                }
            }

            if (!searchMode && (typeFilter != null || purposeFilter != null)) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        typeFilter?.let { t ->
                            CategoryChip(label = typeFilterLabel(t), selected = true) { viewModel.library.setTypeFilter(t) }
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            stringResource(R.string.home_clear_filters),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.pressable { viewModel.library.clearFilters() },
                        )
                    }
                }
            }

            // 미로그인 시: 로그인/백업 안내 배너 (로그인 없이도 전 기능 사용 가능).
            if (!filtering && !signedIn) {
                item(key = "loginBanner") {
                    Row(
                        Modifier
                            .appearOnce(20)
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .glass(RoundedCornerShape(20.dp))
                            .pressable { onOpenAccount() }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Icon(
                            AppIcons.Backup,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.home_sign_in_cta),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                stringResource(R.string.home_sign_in_cta_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = c.secondaryLabel,
                            )
                        }
                        androidx.compose.material3.Icon(
                            AppIcons.KeyboardArrowRight,
                            contentDescription = null,
                            tint = c.secondaryLabel,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            val todayTotal = state.summary.photos + state.summary.videos + state.summary.barcodes + state.summary.memos
            if (!filtering && todayTotal > 0) {
                item {
                    Column(
                        Modifier
                            .appearOnce(60)
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .glass(RoundedCornerShape(26.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(start = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.home_today),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                DateUtils.formatDate(DateUtils.todayIso()),
                                style = MaterialTheme.typography.bodySmall,
                                color = c.secondaryLabel,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            StatPill(
                                state.summary.photos.toString(),
                                stringResource(RecordType.PHOTO.labelRes),
                                MaterialTheme.colorScheme.primary,
                                Modifier.weight(1f).pressable { viewModel.library.setTypeFilter("PHOTO") },
                                active = state.summary.photos > 0,
                            )
                            StatPill(
                                state.summary.videos.toString(),
                                stringResource(RecordType.VIDEO.labelRes),
                                c.accent2,
                                Modifier.weight(1f).pressable { viewModel.library.setTypeFilter("VIDEO") },
                                active = state.summary.videos > 0,
                            )
                            StatPill(
                                state.summary.documents.toString(),
                                stringResource(RecordType.DOCUMENT.labelRes),
                                MaterialTheme.colorScheme.tertiary,
                                Modifier.weight(1f).pressable { viewModel.library.setTypeFilter("DOCUMENT") },
                                active = state.summary.documents > 0,
                            )
                            StatPill(
                                state.summary.barcodes.toString(),
                                stringResource(RecordType.BARCODE.labelRes),
                                c.green,
                                Modifier.weight(1f).pressable { viewModel.library.setTypeFilter("BARCODE") },
                                active = state.summary.barcodes > 0,
                            )
                            StatPill(
                                state.summary.memos.toString(),
                                stringResource(RecordType.MEMO.labelRes),
                                if (c.isDark) com.naury.trubbi.ui.theme.MonoAmberDark else com.naury.trubbi.ui.theme.MonoAmberLight,
                                Modifier.weight(1f).pressable { viewModel.library.setTypeFilter("MEMO") },
                                active = state.summary.memos > 0,
                            )
                        }
                    }
                }
            }
        }

        // 1) 즐겨찾기
        if (!filtering && state.favorites.isNotEmpty()) {
            item {
                SectionHeader(stringResource(R.string.home_favorites), stringResource(R.string.home_see_all)) {
                    viewModel.library.setTypeFilter("FAVORITE")
                }
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.favorites, key = { it.record.recordId }) { record ->
                        Box(Modifier.width(132.dp)) {
                            RecordGridCell(
                                record = record,
                                onClick = { onCellClick(record) },
                                onLongClick = { viewModel.selection.toggleRecord(record.record.recordId) },
                                selected = record.record.recordId in selection,
                                selectionMode = selectionMode,
                            )
                        }
                    }
                }
            }
        }

        // 2) 최근 저장
        if (!filtering && state.recents.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.home_recent), "") {} }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.recents.take(12), key = { it.record.recordId }) { record ->
                        Box(Modifier.width(124.dp)) {
                            RecordGridCell(
                                record = record,
                                onClick = { onCellClick(record) },
                                onLongClick = { viewModel.selection.toggleRecord(record.record.recordId) },
                                selected = record.record.recordId in selection,
                                selectionMode = selectionMode,
                            )
                        }
                    }
                }
            }
        }

        // 3) 날짜 타임라인
        run {
            stickyHeader(key = "calendar") {
                Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Box(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(vertical = 8.dp)) {
                        TrubbiCalendar(
                            expanded = calExpanded,
                            weekAnchor = weekAnchor,
                            month = calMonth,
                            counts = state.counts,
                            dateRange = dateRange,
                            onToggleExpand = onToggleCalendar,
                            onPrevMonth = viewModel.library::previousMonth,
                            onNextMonth = viewModel.library::nextMonth,
                            onShiftWeek = onShiftWeek,
                            onClearRange = viewModel.library::clearDateRange,
                            onPickDay = onPickDay,
                        )
                    }
                }
            }
        }
        if (state.dateSections.isNotEmpty()) {
            state.dateSections.forEach { section ->
                stickyHeader(key = "h_${section.dateIso}", contentType = "dateHeader") {
                    DateStickyHeader(section.dateIso, section.items)
                }
                item(key = "f_${section.dateIso}", contentType = "dateFeed") {
                    DateFeed(
                        items = section.items,
                        onClick = onCellClick,
                        onLongClick = { viewModel.selection.toggleRecord(it) },
                        selection = selection,
                        selectionMode = selectionMode,
                        selectedMedia = selectedMedia,
                        expandedBatch = expandedBatch,
                        onToggleMedia = viewModel.selection::toggleMedia,
                        onToggleBatch = viewModel.selection::toggleBatch,
                        onLongPressBatch = viewModel.selection::enterBatch,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        } else if (filtering) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.home_no_results), style = MaterialTheme.typography.bodyMedium, color = c.secondaryLabel)
                }
            }
        }
    }
}

/** 날짜 구분 헤더 — 큰 날짜, 월·연, 요일, 종류별 개수. 목록에 붙어 따라다닌다. */
@Composable
private fun DateStickyHeader(dateIso: String, items: List<com.naury.trubbi.data.local.RecordWithMedia>) {
    val c = LocalTrubbiColors.current
    val date = runCatching { java.time.LocalDate.parse(dateIso) }.getOrNull()
    val isToday = dateIso == DateUtils.todayIso()
    val monthYear = date?.let { stringResource(R.string.home_month_year, it.monthValue, it.year) }.orEmpty()
    val weekdayNames = listOf(
        stringResource(R.string.weekday_mon_full),
        stringResource(R.string.weekday_tue_full),
        stringResource(R.string.weekday_wed_full),
        stringResource(R.string.weekday_thu_full),
        stringResource(R.string.weekday_fri_full),
        stringResource(R.string.weekday_sat_full),
        stringResource(R.string.weekday_sun_full),
    )
    val weekday = date?.let { weekdayNames[it.dayOfWeek.value - 1] }.orEmpty()
    // 고정 헤더는 스크롤 중 반복 recomposition 된다. 종류별 집계는 목록이 바뀔 때만 계산한다.
    val typeCounts = remember(items) { items.groupingBy { RecordType.fromName(it.record.type) }.eachCount() }
    fun count(t: RecordType) = typeCounts[t] ?: 0
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp), elevation = 4.dp).padding(horizontal = 16.dp, vertical = 12.dp).height(IntrinsicSize.Min),
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        stringResource(R.string.home_day_of_month, date?.dayOfMonth ?: 0),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        weekday,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Text(monthYear, style = MaterialTheme.typography.labelMedium, color = c.secondaryLabel)
            }
            Spacer(Modifier.weight(1f))
            Column(
                Modifier.fillMaxHeight(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    pluralStringResource(R.plurals.home_record_count, items.size, items.size),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TypeCount(AppIcons.ImageIcon, count(RecordType.PHOTO))
                    TypeCount(AppIcons.Videocam, count(RecordType.VIDEO))
                    TypeCount(AppIcons.QrCode2, count(RecordType.BARCODE))
                    TypeCount(AppIcons.Description, count(RecordType.MEMO))
                }
            }
        }
    }
}

@Composable
private fun TypeCount(icon: androidx.compose.ui.graphics.vector.ImageVector, n: Int) {
    if (n > 0) {
        val c = LocalTrubbiColors.current
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            androidx.compose.material3.Icon(icon, contentDescription = null, tint = c.secondaryLabel, modifier = Modifier.size(13.dp))
            Text("$n", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun SectionHeader(title: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        Text(
            actionLabel,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.pressable { onAction() },
        )
    }
}

/** 즐겨찾는 장소 빠른 선택 행 — 여정 시작 카드 아래(없으면 목적지로 시작, 진행 중이면 정차지 추가). */
@Composable
private fun FavoritePlacesRow(
    favorites: List<com.naury.trubbi.data.local.FavoritePlaceEntity>,
    onPick: (com.naury.trubbi.data.local.FavoritePlaceEntity) -> Unit,
) {
    if (favorites.isEmpty()) return
    val c = LocalTrubbiColors.current
    androidx.compose.foundation.layout.Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(AppIcons.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.home_favorite_places),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = c.secondaryLabel,
            )
        }
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(favorites.take(12), key = { it.id }) { f ->
                Row(
                    Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .pressable { onPick(f) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Icon(
                        AppIcons.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(f.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                }
            }
        }
    }
}

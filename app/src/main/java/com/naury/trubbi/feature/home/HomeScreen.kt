package com.naury.trubbi.feature.home

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naury.trubbi.R
import com.naury.trubbi.core.weather.WeatherCondition
import com.naury.trubbi.data.local.RecordWithMedia
import com.naury.trubbi.feature.journey.SendOffOverlay
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.weather.WeatherBackground
import com.naury.trubbi.ui.weather.WeatherHeader
/**
 * 홈 화면.
 *
 * 하단 탭의 "홈"과 "기록"이 이 화면을 나눠 쓴다([section]). 홈은 날씨 · 여정 카드 · 오늘 요약 ·
 * 즐겨찾기 · 최근 저장을, 기록은 검색 · 용도/종류 필터 · 달력 · 날짜별 타임라인을 그린다. 두 탭은 같은
 * 뷰모델을 써서, 홈에서 고른 조건이 기록 탭에 그대로 걸린다.
 *
 * @param section 그릴 구역
 * @param onOpenRecords 기록 탭으로 (홈의 "전체 보기"·통계 칸)
 * @param onCamera 촬영 화면으로
 * @param onOpenSettings 설정으로
 * @param onRecordClick 기록 상세로 (기록 id)
 * @param onOpenJourney 여정 상세로 (여정 id)
 * @param onOpenJourneyHistory 지난 여정 목록으로
 * @param onOpenAccount 계정·백업 화면으로
 * @param contentPadding 시스템 바를 피하기 위한 여백
 * @param viewModel 홈 상태
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    section: HomeSection,
    onOpenRecords: () -> Unit,
    onCamera: () -> Unit,
    onOpenSettings: () -> Unit,
    onRecordClick: (String) -> Unit,
    onOpenJourney: (String) -> Unit,
    onOpenJourneyHistory: () -> Unit,
    onOpenAccount: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.library.state.collectAsStateWithLifecycle()
    val selection by viewModel.selection.records.collectAsStateWithLifecycle()
    val selectedMedia by viewModel.selection.media.collectAsStateWithLifecycle()
    val expandedBatch by viewModel.selection.expandedBatch.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val purposes by viewModel.purposes.collectAsStateWithLifecycle()
    val weather by viewModel.weather.collectAsStateWithLifecycle()
    val activeJourney by viewModel.journey.activeJourney.collectAsStateWithLifecycle()
    val journeyRecords by viewModel.journey.records.collectAsStateWithLifecycle()
    val journeyExpenseTotal by viewModel.journey.expenseTotal.collectAsStateWithLifecycle()
    val sendOff by viewModel.journey.sendOff.collectAsStateWithLifecycle()
    val journeyAsk by viewModel.journey.journeyAsk.collectAsStateWithLifecycle()
    val sharedPlace by viewModel.journey.sharedPlace.collectAsStateWithLifecycle()
    val completeCheck by viewModel.journey.completeCheck.collectAsStateWithLifecycle()
    val completedJourneyCount by viewModel.journey.completedCount.collectAsStateWithLifecycle()
    val recentJourneys by viewModel.journey.recentJourneys.collectAsStateWithLifecycle()
    val arrivalPrompt by viewModel.journey.arrivalPrompt.collectAsStateWithLifecycle()
    val confirm by viewModel.journey.confirm.collectAsStateWithLifecycle()
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    var showStartSheet by remember { mutableStateOf(false) }
    // 홈 다중선택 공유 갤러리 대상.
    var homeShareItems by remember { mutableStateOf<List<com.naury.trubbi.feature.share.ShareComposerItem>>(emptyList()) }
    // 즐겨찾기/장소에서 '목적지 프리필'로 여정 시작할 때의 목적지(없으면 일반 시작).
    var startDestDraft by remember { mutableStateOf<com.naury.trubbi.data.repo.JourneyRepository.StopDraft?>(null) }
    var showAddStopSheet by remember { mutableStateOf(false) }
    var showManageStops by remember { mutableStateOf(false) }
    var confirmCancelJourney by remember { mutableStateOf(false) }
    // 촬영 예약은 저장 시점에 해제된다. 카메라를 취소하고 돌아온 경우를 대비해
    // 홈 진입 시 남아 있는 예약을 정리한다.
    LaunchedEffect(Unit) { viewModel.journey.disarmJourneyCapture() }

    // Android 13+: 여정 추적 알림이 보이려면 런타임 알림 권한이 필요하다 (없으면 조용히 숨겨짐).
    val notifContext = androidx.compose.ui.platform.LocalContext.current
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(activeJourney != null) {
        if (activeJourney != null && android.os.Build.VERSION.SDK_INT >= 33) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                notifContext,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val selectionMode = selection.isNotEmpty() || selectedMedia.isNotEmpty() || expandedBatch != null

    BackHandler(enabled = selectionMode) { viewModel.selection.clear() }

    // 날씨에 쓸 위치 권한 — 이미 있으면 갱신만, 없으면 처음 들어올 때 한 번만 묻는다.
    val weatherPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.refreshWeather() }
    LaunchedEffect(Unit) {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.refreshWeather()
        } else {
            weatherPermission.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) viewModel.library.importPhoto(uri) { id -> onRecordClick(id) } }
    val profilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) viewModel.changeProfilePhoto(uri) }

    val cellClick: (RecordWithMedia) -> Unit = { record ->
        val id = record.record.recordId
        if (selectionMode) viewModel.selection.toggleRecord(id) else onRecordClick(id)
    }

    val shareChooserTitle = stringResource(R.string.action_share)

    // 전체 화면 오버레이가 떠 있으면 탭 바(셸)가 그 위에 겹치지 않게 알린다.
    val overlayOpen = showStartSheet || showAddStopSheet || showManageStops || homeShareItems.isNotEmpty()
    LaunchedEffect(overlayOpen) { viewModel.fullScreenOverlay.value = overlayOpen }
    DisposableEffect(Unit) { onDispose { viewModel.fullScreenOverlay.value = false } }

    // 탭 바의 "+" 메뉴가 맡긴 일. 경로 화면·여정 오버레이는 홈 탭에만 있으므로 홈 탭이 처리한다.
    val pendingAction by viewModel.pendingAction.collectAsStateWithLifecycle()
    LaunchedEffect(pendingAction, section) {
        if (section != HomeSection.TODAY) return@LaunchedEffect
        when (pendingAction) {
            HomeAction.PLAN_ROUTE -> if (activeJourney == null) showStartSheet = true else showAddStopSheet = true
            HomeAction.IMPORT_PHOTO -> picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            null -> return@LaunchedEffect
        }
        viewModel.pendingAction.value = null
    }

    val listState = rememberLazyListState()
    // 중첩 스크롤 델타로 헤더 접힘을 계산한다. 첫 항목의 통과 시점을 기준으로 하면
    // 스크롤 양과 접힘 정도가 연동되지 않는다.
    val density = LocalDensity.current
    val maxCollapsePx = with(density) { 134.dp.toPx() }
    var collapsePx by remember { mutableStateOf(0f) }
    val collapse = (collapsePx / maxCollapsePx).coerceIn(0f, 1f)
    val headerNestedScroll = remember(maxCollapsePx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (dy < 0f) {
                    val prev = collapsePx
                    collapsePx = (collapsePx - dy).coerceAtMost(maxCollapsePx)
                    return Offset(0f, -(collapsePx - prev))
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (dy > 0f) {
                    val prev = collapsePx
                    collapsePx = (collapsePx - dy).coerceAtLeast(0f)
                    return Offset(0f, prev - collapsePx)
                }
                return Offset.Zero
            }
        }
    }
    val expandedDate by viewModel.library.expandedDate.collectAsStateWithLifecycle()
    var searchMode by remember { mutableStateOf(false) }
    // 타임라인 조건은 흐름 하나로 온다. 조건마다 구독하면 두 개를 함께 바꿀 때 중간 상태로 한 번 더 그려진다.
    val filter by viewModel.library.filter.collectAsStateWithLifecycle()
    val query = filter.query
    val searchFocus = remember { FocusRequester() }
    // 검색 중에 뒤로가기를 누르면 화면을 떠나지 말고 검색만 닫는다.
    androidx.activity.compose.BackHandler(enabled = searchMode) {
        searchMode = false
        viewModel.library.clearSearch()
    }
    val typeFilter = filter.type
    val purposeFilter = filter.purpose
    val dateRange = filter.dateRange
    // 검색·선택 중이 아닐 때 뒤로가기는 화면을 벗어나기 전에 걸린 필터를 먼저 해제한다.
    androidx.activity.compose.BackHandler(
        enabled = !selectionMode && !searchMode && (typeFilter != null || purposeFilter != null || dateRange != null),
    ) {
        viewModel.library.clearFilters()
    }
    val calMonth by viewModel.library.calendarMonth.collectAsStateWithLifecycle()
    var calExpanded by remember { mutableStateOf(false) }
    var weekAnchor by remember { mutableStateOf(java.time.LocalDate.now()) }
    // 용도 필터 목록의 스크롤 위치를 처음으로 되감는다.
    val purposeRowState = rememberLazyListState()
    LaunchedEffect(purposes) { purposeRowState.scrollToItem(0) }
    // 첫 탭은 단일 날짜, 두 번째 탭은 기간으로 확장한다.
    val onPickDay: (String) -> Unit = { iso ->
        val r = dateRange
        if (r == null || r.first != r.second) {
            viewModel.library.setDateRange(iso, iso)
        } else {
            viewModel.library.setDateRange(r.first, iso)
        }
    }
    // 검색 중이거나 필터가 하나라도 걸리면 상단 섹션을 감추고 결과 목록만 표시한다.
    val filtering = searchMode || typeFilter != null || purposeFilter != null || dateRange != null
    // 검색 진입 시 입력칸에 포커스를 주고 목록을 맨 위로 이동시킨다.
    LaunchedEffect(searchMode) {
        if (searchMode) {
            listState.animateScrollToItem(0)
            runCatching { searchFocus.requestFocus() }
        }
    }
    run {
        Box(Modifier.fillMaxSize()) {
            // 시간·날씨 배경.
            Box(Modifier.fillMaxSize()) {
                WeatherBackground(
                    condition = weather?.condition ?: WeatherCondition.CLOUDS,
                    isDay = weather?.isDay ?: true,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = 0.45f),
                            0.28f to Color.Black.copy(alpha = 0.22f),
                            0.6f to Color.Black.copy(alpha = 0.14f),
                            1.0f to Color.Black.copy(alpha = 0.32f),
                        ),
                    ),
                )
                // 선택 모드에서는 화면 전체에 오버레이를 씌운다.
                androidx.compose.animation.AnimatedVisibility(visible = selectionMode, enter = fadeIn(), exit = fadeOut()) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)))
                }
            }
            Column(Modifier.fillMaxSize()) {
                // 접히는 날씨 배너. 선택 도구는 하단에 배치되므로 헤더는 그대로 유지된다.
                run {
                    // 검색 버튼 위치에서 왼쪽으로 확장되는 검색창.
                    Box(Modifier.fillMaxWidth()) {
                        if (!searchMode && section == HomeSection.RECORDS) {
                            RecordsHeader(
                                topPadding = contentPadding.calculateTopPadding(),
                                onSearch = { searchMode = true },
                            )
                        } else if (!searchMode) {
                            WeatherHeader(
                                collapse = collapse,
                                topPadding = contentPadding.calculateTopPadding(),
                                weather = weather,
                                name = profile.name,
                                profilePhoto = profile.photoUri,
                                email = account?.email,
                                providers = account?.providers.orEmpty(),
                                emailVerified = account?.emailVerified == true,
                                onAvatar = { profilePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                onSettings = onOpenSettings,
                                onSearch = { searchMode = true },
                            )
                        } else {
                            Spacer(Modifier.fillMaxWidth().height(contentPadding.calculateTopPadding() + 64.dp))
                        }
                        androidx.compose.animation.AnimatedVisibility(
                            visible = searchMode,
                            enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
                            exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut(),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = contentPadding.calculateTopPadding() + 12.dp, start = 16.dp, end = 16.dp),
                        ) {
                            SearchField(
                                query = query,
                                focusRequester = searchFocus,
                                onQuery = viewModel.library::setSearch,
                                onClose = {
                                    searchMode = false
                                    viewModel.library.clearSearch()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                HomeFeed(
                    state = state,
                    viewModel = viewModel,
                    listState = listState,
                    purposeRowState = purposeRowState,
                    headerNestedScroll = headerNestedScroll,
                    contentPadding = contentPadding,
                    modifier = Modifier.weight(1f),
                    selectionMode = selectionMode,
                    section = section,
                    filtering = filtering,
                    searchMode = searchMode,
                    signedIn = signedIn,
                    typeFilter = typeFilter,
                    purposeFilter = purposeFilter,
                    purposes = purposes,
                    dateRange = dateRange,
                    selection = selection,
                    selectedMedia = selectedMedia,
                    expandedBatch = expandedBatch,
                    activeJourney = activeJourney,
                    journeyRecords = journeyRecords,
                    journeyExpenseTotal = journeyExpenseTotal,
                    completedJourneyCount = completedJourneyCount,
                    recentJourneys = recentJourneys,
                    calExpanded = calExpanded,
                    weekAnchor = weekAnchor,
                    calMonth = calMonth,
                    onCellClick = cellClick,
                    onPickDay = onPickDay,
                    onToggleCalendar = {
                        calExpanded = !calExpanded
                        if (calExpanded) viewModel.library.setMonth(java.time.YearMonth.from(weekAnchor))
                    },
                    onShiftWeek = { delta ->
                        weekAnchor = weekAnchor.plusWeeks(delta)
                        viewModel.library.setMonth(java.time.YearMonth.from(weekAnchor))
                    },
                    onStartJourney = { showStartSheet = true },
                    onPrefillDestination = { startDestDraft = it },
                    onAddStop = { showAddStopSheet = true },
                    onManageStops = { showManageStops = true },
                    onCancelJourney = { confirmCancelJourney = true },
                    onCamera = onCamera,
                    onOpenAccount = onOpenAccount,
                    onOpenJourney = onOpenJourney,
                    onOpenJourneyHistory = onOpenJourneyHistory,
                    onOpenRecords = onOpenRecords,
                )
            }

            // 다중 선택 중에는 탭 바 대신 선택 도구가 아래에 뜬다.
            if (selectionMode) {
                HomeSelectionDock(
                    count = selection.size + selectedMedia.size,
                    onClose = viewModel.selection::clear,
                    onShare = {
                        val items = viewModel.library.shareComposerItems()
                        if (items.isNotEmpty()) {
                            homeShareItems = items
                        } else {
                            viewModel.library.shareSelectedIntent()?.let { context.startActivity(android.content.Intent.createChooser(it, shareChooserTitle)) }
                        }
                    },
                    onFavorite = viewModel.library::favoriteSelected,
                    onDelete = viewModel.library::deleteSelected,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = contentPadding.calculateBottomPadding() + 16.dp),
                )
            }

            // 여정 시작·도착·완료 오버레이. 최상위에 잠시 표시된 뒤 사라진다.
            SendOffOverlay(sendOff = sendOff, onDone = viewModel.journey::clearSendOff)

            // 단계 시작/완료를 중앙에 또렷하게 확인.
            com.naury.trubbi.feature.journey.StageConfirmOverlay(confirm, viewModel.journey::clearConfirm)

            // 재방문 환영 인사 — 상단에서 잠깐 내려왔다 사라진다. 상단 바를 잠시 덮으므로 불투명한 토스트로 띄운다.
            val greeting by viewModel.greeting.collectAsStateWithLifecycle()
            val greetingToast = com.naury.kinetica.ui.rememberKToastState()
            // 스플래시가 걷힌 뒤에 띄운다. 토스트는 별도 창이라 스플래시보다 위에 그려진다.
            val appReady = com.naury.trubbi.ui.designsystem.LocalAppReady.current
            LaunchedEffect(greeting, appReady) {
                if (!appReady) return@LaunchedEffect
                greeting?.let {
                    greetingToast.show(it, durationMs = 2600)
                    viewModel.clearGreeting()
                }
            }
            com.naury.kinetica.ui.KToastHost(greetingToast, Modifier.align(Alignment.TopCenter))

            HomeOverlays(
                viewModel = viewModel,
                activeJourney = activeJourney,
                showStartSheet = showStartSheet,
                startDestDraft = startDestDraft,
                showAddStopSheet = showAddStopSheet,
                showManageStops = showManageStops,
                shareItems = homeShareItems,
                onCloseStartSheet = {
                    showStartSheet = false
                    startDestDraft = null
                },
                onCloseAddStop = { showAddStopSheet = false },
                onCloseManageStops = { showManageStops = false },
                onCloseShare = { homeShareItems = emptyList() },
                onOpenAddStop = { showAddStopSheet = true },
            )
        }
    }

    HomeDialogs(
        viewModel = viewModel,
        activeJourney = activeJourney,
        arrivalPrompt = arrivalPrompt,
        completeCheck = completeCheck,
        sharedPlace = sharedPlace,
        journeyAsk = journeyAsk,
        confirmCancelJourney = confirmCancelJourney,
        onDismissCancel = { confirmCancelJourney = false },
    )
}

/** 기록 탭 머리 — 큰 제목과 검색 버튼. 날씨 배너 대신 쓴다. */
@Composable
private fun RecordsHeader(topPadding: androidx.compose.ui.unit.Dp, onSearch: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = topPadding + 12.dp, start = 20.dp, end = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.tab_records),
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        com.naury.kinetica.ui.KIconButton(
            AppIcons.Search,
            stringResource(R.string.action_search),
            onClick = onSearch,
            surface = com.naury.kinetica.ui.SurfaceStyles.LiteGlassDark,
            contentColor = Color.White,
        )
    }
}

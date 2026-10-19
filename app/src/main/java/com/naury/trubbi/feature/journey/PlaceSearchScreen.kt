package com.naury.trubbi.feature.journey

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naury.trubbi.R
import com.naury.trubbi.core.i18n.label
import com.naury.trubbi.core.location.LocationProvider
import com.naury.trubbi.core.location.PlaceCandidate
import com.naury.trubbi.data.i18n.displayName
import com.naury.trubbi.data.local.JourneyStopEntity
import com.naury.trubbi.data.local.JourneyWithStops
import com.naury.trubbi.data.repo.JourneyRepository
import com.naury.trubbi.domain.model.StopKind
import com.naury.trubbi.domain.tracking.JourneyTracker
import com.naury.trubbi.ui.common.KakaoMapView
import com.naury.trubbi.ui.common.MapFocus
import com.naury.trubbi.ui.common.MapMarker
import com.naury.trubbi.ui.common.MapPoint
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.GlassIconButton
import com.naury.trubbi.ui.designsystem.PillButton
import com.naury.trubbi.ui.designsystem.pressable
import com.naury.trubbi.ui.theme.LocalTrubbiColors
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PlaceSearchViewModel @Inject constructor(
    private val location: LocationProvider,
    private val journeyRepo: JourneyRepository,
    private val tracker: JourneyTracker,
) : ViewModel() {

    val query = MutableStateFlow("")
    val results = MutableStateFlow<List<PlaceCandidate>?>(null)
    val searching = MutableStateFlow(false)

    val activeJourney = journeyRepo.activeJourney
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 진행 중 여정의 실제 주행 경로(GPS 브레드크럼) — 도로를 따라가는 경로선. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val journeyRoute = journeyRepo.activeJourney.flatMapLatest { j ->
        if (j == null) flowOf(emptyList()) else journeyRepo.observeRoutePoints(j.journey.journeyId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 현재 위치 — 검색 전 초기 지도 중심으로 사용(일반 지도 앱처럼). */
    val currentLatLng = MutableStateFlow<Pair<Double, Double>?>(null)

    init {
        refreshLocation()
    }

    /** 현재 위치 재측정(현위치 버튼 / 주기적 추적). */
    fun refreshLocation() {
        viewModelScope.launch {
            runCatching { location.capture() }.getOrNull()?.let { currentLatLng.value = it.latitude to it.longitude }
        }
    }

    fun setQuery(q: String) {
        query.value = q
        if (q.isBlank()) results.value = null
    }

    fun search() {
        val q = query.value.trim()
        if (q.isEmpty() || searching.value) return
        viewModelScope.launch {
            searching.value = true
            results.value = location.searchPlaces(q, 12)
            searching.value = false
        }
    }

    /**
     * 이 장소를 첫 정차지로 새 여정을 시작한다.
     *
     * @param place 첫 정차지가 될 장소
     * @param kind 상차지인지 하차지인지
     * @return 만들어진 여정 id
     */
    fun startJourneyHere(place: PlaceCandidate, kind: StopKind, onStarted: (String) -> Unit) {
        viewModelScope.launch {
            val id = journeyRepo.startJourney(
                listOf(JourneyRepository.StopDraft(kind, place.name, place.address, place.latitude, place.longitude)),
            )
            tracker.start(id)
            onStarted(id)
        }
    }

    /**
     * 진행 중 여정에 정차지를 추가한다.
     *
     * @param place 추가할 장소
     * @param kind 상차지인지 하차지인지
     */
    fun addToActive(place: PlaceCandidate, kind: StopKind, onAdded: () -> Unit) {
        val active: JourneyWithStops = activeJourney.value ?: return
        viewModelScope.launch {
            journeyRepo.addStop(active.journey.journeyId, kind, place.name, place.address, place.latitude, place.longitude)
            onAdded()
        }
    }
}

/**
 * 지도 위에서 하는 장소 검색.
 *
 * 검색하면 결과가 지도에 핀으로 뜨고, 결과나 핀을 누르면 그 자리에서 여정을 시작하거나
 * 진행 중 여정에 상·하차지로 붙일 수 있다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceSearchScreen(
    onBack: () -> Unit,
    onJourneyStarted: (String) -> Unit,
    viewModel: PlaceSearchViewModel = hiltViewModel(),
) {
    val c = LocalTrubbiColors.current
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val searching by viewModel.searching.collectAsState()
    val active by viewModel.activeJourney.collectAsState()
    val primary = MaterialTheme.colorScheme.primary.toArgb()
    val onPrimary = MaterialTheme.colorScheme.onPrimary.toArgb()

    var selected by remember { mutableStateOf<PlaceCandidate?>(null) }
    var selectedStop by remember { mutableStateOf<com.naury.trubbi.data.local.JourneyStopEntity?>(null) }
    val handle = remember { com.naury.trubbi.ui.common.MapHandle() }
    var mapReady by remember { mutableStateOf(false) }

    // 인앱 음성
    val speech = com.naury.trubbi.core.voice.rememberSpeechInput()
    val context = androidx.compose.ui.platform.LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val audioPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            speech.start {
                viewModel.setQuery(it)
                viewModel.search()
            }
        }
    }

    val here by viewModel.currentLatLng.collectAsState()
    val routePoints by viewModel.journeyRoute.collectAsState()

    // 화면이 떠 있는 동안 현위치를 주기적으로 갱신(추적).
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refreshLocation()
            kotlinx.coroutines.delay(10_000)
        }
    }

    // 검색 결과가 없고 진행 중 여정이 있으면 → 첫 화면에 여정 정차지·경로 표시.
    val journeyStops = remember(active, results) {
        if (results == null) {
            active?.orderedStops?.filter { it.latitude != null && it.longitude != null }.orEmpty()
        } else {
            emptyList()
        }
    }
    val markers = remember(results, journeyStops, primary, onPrimary) {
        val list = results
        when {
            list != null -> list.mapIndexed { i, p ->
                MapMarker("r$i", MapPoint(p.latitude, p.longitude), searchPin(i + 1, primary, onPrimary))
            }
            else -> journeyStops.mapIndexedNotNull { i, stop ->
                val point = stop.point() ?: return@mapIndexedNotNull null
                MapMarker(
                    "s$i",
                    point,
                    stopPin(
                        resources.getString(stop.kindEnum.shortRes),
                        stop.completedAt != null,
                        stop.stopId == active?.currentStop?.stopId,
                    ),
                )
            }
        }
    }
    // 실제 주행 경로가 있으면 그것을 쓰고, 아직 없으면 정차지를 직선으로 이어 형태만 보여 준다.
    val route = remember(routePoints, journeyStops, results) {
        when {
            results != null -> emptyList()
            routePoints.size >= 2 -> routePoints.map { MapPoint(it.latitude, it.longitude) }
            journeyStops.size >= 2 -> journeyStops.mapNotNull { it.point() }
            else -> emptyList()
        }
    }
    val myLoc = here?.let { MapPoint(it.first, it.second) }

    // 상황별 지도 포커스: 선택 → 확대 / 결과 → 전체 / 여정 → 경로 전체. (현위치는 here 갱신마다
    // 카메라가 튀지 않도록 포커스에 넣지 않고, 최초 1회만 아래 효과로 중심을 잡는다.)
    val focus = remember(selected, selectedStop, results, journeyStops) {
        val sel = selected
        val st = selectedStop
        val list = results
        when {
            sel != null -> MapFocus.Point(sel.latitude, sel.longitude, 16)
            st?.point() != null -> MapFocus.Point(st.latitude ?: 0.0, st.longitude ?: 0.0, 16)
            !list.isNullOrEmpty() -> MapFocus.Bounds(list.map { MapPoint(it.latitude, it.longitude) })
            else -> null // 여정이 있어도 자동 프레이밍하지 않음 → 처음엔 내 위치에 포커스.
        }
    }

    // 초기 카메라는 여정 유무와 무관하게 현재 위치를 잡는다. 검색 결과가 나오면 그쪽으로 옮긴다.
    var didInitialCenter by remember { mutableStateOf(false) }
    LaunchedEffect(mapReady, here, results) {
        val start = here
        if (mapReady && !didInitialCenter && start != null && results == null) {
            handle.moveTo(start.first, start.second, 15)
            didInitialCenter = true
        }
    }

    // 결과 바텀시트 높이 + 그에 붙어 다니는 툴 버튼 위치.
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val sheetHeight = (config.screenHeightDp * 0.42f).dp.coerceIn(280.dp, 440.dp)
    val toolsBottom by animateDpAsState(if (results != null) sheetHeight + 14.dp else 56.dp, label = "toolsBottom")

    // 전체화면 지도 + 플로팅 UI
    Box(Modifier.fillMaxSize()) {
        KakaoMapView(
            markers = markers,
            route = route,
            routeColor = 0xFFF5871F.toInt(),
            focus = focus,
            myLocation = myLoc,
            handle = handle,
            onMarkerClick = { id ->
                when {
                    id.startsWith("r") -> id.drop(1).toIntOrNull()?.let { selected = results?.getOrNull(it) }
                    id.startsWith("s") -> id.drop(1).toIntOrNull()?.let { selectedStop = journeyStops.getOrNull(it) }
                }
            },
            onReady = { mapReady = true },
            modifier = Modifier.fillMaxSize(),
        )

        // ── 상단 플로팅 검색 바
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(AppIcons.ArrowBackIos, onBack, stringResource(R.string.action_back), size = 42.dp)
            Spacer(Modifier.width(8.dp))
            Row(
                Modifier
                    .weight(1f)
                    .shadow(8.dp, RoundedCornerShape(percent = 50), clip = false)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Search, contentDescription = null, tint = c.secondaryLabel, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(stringResource(R.string.place_search_hint), style = MaterialTheme.typography.bodyMedium, color = c.secondaryLabel)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Icon(
                    AppIcons.Mic,
                    contentDescription = stringResource(R.string.place_search_voice),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp).pressable {
                        if (androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.RECORD_AUDIO,
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            speech.start {
                                viewModel.setQuery(it)
                                viewModel.search()
                            }
                        } else {
                            audioPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                        }
                    },
                )
            }
        }

        // ── 우측 플로팅 컨트롤 — 공용 MapControls(현위치+줌)로 통일 + 재검색 버튼.
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = toolsBottom),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            com.naury.trubbi.ui.common.MapControls(
                handle = handle,
                onRecenter = {
                    here?.let { handle.moveTo(it.first, it.second, 16) }
                    viewModel.refreshLocation()
                },
            )
            GlassIconButton(
                AppIcons.Refresh,
                {
                    viewModel.refreshLocation()
                    if (query.isNotBlank()) viewModel.search()
                },
                stringResource(R.string.action_refresh),
            )
        }

        // ── 범례(검색 전, 여정 경로 표시 중) — 색상별 표기 안내.
        if (results == null && (route.isNotEmpty() || journeyStops.isNotEmpty())) {
            Column(
                Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 14.dp, top = 78.dp)
                    .shadow(6.dp, RoundedCornerShape(14.dp), clip = false)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LegendRow(line = true, color = Color(0xFFF5871F), label = stringResource(R.string.map_legend_route))
                LegendRow(line = false, color = Color(0xFFF5871F), label = stringResource(R.string.map_legend_current_stop))
                LegendRow(line = false, color = Color(0xFF33373D), label = stringResource(R.string.map_legend_stops))
                LegendRow(line = false, color = Color(0xFF2D6CDF), label = stringResource(R.string.map_legend_here))
            }
        }

        // ── 검색 결과: 지도를 밀고 올라오는 바텀 시트(툴 버튼이 그 위에 붙어 따라온다).
        AnimatedVisibility(
            visible = results != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val list = results.orEmpty()
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(sheetHeight)
                    .shadow(20.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), clip = false)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = 38.dp, height = 4.dp).clip(RoundedCornerShape(percent = 50)).background(c.separator))
                }
                Text(
                    if (list.isEmpty()) {
                        stringResource(R.string.place_search_empty)
                    } else {
                        stringResource(R.string.place_search_count, list.size)
                    },
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = c.secondaryLabel,
                    modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 6.dp),
                )
                LazyColumn(Modifier.weight(1f)) {
                    itemsIndexed(list, key = { _, p -> "${p.latitude},${p.longitude},${p.name}" }) { i, p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .pressable { selected = p }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(26.dp).clip(RoundedCornerShape(percent = 50))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("${i + 1}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = c.secondaryLabel)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    p.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                )
                                p.address?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = c.secondaryLabel, maxLines = 2) }
                            }
                        }
                    }
                }
                Spacer(Modifier.navigationBarsPadding())
            }
        }

        // ── 검색 중 표시(검색바 아래).
        if (searching) {
            Box(
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 74.dp)
                    .shadow(6.dp, RoundedCornerShape(percent = 50), clip = false)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    stringResource(R.string.place_searching, query.trim()),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.secondaryLabel,
                )
            }
        }
    }

    selected?.let { place ->
        PlaceActionSheet(
            place = place,
            hasActiveJourney = active != null,
            onDismiss = { selected = null },
            onStart = { kind ->
                viewModel.startJourneyHere(place, kind) { id ->
                    selected = null
                    onJourneyStarted(id)
                }
            },
            onAdd = { kind ->
                viewModel.addToActive(place, kind) {
                    selected = null
                    onBack()
                }
            },
        )
    }

    // 여정 정차지 마커 정보
    selectedStop?.let { stop ->
        StopInfoSheet(stop = stop, onDismiss = { selectedStop = null })
    }
}

@Composable
private fun LegendRow(line: Boolean, color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (line) {
            Box(Modifier.size(width = 16.dp, height = 3.dp).clip(RoundedCornerShape(percent = 50)).background(color))
        } else {
            Box(Modifier.size(11.dp).clip(RoundedCornerShape(percent = 50)).background(color))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StopInfoSheet(
    stop: JourneyStopEntity,
    onDismiss: () -> Unit,
) {
    val c = LocalTrubbiColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 30.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(percent = 50))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(stop.kindEnum.shortRes),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stop.displayName(),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                    )
                    Text(
                        "${stop.kindEnum.label()} · ${stop.phase.label()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = c.secondaryLabel,
                    )
                }
            }
            stop.address?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = c.secondaryLabel, maxLines = 3)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceActionSheet(
    place: PlaceCandidate,
    hasActiveJourney: Boolean,
    onDismiss: () -> Unit,
    onStart: (StopKind) -> Unit,
    onAdd: (StopKind) -> Unit,
) {
    val c = LocalTrubbiColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
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
            Spacer(Modifier.height(18.dp))
            if (hasActiveJourney) {
                Text(stringResource(R.string.place_add_to_journey), style = MaterialTheme.typography.labelMedium, color = c.secondaryLabel)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton(stringResource(R.string.place_add_pickup), { onAdd(StopKind.PICKUP) }, Modifier.weight(1f), icon = AppIcons.Pickup)
                    PillButton(
                        stringResource(R.string.place_add_dropoff),
                        { onAdd(StopKind.DROPOFF) },
                        Modifier.weight(1f),
                        icon = AppIcons.Dropoff,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(14.dp))
            }
            Text(
                stringResource(
                    if (hasActiveJourney) R.string.place_or_start_new else R.string.place_start_journey_here,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = c.secondaryLabel,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(stringResource(R.string.place_depart_here), { onStart(StopKind.PICKUP) }, Modifier.weight(1f), icon = AppIcons.Pickup)
                PillButton(
                    stringResource(R.string.place_arrive_here),
                    { onStart(StopKind.DROPOFF) },
                    Modifier.weight(1f),
                    icon = AppIcons.Flag,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** 물방울 모양 핀에 이름을 넣어 그린다. 흰 테두리를 둘러 어떤 지도 색 위에서도 읽힌다. */
private fun pinBitmap(label: String, fill: Int, textColor: Int): Bitmap {
    val w = 96
    val h = 124
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val cx = w / 2f
    val r = w / 2f - 10f
    val cy = r + 8f
    val tipY = h - 8f

    paint.color = 0x33000000
    canvas.drawOval(cx - r * 0.55f, tipY - 12f, cx + r * 0.55f, tipY - 2f, paint)

    fun drawPin(radius: Float, color: Int) {
        paint.color = color
        val path = android.graphics.Path().apply {
            // 둥근 머리 + 아래로 뾰족한 꼬리.
            addCircle(cx, cy, radius, android.graphics.Path.Direction.CW)
            moveTo(cx - radius * 0.62f, cy + radius * 0.55f)
            lineTo(cx + radius * 0.62f, cy + radius * 0.55f)
            lineTo(cx, tipY)
            close()
        }
        canvas.drawPath(path, paint)
    }
    drawPin(r + 5f, AColor.WHITE) // 흰 외곽선
    drawPin(r, fill)

    paint.color = textColor
    paint.textSize = w * 0.40f
    paint.textAlign = Paint.Align.CENTER
    paint.isFakeBoldText = true
    canvas.drawText(label, cx, cy - (paint.descent() + paint.ascent()) / 2f, paint)
    return out
}

/** 번호가 붙은 검색 결과 핀. 음성으로 “두 번째” 라고 부를 수 있게 번호를 보여 준다. */
private fun searchPin(rank: Int, color: Int, textColor: Int): Bitmap =
    pinBitmap("$rank", color, textColor)

/** Journey stop pin: 상/하차 글자, 현재 정차지=앰버 강조, 완료=회색, 그 외=잉크. */
private fun stopPin(label: String, done: Boolean, current: Boolean): Bitmap {
    val fill = when {
        current -> 0xFFF5871F.toInt()
        done -> 0xFF9AA0A6.toInt()
        else -> 0xFF33373D.toInt()
    }
    return pinBitmap(label, fill, AColor.WHITE)
}

/** 좌표가 있는 정차지만 지도 점으로 바꾼다. 등록만 하고 좌표를 못 채운 정차지가 있을 수 있다. */
private fun JourneyStopEntity.point(): MapPoint? {
    val lat = latitude ?: return null
    val lng = longitude ?: return null
    return MapPoint(lat, lng)
}

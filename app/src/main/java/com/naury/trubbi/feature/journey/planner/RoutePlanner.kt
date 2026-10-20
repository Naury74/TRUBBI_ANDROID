package com.naury.trubbi.feature.journey.planner

import android.app.Activity
import android.location.Location
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.naury.kinetica.ui.KButton
import com.naury.kinetica.ui.KButtonSize
import com.naury.kinetica.ui.KEmptyState
import com.naury.kinetica.ui.KIconButton
import com.naury.kinetica.ui.KListItem
import com.naury.kinetica.ui.KListLeading
import com.naury.kinetica.ui.KListTrailing
import com.naury.kinetica.ui.KSearchBar
import com.naury.kinetica.ui.KSegmentedControl
import com.naury.kinetica.ui.KSkeletonListItem
import com.naury.kinetica.ui.KSurface
import com.naury.kinetica.ui.KVariant
import com.naury.trubbi.R
import com.naury.trubbi.core.location.CapturedLocation
import com.naury.trubbi.core.location.PlaceCandidate
import com.naury.trubbi.core.voice.PlaceUtterance
import com.naury.trubbi.core.voice.rememberSpeechInput
import com.naury.trubbi.data.local.FavoritePlaceEntity
import com.naury.trubbi.data.local.JourneyStopEntity
import com.naury.trubbi.data.repo.JourneyRepository.StopDraft
import com.naury.trubbi.domain.model.StopKind
import com.naury.trubbi.ui.common.KakaoMapView
import com.naury.trubbi.ui.common.MapFocus
import com.naury.trubbi.ui.common.MapMarker
import com.naury.trubbi.ui.common.MapPoint
import com.naury.trubbi.ui.common.pinBitmap
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.VoiceListeningCard
import com.naury.trubbi.ui.designsystem.messageRes
import com.naury.trubbi.ui.designsystem.rememberMicStarter
import com.naury.trubbi.ui.theme.LocalTrubbiColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 티맵처럼 지도 위에서 출발지·도착지를 고르는 화면. 새 여정을 시작하거나 진행 중인 여정에 정차지를 더한다.
 *
 * 출발지는 처음부터 현재 위치로 채워 두고, 목적지 칸을 눌러 바로 검색하게 한다. 글자를 치는 대로 찾고(0.3초 쉬면),
 * 결과마다 내 위치에서의 직선거리를 보여 준다. 음성은 마이크 버튼을 눌렀을 때만 켠다. 목적지를 고르면 지도에
 * 경로를 미리 보여 주고 출발 버튼 한 번으로 시작한다.
 *
 * @param addMode true 면 진행 중 여정에 정차지를 더한다(출발지 칸이 없다)
 * @param recentStops 최근·자주 간 정차지. 검색어가 없을 때 보여 준다
 * @param favorites 별표한 장소
 * @param searchPlaces 검색어와 기준 위치(위도, 경도)로 후보를 찾는다
 * @param currentPlace 현재 위치와 주소. 권한이 없거나 측위가 안 되면 null
 * @param onToggleFavorite 별표 켜고 끄기
 * @param onConfirm 출발(또는 정차지 추가)을 눌렀을 때. 정차지 목록이 순서대로 온다
 * @param onDismiss 닫을 때
 * @param initialDestination 목적지를 미리 채워 연다(즐겨찾기 칩·공유받은 장소)
 */
@Composable
fun RoutePlanner(
    addMode: Boolean,
    recentStops: List<JourneyStopEntity>,
    favorites: List<FavoritePlaceEntity>,
    searchPlaces: suspend (query: String, nearLat: Double?, nearLng: Double?) -> List<PlaceCandidate>,
    currentPlace: suspend () -> CapturedLocation?,
    onToggleFavorite: (name: String, address: String?, lat: Double?, lng: Double?) -> Unit,
    onConfirm: (List<StopDraft>) -> Unit,
    onDismiss: () -> Unit,
    initialDestination: PlannedPlace? = null,
) {
    val hereLabel = stringResource(R.string.planner_here)
    var plan by remember { mutableStateOf(RoutePlan(addMode = addMode, destination = initialDestination)) }
    // 목적지가 없으면 바로 목적지 검색부터 연다(티맵의 "어디로 갈까요?").
    var editing by remember { mutableStateOf<PlanField?>(if (initialDestination == null) PlanField.Destination else null) }
    var locating by remember { mutableStateOf(!addMode) }

    LaunchedEffect(Unit) {
        val here = currentPlace()
        // 출발지 이름은 여정 기록에 남으므로 "현재 위치"라는 글자 대신 짧은 주소로 둔다(화면에는 계속 "현재 위치").
        plan = plan.copy(here = here?.let { PlannedPlace(shortAddress(it.address) ?: hereLabel, it.address, it.latitude, it.longitude) })
        locating = false
    }

    // 홈은 날씨 배경 때문에 상태 표시줄 글자를 늘 흰색으로 둔다. 이 화면은 밝은 지도·패널 위라 테마에 맞춰 되돌린다.
    val view = LocalView.current
    val darkUi = LocalTrubbiColors.current.isDark
    DisposableEffect(darkUi) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = !darkUi
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }

    BackHandler {
        // 검색 중이면 검색만 닫는다. 처음 연 목적지 검색에서 아무것도 안 골랐으면 화면을 닫는다.
        if (editing != null && plan.destination != null) editing = null else onDismiss()
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PlanMap(plan, Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            RouteCard(
                plan = plan,
                locating = locating,
                onBack = onDismiss,
                onEdit = { editing = it },
                onSwap = { plan = plan.swap() },
                onRemoveVia = { plan = plan.removeWaypoint(it) },
                onToggleViaKind = { plan = plan.toggleWaypointKind(it) },
            )
        }

        AnimatedVisibility(
            visible = editing == null && plan.destination != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            SummaryPanel(
                plan = plan,
                onAddVia = { editing = PlanField.Via(null) },
                onToggleDestinationKind = { plan = plan.toggleDestinationKind() },
                onConfirm = { onConfirm(plan.toDrafts()) },
            )
        }

        editing?.let { field ->
            SearchPanel(
                field = field,
                addMode = addMode,
                here = plan.here,
                locating = locating,
                recentStops = recentStops,
                favorites = favorites,
                searchPlaces = searchPlaces,
                onToggleFavorite = onToggleFavorite,
                onPick = { place ->
                    plan = if (place == null) plan.useHereAsOrigin() else plan.put(field, place)
                    editing = null
                },
                onClose = { if (plan.destination == null && field == PlanField.Destination) onDismiss() else editing = null },
            )
        }
    }
}

/** 출발 → 경유 → 도착을 지도에 핀과 직선으로 보여 준다. 도로 경로가 아니라 순서를 보이기 위한 선이다. */
@Composable
private fun PlanMap(plan: RoutePlan, modifier: Modifier) {
    val primary = MaterialTheme.colorScheme.primary.toArgb()
    val ink = 0xFF33373D.toInt()
    val labels = Triple(
        stringResource(R.string.planner_origin),
        stringResource(R.string.planner_destination),
        StopKind.entries.associateWith { stringResource(it.shortRes) },
    )
    val places = plan.mapPoints()
    val markers = remember(places, labels) {
        val origin = if (!plan.addMode) (plan.origin ?: plan.here) else null
        places.mapIndexed { i, p ->
            val point = MapPoint(p.latitude!!, p.longitude!!)
            val (label, color) = when {
                p == plan.destination -> labels.second to primary
                p == origin && i == 0 -> labels.first to ink
                else -> (plan.waypoints.firstOrNull { it.place == p }?.kind?.let { labels.third[it] } ?: "$i") to ink
            }
            MapMarker("p$i", point, pinBitmap(label, color))
        }
    }
    val points = places.map { MapPoint(it.latitude!!, it.longitude!!) }
    val focus = when {
        points.size >= 2 -> MapFocus.Bounds(points, paddingPx = 220)
        points.size == 1 -> MapFocus.Point(points[0].lat, points[0].lng, zoom = 15)
        else -> null
    }
    val here = plan.here?.let { h -> h.latitude?.let { MapPoint(it, h.longitude!!) } }
    KakaoMapView(markers = markers, route = points, modifier = modifier, focus = focus, myLocation = here)
}

/** 위쪽 입력 카드. 출발·경유·도착 칸을 세로로 쌓고, 오른쪽에 바꾸기 버튼을 둔다. */
@Composable
private fun RouteCard(
    plan: RoutePlan,
    locating: Boolean,
    onBack: () -> Unit,
    onEdit: (PlanField) -> Unit,
    onSwap: () -> Unit,
    onRemoveVia: (Int) -> Unit,
    onToggleViaKind: (Int) -> Unit,
) {
    val c = LocalTrubbiColors.current
    KSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            KIconButton(AppIcons.ArrowBackIos, stringResource(R.string.action_back), onBack, variant = KVariant.Text, size = 44.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!plan.addMode) {
                    val origin = plan.origin
                    PlaceField(
                        icon = AppIcons.OriginDot,
                        tint = c.green,
                        text = origin?.name ?: if (locating) stringResource(R.string.planner_locating) else stringResource(R.string.planner_here),
                        filled = true,
                        onClick = { onEdit(PlanField.Origin) },
                    )
                }
                plan.waypoints.forEachIndexed { i, w ->
                    PlaceField(
                        icon = if (w.kind == StopKind.PICKUP) AppIcons.Pickup else AppIcons.Dropoff,
                        tint = c.orange,
                        text = w.place.name,
                        filled = true,
                        badge = stringResource(w.kind.shortRes),
                        onBadge = { onToggleViaKind(i) },
                        onRemove = { onRemoveVia(i) },
                        onClick = { onEdit(PlanField.Via(i)) },
                    )
                }
                val dest = plan.destination
                PlaceField(
                    icon = AppIcons.Flag,
                    tint = MaterialTheme.colorScheme.primary,
                    text = dest?.name ?: stringResource(if (plan.addMode) R.string.planner_add_hint else R.string.planner_dest_hint),
                    filled = dest != null,
                    onClick = { onEdit(PlanField.Destination) },
                )
            }
            if (!plan.addMode) {
                KIconButton(AppIcons.SwapVert, stringResource(R.string.planner_swap), onSwap, variant = KVariant.Text, size = 44.dp)
            }
        }
    }
}

/** 장소 칸 한 줄. 누르면 그 칸의 검색이 열린다. */
@Composable
private fun PlaceField(
    icon: ImageVector,
    tint: Color,
    text: String,
    filled: Boolean,
    onClick: () -> Unit,
    badge: String? = null,
    onBadge: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    val c = LocalTrubbiColors.current
    KSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(46.dp),
        shape = RoundedCornerShape(14.dp),
        surface = com.naury.kinetica.ui.SurfaceStyle.Solid(
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            background = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (filled) FontWeight.SemiBold else FontWeight.Normal),
                color = if (filled) MaterialTheme.colorScheme.onSurface else c.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (badge != null && onBadge != null) {
                KButton(badge, onBadge, size = KButtonSize.Small, variant = KVariant.Tonal)
            }
            if (onRemove != null) {
                KIconButton(AppIcons.Close, stringResource(R.string.planner_remove_via), onRemove, variant = KVariant.Text, size = 36.dp)
            }
        }
    }
}

/** 목적지를 고른 뒤 아래에 뜨는 요약. 거리·경유 수와 큰 출발 버튼. */
@Composable
private fun SummaryPanel(
    plan: RoutePlan,
    onAddVia: () -> Unit,
    onToggleDestinationKind: () -> Unit,
    onConfirm: () -> Unit,
) {
    val c = LocalTrubbiColors.current
    val dest = plan.destination ?: return
    KSurface(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                dest.name,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val facts = buildList {
                straightDistance(plan)?.let { add(stringResource(R.string.planner_distance_straight, formatDistance(it))) }
                if (plan.waypoints.isNotEmpty()) add(pluralStringResource(R.plurals.planner_via_count, plan.waypoints.size, plan.waypoints.size))
                dest.address?.takeIf { it != dest.name }?.let(::add)
            }
            if (facts.isNotEmpty()) {
                Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = c.secondaryLabel)
            }
            if (plan.addMode) {
                // 진행 중 여정에 더할 때는 상차지인지 하차지인지가 중요하다.
                KSegmentedControl(
                    options = listOf(stringResource(StopKind.PICKUP.labelRes), stringResource(StopKind.DROPOFF.labelRes)),
                    selectedIndex = if (plan.destinationKind == StopKind.PICKUP) 0 else 1,
                    onSelect = { i -> if ((i == 0) != (plan.destinationKind == StopKind.PICKUP)) onToggleDestinationKind() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                KButton(
                    stringResource(R.string.planner_add_via),
                    onAddVia,
                    icon = AppIcons.Add,
                    variant = KVariant.Tonal,
                    size = KButtonSize.Large,
                )
                KButton(
                    stringResource(if (plan.addMode) R.string.planner_add_confirm else R.string.planner_start),
                    onConfirm,
                    icon = if (plan.addMode) AppIcons.Add else AppIcons.Navigation,
                    size = KButtonSize.Large,
                    modifier = Modifier.weight(1f),
                    fullWidth = true,
                )
            }
        }
    }
}

/** 칸 하나의 장소를 찾는 전체 화면 패널. 검색어가 없으면 현재 위치·즐겨찾기·최근 간 곳을 보여 준다. */
@Composable
private fun SearchPanel(
    field: PlanField,
    addMode: Boolean,
    here: PlannedPlace?,
    locating: Boolean,
    recentStops: List<JourneyStopEntity>,
    favorites: List<FavoritePlaceEntity>,
    searchPlaces: suspend (String, Double?, Double?) -> List<PlaceCandidate>,
    onToggleFavorite: (String, String?, Double?, Double?) -> Unit,
    onPick: (PlannedPlace?) -> Unit,
    onClose: () -> Unit,
) {
    var query by remember(field) { mutableStateOf("") }
    var results by remember(field) { mutableStateOf<List<PlaceCandidate>?>(null) }
    var searching by remember(field) { mutableStateOf(false) }
    // 음성 검색이 이미 찾아 둔 결과. 검색어를 바꿔 넣을 때 같은 검색을 두 번 하지 않게 한다.
    var voiceHit by remember(field) { mutableStateOf<VoicePick?>(null) }
    var voiceNote by remember(field) { mutableStateOf<String?>(null) }

    // 치는 대로 찾는다. 0.3초 안에 다음 글자가 오면 앞 검색은 버린다.
    LaunchedEffect(query, here) {
        if (query.isBlank()) {
            results = null
            searching = false
            return@LaunchedEffect
        }
        voiceHit?.takeIf { it.query == query }?.let {
            results = it.results
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(SEARCH_DEBOUNCE_MS)
        results = searchPlaces(query.trim(), here?.latitude, here?.longitude)
        searching = false
    }

    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val speech = rememberSpeechInput()
    // 인식기에 미리 알려 줄 말. 기사가 자주 가는 곳 이름과 이 화면의 명령어다.
    val voiceHints = remember(favorites, recentStops) {
        favorites.map { it.name } + recentStops.mapNotNull { it.name } + VOICE_COMMAND_HINTS
    }

    fun onSpoken(top: String) {
        val heard = speech.alternatives.ifEmpty { listOf(top) }
        when (val meaning = PlaceUtterance.meaning(heard.first())) {
            PlaceUtterance.Meaning.Cancel -> onClose()
            PlaceUtterance.Meaning.Here -> if (field == PlanField.Origin) {
                onPick(null)
            } else {
                voiceNote = resources.getString(R.string.voice_retry)
            }
            else -> {
                val queries = PlaceUtterance.queries(heard).ifEmpty { listOfNotNull((meaning as? PlaceUtterance.Meaning.Search)?.query) }
                if (queries.isEmpty()) {
                    voiceNote = resources.getString(R.string.voice_retry)
                    return
                }
                voiceNote = null
                searching = true
                query = queries.first()
                scope.launch {
                    val pick = pickVoiceQuery(queries) { q -> searchPlaces(q, here?.latitude, here?.longitude) } ?: return@launch
                    voiceHit = pick
                    if (query == pick.query) {
                        results = pick.results
                        searching = false
                    } else {
                        query = pick.query
                    }
                }
            }
        }
    }

    val listen = rememberMicStarter(onDenied = { voiceNote = resources.getString(R.string.voice_need_mic) }) {
        keyboard?.hide()
        focusManager.clearFocus()
        voiceNote = null
        speech.start(voiceHints, ::onSpoken)
    }
    LaunchedEffect(speech.error) {
        if (speech.error) voiceNote = speech.failure?.let { resources.getString(it.messageRes()) }
    }
    // 칸을 누르면 바로 칠 수 있게 검색창에 포커스를 준다(키보드가 같이 올라온다).
    val focus = remember { FocusRequester() }
    LaunchedEffect(field) { runCatching { focus.requestFocus() } }
    val favoriteKeys = favorites.map { it.name to it.address }.toSet()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            KIconButton(AppIcons.ArrowBackIos, stringResource(R.string.action_back), onClose, variant = KVariant.Text, size = 44.dp)
            KSearchBar(
                query = query,
                onQueryChange = {
                    query = it
                    voiceNote = null
                },
                onSearch = { query = it },
                placeholder = stringResource(
                    when (field) {
                        PlanField.Origin -> R.string.planner_origin_hint
                        PlanField.Destination -> if (addMode) R.string.planner_add_hint else R.string.planner_dest_hint
                        is PlanField.Via -> R.string.planner_via_hint
                    },
                ),
                showCancel = false,
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
            KIconButton(
                AppIcons.Mic,
                stringResource(R.string.planner_voice),
                onClick = { if (speech.listening) speech.cancel() else listen() },
                variant = if (speech.listening) KVariant.Tonal else KVariant.Text,
                size = 44.dp,
            )
        }

        if (speech.listening) {
            VoiceListeningCard(
                speech = speech,
                hint = stringResource(R.string.planner_voice_examples),
                onStop = speech::cancel,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        voiceNote?.let { note ->
            Text(
                note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }
        // 인식기가 다르게 들은 후보. 틀리게 들렸으면 다시 말하지 않고 눌러서 바꾼다.
        val others = voiceHit?.takeIf { it.query == query }?.others.orEmpty()
        if (others.isNotEmpty() && !speech.listening) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.planner_did_you_mean),
                    style = MaterialTheme.typography.labelLarge,
                    color = LocalTrubbiColors.current.secondaryLabel,
                )
                others.forEach { alt ->
                    KButton(alt, { query = alt }, size = KButtonSize.Small, variant = KVariant.Tonal)
                }
            }
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
            val list = results
            when {
                query.isBlank() -> {
                    if (field == PlanField.Origin) {
                        item(key = "here") {
                            KListItem(
                                headline = stringResource(R.string.planner_here),
                                supporting = if (locating) stringResource(R.string.planner_locating) else here?.address,
                                leading = KListLeading.Icon(AppIcons.MyLocation, tint = LocalTrubbiColors.current.green),
                                onClick = { onPick(null) },
                            )
                        }
                    }
                    if (favorites.isNotEmpty()) {
                        item(key = "favH") { SectionLabel(stringResource(R.string.planner_favorites)) }
                        items(favorites, key = { "f_" + it.id }) { f ->
                            KListItem(
                                headline = f.name,
                                supporting = f.address,
                                leading = KListLeading.Icon(AppIcons.Star, tint = MaterialTheme.colorScheme.primary),
                                trailing = distanceTrailing(here, f.latitude, f.longitude),
                                onClick = { onPick(PlannedPlace(f.name, f.address, f.latitude, f.longitude)) },
                            )
                        }
                    }
                    val recents = recentStops.filter { it.kind != StopKind.ORIGIN.name && !it.name.isNullOrBlank() }
                    item(key = "recH") { SectionLabel(stringResource(R.string.planner_recent)) }
                    if (recents.isEmpty()) {
                        item(key = "recE") {
                            Text(
                                stringResource(R.string.planner_recent_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = LocalTrubbiColors.current.secondaryLabel,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            )
                        }
                    }
                    items(recents, key = { "r_" + it.stopId }) { s ->
                        KListItem(
                            headline = s.name.orEmpty(),
                            supporting = s.address,
                            leading = KListLeading.Icon(AppIcons.Restore, tint = LocalTrubbiColors.current.secondaryLabel),
                            trailing = distanceTrailing(here, s.latitude, s.longitude),
                            onClick = { onPick(PlannedPlace(s.name.orEmpty(), s.address, s.latitude, s.longitude)) },
                        )
                    }
                }
                searching && list == null -> items(SKELETON_ROWS) { KSkeletonListItem(trailing = true) }
                list.isNullOrEmpty() -> item(key = "empty") {
                    KEmptyState(
                        title = stringResource(R.string.planner_no_results),
                        message = stringResource(R.string.planner_no_results_body),
                        modifier = Modifier.padding(top = 40.dp),
                    )
                }
                else -> itemsIndexed(list, key = { i, p -> "s_${i}_${p.latitude}_${p.longitude}" }) { _, p ->
                    val starred = (p.name to p.address) in favoriteKeys
                    KListItem(
                        headline = p.name,
                        supporting = listOfNotNull(p.category, p.address).joinToString(" · ").ifBlank { null },
                        leading = KListLeading.Icon(AppIcons.LocationOn, tint = MaterialTheme.colorScheme.primary),
                        trailing = KListTrailing.Custom {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                p.distanceM?.let {
                                    Text(
                                        formatDistance(it),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = LocalTrubbiColors.current.secondaryLabel,
                                    )
                                }
                                KIconButton(
                                    if (starred) AppIcons.Star else AppIcons.StarBorder,
                                    resources.getString(if (starred) R.string.planner_unstar else R.string.planner_star),
                                    onClick = { onToggleFavorite(p.name, p.address, p.latitude, p.longitude) },
                                    variant = KVariant.Text,
                                    size = 40.dp,
                                    contentColor = if (starred) MaterialTheme.colorScheme.primary else LocalTrubbiColors.current.secondaryLabel,
                                )
                            }
                        },
                        onClick = { onPick(PlannedPlace(p.name, p.address, p.latitude, p.longitude)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = LocalTrubbiColors.current.secondaryLabel,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** 내 위치를 알 때만 거리 칸을 붙인다. */
private fun distanceTrailing(here: PlannedPlace?, lat: Double?, lng: Double?): KListTrailing? {
    val hLat = here?.latitude ?: return null
    val hLng = here.longitude ?: return null
    if (lat == null || lng == null) return null
    return KListTrailing.Value(formatDistance(metersBetween(hLat, hLng, lat, lng)))
}

/** 출발(또는 첫 장소)부터 도착까지 순서대로 이은 직선거리 합(m). 좌표가 둘 이상일 때만. */
internal fun straightDistance(plan: RoutePlan): Int? {
    val points = plan.mapPoints()
    if (points.size < 2) return null
    return points.zipWithNext { a, b -> metersBetween(a.latitude!!, a.longitude!!, b.latitude!!, b.longitude!!) }.sum()
}

private fun metersBetween(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Int {
    val out = FloatArray(1)
    Location.distanceBetween(lat1, lng1, lat2, lng2, out)
    return out[0].toInt()
}

/** "서울특별시 중구 한강대로 405" → "중구 한강대로 405". 세 토막 미만이면 그대로, 비었으면 null. */
internal fun shortAddress(address: String?): String? {
    val tokens = address?.trim()?.split(Regex("\\s+"))?.filter { it.isNotBlank() }.orEmpty()
    if (tokens.isEmpty()) return null
    return if (tokens.size >= 3) tokens.drop(1).joinToString(" ") else tokens.joinToString(" ")
}

/** 1km 미만은 m, 그 위는 소수 한 자리 km(100km 이상은 정수). */
internal fun formatDistance(meters: Int): String = when {
    meters < 1000 -> "${meters}m"
    meters < 100_000 -> "%.1fkm".format(meters / 1000f)
    else -> "${meters / 1000}km"
}

private const val SEARCH_DEBOUNCE_MS = 300L
private const val SKELETON_ROWS = 5

/** 검색 칸에서 쓰는 명령어. 인식기가 비슷한 소리를 이쪽으로 맞추게 미리 알려 준다. */
private val VOICE_COMMAND_HINTS = listOf("현재 위치", "취소", "물류센터", "신항", "터미널", "휴게소", "공장", "창고", "IC", "JC")

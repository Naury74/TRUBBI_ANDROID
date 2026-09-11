package com.naury.trubbi.feature.journey

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.naury.trubbi.R
import com.naury.trubbi.core.location.CapturedLocation
import com.naury.trubbi.core.location.PlaceCandidate
import com.naury.trubbi.core.voice.VoiceAction
import com.naury.trubbi.core.voice.VoiceContext
import com.naury.trubbi.core.voice.VoiceIntent
import com.naury.trubbi.data.local.JourneyStopEntity
import com.naury.trubbi.data.repo.JourneyRepository
import com.naury.trubbi.domain.model.StopKind
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.PillButton
import com.naury.trubbi.ui.designsystem.pressable
import com.naury.trubbi.ui.theme.LocalTrubbiColors
import kotlinx.coroutines.delay
/**
 * 대화형 여정 시작 마법사.
 *
 * 세 단계를 순서대로 묻는다.
 *  1. 하차지(목적지)
 *  2. 상차지(출발지) — 현재 위치·검색·음성 중 하나로 입력
 *  3. 경유지 — 없으면 건너뛴다
 *
 * 각 단계는 마이크를 먼저 열고, 화면 입력 없이 음성만으로 완료할 수 있다.
 * 인식에 실패하면 다시 묻고, 응답이 없으면 현재 위치 사용 여부를 물은 뒤 카운트다운으로 진행한다.
 *
 * 모든 단계는 건너뛸 수 있다. 정차지는 도착 시점에 자동으로 기록되므로 시작 시점에 비어 있어도
 * 여정을 만들 수 있다.
 *
 * 상태와 음성 처리는 [JourneyWizardState] 가 갖고, 이 함수는 배치만 담당한다.
 *
 * @param recentStops 최근 정차지들. 빠른 선택 목록에 쓴다
 * @param searchPlaces 장소 검색
 * @param currentPlace 현재 위치 캡처
 * @param onStart 시작을 확정할 때 정차지 목록과 함께 불린다
 * @param onDismiss 닫을 때
 * @param favorites 즐겨찾는 장소들
 * @param onToggleFavorite 지도 확인 화면에서 별표를 켜고 끌 때
 * @param initialDestination 목적지를 미리 정한 채 시작할 때. 주면 출발지 단계부터 진행한다
 * @param speak 안내를 말한다(결과를 기다리지 않음)
 * @param speakAwait 안내를 말하고 끝날 때까지 기다린다. 말이 끝나야 마이크를 열 수 있다
 * @param stopVoice 진행 중 안내를 즉시 끊는다
 * @param interpret 발화를 의도로 해석한다. 기본값은 ‘알아듣지 못함’
 * @param addMode true 면 진행 중 여정에 정차지만 추가하는 모드로, 출발·도착 단계를 건너뛴다
 */
@Composable
fun JourneyStartWizard(
    recentStops: List<JourneyStopEntity>,
    searchPlaces: suspend (String) -> List<PlaceCandidate>,
    currentPlace: suspend () -> CapturedLocation?,
    onStart: (List<JourneyRepository.StopDraft>) -> Unit,
    onDismiss: () -> Unit,
    favorites: List<com.naury.trubbi.data.local.FavoritePlaceEntity> = emptyList(),
    onToggleFavorite: (name: String, address: String?, lat: Double?, lng: Double?) -> Unit = { _, _, _, _ -> },
    initialDestination: JourneyRepository.StopDraft? = null,
    speak: (String) -> Unit = {},
    speakAwait: suspend (String) -> Unit = { speak(it) },
    stopVoice: () -> Unit = {},
    interpret: suspend (String, VoiceContext) -> VoiceIntent = { _, _ -> VoiceIntent(VoiceAction.NONE) },
    addMode: Boolean = false,
) {
    val state = rememberJourneyWizardState(
        searchPlaces = searchPlaces,
        currentPlace = currentPlace,
        interpret = interpret,
        speakAwait = speakAwait,
        stopVoice = stopVoice,
        onStart = onStart,
        onDismiss = onDismiss,
        initialDestination = initialDestination,
        addMode = addMode,
    )
    val launchSpeech = rememberSpeechLauncher(state)

    WizardVoiceEffects(state, launchSpeech)

    // 실수로 닫히지 않게 뒤로가기는 이전 단계로만 이동한다. 닫기는 X 버튼으로만 가능하다.
    BackHandler {
        if (state.confirmTarget != null) state.confirmTarget = null else state.stepBack()
    }

    WizardScaffold(
        state = state,
        recentStops = recentStops,
        favorites = favorites,
        onToggleFavorite = onToggleFavorite,
        launchSpeech = launchSpeech,
    )
}

/**
 * 마이크를 여는 함수를 만든다.
 *
 * 기기 인식기를 쓸 수 없으면 시스템 받아쓰기 화면으로 넘기고, 권한이 없으면 먼저 요청한다.
 * 두 경로 모두 결과를 [JourneyWizardState.onSpoken] 으로 되돌린다.
 */
@Composable
private fun rememberSpeechLauncher(state: JourneyWizardState): () -> Unit {
    val context = LocalContext.current
    val systemSpeech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()?.takeIf { it.isNotBlank() }
            ?.let { state.onSpoken(it.trim()) }
    }
    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) state.speech.start(state::onSpoken)
    }
    return remember(state, systemSpeech, audioPermission) {
        {
            when {
                !state.speech.available -> runCatching {
                    systemSpeech.launch(
                        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                        },
                    )
                }

                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED -> state.speech.start(state::onSpoken)

                else -> audioPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
}

/**
 * 단계 전환·인식 실패·검색 결과에 반응하는 부수 효과들.
 *
 * 안내를 읽는 동안에는 마이크를 열지 않는다. 클라우드 TTS 는 지연이 있어, 겹치면 자기 음성이
 * 그대로 인식된다.
 */
@Composable
private fun WizardVoiceEffects(state: JourneyWizardState, launchSpeech: () -> Unit) {
    // 인식 실패(무발화·오류) → 출발지 단계는 현재 위치를 제안하고, 경유지 단계는 자동 시작으로 넘어간다.
    LaunchedEffect(state.speech.error) {
        if (!state.speech.error || state.speech.listening) return@LaunchedEffect
        val idle = state.query.isBlank() && state.candidates == null
        when {
            state.step == WizardStep.ORIGIN && !state.askHere && idle && !state.locating -> state.askUseHere()
            state.step == WizardStep.EXTRA && !state.counting && state.destPlace() != null && idle -> state.startCountdown()
        }
    }

    // 진입 시 이전 화면의 안내 음성이 남아 겹치지 않게 먼저 끊는다.
    LaunchedEffect(Unit) { state.stopPreviousVoice() }

    // 단계에 들어올 때마다(경유지는 한 곳 추가될 때마다) 안내하고, 멘트가 끝난 뒤 마이크를 연다.
    LaunchedEffect(state.step, state.planned.size) {
        state.speech.cancel()
        state.resetInput()
        state.cancelCountdown()
        val prompt = state.stepPrompt()
        state.showGuide(prompt) // 화면에 먼저 띄우고
        delay(PROMPT_SETTLE_MS) // 화면 전환이 끝난 뒤 말하기 시작
        state.speakGuide(prompt)
        delay(MIC_OPEN_DELAY_MS)
        launchSpeech()
    }

    // 음성 검색 결과가 나오면 개수를 읽어 주고 몇 번째인지 묻는다.
    LaunchedEffect(state.candidates, state.voiceMode) {
        val list = state.candidates
        if (!state.voiceMode || state.pickPrompted || state.searching || list == null) return@LaunchedEffect
        state.pickPrompted = true
        state.speech.cancel()
        if (list.isNotEmpty()) {
            delay(PROMPT_SETTLE_MS)
            state.speakGuide(state.resultPrompt(list.take(MAX_SPOKEN_RESULTS).size))
            delay(MIC_OPEN_DELAY_MS)
            if (!state.candidates.isNullOrEmpty()) state.speech.start(state::onPickSpoken)
        } else if (state.step == WizardStep.ORIGIN) {
            // 출발지 검색 결과가 없으면 현재 위치 사용을 제안한다.
            state.askUseHere()
        } else {
            state.sayNoResults()
            delay(MIC_OPEN_DELAY_MS)
            launchSpeech()
        }
    }
}

/** 시트 전체 배치. 넓은 화면에서 지도 확인 중이면 좌(입력)·우(지도) 2단으로 나눈다. */
@Composable
private fun WizardScaffold(
    state: JourneyWizardState,
    recentStops: List<JourneyStopEntity>,
    favorites: List<com.naury.trubbi.data.local.FavoritePlaceEntity>,
    onToggleFavorite: (name: String, address: String?, lat: Double?, lng: Double?) -> Unit,
    launchSpeech: () -> Unit,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val enter by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(340, easing = FastOutSlowInEasing),
        label = "wizardEnter",
    )
    val slidePx = with(LocalDensity.current) { 60.dp.toPx() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoPane = maxWidth >= 720.dp && state.confirmTarget != null
        val roleLabel = when (state.step) {
            WizardStep.ORIGIN -> stringResource(R.string.wizard_role_origin)
            WizardStep.EXTRA -> stringResource(R.string.wizard_role_extra)
            WizardStep.DEST -> stringResource(R.string.wizard_role_dest)
        }
        // 뒤 배경 딤. 탭으로는 닫히지 않는다 — 입력 중에 실수로 닫히면 처음부터 다시 해야 한다.
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f * enter)))
        Row(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .graphicsLayer {
                    translationY = (1f - enter) * slidePx
                    alpha = enter
                },
        ) {
            androidx.compose.material3.Surface(
                modifier = Modifier.weight(1f).fillMaxHeight().imePadding(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            ) {
                Column(Modifier.fillMaxSize()) {
                    WizardTopBar(addMode = state.addMode, onClose = state::dismiss)
                    Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 22.dp)) {
                        WizardPrompt(state = state, launchSpeech = launchSpeech)
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                            WizardBody(
                                state = state,
                                recentStops = recentStops,
                                favorites = favorites,
                                roleLabel = roleLabel,
                                launchSpeech = launchSpeech,
                            )
                        }
                    }
                    WizardBottomBar(state)
                }
            }

            if (twoPane) {
                state.confirmTarget?.let { place ->
                    androidx.compose.material3.Surface(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        WizardConfirmMap(state, place, roleLabel, favorites, onToggleFavorite)
                    }
                }
            }
        }

        // 좁은 화면에서는 지도 확인을 전체화면 오버레이로 덮는다.
        if (!twoPane) {
            state.confirmTarget?.let { place ->
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).statusBarsPadding()) {
                    WizardConfirmMap(state, place, roleLabel, favorites, onToggleFavorite)
                }
            }
        }

        if (state.speech.listening || state.speech.processing || state.thinking) {
            VoiceListeningSheet(
                listening = state.speech.listening,
                partial = state.speech.partial,
                level = state.speech.level,
                guide = state.guide,
                hint = when (state.step) {
                    WizardStep.DEST -> stringResource(R.string.wizard_head_dest)
                    WizardStep.ORIGIN -> stringResource(R.string.wizard_head_origin)
                    WizardStep.EXTRA -> stringResource(R.string.wizard_head_extra_say)
                },
                onCancel = { state.speech.cancel() },
            )
        }
    }
}

/** 시트 손잡이와 제목·닫기 버튼. */
@Composable
private fun WizardTopBar(addMode: Boolean, onClose: () -> Unit) {
    val c = LocalTrubbiColors.current
    Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(percent = 50)).background(c.separator))
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(if (addMode) R.string.action_add_stop else R.string.home_action_start_journey),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Row(
            Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pressable(onClick = onClose)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.Close, contentDescription = null, tint = c.secondaryLabel, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.action_close), style = MaterialTheme.typography.labelLarge, color = c.secondaryLabel)
        }
    }
}

/** 단계 표시부터 입력줄까지의 고정 영역. 아래 결과 목록만 스크롤된다. */
@Composable
private fun WizardPrompt(state: JourneyWizardState, launchSpeech: () -> Unit) {
    Spacer(Modifier.height(14.dp))
    if (!state.addMode) {
        StepIndicator(state.step)
        Spacer(Modifier.height(18.dp))
    }

    AnimatedContent(
        targetState = state.step,
        transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith fadeOut() },
        label = "wizardQ",
    ) { s ->
        Text(
            when (s) {
                WizardStep.DEST -> stringResource(R.string.wizard_head_dest)
                WizardStep.ORIGIN -> stringResource(R.string.wizard_head_origin)
                WizardStep.EXTRA -> stringResource(R.string.wizard_head_extra)
            },
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }

    // 정차지가 많아도 본문을 밀지 않도록 높이를 제한하고 그 안에서만 스크롤한다.
    if (state.planned.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Box(Modifier.heightIn(max = 168.dp).verticalScroll(rememberScrollState())) {
            com.naury.trubbi.ui.designsystem.DragReorderColumn(
                items = state.planned.toList(),
                keyOf = { "${it.first}|${it.second.name}|${it.second.latitude}|${it.second.longitude}" },
                rowHeight = 50.dp,
                onReorder = state::reorder,
            ) { index, item, handle, dragging ->
                PlannedStopRow(
                    order = index + 1,
                    kind = item.first,
                    name = item.second.name,
                    handle = handle,
                    dragging = dragging,
                    onRemove = { state.remove(item) },
                )
            }
        }
    }

    if (state.step == WizardStep.EXTRA) {
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KindChip(stringResource(R.string.wizard_kind_pickup_stop), state.extraKind == StopKind.PICKUP) {
                state.extraKind = StopKind.PICKUP
            }
            KindChip(stringResource(R.string.wizard_kind_dropoff_stop), state.extraKind == StopKind.DROPOFF) {
                state.extraKind = StopKind.DROPOFF
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    StopField(
        icon = when (state.step) {
            WizardStep.ORIGIN -> AppIcons.Pickup
            WizardStep.DEST -> AppIcons.Dropoff
            WizardStep.EXTRA -> AppIcons.Add
        },
        placeholder = when (state.step) {
            WizardStep.DEST -> stringResource(R.string.wizard_hint_dest)
            WizardStep.ORIGIN -> stringResource(R.string.wizard_hint_origin)
            WizardStep.EXTRA -> stringResource(R.string.wizard_hint_extra)
        },
        value = state.query,
        onValue = state::onQueryTyped,
        onMic = launchSpeech,
        onImeSearch = state::runSearch,
    )

    WizardHeardBanner(state, launchSpeech)
    Spacer(Modifier.height(12.dp))
}

/** 인식 결과와 실패를 입력줄 바로 아래에 알린다. 결과 목록을 밀지 않도록 컴팩트하게 둔다. */
@Composable
private fun WizardHeardBanner(state: JourneyWizardState, launchSpeech: () -> Unit) {
    when {
        state.voiceMode && state.query.isNotBlank() && !state.speech.listening && !state.askHere -> {
            Spacer(Modifier.height(8.dp))
            HeardBanner(
                heard = state.query.trim(),
                alternatives = state.speech.alternatives.filter { it.trim() != state.query.trim() }.take(3),
                onRetry = launchSpeech,
                onPickAlt = state::retryWith,
            )
        }

        state.speech.error && state.idleForRetry() -> {
            Spacer(Modifier.height(8.dp))
            RetryBanner(stringResource(R.string.wizard_retry_banner), onRetry = launchSpeech)
        }
    }
}

/**
 * 남은 공간을 채우는 본문. 상태에 따라 카운트다운·검색 결과·추천 목록 중 하나를 그린다.
 *
 * 음성 인식 중 UI 는 [VoiceListeningSheet] 가 따로 덮으므로 여기서 다루지 않는다.
 */
@Composable
private fun WizardBody(
    state: JourneyWizardState,
    recentStops: List<JourneyStopEntity>,
    favorites: List<com.naury.trubbi.data.local.FavoritePlaceEntity>,
    roleLabel: String,
    launchSpeech: () -> Unit,
) {
    when {
        state.counting -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            CountdownStartCard(
                seconds = state.countSec,
                onStartNow = {
                    state.cancelCountdown()
                    state.startJourney()
                },
                onAddMore = {
                    state.cancelCountdown()
                    launchSpeech()
                },
            )
        }

        state.askHere -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            UseHereCard(
                locating = state.locating,
                seconds = state.originCountSec,
                onUseHere = state::useHere,
                onRetry = {
                    state.cancelAskHere()
                    launchSpeech()
                },
            )
        }

        state.query.isNotBlank() -> WizardSearchState(state, roleLabel)
        else -> WizardSuggestionList(state, recentStops, favorites)
    }
}

/** 검색 중·검색 전·검색 결과 세 상태를 그린다. */
@Composable
private fun WizardSearchState(state: JourneyWizardState, roleLabel: String) {
    val c = LocalTrubbiColors.current
    when {
        state.searching -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(vertical = 18.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PulsingDot()
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.wizard_searching, state.query.trim()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.secondaryLabel,
                )
            }
        }

        state.candidates == null -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            PillButton(
                text = stringResource(R.string.wizard_place_search),
                icon = AppIcons.Search,
                onClick = state::runSearch,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        else -> WizardResultList(state, roleLabel)
    }
}

/** 검색 결과 목록. 마지막 항목은 입력한 이름을 그대로 쓰는 선택지다. */
@Composable
private fun WizardResultList(state: JourneyWizardState, roleLabel: String) {
    val c = LocalTrubbiColors.current
    // candidates 를 지역 변수로 고정한다. pick() 이후 null 이 돼도 LazyList 가 옛 람다를 재호출한다.
    val list = state.candidates.orEmpty()
    val typed = state.query.trim()
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "resHeader") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Search, contentDescription = null, tint = c.secondaryLabel, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(7.dp))
                Text(
                    if (list.isEmpty()) {
                        stringResource(R.string.wizard_no_results_for, typed)
                    } else {
                        stringResource(R.string.wizard_results_count, list.size)
                    },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                OrderTag(roleLabel)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (list.isEmpty()) {
                    stringResource(R.string.wizard_use_typed_hint)
                } else {
                    stringResource(R.string.wizard_tap_to_confirm, roleLabel)
                },
                style = MaterialTheme.typography.bodySmall,
                color = c.secondaryLabel,
            )
            Spacer(Modifier.height(8.dp))
        }
        itemsIndexed(list.take(8)) { i, cand ->
            CandidateRow(
                rank = i + 1,
                candidate = cand,
                onPick = { state.requestConfirm(PickedPlace(cand.name, cand.address, cand.latitude, cand.longitude)) },
                onMap = null,
            )
        }
        item(key = "freeText") {
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .pressable { state.pick(PickedPlace(typed)) }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.wizard_use_typed, typed),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** 검색 전에 보여 주는 추천 — 현재 위치(출발지 단계), 즐겨찾기, 최근 정차지. */
@Composable
private fun WizardSuggestionList(
    state: JourneyWizardState,
    recentStops: List<JourneyStopEntity>,
    favorites: List<com.naury.trubbi.data.local.FavoritePlaceEntity>,
) {
    val c = LocalTrubbiColors.current
    val recents = recentStops.filter { !it.name.isNullOrBlank() }
    LazyColumn(Modifier.fillMaxSize()) {
        if (state.step == WizardStep.ORIGIN) {
            item(key = "here") {
                RecentPlaceRow(
                    icon = AppIcons.MyLocation,
                    title = stringResource(if (state.locating) R.string.wizard_locating else R.string.wizard_start_from_here),
                    subtitle = stringResource(R.string.wizard_start_from_here_sub),
                    accent = true,
                    onClick = state::useHere,
                )
                Spacer(Modifier.height(6.dp))
            }
        }
        if (favorites.isNotEmpty()) {
            item(key = "favHeader") { SuggestionHeader(stringResource(R.string.home_favorites), c.secondaryLabel) }
            items(favorites, key = { "fav-${it.id}" }) { f ->
                RecentPlaceRow(
                    icon = AppIcons.Star,
                    title = f.name,
                    subtitle = f.address,
                    onClick = { state.requestConfirm(PickedPlace(f.name, f.address, f.latitude, f.longitude)) },
                )
            }
            item(key = "favTail") { Spacer(Modifier.height(10.dp)) }
        }
        if (recents.isNotEmpty()) {
            item(key = "recentHeader") { SuggestionHeader(stringResource(R.string.wizard_recent_stops), c.secondaryLabel) }
            items(recents, key = { it.stopId }) { stop ->
                val name = stop.name ?: return@items
                RecentPlaceRow(
                    icon = AppIcons.Restore,
                    title = name,
                    subtitle = stop.address,
                    onClick = { state.requestConfirm(PickedPlace(name, stop.address, stop.latitude, stop.longitude)) },
                )
            }
            item(key = "recentTail") { Spacer(Modifier.height(16.dp)) }
        }
    }
}

/** 추천 목록의 구역 제목. */
@Composable
private fun SuggestionHeader(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = color,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

/** 하단 고정 액션. 스크롤과 분리해 어느 단계에서도 같은 자리에 있다. */
@Composable
private fun WizardBottomBar(state: JourneyWizardState) {
    val c = LocalTrubbiColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 10.dp, bottom = 18.dp)
            .navigationBarsPadding(),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.separator.copy(alpha = 0.5f)))
        Spacer(Modifier.height(14.dp))
        when (state.step) {
            WizardStep.DEST -> LaterButton(stringResource(R.string.wizard_later_dest), state::startWithoutStops)
            WizardStep.ORIGIN -> LaterButton(stringResource(R.string.wizard_later_origin), state::skipToExtra)
            WizardStep.EXTRA -> PillButton(
                text = stringResource(if (state.addMode) R.string.wizard_add_done else R.string.wizard_start_journey_bang),
                icon = if (state.addMode) AppIcons.Check else AppIcons.Truck,
                onClick = state::startJourney,
                modifier = Modifier.fillMaxWidth().height(60.dp),
            )
        }
    }
}

/** 고른 후보의 위치를 지도에서 확인하는 단계. */
@Composable
private fun WizardConfirmMap(
    state: JourneyWizardState,
    place: PickedPlace,
    roleLabel: String,
    favorites: List<com.naury.trubbi.data.local.FavoritePlaceEntity>,
    onToggleFavorite: (name: String, address: String?, lat: Double?, lng: Double?) -> Unit,
) {
    PlaceConfirmMap(
        place = place,
        roleLabel = roleLabel,
        favorited = favorites.isFavorite(place),
        onToggleFavorite = { onToggleFavorite(place.name, place.address, place.latitude, place.longitude) },
        onBack = { state.confirmTarget = null },
        onConfirm = {
            state.confirmTarget = null
            state.pick(place)
        },
        modifier = Modifier.fillMaxSize(),
    )
}

/** 이름과 좌표가 모두 근사 일치하면 같은 장소로 본다. 좌표 없이 이름만 있는 장소도 비교된다. */
private fun List<com.naury.trubbi.data.local.FavoritePlaceEntity>.isFavorite(place: PickedPlace): Boolean {
    fun close(a: Double?, b: Double?) =
        (a == null && b == null) || (a != null && b != null && kotlin.math.abs(a - b) < COORD_EPSILON)
    return any { it.name == place.name && close(it.latitude, place.latitude) && close(it.longitude, place.longitude) }
}

private const val PROMPT_SETTLE_MS = 250L
private const val MIC_OPEN_DELAY_MS = 400L
private const val MAX_SPOKEN_RESULTS = 5
private const val COORD_EPSILON = 0.0001

// ── 화면 조각들

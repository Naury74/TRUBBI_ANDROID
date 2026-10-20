package com.naury.trubbi.navigation

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.naury.trubbi.feature.ads.LocalAdsAllowed
import com.naury.trubbi.feature.ads.LocalAdsController
import com.naury.trubbi.feature.barcode.BarcodeScreen
import com.naury.trubbi.feature.camera.CameraScreen
import com.naury.trubbi.feature.contact.ContactsScreen
import com.naury.trubbi.feature.detail.DetailScreen
import com.naury.trubbi.feature.editor.EditKind
import com.naury.trubbi.feature.editor.MediaEditRoute
import com.naury.trubbi.feature.home.HomeAction
import com.naury.trubbi.feature.home.HomeScreen
import com.naury.trubbi.feature.home.HomeSection
import com.naury.trubbi.feature.home.HomeViewModel
import com.naury.trubbi.feature.journey.JourneyDetailScreen
import com.naury.trubbi.feature.journey.JourneyHistoryScreen
import com.naury.trubbi.feature.journey.JourneyMapScreen
import com.naury.trubbi.feature.memo.MemoScreen
import com.naury.trubbi.feature.settings.PurposeSettingsScreen
import com.naury.trubbi.feature.settings.SettingsScreen
import com.naury.trubbi.ui.designsystem.LocalNavAnimatedScope
import com.naury.trubbi.ui.designsystem.LocalSharedTransitionScope
import com.naury.trubbi.ui.theme.LocalTrubbiColors

@OptIn(ExperimentalSharedTransitionApi::class)
/**
 * 앱의 모든 화면 경로를 잇는 NavHost.
 *
 * 화면 사이 공유 요소 전환(앨범 썸네일 → 상세 이미지)을 쓰려고 전체를 `SharedTransitionLayout`
 * 으로 감쌌다. 전환은 좌우 슬라이드로 통일해 어느 화면에서 왔는지가 움직임으로 드러난다.
 *
 * @param navController 내비게이션 컨트롤러
 * @param contentPadding 시스템 바를 피하기 위한 여백
 * @param startDestination 첫 화면 경로
 */
@Composable
fun TrubbiNavHost(
    navController: NavHostController,
    contentPadding: PaddingValues,
    startDestination: String = Routes.HOME,
) {
    val dur = 320
    // 홈 탭과 기록 탭은 같은 뷰모델을 나눠 쓴다(홈에서 고른 조건이 기록 탭에 그대로 걸린다). 탭 바도 같은 것을 본다.
    val activity = LocalActivity.current as ComponentActivity
    val homeVm: HomeViewModel = hiltViewModel(activity)
    val backStack by navController.currentBackStackEntryAsState()
    val currentTab = MainTab.of(backStack?.destination?.route)
    val selectedRecords by homeVm.selection.records.collectAsStateWithLifecycle()
    val selectedMedia by homeVm.selection.media.collectAsStateWithLifecycle()
    val activeJourney by homeVm.journey.activeJourney.collectAsStateWithLifecycle()
    val homeOverlay by homeVm.fullScreenOverlay.collectAsStateWithLifecycle()
    fun homeDo(action: HomeAction) {
        homeVm.pendingAction.value = action
        if (currentTab != MainTab.HOME) navController.navigateTab(Routes.HOME)
    }

    // 홈·기록 탭은 날씨 배경이라 상태 표시줄 글자를 흰색으로, 나머지 탭은 테마 밝기를 따른다.
    // 하위 화면(상세·카메라 등)은 각자 정하므로 탭에 있을 때만 건드린다.
    val view = LocalView.current
    val darkUi = LocalTrubbiColors.current.isDark
    LaunchedEffect(currentTab, darkUi) {
        val tab = currentTab ?: return@LaunchedEffect
        val window = (view.context as? android.app.Activity)?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
            if (tab == MainTab.HOME || tab == MainTab.RECORDS) false else !darkUi
    }

    // 광고는 동의·SDK 준비가 끝났고 여정 중(운전 중)이 아닐 때만 띄운다.
    val adsReadyFlow = LocalAdsController.current?.ready ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }
    val adsReady by adsReadyFlow.collectAsStateWithLifecycle()
    val adsAllowed = adsReady && activeJourney == null

    CompositionLocalProvider(LocalAdsAllowed provides adsAllowed) {
        Box(Modifier.fillMaxSize()) {
            SharedTransitionLayout {
                val sharedScope = this
                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    enterTransition = { slideInHorizontally(tween(dur)) { it / 4 } + fadeIn(tween(dur)) },
                    exitTransition = { slideOutHorizontally(tween(dur)) { -it / 8 } + fadeOut(tween(dur)) },
                    popEnterTransition = { slideInHorizontally(tween(dur)) { -it / 8 } + fadeIn(tween(dur)) },
                    popExitTransition = { slideOutHorizontally(tween(dur)) { it / 4 } + fadeOut(tween(dur)) },
                ) {
                    for (section in HomeSection.entries) {
                        composable(if (section == HomeSection.TODAY) Routes.HOME else Routes.RECORDS) {
                            HomeScreen(
                                section = section,
                                onOpenRecords = { navController.navigateTab(Routes.RECORDS) },
                                onCamera = { navController.navigate(Routes.camera()) },
                                onOpenSettings = { navController.navigateTab(Routes.SETTINGS) },
                                onRecordClick = { id -> navController.navigate(Routes.detail(id)) },
                                onOpenJourney = { id -> navController.navigate(Routes.journey(id)) },
                                onOpenJourneyHistory = { navController.navigateTab(Routes.JOURNEYS) },
                                onOpenAccount = { navController.navigate(Routes.ACCOUNT) },
                                contentPadding = contentPadding,
                                viewModel = homeVm,
                            )
                        }
                    }

                    composable(Routes.JOURNEYS) {
                        JourneyHistoryScreen(
                            onBack = null,
                            onOpenJourney = { id -> navController.navigate(Routes.journey(id)) },
                            onStartJourney = if (activeJourney == null) ({ homeDo(HomeAction.PLAN_ROUTE) }) else null,
                            bottomPadding = TAB_BAR_ROOM,
                        )
                    }

                    composable(
                        route = Routes.JOURNEY,
                        arguments = listOf(navArgument("journeyId") { type = NavType.StringType }),
                    ) { entry ->
                        val jid = entry.arguments?.getString("journeyId").orEmpty()
                        JourneyDetailScreen(
                            onBack = { navController.popBackStack() },
                            onOpenRecord = { id -> navController.navigate(Routes.detail(id)) },
                            onCapture = { navController.navigate(Routes.camera()) },
                            onOpenMap = { navController.navigate(Routes.journeyMap(jid)) },
                        )
                    }

                    composable(
                        route = Routes.JOURNEY_MAP,
                        arguments = listOf(navArgument("journeyId") { type = NavType.StringType }),
                    ) {
                        JourneyMapScreen(
                            onBack = { navController.popBackStack() },
                            onOpenRecord = { id -> navController.navigate(Routes.detail(id)) },
                        )
                    }

                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            onOpenContacts = { navController.navigate(Routes.CONTACTS) },
                            onOpenPurposes = { navController.navigate(Routes.PURPOSES) },
                            onOpenAccount = { navController.navigate(Routes.ACCOUNT) },
                            onBack = null,
                            contentPadding = contentPadding,
                        )
                    }

                    composable(Routes.ACCOUNT) {
                        com.naury.trubbi.feature.account.AccountScreen(onBack = { navController.popBackStack() })
                    }

                    composable(
                        route = Routes.MEMO,
                        arguments = listOf(
                            navArgument("recordId") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                        ),
                    ) {
                        MemoScreen(onBack = { navController.popBackStack() }, contentPadding = contentPadding)
                    }

                    composable(Routes.CONTACTS) {
                        ContactsScreen(onBack = { navController.popBackStack() }, contentPadding = contentPadding)
                    }

                    composable(Routes.PURPOSES) {
                        PurposeSettingsScreen(onBack = { navController.popBackStack() }, contentPadding = contentPadding)
                    }

                    composable(
                        route = Routes.CAMERA,
                        arguments = listOf(
                            navArgument("purpose") {
                                type = NavType.StringType
                                defaultValue = "ETC"
                            },
                            navArgument("mode") {
                                type = NavType.StringType
                                defaultValue = "photo"
                            },
                        ),
                    ) { backStackEntry ->
                        val purpose = backStackEntry.arguments?.getString("purpose") ?: "ETC"
                        val mode = backStackEntry.arguments?.getString("mode") ?: "photo"
                        CameraScreen(
                            purposeName = purpose,
                            startInDocumentMode = mode == "document",
                            onClose = { navController.popBackStack() },
                            onSaved = { id, mediaUri, edit ->
                                val target = if (edit) Routes.editor(id, mediaUri) else Routes.detail(id)
                                navController.navigate(target) {
                                    popUpTo(Routes.CAMERA) { inclusive = true }
                                }
                            },
                        )
                    }

                    composable(
                        route = Routes.BARCODE,
                        arguments = listOf(
                            navArgument("purpose") {
                                type = NavType.StringType
                                defaultValue = "WAYBILL"
                            },
                        ),
                    ) { backStackEntry ->
                        val purpose = backStackEntry.arguments?.getString("purpose") ?: "WAYBILL"
                        BarcodeScreen(
                            purposeName = purpose,
                            onClose = { navController.popBackStack() },
                            onSaved = { id ->
                                navController.navigate(Routes.detail(id)) {
                                    popUpTo(Routes.BARCODE) { inclusive = true }
                                }
                            },
                        )
                    }

                    composable(
                        route = Routes.DETAIL,
                        arguments = listOf(navArgument("recordId") { type = NavType.StringType }),
                        // 확대 효과는 공유 요소가 만든다. 화면 자체는 은은한 페이드로 둬야 둘이 안 싸운다.
                        enterTransition = { fadeIn(tween(220)) },
                        exitTransition = { fadeOut(tween(160)) },
                        popEnterTransition = { fadeIn(tween(160)) },
                        popExitTransition = { fadeOut(tween(220)) },
                    ) {
                        CompositionLocalProvider(
                            LocalSharedTransitionScope provides sharedScope,
                            LocalNavAnimatedScope provides this,
                        ) {
                            DetailScreen(
                                onBack = { navController.popBackStack() },
                                onEditImage = { recordId, uri -> navController.navigate(Routes.editor(recordId, uri)) },
                                onEditVideo = { recordId, uri -> navController.navigate(Routes.videoEditor(recordId, uri)) },
                                onEditMemo = { recordId -> navController.navigate(Routes.memo(recordId)) },
                            )
                        }
                    }

                    composable(
                        route = Routes.EDITOR,
                        arguments = listOf(
                            navArgument("recordId") { type = NavType.StringType },
                            navArgument("mediaUri") { type = NavType.StringType },
                        ),
                    ) {
                        MediaEditRoute(EditKind.PHOTO, onClose = { navController.popBackStack() })
                    }

                    composable(
                        route = Routes.VIDEO_EDITOR,
                        arguments = listOf(
                            navArgument("recordId") { type = NavType.StringType },
                            navArgument("mediaUri") { type = NavType.StringType },
                        ),
                    ) {
                        MediaEditRoute(EditKind.VIDEO, onClose = { navController.popBackStack() })
                    }
                }
            }
            // 최상위 탭에서만 탭 바를 띄운다. 여러 개를 고르는 중에는 그 자리에 선택 도구가 뜬다.
            if (currentTab != null && selectedRecords.isEmpty() && selectedMedia.isEmpty() && !homeOverlay) {
                MainTabBar(
                    current = currentTab,
                    journeyActive = activeJourney != null,
                    onSelect = { tab -> navController.navigateTab(tab.route) },
                    onCapture = { navController.navigate(Routes.camera()) },
                    onQuick = { action ->
                        when (action) {
                            QuickAction.PLAN_ROUTE -> homeDo(HomeAction.PLAN_ROUTE)
                            QuickAction.IMPORT_PHOTO -> homeDo(HomeAction.IMPORT_PHOTO)
                            QuickAction.DOCUMENT -> navController.navigate(Routes.camera(mode = "document"))
                            QuickAction.BARCODE -> navController.navigate(Routes.barcode())
                            QuickAction.MEMO -> navController.navigate(Routes.memo())
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

/**
 * 최상위 화면으로 이동한다. 같은 화면을 여러 번 쌓지 않고 이전 상태를 되살린다.
 *
 * 그냥 `navigate` 만 쓰면 홈 → 앨범 → 홈 을 오갈 때마다 백스택이 계속 자라고,
 * 뒤로가기를 여러 번 눌러야 앱이 닫힌다.
 *
 * @param route 이동할 최상위 경로
 */
fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** 하단 탭 바(촬영 버튼 포함)에 목록 끝이 가리지 않도록 더 두는 여백. */
internal val TAB_BAR_ROOM = 170.dp

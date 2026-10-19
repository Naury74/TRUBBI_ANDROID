package com.naury.trubbi.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.naury.trubbi.feature.barcode.BarcodeScreen
import com.naury.trubbi.feature.calendar.CalendarScreen
import com.naury.trubbi.feature.camera.CameraScreen
import com.naury.trubbi.feature.contact.ContactsScreen
import com.naury.trubbi.feature.detail.DetailScreen
import com.naury.trubbi.feature.editor.EditKind
import com.naury.trubbi.feature.editor.MediaEditRoute
import com.naury.trubbi.feature.home.HomeScreen
import com.naury.trubbi.feature.journey.JourneyDetailScreen
import com.naury.trubbi.feature.journey.JourneyHistoryScreen
import com.naury.trubbi.feature.journey.JourneyMapScreen
import com.naury.trubbi.feature.journey.PlaceSearchScreen
import com.naury.trubbi.feature.memo.MemoScreen
import com.naury.trubbi.feature.search.SearchScreen
import com.naury.trubbi.feature.settings.PurposeSettingsScreen
import com.naury.trubbi.feature.settings.SettingsScreen
import com.naury.trubbi.feature.viewer.ImageViewerScreen
import com.naury.trubbi.ui.designsystem.LocalNavAnimatedScope
import com.naury.trubbi.ui.designsystem.LocalSharedTransitionScope

@OptIn(ExperimentalSharedTransitionApi::class)
/**
 * 앱의 모든 화면 경로를 잇는 NavHost.
 *
 * 화면 사이 공유 요소 전환(앨범 썸네일 → 상세 이미지)을 쓰려고 전체를 `SharedTransitionLayout`
 * 으로 감쌌다. 전환은 좌우 슬라이드로 통일해 어느 화면에서 왔는지가 움직임으로 드러난다.
 *
 * @param navController 내비게이션 컨트롤러
 * @param expanded 넓은 화면인지. 각 화면이 2단 배치를 고를 때 넘겨준다
 * @param contentPadding 시스템 바를 피하기 위한 여백
 * @param startDestination 첫 화면 경로
 */
@Composable
fun TrubbiNavHost(
    navController: NavHostController,
    expanded: Boolean,
    contentPadding: PaddingValues,
    startDestination: String = Routes.HOME,
) {
    val dur = 320
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
            composable(Routes.HOME) {
                HomeScreen(
                    onCamera = { navController.navigate(Routes.camera()) },
                    onBarcode = { navController.navigate(Routes.barcode()) },
                    onMemo = { navController.navigate(Routes.memo()) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onRecordClick = { id -> navController.navigate(Routes.detail(id)) },
                    onOpenJourney = { id -> navController.navigate(Routes.journey(id)) },
                    onOpenJourneyHistory = { navController.navigate(Routes.JOURNEYS) },
                    onOpenPlaceSearch = { navController.navigate(Routes.PLACE_SEARCH) },
                    onOpenAccount = { navController.navigate(Routes.ACCOUNT) },
                    contentPadding = contentPadding,
                )
            }

            composable(Routes.JOURNEYS) {
                JourneyHistoryScreen(
                    onBack = { navController.popBackStack() },
                    onOpenJourney = { id -> navController.navigate(Routes.journey(id)) },
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

            composable(Routes.PLACE_SEARCH) {
                PlaceSearchScreen(
                    onBack = { navController.popBackStack() },
                    onJourneyStarted = { id -> navController.navigate(Routes.journey(id)) { popUpTo(Routes.PLACE_SEARCH) { inclusive = true } } },
                )
            }

            composable(Routes.CALENDAR) {
                CalendarScreen(
                    expanded = expanded,
                    onRecordClick = { id -> navController.navigate(Routes.detail(id)) },
                    contentPadding = contentPadding,
                )
            }

            composable(Routes.SEARCH) {
                SearchScreen(
                    onRecordClick = { id -> navController.navigate(Routes.detail(id)) },
                    contentPadding = contentPadding,
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenContacts = { navController.navigate(Routes.CONTACTS) },
                    onOpenPurposes = { navController.navigate(Routes.PURPOSES) },
                    onOpenAccount = { navController.navigate(Routes.ACCOUNT) },
                    onBack = { navController.popBackStack() },
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

            composable(
                route = Routes.VIEWER,
                arguments = listOf(navArgument("recordId") { type = NavType.StringType }),
            ) {
                ImageViewerScreen(onClose = { navController.popBackStack() })
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

package com.naury.trubbi

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.core.util.Consumer
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.naury.trubbi.data.settings.ThemeMode
import com.naury.trubbi.domain.model.Purpose
import com.naury.trubbi.navigation.Routes
import com.naury.trubbi.navigation.TrubbiNavHost
import com.naury.trubbi.navigation.navigateTab
import com.naury.trubbi.ui.designsystem.LocalAppToast
import com.naury.trubbi.ui.designsystem.TrubbiToastHost
import com.naury.trubbi.ui.designsystem.rememberTrubbiToastState
import com.naury.trubbi.ui.theme.LocalTrubbiColors
import com.naury.trubbi.ui.theme.TrubbiTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 앱의 단 하나뿐인 액티비티.
 *
 * 화면 전환은 전부 Compose Navigation 이 맡고, 액티비티는 시스템 바 설정과 테마 결정,
 * 그리고 밖에서 들어오는 인텐트(위젯 딥링크·다른 앱의 공유)를 받아 넘기는 일만 한다.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 시스템 바를 완전히 투명하게 — 내용이 내비게이션 바 아래까지 이어져 보이도록.
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        setContent {
            val appViewModel: AppViewModel = hiltViewModel()
            val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()
            val ambientWeather by appViewModel.ambientWeather.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            // 테마를 '시스템'으로 두고 시스템이 라이트일 때만 자동 전환한다.
            // 사용자가 직접 고른 설정은 건드리지 않는다.
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val night = hour < 7 || hour >= 19
            val badWeather = ambientWeather == com.naury.trubbi.core.weather.WeatherCondition.RAIN ||
                ambientWeather == com.naury.trubbi.core.weather.WeatherCondition.SNOW ||
                ambientWeather == com.naury.trubbi.core.weather.WeatherCondition.THUNDER ||
                ambientWeather == com.naury.trubbi.core.weather.WeatherCondition.FOG
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> systemDark || night || badWeather
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            TrubbiTheme(darkTheme = darkTheme) {
                val windowSizeClass = calculateWindowSizeClass(this)
                val widthClass = windowSizeClass.widthSizeClass
                val expanded = widthClass == WindowWidthSizeClass.Expanded

                TrubbiAppRoot(expanded = expanded, initialIntent = intent)
            }
        }
    }
}

/**
 * 앱 최상단 컴포저블. 관문(환영 → 로그인 → 인증 → 약관 → 사용 안내)을 순서대로 통과시키고,
 * 다 지나면 본 화면을 띄운다.
 *
 * @param expanded 폴더블 펼침·태블릿처럼 넓은 화면인지. 하위 화면이 2단 배치를 고를 때 쓴다
 * @param initialIntent 앱을 띄운 인텐트. 딥링크와 공유 텍스트를 여기서 꺼낸다
 */
@Composable
private fun TrubbiAppRoot(expanded: Boolean, initialIntent: Intent?) {
    val navController = rememberNavController()

    // 뒤로가기로 즉시 종료되지 않게 — 스택 맨 아래(더 돌아갈 곳 없음)에서만 '한 번 더' 확인.
    val backCtx = androidx.compose.ui.platform.LocalContext.current
    val backRes = androidx.compose.ui.platform.LocalResources.current
    val appToast = rememberTrubbiToastState()
    var lastBackAt by remember { mutableStateOf(0L) }
    androidx.activity.compose.BackHandler(enabled = navController.previousBackStackEntry == null) {
        val now = System.currentTimeMillis()
        if (now - lastBackAt in 1..2000) {
            (backCtx as? android.app.Activity)?.finish()
        } else {
            lastBackAt = now
            appToast.show(backRes.getString(R.string.press_back_again))
        }
    }

    // 위젯·알림 탭으로 들어오는 딥링크. 앱이 꺼져 있을 때와 떠 있을 때 둘 다 받는다.
    var pendingDeepLink by remember { mutableStateOf(initialIntent?.data) }
    // 다른 앱에서 공유해 온 텍스트(지도 앱 → 정차지). 마찬가지로 두 경로 모두.
    var pendingShare by remember {
        mutableStateOf(initialIntent?.takeIf { it.action == Intent.ACTION_SEND }?.getStringExtra(Intent.EXTRA_TEXT))
    }
    val activity = androidx.activity.compose.LocalActivity.current as? ComponentActivity
    DisposableEffect(activity) {
        val listener = Consumer<Intent> { intent ->
            if (intent.action == Intent.ACTION_SEND) {
                pendingShare = intent.getStringExtra(Intent.EXTRA_TEXT)
            } else {
                pendingDeepLink = intent.data
            }
        }
        activity?.addOnNewIntentListener(listener)
        onDispose { activity?.removeOnNewIntentListener(listener) }
    }
    val deepLinkViewModel: AppViewModel = hiltViewModel()
    // 빠른 실행 플로팅이 켜져 있고 오버레이 권한이 있으면 앱 시작 시 서비스 복구(재부팅/강제종료 후).
    val floatingOn by deepLinkViewModel.floatingLauncher.collectAsStateWithLifecycle()
    val appCtx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(floatingOn) {
        if (floatingOn && android.provider.Settings.canDrawOverlays(appCtx)) {
            com.naury.trubbi.widget.FloatingLauncherService.start(appCtx)
        }
    }
    // 빠른 기록 알림 복구.
    val quickNotifOn by deepLinkViewModel.quickNotification.collectAsStateWithLifecycle()
    LaunchedEffect(quickNotifOn) {
        if (quickNotifOn) com.naury.trubbi.widget.QuickNotifier.show(appCtx)
    }
    LaunchedEffect(pendingDeepLink) {
        val uri = pendingDeepLink ?: return@LaunchedEffect
        if (uri.scheme != "trubbi") return@LaunchedEffect
        pendingDeepLink = null
        when (uri.host) {
            // 여정 알림 본문 탭 → 진행 중 여정 상세로.
            "journey" -> deepLinkViewModel.activeJourneyId()?.let { navController.navigate(Routes.journey(it)) }
            else -> {
                // 알림의 촬영 액션(journey=1)은 저장 시 여정 자동 귀속되도록 arm.
                if (uri.host == "capture" && uri.getQueryParameter("journey") == "1") {
                    deepLinkViewModel.armJourneyCapture()
                }
                deepLinkRoute(uri)?.let { navController.navigate(it) }
            }
        }
    }

    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1300)
        showSplash = false
    }

    // 첫 실행 관문: 로그인(한 번) → 사용 안내. 통과한 뒤에는 오프라인에서도 그대로 열린다.
    val appViewModel: AppViewModel = hiltViewModel()

    // 앱이 보이는 동안엔 앱 내 오버레이가, 가려지면 플로팅이 음성/UI를 담당하도록 신호.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> appViewModel.setAppForeground(true)
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> appViewModel.setAppForeground(false)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            appViewModel.setAppForeground(false)
        }
    }
    val profile by appViewModel.profile.collectAsStateWithLifecycle()
    val authGate by appViewModel.authGate.collectAsStateWithLifecycle()
    val welcomeSeen by appViewModel.welcomeSeen.collectAsStateWithLifecycle()
    val termsAgreed by appViewModel.termsAgreed.collectAsStateWithLifecycle()
    val needsOnboarding = profile?.let { !it.onboarded } ?: false

    // 공유 텍스트를 여정 쪽에 넘기고 홈으로 보낸다 — 물어보는 시트가 홈에 있다.
    LaunchedEffect(pendingShare) {
        pendingShare?.let { text ->
            pendingShare = null
            appViewModel.offerSharedText(text)
            navController.navigateTab(Routes.HOME)
        }
    }

    CompositionLocalProvider(LocalAppToast provides appToast) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            if (authGate == com.naury.trubbi.AuthGate.SIGNED_OUT && welcomeSeen == false) {
                com.naury.trubbi.ui.designsystem.AuthHeroBackground {
                    com.naury.trubbi.feature.onboarding.WelcomeScreen(onStart = { appViewModel.markWelcomeSeen() })
                }
            } else if (authGate == com.naury.trubbi.AuthGate.SIGNED_OUT) {
                // 로그인 관문. 세션이 기기에 캐시되므로 한 번 지나면 오프라인에서도 통과한다.
                com.naury.trubbi.ui.designsystem.AuthHeroBackground {
                    com.naury.trubbi.feature.account.LoginGateScreen()
                }
            } else if (authGate == com.naury.trubbi.AuthGate.NEEDS_VERIFICATION) {
                // 이메일 가입은 메일로 온 링크를 눌러야 통과한다.
                com.naury.trubbi.ui.designsystem.AuthHeroBackground {
                    com.naury.trubbi.feature.account.VerifyEmailScreen()
                }
            } else if (termsAgreed == false) {
                // 로그인했지만 약관 동의 기록이 없으면(소셜/기기변경 등) 동의를 받는다.
                com.naury.trubbi.ui.designsystem.AuthHeroBackground {
                    com.naury.trubbi.feature.account.TermsGateScreen()
                }
            } else if (needsOnboarding) {
                com.naury.trubbi.feature.onboarding.OnboardingScreen(onDone = {})
            } else {
                com.naury.trubbi.ui.designsystem.TrubbiBackground {
                    // 메인 화면 하나에 하위 화면을 쌓는 구조. 탭 바 대신 화면 안의 하단 독으로 이동한다.
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    ) { innerPadding ->
                        TrubbiNavHost(
                            navController = navController,
                            expanded = expanded,
                            contentPadding = innerPadding,
                        )
                    }
                }
            }
            SplashOverlay(visible = showSplash || profile == null)
            TrubbiToastHost(appToast, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** 첫 프레임을 덮는 스플래시. 관문 판단이 끝나기 전에 화면이 깜빡이는 걸 가린다. */
@Composable
private fun SplashOverlay(visible: Boolean) {
    val dark = LocalTrubbiColors.current.isDark
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(tween(420))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(if (dark) R.drawable.trubbi_splash_dark else R.drawable.trubbi_splash),
                contentDescription = "TRUBBI",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(0.72f),
            )
        }
    }
}

/**
 * 위젯·알림 딥링크 uri 를 내비게이션 경로로 바꾼다.
 *
 * @param uri 인텐트에 실려 온 uri
 * @return 이동할 경로. 알아볼 수 없으면 null
 */
private fun deepLinkRoute(uri: Uri?): String? {
    if (uri == null || uri.scheme != "trubbi") return null
    val purpose = Purpose.fromDeepLink(uri.getQueryParameter("purpose")).name
    return when (uri.host) {
        "capture" -> Routes.camera(purpose, if (uri.getQueryParameter("mode") == "document") "document" else "photo")
        "scan" -> Routes.barcode(purpose)
        "memo" -> Routes.memo()
        "record" -> uri.getQueryParameter("recordId")?.let { Routes.detail(it) }
        else -> null
    }
}

package com.naury.trubbi.feature.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naury.trubbi.R
import com.naury.trubbi.ui.common.ProfileAvatar
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.PillButton
import com.naury.trubbi.ui.designsystem.pressable
import com.naury.trubbi.ui.designsystem.sentenceLines
import com.naury.trubbi.ui.theme.LocalTrubbiColors
import kotlinx.coroutines.launch

/**
 * 첫 실행 안내 — 이름·사진을 받고 필요한 권한을 한 번에 설명한다.
 *
 * 권한을 쓸 때마다 하나씩 묻지 않고 여기서 왜 필요한지 먼저 보여 준다. 위치나 알림 권한은
 * 이유를 모르면 대개 거부되고, 한 번 거부되면 다시 묻기가 까다롭다.
 */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val c = LocalTrubbiColors.current
    var name by remember { mutableStateOf(viewModel.initialName) }
    val photoUri by viewModel.photoUri.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val pageCount = 5
    val pager = rememberPagerState(pageCount = { pageCount })

    // 여정 추적·알림 권한은 용도 설명과 함께 여기서 한 번에 요청한다.
    // 카메라·마이크 권한은 해당 기능을 실행하는 시점에 요청한다.
    val context = androidx.compose.ui.platform.LocalContext.current
    fun hasPerm(p: String) = androidx.core.content.ContextCompat.checkSelfPermission(context, p) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
    fun coreGranted(): Boolean {
        val loc = hasPerm(android.Manifest.permission.ACCESS_FINE_LOCATION) || hasPerm(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        val notif = android.os.Build.VERSION.SDK_INT < 33 || hasPerm(android.Manifest.permission.POST_NOTIFICATIONS)
        return loc && notif
    }
    // 필수 권한 — 허용해야 다음으로 진행.
    var permGranted by remember { mutableStateOf(coreGranted()) }
    var permRequested by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permGranted = coreGranted()
        if (permGranted) scope.launch { pager.animateScrollToPage(pageCount - 1) } // 허용되면 다음(이름) 단계로
    }
    fun requestCorePermissions() {
        permRequested = true
        val perms = buildList {
            // Android 12+ 는 정밀 위치를 대략적 위치와 함께 물어야 한다. 정밀만 물으면 요청이 무시될 수 있다.
            add(android.Manifest.permission.ACCESS_FINE_LOCATION)
            add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
            if (android.os.Build.VERSION.SDK_INT >= 33) add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        runCatching { permLauncher.launch(perms.toTypedArray()) }
    }
    fun openAppSettings() {
        runCatching {
            context.startActivity(
                android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:${context.packageName}"),
                ),
            )
        }
    }
    // 설정에서 권한 변경 후 돌아오면 상태 갱신.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) permGranted = coreGranted()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    fun handlePermission() {
        when {
            coreGranted() -> {
                permGranted = true
                scope.launch { pager.animateScrollToPage(pageCount - 1) }
            }
            !permRequested -> requestCorePermissions()
            else -> openAppSettings() // 이미 요청했는데 거부됨(영구 거부 가능) → 설정으로
        }
    }
    // 뒤로가기는 이전 소개 페이지로. 첫 페이지에서는 가로채지 않는다.
    androidx.activity.compose.BackHandler(enabled = pager.currentPage > 0) {
        scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) viewModel.pickPhoto(uri) }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().imePadding(),
    ) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f),
            // 권한 페이지(3)에서 미허용이면 스와이프로 넘어가지 못하게(버튼으로만).
            userScrollEnabled = permGranted || pager.currentPage != 3,
        ) { page ->
            when (page) {
                0 -> WelcomePage()
                1 -> IntroPage(
                    icon = AppIcons.Truck,
                    title = stringResource(R.string.onboarding_page1_title),
                    body = stringResource(R.string.onboarding_page1_body),
                )
                2 -> IntroPage(
                    icon = AppIcons.Description,
                    title = stringResource(R.string.onboarding_page2_title),
                    body = stringResource(R.string.onboarding_page2_body),
                )
                3 -> PermissionsPage()
                else -> NamePage(
                    name = name,
                    onName = { name = it },
                    photoUri = photoUri,
                    onPickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                )
            }
        }

        // 인디케이터 + 다음/시작 버튼
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(pageCount) { i ->
                    val active = i == pager.currentPage
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(width = if (active) 20.dp else 8.dp, height = 8.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(if (active) MaterialTheme.colorScheme.primary else c.separator),
                    )
                }
            }
            when {
                // 권한 페이지(3): 필수 — 허용해야 다음으로.
                pager.currentPage == 3 -> {
                    PillButton(
                        if (permGranted) {
                            stringResource(R.string.action_next_step)
                        } else if (permRequested) {
                            stringResource(R.string.onboarding_open_settings)
                        } else {
                            stringResource(R.string.onboarding_grant_and_continue)
                        },
                        {
                            if (permGranted) scope.launch { pager.animateScrollToPage(pageCount - 1) } else handlePermission()
                        },
                        Modifier.fillMaxWidth(),
                    )
                    if (!permGranted) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.onboarding_permission_note),
                            style = MaterialTheme.typography.labelMedium,
                            color = c.secondaryLabel,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                pager.currentPage < pageCount - 1 -> {
                    PillButton(stringResource(R.string.action_next_step), {
                        scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.action_skip),
                        style = MaterialTheme.typography.labelLarge,
                        color = c.secondaryLabel,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            // 권한은 건너뛸 수 없게 — 인트로만 건너뛰고 권한 페이지로.
                            .pressable { scope.launch { pager.animateScrollToPage(3) } }.padding(8.dp),
                    )
                }
                else -> PillButton(stringResource(R.string.welcome_start), { viewModel.complete(name) { onDone() } }, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    val c = LocalTrubbiColors.current
    val pop = remember { Animatable(0.7f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 320f)) }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        com.naury.trubbi.ui.designsystem.TrubbiLogo(
            modifier = Modifier.fillMaxWidth(0.56f).graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            },
        )
        Spacer(Modifier.height(26.dp))
        Text(
            "환영합니다! 🎉",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding_welcome_body),
            style = MaterialTheme.typography.bodyMedium,
            color = c.secondaryLabel,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PermissionsPage() {
    val c = LocalTrubbiColors.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.onboarding_permission_title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            stringResource(R.string.onboarding_permission_sub),
            style = MaterialTheme.typography.bodyMedium,
            color = c.secondaryLabel,
            modifier = Modifier.padding(top = 4.dp, bottom = 22.dp),
        )

        PermRow(AppIcons.LocationOn, stringResource(R.string.permission_location), stringResource(R.string.permission_location_sub))
        Spacer(Modifier.height(12.dp))
        PermRow(AppIcons.Info, stringResource(R.string.permission_notification), stringResource(R.string.permission_notification_sub))
        Spacer(Modifier.height(12.dp))
        PermRow(AppIcons.PhotoCamera, stringResource(R.string.permission_camera), stringResource(R.string.permission_camera_sub))
    }
}

@Composable
private fun PermRow(icon: ImageVector, title: String, body: String) {
    val c = LocalTrubbiColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
            Text(body, style = MaterialTheme.typography.bodySmall, color = c.secondaryLabel)
        }
    }
}

@Composable
private fun IntroPage(icon: ImageVector, title: String, body: String) {
    val c = LocalTrubbiColors.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(c.gradient)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(46.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(body.sentenceLines(), style = MaterialTheme.typography.bodyMedium, color = c.secondaryLabel, textAlign = TextAlign.Center)
    }
}

@Composable
private fun NamePage(name: String, onName: (String) -> Unit, photoUri: String?, onPickPhoto: () -> Unit) {
    val c = LocalTrubbiColors.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.onboarding_almost_done),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            stringResource(R.string.onboarding_set_profile),
            style = MaterialTheme.typography.bodyMedium,
            color = c.secondaryLabel,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
        )

        Box(contentAlignment = Alignment.BottomEnd) {
            ProfileAvatar(photoUri = photoUri, name = name, size = 104.dp, onClick = onPickPhoto)
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = onName,
            label = { Text(stringResource(R.string.field_name)) },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
        )
    }
}

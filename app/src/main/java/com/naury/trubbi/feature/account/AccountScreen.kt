package com.naury.trubbi.feature.account

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.naury.trubbi.R
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.GlassTopBar
import com.naury.trubbi.ui.designsystem.PillButton
import com.naury.trubbi.ui.designsystem.pressable
import com.naury.trubbi.ui.theme.LocalTrubbiColors
private enum class AuthMode { ENTRY, LOGIN, SIGNUP }

/**
 * 앱을 처음 켰을 때 지나야 하는 로그인 화면. 뒤로 가거나 건너뛸 수 없다.
 *
 * FirebaseAuth 가 세션을 기기에 캐시하므로, 최초 로그인 이후에는 오프라인에서도 통과한다.
 *
 * @param viewModel 인증 상태
 */
@Composable
fun LoginGateScreen(viewModel: AccountViewModel = hiltViewModel()) {
    // 키보드가 올라오면 로고 영역이 줄고 입력 폼은 아래에 붙어 있는다. 키보드와 내비게이션 바를
    // 함께 피해야 버튼이 가려지지 않는다.
    Column(
        Modifier.fillMaxSize().statusBarsPadding()
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
    ) {
        Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 28.dp), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.trubbi_logo_dark),
                contentDescription = "TRUBBI",
                modifier = Modifier.fillMaxWidth(0.56f),
            )
        }
        AuthPanel(viewModel)
        Spacer(Modifier.weight(0.35f))
    }
    AuthDialogHost(viewModel)
    AuthProgressHost(viewModel)
}

/**
 * 이메일 인증 대기 화면. 메일의 링크를 누를 때까지 여기 머문다.
 *
 * 링크 클릭은 앱으로 콜백되지 않으므로 수 초 간격으로 서버 상태를 폴링한다.
 *
 * @param viewModel 인증 상태
 */
@Composable
fun VerifyEmailScreen(viewModel: AccountViewModel = hiltViewModel()) {
    AuthDialogHost(viewModel)
    // 전체 진행 오버레이를 쓰지 않는다. 자동 확인이 계속 도는 화면이라 스피너가 걸린 것처럼 보인다.
    // 뒤로가기는 앱을 닫지 않고 로그아웃해 로그인 화면으로 돌린다.
    androidx.activity.compose.BackHandler { viewModel.signOut() }
    // 화면이 뜬 직후에는 currentUser 가 아직 안 왔을 수 있어, 가입할 때 쓴 이메일을 대신 보여 준다.
    val user by viewModel.user.collectAsState()
    val lastEmail by viewModel.lastSignupEmail.collectAsState()
    val email = user?.email ?: lastEmail

    // 주기적으로 인증 여부를 확인한다. 자동 확인이라 실패해도 알리지 않는다.
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(4000)
            viewModel.checkVerified(showMessage = false)
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(76.dp).clip(RoundedCornerShape(percent = 50)).background(Brush.linearGradient(LocalTrubbiColors.current.gradient)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Mail, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text(
            stringResource(R.string.account_verify_email_title),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = OnDark,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            maskEmail(email).ifBlank { stringResource(R.string.account_signup_email_label) },
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = OnDark,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.account_verify_sent_body),
            style = MaterialTheme.typography.bodyMedium,
            color = OnDarkDim,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        PillButton(stringResource(R.string.account_verified_continue), { viewModel.checkVerified() }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        PillButton(
            stringResource(R.string.account_resend_verification),
            { viewModel.resendVerification() },
            Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(R.string.account_use_other_account),
            style = MaterialTheme.typography.labelLarge,
            color = OnDarkDim,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).pressable { viewModel.signOut() }.padding(8.dp),
        )
    }
}

/**
 * 로그인 후 약관 동의 게이트 — 소셜 로그인/기기 변경 등으로 동의 기록이 없을 때 표시.
 * 이메일 가입자는 가입 단계에서 이미 동의해 통과한다.
 */
@Composable
fun TermsGateScreen(viewModel: AccountViewModel = hiltViewModel()) {
    AuthDialogHost(viewModel)
    var agreeTerms by remember { mutableStateOf(false) }
    var agreePrivacy by remember { mutableStateOf(false) }
    var agreeLocation by remember { mutableStateOf(false) }
    var agreeAge by remember { mutableStateOf(false) }
    var agreeMarketing by remember { mutableStateOf(false) }
    var showReqError by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<Pair<String, String>?>(null) }
    viewing?.let { TermsViewerDialog(it.first, it.second) { viewing = null } }
    val requiredOk = agreeTerms && agreePrivacy && agreeLocation && agreeAge

    Column(
        Modifier.fillMaxSize().statusBarsPadding()
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
    ) {
        Box(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Column {
                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(R.string.account_agree_title),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = OnDark,
                )
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.account_agree_sub), style = MaterialTheme.typography.bodyMedium, color = OnDarkDim)
                Spacer(Modifier.height(20.dp))
                TermsStep(
                    agreeTerms = agreeTerms, agreePrivacy = agreePrivacy, agreeLocation = agreeLocation,
                    agreeAge = agreeAge, agreeMarketing = agreeMarketing, highlightMissing = showReqError && !requiredOk,
                    onTerms = {
                        agreeTerms = it
                        showReqError = false
                    }, onPrivacy = {
                        agreePrivacy = it
                        showReqError = false
                    }, onLocation = {
                        agreeLocation =
                            it
                        showReqError = false
                    },
                    onAge = {
                        agreeAge = it
                        showReqError = false
                    }, onMarketing = { agreeMarketing = it },
                    onAll = { all ->
                        agreeTerms = all
                        agreePrivacy = all
                        agreeLocation = all
                        agreeAge = all
                        agreeMarketing = all
                        showReqError = false
                    },
                    onView = { title, body -> viewing = title to body },
                )
                Spacer(Modifier.height(18.dp))
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            PillButton(stringResource(R.string.account_agree_and_start), {
                if (requiredOk) viewModel.agreeTermsGate(agreeMarketing) else showReqError = true
            }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.account_use_other_account),
                style = MaterialTheme.typography.labelLarge,
                color = OnDarkDim,
                modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(8.dp)).pressable { viewModel.signOut() }.padding(8.dp),
            )
        }
    }
}

/**
 * 설정·독에서 들어오는 계정 화면. 로그인 상태면 프로필·백업, 아니면 로그인 폼.
 *
 * @param onBack 뒤로 갈 때
 * @param viewModel 인증 상태
 */
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val user by viewModel.user.collectAsState()
    val message by viewModel.message.collectAsState()
    LaunchedEffectMessage(message) { viewModel.clearMessage() }
    AuthDialogHost(viewModel)
    AuthProgressHost(viewModel)

    val signedIn = user
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        GlassTopBar(title = stringResource(if (signedIn == null) R.string.account_title_sign_in else R.string.account_title_signed_in), onBack = onBack)
        if (signedIn == null) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) { AuthPanel(viewModel) }
        } else {
            AccountInfo(signedIn, viewModel)
        }
    }
}

// ── 로그인 · 회원가입

@Composable
private fun AuthPanel(viewModel: AccountViewModel) {
    var mode by remember { mutableStateOf(AuthMode.ENTRY) }
    // 가입하려는 이메일에 이미 계정이 있으면 그 주소를 채운 채 로그인 쪽으로 옮겨 준다.
    val prefill by viewModel.pendingLoginEmail.collectAsState()
    var loginEmail by remember { mutableStateOf("") }
    LaunchedEffect(prefill) {
        prefill?.let {
            loginEmail = it
            mode = AuthMode.LOGIN
            viewModel.consumeLoginEmail()
        }
    }
    // 반대로 로그인하려는 이메일에 계정이 없으면 가입 쪽으로 옮겨 준다.
    val signupPrefill by viewModel.pendingSignupEmail.collectAsState()
    var signupEmail by remember { mutableStateOf("") }
    LaunchedEffect(signupPrefill) {
        signupPrefill?.let {
            signupEmail = it
            mode = AuthMode.SIGNUP
            viewModel.consumeSignupEmail()
        }
    }
    androidx.compose.animation.AnimatedContent(
        targetState = mode,
        transitionSpec = {
            // 로그인·가입으로 들어갈 때는 왼쪽으로 밀고, 처음으로 돌아올 때는 반대로.
            val forward = targetState != AuthMode.ENTRY
            (
                androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(320)) { if (forward) it else -it } +
                    androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220))
                ) togetherWith
                (
                    androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(320)) { if (forward) -it else it } +
                        androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(180))
                    )
        },
        label = "authMode",
    ) { m ->
        when (m) {
            AuthMode.ENTRY -> EntryContent(viewModel, onLogin = { mode = AuthMode.LOGIN }, onSignup = { mode = AuthMode.SIGNUP })
            AuthMode.LOGIN -> LoginContent(viewModel, onBack = {
                loginEmail = ""
                mode = AuthMode.ENTRY
            }, initialEmail = loginEmail)
            AuthMode.SIGNUP -> SignupContent(viewModel, onBack = {
                signupEmail = ""
                mode = AuthMode.ENTRY
            }, initialEmail = signupEmail)
        }
    }
}

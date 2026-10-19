package com.naury.trubbi.feature.account

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.naury.trubbi.R
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.PillButton
import com.naury.trubbi.ui.designsystem.pressable
import com.naury.trubbi.ui.theme.LocalTrubbiColors

/**
 * 로그인·가입 입력 화면과 그 안의 입력 필드·규칙 표시.
 */
/** Entry: choose TRUBBI(이메일) login or 간편 로그인. */
@Composable
internal fun EntryContent(viewModel: AccountViewModel, onLogin: () -> Unit, onSignup: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
        Text(stringResource(R.string.welcome_start), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = OnDark)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.account_start_sub), style = MaterialTheme.typography.bodyMedium, color = OnDarkDim)
        Spacer(Modifier.height(26.dp))

        PillButton(
            stringResource(R.string.account_sign_in_with_trubbi),
            onLogin,
            Modifier.fillMaxWidth(),
            leading = {
                Image(
                    painter = painterResource(R.drawable.trubbi_mascot),
                    contentDescription = null,
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onPrimary),
                    modifier = Modifier.size(22.dp),
                )
            },
        )

        SocialDivider()
        SocialRow(
            onGoogle = { viewModel.signInGoogle(context) {} },
            onKakao = {
                viewModel.dialog.value =
                    AuthDialog(resources.getString(R.string.account_kakao_soon_title), resources.getString(R.string.account_kakao_soon_body), isError = false)
            },
            onNaver = {
                viewModel.dialog.value =
                    AuthDialog(resources.getString(R.string.account_naver_soon_title), resources.getString(R.string.account_naver_soon_body), isError = false)
            },
        )

        Spacer(Modifier.height(24.dp))
        SwitchPrompt(stringResource(R.string.account_no_account_yet), stringResource(R.string.auth_sign_up), onSignup)
    }
}

@Composable
internal fun LoginContent(viewModel: AccountViewModel, onBack: () -> Unit, initialEmail: String = "") {
    val busy by viewModel.authBusy.collectAsState()
    var step by remember { mutableStateOf(if (initialEmail.isNotBlank()) 1 else 0) }
    var email by remember { mutableStateOf(initialEmail) }
    var pw by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    val fieldFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(step) {
        err = null
        runCatching { fieldFocus.requestFocus() }
    }
    // 뒤로가기는 이전 단계로만 이동한다. 첫 단계에서는 [onBack] 으로 위임한다.
    androidx.activity.compose.BackHandler { if (step > 0) step-- else onBack() }

    val loginAccount by viewModel.loginAccount.collectAsState()
    val errEmailRequired = stringResource(R.string.account_err_email_required)
    val errEmailFormat = stringResource(R.string.account_err_email_format)
    val errPasswordRequired = stringResource(R.string.account_err_password_required)

    fun goPw() {
        when {
            email.isBlank() -> err = errEmailRequired
            !emailValid(email) -> err = errEmailFormat
            // 계정이 없으면 회원가입을 권하고, 있으면 프로필을 받아 비밀번호 단계로 넘어간다.
            else -> {
                focusManager.clearFocus()
                viewModel.lookupForLogin(email) { step = 1 }
            }
        }
    }
    fun submit() {
        if (pw.isBlank()) {
            err = errPasswordRequired
            return
        }
        focusManager.clearFocus()
        viewModel.signInEmail(email, pw) {}
    }

    androidx.compose.animation.AnimatedContent(
        targetState = step,
        transitionSpec = {
            val fwd = targetState > initialState
            (
                androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(280)) { if (fwd) it else -it } +
                    androidx.compose.animation.fadeIn()
                ) togetherWith
                (
                    androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(280)) { if (fwd) -it else it } +
                        androidx.compose.animation.fadeOut()
                    )
        },
        label = "loginStep",
    ) { s ->
        if (s == 0) {
            StepScaffold(
                flowTitle = stringResource(
                    R.string.account_title_sign_in,
                ),
                stepText = "1 / 2", title = stringResource(R.string.account_err_email_required), subtitle = stringResource(R.string.account_step_email_sub),
                onBack = onBack, primaryText = stringResource(R.string.action_next_step), primaryBusy = false, onPrimary = ::goPw,
                field = {
                    AuthField(
                        stringResource(R.string.account_field_email), email, {
                            email = it
                            err = null
                        }, AppIcons.Mail, keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next, focusRequester = fieldFocus, error = err, onImeAction = ::goPw,
                    )
                },
            )
        } else {
            val greetName = loginAccount?.displayName?.takeIf { it.isNotBlank() }
            StepScaffold(
                flowTitle = stringResource(R.string.account_title_sign_in), stepText = "2 / 2",
                title = if (greetName !=
                    null
                ) {
                    stringResource(R.string.account_welcome_back, greetName)
                } else {
                    stringResource(R.string.account_err_password_required)
                },
                subtitle = if (greetName !=
                    null
                ) {
                    stringResource(R.string.account_step_password_sub_named)
                } else {
                    email.ifBlank { stringResource(R.string.account_step_password_sub) }
                },
                onBack = { step = 0 }, primaryText = stringResource(R.string.account_title_sign_in), primaryBusy = busy, onPrimary = ::submit,
                field = {
                    Column {
                        // 가입된 계정의 프로필(이름·사진)을 보여주며 인사.
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            com.naury.trubbi.ui.common.ProfileAvatar(
                                photoUri = loginAccount?.photoUrl,
                                name = greetName.orEmpty(),
                                size = 44.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    greetName ?: email,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = OnDark,
                                    maxLines = 1,
                                )
                                Text(email, style = MaterialTheme.typography.labelMedium, color = OnDarkDim, maxLines = 1)
                            }
                        }
                        AuthField(
                            stringResource(R.string.account_field_password), pw, {
                                pw = it
                                err = null
                            }, AppIcons.Lock, password = true,
                            imeAction = ImeAction.Done, focusRequester = fieldFocus, error = err, onImeAction = ::submit,
                        )
                    }
                },
                footer = {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.account_forgot_password),
                        style = MaterialTheme.typography.labelLarge,
                        color = OnDarkDim,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).pressable { viewModel.resetPassword(email) }.padding(6.dp),
                    )
                },
            )
        }
    }
}

@Composable
internal fun SignupContent(viewModel: AccountViewModel, onBack: () -> Unit, initialEmail: String = "") {
    val busy by viewModel.authBusy.collectAsState()
    var step by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(initialEmail) }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var err2 by remember { mutableStateOf<String?>(null) }
    // Consent state (한국법): 약관/개인정보/위치기반/만14세 필수, 마케팅 선택.
    var agreeTerms by remember { mutableStateOf(false) }
    var agreePrivacy by remember { mutableStateOf(false) }
    var agreeLocation by remember { mutableStateOf(false) }
    var agreeAge by remember { mutableStateOf(false) }
    var agreeMarketing by remember { mutableStateOf(false) }
    val requiredOk = agreeTerms && agreePrivacy && agreeLocation && agreeAge
    val fieldFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var showReqError by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<Pair<String, String>?>(null) }
    viewing?.let { TermsViewerDialog(it.first, it.second) { viewing = null } }
    // 약관(0)이 먼저, 그 다음 이름(1)·이메일(2)·비밀번호(3)·확인(4).
    LaunchedEffect(step) {
        err = null
        err2 = null
        if (step in 1..3) runCatching { fieldFocus.requestFocus() }
    }
    androidx.activity.compose.BackHandler { if (step > 0) step-- else onBack() }

    val errNameRequired = stringResource(R.string.account_err_name_required)
    val errEmailRequired = stringResource(R.string.account_err_email_required)
    val errEmailFormat = stringResource(R.string.account_err_email_format)
    val errPasswordRules = stringResource(R.string.account_err_password_rules)
    val errPasswordConfirm = stringResource(R.string.account_err_password_confirm)
    val errPasswordMismatch = stringResource(R.string.account_err_password_mismatch)

    fun next() {
        when (step) {
            0 -> if (!requiredOk) showReqError = true else step = 1
            1 -> if (name.isBlank()) err = errNameRequired else step = 2
            2 -> when {
                email.isBlank() -> err = errEmailRequired
                !emailValid(email) -> err = errEmailFormat
                else -> {
                    focusManager.clearFocus()
                    viewModel.checkEmailThenProceed(email) { step = 3 }
                }
            }

            3 -> if (!passwordOk(pw)) {
                err = errPasswordRules
            } else {
                focusManager.clearFocus()
                step = 4
            }
        }
    }
    fun submit() {
        when {
            !requiredOk -> {
                step = 0
                showReqError = true
            }
            pw2.isBlank() -> err2 = errPasswordConfirm
            pw != pw2 -> err2 = errPasswordMismatch
            else -> {
                focusManager.clearFocus()
                viewModel.signUpEmail(email, pw, name, agreeMarketing) {}
            }
        }
    }

    androidx.compose.animation.AnimatedContent(
        targetState = step,
        transitionSpec = {
            val fwd = targetState > initialState
            (
                androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(280)) { if (fwd) it else -it } +
                    androidx.compose.animation.fadeIn()
                ) togetherWith
                (
                    androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(280)) { if (fwd) -it else it } +
                        androidx.compose.animation.fadeOut()
                    )
        },
        label = "signupStep",
    ) { s ->
        when (s) {
            0 -> StepScaffold(
                flowTitle = stringResource(
                    R.string.auth_sign_up,
                ),
                stepText = "1 / 5", title = stringResource(R.string.account_agree_title), subtitle = stringResource(R.string.account_signup_agree_sub),
                onBack = onBack, primaryText = stringResource(R.string.account_agree_and_continue), primaryBusy = false, onPrimary = ::next,
                field = {
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
                },
            )
            1 -> StepScaffold(
                flowTitle = stringResource(
                    R.string.auth_sign_up,
                ),
                stepText = "2 / 5", title = stringResource(R.string.account_signup_name_title), subtitle = stringResource(R.string.account_signup_name_sub),
                onBack = { step = 0 }, primaryText = stringResource(R.string.action_next_step), primaryBusy = false, onPrimary = ::next,
                field = {
                    AuthField(stringResource(R.string.field_name), name, {
                        name = it
                        err = null
                    }, AppIcons.Person, imeAction = ImeAction.Next, focusRequester = fieldFocus, error = err, onImeAction = ::next)
                },
            )
            2 -> StepScaffold(
                flowTitle = stringResource(
                    R.string.auth_sign_up,
                ),
                stepText = "3 / 5",
                title = stringResource(
                    R.string.account_err_email_required,
                ),
                subtitle = stringResource(R.string.account_signup_email_sub),
                onBack = { step = 1 }, primaryText = stringResource(R.string.action_next_step), primaryBusy = false, onPrimary = ::next,
                field = {
                    AuthField(
                        stringResource(R.string.account_field_email), email, {
                            email = it
                            err = null
                        },
                        AppIcons.Mail,
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                        focusRequester = fieldFocus,
                        error = err,
                        onImeAction = ::next,
                    )
                },
            )
            3 -> StepScaffold(
                flowTitle = stringResource(
                    R.string.auth_sign_up,
                ),
                stepText = "4 / 5",
                title = stringResource(
                    R.string.account_signup_password_title,
                ),
                subtitle = stringResource(R.string.account_signup_password_sub),
                onBack = { step = 2 }, primaryText = stringResource(R.string.action_next_step), primaryBusy = false, onPrimary = ::next,
                field = {
                    Column {
                        AuthField(
                            stringResource(R.string.account_field_password), pw, {
                                pw = it
                                err = null
                            }, AppIcons.Lock, password = true,
                            imeAction = ImeAction.Done, focusRequester = fieldFocus, error = err, onImeAction = ::next,
                        )
                        PasswordRules(pw)
                    }
                },
            )
            else -> StepScaffold(
                flowTitle = stringResource(
                    R.string.auth_sign_up,
                ),
                stepText = "5 / 5",
                title = stringResource(
                    R.string.account_signup_confirm_title,
                ),
                subtitle = stringResource(R.string.account_signup_confirm_sub),
                onBack = { step = 3 }, primaryText = stringResource(R.string.account_do_sign_up), primaryBusy = busy,
                onPrimary = ::submit,
                field = {
                    Column {
                        AuthField(
                            stringResource(R.string.account_field_password_confirm), pw2, {
                                pw2 = it
                                err2 = null
                            }, AppIcons.Lock, password = true,
                            imeAction = ImeAction.Done, focusRequester = fieldFocus, error = err2, onImeAction = ::submit,
                        )
                        if (pw2.isNotEmpty() && err2 == null && pw == pw2) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                                Icon(AppIcons.Check, contentDescription = null, tint = LocalTrubbiColors.current.green, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.account_password_match), style = MaterialTheme.typography.labelMedium, color = OnDark)
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun AuthField(
    placeholder: String,
    value: String,
    onValue: (String) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    password: Boolean = false,
    focusRequester: FocusRequester? = null,
    error: String? = null,
    onImeAction: () -> Unit = {},
) {
    val c = LocalTrubbiColors.current
    var reveal by remember { mutableStateOf(false) }
    Column {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (c.isDark) 0.55f else 0.8f))
                .then(if (error != null) Modifier.border(1.5.dp, c.red, RoundedCornerShape(16.dp)) else Modifier)
                .padding(horizontal = 14.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = c.secondaryLabel, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = c.secondaryLabel)
                BasicTextField(
                    value = value, onValueChange = onValue, singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (password) KeyboardType.Password else keyboardType,
                        imeAction = imeAction,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { onImeAction() },
                        onDone = { onImeAction() },
                        onGo = { onImeAction() },
                    ),
                    visualTransformation = if (password && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = (focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier).fillMaxWidth(),
                )
            }
            // 비밀번호 보기/숨기기 토글.
            if (password) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    if (reveal) AppIcons.EyeOff else AppIcons.Eye,
                    contentDescription = stringResource(if (reveal) R.string.account_hide_password else R.string.account_show_password),
                    tint = c.secondaryLabel,
                    modifier = Modifier.size(20.dp).clip(RoundedCornerShape(percent = 50)).pressable { reveal = !reveal }.padding(1.dp),
                )
            }
        }
        if (error != null) {
            Spacer(Modifier.height(6.dp))
            Text(error, style = MaterialTheme.typography.labelMedium, color = c.red, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

/** Live password requirement checklist (8자/대문자/숫자/특수문자). */
@Composable
private fun PasswordRules(pw: String) {
    Column(Modifier.padding(top = 10.dp, start = 4.dp)) {
        RuleRow(stringResource(R.string.password_rule_length), pw.length >= 8)
        RuleRow(stringResource(R.string.password_rule_upper), pw.any { it.isUpperCase() })
        RuleRow(stringResource(R.string.password_rule_digit), pw.any { it.isDigit() })
        RuleRow(stringResource(R.string.password_rule_symbol), pw.any { !it.isLetterOrDigit() && !it.isWhitespace() })
    }
}

@Composable
private fun RuleRow(text: String, ok: Boolean) {
    val c = LocalTrubbiColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Icon(
            if (ok) AppIcons.Check else AppIcons.Close,
            contentDescription = null,
            tint = if (ok) c.green else OnDarkDim,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (ok) OnDark else OnDarkDim)
    }
}

/** 비밀번호 규칙 — 8자 이상, 대문자·숫자·특수문자 각각 하나 이상. */
internal fun passwordOk(pw: String): Boolean =
    pw.length >= 8 &&
        pw.any { it.isUpperCase() } &&
        pw.any { it.isDigit() } &&
        pw.any { !it.isLetterOrDigit() && !it.isWhitespace() }

/** 화면에 보여 줄 이메일을 가린다 — `te****@gmail.com`. 도메인은 남겨 어느 계정인지 알아볼 수 있게. */
internal fun maskEmail(email: String?): String {
    val e = email?.trim().orEmpty()
    val at = e.indexOf('@')
    if (at <= 0) return e
    val local = e.substring(0, at)
    val domain = e.substring(at)
    val shown = when {
        local.length <= 2 -> local.take(1)
        else -> local.take(2)
    }
    return shown + "*".repeat((local.length - shown.length).coerceIn(2, 6)) + domain
}

/** 로그인·가입 단계에서 쓰는 최소한의 이메일 형식 확인. */
private fun emailValid(s: String): Boolean =
    android.util.Patterns.EMAIL_ADDRESS.matcher(s.trim()).matches()

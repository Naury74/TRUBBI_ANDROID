package com.naury.trubbi.feature.account

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.naury.trubbi.R
import com.naury.trubbi.core.ui.StringProvider
import com.naury.trubbi.data.auth.AuthRepository
import com.naury.trubbi.data.backup.BackupScheduler
import com.naury.trubbi.data.backup.CloudBackupRepository
import com.naury.trubbi.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 인증 흐름에서 띄우는 안내 팝업.
 *
 * [dismissText] 와 [onConfirm] 이 함께 있으면 두 버튼, 아니면 확인 하나만 보여 준다.
 */
data class AuthDialog(
    val title: String,
    val message: String,
    val isError: Boolean = true,
    val confirmText: String? = null,
    val onConfirm: (() -> Unit)? = null,
    val dismissText: String? = null,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val cloudBackup: CloudBackupRepository,
    private val settings: SettingsRepository,
    private val scheduler: BackupScheduler,
    private val strings: StringProvider,
) : ViewModel() {

    val user = authRepo.currentUser
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val autoBackup = settings.autoBackup
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val backupWifiOnly = settings.backupWifiOnly
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setAutoBackup(on: Boolean) = viewModelScope.launch {
        settings.setAutoBackup(on)
        scheduler.apply(on, backupWifiOnly.value)
    }

    fun setBackupWifiOnly(on: Boolean) = viewModelScope.launch {
        settings.setBackupWifiOnly(on)
        if (autoBackup.value) scheduler.apply(true, on)
    }

    val cloudMeta = authRepo.currentUser
        .flatMapLatest { if (it == null) kotlinx.coroutines.flow.flowOf(null) else cloudBackup.observeMeta() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 토스트로 한 번 보여 줄 문구. 백업 결과처럼 놓쳐도 되는 알림. */
    val message = MutableStateFlow<String?>(null)
    fun clearMessage() {
        message.value = null
    }

    /** 팝업으로 보여 줄 결과·오류. 로그인 흐름은 놓치면 안 되므로 토스트가 아니라 팝업이다. */
    val dialog = MutableStateFlow<AuthDialog?>(null)
    fun dismissDialog() {
        dialog.value = null
    }

    /** 값이 있으면 이 이메일을 채운 채 로그인 폼으로 전환한다(이미 가입된 주소였을 때). */
    val pendingLoginEmail = MutableStateFlow<String?>(null)
    fun consumeLoginEmail() {
        pendingLoginEmail.value = null
    }

    /** 값이 있으면 이 이메일을 채운 채 가입 폼으로 전환한다(계정이 없는 주소였을 때). */
    val pendingSignupEmail = MutableStateFlow<String?>(null)
    fun consumeSignupEmail() {
        pendingSignupEmail.value = null
    }

    /** Looked-up account shown on the login password step (인사 + 프로필). */
    val loginAccount = MutableStateFlow<com.naury.trubbi.data.auth.AccountLookup?>(null)

    /** Login email step: confirm the account exists; if not → 회원가입 유도; if yes → 프로필 미리보기 후 진행. */
    fun lookupForLogin(email: String, onProceed: () -> Unit) = launchGuarded(authBusy) {
        progress.value = strings[R.string.auth_checking_account]
        val r = authRepo.lookupAccount(email).getOrNull()
        when {
            r == null -> {
                loginAccount.value = null
                onProceed()
            } // 확인 실패 → 그냥 비번 단계로
            !r.exists -> dialog.value = AuthDialog(
                strings[R.string.auth_no_account_title],
                strings[R.string.auth_no_account_body],
                confirmText = strings[R.string.auth_sign_up],
                onConfirm = { pendingSignupEmail.value = email.trim() },
                dismissText = strings[R.string.action_close],
            )
            else -> {
                loginAccount.value = r
                onProceed()
            }
        }
    }

    /** 진행 중 표시 겸 연타 방지. 네트워크가 느릴 때 가입 버튼이 두 번 눌리는 일이 있었다. */
    val authBusy = MutableStateFlow(false)
    val backupBusy = MutableStateFlow(false)

    /** Blocking progress label (strings[R.string.auth_signing_in] 등) — shows a full-screen overlay while set. */
    val progress = MutableStateFlow<String?>(null)

    val googleAvailable: Boolean get() = authRepo.googleWebClientId != null

    /** 마지막으로 가입에 쓴 이메일. 인증 화면이 currentUser 보다 먼저 뜰 때 대신 보여 준다. */
    val lastSignupEmail = MutableStateFlow<String?>(null)

    fun signUpEmail(email: String, pw: String, name: String, marketing: Boolean, onDone: () -> Unit) = launchGuarded(authBusy) {
        lastSignupEmail.value = email.trim()
        progress.value = strings[R.string.auth_signing_up]
        authRepo.signUpEmail(email, pw, name, marketing)
            .onSuccess {
                settings.setTermsAgreed(true)
                onDone()
            } // 가입 단계에서 이미 동의 → 게이트 통과
            .onFailure { showAuthError(it, email) }
    }

    /** 로그인 후 약관 동의 게이트: 로컬 플래그 설정 + 계정에 기록(best-effort). */
    fun agreeTermsGate(marketing: Boolean) = viewModelScope.launch {
        settings.setTermsAgreed(true)
        runCatching { authRepo.recordConsent(marketing) }
    }

    /**
     * 가입 이메일 단계 — 이미 가입된 주소인지 먼저 확인하고 넘어간다.
     *
     * @param email 확인할 이메일
     * @param onFree 쓸 수 있는 주소일 때 다음 단계로 넘기는 콜백
     */
    fun checkEmailThenProceed(email: String, onAvailable: () -> Unit) = launchGuarded(authBusy) {
        progress.value = strings[R.string.auth_checking_email]
        authRepo.emailRegistered(email)
            .onSuccess { taken ->
                if (taken) {
                    dialog.value = AuthDialog(
                        strings[R.string.auth_email_taken_title],
                        strings[R.string.auth_email_taken_body],
                        confirmText = strings[R.string.auth_go_sign_in],
                        onConfirm = { pendingLoginEmail.value = email.trim() },
                        dismissText = strings[R.string.action_close],
                    )
                } else {
                    onAvailable()
                }
            }
            .onFailure { onAvailable() } // can't verify (enumeration protection) → continue; submit catches it
    }

    fun signInEmail(email: String, pw: String, onDone: () -> Unit) = launchGuarded(authBusy) {
        progress.value = strings[R.string.auth_signing_in]
        authRepo.signInEmail(email, pw)
            .onSuccess { onDone() }
            .onFailure { showAuthError(it, email) }
    }

    /**
     * Firebase 오류 코드를 사용자가 읽을 수 있는 팝업으로 옮긴다.
     *
     * 원문 메시지는 영어인 데다 “credential is malformed” 같은 식이라 그대로 보여 줄 수 없다.
     * 다음에 뭘 하면 되는지가 함께 나와야 하므로 필요한 경우 버튼도 붙인다.
     */
    private fun showAuthError(t: Throwable, email: String) {
        val msg = t.localizedMessage.orEmpty()
        dialog.value = when {
            msg.contains("OPERATION_NOT_ALLOWED", true) || msg.contains("not allowed", true) -> AuthDialog(
                strings[R.string.auth_email_login_off_title],
                strings[R.string.auth_email_login_off_body],
            )
            msg.contains("EMAIL_EXISTS", true) || msg.contains("already in use", true) -> AuthDialog(
                strings[R.string.auth_email_taken_title],
                strings[R.string.auth_email_taken_body],
                confirmText = strings[R.string.auth_go_sign_in],
                onConfirm = { pendingLoginEmail.value = email.trim() },
                dismissText = strings[R.string.action_close],
            )
            msg.contains("INVALID_LOGIN", true) ||
                msg.contains("password is invalid", true) ||
                msg.contains("no user record", true) ||
                msg.contains("INVALID_CREDENTIAL", true) -> AuthDialog(
                strings[R.string.auth_cannot_sign_in_title],
                strings[R.string.auth_cannot_sign_in_body],
            )
            msg.contains("TOO_MANY", true) || msg.contains("too many", true) -> AuthDialog(
                strings[R.string.auth_retry_later_title],
                strings[R.string.auth_retry_later_body],
            )
            msg.contains("network", true) -> AuthDialog(
                strings[R.string.auth_network_title],
                strings[R.string.auth_network_body],
            )
            msg.contains("WEAK_PASSWORD", true) -> AuthDialog(
                strings[R.string.auth_weak_password_title],
                strings[R.string.auth_weak_password_body],
            )
            else -> AuthDialog(strings[R.string.auth_generic_error_title], msg.ifBlank { strings[R.string.auth_generic_error_body] })
        }
    }

    /** 인증을 기다리는 계정의 이메일. */
    val pendingEmail: String? get() = user.value?.email

    fun resendVerification() = viewModelScope.launch {
        authRepo.sendEmailVerification()
            .onSuccess { dialog.value = AuthDialog(strings[R.string.auth_verify_sent_title], strings[R.string.auth_verify_sent_body], isError = false) }
            .onFailure { showAuthError(it, "") }
    }

    /**
     * 서버에서 인증 여부를 다시 확인한다.
     *
     * @param notify 결과를 사용자에게 알릴지. 주기적 자동 확인에서는 false 를 넘긴다
     */
    fun checkVerified(showMessage: Boolean = true) = viewModelScope.launch {
        val verified = authRepo.reloadVerification()
        if (!verified && showMessage) {
            dialog.value = AuthDialog(
                strings[R.string.auth_not_verified_title],
                strings[R.string.auth_not_verified_body],
            )
        }
    }

    fun resetPassword(email: String) = viewModelScope.launch {
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            dialog.value = AuthDialog(strings[R.string.auth_check_email_title], strings[R.string.auth_check_email_body])
            return@launch
        }
        authRepo.sendPasswordReset(email)
            .onSuccess { dialog.value = AuthDialog(strings[R.string.auth_reset_sent_title], strings[R.string.auth_reset_sent_body], isError = false) }
            .onFailure { showAuthError(it, email) }
    }

    /**
     * Credential Manager 로 구글 로그인.
     *
     * @param context 계정 선택 시트를 띄우려면 액티비티 컨텍스트여야 한다
     */
    fun signInGoogle(context: Context, onDone: () -> Unit) {
        val webClientId = authRepo.googleWebClientId
        if (webClientId == null) {
            dialog.value = AuthDialog(strings[R.string.auth_google_not_ready_title], strings[R.string.auth_google_not_ready_body])
            return
        }
        launchGuarded(authBusy) {
            progress.value = strings[R.string.auth_google_signing_in]
            val idToken = runCatching {
                val option = GetGoogleIdOption.Builder()
                    .setServerClientId(webClientId)
                    .setFilterByAuthorizedAccounts(false)
                    .build()
                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                val response = CredentialManager.create(context).getCredential(context, request)
                GoogleIdTokenCredential.createFrom(response.credential.data).idToken
            }.getOrElse { return@launchGuarded } // user cancelled the picker — no error popup
            authRepo.signInWithGoogleIdToken(idToken).onSuccess { onDone() }
                .onFailure { showAuthError(it, "") }
        }
    }

    /** 이름(표시 이름)을 Firebase 계정에 저장. */
    fun updateName(name: String) = viewModelScope.launch {
        if (name.isBlank()) {
            dialog.value = AuthDialog(strings[R.string.auth_name_required_title], strings[R.string.auth_name_required_body])
            return@launch
        }
        authRepo.updateDisplayName(name)
            .onSuccess { dialog.value = AuthDialog(strings[R.string.auth_name_changed], name.trim(), isError = false) }
            .onFailure { showAuthError(it, "") }
    }

    /** 프로필 사진을 Firebase Storage에 올리고 계정에 동기화. */
    fun updatePhoto(uri: android.net.Uri) = launchGuarded(authBusy) {
        progress.value = strings[R.string.auth_uploading_photo]
        authRepo.updatePhoto(uri)
            .onFailure { showAuthError(it, "") }
    }

    fun signOut() = authRepo.signOut()

    fun deleteAccount(onDone: () -> Unit) = launchGuarded(authBusy) {
        authRepo.deleteAccount().onSuccess { onDone() }
            .onFailure { dialog.value = AuthDialog(strings[R.string.auth_cannot_delete_title], strings[R.string.auth_cannot_delete_body]) }
    }

    fun backupNow() {
        if (backupBusy.value) return
        backupBusy.value = true
        viewModelScope.launch {
            cloudBackup.backup()
                .onSuccess { message.value = strings[R.string.cloud_backup_done] }
                .onFailure { message.value = strings[R.string.backup_failed, it.localizedMessage.orEmpty()] }
            backupBusy.value = false
        }
    }

    fun restoreNow() {
        if (backupBusy.value) return
        backupBusy.value = true
        viewModelScope.launch {
            cloudBackup.restore()
                .onSuccess { message.value = strings[R.string.cloud_restore_done] }
                .onFailure { message.value = strings[R.string.restore_failed, it.localizedMessage.orEmpty()] }
            backupBusy.value = false
        }
    }

    /** 인증 동작을 진행 표시로 감싸 실행한다. 어떤 경로로 끝나든 표시는 반드시 내린다. */
    private fun launchGuarded(flag: MutableStateFlow<Boolean>, block: suspend () -> Unit) {
        if (flag.value) return
        flag.value = true
        viewModelScope.launch {
            try {
                block()
            } finally {
                flag.value = false
                progress.value = null
            }
        }
    }
}

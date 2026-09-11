package com.naury.trubbi.feature.onboarding

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naury.trubbi.R
import com.naury.trubbi.core.file.MediaStorage
import com.naury.trubbi.core.ui.StringProvider
import com.naury.trubbi.data.auth.AuthRepository
import com.naury.trubbi.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 첫 실행 안내 상태 — 이름·사진을 저장하고 안내를 끝난 것으로 표시한다.
 *
 * @param repo 프로필 저장
 * @param mediaStorage 프로필 사진을 앱 저장소로 들여온다
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val mediaStorage: MediaStorage,
    private val authRepo: AuthRepository,
    private val appScope: kotlinx.coroutines.CoroutineScope,
    private val strings: StringProvider,
) : ViewModel() {

    val photoUri = MutableStateFlow<String?>(null)

    /** 가입할 때 쓴 이름을 미리 채운다. 같은 사람인데 계정과 프로필 이름이 다르면 혼란스럽다. */
    val initialName: String = authRepo.currentUserNow()?.displayName.orEmpty()

    /** 고른 사진의 임시 uri. 안내를 끝낼 때 저장소로 들여온다. */
    private var pickedLocal: Uri? = null

    init {
        // 계정에 사진이 이미 있으면 그걸 먼저 보여 준다.
        authRepo.currentUserNow()?.photoUrl?.takeIf { it.isNotBlank() }?.let { photoUri.value = it }
    }

    fun pickPhoto(uri: Uri) {
        pickedLocal = uri
        viewModelScope.launch {
            photoUri.value = runCatching { mediaStorage.saveProfilePhoto(uri) }.getOrNull() ?: uri.toString()
        }
    }

    fun complete(name: String, onDone: () -> Unit) {
        val finalName = name.trim().ifBlank { initialName.ifBlank { strings[R.string.default_driver_name] } }
        // 1) 로컬 완료를 먼저 → 게이트가 즉시 넘어간다(네트워크에 막히지 않음).
        viewModelScope.launch {
            settingsRepo.completeOnboarding(finalName, photoUri.value)
            onDone()
        }
        // 2) Firebase 동기화는 화면이 닫혀도 끝까지 도는 앱 스코프에서 best-effort.
        val photo = pickedLocal
        appScope.launch {
            runCatching { authRepo.updateDisplayName(finalName) }
            photo?.let { runCatching { authRepo.updatePhoto(it) } }
        }
    }
}

package com.naury.trubbi.feature.settings

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naury.trubbi.R
import com.naury.trubbi.core.file.MediaStorage
import com.naury.trubbi.core.ui.StringProvider
import com.naury.trubbi.data.backup.BackupManager
import com.naury.trubbi.data.backup.BackupResult
import com.naury.trubbi.data.settings.PhotoQuality
import com.naury.trubbi.data.settings.SettingsRepository
import com.naury.trubbi.data.settings.ThemeMode
import com.naury.trubbi.data.settings.TrubbiSettings
import com.naury.trubbi.data.settings.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 백업·복원 진행 상황.
 *
 * @property message 사용자에게 보여 줄 문구
 * @property inProgress 아직 진행 중이면 true — 이때는 메시지를 지우지 않는다
 */
data class BackupStatus(val message: String, val inProgress: Boolean)

/**
 * 설정 화면 상태.
 *
 * @property repo 설정 저장소
 * @property mediaStorage 프로필 사진 저장
 * @property backupManager 로컬 파일 백업·복원
 * @property strings 진행·결과 문구용 리소스 접근자
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val mediaStorage: MediaStorage,
    private val backupManager: BackupManager,
    private val strings: StringProvider,
) : ViewModel() {

    val settings = repo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TrubbiSettings())

    val profile = repo.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfile())

    fun setUserName(name: String) = viewModelScope.launch { repo.setUserName(name) }
    fun changeProfilePhoto(uri: android.net.Uri) = viewModelScope.launch {
        val saved = runCatching { mediaStorage.saveProfilePhoto(uri) }.getOrNull() ?: return@launch
        repo.setProfilePhoto(saved)
    }

    val purposes = repo.purposes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val backupStatus = MutableStateFlow<BackupStatus?>(null)

    /** 공유 워터마크 기본 선택값 + 변경. */
    val watermarkDefaults = repo.watermarkDefaults
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.naury.trubbi.data.settings.WatermarkDefaults())
    fun setWatermarkDefaults(w: com.naury.trubbi.data.settings.WatermarkDefaults) =
        viewModelScope.launch { repo.setWatermarkDefaults(w) }

    fun addPurpose(label: String) = viewModelScope.launch { repo.addPurpose(label) }
    fun removePurpose(label: String) = viewModelScope.launch { repo.removePurpose(label) }
    fun resetPurposes() = viewModelScope.launch { repo.resetPurposes() }
    fun movePurpose(from: Int, to: Int) = viewModelScope.launch { repo.movePurpose(from, to) }

    /** 재배치된 전체 순서를 한 번에 저장(드래그 종료 시). */
    fun setPurposes(list: List<String>) = viewModelScope.launch { repo.setPurposes(list) }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { repo.setThemeMode(mode) }
    fun setDefaultPurpose(name: String) = viewModelScope.launch { repo.setDefaultPurpose(name) }
    fun setSaveLocation(value: Boolean) = viewModelScope.launch { repo.setSaveLocation(value) }
    fun setAutoSave(value: Boolean) = viewModelScope.launch { repo.setAutoSave(value) }
    fun setEditAfterCapture(value: Boolean) = viewModelScope.launch { repo.setEditAfterCapture(value) }
    fun setPhotoQuality(q: PhotoQuality) = viewModelScope.launch { repo.setPhotoQuality(q) }

    // ── 여정 설정
    val arrivalRadius = repo.arrivalRadiusM
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 300)
    val voiceGuide = repo.voiceGuide
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val cloudVoice = repo.cloudVoice
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val floatingControl = repo.floatingControl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val floatingLauncher = repo.floatingLauncher
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val quickNotification = repo.quickNotification
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun cycleArrivalRadius() = viewModelScope.launch {
        val options = listOf(100, 300, 500, 1000)
        val next = options[(options.indexOf(arrivalRadius.value) + 1).mod(options.size)]
        repo.setArrivalRadius(next)
    }

    fun setVoiceGuide(on: Boolean) = viewModelScope.launch { repo.setVoiceGuide(on) }
    fun setCloudVoice(on: Boolean) = viewModelScope.launch { repo.setCloudVoice(on) }
    fun setFloatingControl(on: Boolean) = viewModelScope.launch { repo.setFloatingControl(on) }
    fun setFloatingLauncher(on: Boolean) = viewModelScope.launch { repo.setFloatingLauncher(on) }
    fun setQuickNotification(on: Boolean) = viewModelScope.launch { repo.setQuickNotification(on) }

    fun exportBackup(uri: Uri) = viewModelScope.launch {
        backupStatus.value = BackupStatus(strings[R.string.backup_in_progress], inProgress = true)
        backupStatus.value =
            BackupStatus(describe(backupManager.export(uri), R.string.backup_failed), inProgress = false)
    }

    fun importBackup(uri: Uri) = viewModelScope.launch {
        backupStatus.value = BackupStatus(strings[R.string.restore_in_progress], inProgress = true)
        backupStatus.value =
            BackupStatus(describe(backupManager.import(uri), R.string.restore_failed), inProgress = false)
    }

    /**
     * 백업 결과를 사용자에게 보일 문구로 옮긴다.
     *
     * 저장 계층은 숫자와 원인만 돌려주므로 문구를 만드는 일은 화면 쪽인 여기서 한다.
     *
     * @param failureRes 실패 문구 서식. 내보내기와 되돌리기가 서로 다른 문장을 쓴다
     */
    private fun describe(result: BackupResult, @StringRes failureRes: Int): String = when (result) {
        is BackupResult.Exported -> strings[R.string.backup_done, result.records, result.media]
        is BackupResult.Imported -> strings[R.string.restore_done, result.records]
        is BackupResult.Failed -> strings[failureRes, result.cause.message.orEmpty()]
    }

    /** 여정 증빙과 묶여 있어 지울 수 없는 용도. 목록은 저장소가 단독으로 관리한다. */
    val protectedPurposes: Set<String> = repo.protectedPurposes()

    fun clearMessage() {
        backupStatus.value = null
    }
}

package com.naury.trubbi.feature.barcode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naury.trubbi.data.repo.RecordRepository
import com.naury.trubbi.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 바코드 화면 상태.
 *
 * @property repo 바코드 기록 저장
 * @param settingsRepo 용도 목록을 읽는 데만 쓴다
 */
@HiltViewModel
class BarcodeViewModel @Inject constructor(
    private val repo: RecordRepository,
    settingsRepo: SettingsRepository,
) : ViewModel() {

    val purposes = settingsRepo.purposes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 스캔한 값을 기록으로 저장한다.
     *
     * @param value 바코드에서 읽은 문자열
     * @param format 바코드 형식 이름(QR·CODE_128 …). 나중에 같은 형식으로 다시 그릴 때 쓴다
     * @param purposeLabel 용도 라벨
     * @param name 기록 제목. 비우면 값 자체를 제목으로 쓴다
     * @param onSaved 저장이 끝나면 만들어진 기록 id 로 불린다
     */
    fun save(value: String, format: String, purposeLabel: String, name: String, onSaved: (String) -> Unit) {
        viewModelScope.launch {
            val id = repo.createBarcodeRecord(
                value = value,
                format = format,
                media = null,
                purposeLabel = purposeLabel,
                title = name.ifBlank { value },
            )
            onSaved(id)
        }
    }
}

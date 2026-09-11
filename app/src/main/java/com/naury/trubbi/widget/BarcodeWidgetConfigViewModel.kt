package com.naury.trubbi.widget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naury.trubbi.data.local.RecordWithMedia
import com.naury.trubbi.data.repo.RecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

/**
 * 바코드 위젯 설정 화면 상태 — 고를 수 있는 바코드 기록 목록.
 *
 * @param repo 바코드 기록 조회
 */
@HiltViewModel
class BarcodeWidgetConfigViewModel @Inject constructor(
    repo: RecordRepository,
) : ViewModel() {
    val barcodes = repo.observeBarcodes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<RecordWithMedia>())
}

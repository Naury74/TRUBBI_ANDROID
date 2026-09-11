package com.naury.trubbi.feature.viewer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naury.trubbi.data.repo.RecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 이미지 뷰어 상태 — 기록에 붙은 사진 uri 들을 촬영 순서대로 흘린다.
 *
 * @param repo 기록 조회
 * @param savedStateHandle `recordId` 인자를 받는다
 */
@HiltViewModel
class ImageViewerViewModel @Inject constructor(
    repo: RecordRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val recordId: String? = savedStateHandle.get<String>("recordId")

    val uris = (if (recordId == null) flowOf(null) else repo.observeRecord(recordId))
        .map { rec -> rec?.media?.sortedBy { it.createdAt }?.map { it.uri }.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

package com.naury.trubbi.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naury.trubbi.R
import com.naury.trubbi.core.ui.StringProvider
import com.naury.trubbi.data.local.RecordWithMedia
import com.naury.trubbi.data.repo.RecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * 검색 화면 상태.
 *
 * 입력마다 조회하지 않고 250 ms 디바운스를 건다. 검색 쿼리가 OCR 텍스트까지 훑기 때문이다.
 *
 * @property repo 기록 검색
 * @param strings 예시 키워드 배열을 읽는 데만 쓴다
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repo: RecordRepository,
    strings: StringProvider,
) : ViewModel() {

    val query = MutableStateFlow("")

    /** 검색창이 비었을 때 띄우는 예시 키워드. */
    val suggestions: List<String> = strings.array(R.array.search_suggestions)

    val results = query
        .debounce(250)
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(emptyList()) else repo.search(q.trim())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<RecordWithMedia>())

    fun setQuery(q: String) {
        query.value = q
    }
}

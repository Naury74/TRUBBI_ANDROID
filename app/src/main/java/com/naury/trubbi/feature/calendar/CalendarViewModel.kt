package com.naury.trubbi.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naury.trubbi.data.local.RecordWithMedia
import com.naury.trubbi.data.repo.RecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 달력 화면 상태.
 *
 * 달의 날짜별 개수와 고른 날의 기록을 따로 흘린다. 개수는 배지만 그리면 되므로 기록 전체를
 * 읽지 않는 가벼운 조회를 쓴다 — 한 달치를 통째로 읽으면 사진 목록까지 딸려 온다.
 *
 * @property repo 기록 조회
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repo: RecordRepository,
) : ViewModel() {

    private val iso = DateTimeFormatter.ISO_LOCAL_DATE

    val month = MutableStateFlow(YearMonth.now())
    val selectedDate = MutableStateFlow(LocalDate.now())

    val counts = month.flatMapLatest { ym ->
        val start = ym.atDay(1).format(iso)
        val end = ym.atEndOfMonth().format(iso)
        repo.observeDateCounts(start, end)
    }.map { list -> list.associate { it.jobDate to it.count } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val dayRecords = selectedDate.flatMapLatest { date ->
        repo.observeByDate(date.format(iso))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<RecordWithMedia>())

    fun previousMonth() {
        month.value = month.value.minusMonths(1)
    }
    fun nextMonth() {
        month.value = month.value.plusMonths(1)
    }
    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }
}

package com.naury.trubbi.feature.calendar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naury.trubbi.R
import com.naury.trubbi.data.local.RecordWithMedia
import com.naury.trubbi.ui.common.RecordListRow
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.GlassCard
import com.naury.trubbi.ui.designsystem.GlassIconButton
import com.naury.trubbi.ui.designsystem.TrubbiLargeTitle
import com.naury.trubbi.ui.theme.LocalTrubbiColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 달력 화면 — 날짜를 눌러 그날 기록을 본다.
 *
 * @param expanded 넓은 화면인지. 달력과 목록을 좌우로 나눌지 결정한다
 * @param onRecordClick 기록 상세로 (기록 id)
 * @param contentPadding 시스템 바를 피하기 위한 여백
 * @param viewModel 달력 상태
 */
@Composable
fun CalendarScreen(
    expanded: Boolean,
    onRecordClick: (String) -> Unit,
    contentPadding: PaddingValues,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val month by viewModel.month.collectAsStateWithLifecycle()
    val selected by viewModel.selectedDate.collectAsStateWithLifecycle()
    val counts by viewModel.counts.collectAsStateWithLifecycle()
    val dayRecords by viewModel.dayRecords.collectAsStateWithLifecycle()

    val calendar: @Composable (Modifier) -> Unit = { mod ->
        Column(mod) {
            TrubbiLargeTitle(
                title = stringResource(R.string.calendar_title),
                modifier = Modifier.padding(top = contentPadding.calculateTopPadding()),
            )
            MonthCard(
                month = month,
                selected = selected,
                counts = counts,
                onPrev = viewModel::previousMonth,
                onNext = viewModel::nextMonth,
                onSelect = viewModel::selectDate,
            )
        }
    }

    val dayList: @Composable (Modifier) -> Unit = { mod ->
        DayRecords(
            date = selected,
            records = dayRecords,
            onRecordClick = onRecordClick,
            contentPadding = contentPadding,
            modifier = mod,
        )
    }

    if (expanded) {
        Row(Modifier.fillMaxSize()) {
            calendar(Modifier.weight(0.5f).padding(horizontal = 8.dp))
            dayList(Modifier.weight(0.5f))
        }
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            item { calendar(Modifier.fillMaxWidth()) }
            item {
                Text(
                    stringResource(R.string.calendar_day_records, selected.monthValue, selected.dayOfMonth),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp),
                )
            }
            if (dayRecords.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.calendar_day_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            } else {
                items(dayRecords, key = { it.record.recordId }) { record ->
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        RecordListRow(record, onClick = { onRecordClick(record.record.recordId) })
                    }
                }
                item { Spacer(Modifier.height(contentPadding.calculateBottomPadding() + 24.dp)) }
            }
        }
    }
}

@Composable
private fun MonthCard(
    month: YearMonth,
    selected: LocalDate,
    counts: Map<String, Int>,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    val iso = DateTimeFormatter.ISO_LOCAL_DATE
    val today = LocalDate.now()
    val monthPattern = stringResource(R.string.calendar_month_pattern)
    val monthFormatter = remember(monthPattern) { DateTimeFormatter.ofPattern(monthPattern, Locale.getDefault()) }
    GlassCard(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    month.format(monthFormatter),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                GlassIconButton(AppIcons.ChevronLeft, onPrev, stringResource(R.string.action_prev), size = 36.dp)
                Spacer(Modifier.size(8.dp))
                GlassIconButton(AppIcons.ChevronRight, onNext, stringResource(R.string.action_next), size = 36.dp)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                val days = weekdayNames()
                days.forEachIndexed { i, d ->
                    Text(
                        d,
                        style = MaterialTheme.typography.labelMedium,
                        color = when (i) {
                            0 -> LocalTrubbiColors.current.red
                            6 -> MaterialTheme.colorScheme.primary
                            else -> LocalTrubbiColors.current.secondaryLabel
                        },
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))

            // 격자 구성 — 1일이 무슨 요일인지에 맞춰 앞을 빈칸으로 채운다(일요일 = 0).
            val firstDay = month.atDay(1)
            val leadingBlanks = firstDay.dayOfWeek.value % 7 // Mon=1..Sun=7 -> Sun=0
            val daysInMonth = month.lengthOfMonth()
            val totalCells = leadingBlanks + daysInMonth
            val rows = (totalCells + 6) / 7

            for (row in 0 until rows) {
                Row(Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val cellIndex = row * 7 + col
                        val dayNum = cellIndex - leadingBlanks + 1
                        Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                            if (dayNum in 1..daysInMonth) {
                                val date = month.atDay(dayNum)
                                val count = counts[date.format(iso)] ?: 0
                                DayCell(
                                    day = dayNum,
                                    count = count,
                                    isSelected = date == selected,
                                    isToday = date == today,
                                    onClick = { onSelect(date) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    count: Int,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalTrubbiColors.current
    Column(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            day.toString(),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            ),
            color = when {
                isSelected -> Color.White
                isToday -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(5.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(
                    if (count > 0) {
                        if (isSelected) Color.White else c.orange
                    } else {
                        Color.Transparent
                    },
                ),
        )
    }
}

@Composable
private fun DayRecords(
    date: LocalDate,
    records: List<RecordWithMedia>,
    onRecordClick: (String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                stringResource(R.string.calendar_day_records, date.monthValue, date.dayOfMonth),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        if (records.isEmpty()) {
            item { Text(stringResource(R.string.calendar_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(records, key = { it.record.recordId }) { record ->
                RecordListRow(record, onClick = { onRecordClick(record.record.recordId) })
            }
        }
    }
}

/** 일요일부터 시작하는 요일 머리글. */
@Composable
private fun weekdayNames(): List<String> = listOf(
    stringResource(R.string.weekday_sun),
    stringResource(R.string.weekday_mon),
    stringResource(R.string.weekday_tue),
    stringResource(R.string.weekday_wed),
    stringResource(R.string.weekday_thu),
    stringResource(R.string.weekday_fri),
    stringResource(R.string.weekday_sat),
)

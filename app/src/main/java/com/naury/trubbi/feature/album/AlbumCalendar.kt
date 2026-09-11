package com.naury.trubbi.feature.album

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.naury.trubbi.R
import com.naury.trubbi.core.util.DateUtils
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.GlassIconButton
import com.naury.trubbi.ui.theme.LocalTrubbiColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * 기간을 고르는 달력. 하루만 눌러도 되고, 두 번 눌러 범위로 만들 수도 있다.
 */
/**
 * 앨범 제목 아래 붙는 달력. 주간 줄과 월간 격자를 오간다.
 *
 * @param expanded 월간으로 펼친 상태인지
 * @param weekAnchor 주간 줄이 보여 줄 기준 날짜
 * @param month 월간 격자가 보여 줄 달
 */
@Composable
internal fun AlbumCalendar(
    expanded: Boolean,
    weekAnchor: LocalDate,
    month: YearMonth,
    counts: Map<String, Int>,
    dateRange: Pair<String, String>?,
    onToggleExpand: () -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onShiftWeek: (Long) -> Unit,
    onClearRange: () -> Unit,
    onPickDay: (String) -> Unit,
) {
    val c = LocalTrubbiColors.current
    val iso = DateTimeFormatter.ISO_LOCAL_DATE
    // 드래그 하나로 두 동작을 처리한다. 가로는 이전·다음, 세로는 접기·펼치기.
    val drag = Modifier.pointerInput(expanded) {
        var dx = 0f
        var dy = 0f
        detectDragGestures(
            onDragStart = {
                dx = 0f
                dy = 0f
            },
            onDragEnd = {
                if (kotlin.math.abs(dy) > kotlin.math.abs(dx)) {
                    if (dy > 36f && !expanded) {
                        onToggleExpand()
                    } else if (dy < -36f && expanded) {
                        onToggleExpand()
                    }
                } else {
                    if (dx > 40f) {
                        if (expanded) onPrevMonth() else onShiftWeek(-1)
                    } else if (dx < -40f) {
                        if (expanded) onNextMonth() else onShiftWeek(1)
                    }
                }
            },
        ) { change, d ->
            dx += d.x
            dy += d.y
            change.consume()
        }
    }
    val monthPattern = stringResource(R.string.calendar_month_pattern)
    val monthFormatter = remember(monthPattern) {
        java.time.format.DateTimeFormatter.ofPattern(monthPattern, java.util.Locale.getDefault())
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .animateContentSize(),
    ) {
        if (expanded) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)) {
                Text(
                    month.format(monthFormatter),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                if (dateRange != null) {
                    Text(
                        stringResource(R.string.calendar_reset),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onClearRange() }.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                GlassIconButton(AppIcons.ChevronLeft, onPrevMonth, stringResource(R.string.action_prev), size = 34.dp)
                Spacer(Modifier.width(12.dp))
                GlassIconButton(AppIcons.ChevronRight, onNextMonth, stringResource(R.string.action_next), size = 34.dp)
                Spacer(Modifier.width(2.dp))
            }
            WeekdayHeader(c)
            AnimatedContent(
                targetState = month,
                modifier = drag,
                transitionSpec = {
                    val fwd = targetState.isAfter(initialState)
                    (slideInHorizontally { if (fwd) it else -it } + fadeIn()) togetherWith
                        (slideOutHorizontally { if (fwd) -it else it } + fadeOut())
                },
                label = "month",
            ) { m ->
                val firstDay = m.atDay(1)
                val leadingBlanks = firstDay.dayOfWeek.value % 7
                val daysInMonth = m.lengthOfMonth()
                val rows = (leadingBlanks + daysInMonth + 6) / 7
                Column(Modifier.fillMaxWidth()) {
                    for (row in 0 until rows) {
                        Row(Modifier.fillMaxWidth()) {
                            for (col in 0 until 7) {
                                val dayNum = row * 7 + col - leadingBlanks + 1
                                if (dayNum in 1..daysInMonth) {
                                    DayCell(m.atDay(dayNum), counts, dateRange, iso, c, Modifier.weight(1f), onPickDay)
                                } else {
                                    Spacer(Modifier.weight(1f).aspectRatio(1f))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            WeekdayHeader(c)
            AnimatedContent(
                targetState = weekAnchor.minusDays((weekAnchor.dayOfWeek.value % 7).toLong()),
                modifier = drag,
                transitionSpec = {
                    val fwd = targetState.isAfter(initialState)
                    (slideInHorizontally { if (fwd) it else -it } + fadeIn()) togetherWith
                        (slideOutHorizontally { if (fwd) -it else it } + fadeOut())
                },
                label = "week",
            ) { startOfWeek ->
                Row(Modifier.fillMaxWidth()) {
                    for (i in 0..6) {
                        DayCell(startOfWeek.plusDays(i.toLong()), counts, dateRange, iso, c, Modifier.weight(1f), onPickDay)
                    }
                }
            }
        }

        // 손잡이. 탭과 세로 스와이프 모두 접기·펼치기로 동작한다.
        Box(
            Modifier
                .fillMaxWidth()
                .height(26.dp)
                .clickable { onToggleExpand() },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(c.separator),
            )
        }
    }
}

@Composable
private fun WeekdayHeader(c: com.naury.trubbi.ui.theme.TrubbiColors) {
    Row(Modifier.fillMaxWidth()) {
        albumWeekdayNames().forEachIndexed { i, d ->
            Text(
                d,
                style = MaterialTheme.typography.labelSmall,
                color = when (i) {
                    0 -> c.red
                    6 -> MaterialTheme.colorScheme.primary
                    else -> c.secondaryLabel
                },
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    counts: Map<String, Int>,
    dateRange: Pair<String, String>?,
    iso: DateTimeFormatter,
    c: com.naury.trubbi.ui.theme.TrubbiColors,
    modifier: Modifier,
    onPick: (String) -> Unit,
) {
    val today = LocalDate.now()
    val primary = MaterialTheme.colorScheme.primary
    val key = date.format(iso)
    val count = counts[key] ?: 0
    val endpoint = dateRange != null && (key == dateRange.first || key == dateRange.second)
    val inBand = dateRange != null && key >= dateRange.first && key <= dateRange.second && !endpoint
    Box(
        modifier
            .aspectRatio(1f)
            .background(if (inBand) primary.copy(alpha = 0.14f) else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(if (endpoint) primary else Color.Transparent)
                .clickable { onPick(key) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal,
                ),
                color = when {
                    endpoint -> MaterialTheme.colorScheme.onPrimary
                    date == today -> primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            Box(
                Modifier
                    .padding(top = 2.dp)
                    .size(5.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (count > 0) (if (endpoint) MaterialTheme.colorScheme.onPrimary else c.orange) else Color.Transparent),
            )
        }
    }
}

internal fun rangeLabel(range: Pair<String, String>): String =
    if (range.first == range.second) {
        DateUtils.formatDate(range.first)
    } else {
        "${DateUtils.formatDate(range.first)} ~ ${DateUtils.formatDate(range.second)}"
    }

/** 일요일부터 시작하는 요일 머리글. */
@Composable
private fun albumWeekdayNames(): List<String> = listOf(
    stringResource(R.string.weekday_sun),
    stringResource(R.string.weekday_mon),
    stringResource(R.string.weekday_tue),
    stringResource(R.string.weekday_wed),
    stringResource(R.string.weekday_thu),
    stringResource(R.string.weekday_fri),
    stringResource(R.string.weekday_sat),
)

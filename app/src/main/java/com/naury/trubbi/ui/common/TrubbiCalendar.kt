package com.naury.trubbi.ui.common

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
import androidx.compose.runtime.remember
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
import com.naury.trubbi.ui.theme.TrubbiColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * 앨범과 홈이 함께 쓰는 달력.
 *
 * 주 ⇄ 월 보기를 접었다 펼 수 있고, 가로로 밀면 이전/다음, 세로로 밀면 펼치기다.
 * 날짜에는 기록 개수 점이 붙고, 두 날짜를 눌러 기간을 고를 수 있다.
 *
 * @param counts ISO 날짜별 기록 수 — 점 표시에 쓴다
 * @param dateRange 선택된 기간 (시작 ISO, 끝 ISO). 없으면 null
 */
@Composable
fun TrubbiCalendar(
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
    val colors = LocalTrubbiColors.current
    val iso = DateTimeFormatter.ISO_LOCAL_DATE
    val monthPattern = stringResource(R.string.calendar_month_pattern)
    val monthFormatter = remember(monthPattern) {
        DateTimeFormatter.ofPattern(monthPattern, Locale.getDefault())
    }
    // 드래그 한 번으로 두 가지를 판단한다 — 가로는 이전/다음, 세로는 접기/펼치기.
    val drag = Modifier.pointerInput(expanded) {
        var dx = 0f
        var dy = 0f
        detectDragGestures(
            onDragStart = {
                dx = 0f
                dy = 0f
            },
            onDragEnd = {
                if (abs(dy) > abs(dx)) {
                    if (dy > VERTICAL_SWIPE_PX && !expanded) {
                        onToggleExpand()
                    } else if (dy < -VERTICAL_SWIPE_PX && expanded) {
                        onToggleExpand()
                    }
                } else {
                    if (dx > HORIZONTAL_SWIPE_PX) {
                        if (expanded) onPrevMonth() else onShiftWeek(-1)
                    } else if (dx < -HORIZONTAL_SWIPE_PX) {
                        if (expanded) onNextMonth() else onShiftWeek(1)
                    }
                }
            },
        ) { change, drag ->
            dx += drag.x
            dy += drag.y
            change.consume()
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .animateContentSize(),
    ) {
        if (expanded) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 28.dp)) {
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
            WeekdayHeader(colors)
            Spacer(Modifier.height(8.dp))
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
                                    DayCell(m.atDay(dayNum), counts, dateRange, iso, colors, Modifier.weight(1f), onPickDay)
                                } else {
                                    Spacer(Modifier.weight(1f).aspectRatio(1f))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // 주 보기에서도 연·월 표시와 화살표를 남겨 둔다. 펼치지 않고도 주를 넘길 수 있어야 한다.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 12.dp)) {
                Text(
                    YearMonth.from(weekAnchor).format(monthFormatter),
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
                GlassIconButton(AppIcons.ChevronLeft, { onShiftWeek(-1L) }, stringResource(R.string.action_prev), size = 34.dp)
                Spacer(Modifier.width(12.dp))
                GlassIconButton(AppIcons.ChevronRight, { onShiftWeek(1L) }, stringResource(R.string.action_next), size = 34.dp)
                Spacer(Modifier.width(2.dp))
            }
            WeekdayHeader(colors)
            Spacer(Modifier.height(8.dp))
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
                        DayCell(startOfWeek.plusDays(i.toLong()), counts, dateRange, iso, colors, Modifier.weight(1f), onPickDay)
                    }
                }
            }
        }

        // 손잡이 — 탭으로도 접었다 펼 수 있게 둔다(달력 위 세로 스와이프와 동일).
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
                    .background(colors.separator),
            )
        }
    }
}

@Composable
private fun WeekdayHeader(colors: TrubbiColors) {
    val names = listOf(
        R.string.weekday_sun,
        R.string.weekday_mon,
        R.string.weekday_tue,
        R.string.weekday_wed,
        R.string.weekday_thu,
        R.string.weekday_fri,
        R.string.weekday_sat,
    )
    Row(Modifier.fillMaxWidth()) {
        names.forEachIndexed { index, nameRes ->
            Text(
                stringResource(nameRes),
                style = MaterialTheme.typography.labelSmall,
                color = when (index) {
                    0 -> colors.red
                    6 -> MaterialTheme.colorScheme.primary
                    else -> colors.secondaryLabel
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
    colors: TrubbiColors,
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
                    .background(if (count > 0) (if (endpoint) MaterialTheme.colorScheme.onPrimary else colors.orange) else Color.Transparent),
            )
        }
    }
}

/** 선택한 기간을 한 줄로. 같은 날이면 날짜 하나만 보여 준다. */
fun rangeLabel(range: Pair<String, String>): String =
    if (range.first == range.second) {
        DateUtils.formatDate(range.first)
    } else {
        "${DateUtils.formatDate(range.first)} ~ ${DateUtils.formatDate(range.second)}"
    }

private const val VERTICAL_SWIPE_PX = 36f
private const val HORIZONTAL_SWIPE_PX = 40f

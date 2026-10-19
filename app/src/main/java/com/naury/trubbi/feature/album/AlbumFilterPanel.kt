package com.naury.trubbi.feature.album

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.trubbi.R
import com.naury.trubbi.core.i18n.label
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.pressable
import com.naury.trubbi.ui.theme.LocalTrubbiColors

/**
 * 앨범을 좁히는 검색창과 필터 패널, 그리고 지금 걸린 조건 표시.
 */
@Composable
internal fun FilterPanel(
    filter: AlbumFilter,
    purposeFilter: String?,
    sortMode: SortMode,
    purposes: List<String>,
    columns: Int,
    dateRange: Pair<String, String>?,
    onFilter: (AlbumFilter) -> Unit,
    onPurpose: (String?) -> Unit,
    onSort: (SortMode) -> Unit,
    onColumns: (Int) -> Unit,
    onClearDate: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (dateRange != null) {
            Chip("${rangeLabel(dateRange)} ✕", selected = true) { onClearDate() }
        }
        FilterSection(stringResource(R.string.filter_section_type)) {
            items(AlbumFilter.entries) { f -> Chip(f.label(), selected = f == filter) { onFilter(f) } }
        }
        FilterSection(stringResource(R.string.filter_section_purpose)) {
            item { Chip(stringResource(R.string.album_filter_all), selected = purposeFilter == null) { onPurpose(null) } }
            items(purposes) { p -> Chip(p, selected = purposeFilter == p) { onPurpose(p) } }
        }
        FilterSection(stringResource(R.string.filter_section_sort)) {
            items(SortMode.entries) { s -> Chip(s.label(), selected = s == sortMode) { onSort(s) } }
        }
        FilterSection(stringResource(R.string.filter_section_grid)) {
            item { Chip(stringResource(R.string.grid_auto), selected = columns == 0) { onColumns(0) } }
            items(listOf(2, 3, 4, 5, 6)) { n ->
                Chip(stringResource(R.string.grid_columns, n), selected = n == columns) { onColumns(n) }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
internal fun SearchBar(
    query: String,
    purposes: List<String>,
    onQuery: (String) -> Unit,
) {
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        stringResource(R.string.album_search_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
            }
            if (query.isNotEmpty()) {
                Icon(
                    AppIcons.Close,
                    contentDescription = stringResource(R.string.action_clear),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp).clickable { onQuery("") },
                )
            }
        }
        // 빠른 제안. 등록된 용도 목록을 그대로 노출한다.
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(purposes) { p -> Chip(p, selected = query == p) { onQuery(p) } }
        }
    }
}

@Composable
private fun FilterSection(label: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

/**
 * 지금 걸려 있는 필터를 한 줄로 요약해 보여 준다.
 *
 * @param filter 종류 필터
 * @param purposeFilter 용도 필터
 * @param dateRange 기간 필터 (시작, 끝)
 * @param onOpenFilters 눌러서 필터 패널을 펼칠 때
 */
@Composable
internal fun ActiveFilterBar(
    filter: AlbumFilter,
    purposeFilter: String?,
    dateRange: Pair<String, String>?,
    onOpenFilters: () -> Unit,
    onClear: () -> Unit,
) {
    val c = LocalTrubbiColors.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(AppIcons.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (filter != AlbumFilter.ALL) item { Chip(filter.label(), selected = true, onClick = onOpenFilters) }
            if (purposeFilter != null) item { Chip(purposeFilter, selected = true, onClick = onOpenFilters) }
            if (dateRange != null) item { Chip(rangeLabel(dateRange), selected = true, onClick = onOpenFilters) }
        }
        Text(
            stringResource(R.string.calendar_reset),
            style = MaterialTheme.typography.labelMedium,
            color = c.secondaryLabel,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onClear() }.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** 검색어가 어느 항목에서 몇 건 걸렸는지 보여 준다 — 결과가 왜 나왔는지 납득시키는 줄. */
@Composable
internal fun SearchBreakdownBar(total: Int, breakdown: List<Pair<SearchField, Int>>) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            pluralStringResource(R.plurals.search_result_count, total, total),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (breakdown.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(breakdown) { (field, count) ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            "${field.label()} $count",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .pressable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
        )
    }
}

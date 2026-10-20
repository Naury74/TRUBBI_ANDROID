package com.naury.trubbi.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.KListItem
import com.naury.kinetica.ui.KListLeading
import com.naury.trubbi.R
import com.naury.trubbi.domain.record.NearPlace
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.glass
import com.naury.trubbi.ui.designsystem.pressable
import com.naury.trubbi.ui.theme.LocalTrubbiColors

/**
 * 검색창 아래 도움말.
 *
 * 검색어가 비어 있으면 최근 검색어와 자주 간 동네를, 검색어가 있으면 그 이름의 실제 장소(주변에 내 기록이 있는 곳)와
 * 태그를 보여 준다. 장소를 누르면 "그 장소 주변 기록" 조건이 걸린다.
 *
 * @param query 지금 검색어
 * @param assist 보여 줄 도움말
 * @param near 걸려 있는 장소 조건
 * @param resultCount 지금 조건으로 걸린 기록 수. 조건이 없으면 null
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun SearchAssistPanel(
    query: String,
    assist: SearchAssist,
    near: NearPlace?,
    resultCount: Int?,
    onQuery: (String) -> Unit,
    onNear: (NearPlace?) -> Unit,
    onRadius: (Int) -> Unit,
    onForget: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (near != null) NearPlaceBar(near, onRadius = onRadius, onClear = { onNear(null) })

        if (query.isBlank() && near == null) {
            if (assist.recent.isNotEmpty()) {
                AssistSection(stringResource(R.string.search_recent), action = stringResource(R.string.search_clear_history), onAction = onClearHistory) {
                    assist.recent.forEach { q -> RemovableChip(q, onClick = { onQuery(q) }, onRemove = { onForget(q) }) }
                }
            }
            if (assist.frequentPlaces.isNotEmpty()) {
                AssistSection(stringResource(R.string.search_frequent_places)) {
                    assist.frequentPlaces.forEach { (name, count) ->
                        CategoryChip(label = stringResource(R.string.search_place_chip, name, count)) { onQuery(name) }
                    }
                }
            }
            Text(stringResource(R.string.search_tips), style = MaterialTheme.typography.bodySmall, color = LocalTrubbiColors.current.secondaryLabel)
        }

        if (query.isNotBlank()) {
            if (assist.places.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).padding(vertical = 4.dp)) {
                    Text(
                        stringResource(R.string.search_places_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = LocalTrubbiColors.current.secondaryLabel,
                        modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 2.dp),
                    )
                    assist.places.forEach { hit ->
                        KListItem(
                            headline = hit.place.name,
                            supporting = listOfNotNull(
                                hit.place.address,
                                pluralStringResource(R.plurals.search_place_records, hit.count, hit.count),
                            ).joinToString(" · "),
                            leading = KListLeading.Icon(AppIcons.LocationOn, tint = MaterialTheme.colorScheme.primary),
                            onClick = { onNear(hit.toNear()) },
                        )
                    }
                }
            }
            if (assist.tags.isNotEmpty()) {
                AssistSection(stringResource(R.string.search_tags)) {
                    assist.tags.forEach { t -> CategoryChip(label = "#$t") { onQuery(t) } }
                }
            }
        }

        if (resultCount != null) {
            Text(
                pluralStringResource(R.plurals.search_result_count, resultCount, resultCount),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

/** 걸려 있는 "주변" 조건 — 장소 이름, 반경 고르기, 해제. 검색 밖(기록 탭 필터 줄)에서도 쓴다. */
@Composable
internal fun NearPlaceBar(near: NearPlace, onRadius: (Int) -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().glass(RoundedCornerShape(percent = 50)).padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.search_near_label, near.label),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        NearPlace.RADII.forEach { r ->
            val selected = r == near.radiusM
            Text(
                radiusLabel(r),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                color = if (selected) MaterialTheme.colorScheme.primary else LocalTrubbiColors.current.secondaryLabel,
                modifier = Modifier.pressable { onRadius(r) }.padding(horizontal = 7.dp, vertical = 6.dp),
            )
        }
        Icon(
            AppIcons.Close,
            contentDescription = stringResource(R.string.search_near_clear),
            tint = LocalTrubbiColors.current.secondaryLabel,
            modifier = Modifier.pressable(onClick = onClear).padding(6.dp).size(18.dp),
        )
    }
}

@Composable
private fun radiusLabel(m: Int): String =
    if (m >= METERS_PER_KM) stringResource(R.string.search_radius_km, m / METERS_PER_KM) else stringResource(R.string.search_radius_m, m)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AssistSection(title: String, action: String? = null, onAction: () -> Unit = {}, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (action != null) {
                Text(
                    action,
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalTrubbiColors.current.secondaryLabel,
                    modifier = Modifier.pressable(onClick = onAction).padding(4.dp),
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
    }
}

@Composable
private fun RemovableChip(label: String, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.glass(RoundedCornerShape(percent = 50), elevation = 3.dp).pressable(onClick = onClick).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        Icon(
            AppIcons.Close,
            contentDescription = stringResource(R.string.search_forget, label),
            tint = LocalTrubbiColors.current.secondaryLabel,
            modifier = Modifier.pressable(onClick = onRemove).padding(8.dp).size(14.dp),
        )
    }
}

private const val METERS_PER_KM = 1000

package com.naury.trubbi.feature.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.naury.trubbi.R
import com.naury.trubbi.core.location.PlaceNames
import com.naury.trubbi.core.util.DateUtils
import com.naury.trubbi.data.local.RecordWithMedia
import com.naury.trubbi.domain.record.NearPlace
import com.naury.trubbi.domain.record.RelatedRecords
import com.naury.trubbi.ui.common.RecordThumb
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.pressable
import com.naury.trubbi.ui.theme.LocalTrubbiColors

/**
 * 정보 시트의 "관련 자료" — 같은 여정, 같은 장소, 같은 바코드·태그로 이어진 기록들.
 *
 * 다른 날 같은 거래처에 다시 갔을 때 지난번 인수증을 찾으려고 날짜를 거슬러 넘기지 않아도 되게 한다.
 * 위치가 있는 기록이면 맨 아래에 "이 근처 기록 모두 보기"를 둬 기록 탭에서 장소 조건으로 이어서 볼 수 있다.
 *
 * @param item 지금 보고 있는 기록
 * @param related 이어진 기록들
 * @param onOpenRecord 관련 기록을 누를 때 (기록 id)
 * @param onShowNearby 근처 기록 모두 보기
 */
@Composable
internal fun RelatedSection(
    item: RecordWithMedia,
    related: RelatedRecords,
    onOpenRecord: (String) -> Unit,
    onShowNearby: (NearPlace) -> Unit,
) {
    val r = item.record
    if (related.sameJourney.isNotEmpty()) RelatedRow(stringResource(R.string.related_same_journey), related.sameJourney, onOpenRecord)
    if (related.samePlace.isNotEmpty()) RelatedRow(stringResource(R.string.related_same_place), related.samePlace, onOpenRecord)
    if (related.linked.isNotEmpty()) RelatedRow(stringResource(R.string.related_linked), related.linked, onOpenRecord)

    val lat = r.latitude
    val lng = r.longitude
    if (lat != null && lng != null && related.nearbyCount > 0) {
        val label = r.locationPlace?.ifBlank { null }
            ?: r.locationRegion?.ifBlank { null }
            ?: PlaceNames.street(r.locationName)
            ?: stringResource(R.string.related_here)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).pressable { onShowNearby(NearPlace(label, lat, lng)) }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                pluralStringResource(R.plurals.related_show_nearby, related.nearbyCount, related.nearbyCount),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Icon(AppIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun RelatedRow(title: String, records: List<RecordWithMedia>, onOpen: (String) -> Unit) {
    val c = LocalTrubbiColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.related_title_count, title, records.size),
            style = MaterialTheme.typography.labelMedium,
            color = c.secondaryLabel,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 4.dp)) {
            items(records, key = { it.record.recordId }) { rec ->
                Column(Modifier.width(THUMB.dp).pressable { onOpen(rec.record.recordId) }) {
                    RecordThumb(rec, Modifier.size(THUMB.dp).clip(RoundedCornerShape(14.dp)))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        rec.record.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(DateUtils.formatDate(rec.record.jobDate), style = MaterialTheme.typography.labelSmall, color = c.secondaryLabel)
                }
            }
        }
    }
}

private const val THUMB = 92

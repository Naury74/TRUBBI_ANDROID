package com.naury.trubbi.feature.journey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.trubbi.R
import com.naury.trubbi.core.location.PlaceCandidate
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.PillButton
import com.naury.trubbi.ui.designsystem.pressable
import com.naury.trubbi.ui.theme.LocalTrubbiColors

/**
 * 장소를 고르는 조각들 — 검색 결과 행, 최근 방문지, 현재 위치 카드, 지도 확인.
 */
/**
 * 선택한 후보의 위치를 전체화면 지도에서 확인하는 단계(TMAP식): 마커로 위치를 보여주고
 * 상단에 장소명·전체 주소, 하단에 "여기가 맞아요"(다음) / "다시 찾기"를 둔다.
 */
@Composable
internal fun PlaceConfirmMap(
    place: PickedPlace,
    roleLabel: String,
    favorited: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalTrubbiColors.current
    val lat = place.latitude ?: return
    val lng = place.longitude ?: return
    val pinColor = MaterialTheme.colorScheme.primary.toArgb()
    val pin = remember(pinColor) { pinBitmap(pinColor) }
    val handle = remember { com.naury.trubbi.ui.common.MapHandle() }
    val markers = remember(lat, lng, pin) {
        listOf(com.naury.trubbi.ui.common.MapMarker("confirm", com.naury.trubbi.ui.common.MapPoint(lat, lng), pin))
    }
    Box(modifier) {
        com.naury.trubbi.ui.common.KakaoMapView(
            markers = markers,
            route = emptyList(),
            focus = com.naury.trubbi.ui.common.MapFocus.Point(lat, lng, 17),
            handle = handle,
            modifier = Modifier.fillMaxSize(),
        )
        // 상단 장소 카드 — 이름 + 전체 주소.
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(14.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(percent = 50)).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    place.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                place.address?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = c.secondaryLabel, maxLines = 2)
                }
            }
            Spacer(Modifier.width(8.dp))
            // 별표(즐겨찾기) 토글.
            Icon(
                if (favorited) AppIcons.Star else AppIcons.StarBorder,
                contentDescription = stringResource(if (favorited) R.string.wizard_unfavorite else R.string.wizard_favorite),
                tint = if (favorited) MaterialTheme.colorScheme.primary else c.secondaryLabel,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .pressable(onClick = onToggleFavorite)
                    .padding(6.dp),
            )
            Spacer(Modifier.width(4.dp))
            OrderTag(roleLabel)
        }
        // 우측 줌 컨트롤.
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            com.naury.trubbi.ui.designsystem.GlassIconButton(AppIcons.Add, { handle.zoomIn() }, stringResource(R.string.action_add))
            com.naury.trubbi.ui.designsystem.GlassIconButton(AppIcons.Remove, { handle.zoomOut() }, stringResource(R.string.action_remove))
        }
        // 하단 액션 — 다시 찾기 / 여기가 맞아요(다음).
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PillButton(
                text = stringResource(R.string.wizard_search_again),
                icon = AppIcons.Search,
                onClick = onBack,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).height(56.dp),
            )
            PillButton(
                text = stringResource(R.string.wizard_confirm_here),
                icon = AppIcons.Check,
                onClick = onConfirm,
                modifier = Modifier.weight(1.3f).height(56.dp),
            )
        }
    }
}

/** 지도 미리보기용 단순 핀(흰 테두리 + 테마색 점) 비트맵. */
private fun pinBitmap(color: Int): android.graphics.Bitmap {
    val size = 72
    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 3f, paint)
    paint.color = color
    canvas.drawCircle(size / 2f, size / 2f, size / 2.8f, paint)
    return bmp
}

/** 최근/현재위치 등 빠른 선택 행 — 아이콘 + 제목(굵게) + 보조(주소). */
@Composable
internal fun RecentPlaceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val c = LocalTrubbiColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (accent) {
                    MaterialTheme.colorScheme.primary.copy(alpha = if (c.isDark) 0.20f else 0.10f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .pressable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(percent = 50))
                .background(if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (accent) MaterialTheme.colorScheme.onPrimary else c.secondaryLabel,
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.secondaryLabel, maxLines = 2)
            }
        }
        Icon(AppIcons.KeyboardArrowRight, contentDescription = null, tint = c.secondaryLabel, modifier = Modifier.size(18.dp))
    }
}

/** 상차지를 못 들었을 때: 현재 위치를 출발지로 쓸지 묻는 카드. */
@Composable
internal fun UseHereCard(locating: Boolean, seconds: Int, onUseHere: () -> Unit, onRetry: () -> Unit) {
    val c = LocalTrubbiColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (c.isDark) 0.14f else 0.08f))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (seconds > 0 && !locating) {
                Box(
                    Modifier.size(28.dp).clip(RoundedCornerShape(percent = 50)).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$seconds",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            } else {
                Icon(AppIcons.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (seconds > 0 && !locating) {
                    stringResource(R.string.wizard_countdown_depart, seconds)
                } else {
                    stringResource(R.string.wizard_depart_from_here)
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(
                text = stringResource(if (locating) R.string.wizard_locating else R.string.wizard_depart_now),
                icon = AppIcons.LocationOn,
                onClick = onUseHere,
                loading = locating,
                enabled = !locating,
                modifier = Modifier.weight(1f),
            )
            PillButton(
                text = stringResource(R.string.wizard_say_again),
                icon = AppIcons.Mic,
                onClick = onRetry,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 검색 후보 한 줄 — 좌측 순서 동그라미(검색 순위) + 우측 "N번째" 칩(음성으로 부를 번호). */
@Composable
internal fun CandidateRow(rank: Int, candidate: PlaceCandidate, onPick: () -> Unit, onMap: (() -> Unit)? = null) {
    val c = LocalTrubbiColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .pressable(onClick = onPick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 좌측: 좌표가 있으면 지도 미리보기 버튼, 없으면 순위 동그라미.
        if (onMap != null) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = if (c.isDark) 0.22f else 0.10f))
                    .pressable(onClick = onMap)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    AppIcons.LocationOn,
                    contentDescription = stringResource(R.string.wizard_view_on_map),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(stringResource(R.string.wizard_map), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$rank",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = c.secondaryLabel,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                candidate.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            candidate.address?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = c.secondaryLabel, maxLines = 2)
            }
        }
        Spacer(Modifier.width(10.dp))
        // 음성으로 부를 번호(검색 순위). "OO번째" 라고 말하면 이 후보가 선택된다.
        Row(
            Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.wizard_rank_nth, rank),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

package com.naury.trubbi.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.naury.trubbi.R
import com.naury.trubbi.core.i18n.purposeLabel
import com.naury.trubbi.core.util.DateUtils
import com.naury.trubbi.data.local.MediaEntity
import com.naury.trubbi.data.local.RecordWithMedia
import com.naury.trubbi.domain.model.RecordType
import com.naury.trubbi.ui.common.PurposeChip
import com.naury.trubbi.ui.common.formatSize
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.PillButton
import com.naury.trubbi.ui.designsystem.glass
import com.naury.trubbi.ui.designsystem.pressable
import com.naury.trubbi.ui.theme.LocalTrubbiColors
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * 사진 위에 겹쳐 뜨는 정보 패널과 그 안의 칩·태그.
 */
/** 넓은 화면 2단 배치의 오른쪽 패널 — 정보와 종류별 동작 버튼을 세로로 쌓는다. */
@Composable
internal fun DetailSidePanel(
    hazeState: HazeState,
    record: RecordWithMedia,
    media: MediaEntity?,
    barcode: String?,
    isCardPage: Boolean,
    isPdfPage: Boolean,
    onOpenPdf: () -> Unit,
    panelWidth: androidx.compose.ui.unit.Dp,
    onRename: () -> Unit,
    onInfo: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onEditImage: () -> Unit,
    onEditVideo: () -> Unit,
    onEditMemo: () -> Unit,
    onShare: () -> Unit,
    onShareBarcode: () -> Unit,
    onBarcodeFull: () -> Unit,
    onCopyBarcode: () -> Unit,
) {
    val r = record.record
    Column(
        Modifier
            .width(panelWidth)
            .fillMaxHeight()
            .hazeBlur(
                HazeInput.Sources(hazeState),
                HazeBlurStyle {
                    backgroundColor(Color.Black)
                    blurRadius(24.dp)
                },
            )
            .background(Color.Black.copy(alpha = 0.42f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 패널 맨 위에 즐겨찾기·삭제.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Spacer(Modifier.weight(1f))
            ImmersiveCircle(
                hazeState,
                if (r.isFavorite) AppIcons.Star else AppIcons.StarBorder,
                if (r.isFavorite) LocalTrubbiColors.current.orange else Color.White,
                42.dp,
            ) { onToggleFavorite() }
            ImmersiveCircle(hazeState, AppIcons.Delete, Color(0xFFFF6B6B), 42.dp) { onDelete() }
        }

        ImmersiveTag(hazeState, purposeLabel(r.purpose))
        Text(
            media?.name?.takeIf { it.isNotBlank() } ?: r.title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.pressable { onRename() },
        )
        Text(DateUtils.formatDateTime(r.createdAt), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))

        val memoText = r.memo?.takeIf { it.isNotBlank() }
        if (memoText != null) {
            Text(
                memoText,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.82f),
                modifier = Modifier.pressable { onInfo() },
            )
        } else if (!isCardPage) {
            Row(
                Modifier.pressable { onInfo() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(AppIcons.Edit, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(15.dp))
                Text(stringResource(R.string.detail_write_memo), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
            }
        }
        val loc = r.locationName
        if (!loc.isNullOrBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(AppIcons.LocationOn, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(15.dp))
                Text(loc, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.82f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }

        Spacer(Modifier.size(2.dp))
        // 기록 종류에 따라 달라지는 동작들. 가로로 꽉 채워 쌓는다.
        when {
            barcode != null -> {
                ImmersiveChip(hazeState, stringResource(R.string.detail_enlarge), AppIcons.Fullscreen, large = true, modifier = Modifier.fillMaxWidth()) {
                    onBarcodeFull()
                }
                ImmersiveChip(hazeState, stringResource(R.string.detail_copy_value), AppIcons.ContentCopy, large = true, modifier = Modifier.fillMaxWidth()) {
                    onCopyBarcode()
                }
                ImmersiveChip(hazeState, stringResource(R.string.action_share), AppIcons.Share, large = true, modifier = Modifier.fillMaxWidth()) {
                    onShareBarcode()
                }
            }
            isCardPage -> {
                ImmersiveChip(hazeState, stringResource(R.string.detail_edit_memo), AppIcons.Edit, large = true, modifier = Modifier.fillMaxWidth()) {
                    onEditMemo()
                }
                ImmersiveChip(hazeState, stringResource(R.string.action_share_long), AppIcons.Share, large = true, modifier = Modifier.fillMaxWidth()) {
                    onShare()
                }
            }
            else -> {
                if (isPdfPage) {
                    ImmersiveChip(hazeState, stringResource(R.string.detail_view_pdf), AppIcons.Description, large = true, modifier = Modifier.fillMaxWidth()) {
                        onOpenPdf()
                    }
                } else if (media != null && media.isVideo) {
                    ImmersiveChip(hazeState, stringResource(R.string.detail_edit_video), AppIcons.Edit, large = true, modifier = Modifier.fillMaxWidth()) {
                        onEditVideo()
                    }
                } else if (media != null) {
                    ImmersiveChip(hazeState, stringResource(R.string.detail_edit_image), AppIcons.Edit, large = true, modifier = Modifier.fillMaxWidth()) {
                        onEditImage()
                    }
                }
                ImmersiveChip(hazeState, stringResource(R.string.action_share_long), AppIcons.Share, large = true, modifier = Modifier.fillMaxWidth()) {
                    onShare()
                }
            }
        }
    }
}

/** 사진 위에 얹는 어두운 유리 칩. 밝은 사진에서도 글씨가 읽히도록 뒤를 살짝 덮는다. */
@Composable
internal fun ImmersiveChip(
    hazeState: HazeState,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(percent = 50))
            .hazeBlur(
                HazeInput.Sources(hazeState),
                HazeBlurStyle {
                    backgroundColor(Color.Black)
                    blurRadius(18.dp)
                },
            )
            .background(Color.Black.copy(alpha = 0.34f))
            .pressable(onClick = onClick)
            .padding(horizontal = if (large) 18.dp else 14.dp, vertical = if (large) 12.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(if (large) 18.dp else 16.dp))
        Text(
            label,
            style = if (large) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold) else MaterialTheme.typography.labelLarge,
            color = Color.White,
            maxLines = 1,
        )
    }
}

/** A small read-only dark-glass tag (용도 / 용량). */
@Composable
internal fun ImmersiveTag(hazeState: HazeState, text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .hazeBlur(
                HazeInput.Sources(hazeState),
                HazeBlurStyle {
                    backgroundColor(Color.Black)
                    blurRadius(16.dp)
                },
            )
            .background(Color.Black.copy(alpha = 0.30f))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
    }
}

/** 사진 위에 얹는 원형 버튼(뒤로·즐겨찾기·삭제). */
@Composable
internal fun ImmersiveCircle(
    hazeState: HazeState,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(percent = 50))
            .hazeBlur(
                HazeInput.Sources(hazeState),
                HazeBlurStyle {
                    backgroundColor(Color.Black)
                    blurRadius(16.dp)
                },
            )
            .background(Color.Black.copy(alpha = 0.34f))
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.45f))
    }
}

@Composable
internal fun InfoSheet(item: RecordWithMedia, viewModel: DetailViewModel, onSaved: () -> Unit) {
    val r = item.record
    val c = LocalTrubbiColors.current
    var memo by remember(r.recordId) { mutableStateOf(r.memo.orEmpty()) }
    var tagsText by remember(r.recordId) { mutableStateOf(r.tags.joinToString(", ")) }

    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), elevation = 0.dp)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(r.title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 용도 칩 — 탭하면 용도 목록이 떠서 이미 저장된 기록의 용도도 바꿀 수 있다.
            var purposeMenu by remember(r.recordId) { mutableStateOf(false) }
            val purposeOptions by viewModel.purposes.collectAsState()
            Box {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .pressable { purposeMenu = true }
                        .padding(end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PurposeChip(r.purpose)
                    Spacer(Modifier.width(3.dp))
                    Icon(
                        AppIcons.Edit,
                        contentDescription = stringResource(R.string.detail_change_purpose),
                        tint = c.secondaryLabel,
                        modifier = Modifier.size(13.dp),
                    )
                }
                DropdownMenu(expanded = purposeMenu, onDismissRequest = { purposeMenu = false }) {
                    val cur = purposeLabel(r.purpose)
                    purposeOptions.forEach { label ->
                        DropdownMenuItem(
                            text = { Text(label, fontWeight = if (label == cur) FontWeight.Bold else FontWeight.Normal) },
                            trailingIcon = if (label == cur) {
                                { Icon(AppIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
                            } else {
                                null
                            },
                            onClick = {
                                purposeMenu = false
                                if (label != cur) viewModel.setPurpose(item, label)
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            val sizeText = formatSize(item.totalSize)
            val mediaCountSuffix = if (item.media.size > 1) stringResource(R.string.detail_media_count, item.media.size) else ""
            Text(
                buildString {
                    append(DateUtils.formatDate(r.jobDate))
                    if (sizeText != null) append(" · $sizeText")
                    append(mediaCountSuffix)
                },
                style = MaterialTheme.typography.labelMedium,
                color = c.secondaryLabel,
            )
        }
        val lat = r.latitude
        val lng = r.longitude
        // 위치가 틀리게 들어갔거나 없을 때 고칠 수 있다(지도 가운데 핀 또는 장소 검색).
        var pickingLocation by remember(r.recordId) { mutableStateOf(false) }
        if (lat != null && lng != null) {
            com.naury.trubbi.ui.common.LocationMapCard(
                latitude = lat,
                longitude = lng,
                address = r.locationName,
                onEdit = { pickingLocation = true },
            )
        } else {
            if (!r.locationName.isNullOrBlank()) {
                Text(
                    stringResource(R.string.detail_location_line, r.locationName.orEmpty()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.secondaryLabel,
                )
            }
            com.naury.kinetica.ui.KButton(
                stringResource(R.string.location_add),
                { pickingLocation = true },
                icon = AppIcons.LocationOn,
                size = com.naury.kinetica.ui.KButtonSize.Small,
                variant = com.naury.kinetica.ui.KVariant.Tonal,
            )
        }
        if (pickingLocation) {
            com.naury.trubbi.ui.common.LocationPicker(
                start = if (lat != null && lng != null) com.naury.trubbi.ui.common.MapPoint(lat, lng) else null,
                search = viewModel::searchPlaces,
                describe = viewModel::describePlace,
                onPick = { place ->
                    viewModel.setLocation(r.recordId, place.latitude, place.longitude, place.building ?: place.address)
                    pickingLocation = false
                },
                onDismiss = { pickingLocation = false },
            )
        }

        Text(stringResource(RecordType.MEMO.labelRes), style = MaterialTheme.typography.labelMedium, color = c.secondaryLabel)
        OutlinedTextField(
            value = memo,
            onValueChange = { memo = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            placeholder = { Text(stringResource(R.string.detail_memo_hint)) },
        )
        Text(stringResource(R.string.detail_tags_label), style = MaterialTheme.typography.labelMedium, color = c.secondaryLabel)
        OutlinedTextField(
            value = tagsText,
            onValueChange = { tagsText = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.detail_tags_hint)) },
        )
        PillButton(
            stringResource(R.string.detail_save_changes),
            onClick = {
                viewModel.saveMemo(r.recordId, memo)
                viewModel.saveTags(r.recordId, tagsText.split(",").map { it.trim() }.filter { it.isNotBlank() })
                onSaved()
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.size(8.dp))
    }
}

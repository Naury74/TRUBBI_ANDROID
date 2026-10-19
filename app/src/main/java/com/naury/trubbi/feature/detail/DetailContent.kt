package com.naury.trubbi.feature.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.naury.trubbi.R
import com.naury.trubbi.core.barcode.BarcodeRenderer
import com.naury.trubbi.core.i18n.purposeLabel
import com.naury.trubbi.core.util.DateUtils
import com.naury.trubbi.data.local.RecordWithMedia
import com.naury.trubbi.domain.memo.MemoContent
import com.naury.trubbi.domain.model.RecordType
import com.naury.trubbi.ui.common.BarcodeFullScreenDialog
import com.naury.trubbi.ui.common.LocationMapCard
import com.naury.trubbi.ui.common.MemoBlocksView
import com.naury.trubbi.ui.common.formatSize
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.GlassIconButton
import com.naury.trubbi.ui.designsystem.GlassPillButton
import com.naury.trubbi.ui.designsystem.PillButton
import com.naury.trubbi.ui.designsystem.TrubbiToastHost
import com.naury.trubbi.ui.designsystem.glass
import com.naury.trubbi.ui.designsystem.rememberTrubbiToastState
import com.naury.trubbi.ui.theme.LocalTrubbiColors

/**
 * 카드형 기록(바코드·메모)의 상세 본문.
 *
 * 사진 기록은 전체화면 뷰어가 맡고, 이쪽은 사진이 아닌 기록을 다룬다.
 */
@Composable
fun DetailContent(
    item: RecordWithMedia,
    onBack: (() -> Unit)?,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
    onShareBarcode: () -> Unit,
    onDelete: () -> Unit,
    onEditImage: (String) -> Unit,
    onOpenImage: () -> Unit,
    onEditMemo: () -> Unit,
    onSaveMemo: (String) -> Unit,
    onSaveTags: (List<String>) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    modifier: Modifier = Modifier,
) {
    val r = item.record
    val c = LocalTrubbiColors.current
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.toast_copied)
    var memo by remember(r.recordId) { mutableStateOf(r.memo.orEmpty()) }
    var tagsText by remember(r.recordId) { mutableStateOf(r.tags.joinToString(", ")) }
    val primaryMedia = item.displayMedia
    val isMemo = RecordType.fromName(r.type) == RecordType.MEMO
    val memoBlocks = remember(r.recordId, r.memo) { MemoContent.decode(r.memo) }
    var showBarcodeFull by remember { mutableStateOf(false) }

    val barcodeValue = r.barcodeValue
    val barcodeBitmap = remember(barcodeValue, r.barcodeFormat) {
        if (barcodeValue.isNullOrBlank()) {
            null
        } else {
            val twoD = BarcodeRenderer.isTwoDimensional(r.barcodeFormat)
            BarcodeRenderer.render(
                value = barcodeValue,
                formatName = r.barcodeFormat,
                widthPx = if (twoD) 640 else 800,
                heightPx = if (twoD) 640 else 320,
            )
        }
    }

    val toast = rememberTrubbiToastState()
    androidx.compose.runtime.CompositionLocalProvider(com.naury.trubbi.ui.designsystem.LocalHazeState provides null) {
        Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = contentPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding() + 32.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (onBack != null) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            GlassIconButton(AppIcons.ArrowBackIos, onBack, stringResource(R.string.action_back), size = 40.dp)
                        }
                    }
                }

                if (barcodeValue != null) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(30.dp))
                                .background(Brush.verticalGradient(listOf(c.gradient.first().copy(alpha = 0.22f), c.accent2.copy(alpha = 0.10f))))
                                .padding(16.dp),
                        ) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(androidx.compose.ui.graphics.Color.White)
                                    .clickable { showBarcodeFull = true }
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                if (barcodeBitmap != null) {
                                    Image(
                                        bitmap = barcodeBitmap.asImageBitmap(),
                                        contentDescription = stringResource(R.string.cd_barcode, barcodeValue),
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(if (BarcodeRenderer.isTwoDimensional(r.barcodeFormat)) 240.dp else 130.dp),
                                    )
                                } else {
                                    Text(
                                        stringResource(R.string.barcode_render_failed),
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    barcodeValue,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.Black,
                                )
                                r.barcodeFormat?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.Gray,
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    stringResource(R.string.detail_tap_to_enlarge),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            GlassPillButton(stringResource(R.string.detail_enlarge), onClick = {
                                showBarcodeFull = true
                            }, icon = AppIcons.Fullscreen, modifier = Modifier.weight(1f))
                            GlassPillButton(stringResource(R.string.detail_copy_value), onClick = {
                                context.copyToClipboard("barcode", barcodeValue, copiedMessage)
                            }, icon = AppIcons.ContentCopy, modifier = Modifier.weight(1f))
                        }
                    }
                }

                if (primaryMedia != null) {
                    item {
                        val isBatch = item.media.size > 1
                        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                            // 뒤로 카드가 겹쳐 보이면 여러 장 묶음이라는 뜻.
                            if (isBatch) {
                                Box(
                                    Modifier.align(Alignment.TopCenter).fillMaxWidth(0.80f).height(22.dp)
                                        .shadow(4.dp, RoundedCornerShape(22.dp), clip = false)
                                        .clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface),
                                )
                                Box(
                                    Modifier.align(Alignment.TopCenter).padding(top = 6.dp).fillMaxWidth(0.90f).height(22.dp)
                                        .shadow(6.dp, RoundedCornerShape(22.dp), clip = false)
                                        .clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                                )
                            }
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .then(if (isBatch) Modifier.padding(top = 13.dp) else Modifier)
                                    .shadow(16.dp, RoundedCornerShape(28.dp), clip = false, spotColor = Color.Black.copy(alpha = 0.25f))
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { onOpenImage() },
                            ) {
                                AsyncImage(
                                    model = primaryMedia.uri,
                                    contentDescription = r.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                if (isBatch) {
                                    OverlayChipDetail(stringResource(R.string.photo_count, item.media.size), Modifier.align(Alignment.TopEnd).padding(12.dp))
                                }
                                OverlayChipDetail(stringResource(R.string.detail_tap_to_enlarge), Modifier.align(Alignment.BottomCenter).padding(12.dp))
                            }
                        }
                    }
                    item {
                        GlassPillButton(
                            stringResource(R.string.detail_edit_image),
                            onClick = { onEditImage(primaryMedia.uri) },
                            icon = AppIcons.Edit,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // 즐겨찾기 별은 큰 이미지 바로 아래, 제목 줄에 둔다.
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            r.title,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                        )
                        GlassIconButton(
                            if (r.isFavorite) AppIcons.Star else AppIcons.StarBorder,
                            onToggleFavorite,
                            stringResource(
                                if (r.isFavorite) R.string.wizard_unfavorite else R.string.wizard_favorite,
                            ),
                            size = 46.dp,
                            tint = if (r.isFavorite) c.orange else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                item {
                    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 4.dp)) {
                        InfoRow(stringResource(R.string.search_field_purpose), purposeLabel(r.purpose))
                        InfoRow(stringResource(R.string.detail_info_job_date), DateUtils.formatDate(r.jobDate))
                        InfoRow(stringResource(R.string.detail_info_saved_at), DateUtils.formatDateTime(r.createdAt))
                        if (item.media.isNotEmpty()) {
                            InfoRow(stringResource(R.string.detail_info_size), formatSize(item.totalSize) ?: "-")
                        }
                        // 다른 모듈의 val 이라 스마트 캐스트가 안 된다.
                        r.locationName?.takeIf { it.isNotBlank() }?.let { place ->
                            InfoRow(stringResource(R.string.detail_info_location), place)
                        }
                    }
                }

                val lat = r.latitude
                val lng = r.longitude
                if (lat != null && lng != null) {
                    item {
                        LocationMapCard(
                            latitude = lat,
                            longitude = lng,
                            address = r.locationName,
                        )
                    }
                }

                if (isMemo) {
                    item {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .glass(RoundedCornerShape(22.dp))
                                .padding(18.dp),
                        ) {
                            if (memoBlocks.isEmpty()) {
                                Text(stringResource(R.string.detail_no_content), style = MaterialTheme.typography.bodyMedium, color = c.secondaryLabel)
                            } else {
                                MemoBlocksView(memoBlocks)
                            }
                        }
                    }
                    item {
                        GlassPillButton(
                            stringResource(R.string.detail_edit_memo),
                            onClick = onEditMemo,
                            icon = AppIcons.Edit,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    item {
                        Column {
                            Text(
                                stringResource(RecordType.MEMO.labelRes),
                                style = MaterialTheme.typography.labelMedium,
                                color = c.secondaryLabel,
                                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
                            )
                            OutlinedTextField(
                                value = memo,
                                onValueChange = { memo = it },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                placeholder = { Text(stringResource(R.string.detail_memo_hint)) },
                            )
                        }
                    }
                }

                item {
                    Column {
                        Text(
                            stringResource(R.string.detail_tags_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = c.secondaryLabel,
                            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
                        )
                        OutlinedTextField(
                            value = tagsText,
                            onValueChange = { tagsText = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.detail_tags_hint)) },
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(4.dp))
                    val savedMessage = stringResource(R.string.toast_saved)
                    PillButton(
                        stringResource(R.string.detail_save_changes),
                        onClick = {
                            // 메모 기록의 본문은 에디터에서 고친다. 여기서는 메모 칸과 태그만 저장한다.
                            if (!isMemo) onSaveMemo(memo)
                            onSaveTags(tagsText.split(",").map { it.trim() }.filter { it.isNotBlank() })
                            toast.show(savedMessage)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlassPillButton(stringResource(R.string.action_share_long), onClick = onShare, icon = AppIcons.Share, modifier = Modifier.weight(1f))
                        GlassIconButton(AppIcons.Delete, onDelete, stringResource(R.string.action_delete), size = 52.dp, tint = c.red)
                    }
                }
            }
            TrubbiToastHost(toast, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    if (showBarcodeFull && barcodeValue != null) {
        BarcodeFullScreenDialog(
            bitmap = barcodeBitmap,
            value = barcodeValue,
            format = r.barcodeFormat,
            isTwoDimensional = BarcodeRenderer.isTwoDimensional(r.barcodeFormat),
            onShare = {
                showBarcodeFull = false
                onShareBarcode()
            },
            onDismiss = { showBarcodeFull = false },
        )
    }
}

@Composable
private fun OverlayChipDetail(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = LocalTrubbiColors.current.secondaryLabel, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

private fun Context.copyToClipboard(label: String, text: String, message: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

package com.naury.trubbi.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.video.videoFramePercent
import com.naury.trubbi.R
import com.naury.trubbi.core.barcode.BarcodeRenderer
import com.naury.trubbi.core.i18n.purposeLabel
import com.naury.trubbi.data.local.RecordWithMedia
import com.naury.trubbi.domain.model.RecordType
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.liquidGlass
import com.naury.trubbi.ui.designsystem.pressable
import com.naury.trubbi.ui.designsystem.sharedMedia
import com.naury.trubbi.ui.theme.LocalTrubbiColors
import java.util.Locale

/** 용도 배지. 저장된 토큰이 기본 용도면 다국어 라벨로, 사용자 정의 라벨이면 그대로 보여 준다. */
@Composable
fun PurposeChip(purposeName: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            purposeLabel(purposeName),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 사진 위에 얹는 반투명 칩. */
@Composable
private fun OverlayChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1)
    }
}

/** 저장된 바코드 값을 다시 스캔 가능한 미리보기 비트맵으로 렌더한다(메인 스레드 밖에서). */
@Composable
private fun BarcodePreview(record: RecordWithMedia, modifier: Modifier = Modifier) {
    val value = record.record.barcodeValue
    val format = record.record.barcodeFormat
    val bitmap by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, value, format) {
        this.value = if (value.isNullOrBlank()) {
            null
        } else {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                val twoD = BarcodeRenderer.isTwoDimensional(format)
                BarcodeRenderer.render(
                    value = value,
                    formatName = format,
                    widthPx = if (twoD) 360 else 480,
                    heightPx = if (twoD) 360 else 220,
                )
            }
        }
    }
    val bmp = bitmap
    Box(modifier.background(Color.White), contentAlignment = Alignment.Center) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = record.record.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(12.dp),
            )
        } else {
            Icon(
                AppIcons.QrCode2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

/** 기록의 대표 이미지. 바코드는 값에서 다시 렌더하고, 그 외에는 저장된 썸네일을 쓴다. */
@Composable
fun RecordThumb(record: RecordWithMedia, modifier: Modifier = Modifier) {
    val type = RecordType.fromName(record.record.type)
    val context = LocalContext.current
    val media = record.displayMedia
    val thumb = record.primaryThumb?.takeIf { it.isNotBlank() }
    // 영상은 파일에서 직접 포스터 프레임을 뽑는다. 편집본·묶음 영상도 빈 칸으로 남지 않게.
    // size() 로 상한을 두지 않으면 목록 스크롤이 끊긴다.
    val model: Any? = remember(media?.uri, media?.isVideo, thumb) {
        if (media != null && media.isVideo) {
            ImageRequest.Builder(context).data(media.uri).videoFramePercent(0.1).size(640).crossfade(true).build()
        } else {
            thumb
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        when {
            type == RecordType.BARCODE && thumb == null -> BarcodePreview(record, Modifier.fillMaxSize())
            model != null -> AsyncImage(
                model = model,
                contentDescription = record.record.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            else -> Icon(
                AppIcons.Description,
                contentDescription = null,
                tint = LocalTrubbiColors.current.secondaryLabel,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

/**
 * 갤러리형 정사각 셀 — 미디어를 가득 채우고 그 위에 용도·장수·즐겨찾기 칩을 얹는다.
 *
 * @param selectionMode 다중 선택 중인지
 * @param batchTotal 묶음 촬영일 때 전체 장수 (1 이하면 단일 기록으로 취급)
 * @param batchSelected 묶음 중 선택된 장수
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordGridCell(
    record: RecordWithMedia,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    batchTotal: Int = 0,
    batchSelected: Int = 0,
) {
    val type = RecordType.fromName(record.record.type)
    val isVideo = type == RecordType.VIDEO || record.hasVideo
    val sizeText = formatSize(record.totalSize)
    val stacked = (type == RecordType.PHOTO || type == RecordType.VIDEO) && record.media.size > 1
    val typeLabel = stringResource(if (isVideo) RecordType.VIDEO.labelRes else type.labelRes)
    // 탭하면 셀 전체가 살짝 눌리는 스프링 피드백.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 650f),
        label = "cellPress",
    )
    Box(
        modifier.aspectRatio(1f).graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
        },
    ) {
        // 묶음 촬영이면 뒤에 카드가 겹쳐 보이게 해서 한 장이 아님을 드러낸다.
        if (stacked) {
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth(0.74f).height(22.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, LocalTrubbiColors.current.separator, RoundedCornerShape(14.dp)),
            )
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = 5.dp).fillMaxWidth(0.86f).height(22.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, LocalTrubbiColors.current.separator, RoundedCornerShape(14.dp)),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .then(if (stacked) Modifier.padding(top = 11.dp) else Modifier)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .combinedClickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick, onLongClick = onLongClick),
        ) {
            // 공유 요소 전환은 이미지에만 적용한다.
            RecordThumb(record, Modifier.fillMaxSize().sharedMedia(record.record.recordId))

            OverlayChip(
                purposeLabel(record.record.purpose),
                Modifier.align(Alignment.TopStart).padding(6.dp),
            )

            Row(
                Modifier.align(Alignment.TopEnd).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (!selectionMode && record.record.type == RecordType.PHOTO.name && record.media.size > 1) {
                    OverlayChip(stringResource(R.string.photo_count, record.media.size))
                }
                if (record.record.isFavorite) {
                    Icon(AppIcons.Star, contentDescription = null, tint = LocalTrubbiColors.current.orange, modifier = Modifier.size(18.dp))
                }
            }

            if (isVideo) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(40.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }

            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                            ),
                        ),
                    )
                    .padding(start = 10.dp, end = 10.dp, top = 16.dp, bottom = 9.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    record.record.title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (sizeText != null) "$typeLabel · $sizeText" else typeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalTrubbiColors.current.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (selectionMode) {
                val isBatch = batchTotal > 1
                if (isBatch) {
                    if (batchSelected > 0) {
                        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.2f)))
                    }
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(if (batchSelected > 0) Color.White else Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            "$batchSelected/$batchTotal",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (batchSelected > 0) Color.Black else Color.White,
                        )
                    }
                } else {
                    if (selected) {
                        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.22f)))
                    }
                    if (selected) {
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp)
                                .clip(RoundedCornerShape(percent = 50)).background(Color.White),
                            contentAlignment = Alignment.Center,
                        ) { Icon(AppIcons.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(15.dp)) }
                    } else {
                        Icon(
                            AppIcons.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(24.dp)
                                .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(percent = 50)),
                        )
                    }
                }
                if (selected || batchSelected > 0) {
                    Box(Modifier.matchParentSize().border(2.dp, Color.White, RoundedCornerShape(20.dp)))
                }
            }
        }
    }
}

/** 달력·상세 목록에서 쓰는 가로 행. */
@Composable
fun RecordListRow(record: RecordWithMedia, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .liquidGlass(RoundedCornerShape(18.dp))
            .pressable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp)),
        ) { RecordThumb(record, Modifier.fillMaxSize()) }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                record.record.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            PurposeChip(record.record.purpose)
        }
        if (record.record.isFavorite) {
            Icon(
                AppIcons.Star,
                contentDescription = null,
                tint = LocalTrubbiColors.current.orange,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(18.dp),
            )
        }
    }
}

/** 사람이 읽는 파일 크기. 크기를 모르거나 0이면 null. */
fun formatSize(bytes: Long): String? {
    if (bytes <= 0L) return null
    val kb = bytes / 1024.0
    if (kb < 1024) return "${kb.toInt()}KB"
    val mb = kb / 1024.0
    return if (mb < 100) String.format(Locale.US, "%.1fMB", mb) else "${mb.toInt()}MB"
}

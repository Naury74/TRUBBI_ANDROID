package com.naury.trubbi.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFramePercent
import com.naury.trubbi.R
import com.naury.trubbi.core.barcode.BarcodeRenderer
import com.naury.trubbi.core.i18n.purposeLabel
import com.naury.trubbi.data.local.RecordWithMedia
import com.naury.trubbi.domain.memo.MemoContent
import com.naury.trubbi.domain.model.RecordType
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.glass
import com.naury.trubbi.ui.designsystem.sharedMedia
import com.naury.trubbi.ui.theme.LocalTrubbiColors

/**
 * 하루치 기록을 가용 폭에 맞춰 메이슨리로 배치한다.
 *
 * 열 수는 가용 폭에서 계산한다. 사진·영상은 원본 비율을 유지하고, 이미지가 없는 기록은
 * 정보 카드로 그린다. 하루 항목 수가 제한적이라 lazy 컴포넌트를 쓰지 않으며,
 * 따라서 다른 스크롤 컨테이너 안에 중첩할 수 있다.
 *
 * @param selectedMedia 묶음을 풀어 개별 선택 중인 미디어 id
 * @param expandedBatch 지금 펼쳐 둔 묶음 기록 id
 * @param minCellWidth 이 폭을 기준으로 열 수를 정한다
 */
@Composable
fun DateFeed(
    items: List<RecordWithMedia>,
    onClick: (RecordWithMedia) -> Unit,
    onLongClick: (String) -> Unit,
    selection: Set<String>,
    selectionMode: Boolean,
    modifier: Modifier = Modifier,
    selectedMedia: Set<String> = emptySet(),
    expandedBatch: String? = null,
    onToggleMedia: (String) -> Unit = {},
    onToggleBatch: (String, List<String>) -> Unit = { _, _ -> },
    onLongPressBatch: (String, List<String>) -> Unit = { _, _ -> },
    minCellWidth: Dp = 170.dp,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cols = (maxWidth / minCellWidth).toInt().coerceIn(2, 5)
        // 직접 펼친 묶음과 '일부만 선택된' 묶음을 모두 펼친 상태로 유지한다.
        val openIds: Set<String> = if (selectionMode) {
            items.mapNotNullTo(LinkedHashSet()) { rec ->
                val id = rec.record.recordId
                if (rec.media.size > 1) {
                    val sel = rec.media.count { it.mediaId in selectedMedia }
                    if (id == expandedBatch || sel in 1 until rec.media.size) id else null
                } else {
                    null
                }
            }
        } else {
            emptySet()
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            var start = 0
            items.forEachIndexed { i, rec ->
                if (rec.record.recordId in openIds) {
                    MasonrySection(
                        items.subList(
                            start,
                            i + 1,
                        ),
                        cols, onClick, onLongClick, selection, selectionMode, selectedMedia, expandedBatch, onToggleBatch, onLongPressBatch,
                    )
                    val appear = remember(rec.record.recordId) { MutableTransitionState(false).apply { targetState = true } }
                    AnimatedVisibility(
                        visibleState = appear,
                        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                    ) {
                        BatchExpansion(rec, selectedMedia, onToggleMedia)
                    }
                    start = i + 1
                }
            }
            if (start < items.size) {
                MasonrySection(
                    items.subList(
                        start,
                        items.size,
                    ),
                    cols, onClick, onLongClick, selection, selectionMode, selectedMedia, expandedBatch, onToggleBatch, onLongPressBatch,
                )
            }
        }
    }
}

/** 메이슨리 한 덩어리. 묶음의 탭·길게 누르기 규칙까지 여기서 정한다. */
@Composable
private fun MasonrySection(
    items: List<RecordWithMedia>,
    cols: Int,
    onClick: (RecordWithMedia) -> Unit,
    onLongClick: (String) -> Unit,
    selection: Set<String>,
    selectionMode: Boolean,
    selectedMedia: Set<String>,
    expandedBatch: String?,
    onToggleBatch: (String, List<String>) -> Unit,
    onLongPressBatch: (String, List<String>) -> Unit,
) {
    val columns = remember(items, cols) {
        val cells = List(cols) { mutableListOf<RecordWithMedia>() }
        val heights = FloatArray(cols)
        items.forEach { rec ->
            var ci = 0
            for (i in 1 until cols) if (heights[i] < heights[ci]) ci = i
            cells[ci].add(rec)
            heights[ci] += estimateCellHeight(rec)
        }
        cells
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        columns.forEach { col ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                col.forEach { rec ->
                    val isBatch = rec.media.size > 1
                    val mediaIds = remember(rec) { rec.media.map { it.mediaId } }
                    FeedCell(
                        record = rec,
                        // 선택 중에 묶음을 한 번 누르면 묶음 전체가 토글되고 개별 선택 패널이 열린다.
                        onClick = {
                            if (selectionMode && isBatch) onToggleBatch(rec.record.recordId, mediaIds) else onClick(rec)
                        },
                        // 묶음을 길게 누르면 전체가 선택된 상태로 선택 모드에 진입한다.
                        onLongClick = {
                            if (isBatch) {
                                onLongPressBatch(rec.record.recordId, mediaIds)
                            } else {
                                onLongClick(rec.record.recordId)
                            }
                        },
                        selected = rec.record.recordId in selection,
                        selectionMode = selectionMode,
                        expanded = expandedBatch == rec.record.recordId,
                        batchTotal = if (isBatch) rec.media.size else 0,
                        batchSelected = if (isBatch) rec.media.count { it.mediaId in selectedMedia } else 0,
                    )
                }
            }
        }
    }
}

/** 열 높이를 맞추기 위한 상대 높이 추정값. 실제 픽셀이 아니라 비율만 맞으면 된다. */
private fun estimateCellHeight(rec: RecordWithMedia): Float {
    val type = RecordType.fromName(rec.record.type)
    val media = rec.displayMedia
    return when {
        (type == RecordType.PHOTO || type == RecordType.VIDEO || type == RecordType.DOCUMENT) && media != null ->
            1f / mediaAspect(media.width, media.height) + 0.22f

        type == RecordType.MEMO -> 1.25f
        else -> 0.95f
    }
}

/** 지나치게 길쭉한 사진이 한 열을 독차지하지 않도록 비율을 가둔다. */
private fun mediaAspect(width: Int?, height: Int?): Float =
    if (width != null && width > 0 && height != null && height > 0) {
        (width.toFloat() / height).coerceIn(0.62f, 1.85f)
    } else {
        1f
    }

@Composable
private fun FeedCell(
    record: RecordWithMedia,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    selected: Boolean,
    selectionMode: Boolean,
    expanded: Boolean = false,
    batchTotal: Int = 0,
    batchSelected: Int = 0,
) {
    val type = RecordType.fromName(record.record.type)
    val isMedia = record.displayMedia != null &&
        (type == RecordType.PHOTO || type == RecordType.VIDEO || type == RecordType.DOCUMENT)
    if (isMedia) {
        FeedMediaCell(record, onClick, onLongClick, selected, selectionMode, expanded, batchTotal, batchSelected)
    } else {
        FeedTextCard(record, onClick, onLongClick, selected)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedMediaCell(
    record: RecordWithMedia,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    selected: Boolean,
    selectionMode: Boolean,
    expanded: Boolean = false,
    batchTotal: Int = 0,
    batchSelected: Int = 0,
) {
    val colors = LocalTrubbiColors.current
    val media = record.displayMedia
    val isVideo = media?.isVideo == true || record.hasVideo
    val isDoc = media?.isPdf == true || record.hasPdf
    val aspect = mediaAspect(media?.width, media?.height)
    val stacked = record.media.size > 1
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow), label = "feedPress")

    Box(
        Modifier.fillMaxWidth().graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    ) {
        // 묶음이면 뒤에 카드가 겹쳐 보이게 해서 한 장이 아님을 드러낸다.
        if (stacked) {
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth(0.74f).height(18.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, colors.separator, RoundedCornerShape(14.dp)),
            )
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = 4.dp).fillMaxWidth(0.86f).height(18.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, colors.separator, RoundedCornerShape(14.dp)),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .then(if (stacked) Modifier.padding(top = 9.dp) else Modifier)
                .aspectRatio(aspect)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .combinedClickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick, onLongClick = onLongClick),
        ) {
            RecordThumb(record, Modifier.fillMaxSize().sharedMedia(record.record.recordId))

            OverlayChip(purposeLabel(record.record.purpose), Modifier.align(Alignment.TopStart).padding(8.dp))

            if (!selectionMode) {
                Row(
                    Modifier.align(Alignment.TopEnd).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (isDoc) {
                        // 문서는 장수 대신 PDF 배지로 알린다. 원본 이미지가 함께 있으면 쪽수까지.
                        val pages = record.media.count { !it.isPdf }
                        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 7.dp, vertical = 3.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(AppIcons.Description, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                Text(
                                    if (pages > 0) stringResource(R.string.feed_pdf_pages, pages) else "PDF",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    maxLines = 1,
                                )
                            }
                        }
                    } else if (record.media.size > 1) {
                        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 7.dp, vertical = 3.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(
                                    if (isVideo) AppIcons.Videocam else AppIcons.ImageIcon,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp),
                                )
                                Text("${record.media.size}", style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1)
                            }
                        }
                    }
                    if (record.record.isFavorite) {
                        Icon(AppIcons.Star, contentDescription = null, tint = colors.orange, modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (isVideo) {
                Box(
                    Modifier.align(Alignment.Center).size(42.dp).clip(RoundedCornerShape(percent = 50)).background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp)) }
                media?.durationMs?.takeIf { it > 0 }?.let { d ->
                    Box(
                        Modifier.align(
                            Alignment.BottomEnd,
                        ).padding(8.dp).clip(RoundedCornerShape(7.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(formatClock(d), style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1)
                    }
                }
            }

            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))))
                    .padding(start = 10.dp, end = 10.dp, top = 18.dp, bottom = 8.dp),
            ) {
                Text(
                    // 묶음은 기록 제목을, 낱장은 미디어에 붙인 이름을 우선한다.
                    if (stacked) {
                        record.record.title
                    } else {
                        record.displayMedia?.name?.takeIf { it.isNotBlank() } ?: record.record.title
                    },
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (selectionMode) {
                val isBatch = batchTotal > 0
                if (isBatch) {
                    val hasSelection = batchSelected > 0
                    Box(
                        Modifier.fillMaxSize().background(
                            when {
                                hasSelection -> Color.White.copy(alpha = 0.22f)
                                expanded -> Color.Black.copy(alpha = 0.32f)
                                else -> Color.Black.copy(alpha = 0.12f)
                            },
                        ),
                    )
                    Row(
                        Modifier.align(Alignment.TopEnd).padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (expanded) Color.White else Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        val chipContent = if (expanded) Color.Black else Color.White
                        Icon(
                            if (isVideo) AppIcons.Videocam else AppIcons.PhotoLibrary,
                            contentDescription = null,
                            tint = chipContent,
                            modifier = Modifier.size(13.dp),
                        )
                        Text("$batchSelected/$batchTotal", style = MaterialTheme.typography.labelSmall, color = chipContent, maxLines = 1)
                    }
                } else {
                    if (selected) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.22f)))
                    SelectionCheck(selected, Modifier.align(Alignment.TopEnd).padding(8.dp))
                }
            }
            if (selected || (batchTotal > 0 && batchSelected > 0)) {
                Box(Modifier.matchParentSize().border(2.dp, Color.White, RoundedCornerShape(18.dp)))
            }
        }
    }
}

/**
 * 선택 표시.
 *
 */
@Composable
private fun SelectionCheck(selected: Boolean, modifier: Modifier = Modifier) {
    if (selected) {
        Box(
            modifier.size(22.dp).clip(RoundedCornerShape(percent = 50)).background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
        }
    } else {
        Icon(
            AppIcons.RadioButtonUnchecked,
            contentDescription = null,
            tint = Color.White,
            modifier = modifier.size(22.dp).clip(RoundedCornerShape(percent = 50)).background(Color.Black.copy(alpha = 0.28f)),
        )
    }
}

/** 피드 아래로 펼쳐지는 패널 — 묶음을 풀어 사진을 하나씩 고를 수 있게 한다. */
@Composable
private fun BatchExpansion(
    record: RecordWithMedia,
    selectedMedia: Set<String>,
    onToggleMedia: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTrubbiColors.current
    val context = LocalContext.current
    val media = remember(record) { record.media.sortedBy { it.createdAt } }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .glass(RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(
                R.string.feed_batch_panel_title,
                record.record.title.ifBlank { stringResource(R.string.feed_batch_fallback_title) },
                media.size,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = colors.secondaryLabel,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(media, key = { it.mediaId }) { m ->
                val picked = m.mediaId in selectedMedia
                // 영상은 파일에서 포스터 프레임을 뽑는다. size() 로 상한을 두지 않으면 스크롤이 끊긴다.
                val model = remember(m.uri, m.isVideo, m.thumbnailUri) {
                    if (m.isVideo) {
                        ImageRequest.Builder(context).data(m.uri).videoFramePercent(0.1).size(240).crossfade(true).build()
                    } else {
                        (m.thumbnailUri ?: m.uri)
                    }
                }
                Box(
                    Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .combinedClickableSafe(remember { MutableInteractionSource() }, { onToggleMedia(m.mediaId) }, { onToggleMedia(m.mediaId) }),
                ) {
                    AsyncImage(
                        model = model,
                        contentDescription = m.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (m.isVideo) {
                        Box(
                            Modifier.align(Alignment.Center).size(26.dp).clip(RoundedCornerShape(percent = 50)).background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(AppIcons.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                    }
                    if (picked) {
                        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.25f)))
                        Box(Modifier.matchParentSize().border(2.dp, Color.White, RoundedCornerShape(12.dp)))
                    }
                    SelectionCheck(picked, Modifier.align(Alignment.TopEnd).padding(4.dp))
                }
            }
        }
    }
}

@Composable
private fun FeedTextCard(
    record: RecordWithMedia,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    selected: Boolean,
) {
    val colors = LocalTrubbiColors.current
    val r = record.record
    val type = RecordType.fromName(r.type)
    val isBarcode = !r.barcodeValue.isNullOrBlank()
    val emptyMemo = stringResource(R.string.memo_empty)
    // 리치 메모 JSON 을 푸는 비용이 작지 않아 기록 단위로 캐시한다.
    val preview = remember(r.recordId, r.memo, r.barcodeValue, emptyMemo) {
        when {
            isBarcode -> r.barcodeValue.orEmpty()
            else -> MemoContent.plainText(MemoContent.decode(r.memo)).ifBlank { emptyMemo }
        }
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow), label = "feedTextPress")
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .glass(RoundedCornerShape(18.dp))
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp)) else Modifier)
            .combinedClickableSafe(interaction, onClick, onLongClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isBarcode) AppIcons.QrCode2 else AppIcons.Description,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else colors.secondaryLabel,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(6.dp))
            Text(
                purposeLabel(r.purpose),
                style = MaterialTheme.typography.labelSmall,
                color = colors.secondaryLabel,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            if (r.isFavorite) Icon(AppIcons.Star, contentDescription = null, tint = colors.orange, modifier = Modifier.size(16.dp))
        }
        Text(
            r.title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (isBarcode) {
            val twoD = BarcodeRenderer.isTwoDimensional(r.barcodeFormat)
            // 메인 스레드에서 렌더하면 목록 스크롤이 끊긴다.
            val bmp by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, r.barcodeValue, r.barcodeFormat) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    BarcodeRenderer.render(r.barcodeValue.orEmpty(), r.barcodeFormat, if (twoD) 420 else 640, if (twoD) 420 else 240)
                }
            }
            val barcodeBmp = bmp
            if (barcodeBmp != null) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White).padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = barcodeBmp.asImageBitmap(),
                        contentDescription = r.barcodeValue,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(if (twoD) 120.dp else 64.dp),
                    )
                }
            }
            Text(
                r.barcodeValue.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                preview,
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondaryLabel,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableSafe(
    interaction: MutableInteractionSource,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
): Modifier = this.combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongClick)

@Composable
private fun OverlayChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1)
    }
}

private fun formatClock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

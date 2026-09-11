package com.naury.trubbi.feature.detail

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFramePercent
import com.naury.trubbi.R
import com.naury.trubbi.core.barcode.BarcodeRenderer
import com.naury.trubbi.data.local.MediaEntity
import com.naury.trubbi.data.local.RecordWithMedia
import com.naury.trubbi.domain.memo.MemoContent
import com.naury.trubbi.ui.common.MemoBlocksView
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.appearOnce
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 미디어를 그리는 조각 — 확대·축소 이미지, 영상 페이지, 하단 필름스트립.
 */
@Composable
internal fun Filmstrip(entries: List<Pair<Int, MediaEntity>>, currentGlobal: Int, onSelect: (Int) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth(),
    ) {
        itemsIndexed(entries) { index, entry ->
            val globalIndex = entry.first
            val m = entry.second
            val isCurrent = globalIndex == currentGlobal
            Box(
                Modifier
                    // 묶음이 열릴 때 촤라락 펼쳐지는 등장 효과.
                    .appearOnce(index * 45)
                    .size(if (isCurrent) 60.dp else 50.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        width = if (isCurrent) 2.5.dp else 0.dp,
                        color = if (isCurrent) Color.White else Color.Transparent,
                        shape = RoundedCornerShape(10.dp),
                    )
                    .clickable { onSelect(globalIndex) },
            ) {
                AsyncImage(
                    model = rememberMediaModel(m),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (m.isVideo) {
                    Icon(
                        AppIcons.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.align(Alignment.Center).size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun VideoPage(media: MediaEntity, hazeState: HazeState, onToggleChrome: () -> Unit, onPlay: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { onToggleChrome() }) },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = rememberMediaModel(media),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .size(72.dp)
                    .pointerInput(Unit) { detectTapGestures(onTap = { onPlay() }) }
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(percent = 50)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.PlayArrow, contentDescription = stringResource(R.string.detail_play), tint = Color.White, modifier = Modifier.size(40.dp))
            }
            // 재생 버튼 아래 영상 길이 칩.
            media.durationMs?.takeIf { it > 0 }?.let { d ->
                Row(
                    Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .hazeChild(hazeState) {
                            backgroundColor = Color.Black
                            blurRadius = 14.dp
                        }
                        .background(Color.Black.copy(alpha = 0.34f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(AppIcons.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                    Text(formatDuration(d), style = MaterialTheme.typography.labelMedium, color = Color.White)
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

/**
 * 화면에 보이는 기준의 가로세로 비율.
 *
 * 영상은 메타데이터의 가로·세로가 회전 플래그를 빼고 적혀 있어, 세로로 찍은 영상이 16:9 라고
 * 보고한다. 회전 보정을 넣기 전에 저장된 영상들이 아직 남아 있어서, 저장값을 믿지 않고
 * 파일에서 실제 값을 다시 읽어 바로잡는다.
 */
@Composable
internal fun rememberDisplayAspect(uri: String, isVideo: Boolean, fallback: Float): Float {
    if (!isVideo) return fallback
    val context = LocalContext.current
    val st = produceState(fallback, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val r = MediaMetadataRetriever()
                r.setDataSource(context, Uri.parse(uri))
                var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                r.release()
                if (rot == 90 || rot == 270) {
                    val t = w
                    w = h
                    h = t
                }
                if (w > 0 && h > 0) (w.toFloat() / h).coerceIn(0.42f, 2.4f) else fallback
            }.getOrDefault(fallback)
        }
    }
    return st.value
}

/** 미디어를 그릴 이미지 모델. 영상은 파일에서 검지 않은 프레임을 직접 뽑아 쓴다. */
@Composable
internal fun rememberMediaModel(media: MediaEntity): Any {
    val context = LocalContext.current
    return remember(media.uri, media.thumbnailUri, media.isVideo) {
        if (media.isVideo) {
            ImageRequest.Builder(context).data(media.uri).videoFramePercent(0.1).crossfade(true).build()
        } else {
            (media.thumbnailUri ?: media.uri)
        }
    }
}

@Composable
internal fun ZoomableImage(uri: String, onToggleChrome: () -> Unit) {
    var scale by remember(uri) { mutableFloatStateOf(1f) }
    var offset by remember(uri) { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(uri) {
                detectTapGestures(
                    onTap = { onToggleChrome() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                )
            }
            // 두 손가락이거나 이미 확대된 상태에서만 제스처를 가로챈다. 그러지 않으면
            // 원본 크기에서 한 손가락으로 넘기는 동작까지 먹어 페이지가 넘어가지 않는다.
            .pointerInput(uri) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pointers = event.changes.count { it.pressed }
                        if (scale > 1f || pointers >= 2) {
                            val zoom = event.calculateZoom()
                            if (zoom != 1f) scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale > 1f) offset + event.calculatePan() else Offset.Zero
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (scale <= 1f) offset = Offset.Zero
                }
            },
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
        )
    }
}

/** 사진이 아닌 기록의 카드 본문 — 흰 카드 위에 바코드나 메모를 담는다. */
@Composable
internal fun CardPageContent(record: RecordWithMedia, onToggleChrome: () -> Unit) {
    val r = record.record
    val barcodeValue = r.barcodeValue?.takeIf { it.isNotBlank() }
    val tapToggle = Modifier.pointerInput(Unit) { detectTapGestures(onTap = { onToggleChrome() }) }
    if (barcodeValue != null) {
        val twoD = BarcodeRenderer.isTwoDimensional(r.barcodeFormat)
        val bmp = remember(barcodeValue, r.barcodeFormat) {
            BarcodeRenderer.render(barcodeValue, r.barcodeFormat, if (twoD) 640 else 800, if (twoD) 640 else 320)
        }
        Column(
            Modifier.fillMaxWidth().then(tapToggle).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = barcodeValue,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(if (twoD) 260.dp else 140.dp),
                )
            } else {
                Text(stringResource(R.string.barcode_render_failed), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }
            Text(barcodeValue, style = MaterialTheme.typography.titleMedium, color = Color.Black)
            r.barcodeFormat?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Color.Gray) }
        }
    } else {
        val memoBlocks = remember(r.recordId, r.memo) { MemoContent.decode(r.memo) }
        Column(Modifier.fillMaxWidth().then(tapToggle).verticalScroll(rememberScrollState()).padding(22.dp)) {
            if (memoBlocks.isEmpty()) {
                Text(stringResource(R.string.detail_no_content), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            } else {
                MemoBlocksView(memoBlocks)
            }
        }
    }
}

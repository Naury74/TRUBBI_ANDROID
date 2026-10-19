package com.naury.trubbi.feature.pdf
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.naury.trubbi.R
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.GlassIconButton
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PDF 뷰어 — 스캔한 문서를 앱 안에서 바로 본다.
 *
 * 외부 뷰어에 위임하지 않고 플랫폼 `PdfRenderer` 로 페이지를 직접 렌더링한다.
 * 뷰어 앱이 설치돼 있지 않아도 동작하고 앱을 벗어나지 않는다.
 *
 * @param pdfUri 볼 PDF 의 uri
 * @param title 상단 제목
 * @param onBack 뒤로 갈 때
 * @param onShare 주면 상단에 공유 버튼이 생긴다
 */
@Composable
fun PdfViewerScreen(
    pdfUri: String,
    title: String,
    onBack: () -> Unit,
    onShare: (() -> Unit)? = null,
) {
    val pages by produceState<List<Bitmap>?>(initialValue = null, pdfUri) {
        value = withContext(Dispatchers.IO) { renderPdf(pdfUri) }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val rendered = pages
        when {
            rendered == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    com.naury.kinetica.ui.KLoadingIndicator()
                }
            }
            rendered.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.pdf_cannot_open), color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodyLarge)
                }
            }
            else -> {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    // 위쪽 바(상태 표시줄 + 버튼 줄) 아래에서 첫 쪽이 시작되게 한다. 기기마다 상태 표시줄 높이가 다르다.
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 72.dp,
                        bottom = 32.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(rendered) { index, bmp ->
                        ZoomablePage(bmp)
                        if (rendered.size > 1) {
                            Text(
                                "${index + 1} / ${rendered.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlassIconButton(AppIcons.ArrowBackIos, onBack, stringResource(R.string.action_back), tint = MaterialTheme.colorScheme.onSurface)
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (onShare != null) {
                GlassIconButton(AppIcons.Share, onShare, stringResource(R.string.action_share), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ZoomablePage(bmp: Bitmap) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val zoomed = scale > 1f
    Box(
        Modifier
            .fillMaxWidth()
            // 확대 중에는 페이지 경계로 자르지 않아야 전체화면까지 키워 볼 수 있다.
            .then(if (zoomed) Modifier.zIndex(1f) else Modifier.clip(RoundedCornerShape(8.dp)))
            .background(Color.White)
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        scale = 2.5f
                    }
                })
            }
            .pointerInput(Unit) {
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
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
        )
    }
}

private fun renderPdf(uriString: String): List<Bitmap> {
    val path = Uri.parse(uriString).path ?: return emptyList()
    val file = File(path)
    if (!file.exists()) return emptyList()
    val out = mutableListOf<Bitmap>()
    runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { page ->
                        val targetW = 1240
                        val scale = targetW.toFloat() / page.width.coerceAtLeast(1)
                        val w = targetW
                        val h = (page.height * scale).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        out.add(bmp)
                    }
                }
            }
        }
    }
    return out
}

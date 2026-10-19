package com.naury.trubbi.feature.journey

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.RectF
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.naury.trubbi.R
import com.naury.trubbi.data.local.JourneyStopEntity
import com.naury.trubbi.data.local.RecordWithMedia
import com.naury.trubbi.data.local.RoutePointEntity
import com.naury.trubbi.domain.model.RecordType
import com.naury.trubbi.ui.common.KakaoMapView
import com.naury.trubbi.ui.common.MapControls
import com.naury.trubbi.ui.common.MapHandle
import com.naury.trubbi.ui.common.MapMarker
import com.naury.trubbi.ui.common.MapPoint
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.pressable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 여정 지도 — 경로 선, 정차지 핀, 그리고 찍은 사진이 그대로 붙은 기록 마커.
 *
 * 기록 마커에는 아이콘 대신 촬영한 사진의 썸네일과 촬영 시각을 함께 표시한다.
 *
 * 카드 안에서는 [interactive] 를 false 로 두어 지도 조작을 막고 [onExpand] 로 전체화면을 연다.
 * 스크롤 중 지도가 제스처를 가로채는 것을 방지한다.
 */
@Composable
fun JourneyMapCard(
    stops: List<JourneyStopEntity>,
    routePoints: List<RoutePointEntity>,
    records: List<RecordWithMedia>,
    onRecordClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    onExpand: (() -> Unit)? = null,
    issues: List<com.naury.trubbi.data.local.IssueEntity> = emptyList(),
) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary.toArgb()
    val onPrimary = MaterialTheme.colorScheme.onPrimary.toArgb()

    val route = routePoints.map { MapPoint(it.latitude, it.longitude) }
    val handle = remember { MapHandle() }
    // 현재 작업 중인 정차지(첫 미완료) — 마커를 강조한다.
    val currentStopId = stops.firstOrNull { it.completedAt == null }?.stopId

    // 마커는 메인 스레드 밖에서 만든다. 썸네일을 전부 디코딩해야 해서 화면이 멈춘다.
    val markers by produceState(initialValue = emptyList<MapMarker>(), records, stops, primary, onPrimary, currentStopId, issues) {
        value = withContext(Dispatchers.IO) {
            buildMarkers(context, stops, records, primary, onPrimary, currentStopId, issues)
        }
    }

    Box(modifier) {
        KakaoMapView(
            markers = markers,
            route = route,
            routeColor = primary,
            handle = handle,
            onMarkerClick = { id -> if (!id.startsWith("stop:") && !id.startsWith("issue:")) onRecordClick(id) },
            modifier = Modifier.fillMaxSize(),
        )
        // 티맵식 떠 있는 조작 — 확대/축소 + 경로 전체 보기(현위치/마커 복귀). 전체화면(interactive)에서 노출.
        if (interactive) {
            val allPoints = route + stops.mapNotNull { s ->
                val la = s.latitude
                val ln = s.longitude
                if (la != null && ln != null) MapPoint(la, ln) else null
            }
            MapControls(
                handle = handle,
                onRecenter = { handle.fit(allPoints) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 14.dp, bottom = 28.dp),
            )
        }
        // 작은 카드 지도도 두 손가락으로 확대/축소 가능. 전체화면은 우상단 버튼으로.
        if (onExpand != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    .pressable(onClick = onExpand)
                    .padding(8.dp),
            ) {
                Icon(
                    AppIcons.Fullscreen,
                    contentDescription = stringResource(R.string.action_fullscreen),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private val markerTimeFmt = SimpleDateFormat("HH:mm", Locale.KOREA)

private suspend fun buildMarkers(
    context: Context,
    stops: List<JourneyStopEntity>,
    records: List<RecordWithMedia>,
    primary: Int,
    onPrimary: Int,
    currentStopId: String?,
    issues: List<com.naury.trubbi.data.local.IssueEntity>,
): List<MapMarker> {
    val out = ArrayList<MapMarker>()
    // Stop pins — 물방울 핀. 현재 정차지는 크게/강조.
    stops.forEach { stop ->
        val lat = stop.latitude ?: return@forEach
        val lng = stop.longitude ?: return@forEach
        out += MapMarker(
            id = "stop:${stop.stopId}",
            point = MapPoint(lat, lng),
            bitmap = stopPin(
                text = context.getString(stop.kindEnum.shortRes),
                done = stop.completedAt != null,
                current = stop.stopId == currentStopId,
                primary = primary,
            ),
        )
    }
    // 파손·지연 같은 문제가 생긴 자리에 빨간 경고 마커를 찍는다.
    issues.forEach { issue ->
        val lat = issue.latitude ?: return@forEach
        val lng = issue.longitude ?: return@forEach
        out += MapMarker(id = "issue:${issue.issueId}", point = MapPoint(lat, lng), bitmap = issuePin())
    }
    // 기록 마커 — 찍은 사진 썸네일(없으면 종류 아이콘)과 시각.
    records.take(80).forEach { rec ->
        val r = rec.record
        val lat = r.latitude ?: return@forEach
        val lng = r.longitude ?: return@forEach
        val time = markerTimeFmt.format(Date(r.createdAt))
        val type = RecordType.fromName(r.type)
        val thumbUri = rec.primaryThumb
        val thumb: Bitmap? = if (thumbUri != null) {
            runCatching {
                context.imageLoader.execute(
                    ImageRequest.Builder(context).data(thumbUri).size(140).allowHardware(false).build(),
                ).image?.toBitmap()
            }.getOrNull()
        } else {
            null
        }
        val bmp = if (thumb != null) {
            photoMarker(thumb, time, isVideo = rec.hasVideo, primary = primary)
        } else {
            iconMarker(
                label = context.getString(recordMarkerLabelRes(type)),
                time = time,
                color = primary,
                textColor = onPrimary,
            )
        }
        out += MapMarker(id = r.recordId, point = MapPoint(lat, lng), bitmap = bmp)
    }
    return out
}

// ── 마커 비트맵 ───────────────────────────────────────────────────────────────

/** 이미지가 없는 기록을 지도에 찍을 때 쓰는 한 글자 라벨. */
@StringRes
private fun recordMarkerLabelRes(type: RecordType): Int = when (type) {
    RecordType.BARCODE -> R.string.marker_barcode_short
    RecordType.MEMO -> R.string.marker_memo_short
    RecordType.DOCUMENT -> R.string.marker_document_short
    else -> R.string.marker_etc_short
}

/** Photo marker: 부드러운 그림자가 있는 둥근 썸네일 카드 + 아래 시간 알약. */
private fun photoMarker(src: Bitmap, time: String, isVideo: Boolean, primary: Int): Bitmap {
    val pad = 12
    val thumb = 120
    val cardW = thumb + 14
    val cardH = thumb + 14
    val pillH = 40
    val gap = 8
    val w = cardW + pad * 2
    val h = pad + cardH + gap + pillH + pad
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val cardLeft = pad.toFloat()
    val cardTop = pad.toFloat()

    paint.color = AColor.WHITE
    paint.setShadowLayer(12f, 0f, 5f, AColor.argb(85, 0, 0, 0))
    canvas.drawRoundRect(RectF(cardLeft, cardTop, cardLeft + cardW, cardTop + cardH), 26f, 26f, paint)
    paint.clearShadowLayer()

    // 가운데를 잘라 넣은 썸네일.
    val thumbLeft = cardLeft + 7f
    val thumbTop = cardTop + 7f
    val scale = thumb / minOf(src.width, src.height).toFloat()
    val scaled = Bitmap.createScaledBitmap(
        src,
        (src.width * scale).toInt().coerceAtLeast(1),
        (src.height * scale).toInt().coerceAtLeast(1),
        true,
    )
    val shader = android.graphics.BitmapShader(scaled, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
    val m = android.graphics.Matrix()
    m.postTranslate(thumbLeft + thumb / 2f - scaled.width / 2f, thumbTop + thumb / 2f - scaled.height / 2f)
    shader.setLocalMatrix(m)
    paint.shader = shader
    canvas.drawRoundRect(RectF(thumbLeft, thumbTop, thumbLeft + thumb, thumbTop + thumb), 18f, 18f, paint)
    paint.shader = null

    if (isVideo) {
        paint.color = AColor.argb(110, 0, 0, 0)
        canvas.drawRoundRect(RectF(thumbLeft, thumbTop, thumbLeft + thumb, thumbTop + thumb), 18f, 18f, paint)
        paint.color = AColor.WHITE
        val cx = thumbLeft + thumb / 2f
        val cy = thumbTop + thumb / 2f
        val tri = android.graphics.Path().apply {
            moveTo(cx - 11, cy - 15)
            lineTo(cx - 11, cy + 15)
            lineTo(cx + 17, cy)
            close()
        }
        canvas.drawPath(tri, paint)
    }

    // 카드 아래 시각 알약.
    val cx = w / 2f
    val pillTop = cardTop + cardH + gap
    val pill = RectF(cx - 38, pillTop, cx + 38, pillTop + pillH)
    paint.setShadowLayer(6f, 0f, 2f, AColor.argb(70, 0, 0, 0))
    paint.color = primary
    canvas.drawRoundRect(pill, pillH / 2f, pillH / 2f, paint)
    paint.clearShadowLayer()
    paint.color = AColor.WHITE
    paint.textSize = 26f
    paint.isFakeBoldText = true
    paint.textAlign = Paint.Align.CENTER
    canvas.drawText(time, cx, pill.centerY() - (paint.descent() + paint.ascent()) / 2f, paint)
    return out
}

/** Icon marker for non-photo records (메모/바코드): 그림자 있는 컬러 원 + 시간 알약. */
private fun iconMarker(label: String, time: String, color: Int, textColor: Int): Bitmap {
    val pad = 12
    val circle = 84
    val pillH = 38
    val gap = 6
    val w = circle + pad * 2
    val h = pad + circle + gap + pillH + pad
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val cx = w / 2f
    val r = circle / 2f
    val cy = pad + r
    paint.color = AColor.WHITE
    paint.setShadowLayer(11f, 0f, 4f, AColor.argb(85, 0, 0, 0))
    canvas.drawCircle(cx, cy, r, paint)
    paint.clearShadowLayer()
    paint.color = color
    canvas.drawCircle(cx, cy, r - 5, paint)
    paint.color = textColor
    paint.textSize = 34f
    paint.isFakeBoldText = true
    paint.textAlign = Paint.Align.CENTER
    canvas.drawText(label, cx, cy - (paint.descent() + paint.ascent()) / 2f, paint)

    val pillTop = pad + circle + gap
    val pill = RectF(cx - 34, pillTop.toFloat(), cx + 34, pillTop + pillH.toFloat())
    paint.color = color
    canvas.drawRoundRect(pill, pillH / 2f, pillH / 2f, paint)
    paint.color = AColor.WHITE
    paint.textSize = 24f
    canvas.drawText(time, cx, pill.centerY() - (paint.descent() + paint.ascent()) / 2f, paint)
    return out
}

/** Issue pin — 빨간 경고 물방울(! 표시). 파손/지연 등 문제 위치 표시. */
private fun issuePin(): Bitmap {
    val red = AColor.parseColor("#E5484D")
    val w = 92
    val h = 118
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val cx = w / 2f
    val r = w / 2f - 10f
    val cy = r + 8f
    val tipY = h - 8f
    paint.color = AColor.argb(60, 0, 0, 0)
    canvas.drawOval(cx - r * 0.5f, tipY - 12f, cx + r * 0.5f, tipY - 2f, paint)
    fun tear(radius: Float, color: Int) {
        paint.color = color
        val path = android.graphics.Path().apply {
            addCircle(cx, cy, radius, android.graphics.Path.Direction.CW)
            moveTo(cx - radius * 0.62f, cy + radius * 0.55f)
            lineTo(cx + radius * 0.62f, cy + radius * 0.55f)
            lineTo(cx, tipY)
            close()
        }
        canvas.drawPath(path, paint)
    }
    paint.setShadowLayer(10f, 0f, 4f, AColor.argb(80, 0, 0, 0))
    tear(r + 5f, AColor.WHITE)
    paint.clearShadowLayer()
    tear(r, red)
    paint.color = AColor.WHITE
    paint.textSize = w * 0.5f
    paint.textAlign = Paint.Align.CENTER
    paint.isFakeBoldText = true
    canvas.drawText("!", cx, cy - (paint.descent() + paint.ascent()) / 2f, paint)
    return out
}

/**
 * 정차지 핀. 상차·하차·출발을 한 글자로 구분해 표시한다.
 * 현재 정차지=주황 강조+크게, 완료=회색, 예정=잉크색.
 */
private fun stopPin(text: String, done: Boolean, current: Boolean, primary: Int): Bitmap {
    val fill = when {
        current -> primary
        done -> AColor.parseColor("#9AA0A6")
        else -> AColor.parseColor("#33373D")
    }
    val scale = if (current) 1.2f else 1f
    val w = (96 * scale).toInt()
    val h = (124 * scale).toInt()
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val cx = w / 2f
    val r = w / 2f - 10f
    val cy = r + 8f
    val tipY = h - 8f

    paint.color = AColor.argb(60, 0, 0, 0)
    canvas.drawOval(cx - r * 0.5f, tipY - 12f, cx + r * 0.5f, tipY - 2f, paint)

    fun drawTeardrop(radius: Float, color: Int) {
        paint.color = color
        val path = android.graphics.Path().apply {
            addCircle(cx, cy, radius, android.graphics.Path.Direction.CW)
            moveTo(cx - radius * 0.62f, cy + radius * 0.55f)
            lineTo(cx + radius * 0.62f, cy + radius * 0.55f)
            lineTo(cx, tipY)
            close()
        }
        canvas.drawPath(path, paint)
    }
    paint.setShadowLayer(10f, 0f, 4f, AColor.argb(80, 0, 0, 0))
    drawTeardrop(r + 5f, AColor.WHITE) // 흰 외곽선 + 그림자
    paint.clearShadowLayer()
    drawTeardrop(r, fill)

    // 완료된 정차지도 라벨(상/하/출)은 그대로 두고 강조 상태로만 구분한다.
    paint.color = AColor.WHITE
    paint.textSize = w * 0.42f
    paint.textAlign = Paint.Align.CENTER
    paint.isFakeBoldText = true
    canvas.drawText(text, cx, cy - (paint.descent() + paint.ascent()) / 2f, paint)
    return out
}

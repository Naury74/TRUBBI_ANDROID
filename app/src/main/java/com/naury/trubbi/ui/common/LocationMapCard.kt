package com.naury.trubbi.ui.common

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naury.trubbi.R
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.pressable
import com.naury.trubbi.ui.theme.LocalTrubbiColors
import java.util.Locale

/**
 * 저장된 기록의 위치를 조작 가능한 지도로 보여 준다.
 *
 * 두 손가락 확대·이동이 되고 떠 있는 줌/현위치 버튼이 붙는다. 아래 줄을 누르면
 * 기기에 설치된 지도앱으로 넘긴다.
 *
 * @param address 있으면 좌표 대신 주소를 보여 준다
 */
@Composable
fun LocationMapCard(
    latitude: Double,
    longitude: Double,
    address: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = LocalTrubbiColors.current
    val lat = String.format(Locale.US, "%.6f", latitude)
    val lng = String.format(Locale.US, "%.6f", longitude)
    val label = address ?: stringResource(R.string.map_location_fallback)

    val pinColor = MaterialTheme.colorScheme.primary.toArgb()
    val pin = remember(pinColor) { markerPin(pinColor) }
    val handle = remember { MapHandle() }
    val markers = remember(latitude, longitude, pin) {
        listOf(MapMarker("loc", MapPoint(latitude, longitude), pin))
    }

    // geo: 스킴을 받는 앱이 하나도 없는 기기가 있어 웹 지도로 한 번 더 받아 준다.
    fun openMaps() {
        val geo = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, geo)) }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng")),
                )
            }
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            KakaoMapView(
                markers = markers,
                route = emptyList(),
                focus = MapFocus.Point(latitude, longitude, 16),
                handle = handle,
                modifier = Modifier.fillMaxSize(),
            )
            MapControls(
                handle = handle,
                onRecenter = { handle.moveTo(latitude, longitude, 16) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 12.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().pressable { openMaps() }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    address ?: "$lat, $lng",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.map_open_external),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Icon(AppIcons.KeyboardArrowRight, contentDescription = null, tint = colors.secondaryLabel, modifier = Modifier.size(20.dp))
        }
    }
}

/** 흰 테두리를 두른 물방울 핀 비트맵. */
private fun markerPin(color: Int): Bitmap {
    val w = 84
    val h = 108
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val cx = w / 2f
    val r = w / 2f - 9f
    val cy = r + 7f
    val tipY = h - 7f
    paint.color = android.graphics.Color.argb(60, 0, 0, 0)
    canvas.drawOval(cx - r * 0.5f, tipY - 11f, cx + r * 0.5f, tipY - 2f, paint)
    fun tear(radius: Float, col: Int) {
        paint.color = col
        val path = android.graphics.Path().apply {
            addCircle(cx, cy, radius, android.graphics.Path.Direction.CW)
            moveTo(cx - radius * 0.62f, cy + radius * 0.55f)
            lineTo(cx + radius * 0.62f, cy + radius * 0.55f)
            lineTo(cx, tipY)
            close()
        }
        canvas.drawPath(path, paint)
    }
    tear(r + 4f, android.graphics.Color.WHITE)
    tear(r, color)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(cx, cy, r * 0.42f, paint)
    return out
}

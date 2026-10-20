package com.naury.trubbi.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.widget.RemoteViews
import com.naury.trubbi.R
import com.naury.trubbi.core.i18n.label
import com.naury.trubbi.data.i18n.displayName
import com.naury.trubbi.data.i18n.routeLabel
import com.naury.trubbi.data.local.JourneyWithStops
import com.naury.trubbi.data.local.RoutePointEntity
import com.naury.trubbi.data.repo.JourneyRepository
import dagger.hilt.android.EntryPointAccessors
import kotlin.math.cos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 주행 경로를 보여 주는 큰 지도 위젯.
 *
 * RemoteViews 에는 살아 있는 지도 타일을 띄울 수 없어, GPS 자취와 정차지·현재 위치를 캔버스로
 * 직접 그려 비트맵으로 넣는다. 지도 배경은 없지만 오늘 어디를 어떻게 돌았는지는 한눈에 보인다.
 */
class JourneyMapWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = repoOf(context)
                val active = runCatching { repo.activeJourneyOnce() }.getOrNull()
                val route = if (active != null) {
                    runCatching { repo.routePointsOnce(active.journey.journeyId) }.getOrNull().orEmpty()
                } else {
                    emptyList()
                }
                ids.forEach { id -> manager.updateAppWidget(id, buildViews(context, active, route)) }
            } finally {
                pending.finish()
            }
        }
    }

    private fun buildViews(
        context: Context,
        active: JourneyWithStops?,
        route: List<RoutePointEntity>,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_journey_map)
        if (active == null) {
            views.setViewVisibility(R.id.map_image, View.GONE)
            views.setTextViewText(R.id.map_title, context.getString(R.string.widget_no_active_journey))
            views.setTextViewText(R.id.map_sub, context.getString(R.string.widget_tap_to_open))
            views.setOnClickPendingIntent(R.id.map_root, openAppIntent(context))
            return views
        }
        val currentStop = active.currentStop
        val bitmap = runCatching {
            renderMap(
                route = route.map { it.latitude to it.longitude },
                stops = active.orderedStops.mapNotNull { stop ->
                    val lat = stop.latitude ?: return@mapNotNull null
                    val lng = stop.longitude ?: return@mapNotNull null
                    StopDot(
                        lat = lat,
                        lng = lng,
                        label = context.getString(stop.kindEnum.shortRes),
                        done = stop.completedAt != null,
                        current = stop.stopId == currentStop?.stopId,
                    )
                },
                current = route.lastOrNull()?.let { it.latitude to it.longitude },
            )
        }.getOrNull()
        if (bitmap != null) {
            views.setImageViewBitmap(R.id.map_image, bitmap)
            views.setViewVisibility(R.id.map_image, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.map_image, View.GONE)
        }
        views.setTextViewText(R.id.map_title, active.routeLabel(context))
        views.setTextViewText(
            R.id.map_sub,
            currentStop?.let { "${it.displayName(context)} · ${it.kindEnum.label(context)}" }
                ?: context.getString(R.string.widget_all_stops_done),
        )
        views.setOnClickPendingIntent(R.id.map_root, deepLinkIntent(context, "trubbi://journey", 4200))
        return views
    }

    private fun openAppIntent(context: Context) =
        deepLinkIntent(context, "trubbi://journey", 4201)

    private data class StopDot(
        val lat: Double,
        val lng: Double,
        val label: String,
        val done: Boolean,
        val current: Boolean,
    )

    /**
     * 경로·정차지·현재 위치를 등거리 투영으로 그린다.
     *
     * 위도에 따라 경도 간격이 좁아지므로 cos 보정을 넣어야 남북으로 긴 경로가 찌그러지지 않는다.
     */
    private fun renderMap(
        route: List<Pair<Double, Double>>,
        stops: List<StopDot>,
        current: Pair<Double, Double>?,
    ): Bitmap {
        val w = 800
        val h = 460
        val pad = 56f
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.parseColor("#EEF1F4"))

        val all = route + stops.map { it.lat to it.lng } + listOfNotNull(current)
        if (all.isEmpty()) return bmp

        var minLat = all.minOf { it.first }
        var maxLat = all.maxOf { it.first }
        var minLng = all.minOf { it.second }
        var maxLng = all.maxOf { it.second }
        // 점이 하나뿐이면 범위가 0이라 나눗셈이 깨진다. 최소 범위를 만들어 가운데 찍는다.
        if (maxLat - minLat < 1e-6) {
            minLat -= 0.002
            maxLat += 0.002
        }
        if (maxLng - minLng < 1e-6) {
            minLng -= 0.002
            maxLng += 0.002
        }
        val midLat = (minLat + maxLat) / 2.0
        val lngK = cos(Math.toRadians(midLat)).coerceAtLeast(0.1)
        val spanLat = (maxLat - minLat)
        val spanLng = (maxLng - minLng) * lngK
        val scale = minOf((w - 2 * pad) / spanLng, (h - 2 * pad) / spanLat).toFloat()
        val drawW = (spanLng * scale).toFloat()
        val drawH = (spanLat * scale).toFloat()
        val offX = (w - drawW) / 2f
        val offY = (h - drawH) / 2f
        fun px(lat: Double, lng: Double): Pair<Float, Float> {
            val x = offX + ((lng - minLng) * lngK * scale).toFloat()
            // 위도는 위로 갈수록 커지므로 y 를 뒤집는다.
            val y = offY + ((maxLat - lat) * scale).toFloat()
            return x to y
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 아직 GPS 자취가 없으면(막 시작한 여정) 정차지를 직선으로 이어 형태만 보여 준다.
        val line = when {
            route.size >= 2 -> route.map { px(it.first, it.second) }
            stops.size >= 2 -> stops.map { px(it.lat, it.lng) }
            else -> emptyList()
        }
        if (line.size >= 2) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 9f
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            paint.color = com.naury.trubbi.core.ui.BrandPalette.PRIMARY
            val path = Path().apply {
                moveTo(line[0].first, line[0].second)
                line.drop(1).forEach { lineTo(it.first, it.second) }
            }
            canvas.drawPath(path, paint)
        }

        stops.forEach { stop ->
            val (x, y) = px(stop.lat, stop.lng)
            val fill = when {
                stop.current -> com.naury.trubbi.core.ui.BrandPalette.PRIMARY
                stop.done -> Color.parseColor("#9AA0A6")
                else -> Color.parseColor("#33373D")
            }
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            canvas.drawCircle(x, y, 22f, paint)
            paint.color = fill
            canvas.drawCircle(x, y, 18f, paint)
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 22f
            paint.isFakeBoldText = true
            canvas.drawText(stop.label, x, y - (paint.descent() + paint.ascent()) / 2f, paint)
        }

        // 현재 위치는 마지막 자취 점으로 본다.
        current?.let {
            val (x, y) = px(it.first, it.second)
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#332D6CDF")
            canvas.drawCircle(x, y, 26f, paint)
            paint.color = Color.WHITE
            canvas.drawCircle(x, y, 14f, paint)
            paint.color = Color.parseColor("#2D6CDF")
            canvas.drawCircle(x, y, 10f, paint)
        }
        return bmp
    }

    companion object {
        private fun repoOf(context: Context): JourneyRepository =
            EntryPointAccessors
                .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
                .journeyRepository()

        /** 여정·경로·위치가 바뀔 때마다 호출해 지도 위젯을 다시 그린다. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, JourneyMapWidget::class.java))
            if (ids.isEmpty()) return
            val intent = Intent(context, JourneyMapWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }
}

package com.naury.trubbi.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.naury.trubbi.R

/**
 * 홈 화면 바코드 스캔 위젯.
 *
 * 탭하면 홈 화면을 거치지 않고 스캔 화면으로 바로 진입한다.
 */
class BarcodeScanWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_barcode_scan)
            views.setOnClickPendingIntent(
                R.id.widget_root,
                deepLinkIntent(context, "trubbi://scan?type=barcode&purpose=WAYBILL", 1002),
            )
            manager.updateAppWidget(id, views)
        }
    }
}

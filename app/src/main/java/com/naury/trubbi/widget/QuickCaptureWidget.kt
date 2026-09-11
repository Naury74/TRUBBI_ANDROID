package com.naury.trubbi.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.naury.trubbi.MainActivity
import com.naury.trubbi.R

/**
 * 홈 화면 빠른 촬영 위젯 — 촬영·문서·바코드·메모로 한 번에 들어간다.
 *
 * 앱을 열고 화면을 찾아 들어가는 사이에 상황이 지나가 버리는 일이 많았다.
 * 위젯에서 곧장 카메라가 뜨면 그 몇 초가 사라진다.
 */
class QuickCaptureWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_quick_capture)
            views.setOnClickPendingIntent(
                R.id.widget_action_camera,
                deepLinkIntent(context, "trubbi://capture?purpose=ETC", 1001),
            )
            views.setOnClickPendingIntent(
                R.id.widget_action_document,
                deepLinkIntent(context, "trubbi://capture?purpose=ETC&mode=document", 1002),
            )
            views.setOnClickPendingIntent(
                R.id.widget_action_barcode,
                deepLinkIntent(context, "trubbi://scan?purpose=WAYBILL", 1003),
            )
            views.setOnClickPendingIntent(
                R.id.widget_action_memo,
                deepLinkIntent(context, "trubbi://memo", 1004),
            )
            manager.updateAppWidget(id, views)
        }
    }
}

internal fun deepLinkIntent(context: Context, uri: String, requestCode: Int): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = Uri.parse(uri)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    return PendingIntent.getActivity(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

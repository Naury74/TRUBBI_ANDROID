package com.naury.trubbi.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.naury.trubbi.R
import com.naury.trubbi.core.barcode.BarcodeRenderer
import com.naury.trubbi.data.local.RecordEntity
import com.naury.trubbi.data.local.TrubbiDao
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 저장해 둔 바코드를 홈 화면에 그대로 띄우는 위젯.
 *
 * 앱을 열지 않고 홈 화면에서 바로 스캔시킬 수 있다.
 * 표시할 바코드는 위젯 인스턴스마다 지정하며, 지정하지 않으면 가장 최근 항목을 쓴다.
 */
class BarcodeViewWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = daoOf(context)
                ids.forEach { id ->
                    val record = resolveRecord(context, dao, id)
                    manager.updateAppWidget(id, buildViews(context, id, record))
                }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { BarcodeWidgetPrefs.remove(context, it) }
    }

    private suspend fun resolveRecord(context: Context, dao: TrubbiDao, widgetId: Int): RecordEntity? {
        val pinnedId = BarcodeWidgetPrefs.getRecordId(context, widgetId)
        val pinned = pinnedId?.let { runCatching { dao.recordOnce(it) }.getOrNull() }
        if (pinned != null && !pinned.isDeleted && !pinned.barcodeValue.isNullOrBlank()) return pinned
        return runCatching { dao.latestBarcode() }.getOrNull()
    }

    private fun buildViews(context: Context, widgetId: Int, record: RecordEntity?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_barcode_view)
        val value = record?.barcodeValue
        if (record != null && !value.isNullOrBlank()) {
            val twoD = BarcodeRenderer.isTwoDimensional(record.barcodeFormat)
            val bmp = BarcodeRenderer.render(
                value = value,
                formatName = record.barcodeFormat,
                widthPx = if (twoD) 420 else 640,
                heightPx = if (twoD) 420 else 220,
            )
            if (bmp != null) views.setImageViewBitmap(R.id.barcode_image, bmp)
            views.setTextViewText(R.id.barcode_title, record.title)
            views.setTextViewText(R.id.barcode_value, value)
            views.setOnClickPendingIntent(
                R.id.widget_root,
                deepLinkIntent(context, "trubbi://record?recordId=${record.recordId}", 3000 + widgetId),
            )
        } else {
            views.setTextViewText(R.id.barcode_title, context.getString(R.string.barcode_widget_none))
            views.setTextViewText(R.id.barcode_value, context.getString(R.string.barcode_widget_tap_scan))
            views.setOnClickPendingIntent(
                R.id.widget_root,
                deepLinkIntent(context, "trubbi://scan?type=barcode&purpose=WAYBILL", 3000 + widgetId),
            )
        }
        return views
    }

    companion object {
        private fun daoOf(context: Context): TrubbiDao =
            EntryPointAccessors
                .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
                .trubbiDao()

        /** 모든 위젯을 갱신한다. 바코드를 추가·삭제한 뒤 호출한다. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BarcodeViewWidget::class.java))
            if (ids.isEmpty()) return
            val intent = Intent(context, BarcodeViewWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }

        /** 위젯 하나만 갱신한다. 그 위젯의 바코드를 새로 골랐을 때. */
        fun updateOne(context: Context, widgetId: Int) {
            val intent = Intent(context, BarcodeViewWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
            }
            context.sendBroadcast(intent)
        }
    }
}

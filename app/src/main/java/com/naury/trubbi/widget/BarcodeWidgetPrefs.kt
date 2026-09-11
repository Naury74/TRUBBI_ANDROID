package com.naury.trubbi.widget

import android.content.Context

/**
 * 위젯 하나하나가 어떤 바코드를 보여 줄지 기억한다.
 *
 * 위젯은 여러 개를 놓을 수 있어서 설정을 한 곳에 몰아 둘 수 없다. 안드로이드가 주는
 * 위젯 id 를 키로 삼아 SharedPreferences 에 따로 적어 둔다.
 */
object BarcodeWidgetPrefs {
    private const val PREFS = "barcode_widget_prefs"
    private fun key(widgetId: Int) = "widget_$widgetId"

    fun setRecordId(context: Context, widgetId: Int, recordId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(key(widgetId), recordId).apply()
    }

    fun getRecordId(context: Context, widgetId: Int): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(widgetId), null)

    fun remove(context: Context, widgetId: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(key(widgetId)).apply()
    }
}

package com.naury.trubbi.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.naury.trubbi.MainActivity
import com.naury.trubbi.R
import com.naury.trubbi.core.i18n.label
import com.naury.trubbi.data.i18n.displayName
import com.naury.trubbi.data.i18n.routeLabel
import com.naury.trubbi.data.local.JourneyWithStops
import com.naury.trubbi.data.repo.JourneyRepository
import com.naury.trubbi.domain.model.StopPhase
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 진행 중인 여정을 바탕화면 카드로 보여 주는 위젯.
 *
 * 앱 안의 여정 카드와 같은 정보(경로·현재 정차지·단계)를 싣고, 단계 넘기기·촬영·열기 버튼을 붙인다.
 * 여정이 바뀔 때마다 리포지토리가 [updateAll] 을 불러 실시간으로 맞춘다.
 */
class JourneyWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val active = runCatching { repoOf(context).activeJourneyOnce() }.getOrNull()
                ids.forEach { id -> manager.updateAppWidget(id, buildViews(context, active)) }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_NEXT) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repo = repoOf(context)
                    repo.activeJourneyOnce()?.currentStop?.let { runCatching { repo.advanceStop(it) } }
                    updateAll(context)
                } finally {
                    pending.finish()
                }
            }
        } else {
            super.onReceive(context, intent)
        }
    }

    private fun buildViews(context: Context, active: JourneyWithStops?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_journey)
        if (active == null) {
            views.setTextViewText(R.id.journey_route, context.getString(R.string.widget_no_active_journey))
            views.setTextViewText(R.id.journey_distance, "")
            views.setTextViewText(R.id.journey_stop, context.getString(R.string.widget_start_journey))
            views.setTextViewText(R.id.journey_phase, context.getString(R.string.widget_tap_to_open))
            views.setViewVisibility(R.id.journey_buttons, View.GONE)
            views.setOnClickPendingIntent(R.id.widget_journey_root, openAppIntent(context))
            return views
        }
        val current = active.currentStop
        views.setTextViewText(R.id.journey_route, active.routeLabel(context))
        views.setTextViewText(R.id.journey_distance, formatKm(active.journey.totalDistanceM))
        if (current != null) {
            views.setTextViewText(R.id.journey_stop, current.displayName(context))
            views.setTextViewText(
                R.id.journey_phase,
                "${current.kindEnum.label(context)} · ${current.phase.label(context)}",
            )
            views.setTextViewText(R.id.btn_next, context.getString(nextActionRes(current.phase)))
            views.setViewVisibility(R.id.journey_buttons, View.VISIBLE)
            views.setOnClickPendingIntent(R.id.btn_next, nextActionIntent(context))
        } else {
            views.setTextViewText(R.id.journey_stop, context.getString(R.string.widget_all_stops_done))
            views.setTextViewText(R.id.journey_phase, context.getString(R.string.widget_finish_prompt))
            views.setTextViewText(R.id.btn_next, context.getString(R.string.action_complete_journey))
            views.setViewVisibility(R.id.journey_buttons, View.VISIBLE)
            views.setOnClickPendingIntent(R.id.btn_next, deepLinkIntent(context, "trubbi://journey", 4104))
        }
        views.setOnClickPendingIntent(
            R.id.btn_camera,
            deepLinkIntent(context, "trubbi://capture?journey=1&purpose=ETC", 4101),
        )
        views.setOnClickPendingIntent(R.id.btn_open, deepLinkIntent(context, "trubbi://journey", 4102))
        views.setOnClickPendingIntent(R.id.widget_journey_root, deepLinkIntent(context, "trubbi://journey", 4103))
        return views
    }

    private fun nextActionIntent(context: Context): PendingIntent {
        val intent = Intent(context, JourneyWidget::class.java).apply { action = ACTION_NEXT }
        return PendingIntent.getBroadcast(
            context,
            4100,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            4099,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        private const val ACTION_NEXT = "com.naury.trubbi.widget.ACTION_JOURNEY_NEXT"

        private fun repoOf(context: Context): JourneyRepository =
            EntryPointAccessors
                .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
                .journeyRepository()

        private fun formatKm(meters: Long): String =
            if (meters <= 0) "" else "%.1fkm".format(meters / 1000.0)

        /** 버튼에 적을 문구 — 지금 누르면 일어날 다음 동작. */
        private fun nextActionRes(phase: StopPhase): Int = when (phase) {
            StopPhase.MOVING -> R.string.notif_action_arrive
            StopPhase.WAITING -> R.string.action_start_work
            StopPhase.WORKING -> R.string.action_finish_work
            StopPhase.DONE -> R.string.action_next
        }

        /** 여정이 바뀔 때마다 호출해 바탕화면 카드를 맞춘다. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, JourneyWidget::class.java))
            if (ids.isEmpty()) return
            val intent = Intent(context, JourneyWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }
}

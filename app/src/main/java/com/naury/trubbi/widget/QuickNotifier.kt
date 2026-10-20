package com.naury.trubbi.widget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.naury.trubbi.MainActivity
import com.naury.trubbi.R
import com.naury.trubbi.core.notification.NotificationIcons
import com.naury.trubbi.domain.model.RecordType

/**
 * 알림창에 상주하는 빠른 기록 알림 — 촬영·문서·바코드·메모를 한 번에 연다.
 *
 * 플로팅 버블과 목적은 같지만 오버레이 권한이 필요 없다. 다른 앱 위에 그리는 권한은
 * 설정에서 따로 허용해야 하고 거부하는 사용자도 많아, 알림 권한만으로 되는 쪽을 함께 뒀다.
 */
object QuickNotifier {
    private const val CHANNEL = "trubbi_quick_record"
    private const val NOTIF_ID = 4720

    fun show(context: Context) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.quick_notif_channel), NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                    description = context.getString(R.string.quick_notif_channel_desc)
                },
            )
        }
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setLargeIcon(NotificationIcons.glyph(context, R.drawable.ic_stat_camera))
            .setColor(com.naury.trubbi.core.ui.BrandPalette.PRIMARY)
            .setContentTitle(context.getString(R.string.quick_notif_channel))
            .setContentText(context.getString(R.string.quick_notif_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(action(context, "", 0))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, context.getString(R.string.notif_action_capture), action(context, "trubbi://capture?purpose=ETC", 1))
            .addAction(0, context.getString(RecordType.DOCUMENT.labelRes), action(context, "trubbi://capture?purpose=ETC&mode=document", 2))
            .addAction(0, context.getString(RecordType.BARCODE.labelRes), action(context, "trubbi://scan?purpose=WAYBILL", 3))
            .addAction(0, context.getString(RecordType.MEMO.labelRes), action(context, "trubbi://memo", 4))
            .build()
        runCatching { mgr.notify(NOTIF_ID, n) }
    }

    fun hide(context: Context) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { mgr.cancel(NOTIF_ID) }
    }

    private fun action(context: Context, deepLink: String, code: Int): PendingIntent {
        val intent = if (deepLink.isBlank()) {
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent(context, MainActivity::class.java)
        } else {
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse(deepLink)
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            code,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

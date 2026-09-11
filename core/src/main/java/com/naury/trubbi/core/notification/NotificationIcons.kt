package com.naury.trubbi.core.notification

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat

/**
 * 알림 오른쪽에 들어가는 큰 아이콘을 만든다.
 */
object NotificationIcons {
    /**
     * 아이콘을 원판 가운데에 다시 그려 알림용 비트맵을 만든다.
     *
     * 원본 드로어블을 그대로 넘기면 시스템의 원형 마스크에 글리프가 잘린다.
     *
     * 비트맵을 못 만들면 null 을 돌려준다. 호출부는 큰 아이콘 없이 알림을 띄우면 되고,
     * 아이콘 하나 때문에 알림 자체가 안 뜨는 편이 더 나쁘다.
     */
    fun glyph(
        context: Context,
        iconRes: Int,
        background: Int = 0xFFF5871F.toInt(), // 브랜드 앰버 디스크
        foreground: Int = 0xFFFFFFFF.toInt(), // 흰색 글리프(또렷하게)
    ): Bitmap? = runCatching {
        val density = context.resources.displayMetrics.density
        val size = (112 * density).toInt().coerceIn(112, 288)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, disc)
        val glyph = ContextCompat.getDrawable(context, iconRes)?.mutate()
        if (glyph != null) {
            DrawableCompat.setTint(glyph, foreground)
            // 글리프가 원판의 약 64% 를 차지하게 둔다.
            val pad = (size * 0.18f).toInt()
            glyph.setBounds(pad, pad, size - pad, size - pad)
            glyph.draw(canvas)
        }
        bmp
    }.getOrNull()
}

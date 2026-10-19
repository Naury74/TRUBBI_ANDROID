package com.naury.trubbi.ui.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

/**
 * 물방울 모양 지도 핀에 짧은 글자(번호·"출발"·"도착" 등)를 넣어 그린다.
 *
 * 흰 테두리를 둘러 어떤 지도 색 위에서도 읽힌다. 글자는 두 자 안팎을 전제로 크기를 정한다.
 *
 * @param label 핀 머리에 넣을 글자
 * @param fill 핀 색(ARGB)
 * @param textColor 글자 색(ARGB)
 */
fun pinBitmap(label: String, fill: Int, textColor: Int = Color.WHITE): Bitmap {
    val w = 96
    val h = 124
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val cx = w / 2f
    val r = w / 2f - 10f
    val cy = r + 8f
    val tipY = h - 8f

    paint.color = 0x33000000
    canvas.drawOval(cx - r * 0.55f, tipY - 12f, cx + r * 0.55f, tipY - 2f, paint)

    fun drawPin(radius: Float, color: Int) {
        paint.color = color
        val path = Path().apply {
            // 둥근 머리 + 아래로 뾰족한 꼬리.
            addCircle(cx, cy, radius, Path.Direction.CW)
            moveTo(cx - radius * 0.62f, cy + radius * 0.55f)
            lineTo(cx + radius * 0.62f, cy + radius * 0.55f)
            lineTo(cx, tipY)
            close()
        }
        canvas.drawPath(path, paint)
    }
    drawPin(r + 5f, Color.WHITE)
    drawPin(r, fill)

    paint.color = textColor
    // 글자가 길면 핀 안에 들어가도록 줄인다.
    paint.textSize = if (label.length <= 2) w * 0.40f else w * 0.26f
    paint.textAlign = Paint.Align.CENTER
    paint.isFakeBoldText = true
    canvas.drawText(label, cx, cy - (paint.descent() + paint.ascent()) / 2f, paint)
    return out
}

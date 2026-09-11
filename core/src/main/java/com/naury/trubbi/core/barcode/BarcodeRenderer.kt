package com.naury.trubbi.core.barcode

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

/**
 * 저장해 둔 바코드 값을 다시 스캔 가능한 이미지로 만든다.
 *
 * 스캔한 운송장을 창구에서 다시 찍어야 하는 일이 잦아, 값만 저장해 두고 필요할 때 그린다.
 */
object BarcodeRenderer {

    /** 정사각형으로 그려야 하는 2차원 코드인지. */
    fun isTwoDimensional(formatName: String?): Boolean = when (mapFormat(formatName)) {
        BarcodeFormat.QR_CODE, BarcodeFormat.DATA_MATRIX,
        BarcodeFormat.AZTEC, BarcodeFormat.PDF_417,
        -> true
        else -> false
    }

    /**
     * 값을 바코드 이미지로 인코딩한다.
     *
     * 원래 규격으로 못 만드는 값(자릿수·체크섬이 안 맞는 경우)이 있어, 실패하면 QR 로 한 번 더 시도한다.
     * 창구에서 찍히기만 하면 되므로 규격이 바뀌어도 쓸모는 남는다.
     */
    fun render(value: String, formatName: String?, widthPx: Int, heightPx: Int): Bitmap? {
        if (value.isBlank() || widthPx <= 0 || heightPx <= 0) return null
        val primary = mapFormat(formatName)
        return encode(value, primary, widthPx, heightPx)
            ?: if (primary != BarcodeFormat.QR_CODE) {
                val side = minOf(widthPx, heightPx)
                encode(value, BarcodeFormat.QR_CODE, side, side)
            } else {
                null
            }
    }

    private fun encode(value: String, format: BarcodeFormat, w: Int, h: Int): Bitmap? = try {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val matrix: BitMatrix = MultiFormatWriter().encode(value, format, w, h, hints)
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    } catch (e: Exception) {
        // 규격에 맞지 않는 값이면 ZXing 이 예외를 던진다. 호출부에서 QR 로 폴백한다.
        null
    }

    private fun mapFormat(formatName: String?): BarcodeFormat = when (formatName?.uppercase()) {
        "QR_CODE" -> BarcodeFormat.QR_CODE
        "CODE_128" -> BarcodeFormat.CODE_128
        "CODE_39" -> BarcodeFormat.CODE_39
        "CODE_93" -> BarcodeFormat.CODE_93
        "EAN_13" -> BarcodeFormat.EAN_13
        "EAN_8" -> BarcodeFormat.EAN_8
        "UPC_A" -> BarcodeFormat.UPC_A
        "UPC_E" -> BarcodeFormat.UPC_E
        "PDF417", "PDF_417" -> BarcodeFormat.PDF_417
        "DATA_MATRIX" -> BarcodeFormat.DATA_MATRIX
        "AZTEC" -> BarcodeFormat.AZTEC
        "ITF" -> BarcodeFormat.ITF
        "CODABAR" -> BarcodeFormat.CODABAR
        else -> BarcodeFormat.QR_CODE
    }
}

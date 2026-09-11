package com.naury.trubbi.feature.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naury.trubbi.R
import com.naury.trubbi.core.file.MediaStorage
import com.naury.trubbi.data.repo.RecordRepository
import com.naury.trubbi.domain.model.Labeled
import com.naury.trubbi.domain.model.Purpose
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 자르기 비율 프리셋. 운송장·영수증은 실제 서류 비율에 맞춰 둔 값이다.
 *
 * @property ratio null 이면 원본 비율을 유지한다
 */
enum class CropPreset(@StringRes override val labelRes: Int, val ratio: Float?) : Labeled {
    ORIGINAL(R.string.option_original, null),
    SQUARE(R.string.crop_square, 1f),
    R4_3(R.string.crop_4_3, 4f / 3f),
    R16_9(R.string.crop_16_9, 16f / 9f),
    WAYBILL(Purpose.WAYBILL.labelRes, 3f / 4f),
    RECEIPT(R.string.crop_receipt, 9f / 16f),
}

/** 이미지 편집기의 도구 탭. */
enum class EditorTool(@StringRes override val labelRes: Int) : Labeled {
    ADJUST(R.string.editor_tool_adjust),
    CROP(R.string.editor_tool_crop),
    REDACT(R.string.editor_tool_redact),
}

/** 가림 방식 — 개인정보가 드러난 부분을 덮는다. */
enum class RedactStyle(@StringRes override val labelRes: Int) : Labeled {
    MOSAIC(R.string.redact_mosaic),
    BLUR(R.string.redact_blur),
}

/**
 * 0~1 로 정규화한 사각 영역. 기준은 회전을 이미 바로잡은 원본 비트맵이다.
 *
 * 미리보기와 저장 시점의 해상도가 다르므로 픽셀 대신 비율로 보관한다.
 * 어떤 크기의 비트맵에 적용해도 같은 영역을 가리킨다.
 */
data class NormRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val isMeaningful: Boolean get() = (right - left) > 0.02f && (bottom - top) > 0.02f
    val isFull: Boolean get() = left <= 0.002f && top <= 0.002f && right >= 0.998f && bottom >= 0.998f

    /** 좌우·상하를 바로잡고 최소 크기를 지키며 0~1 안으로 가둔다. */
    fun sanitized(min: Float = 0.05f): NormRect {
        var l = left.coerceIn(0f, 1f)
        var t = top.coerceIn(0f, 1f)
        var r = right.coerceIn(0f, 1f)
        var b = bottom.coerceIn(0f, 1f)
        if (r < l) {
            val tmp = l
            l = r
            r = tmp
        }
        if (b < t) {
            val tmp = t
            t = b
            b = tmp
        }
        if (r - l < min) r = (l + min).coerceAtMost(1f)
        if (b - t < min) b = (t + min).coerceAtMost(1f)
        return NormRect(l, t, r, b)
    }

    companion object {
        val FULL = NormRect(0f, 0f, 1f, 1f)
    }
}

data class EditorState(
    val loading: Boolean = true,
    val tool: EditorTool = EditorTool.ADJUST,
    val rotation: Int = 0,
    val flipH: Boolean = false,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val cropRect: NormRect = NormRect.FULL,
    val cropPreset: CropPreset? = CropPreset.ORIGINAL,
    val redactions: List<NormRect> = emptyList(),
    val redactStyle: RedactStyle = RedactStyle.MOSAIC,
    val saving: Boolean = false,
)

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val repo: RecordRepository,
    private val mediaStorage: MediaStorage,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val recordId: String = savedStateHandle.get<String>("recordId").orEmpty()
    private val mediaUri: String = savedStateHandle.get<String>("mediaUri").orEmpty()

    val state = MutableStateFlow(EditorState())
    val source = MutableStateFlow<Bitmap?>(null)

    /** 가리기가 실제로 적용된 미리보기. 도구를 쓰는 동안 결과를 그대로 보여 준다. */
    val redactedPreview = MutableStateFlow<Bitmap?>(null)

    /** 가림 + 크롭까지 적용한 미리보기(밝기/회전은 표시 단계에서) — 크롭이 다른 탭에서도 유지되게. */
    val geometryPreview = MutableStateFlow<Bitmap?>(null)

    /** 가림+크롭을 실제 비트맵으로 합성(밝기·회전 제외). 크롭/가림이 바뀔 때마다 갱신. */
    private fun recomputeGeometryPreview() {
        val src = source.value ?: run {
            geometryPreview.value = null
            return
        }
        val s = state.value
        viewModelScope.launch {
            geometryPreview.value = withContext(Dispatchers.IO) {
                var work = if (s.redactions.isEmpty()) {
                    src
                } else {
                    src.copy(Bitmap.Config.ARGB_8888, true).also { w -> s.redactions.forEach { applyRedaction(w, it, s.redactStyle) } }
                }
                if (!s.cropRect.isFull) {
                    val r = s.cropRect.sanitized()
                    val l = (r.left * work.width).toInt().coerceIn(0, work.width - 1)
                    val t = (r.top * work.height).toInt().coerceIn(0, work.height - 1)
                    val cw = ((r.right - r.left) * work.width).toInt().coerceIn(1, work.width - l)
                    val ch = ((r.bottom - r.top) * work.height).toInt().coerceIn(1, work.height - t)
                    work = Bitmap.createBitmap(work, l, t, cw, ch)
                }
                work
            }
        }
    }

    init {
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                runCatching { mediaStorage.decodeForEditing(mediaUri) }.getOrNull()
            }
            source.value = bmp
            state.value = state.value.copy(loading = false)
            recomputeGeometryPreview()
        }
    }

    fun setTool(t: EditorTool) {
        state.value = state.value.copy(tool = t)
    }
    fun rotateLeft() {
        state.value = state.value.copy(rotation = (state.value.rotation + 270) % 360)
    }
    fun rotateRight() {
        state.value = state.value.copy(rotation = (state.value.rotation + 90) % 360)
    }
    fun toggleFlip() {
        state.value = state.value.copy(flipH = !state.value.flipH)
    }
    fun setBrightness(v: Float) {
        state.value = state.value.copy(brightness = v)
    }
    fun setContrast(v: Float) {
        state.value = state.value.copy(contrast = v)
    }

    /**
     * 비율 프리셋을 고르면 그 비율의 중앙 영역으로 자르기 사각형을 맞춘다.
     *
     * @param preset 고른 비율. `ORIGINAL` 은 전체, `FREE` 는 직접 끄는 상태
     */
    fun setCropPreset(p: CropPreset) {
        val ratio = p.ratio
        val src = source.value
        val rect = if (ratio == null || src == null) {
            NormRect.FULL
        } else {
            val w = src.width.toFloat()
            val h = src.height.toFloat()
            val rw: Float
            val rh: Float
            if (w / h > ratio) {
                rh = h
                rw = h * ratio
            } else {
                rw = w
                rh = w / ratio
            }
            val nw = rw / w
            val nh = rh / h
            val l = (1f - nw) / 2f
            val t = (1f - nh) / 2f
            NormRect(l, t, l + nw, t + nh)
        }
        state.value = state.value.copy(cropRect = rect, cropPreset = p)
        recomputeGeometryPreview()
    }

    /** 모서리·변 손잡이로 크기를 바꾼다. 비율이 깨지므로 프리셋 선택 표시는 해제된다. */
    fun setCropRect(r: NormRect) {
        state.value = state.value.copy(cropRect = r.sanitized(), cropPreset = null)
        recomputeGeometryPreview()
    }

    /** 크기는 그대로 두고 위치만 옮긴다. 비율이 유지되므로 프리셋 표시도 남는다. */
    fun moveCropRect(r: NormRect) {
        state.value = state.value.copy(cropRect = r.sanitized())
        recomputeGeometryPreview()
    }

    fun setRedactStyle(s: RedactStyle) {
        state.value = state.value.copy(redactStyle = s)
        recomputeRedactPreview()
        recomputeGeometryPreview()
    }
    fun addRedaction(r: NormRect) {
        if (r.isMeaningful) {
            state.value = state.value.copy(redactions = state.value.redactions + r.sanitized(0.01f))
            recomputeRedactPreview()
            recomputeGeometryPreview()
        }
    }
    fun undoRedaction() {
        state.value = state.value.copy(redactions = state.value.redactions.dropLast(1))
        recomputeRedactPreview()
        recomputeGeometryPreview()
    }
    fun clearRedactions() {
        state.value = state.value.copy(redactions = emptyList())
        recomputeRedactPreview()
        recomputeGeometryPreview()
    }

    /** 가리기 미리보기를 다시 만든다. 모자이크는 무거워서 메인 스레드 밖에서 돌린다. */
    private fun recomputeRedactPreview() {
        val src = source.value ?: return
        val redactions = state.value.redactions
        val style = state.value.redactStyle
        viewModelScope.launch {
            redactedPreview.value = withContext(Dispatchers.IO) {
                if (redactions.isEmpty()) {
                    null
                } else {
                    src.copy(Bitmap.Config.ARGB_8888, true).also { work ->
                        redactions.forEach { applyRedaction(work, it, style) }
                    }
                }
            }
        }
    }

    fun reset() {
        state.value = state.value.copy(
            rotation = 0,
            flipH = false,
            brightness = 0f,
            contrast = 1f,
            cropRect = NormRect.FULL,
            cropPreset = CropPreset.ORIGINAL,
            redactions = emptyList(),
        )
        redactedPreview.value = null
        recomputeGeometryPreview()
    }

    fun save(onSaved: () -> Unit) {
        val src = source.value ?: return
        val s = state.value
        state.value = s.copy(saving = true)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val processed = process(src, s)
                val saved = mediaStorage.saveEdited(processed, "edit")
                repo.addEditedMedia(recordId, saved, mediaUri)
            }
            state.value = state.value.copy(saving = false)
            onSaved()
        }
    }

    /** 공유 화면에서 임의의 이미지를 직접 불러와 1회성으로 편집할 때 사용(nav 인자 불필요). */
    fun loadSource(uri: String) {
        viewModelScope.launch {
            state.value = state.value.copy(loading = true)
            val bmp = withContext(Dispatchers.IO) { runCatching { mediaStorage.decodeForEditing(uri) }.getOrNull() }
            source.value = bmp
            reset()
            state.value = state.value.copy(loading = false)
        }
    }

    /** 편집 결과를 임시 파일로만 저장(DB/원본 미변경)하고 (uri, 가로, 세로)를 반환 — 1회성 공유 편집용. */
    fun saveForShare(onResult: (String, Int, Int) -> Unit) {
        val src = source.value ?: return
        val s = state.value
        state.value = s.copy(saving = true)
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                mediaStorage.saveEdited(process(src, s), "shareedit")
            }
            state.value = state.value.copy(saving = false)
            onResult(saved.uri, saved.width, saved.height)
        }
    }

    private fun process(src: Bitmap, s: EditorState): Bitmap {
        // 0) 원본을 건드리지 않도록 작업용 사본을 뜬다
        var work = src.copy(Bitmap.Config.ARGB_8888, true)

        // 1) 가리기 — 자르기보다 먼저 해야 원본 좌표 기준이 그대로 맞는다
        s.redactions.forEach { applyRedaction(work, it, s.redactStyle) }

        // 2) 자르기
        if (!s.cropRect.isFull) {
            val r = s.cropRect.sanitized()
            val l = (r.left * work.width).toInt().coerceIn(0, work.width - 1)
            val t = (r.top * work.height).toInt().coerceIn(0, work.height - 1)
            val cw = ((r.right - r.left) * work.width).toInt().coerceIn(1, work.width - l)
            val ch = ((r.bottom - r.top) * work.height).toInt().coerceIn(1, work.height - t)
            work = Bitmap.createBitmap(work, l, t, cw, ch)
        }

        // 3) 회전·반전
        val matrix = Matrix()
        if (s.flipH) matrix.postScale(-1f, 1f)
        if (s.rotation != 0) matrix.postRotate(s.rotation.toFloat())
        val oriented = if (matrix.isIdentity) {
            work
        } else {
            Bitmap.createBitmap(work, 0, 0, work.width, work.height, matrix, true)
        }

        // 4) 밝기·대비
        val cm = ColorMatrix()
        val scale = s.contrast
        val translate = (-0.5f * scale + 0.5f) * 255f + s.brightness * 2.55f
        cm.set(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        val colored = Bitmap.createBitmap(oriented.width, oriented.height, Bitmap.Config.ARGB_8888)
        Canvas(colored).drawBitmap(
            oriented,
            0f,
            0f,
            Paint().apply { colorFilter = ColorMatrixColorFilter(cm) },
        )
        return colored
    }

    private fun applyRedaction(bmp: Bitmap, r: NormRect, style: RedactStyle) {
        val l = (r.left * bmp.width).toInt().coerceIn(0, bmp.width - 1)
        val t = (r.top * bmp.height).toInt().coerceIn(0, bmp.height - 1)
        val w = ((r.right - r.left) * bmp.width).toInt().coerceIn(1, bmp.width - l)
        val h = ((r.bottom - r.top) * bmp.height).toInt().coerceIn(1, bmp.height - t)
        if (w < 2 || h < 2) return
        val sub = Bitmap.createBitmap(bmp, l, t, w, h)
        val factor = if (style == RedactStyle.MOSAIC) 0.06f else 0.10f
        val smallW = max(1, (w * factor).toInt())
        val smallH = max(1, (h * factor).toInt())
        val useFilter = style == RedactStyle.BLUR
        val small = Bitmap.createScaledBitmap(sub, smallW, smallH, useFilter)
        val big = Bitmap.createScaledBitmap(small, w, h, useFilter)
        // 처리한 조각을 원래 자리에 다시 얹는다.
        val canvas = Canvas(bmp)
        val radius = (minOf(w, h) * 0.15f)
        val path = Path().apply {
            addRoundRect(RectF(l.toFloat(), t.toFloat(), (l + w).toFloat(), (t + h).toFloat()), radius, radius, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(path)
        canvas.drawBitmap(big, l.toFloat(), t.toFloat(), null)
        canvas.restore()
    }
}

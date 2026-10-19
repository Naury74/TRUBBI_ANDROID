package com.naury.trubbi.core.file

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import com.naury.trubbi.core.R
import com.naury.trubbi.core.log.TrubbiLog
import com.naury.trubbi.core.log.logFailure
import com.naury.trubbi.domain.model.Labeled
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 공유 워터마크 스타일. 미리보기와 실제 굽기가 같은 값을 쓴다.
 */
enum class WatermarkStyle(@StringRes override val labelRes: Int) : Labeled {
    BAR(R.string.watermark_style_bar),
    MINIMAL(R.string.watermark_style_minimal),
    CARD(R.string.watermark_style_card),
}

/**
 * 저장이 끝난 미디어 한 건의 결과.
 *
 * @property uri 원본 파일 uri (앱 내부 저장소)
 * @property thumbnailUri 목록용 축소본 uri. 영상·PDF 도 항상 JPEG 이다
 * @property width 표시 기준 가로 픽셀 — 영상은 회전 플래그를 반영한 값
 * @property height 표시 기준 세로 픽셀
 * @property fileSize 원본 바이트 수
 * @property mimeType `image/jpeg` · `video/mp4` · `application/pdf`
 * @property durationMs 영상 길이(ms). 영상이 아니면 null
 */
data class SavedMedia(
    val uri: String,
    val thumbnailUri: String,
    val width: Int,
    val height: Int,
    val fileSize: Long,
    val mimeType: String = "image/jpeg",
    val durationMs: Long? = null,
)

/**
 * 사진·영상·PDF 를 앱 내부 저장소에 넣고, 썸네일·워터마크·공유용 사본을 만드는 곳.
 *
 * MediaStore 가 아니라 `filesDir` 안에만 저장한다. 다른 앱과 갤러리에서 접근·삭제되지 않는다.
 * 외부로 내보낼 때는 `cacheDir` 로 사본을 만들어 FileProvider uri 로 넘긴다.
 *
 * 파일명 규칙은 `Trubbi_{용도}_{날짜시간}_{위치}_{고유}` 다. 이름순 정렬이 용도별 정렬과 같아지고,
 * 백업 ZIP 을 푼 뒤에도 파일명만으로 출처를 식별할 수 있다.
 *
 * @property context 저장 경로·FileProvider·ContentResolver 접근용 (Hilt 주입)
 * @property analyzer PDF 를 만들 때 페이지에서 글자를 뽑는 온디바이스 OCR
 */
@Singleton
class MediaStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val analyzer: com.naury.trubbi.core.ml.MediaAnalyzer,
) {
    private val mediaDir: File by lazy { File(context.filesDir, "media").apply { mkdirs() } }
    private val thumbDir: File by lazy { File(context.filesDir, "media/thumb").apply { mkdirs() } }
    private val shareDir: File by lazy { File(context.cacheDir, "shared").apply { mkdirs() } }

    /** 파일명 조각을 정리한다. 공백·금지문자를 `_` 로 바꾸고 20자로 자른다. */
    private fun slug(s: String): String =
        s.trim().replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").trim('_').take(20)

    /**
     * `Trubbi_{용도}_{날짜시간}_{위치}_{고유}.jpg` 를 만든다.
     *
     * 초 단위 타임스탬프만으로는 같은 초에 저장되는 여러 장(문서 페이지 등)이 충돌하므로
     * 4자리 고유 토큰을 덧붙인다.
     */
    private fun fileName(purpose: String, locationName: String?): String {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val p = slug(purpose).ifBlank { "기타" }
        val loc = locationName?.takeIf { it.isNotBlank() }?.let { "_" + slug(it) }.orEmpty()
        val uniq = UUID.randomUUID().toString().take(4)
        return "Trubbi_${p}_${ts}${loc}_$uniq.jpg"
    }

    /**
     * 촬영한 비트맵을 JPEG 로 저장하고 썸네일까지 만든다.
     *
     * @param bitmap 저장할 이미지. 회전은 이미 적용된 상태로 받는다
     * @param purpose 용도 라벨. 파일명 앞쪽에 들어간다
     * @param locationName 짧은 위치명. 있으면 파일명에 덧붙는다
     * @param quality JPEG 품질(0~100). 설정의 사진 화질이 그대로 내려온다
     */
    suspend fun saveBitmap(
        bitmap: Bitmap,
        purpose: String,
        locationName: String? = null,
        quality: Int = 92,
    ): SavedMedia = withContext(Dispatchers.IO) {
        val name = fileName(purpose, locationName)
        val target = File(mediaDir, name)
        FileOutputStream(target).use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        val thumb = writeThumbnail(bitmap, name)
        SavedMedia(
            uri = Uri.fromFile(target).toString(),
            thumbnailUri = thumb,
            width = bitmap.width,
            height = bitmap.height,
            fileSize = target.length(),
        )
    }

    /**
     * 갤러리 등 외부에서 고른 이미지를 앱 저장소로 들여온다.
     *
     * 단순 복사가 아니라 EXIF 회전을 픽셀에 적용해 다시 인코딩한다. 회전 플래그를 읽지 않는
     * 수신 앱에서도 방향이 유지된다.
     *
     * @param source 외부 이미지 uri (content 또는 file)
     * @param purpose 용도 라벨 — 파일명에 들어간다
     * @param locationName 짧은 위치명(선택)
     */
    suspend fun copyFromUri(
        source: Uri,
        purpose: String,
        locationName: String? = null,
    ): SavedMedia = withContext(Dispatchers.IO) {
        val name = fileName(purpose, locationName)
        val target = File(mediaDir, name)
        context.contentResolver.openInputStream(source)?.use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        } ?: error("Cannot open source uri")

        val normalized = decodeNormalized(target)
        FileOutputStream(target).use { normalized.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        val thumb = writeThumbnail(normalized, name)
        SavedMedia(
            uri = Uri.fromFile(target).toString(),
            thumbnailUri = thumb,
            width = normalized.width,
            height = normalized.height,
            fileSize = target.length(),
        )
    }

    /**
     * 촬영한 영상 파일을 저장소로 옮기고 포스터 프레임·길이를 뽑는다.
     *
     * @param file 카메라가 만든 임시 mp4. 복사 후 원본은 호출부가 정리한다
     * @param purpose 용도 라벨 — 파일명에 들어간다
     */
    suspend fun saveVideo(file: File, purpose: String): SavedMedia = withContext(Dispatchers.IO) {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val name = "Trubbi_${slug(purpose).ifBlank { "기타" }}_$ts.mp4"
        val target = File(mediaDir, name)
        file.copyTo(target, overwrite = true)

        var width = 0
        var height = 0
        var duration: Long? = null
        // 프레임 추출 실패 시의 기본값. Coil 의 영상 디코더가 uri 에서 직접 프레임을 뽑는다.
        var thumbUri = Uri.fromFile(target).toString()
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(target.absolutePath)
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            // 메타데이터의 가로·세로는 회전 플래그가 반영되지 않은 값이다.
            // 90°·270° 면 두 값을 바꿔 표시 기준 크기로 맞춘다.
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) {
                val tmp = width
                width = height
                height = tmp
            }
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            // 첫 프레임은 비어 있는 경우가 많아 1초 지점(1초 미만이면 중간)에서 추출한다.
            val posterUs = (((duration ?: 0L) / 2).coerceAtMost(1000L)) * 1000L
            val frame = retriever.getFrameAtTime(posterUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(posterUs, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: retriever.getFrameAtTime(-1)
            // 확장자를 `.jpg` 로 둔다. `.mp4` 로 두면 Coil 이 영상으로 판단해 프레임 디코더를
            // 태우고, JPEG 이라 디코딩에 실패한다.
            if (frame != null) thumbUri = writeThumbnail(frame, name.removeSuffix(".mp4") + ".jpg")
        } catch (e: Exception) {
            // 메타데이터를 읽지 못해도 저장은 성공 처리한다. 크기는 0, 썸네일은 영상 파일 uri 가 된다.
            TrubbiLog.warn(TAG, "영상 메타데이터 추출 실패", e)
        } finally {
            runCatching { retriever.release() }
        }

        SavedMedia(
            uri = Uri.fromFile(target).toString(),
            thumbnailUri = thumbUri,
            width = width,
            height = height,
            fileSize = target.length(),
            mimeType = "video/mp4",
            durationMs = duration,
        )
    }

    /**
     * 스캔한 PDF 를 저장소로 옮기고 첫 페이지를 JPEG 썸네일로 굽는다.
     *
     * 목록에 문서 아이콘 대신 첫 페이지가 표시된다.
     *
     * @param source 문서 스캐너가 만든 PDF uri
     * @param purpose 용도 라벨 — 파일명에 들어간다
     * @param locationName 짧은 위치명(선택)
     * @return `mimeType = "application/pdf"` 인 [SavedMedia]
     */
    suspend fun savePdf(
        source: Uri,
        purpose: String,
        locationName: String? = null,
    ): SavedMedia = withContext(Dispatchers.IO) {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val p = slug(purpose).ifBlank { "기타" }
        val loc = locationName?.takeIf { it.isNotBlank() }?.let { "_" + slug(it) }.orEmpty()
        val name = context.getString(R.string.file_name_document, p, ts, loc)
        val target = File(mediaDir, name)
        context.contentResolver.openInputStream(source)?.use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        } ?: error("Cannot open source uri")

        var width = 0
        var height = 0
        var thumbUri = Uri.fromFile(target).toString()
        runCatching {
            android.os.ParcelFileDescriptor.open(
                target,
                android.os.ParcelFileDescriptor.MODE_READ_ONLY,
            ).use { pfd ->
                android.graphics.pdf.PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount > 0) {
                        renderer.openPage(0).use { page ->
                            val scale = 1240f / page.width.coerceAtLeast(1)
                            val w = (page.width * scale).toInt().coerceIn(1, 2000)
                            val h = (page.height * scale).toInt().coerceIn(1, 3000)
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            width = w
                            height = h
                            thumbUri = writeThumbnail(bmp, name.removeSuffix(".pdf") + ".jpg")
                        }
                    }
                }
            }
        }

        SavedMedia(
            uri = Uri.fromFile(target).toString(),
            thumbnailUri = thumbUri,
            width = width,
            height = height,
            fileSize = target.length(),
            mimeType = "application/pdf",
        )
    }

    /** 페이지에서 처음 나오는 의미 있는 줄을 문서 제목으로 쓴다. 인식 실패 시 null. */
    suspend fun ocrTitle(uri: Uri): String? = withContext(Dispatchers.IO) {
        val bmp = runCatching { decodeUri(uri, 1600) }
            .logFailure(TAG, "문서 제목 추출용 이미지 디코딩 실패")
            .getOrNull() ?: return@withContext null
        val text = runCatching { analyzer.recognizeBlocks(bmp) }.getOrNull()
        bmp.recycle()
        text?.textBlocks
            ?.flatMap { it.lines }
            ?.map { it.text.trim() }
            ?.firstOrNull { line -> line.length in 2..40 && line.any { it.isLetter() } }
            ?.take(40)
    }

    /** 이미지 uri 를 축소 디코딩한다. 스캔 원본을 그대로 읽으면 OOM 이 발생한다. */
    private fun decodeUri(uri: Uri, maxDim: Int = 2200): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: error("decode failed: $uri")
    }

    /** 편집 결과를 새 파일로 저장한다. 원본 파일은 변경하지 않는다. */
    suspend fun saveEdited(bitmap: Bitmap, baseName: String): SavedMedia = withContext(Dispatchers.IO) {
        val name = "Trubbi_edit_${UUID.randomUUID().toString().take(8)}_$baseName.jpg"
        val target = File(mediaDir, name)
        FileOutputStream(target).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        val thumb = writeThumbnail(bitmap, name)
        SavedMedia(
            uri = Uri.fromFile(target).toString(),
            thumbnailUri = thumb,
            width = bitmap.width,
            height = bitmap.height,
            fileSize = target.length(),
        )
    }

    /**
     * 편집기(FrameKit)가 만든 사진을 저장소로 들여온다.
     *
     * 편집기 결과는 회전이 이미 적용된 파일이라 다시 인코딩하지 않고 바이트 그대로 옮긴다(화질 손실 없음).
     * 편집기 쪽 파일은 호출한 쪽이 지운다.
     *
     * @param source 편집기 결과 uri (`content://…framekit.files/…`)
     * @param extension 파일 확장자(`jpg`·`png`·`webp`)
     * @param mimeType 결과 형식
     * @param width 결과 가로 픽셀. 0 이하면 파일에서 읽는다
     * @param height 결과 세로 픽셀. 0 이하면 파일에서 읽는다
     * @param label 파일명에 들어갈 출처(`edit`·`scan` 등)
     */
    suspend fun importEditedImage(
        source: Uri,
        extension: String,
        mimeType: String,
        width: Int,
        height: Int,
        label: String = "edit",
    ): SavedMedia = withContext(Dispatchers.IO) {
        val name = "Trubbi_${label}_${UUID.randomUUID().toString().take(8)}.$extension"
        val target = File(mediaDir, name)
        copyInto(source, target)
        val thumb = writeThumbnail(decodeNormalized(target), name.substringBeforeLast('.') + ".jpg")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (width <= 0 || height <= 0) BitmapFactory.decodeFile(target.absolutePath, bounds)
        SavedMedia(
            uri = Uri.fromFile(target).toString(),
            thumbnailUri = thumb,
            width = if (width > 0) width else bounds.outWidth,
            height = if (height > 0) height else bounds.outHeight,
            fileSize = target.length(),
            mimeType = mimeType,
        )
    }

    /**
     * 편집기(FrameKit)가 만든 영상을 저장소로 들여온다. 포스터 프레임과 길이는 [saveVideo] 와 같은 방식으로 뽑는다.
     *
     * @param source 편집기 결과 uri
     */
    suspend fun importEditedVideo(source: Uri): SavedMedia = withContext(Dispatchers.IO) {
        val temp = File(context.cacheDir, "import_${UUID.randomUUID()}.mp4")
        try {
            copyInto(source, temp)
            saveVideo(temp, "edit")
        } finally {
            temp.delete()
        }
    }

    /**
     * 공유 화면에서 한 번만 편집한 결과를 공유 캐시에 둔다. 기록에는 남기지 않는다.
     *
     * 캐시에 두므로 기기가 공간이 필요할 때 지울 수 있다. 공유가 끝난 뒤에는 필요 없다.
     *
     * @return 공유 캐시 파일의 `file://` uri. [watermarkForShare]·[shareUri] 에 그대로 넘길 수 있다
     */
    suspend fun cacheEditedForShare(source: Uri, extension: String): String = withContext(Dispatchers.IO) {
        val target = File(shareDir, "edit_${UUID.randomUUID().toString().take(8)}.$extension")
        copyInto(source, target)
        Uri.fromFile(target).toString()
    }

    private fun copyInto(source: Uri, target: File) {
        context.contentResolver.openInputStream(source)?.use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        } ?: error("Cannot open source uri")
    }

    private fun writeThumbnail(source: Bitmap, baseName: String): String {
        val maxSide = 480
        val scale = maxSide.toFloat() / maxOf(source.width, source.height).coerceAtLeast(1)
        val thumb = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                source,
                (source.width * scale).toInt().coerceAtLeast(1),
                (source.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            source
        }
        val file = File(thumbDir, "thumb_$baseName")
        FileOutputStream(file).use { thumb.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        return Uri.fromFile(file).toString()
    }

    /** EXIF 회전을 픽셀에 적용해 디코딩한다. */
    fun decodeNormalized(file: File): Bitmap {
        val opts = BitmapFactory.Options().apply { inSampleSize = computeSampleSize(file) }
        val bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: error("decode failed")
        val orientation = try {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bmp
        }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
    }

    fun decodeForEditing(uriString: String): Bitmap {
        val file = uriString.toFile()
        return decodeNormalized(file)
    }

    private fun computeSampleSize(file: File): Int {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        val maxDim = 2400
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) {
            sample *= 2
        }
        return sample
    }

    /**
     * 프로필 사진을 고정된 파일 한 칸(`profile.jpg`)에 덮어쓴다.
     *
     * @param source 사용자가 고른 이미지 uri
     * @return 캐시 무효화용 버전 토큰이 붙은 uri 문자열
     */
    suspend fun saveProfilePhoto(source: Uri): String = withContext(Dispatchers.IO) {
        val target = File(mediaDir, "profile.jpg")
        context.contentResolver.openInputStream(source)?.use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        } ?: error("Cannot open source uri")
        // 회전 보정과 축소 디코딩을 거쳐 같은 경로에 다시 인코딩한다.
        val normalized = decodeNormalized(target)
        FileOutputStream(target).use { normalized.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        // 파일 경로가 그대로라 Coil 캐시가 적중한다. 버전 토큰을 붙여 캐시를 무효화한다.
        "${Uri.fromFile(target)}?v=${System.currentTimeMillis()}"
    }

    /** 비트맵을 공유 캐시에 PNG 로 쓰고 FileProvider uri 를 돌려준다. */
    fun cacheBitmapForShare(bitmap: Bitmap, baseName: String): Uri {
        val dest = File(shareDir, "$baseName.png")
        FileOutputStream(dest).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dest)
    }

    /**
     * 사진 위에 정보를 직접 구워 공유용 사본을 만든다.
     *
     * 이미지와 별개로 전달되는 텍스트는 수신 측에 남지 않는 경우가 있어, 픽셀에 직접 굽는다.
     * 원본 파일은 변경하지 않고 공유 캐시의 사본에만 적용한다.
     *
     * @param uriString 원본 이미지 uri 문자열
     * @param lines 위에서부터 한 줄씩 그릴 문구들 (시각·장소·용도 등)
     * @param style 워터마크 모양 — 하단 바 / 최소 표기 / 카드
     * @return 공유 캐시에 만들어진 사본의 FileProvider uri
     */
    fun watermarkForShare(uriString: String, lines: List<String>, style: WatermarkStyle = WatermarkStyle.BAR): Uri {
        val base = decodeForEditing(uriString)
        val bmp = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(bmp)
        val w = bmp.width.toFloat()
        val h = bmp.height.toFloat()
        if (lines.isNotEmpty()) {
            val textSize = (w * 0.032f).coerceIn(20f, 84f)
            val pad = textSize * 0.8f
            val lineH = textSize * 1.32f
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.WHITE
                this.textSize = textSize
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            val blockH = lineH * lines.size
            when (style) {
                WatermarkStyle.BAR -> {
                    paint.setShadowLayer(textSize * 0.14f, 0f, textSize * 0.06f, android.graphics.Color.argb(220, 0, 0, 0))
                    val blockTop = h - blockH - pad
                    val scrim = android.graphics.Paint().apply {
                        shader = android.graphics.LinearGradient(
                            0f,
                            blockTop - pad,
                            0f,
                            h,
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.argb(160, 0, 0, 0),
                            android.graphics.Shader.TileMode.CLAMP,
                        )
                    }
                    canvas.drawRect(0f, blockTop - pad, w, h, scrim)
                    paint.textAlign = android.graphics.Paint.Align.LEFT
                    var y = h - pad - (lines.size - 1) * lineH
                    lines.forEach {
                        canvas.drawText(it, pad, y, paint)
                        y += lineH
                    }
                }
                WatermarkStyle.MINIMAL -> {
                    paint.setShadowLayer(textSize * 0.22f, 0f, textSize * 0.08f, android.graphics.Color.argb(235, 0, 0, 0))
                    paint.textAlign = android.graphics.Paint.Align.RIGHT
                    var y = h - pad - (lines.size - 1) * lineH
                    lines.forEach {
                        canvas.drawText(it, w - pad, y, paint)
                        y += lineH
                    }
                }
                WatermarkStyle.CARD -> {
                    val maxTextW = lines.maxOf { paint.measureText(it) }
                    val cardLeft = pad
                    val cardTop = h - blockH - pad * 2
                    val cardRight = cardLeft + maxTextW + pad * 2
                    val cardBottom = h - pad
                    val card = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb(165, 0, 0, 0)
                        isAntiAlias = true
                    }
                    canvas.drawRoundRect(
                        android.graphics.RectF(cardLeft, cardTop, cardRight.coerceAtMost(w - pad), cardBottom),
                        textSize * 0.5f,
                        textSize * 0.5f,
                        card,
                    )
                    paint.textAlign = android.graphics.Paint.Align.LEFT
                    var y = cardBottom - pad - (lines.size - 1) * lineH + textSize * 0.1f
                    lines.forEach {
                        canvas.drawText(it, cardLeft + pad, y, paint)
                        y += lineH
                    }
                }
            }
        }
        val dest = File(shareDir, "wm_${UUID.randomUUID().toString().take(8)}.jpg")
        FileOutputStream(dest).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dest)
    }

    /** 미디어 파일을 공유 캐시로 복사하고 FileProvider uri 를 돌려준다. */
    fun shareUri(uriString: String): Uri {
        val src = uriString.toFile()
        val dest = File(shareDir, src.name)
        src.copyTo(dest, overwrite = true)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dest)
    }

    private fun String.toFile(): File = File(Uri.parse(this).path ?: this)

    private companion object {
        const val TAG = "MediaStorage"
    }
}

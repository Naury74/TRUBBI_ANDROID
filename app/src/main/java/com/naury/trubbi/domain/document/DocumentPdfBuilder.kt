package com.naury.trubbi.domain.document

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.naury.framekit.android.input.EditorInput
import com.naury.framekit.android.result.EditedMedia
import com.naury.framekit.android.result.FrameKitResult
import com.naury.framekit.core.pdf.PdfPageSize
import com.naury.framekit.image.export.ImageExportConfig
import com.naury.framekit.image.export.ImageFormat
import com.naury.framekit.image.export.PdfOptions
import com.naury.framekit.image.headless.ImageProcessor
import com.naury.framekit.image.ocr.RecognizedLine
import com.naury.framekit.image.ocr.TextRecognizer
import com.naury.trubbi.core.ml.MediaAnalyzer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.coroutineScope

/**
 * 스캔한 페이지 사진들을 PDF 한 개로 묶는다.
 *
 * 페이지마다 사진을 그대로 담고, 그 위에 보이지 않는 글자 레이어를 겹쳐 PDF 뷰어에서 검색·복사가 되게 한다.
 * 글자 인식은 앱이 이미 가진 기기 내 한국어 모델(ML Kit 번들형)을 쓴다. 문서를 외부로 보내지 않는다.
 */
@Singleton
class DocumentPdfBuilder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val analyzer: MediaAnalyzer,
) {
    /**
     * @param pages 페이지 순서대로의 사진 uri. 1장 이상
     * @return FrameKit 저장소에 만들어진 PDF. 실패하면 null. 다 쓴 뒤 호출한 쪽이 `FrameKitOutputs.deleteOutput` 으로 지운다
     */
    suspend fun build(pages: List<Uri>): EditedMedia? {
        if (pages.isEmpty()) return null
        return ImageProcessor(context, textRecognizer = BundledTextRecognizer(analyzer)).use { processor ->
            coroutineScope {
                val projects = pages.map { uri ->
                    val source = processor.open(EditorInput.UriSource(uri))
                    processor.newProject(source) to source
                }
                val config = ImageExportConfig(
                    format = ImageFormat.PDF,
                    quality = PDF_QUALITY,
                    pdf = PdfOptions(pageSize = PdfPageSize.A4, marginMm = PDF_MARGIN_MM),
                )
                (processor.startPdfExport(projects, config, scope = this).awaitResult() as? FrameKitResult.Success)?.output
            }
        }
    }

    private companion object {
        // 영수증·운송장의 작은 글자가 뭉개지지 않으면서 파일이 메일 첨부 한도를 넘지 않는 값.
        const val PDF_QUALITY = 85
        const val PDF_MARGIN_MM = 6.0
    }
}

/** 앱의 ML Kit 한국어 인식기를 FrameKit 의 글자 레이어 인식기로 쓴다. */
private class BundledTextRecognizer(private val analyzer: MediaAnalyzer) : TextRecognizer {
    override suspend fun recognize(image: Bitmap): List<RecognizedLine> {
        val text = analyzer.recognizeBlocks(image) ?: return emptyList()
        return text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            RecognizedLine(line.text, box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat())
        }
    }
}

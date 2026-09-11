package com.naury.trubbi.core.ml

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 사진 한 장을 분석한 결과. 둘 다 검색 색인에 그대로 들어간다.
 *
 * @property ocrText 사진에서 읽어 낸 글자 전체. 못 읽었으면 null
 * @property labelKeywords ML Kit 이 붙인 영어 라벨과 [SynonymDict] 로 넓힌 한국어 낱말을 합친 것
 */
data class AnalysisResult(
    val ocrText: String?,
    val labelKeywords: List<String>,
)

/**
 * 사진에서 글자와 사물 라벨을 뽑는 온디바이스 분석기.
 *
 * 전부 ML Kit 기기 내 모델이다. 증빙 사진에는 운송장 번호와 거래처 정보가 포함되므로
 * 이미지를 외부로 전송하지 않는다.
 *
 * 인식 실패는 예외로 전파하지 않는다. 분석 결과는 검색 색인에만 쓰이며, 저장 경로를 막지 않는다.
 */
@Singleton
class MediaAnalyzer @Inject constructor() {

    private val textRecognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

    /**
     * 글자 인식과 사물 라벨링을 한 번에 돌린다.
     *
     * @param bitmap 분석할 이미지. 해상도가 클수록 처리 시간이 늘어난다
     */
    suspend fun analyze(bitmap: Bitmap): AnalysisResult {
        val image = InputImage.fromBitmap(bitmap, 0)
        val ocr = runCatching { recognizeText(image) }.getOrNull()
        val labels = runCatching { labelImage(image) }.getOrDefault(emptyList())
        return AnalysisResult(ocrText = ocr?.takeIf { it.isNotBlank() }, labelKeywords = labels)
    }

    private suspend fun recognizeText(image: InputImage): String =
        suspendCancellableCoroutine { cont ->
            textRecognizer.process(image)
                .addOnSuccessListener { cont.resume(it.text) }
                .addOnFailureListener { cont.resume("") }
        }

    /**
     * 줄 단위 좌표까지 담긴 OCR 원본 결과.
     *
     * [analyze] 는 인식 결과를 하나의 문자열로 합치므로 좌표가 사라진다. 문서 PDF 생성처럼
     * 줄 위치가 필요한 경우 이 함수를 쓴다.
     *
     * @param bitmap 인식할 페이지 이미지
     * @return 인식 결과. 실패하면 null
     */
    suspend fun recognizeBlocks(bitmap: Bitmap): com.google.mlkit.vision.text.Text? =
        suspendCancellableCoroutine { cont ->
            textRecognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        }

    private suspend fun labelImage(image: InputImage): List<String> =
        suspendCancellableCoroutine { cont ->
            labeler.process(image)
                .addOnSuccessListener { labels ->
                    val keywords = LinkedHashSet<String>()
                    // 신뢰도 0.6 미만은 오탐이 많아 색인에서 제외한다.
                    labels.filter { it.confidence >= 0.6f }.forEach { label ->
                        keywords.add(label.text)
                        keywords.addAll(SynonymDict.expand(label.text))
                    }
                    cont.resume(keywords.toList())
                }
                .addOnFailureListener { cont.resume(emptyList()) }
        }
}

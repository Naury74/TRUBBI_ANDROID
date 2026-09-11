package com.naury.trubbi.feature.camera

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/**
 * ML Kit 문서 스캐너 런처. 호출하면 스캔 UI(촬영 → 자동 엣지 감지 → 영역 조정 →
 * 보정 필터)가 뜨고, 완료 시 [onResult]로 처리된 PDF와 페이지별 원본 이미지 URI를 돌려준다.
 * 취소하면 둘 다 null/빈 리스트로 콜백한다.
 *
 * @param onResult 스캔이 끝나면 (PDF uri, 페이지 원본 이미지 uri 들) 로 불린다.
 */
@Composable
fun rememberDocumentScanLauncher(
    onResult: (pdfUri: Uri?, pageUris: List<Uri>) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scan = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val pages = scan?.pages?.mapNotNull { it.imageUri }.orEmpty()
            onResult(scan?.pdf?.uri, pages)
        } else {
            onResult(null, emptyList())
        }
    }
    return launch@{
        val activity = context.findActivity() ?: run {
            onResult(null, emptyList())
            return@launch
        }
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(20)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF,
            )
            // BASE 는 자동 보정 필터 없이 원본 페이지를 그대로 돌려준다.
            // 보정본은 앱이 OCR 로 직접 만들기 때문에 ML Kit 쪽 보정이 필요 없다.
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE)
            .build()
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                launcher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { onResult(null, emptyList()) }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

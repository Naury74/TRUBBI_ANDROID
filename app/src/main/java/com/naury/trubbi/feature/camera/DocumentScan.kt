package com.naury.trubbi.feature.camera

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import com.naury.framekit.android.result.FrameKitResult
import com.naury.framekit.scanner.DocumentScanContract
import com.naury.framekit.scanner.DocumentScanRequest
import com.naury.trubbi.feature.editor.editorUi

/**
 * FrameKit 문서 스캐너 런처. 카메라 미리보기에서 문서 테두리를 찾아 자동으로 찍고, 찍은 쪽을 반듯하게 펴서 돌려준다.
 *
 * 여러 쪽을 이어 찍을 수 있다(최대 [MAX_PAGES]쪽). 결과 파일은 FrameKit 저장소에 있으므로 다 쓴 뒤
 * `FrameKitOutputs.deleteOutput` 으로 지운다.
 *
 * @param onResult 스캔을 마치면 쪽 순서대로의 JPEG uri 로 불린다. 취소하면 빈 목록
 */
@Composable
fun rememberDocumentScanLauncher(onResult: (pageUris: List<Uri>) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(DocumentScanContract()) { result ->
        onResult(if (result is FrameKitResult.Success) result.outputs.map { it.uri } else emptyList())
    }
    val ui = editorUi()
    return { launcher.launch(DocumentScanRequest(maxPages = MAX_PAGES, ui = ui)) }
}

private const val MAX_PAGES = 20

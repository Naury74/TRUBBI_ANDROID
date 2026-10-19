package com.naury.trubbi.feature.editor

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naury.framekit.android.input.EditorInput
import com.naury.framekit.android.output.FrameKitOutputs
import com.naury.framekit.android.result.EditedMedia
import com.naury.framekit.android.result.FrameKitResult
import com.naury.framekit.ui.image.contract.ImageEditorConfig
import com.naury.framekit.ui.image.contract.ImageEditorContract
import com.naury.framekit.ui.image.contract.ImageEditorRequest
import com.naury.framekit.ui.image.contract.ImageTool
import com.naury.trubbi.core.file.MediaStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * 공유 직전에 사진 한 장을 한 번만 편집하는 런처. 편집본은 공유 캐시에만 두고 기록에는 남기지 않는다.
 *
 * 워터마크 도구는 숨긴다. 공유 화면에 이미 여러 줄 정보 표기가 있어 두 가지가 겹치지 않게 한다.
 *
 * @param onEdited 편집을 마쳤을 때 공유 캐시 파일 uri 와 가로·세로 픽셀로 불린다. 취소하면 불리지 않는다
 * @return 원본 uri(`file://`)를 받아 편집기를 여는 함수
 */
@Composable
fun rememberShareEditor(onEdited: (uri: String, width: Int, height: Int) -> Unit): (String) -> Unit {
    val viewModel: ShareEditViewModel = hiltViewModel()
    val callback = rememberUpdatedState(onEdited)
    val launcher = rememberLauncherForActivityResult(ImageEditorContract()) { result ->
        if (result is FrameKitResult.Success) viewModel.cache(result.output) { uri -> callback.value(uri, result.output.width, result.output.height) }
    }
    val ui = editorUi()
    return { source ->
        launcher.launch(
            ImageEditorRequest(
                EditorInput.FileSource(Uri.parse(source).path ?: source),
                config = ImageEditorConfig(enabledTools = ImageTool.defaults - ImageTool.WATERMARK, maxImageCount = 1),
                ui = ui,
            ),
        )
    }
}

/** 편집 결과를 공유 캐시로 옮기고 FrameKit 쪽 파일을 지운다. */
@HiltViewModel
class ShareEditViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaStorage: MediaStorage,
) : ViewModel() {
    fun cache(media: EditedMedia, onCached: (String) -> Unit) {
        viewModelScope.launch {
            val uri = runCatching { mediaStorage.cacheEditedForShare(media.uri, extensionOf(media.mimeType)) }.getOrNull()
            FrameKitOutputs.deleteOutput(context, media.uri)
            if (uri != null) onCached(uri)
        }
    }
}

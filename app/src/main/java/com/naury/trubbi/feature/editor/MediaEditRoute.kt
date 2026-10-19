package com.naury.trubbi.feature.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naury.framekit.android.input.EditorInput
import com.naury.framekit.android.result.FrameKitResult
import com.naury.framekit.ui.config.EditorUiConfig
import com.naury.framekit.ui.config.ThemeMode
import com.naury.framekit.ui.config.WatermarkConfig
import com.naury.framekit.ui.image.contract.ImageEditorConfig
import com.naury.framekit.ui.image.contract.ImageEditorContract
import com.naury.framekit.ui.image.contract.ImageEditorRequest
import com.naury.framekit.ui.video.contract.VideoEditorConfig
import com.naury.framekit.ui.video.contract.VideoEditorContract
import com.naury.framekit.ui.video.contract.VideoEditorRequest
import com.naury.kinetica.ui.KLoadingIndicator
import com.naury.trubbi.R
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.TrubbiStatusDialog
import com.naury.trubbi.ui.theme.LocalTrubbiColors

/** 편집할 미디어 종류. 어느 FrameKit 편집기를 열지 정한다. */
enum class EditKind { PHOTO, VIDEO }

/**
 * 기록의 사진·영상 하나를 FrameKit 편집기로 연다. 이 화면은 편집기를 띄우고 결과를 저장하는 동안만 보인다.
 *
 * 편집기는 별도 Activity 라 회전·프로세스 종료 뒤 돌아와도 한 번만 띄운다. 저장한 편집본은 원본과 함께 기록에 남는다.
 *
 * @param kind 사진/영상
 * @param onClose 취소했거나 저장을 마쳤을 때
 */
@Composable
fun MediaEditRoute(kind: EditKind, onClose: () -> Unit, viewModel: MediaEditViewModel = hiltViewModel()) {
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    val stamp by viewModel.stamp.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    var launched by rememberSaveable { mutableStateOf(false) }
    var failed by rememberSaveable { mutableStateOf<Int?>(null) }

    val onResult: (FrameKitResult) -> Unit = { result ->
        when (result) {
            is FrameKitResult.Success -> viewModel.importResults(result.outputs) { imported ->
                if (imported > 0) onClose() else failed = R.string.editor_import_failed
            }
            FrameKitResult.Cancelled -> onClose()
            is FrameKitResult.Failure -> failed = R.string.editor_open_failed
        }
    }
    val photoEditor = rememberLauncherForActivityResult(ImageEditorContract(), onResult)
    val videoEditor = rememberLauncherForActivityResult(VideoEditorContract(), onResult)

    val ui = editorUi()
    LaunchedEffect(ready) {
        if (!ready || launched) return@LaunchedEffect
        launched = true
        val input = EditorInput.FileSource(viewModel.sourcePath)
        // 증빙 사진에 넣을 시각·장소 문구를 워터마크 도구에 미리 채워 둔다. 넣을지는 사용자가 정한다.
        val watermark = stamp?.let { WatermarkConfig(text = it.primary, background = true, suggestions = it.suggestions) }
        when (kind) {
            EditKind.PHOTO -> photoEditor.launch(
                ImageEditorRequest(input, config = ImageEditorConfig(maxImageCount = 1, watermark = watermark), ui = ui),
            )
            EditKind.VIDEO -> videoEditor.launch(
                VideoEditorRequest(input, config = VideoEditorConfig(maxClipCount = 1, watermark = watermark), ui = ui),
            )
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        if (saving || !launched) KLoadingIndicator()
    }
    failed?.let { title ->
        TrubbiStatusDialog(
            title = stringResource(title),
            message = stringResource(R.string.editor_open_failed_body),
            icon = AppIcons.Warning,
            destructive = true,
            onDismiss = onClose,
        )
    }
}

/** 편집기 화면을 앱의 밝기·언어·브랜드 색에 맞춘다(앱이 밤·궂은 날씨에 스스로 어두워지므로 시스템 값을 따르지 않는다). */
@Composable
internal fun editorUi(): EditorUiConfig = EditorUiConfig(
    themeMode = if (LocalTrubbiColors.current.isDark) ThemeMode.DARK else ThemeMode.LIGHT,
    primaryArgb = MaterialTheme.colorScheme.primary.toArgb(),
    localeTag = LocalConfiguration.current.locales[0].language,
)

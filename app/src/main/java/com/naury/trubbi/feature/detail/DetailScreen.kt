package com.naury.trubbi.feature.detail

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naury.trubbi.R
import com.naury.trubbi.data.local.RecordWithMedia
import com.naury.trubbi.domain.model.RecordType

/**
 * 기록 상세 진입점.
 *
 * 페이지 이미지 없이 PDF 만 있는 문서는 전용 뷰어로, 나머지는 모두 전체화면 뷰어로 보낸다.
 * 사진·영상·바코드·메모가 하나의 페이저에서 연속 탐색된다.
 */
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onEditImage: (recordId: String, mediaUri: String) -> Unit,
    onEditVideo: (recordId: String, mediaUri: String) -> Unit,
    onEditMemo: (recordId: String) -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.action_share)
    val record by viewModel.record.collectAsStateWithLifecycle()
    val item: RecordWithMedia? = record

    if (item == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.record_not_found),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        return
    }

    if (RecordType.fromName(item.record.type) == RecordType.DOCUMENT) {
        val pdf = item.pdfMedia
        val hasPages = item.media.any { !it.isPdf }
        if (pdf != null && !hasPages) {
            com.naury.trubbi.feature.pdf.PdfViewerScreen(
                pdfUri = pdf.uri,
                title = item.record.title,
                onBack = onBack,
                onShare = {
                    context.startActivity(Intent.createChooser(viewModel.shareIntent(item), shareTitle))
                },
            )
            return
        }
    }

    // 콜백은 지금 보고 있는 페이지의 기록을 인자로 받는다. 좌우로 넘겨도 대상이 어긋나지 않게.
    val pages by viewModel.detailPages.collectAsStateWithLifecycle()
    ImmersiveDetailScreen(
        item = item,
        pages = pages,
        onBack = onBack,
        onEditImage = { recordId, uri -> onEditImage(recordId, uri) },
        onEditVideo = { recordId, uri -> onEditVideo(recordId, uri) },
        onEditMemo = { recordId -> onEditMemo(recordId) },
        onShareBarcode = { rw ->
            val intent = viewModel.shareBarcodeIntent(rw) ?: viewModel.shareIntent(rw)
            context.startActivity(Intent.createChooser(intent, shareTitle))
        },
        viewModel = viewModel,
    )
}

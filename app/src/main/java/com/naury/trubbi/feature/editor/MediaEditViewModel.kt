package com.naury.trubbi.feature.editor

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naury.framekit.android.output.FrameKitOutputs
import com.naury.framekit.android.result.EditedMedia
import com.naury.framekit.core.model.MediaType
import com.naury.trubbi.core.file.MediaStorage
import com.naury.trubbi.core.log.TrubbiLog
import com.naury.trubbi.data.repo.RecordRepository
import com.naury.trubbi.feature.share.ShareUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * FrameKit 편집기를 띄우고, 편집 결과를 기록에 새 파일로 붙인다. 원본 파일은 그대로 둔다.
 *
 * 편집기 결과는 FrameKit 저장소(`files/framekit/exports`)에 생긴다. 백업·공유·워터마크가 모두 `files/media` 의
 * `file://` 경로를 전제로 하므로, 그쪽으로 옮긴 뒤 FrameKit 쪽 파일은 지운다.
 */
@HiltViewModel
class MediaEditViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: RecordRepository,
    private val mediaStorage: MediaStorage,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val recordId: String = savedStateHandle.get<String>("recordId").orEmpty()
    val mediaUri: String = savedStateHandle.get<String>("mediaUri").orEmpty()

    /** 편집기에 넘길 원본 파일 절대 경로. 앱 내부 파일이라 권한 없이 열린다. */
    val sourcePath: String get() = Uri.parse(mediaUri).path ?: mediaUri

    private val _stamp = MutableStateFlow<StampSuggestions?>(null)

    /** 워터마크 도구에 미리 넣어 둘 증빙 문구. 기록을 읽은 뒤 채워진다. */
    val stamp: StateFlow<StampSuggestions?> = _stamp.asStateFlow()

    private val _ready = MutableStateFlow(false)

    /** 추천 문구를 읽어 편집기를 열 준비가 됐는지. 기록이 없어도 읽기만 끝나면 true 다. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _saving = MutableStateFlow(false)

    /** 편집 결과를 저장소로 옮기는 중인지. */
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    init {
        viewModelScope.launch {
            val record = repo.observeRecord(recordId).firstOrNull()
            _stamp.value = StampSuggestions.from(record?.let(ShareUtils::watermarkLines).orEmpty())
            _ready.value = true
        }
    }

    /**
     * 편집 결과를 기록에 붙인다. 여러 장이면 모두 붙인다.
     *
     * @param outputs 편집기가 돌려준 결과들
     * @param onDone 다 옮긴 뒤(실패해도) 불린다. 인자는 성공한 개수
     */
    fun importResults(outputs: List<EditedMedia>, onDone: (Int) -> Unit) {
        _saving.value = true
        viewModelScope.launch {
            var imported = 0
            for (media in outputs) {
                runCatching {
                    val saved = when (media.mediaType) {
                        MediaType.VIDEO -> mediaStorage.importEditedVideo(media.uri)
                        else -> mediaStorage.importEditedImage(
                            media.uri,
                            extensionOf(media.mimeType),
                            media.mimeType,
                            media.width,
                            media.height,
                        )
                    }
                    repo.addEditedMedia(recordId, saved, mediaUri)
                    imported++
                }.onFailure { TrubbiLog.warn(TAG, "편집 결과 저장 실패", it) }
                FrameKitOutputs.deleteOutput(context, media.uri)
            }
            _saving.value = false
            onDone(imported)
        }
    }

    private companion object {
        const val TAG = "MediaEdit"
    }
}

/**
 * 워터마크 도구의 추천 문구. 기록의 촬영 시각·장소·용도 줄을 이어 만든다.
 *
 * @property primary 처음 열었을 때 입력돼 있는 문구
 * @property suggestions 고를 수 있는 문구들(최대 [MAX_SUGGESTIONS]개, 각 [MAX_LENGTH]자 이하)
 */
data class StampSuggestions(val primary: String, val suggestions: List<String>) {
    companion object {
        /** FrameKit 워터마크는 한 줄 60자까지다. */
        const val MAX_LENGTH = 60
        const val MAX_SUGGESTIONS = 8

        /** 기록의 워터마크 줄(위에서부터 시각·장소·…)로 추천 문구를 만든다. 줄이 없으면 null. */
        fun from(lines: List<String>): StampSuggestions? {
            val clean = lines.map { it.replace('\n', ' ').trim() }.filter { it.isNotEmpty() }
            if (clean.isEmpty()) return null
            val joined = clean.joinToString(" · ").take(MAX_LENGTH).trim()
            val options = (listOf(joined) + clean.map { it.take(MAX_LENGTH) }).distinct().take(MAX_SUGGESTIONS)
            return StampSuggestions(joined, options)
        }
    }
}

internal fun extensionOf(mimeType: String): String = when (mimeType) {
    "image/png" -> "png"
    "image/webp" -> "webp"
    "video/mp4" -> "mp4"
    "application/pdf" -> "pdf"
    else -> "jpg"
}

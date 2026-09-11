package com.naury.trubbi.feature.memo

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naury.trubbi.core.file.MediaStorage
import com.naury.trubbi.core.ui.StringProvider
import com.naury.trubbi.data.repo.RecordRepository
import com.naury.trubbi.domain.memo.MemoBlock
import com.naury.trubbi.domain.memo.MemoContent
import com.naury.trubbi.domain.model.RecordType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * 메모 작성·수정 상태.
 *
 * @param repo 메모 기록 저장
 * @param mediaStorage 본문에 넣을 이미지를 앱 저장소로 들여온다
 * @param savedStateHandle `recordId` 가 있으면 수정 모드
 */
@HiltViewModel
class MemoViewModel @Inject constructor(
    private val repo: RecordRepository,
    private val mediaStorage: MediaStorage,
    private val strings: StringProvider,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** 기존 메모를 여는 경우의 기록 id. 새로 쓰는 중이면 null. */
    private val editingId: String? = savedStateHandle.get<String>("recordId")?.takeIf { it.isNotBlank() }
    val isEditing: Boolean get() = editingId != null

    /** 편집기를 채울 초기 블록들. 수정 모드에서 한 번만 읽는다. */
    val initialBlocks = MutableStateFlow<List<MemoBlock>?>(null)

    init {
        if (editingId != null) {
            viewModelScope.launch {
                val item = repo.observeRecord(editingId).firstOrNull()
                initialBlocks.value = item?.let {
                    MemoContent.decode(it.record.memo).ifEmpty { listOf(MemoBlock.Text()) }
                } ?: listOf(MemoBlock.Text())
            }
        } else {
            initialBlocks.value = listOf(MemoBlock.Text())
        }
    }

    /**
     * 고른 이미지를 앱 저장소로 들여오고 저장된 uri 를 편집기에 돌려준다.
     *
     * 외부 uri 를 그대로 본문에 넣으면 나중에 원본이 지워졌을 때 메모 안의 이미지가 사라진다.
     *
     * @param uri 사용자가 고른 이미지
     * @param onSaved 저장이 끝나면 앱 저장소 uri 로 불린다
     */
    fun importImage(uri: Uri, onSaved: (String) -> Unit) {
        viewModelScope.launch {
            val saved = runCatching { mediaStorage.copyFromUri(uri, "memo") }.getOrNull() ?: return@launch
            onSaved(saved.uri)
        }
    }

    fun save(blocks: List<MemoBlock>, onSaved: () -> Unit) {
        val cleaned = blocks.filterNot {
            (it is MemoBlock.Text && it.text.isBlank()) ||
                (it is MemoBlock.Html && MemoContent.htmlToPlain(it.html).isBlank())
        }
        if (cleaned.isEmpty()) {
            onSaved()
            return
        }
        val content = MemoContent.encode(cleaned)
        val title = MemoContent.deriveTitle(cleaned, strings[RecordType.MEMO.labelRes])
        viewModelScope.launch {
            val id = editingId
            if (id != null) {
                repo.updateMemoContent(id, title, content)
            } else {
                repo.createMemoRecord(text = content, title = title)
            }
            onSaved()
        }
    }
}

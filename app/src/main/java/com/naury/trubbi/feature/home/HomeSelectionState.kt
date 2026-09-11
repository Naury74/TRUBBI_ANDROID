package com.naury.trubbi.feature.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 홈 목록의 다중 선택 상태.
 *
 * 기록 단위 선택과, 묶음을 펼쳐 고른 미디어 단위 선택을 함께 다룬다. 두 가지가 동시에 있는
 * 이유는 사진 여러 장이 기록 하나에 묶여 저장되기 때문이다. 기록을 고르면 그 안의 미디어가
 * 전부 대상이 되고, 묶음을 펼치면 낱장만 고를 수 있다.
 *
 * 코루틴도 안드로이드 타입도 쓰지 않으므로 전이 규칙만 단위 테스트로 검증할 수 있다.
 */
class HomeSelectionState {

    private val _records = MutableStateFlow<Set<String>>(emptySet())
    private val _media = MutableStateFlow<Set<String>>(emptySet())
    private val _expandedBatch = MutableStateFlow<String?>(null)

    /** 통째로 고른 기록 id. */
    val records: StateFlow<Set<String>> = _records.asStateFlow()

    /** 묶음을 풀어 낱장으로 고른 미디어 id. */
    val media: StateFlow<Set<String>> = _media.asStateFlow()

    /** 지금 펼쳐 둔 묶음 기록 id. null 이면 펼친 묶음 없음. */
    val expandedBatch: StateFlow<String?> = _expandedBatch.asStateFlow()

    /** 고른 것이 하나도 없는지 — 선택 패널을 띄울지 판단한다. */
    val isEmpty: Boolean
        get() = _records.value.isEmpty() && _media.value.isEmpty()

    /** 기록 하나를 골랐다 풀었다 한다. */
    fun toggleRecord(recordId: String) {
        _records.value = _records.value.toggle(recordId)
    }

    /** 미디어 한 장을 골랐다 풀었다 한다. */
    fun toggleMedia(mediaId: String) {
        _media.value = _media.value.toggle(mediaId)
    }

    /** 묶음을 펼쳤다 접었다 한다. 선택은 건드리지 않는다. */
    fun toggleExpand(recordId: String) {
        _expandedBatch.value = if (_expandedBatch.value == recordId) null else recordId
    }

    /**
     * 선택 중에 묶음을 탭했을 때의 동작.
     *
     * 이미 전부 골라 뒀으면 전체 해제하고 펼친 것도 접는다. 그렇지 않으면 전체 선택하고 펼친다.
     * 일부만 골라 둔 상태에서 탭하면 나머지가 채워지는 쪽이 눌러서 되돌리는 것보다 자연스럽다.
     *
     * @param mediaIds 그 묶음에 든 미디어 id 전체
     */
    fun toggleBatch(recordId: String, mediaIds: List<String>) {
        val current = _media.value
        val all = mediaIds.toSet()
        if (all.isNotEmpty() && current.containsAll(all)) {
            _media.value = current - all
            if (_expandedBatch.value == recordId) _expandedBatch.value = null
        } else {
            _media.value = current + all
            _expandedBatch.value = recordId
        }
    }

    /**
     * 묶음을 길게 눌렀을 때의 동작 — 전체가 선택된 채로 패널을 연다.
     *
     * 이미 펼쳐 둔 묶음을 다시 길게 누르면 접기만 한다(선택은 유지).
     */
    fun enterBatch(recordId: String, mediaIds: List<String>) {
        if (_expandedBatch.value == recordId) {
            _expandedBatch.value = null
            return
        }
        _media.value = _media.value + mediaIds
        _expandedBatch.value = recordId
    }

    /** 선택과 펼친 묶음을 모두 비운다. */
    fun clear() {
        _records.value = emptySet()
        _media.value = emptySet()
        _expandedBatch.value = null
    }

    private fun Set<String>.toggle(id: String): Set<String> = if (id in this) this - id else this + id
}

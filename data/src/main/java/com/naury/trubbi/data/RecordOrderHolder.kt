package com.naury.trubbi.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 앨범이 지금 보여 주고 있는 기록 순서를 상세 화면과 공유한다.
 *
 * 정렬·필터·검색이 적용된 뒤의 순서를 담으므로, 상세에서 좌우로 넘길 때 목록과 같은 순서가 된다.
 */
@Singleton
class RecordOrderHolder @Inject constructor() {
    val orderedRecordIds = MutableStateFlow<List<String>>(emptyList())
}

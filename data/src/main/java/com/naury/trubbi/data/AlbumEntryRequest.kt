package com.naury.trubbi.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 다른 화면(주로 홈)이 앨범 탭에 한 번만 전달하는 진입 요청.
 *
 * 앨범이 열릴 때 필터를 미리 걸어 두거나 검색창을 펴 둔다. 앨범이 읽고 나면 스스로 비운다.
 */
@Singleton
class AlbumEntryRequest @Inject constructor() {
    /** 미리 적용할 종류 필터 (AlbumFilter 이름). */
    val requestedFilter = MutableStateFlow<String?>(null)

    /** 미리 적용할 용도 필터. */
    val requestedPurpose = MutableStateFlow<String?>(null)

    /** 진입하자마자 검색창을 펼칠지. */
    val openSearch = MutableStateFlow(false)
}

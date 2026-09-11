package com.naury.trubbi.domain.model

import androidx.annotation.StringRes

/**
 * 화면에 보여줄 이름을 문자열 리소스로 갖는 열거형에 붙이는 표식.
 *
 * 라벨을 하드코딩하지 않고 리소스 id 만 들고 있게 해서, 실제 문구 해석은
 * `Labeled.label(context)` / `Labeled.label()` (컴포저블) 확장에서 한 번만 처리한다.
 */
interface Labeled {
    @get:StringRes
    val labelRes: Int
}

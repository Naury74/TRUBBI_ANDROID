package com.naury.trubbi.core.ui

/**
 * Compose 밖(알림·위젯·지도 핀·플로팅 버튼)에서 쓰는 브랜드 색(ARGB).
 *
 * 앱 화면의 같은 색은 app 모듈 `ui/theme/Color.kt` 에 있다. 브랜드를 바꿀 때 둘을 함께 바꾼다.
 */
object BrandPalette {
    /** 브랜드 파랑. 알림 강조색, 위젯 주 버튼, 현재 정차지 핀. */
    const val PRIMARY: Int = 0xFF2457F5.toInt()

    /** 강조 앰버. 파랑과 구분해야 하는 표시(진행 중 단계 등). */
    const val ACCENT: Int = 0xFFF5A524.toInt()

    /** 짙은 남색 글자·바탕. */
    const val INK: Int = 0xFF0B1426.toInt()
}

package com.naury.trubbi.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 색 토큰. 이름은 `역할 + 밝기` 규칙을 따르며, 실제 매핑은 [TrubbiTheme] 에서 한다.
 *
 * 브랜드는 "나이트 드라이브 블루"(파랑 #2457F5 + 앰버 #F5A524, 2026-10 리브랜딩)다. Compose 밖(위젯·알림)에서
 * 쓰는 같은 값은 `core` 의 BrandPalette 에 있다. 둘을 함께 바꾼다.
 */

val MonoInkLight = Color(0xFF0B1426)
val MonoInkDark = Color(0xFFEEF2FA)
val MonoOnInkLight = Color(0xFFFFFFFF)
val MonoOnInkDark = Color(0xFF0B1426)

val MonoGreenLight = Color(0xFF1F8A5B)
val MonoGreenDark = Color(0xFF5FD49A)
val MonoAmberLight = Color(0xFF6B7489)
val MonoAmberDark = Color(0xFFDDE3EE)

val MonoCanvasLight = Color(0xFFF3F5FA)
val MonoSurfaceLight = Color(0xFFFFFFFF)
val MonoTextLight = Color(0xFF0B1426)
val MonoSecondaryLight = Color(0xFF55607A)
val MonoSeparatorLight = Color(0xFFDDE3EE)

val MonoBgDark = Color(0xFF0A0F1C)
val MonoSurfaceDark = Color(0xFF131A2A)
val MonoSurfaceVariantDark = Color(0xFF1C2539)
val MonoTextDark = Color(0xFFEEF2FA)
val MonoSecondaryDark = Color(0xFFAAB4CB)
val MonoSeparatorDark = Color(0xFF263049)

/** 브랜드 파랑. 흰 글자와 5.2:1. */
val BrandBlue = Color(0xFF2457F5)
val BrandBluePressed = Color(0xFF1A44D6)

/** 어두운 바탕용 파랑. 어두운 글자와 함께 쓴다. */
val BrandBlueDark = Color(0xFF7C9DFF)
val BrandBlueSoft = Color(0xFFA9BEFF)
val OnBrandLight = Color(0xFFFFFFFF)
val OnBrandDark = Color(0xFF0B1426)

val BrandGradStart = Color(0xFF3D6BFF)
val BrandGradEnd = Color(0xFF1E46D8)

/** 강조 앰버 — 진행 중 단계, 주의가 필요한 숫자처럼 파랑과 구분해야 하는 곳. */
val SignalAmber = Color(0xFFF5A524)
val SignalAmberDark = Color(0xFFFFC15A)

val CoolCanvasLight = Color(0xFFF3F5FA)
val CoolBgLight = Color(0xFFEEF2F9)
val CoolTintLight = Color(0xFFE6ECFF)
val CoolSurfaceVariantLight = Color(0xFFE8EDF7)
val CoolTintDark = Color(0xFF18223D)

val AuroraRose = Color(0xFFE11D48)
val AuroraRoseLight = Color(0xFFFB7185)

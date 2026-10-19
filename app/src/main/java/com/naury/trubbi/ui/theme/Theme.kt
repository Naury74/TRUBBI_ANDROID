package com.naury.trubbi.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 앱 테마.
 */

/** Material3 `ColorScheme` 에 자리가 없는 토큰들. [LocalTrubbiColors] 로 꺼내 쓴다. */
data class TrubbiColors(
    val isDark: Boolean,
    val orange: Color,
    val green: Color,
    val red: Color,
    val accent2: Color,
    val gradient: List<Color>,
    val tintSurface: Color,
    val groupedBackground: Color,
    val separator: Color,
    val secondaryLabel: Color,
    val glassFill: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
)

private val LightExtras = TrubbiColors(
    isDark = false,
    orange = BrandAmber,
    green = MonoGreenLight,
    red = AuroraRose,
    accent2 = BrandAmberPressed,
    gradient = listOf(BrandGradStart, BrandGradEnd),
    tintSurface = WarmTintLight,
    groupedBackground = WarmBgLight,
    separator = MonoSeparatorLight,
    secondaryLabel = MonoSecondaryLight,
    glassFill = MonoSurfaceLight,
    glassBorder = MonoSeparatorLight,
    glassHighlight = Color.Transparent,
)

private val DarkExtras = TrubbiColors(
    isDark = true,
    orange = BrandAmberDark,
    green = MonoGreenDark,
    red = AuroraRoseLight,
    accent2 = BrandAmberSoft,
    gradient = listOf(BrandGradStart, BrandGradEnd),
    tintSurface = WarmTintDark,
    groupedBackground = MonoBgDark,
    separator = MonoSeparatorDark,
    secondaryLabel = MonoSecondaryDark,
    glassFill = MonoSurfaceDark,
    glassBorder = MonoSeparatorDark,
    glassHighlight = Color.Transparent,
)

/** 어디서든 [TrubbiColors] 를 꺼내는 통로. [TrubbiTheme] 안에서만 값이 채워진다. */
val LocalTrubbiColors = staticCompositionLocalOf { LightExtras }

private val LightColorScheme = lightColorScheme(
    primary = BrandAmber,
    onPrimary = OnAmberLight,
    primaryContainer = WarmTintLight,
    onPrimaryContainer = BrandAmberPressed,
    secondary = MonoInkLight, // 보조 강조는 잉크(가독성 높은 텍스트/아이콘)
    onSecondary = MonoOnInkLight,
    tertiary = BrandAmberPressed,
    background = WarmCanvasLight,
    onBackground = MonoTextLight,
    surface = MonoSurfaceLight,
    onSurface = MonoTextLight,
    surfaceVariant = WarmSurfaceVariantLight,
    onSurfaceVariant = MonoSecondaryLight,
    outline = MonoSeparatorLight,
    error = AuroraRose,
    onError = Color.White,
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandAmberDark,
    onPrimary = OnAmberDark,
    primaryContainer = WarmTintDark,
    onPrimaryContainer = BrandAmberSoft,
    secondary = MonoInkDark,
    onSecondary = MonoOnInkDark,
    tertiary = BrandAmberSoft,
    background = MonoBgDark,
    onBackground = MonoTextDark,
    surface = MonoSurfaceDark,
    onSurface = MonoTextDark,
    surfaceVariant = MonoSurfaceVariantDark,
    onSurfaceVariant = MonoSecondaryDark,
    outline = MonoSeparatorDark,
    error = AuroraRoseLight,
    onError = Color.White,
)

/**
 * @param darkTheme 다크 테마로 그릴지. 기본은 시스템 설정을 따른다
 * @param content 테마 아래에서 그릴 내용
 */
@Composable
fun TrubbiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extras = if (darkTheme) DarkExtras else LightExtras

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalTrubbiColors provides extras) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = TrubbiShapes,
        ) {
            ProvideKinetica(darkTheme, extras, content)
        }
    }
}

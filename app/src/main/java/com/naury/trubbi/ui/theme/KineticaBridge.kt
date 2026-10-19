package com.naury.trubbi.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.KineticaColors
import com.naury.kinetica.ui.KineticaDesign
import com.naury.kinetica.ui.KineticaShapes
import com.naury.kinetica.ui.KineticaTheme
import com.naury.kinetica.ui.LocalKineticaFillContrast
import com.naury.kinetica.ui.SurfaceStyle

/**
 * Trubbi 팔레트·모서리·글꼴을 Kinetica 컴포넌트에도 입힌다.
 *
 * MaterialTheme 안쪽에 둔다(Kinetica 안에서 MaterialTheme 을 다시 감싸면 물결 효과가 돌아온다). 다크 여부는 시스템이
 * 아니라 앱이 정하므로(밤·궂은 날씨에도 어둡게) 그대로 넘긴다. 눌림 표시는 기존 요소가 쓰는 `pressable` 의 크기 효과를
 * 지키도록 호스트 값을 둔다.
 */
@Composable
internal fun ProvideKinetica(darkTheme: Boolean, extras: TrubbiColors, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val surface = remember(scheme, extras) {
        // 지금 화면의 유리 카드는 블러 없이 옅은 면 + 테두리 + 그림자로 그린다. 같은 모양을 단색 표면으로 맞춘다.
        SurfaceStyle.Solid(
            color = scheme.surface,
            contentColor = scheme.onSurface,
            background = scheme.background,
            border = extras.separator,
            borderWidth = 1.dp,
            elevation = 2.dp,
            contentMuted = extras.secondaryLabel,
            raised = scheme.surfaceVariant,
        )
    }
    val colors = remember(scheme, extras) {
        // 켜진 스위치도 브랜드 색으로 칠해 앱의 다른 강조와 같게 보인다.
        KineticaColors(
            accent = scheme.primary,
            onAccent = scheme.onPrimary,
            positive = scheme.primary,
            onPositive = scheme.onPrimary,
            destructive = extras.red,
            onDestructive = scheme.onError,
        )
    }
    val design = remember {
        KineticaDesign(shapes = KineticaShapes(medium = 14.dp, large = 20.dp, extraLarge = 24.dp, huge = 30.dp))
    }
    KineticaTheme(
        darkTheme = darkTheme,
        surface = surface,
        colors = colors,
        textStyle = MaterialTheme.typography.bodyLarge,
        indication = null,
        design = design,
    ) {
        // 브랜드 주황(#F5871F) 위 흰 글자는 4.5:1 에 못 미친다. 4.5 기준이면 채운 면이 갈색으로 어두워지므로
        // FrameKit 과 같은 3:1 기준으로 둔다.
        CompositionLocalProvider(LocalKineticaFillContrast provides FILL_CONTRAST, content = content)
    }
}

private const val FILL_CONTRAST = 3f

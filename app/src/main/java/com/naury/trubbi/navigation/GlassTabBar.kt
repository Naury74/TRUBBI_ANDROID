package com.naury.trubbi.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.trubbi.R
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.TrubbiMotion
import com.naury.trubbi.ui.designsystem.liquidGlass

/**
 * 하단 탭바/사이드 레일에 노출되는 최상위 목적지.
 *
 * @property route 네비게이션 경로
 * @property labelRes 탭 이름 (다국어)
 * @property icon 탭 아이콘
 */
enum class TrubbiTab(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME(Routes.HOME, R.string.tab_home, AppIcons.Home),
    SETTINGS(Routes.SETTINGS, R.string.tab_settings, AppIcons.Settings),
}

@Composable
fun GlassTabBar(
    currentRoute: String?,
    onSelect: (TrubbiTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .fillMaxWidth()
            .liquidGlass(RoundedCornerShape(percent = 50), elevation = 16.dp)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrubbiTab.entries.forEach { tab ->
            TabItem(
                tab = tab,
                selected = currentRoute == tab.route,
                onClick = { onSelect(tab) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TabItem(
    tab: TrubbiTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val faded = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    val progress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = TrubbiMotion.bouncy(),
        label = "tabSel",
    )
    val selection = progress.coerceIn(0f, 1f)
    val color = lerp(faded, primary, selection)
    val label = stringResource(tab.labelRes)
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(primary.copy(alpha = 0.14f * selection))
                .padding(horizontal = 18.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                tab.icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier
                    .size(26.dp)
                    .graphicsLayer {
                        val scale = 1f + 0.10f * selection
                        scaleX = scale
                        scaleY = scale
                    },
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = color,
        )
    }
}

/** 태블릿·넓은 화면용 세로 레일. 하단 탭바와 같은 모양을 세로로 세운 것이다. */
@Composable
fun TrubbiNavRail(
    currentRoute: String?,
    onSelect: (TrubbiTab) -> Unit,
) {
    Column(
        Modifier
            .statusBarsPadding()
            .fillMaxHeight()
            .width(96.dp)
            .padding(8.dp)
            .liquidGlass(RoundedCornerShape(28.dp), elevation = 16.dp)
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TrubbiTab.entries.forEach { tab ->
            TabItem(
                tab = tab,
                selected = currentRoute == tab.route,
                onClick = { onSelect(tab) },
            )
        }
    }
}

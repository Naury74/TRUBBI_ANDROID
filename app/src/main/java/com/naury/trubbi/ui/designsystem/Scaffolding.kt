package com.naury.trubbi.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naury.trubbi.R
import com.naury.trubbi.ui.theme.LocalTrubbiColors

/**
 * 화면 상단 뼈대 — 큰 제목, 상단 바, 설정 행, 통계 알약.
 */

/**
 * 화면 맨 위의 큰 제목.
 *
 * @param title 제목
 * @param modifier 바깥 배치용
 * @param subtitle 제목 아래 보조 설명(선택)
 * @param leading 제목 왼쪽에 그릴 내용(선택)
 * @param trailing 제목 오른쪽에 그릴 내용(선택)
 */
@Composable
fun TrubbiLargeTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = LocalTrubbiColors.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.secondaryLabel,
                )
            }
        }
        if (trailing != null) trailing()
    }
}

/**
 * 하위 화면의 상단 바.
 *
 * @param title 가운데 제목
 * @param onBack 주면 왼쪽에 뒤로 버튼이 생긴다
 * @param modifier 바깥 배치용
 * @param actions 오른쪽에 놓을 버튼들(선택)
 */
@Composable
fun GlassTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            GlassIconButton(
                icon = AppIcons.ArrowBackIos,
                onClick = onBack,
                contentDescription = stringResource(R.string.action_back),
                size = 40.dp,
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (actions != null) actions()
    }
}

/**
 * 설정·이동에 쓰는 한 줄. 왼쪽 아이콘 칩과 오른쪽 화살표가 붙는다.
 *
 * @param title 행 제목
 * @param modifier 바깥 배치용
 * @param subtitle 제목 아래 설명(선택)
 * @param leadingIcon 왼쪽 아이콘(선택)
 * @param leadingTint 왼쪽 아이콘 색
 * @param trailingText 화살표 앞에 붙는 현재 값(선택)
 * @param showChevron 오른쪽 화살표를 그릴지. 눌리지 않는 행이면 끈다
 * @param showDivider 아래 구분선을 그릴지. 섹션 마지막 행에서는 끈다
 * @param onClick 눌렀을 때. null 이면 눌리지 않는다
 */
@Composable
fun NavRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    leadingTint: Color = MaterialTheme.colorScheme.primary,
    trailingText: String? = null,
    showChevron: Boolean = true,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val c = LocalTrubbiColors.current
    Column {
        Row(
            (if (onClick != null) modifier.clickable { onClick() } else modifier)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(leadingTint),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(leadingIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.labelMedium, color = c.secondaryLabel)
                }
            }
            if (trailingText != null) {
                Text(trailingText, style = MaterialTheme.typography.bodyMedium, color = c.secondaryLabel)
                Spacer(Modifier.width(4.dp))
            }
            if (showChevron && onClick != null) {
                Icon(
                    AppIcons.KeyboardArrowRight,
                    contentDescription = null,
                    tint = c.separator.copy(alpha = 1f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (showDivider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = if (leadingIcon != null) 58.dp else 16.dp)
                    .height(0.6.dp)
                    .background(c.separator),
            )
        }
    }
}

/**
 * 숫자 하나를 크게 보여 주는 알약. 홈의 종류별 개수에 쓴다.
 *
 * @param value 크게 보여 줄 값
 * @param label 값 아래 설명
 * @param tint 강조색
 * @param modifier 바깥 배치용
 * @param active false 면 전체를 흐리게 그린다(개수 0 등)
 */
@Composable
fun StatPill(
    value: String,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    active: Boolean = true,
) {
    val c = LocalTrubbiColors.current
    val shape = RoundedCornerShape(22.dp)
    val emphasis = if (active) 1f else 0.4f
    Column(
        modifier
            .clip(shape)
            .background(tint.copy(alpha = (if (c.isDark) 0.18f else 0.10f) * emphasis))
            .border(1.dp, tint.copy(alpha = (if (c.isDark) 0.30f else 0.20f) * emphasis), shape)
            .padding(horizontal = 6.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            value,
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            color = tint.copy(alpha = emphasis),
        )
        // 다섯 칸이 한 줄에 들어가므로 좁은 화면·긴 언어(영어 "Document")에서는 글자를 줄여 한 줄을 지킨다.
        val labelStyle = MaterialTheme.typography.labelMedium
        BasicText(
            label,
            style = labelStyle.copy(color = c.secondaryLabel.copy(alpha = if (active) 1f else 0.55f)),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = labelStyle.fontSize),
        )
    }
}

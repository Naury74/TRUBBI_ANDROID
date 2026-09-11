package com.naury.trubbi.ui.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.naury.trubbi.ui.theme.LocalTrubbiColors
import dev.chrisbanes.haze.HazeState

/**
 * 공용 표면과 그 위에 올라가는 기본 조각들.
 *
 * 배경 블러는 [LocalHazeState] 가 있을 때만 쓴다. 구형 기기에서 블러가 프레임을 크게
 * 잡아먹어, 없으면 반투명 처리로 대신한다.
 */

/**
 * 카드·시트용 표면.
 *
 * @param shape 잘라 낼 모양
 * @param elevation 그림자 크기
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(20.dp),
    elevation: Dp = 6.dp,
): Modifier = glassSurface(shape, elevation)

/** 값이 있으면 [glass] 가 뒤 내용을 실제로 블러 처리한다. 없으면 블러를 생략한다. */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * 목록 섹션·상단 바처럼 넓은 면에 쓰는 표면.
 *
 * @param shape 잘라 낼 모양
 * @param elevation 그림자 크기
 */
@Composable
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(22.dp),
    elevation: Dp = 8.dp,
): Modifier = glassSurface(shape, elevation)

/** [glass]·[liquidGlass]·원형 버튼이 함께 쓰는 실제 그리기. */
@Composable
private fun Modifier.glassSurface(
    shape: Shape,
    elevation: Dp,
): Modifier {
    val c = LocalTrubbiColors.current
    val veil = if (c.isDark) Color(0xFF1C1C1E) else Color.White
    val veilAlpha = if (c.isDark) 0.82f else 0.86f
    val highlight = if (c.isDark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.45f)
    return this
        .shadow(elevation, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.05f), spotColor = Color.Black.copy(alpha = 0.12f))
        .clip(shape)
        .background(veil.copy(alpha = veilAlpha))
        .background(Brush.verticalGradient(listOf(highlight, Color.Transparent)))
        .border(1.dp, c.separator.copy(alpha = 0.5f), shape)
}

/**
 * 아이콘 + 라벨 + 값 타일.
 *
 * @param icon 왼쪽 아이콘
 * @param label 작은 설명 문구
 * @param value 크게 보여 줄 값
 * @param accent 아이콘 색
 * @param modifier 바깥 배치용
 */
@Composable
fun StatTile(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val c = LocalTrubbiColors.current
    Row(
        modifier.glass(RoundedCornerShape(18.dp)).padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(listOf(accent.copy(alpha = if (c.isDark) 0.32f else 0.18f), accent.copy(alpha = if (c.isDark) 0.18f else 0.08f))),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(19.dp))
        }
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = c.secondaryLabel, maxLines = 1)
            Text(
                value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

/**
 * 캡슐 모양 버튼.
 *
 * @param text 버튼 문구
 * @param onClick 눌렀을 때
 * @param modifier 바깥 배치용
 * @param icon 문구 왼쪽 아이콘(선택)
 * @param contentColor 글자·아이콘 색
 */
@Composable
fun GlassPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier
            .glass(RoundedCornerShape(percent = 50), elevation = 4.dp)
            .pressable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = contentColor,
            maxLines = 1,
        )
    }
}

/**
 * 카드 컨테이너.
 *
 * @param modifier 바깥 배치용
 * @param shape 모서리 모양
 * @param onClick 주면 눌리는 카드가 된다
 * @param content 카드 안 내용
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val base = modifier.liquidGlass(shape)
    val withClick = if (onClick != null) base.pressable { onClick() } else base
    Box(withClick) { content() }
}

/**
 * 설정 화면식 묶음 섹션 — 제목 아래에 둥근 카드로 행들을 감싼다.
 *
 * @param modifier 바깥 배치용
 * @param title 섹션 제목. 대문자로 그려진다. null 이면 제목 없이 카드만
 * @param content 카드 안에 넣을 행들
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable () -> Unit,
) {
    val c = LocalTrubbiColors.current
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = c.secondaryLabel,
                modifier = Modifier.padding(start = 20.dp, bottom = 6.dp, top = 4.dp),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            content()
        }
    }
}

/**
 * 탭 전환용 세그먼트 컨트롤. 선택 표시가 스프링으로 미끄러진다.
 *
 * @param options 각 칸에 넣을 문구들
 * @param selectedIndex 선택된 칸. 범위를 벗어나면 안쪽으로 당겨 쓴다
 * @param onSelected 칸을 눌렀을 때 그 인덱스로 불린다
 * @param modifier 바깥 배치용
 * @param onDark 항상 어두운 배경(공유 시트 등) 위에 놓일 때 true
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onDark: Boolean = false,
) {
    val c = LocalTrubbiColors.current
    val n = options.size.coerceAtLeast(1)
    val idx = selectedIndex.coerceIn(0, n - 1)
    val animIndex by animateFloatAsState(
        targetValue = idx.toFloat(),
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
        label = "segIndex",
    )
    BoxWithConstraints(
        modifier
            .height(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (onDark) {
                    Color(0xFF2C2C2E)
                } else if (c.isDark) {
                    Color(0xFF1C1C1E)
                } else {
                    Color(0x14787880)
                },
            )
            .padding(3.dp),
    ) {
        val segWidth = maxWidth / n
        Box(
            Modifier
                .offset(x = segWidth * animIndex)
                .width(segWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(9.dp))
                .background(
                    if (onDark) {
                        Color.White
                    } else if (c.isDark) {
                        Color(0xFF3A3A40)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { index, label ->
                val selected = index == idx
                val textColor by animateColorAsState(
                    when {
                        onDark && selected -> Color.Black
                        onDark -> Color.White.copy(alpha = 0.75f)
                        selected -> MaterialTheme.colorScheme.onSurface
                        else -> c.secondaryLabel
                    },
                    label = "segText",
                )
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(interactionSource = interaction, indication = null) { onSelected(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = textColor,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * 앱의 기본 버튼. 화면마다 주 동작 하나를 크게 보여 주는 자리에 쓴다.
 *
 * @param text 버튼 문구
 * @param onClick 눌렀을 때
 * @param modifier 바깥 배치용
 * @param icon 문구 왼쪽 아이콘(선택)
 * @param enabled false 면 눌리지 않고 흐려진다
 * @param loading true 면 눌리지 않고 진행 표시가 돈다
 * @param leading 아이콘 대신 직접 그릴 앞쪽 내용(선택)
 * @param containerColor 배경색
 * @param contentColor 글자·아이콘 색
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val c = LocalTrubbiColors.current
    val shape = RoundedCornerShape(28.dp)
    val isPrimary = containerColor == MaterialTheme.colorScheme.primary
    val active = enabled && !loading
    val dim = if (active) 1f else 0.5f
    val fill = if (isPrimary) {
        Modifier.background(Brush.horizontalGradient(c.gradient.map { it.copy(alpha = it.alpha * dim) }), shape)
    } else {
        Modifier.background(containerColor.copy(alpha = dim), shape)
    }
    Row(
        modifier
            .then(
                if (isPrimary &&
                    active
                ) {
                    Modifier.shadow(
                        10.dp,
                        shape,
                        clip = false,
                        ambientColor = c.gradient.first().copy(alpha = 0.4f),
                        spotColor = c.gradient.last().copy(alpha = 0.4f),
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .then(fill)
            .pressable(enabled = active, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            androidx.compose.material3.CircularProgressIndicator(
                color = contentColor,
                strokeWidth = 2.2.dp,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(10.dp))
        } else if (leading != null) {
            leading()
            Spacer(Modifier.size(8.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = contentColor,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 원형 아이콘 버튼.
 *
 * 아이콘만 있는 버튼이라 [contentDescription] 이 없으면 TalkBack 이 아무것도 읽지 못한다.
 *
 * @param icon 그릴 아이콘
 * @param onClick 눌렀을 때
 * @param contentDescription 스크린 리더가 읽을 동작 이름. 바로 옆에 같은 뜻의 글자가 있을 때만 null 로 둔다
 * @param modifier 바깥 배치용
 * @param tint 아이콘 색. [Color.White] 는 onSurface 로 치환된다
 * @param size 버튼 지름
 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    size: Dp = 44.dp,
) {
    val c = LocalTrubbiColors.current
    val shape = RoundedCornerShape(percent = 50)
    val iconTint = if (tint == Color.White) MaterialTheme.colorScheme.onSurface else tint
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val veil = if (c.isDark) Color(0xFF1C1C1E) else Color.White
    val veilAlpha = if (c.isDark) 0.82f else 0.86f
    val bg by androidx.compose.animation.animateColorAsState(
        targetValue = if (pressed) {
            if (c.isDark) {
                veil.copy(alpha = 0.94f)
            } else {
                iconTint.copy(alpha = 0.14f).compositeOver(veil.copy(alpha = veilAlpha))
            }
        } else {
            veil.copy(alpha = veilAlpha)
        },
        label = "glassIconBg",
    )
    val circle = modifier
        .size(size)
        .shadow(if (pressed) 1.dp else 4.dp, shape, clip = false)
        .clip(shape)
        .background(bg)
        .border(1.dp, c.separator.copy(alpha = 0.5f), shape)
    Box(
        circle.pressable(interactionSource = interaction, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = iconTint, modifier = Modifier.size(size * 0.45f))
    }
}

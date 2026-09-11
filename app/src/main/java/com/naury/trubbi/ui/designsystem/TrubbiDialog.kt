package com.naury.trubbi.ui.designsystem
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.naury.trubbi.R
import com.naury.trubbi.ui.theme.LocalTrubbiColors

/**
 * 앱의 모든 팝업이 올라타는 바탕 카드.
 *
 * 아래의 용도별 다이얼로그를 쓰고, 이건 직접 부르지 않는다.
 *
 * @param onDismiss 바깥을 눌러 닫을 때
 * @param modifier 바깥 배치용
 * @param content 카드 안 내용
 */
/**
 * 앱의 모든 팝업.
 *
 * Material 기본 `AlertDialog` 대신 하단 시트를 쓴다. 화면 중앙 정렬 다이얼로그는 키보드가
 * 올라올 때 위치가 크게 이동하지만, 하단 고정 시트는 키보드 높이만큼만 밀린다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrubbiPopupCard(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // 시트 컨테이너는 투명하게 두고 카드를 직접 그린다. 하단 고정이라 키보드가 올라와도
    // 위치 이동이 키보드 높이로 제한된다.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            modifier
                .padding(horizontal = 12.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

/** 팝업 머리글 — 뒤로(‹), 제목, 닫기(×). */
@Composable
private fun PopupHeader(title: String, onClose: (() -> Unit)?, centered: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.weight(1f).then(if (centered) Modifier.padding(start = 30.dp) else Modifier),
        )
        if (onClose != null) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClose,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    AppIcons.Close,
                    contentDescription = stringResource(R.string.action_close),
                    tint = LocalTrubbiColors.current.secondaryLabel,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** 팝업 위쪽의 큰 아이콘 배지. 고리가 천천히 부풀었다 줄어든다. */
@Composable
private fun RingIcon(icon: ImageVector, destructive: Boolean = false) {
    val c = LocalTrubbiColors.current
    val accent = if (destructive) c.red else MaterialTheme.colorScheme.primary
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val s by animateFloatAsState(
        targetValue = if (shown) 1f else 0.3f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium),
        label = "ringIcon",
    )
    // 고리가 계속 부풀었다 줄어든다. 정지 화면보다 눈이 먼저 이쪽으로 간다.
    val breathe by rememberInfiniteTransition(label = "ringBreathe").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(92.dp)
                .graphicsLayer {
                    val k = 1f + 0.06f * breathe
                    scaleX = k
                    scaleY = k
                    alpha = 0.55f + 0.45f * (1f - breathe)
                }
                .clip(CircleShape).background(accent.copy(alpha = 0.07f)),
        )
        Box(
            Modifier.size(70.dp)
                .graphicsLayer {
                    val k = 1f + 0.09f * breathe
                    scaleX = k
                    scaleY = k
                }
                .clip(CircleShape).background(accent.copy(alpha = 0.14f)),
        )
        Box(
            Modifier.size(54.dp)
                .graphicsLayer {
                    val k = s * (1f + 0.045f * breathe)
                    scaleX = k
                    scaleY = k
                }
                .clip(CircleShape).background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (destructive) Color.White else MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/**
 * 값 하나를 입력받는 팝업.
 *
 * @param title 제목
 * @param initial 입력칸 초기값
 * @param onDismiss 닫을 때
 * @param onConfirm 확인을 눌렀을 때 입력값과 함께 불린다
 * @param label 입력칸 라벨
 * @param placeholder 입력칸 힌트(선택)
 * @param confirmText 확인 버튼 문구
 * @param icon 제목 옆 아이콘(선택)
 * @param message 입력칸 아래 안내 문구(선택)
 */
@Composable
fun TrubbiInputDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    label: String = stringResource(R.string.field_name),
    placeholder: String? = null,
    confirmText: String = stringResource(R.string.action_save),
    icon: ImageVector? = null,
    message: String? = null,
) {
    var text by remember { mutableStateOf(initial) }
    TrubbiPopupCard(onDismiss = onDismiss) {
        PopupHeader(title, onClose = onDismiss)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            label = { Text(label) },
            placeholder = placeholder?.let { p -> { Text(p) } },
        )
        if (message != null) {
            Text(
                message.sentenceLines(),
                style = MaterialTheme.typography.bodySmall,
                color = LocalTrubbiColors.current.secondaryLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        PillButton(confirmText, onClick = { onConfirm(text) }, modifier = Modifier.fillMaxWidth(), icon = icon)
    }
}

/**
 * 예/아니오를 묻는 팝업.
 *
 * @param title 제목
 * @param message 본문
 * @param onDismiss 취소하거나 바깥을 눌렀을 때
 * @param onConfirm 확인을 눌렀을 때
 * @param confirmText 확인 버튼 문구
 * @param dismissText 취소 버튼 문구
 * @param icon 위쪽 배지 아이콘(선택)
 * @param destructive 삭제처럼 되돌리기 어려운 동작이면 true — 확인 버튼이 빨갛게 바뀐다
 */
@Composable
fun TrubbiConfirmDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmText: String = stringResource(R.string.action_confirm),
    dismissText: String = stringResource(R.string.action_cancel),
    icon: ImageVector? = null,
    destructive: Boolean = false,
) {
    val c = LocalTrubbiColors.current
    TrubbiPopupCard(onDismiss = onDismiss) {
        PopupHeader(title, onClose = onDismiss, centered = icon != null)
        if (icon != null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { RingIcon(icon, destructive) }
        }
        Text(
            message.sentenceLines(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = if (icon != null) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
        PillButton(
            confirmText,
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            containerColor = if (destructive) c.red else MaterialTheme.colorScheme.primary,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                dismissText,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = LocalTrubbiColors.current.secondaryLabel,
            )
        }
    }
}

/**
 * 결과만 알리는 팝업. 버튼 하나.
 *
 * @param title 제목
 * @param message 본문(선택)
 * @param icon 위쪽 배지 아이콘
 * @param buttonText 버튼 문구
 * @param destructive 실패·경고면 true
 * @param onDismiss 닫을 때
 * @param onConfirm 버튼을 눌렀을 때. 기본은 [onDismiss] 와 같다
 */
@Composable
fun TrubbiStatusDialog(
    title: String,
    message: String? = null,
    icon: ImageVector = AppIcons.Check,
    buttonText: String = stringResource(R.string.action_confirm),
    destructive: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit = onDismiss,
) {
    TrubbiPopupCard(onDismiss = onDismiss) {
        PopupHeader("", onClose = onDismiss)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { RingIcon(icon, destructive) }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (message != null) {
            Text(
                message.sentenceLines(),
                style = MaterialTheme.typography.bodySmall,
                color = LocalTrubbiColors.current.secondaryLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(2.dp))
        PillButton(buttonText, onClick = onConfirm, modifier = Modifier.fillMaxWidth())
    }
}

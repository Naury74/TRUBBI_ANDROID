package com.naury.trubbi.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.KAlertDialog
import com.naury.kinetica.ui.KDialogAction
import com.naury.kinetica.ui.KDialogActionStyle
import com.naury.kinetica.ui.KDialogPresentation
import com.naury.kinetica.ui.KSheet
import com.naury.kinetica.ui.KTextField
import com.naury.trubbi.R

// 이 파일의 팝업은 모두 "구성에 있는 동안 열려 있다". 호출한 쪽은 `if (show) TrubbiConfirmDialog(...)` 처럼 띄우고,
// 확인 버튼은 닫지 않고 동작만 알린다(닫을지는 호출한 쪽이 구성에서 빼는 것으로 정한다).
// 바깥 누름·끌어 내림·뒤로 가기·취소만 여기서 닫고, 닫히는 애니메이션이 끝난 뒤 onDismiss 를 부른다.

/**
 * 앱의 모든 시트형 팝업이 올라타는 바탕.
 *
 * 아래에서 올라오고, 끌어 내리거나 바깥을 누르면 닫힌다. 키보드가 올라오면 그만큼만 밀린다.
 *
 * @param onDismiss 바깥을 누르거나 끌어 내려 닫혔을 때. 닫히는 애니메이션이 끝난 뒤 불린다
 * @param modifier 안쪽 내용 배치용
 * @param content 시트 안 내용. 항목 사이는 16dp 로 띄운다
 */
@Composable
fun TrubbiPopupCard(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    var visible by remember { mutableStateOf(true) }
    KSheet(
        visible = visible,
        onDismissRequest = { visible = false },
        onDismissed = onDismiss,
    ) {
        Column(
            modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

/**
 * 내용을 직접 그리는 하단 시트. 화면별 시트(장소 정보, 비용 입력 등)가 쓴다.
 *
 * [TrubbiPopupCard] 와 달리 안쪽 여백을 두지 않는다. 내용이 길면 호출한 쪽이 스크롤을 단다.
 *
 * @param onDismiss 바깥을 누르거나 끌어 내려 닫혔을 때. 닫히는 애니메이션이 끝난 뒤 불린다
 * @param content 시트 안 내용
 */
@Composable
fun TrubbiSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    var visible by remember { mutableStateOf(true) }
    KSheet(visible = visible, onDismissRequest = { visible = false }, onDismissed = onDismiss, content = content)
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
 * @param icon 위쪽 아이콘(선택)
 * @param message 입력칸 위 안내 문구(선택)
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
    var visible by remember { mutableStateOf(true) }
    KAlertDialog(
        visible = visible,
        onDismissRequest = { visible = false },
        title = title,
        message = message?.sentenceLines(),
        icon = icon,
        actions = listOf(
            KDialogAction(confirmText, KDialogActionStyle.Preferred, dismissOnClick = false) { onConfirm(text) },
            KDialogAction(stringResource(R.string.action_cancel), KDialogActionStyle.Cancel),
        ),
        stacked = true,
        keepActionOrder = true,
        presentation = KDialogPresentation.Auto,
        onDismissed = onDismiss,
    ) {
        KTextField(
            value = text,
            onValueChange = { text = it },
            label = label,
            placeholder = placeholder,
            modifier = Modifier.fillMaxWidth(),
        )
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
 * @param icon 위쪽 아이콘(선택)
 * @param destructive 삭제처럼 되돌리기 어려운 동작이면 true — 확인 버튼과 아이콘이 빨갛게 바뀐다
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
    var visible by remember { mutableStateOf(true) }
    KAlertDialog(
        visible = visible,
        onDismissRequest = { visible = false },
        title = title,
        message = message.sentenceLines(),
        icon = icon,
        actions = listOf(
            KDialogAction(
                confirmText,
                if (destructive) KDialogActionStyle.Destructive else KDialogActionStyle.Preferred,
                dismissOnClick = false,
                onClick = onConfirm,
            ),
            KDialogAction(dismissText, KDialogActionStyle.Cancel),
        ),
        stacked = true,
        keepActionOrder = true,
        presentation = KDialogPresentation.Auto,
        onDismissed = onDismiss,
    )
}

/**
 * 결과만 알리는 팝업. 버튼 하나.
 *
 * @param title 제목
 * @param message 본문(선택)
 * @param icon 위쪽 아이콘
 * @param buttonText 버튼 문구
 * @param destructive 실패·경고면 true
 * @param onDismiss 닫을 때
 * @param onConfirm 버튼을 눌렀을 때. 기본은 닫기와 같다
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
    var visible by remember { mutableStateOf(true) }
    // 버튼 동작이 닫기와 같으면 닫히는 애니메이션을 거친 뒤 부른다.
    val closesOnly = onConfirm === onDismiss
    KAlertDialog(
        visible = visible,
        onDismissRequest = { visible = false },
        title = title,
        message = message?.sentenceLines(),
        icon = icon,
        actions = listOf(
            KDialogAction(
                buttonText,
                if (destructive) KDialogActionStyle.Destructive else KDialogActionStyle.Preferred,
                dismissOnClick = closesOnly,
                onClick = if (closesOnly) ({}) else onConfirm,
            ),
        ),
        stacked = true,
        presentation = KDialogPresentation.Auto,
        onDismissed = onDismiss,
    )
}

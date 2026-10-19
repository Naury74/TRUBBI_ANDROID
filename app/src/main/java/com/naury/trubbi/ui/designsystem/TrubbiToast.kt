package com.naury.trubbi.ui.designsystem

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.KSnackbarHost
import com.naury.kinetica.ui.rememberKSnackbarState

/**
 * 커스텀 토스트 컨트롤러.
 *
 * 시스템 토스트는 알림 권한이 꺼져 있으면 아예 표시되지 않아 직접 만들어 쓴다.
 */
class TrubbiToastState {
    /** 지금 보여 줄 문구. null 이면 아무것도 뜨지 않는다. */
    var message by mutableStateOf<String?>(null)
        internal set

    /** 문구를 띄운다. 이미 떠 있으면 새 문구로 갈아 끼운다. */
    fun show(text: String) {
        message = text
    }

    /** 즉시 지운다. 보통은 시간이 지나면 알아서 사라진다. */
    fun clear() {
        message = null
    }
}

@Composable
fun rememberTrubbiToastState(): TrubbiToastState = remember { TrubbiToastState() }

/**
 * 토스트를 그린다. Box 안에 하단 중앙 정렬로 놓는다.
 *
 * 홈의 빠른 실행 바에 가리지 않도록 화면 아래에서 110dp 위에 띄운다. 문구가 바뀌면 떠 있던 것을 바로 바꾼다.
 */
@Composable
fun TrubbiToastHost(state: TrubbiToastState, modifier: Modifier = Modifier) {
    val snackbar = rememberKSnackbarState()
    val msg = state.message
    LaunchedEffect(msg) {
        if (msg != null) {
            snackbar.show(msg, durationMs = TOAST_MS)
            state.clear()
        }
    }
    KSnackbarHost(snackbar, modifier.navigationBarsPadding(), bottomOffset = 110.dp)
}

private const val TOAST_MS = 1800L

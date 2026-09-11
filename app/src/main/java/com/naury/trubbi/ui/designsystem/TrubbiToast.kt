package com.naury.trubbi.ui.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

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

/** 토스트를 그린다. Box 안에 하단 중앙 정렬로 놓는다. */
@Composable
fun TrubbiToastHost(state: TrubbiToastState, modifier: Modifier = Modifier) {
    val msg = state.message
    var last by remember { mutableStateOf("") }
    if (msg != null) last = msg
    LaunchedEffect(msg) {
        if (msg != null) {
            delay(1800)
            state.clear()
        }
    }
    AnimatedVisibility(
        visible = msg != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        Box(
            Modifier
                .navigationBarsPadding()
                .padding(bottom = 110.dp)
                .liquidGlass(RoundedCornerShape(percent = 50), elevation = 12.dp)
                .padding(horizontal = 22.dp, vertical = 12.dp),
        ) {
            Text(
                last,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

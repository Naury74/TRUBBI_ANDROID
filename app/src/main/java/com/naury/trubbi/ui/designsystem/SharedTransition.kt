package com.naury.trubbi.ui.designsystem

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/** NavHost 가 채워 준다. 앨범↔상세 전환에서 썸네일이 자라나는 효과에 쓰인다. */
/**
 * 화면 사이 공유 요소 전환에 필요한 스코프들.
 *
 * 앨범에서 누른 썸네일이 상세 이미지로 자라나게 하려면 두 화면이 같은 전환 스코프를
 * 공유해야 한다. NavHost 가 값을 채우고, 화면들은 이 통로로만 접근한다.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * 기록 id 를 키로 공유 요소 태그를 붙인다. 앨범에서 누른 썸네일이 상세 이미지로 자라나고,
 * 뒤로 가면 다시 제자리로 줄어든다.
 *
 * 스코프가 없으면(공유 전환 밖) 아무 일도 하지 않는다.
 *
 * @param recordId 짝을 맞출 기록 id
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedMedia(recordId: String): Modifier {
    val sts = LocalSharedTransitionScope.current
    val avs = LocalNavAnimatedScope.current
    return if (sts != null && avs != null) {
        with(sts) {
            this@sharedMedia.sharedElement(
                rememberSharedContentState(key = "media-$recordId"),
                animatedVisibilityScope = avs,
                boundsTransform = BoundsTransform { _, _ -> tween(durationMillis = 230) },
            )
        }
    } else {
        this
    }
}

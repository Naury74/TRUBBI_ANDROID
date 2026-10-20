package com.naury.trubbi.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.PillButton
import com.naury.trubbi.ui.designsystem.appearOnce

/**
 * 앱을 처음 켰을 때 한 번만 보이는 환영 화면.
 *
 * @param onStart 시작 버튼을 눌렀을 때
 */
@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    // 첫 화면에서는 뒤로가기를 무시한다. 진행 경로는 시작 버튼뿐이다.
    BackHandler { }

    val primary = MaterialTheme.colorScheme.primary

    val pop = remember { Animatable(0.5f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 260f)) }
    val floatT = rememberInfiniteTransition(label = "welcomeFloat")
    val floatY by floatT.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "floatY",
    )
    // 로고 뒤로 퍼지는 고리.
    val halo by floatT.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "halo",
    )

    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(150.dp)
                        .graphicsLayer {
                            val s = 1f + halo * 0.5f
                            scaleX = s
                            scaleY = s
                            alpha = (1f - halo) * 0.25f
                        }
                        .clip(RoundedCornerShape(percent = 50)).background(Color.White),
                )
                com.naury.trubbi.ui.designsystem.TrubbiLogo(
                    wordmarkColor = Color.White,
                    modifier = Modifier.fillMaxWidth(0.56f).graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                        translationY = floatY
                        alpha = pop.value.coerceIn(0f, 1f)
                    },
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(
                stringResource(R.string.welcome_tagline),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                modifier = Modifier.appearOnce(250),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.welcome_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.72f),
                textAlign = TextAlign.Center,
                modifier = Modifier.appearOnce(400),
            )

            Spacer(Modifier.height(36.dp))
            FeatureRow(AppIcons.Truck, stringResource(R.string.welcome_point1_title), stringResource(R.string.welcome_point1_sub), 600)
            Spacer(Modifier.height(14.dp))
            FeatureRow(AppIcons.Description, stringResource(R.string.welcome_point2_title), stringResource(R.string.welcome_point2_sub), 760)
            Spacer(Modifier.height(14.dp))
            FeatureRow(AppIcons.Backup, stringResource(R.string.welcome_point3_title), stringResource(R.string.welcome_point3_sub), 920)
        }

        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(horizontal = 28.dp, vertical = 28.dp).appearOnce(1100),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PillButton(
                stringResource(R.string.welcome_start),
                onStart,
                Modifier.fillMaxWidth(),
                containerColor = Color.White,
                contentColor = primary,
            )
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.welcome_sign_in_hint), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, body: String, delay: Int) {
    Row(
        Modifier.fillMaxWidth().appearOnce(delay),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
        }
    }
}

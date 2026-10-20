package com.naury.trubbi.ui.designsystem

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.naury.kinetica.ui.KButton
import com.naury.kinetica.ui.KButtonSize
import com.naury.kinetica.ui.KSurface
import com.naury.kinetica.ui.KVariant
import com.naury.trubbi.R
import com.naury.trubbi.core.voice.SpeechFailure
import com.naury.trubbi.core.voice.SpeechInputState
import com.naury.trubbi.ui.theme.LocalTrubbiColors

/** 음성 입력이 실패한 이유를 기사에게 보여 줄 문구. */
@StringRes
fun SpeechFailure.messageRes(): Int = when (this) {
    SpeechFailure.NO_SPEECH, SpeechFailure.NO_MATCH -> R.string.voice_retry
    SpeechFailure.NO_PERMISSION -> R.string.voice_need_mic
    SpeechFailure.NETWORK -> R.string.voice_network
    SpeechFailure.LANGUAGE -> R.string.voice_language
    SpeechFailure.UNAVAILABLE -> R.string.voice_unavailable
}

/** 마이크 권한이 있는지. */
fun Context.hasMicPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

/**
 * 마이크 권한을 확인하고 듣기를 시작하는 함수를 돌려준다.
 *
 * 권한이 있으면 바로 [listen] 을 부르고, 없으면 먼저 묻는다. 거절하면 [onDenied] 로 알린다(버튼으로 하면 된다).
 */
@Composable
fun rememberMicStarter(onDenied: () -> Unit = {}, listen: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentListen by rememberUpdatedState(listen)
    val currentDenied by rememberUpdatedState(onDenied)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) currentListen() else currentDenied()
    }
    return {
        if (context.hasMicPermission()) currentListen() else launcher.launch(Manifest.permission.RECORD_AUDIO)
    }
}

/**
 * 듣는 동안 보여 주는 카드. 말하는 대로 글자가 찍히고(부분 인식), 목소리 크기에 맞춰 마이크가 커진다.
 *
 * 받아쓰기가 실시간으로 보이면 기사가 잘못 들린 것을 바로 알아채고 다시 말할 수 있다.
 *
 * @param speech 음성 입력 상태
 * @param hint 아직 말하기 전에 보여 줄 예시
 * @param onStop 그만 듣기
 */
@Composable
fun VoiceListeningCard(speech: SpeechInputState, hint: String, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val level by animateFloatAsState(speech.level, label = "voiceLevel")
    KSurface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(52.dp)
                        .scale(0.7f + 0.3f * level)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                )
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    speech.partial.ifBlank { stringResource(R.string.voice_listening) },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (speech.partial.isBlank()) {
                    Text(hint, style = MaterialTheme.typography.bodySmall, color = LocalTrubbiColors.current.secondaryLabel)
                }
            }
            KButton(stringResource(R.string.voice_stop), onStop, size = KButtonSize.Small, variant = KVariant.Tonal)
        }
    }
}

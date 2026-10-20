package com.naury.trubbi.feature.journey

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.naury.trubbi.R
import com.naury.trubbi.core.voice.SpeechInputState
import com.naury.trubbi.core.voice.rememberSpeechInput
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.PillButton
import com.naury.trubbi.ui.designsystem.TrubbiSheet
import com.naury.trubbi.ui.designsystem.pressable
import com.naury.trubbi.ui.theme.LocalTrubbiColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 도착 직후 작업을 바로 시작할지 묻는 시트와, 그 답을 음성으로 받는 처리.
 */
/**
 * 도착 직후 "작업을 바로 시작하시나요?"를 묻는 시트.
 *
 * 열리는 즉시 음성으로 질문하고 음성 응답을 받는다. 같은 선택지가 버튼으로도 항상 함께 표시된다.
 *
 * @param onStart 바로 작업을 시작한다
 * @param onWait 대기 단계에 그대로 둔다
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArrivalPromptSheet(
    kindLabel: String,
    stopName: String,
    speak: (String) -> Unit,
    onStart: () -> Unit,
    onWait: () -> Unit,
    speakAwait: suspend (String) -> Unit = { speak(it) },
) {
    val c = LocalTrubbiColors.current
    val speech = rememberSpeechInput()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var attempts by remember { mutableStateOf(0) }
    var done by remember { mutableStateOf(false) }
    val retryHint = stringResource(R.string.arrival_voice_hint)
    val openingPrompt = stringResource(R.string.arrival_voice_prompt, stopName, kindLabel)

    fun finishStart() {
        if (!done) {
            done = true
            speech.cancel()
            onStart()
        }
    }
    fun finishWait() {
        if (!done) {
            done = true
            speech.cancel()
            onWait()
        }
    }

    /**
     * 음성 답을 처리한다.
     *
     * 인식 실패 시 최대 두 번까지 자동으로 다시 듣고, 그 이후에는 버튼 입력을 기다린다.
     */
    fun handle(answer: String) {
        if (done) return
        // 1순위가 "대게"처럼 엉뚱해도 2·3순위에 "대기"가 있는 일이 많아 후보를 차례로 본다.
        val heard = if (answer.isBlank()) emptyList() else speech.alternatives.ifEmpty { listOf(answer) }
        when (heard.firstNotNullOfOrNull(::parseStartWait)) {
            true -> finishStart()
            false -> finishWait()
            else -> {
                if (attempts >= MAX_VOICE_RETRIES) return
                attempts++
                val spokeButUnclear = answer.isNotBlank()
                scope.launch {
                    if (spokeButUnclear) {
                        speakAwait(retryHint)
                        delay(250)
                    } else {
                        delay(500)
                    }
                    if (!done) startListenSafely(speech, context) { handle(it) }
                }
            }
        }
    }

    val audioPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted && !done) speech.start(ARRIVAL_HINTS) { handle(it) } }

    // 클라우드 TTS 는 지연이 있어, 안내가 끝난 뒤에 마이크를 열어야 자기 말을 되받지 않는다.
    LaunchedEffect(Unit) {
        speakAwait(openingPrompt)
        delay(300)
        if (!done) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) {
                speech.start(ARRIVAL_HINTS) { handle(it) }
            } else {
                audioPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
    LaunchedEffect(speech.error) { if (speech.error && !done) handle("") }

    TrubbiSheet(onDismiss = onWait) {
        Column(
            Modifier.padding(horizontal = 22.dp).padding(bottom = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(54.dp).clip(RoundedCornerShape(percent = 50)).background(Brush.linearGradient(c.gradient)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.arrival_title, stopName),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = c.secondaryLabel,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.arrival_question, kindLabel),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            // 어떻게 답하면 되는지는 항상 보여 준다.
            Text(
                when {
                    speech.listening -> speech.partial.ifBlank { stringResource(R.string.arrival_listening) }
                    else -> stringResource(R.string.arrival_hint)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (speech.listening) MaterialTheme.colorScheme.primary else c.secondaryLabel,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            // 세로로 쌓는다. 반반 너비에서는 영어 문구가 잘렸고, 운전석에서는 큰 주 버튼 하나가 누르기 쉽다.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                PillButton(
                    stringResource(R.string.action_start_kind, kindLabel),
                    { finishStart() },
                    Modifier.fillMaxWidth(),
                    icon = AppIcons.PlayArrow,
                )
                PillButton(
                    stringResource(R.string.action_wait),
                    { finishWait() },
                    Modifier.fillMaxWidth(),
                    icon = AppIcons.Pause,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(if (speech.listening) R.string.action_listening else R.string.action_speak_again),
                style = MaterialTheme.typography.labelLarge,
                color = c.secondaryLabel,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .pressable(enabled = !speech.listening) {
                        attempts = 0
                        startListenSafely(speech, context) { handle(it) }
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/** 권한이 있을 때만 듣기를 시작한다. 없으면 조용히 넘어가고 버튼으로 답하면 된다. */
private fun startListenSafely(
    speech: SpeechInputState,
    context: Context,
    onResult: (String) -> Unit,
) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
        == PackageManager.PERMISSION_GRANTED
    ) {
        speech.start(ARRIVAL_HINTS, onResult)
    }
}

/**
 * 도착 물음의 음성 답을 해석한다.
 *
 * 긴 낱말("대기", "시작")은 어미가 붙어 와도 되도록 부분 일치로 본다. 짧은 대답("네", "어", "고", "no", "go")은
 * 다른 말 속에 너무 자주 들어 있어(예: "now" 안의 "no", "그래도" 안의 "어") 낱말 전체가 같을 때만 인정한다.
 *
 * @return true=시작, false=대기, null=알아듣지 못함
 */
internal fun parseStartWait(text: String): Boolean? {
    val lower = text.lowercase().trim()
    if (lower.isBlank()) return null
    val spoken = lower.replace(" ", "")
    val words = lower.split(Regex("[\\s,.!?]+")).filter(String::isNotEmpty).toSet()
    fun hit(stems: List<String>, alone: Set<String>) = stems.any(spoken::contains) || words.any { it in alone } || spoken in alone
    return when {
        hit(WAIT_STEMS, WAIT_ALONE) -> false
        hit(START_STEMS, START_ALONE) -> true
        else -> null
    }
}

// 받아쓰기 결과가 조사·어미까지 붙어 오므로 긴 낱말은 부분 일치로 본다.
private val WAIT_STEMS = listOf(
    "대기", "아니", "이따", "나중", "잠깐", "잠시", "기다", "휴식", "안해", "안할", "조금있다", "멈춰", "스탑", "정지", "보류",
    "wait", "later", "notyet", "hold",
)
private val WAIT_ALONE = setOf("노", "no", "nope", "stop", "쉴게", "쉬자", "쉬어", "아뇨", "아니요")

private val START_STEMS = listOf(
    "시작", "바로", "그래", "좋아", "좋아요", "출발", "할게", "하자", "해줘", "지금", "고고", "오케이", "맞아",
    "start", "yes", "yeah", "okay", "sure", "letsgo",
)
private val START_ALONE = setOf("네", "예", "응", "어", "엉", "웅", "좋", "고", "ok", "go", "yep", "now")

/** 인식기에 미리 알려 줄 대답. */
private val ARRIVAL_HINTS = listOf("시작", "대기", "바로 시작", "나중에", "네", "아니요")

private const val MAX_VOICE_RETRIES = 2

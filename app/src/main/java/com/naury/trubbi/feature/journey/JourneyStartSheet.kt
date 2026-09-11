package com.naury.trubbi.feature.journey

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.trubbi.R
import com.naury.trubbi.data.local.JourneyStopEntity
import com.naury.trubbi.data.repo.JourneyRepository
import com.naury.trubbi.domain.model.StopKind
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.PillButton
import com.naury.trubbi.ui.designsystem.pressable
import com.naury.trubbi.ui.theme.LocalTrubbiColors
import java.util.Locale

/**
 * 정차지를 직접 입력해 여정을 시작하는 시트 — 음성 마법사를 쓰지 않는 경로.
 */
/**
 * 여정 시작 시트.
 *
 * 입력값은 모두 선택이다. 아무것도 채우지 않고 시작할 수 있으며, 추천 칩과 음성 입력으로
 * 키보드 없이 완료할 수 있다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyStartSheet(
    recentStops: List<JourneyStopEntity>,
    onStart: (List<JourneyRepository.StopDraft>) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalTrubbiColors.current
    val context = LocalContext.current
    var pickup by remember { mutableStateOf("") }
    var dropoff by remember { mutableStateOf("") }

    // 시스템 음성 인식 화면을 그대로 쓴다. 별도 권한 없이 동작하고 언어도 기기 설정을 따른다.
    var voiceTarget by remember { mutableStateOf(0) }
    val speech = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val text = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!text.isNullOrBlank()) {
            if (voiceTarget == 0) pickup = text.trim() else dropoff = text.trim()
        }
    }
    fun launchSpeech(target: Int) {
        voiceTarget = target
        runCatching {
            speech.launch(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    putExtra(
                        RecognizerIntent.EXTRA_PROMPT,
                        context.getString(
                            if (target == 0) R.string.wizard_pickup_prompt else R.string.wizard_dropoff_prompt,
                        ),
                    )
                },
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
            Text(
                stringResource(R.string.journey_new_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.journey_sheet_note),
                style = MaterialTheme.typography.bodySmall,
                color = c.secondaryLabel,
            )
            Spacer(Modifier.height(18.dp))

            StopField(
                icon = AppIcons.Pickup,
                placeholder = stringResource(R.string.field_pickup_optional),
                value = pickup,
                onValue = { pickup = it },
                onMic = { launchSpeech(0) },
            )
            Spacer(Modifier.height(10.dp))
            StopField(
                icon = AppIcons.Dropoff,
                placeholder = stringResource(R.string.field_dropoff_optional),
                value = dropoff,
                onValue = { dropoff = it },
                onMic = { launchSpeech(1) },
            )

            // 칩을 누르면 비어 있는 칸부터 채운다.
            if (recentStops.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.frequent_recent_stops), style = MaterialTheme.typography.labelMedium, color = c.secondaryLabel)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(recentStops, key = { _, s -> s.stopId }) { _, s ->
                        val name = s.name ?: return@itemsIndexed
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(percent = 50))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .pressable {
                                    if (pickup.isBlank()) pickup = name else dropoff = name
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            PillButton(
                text = stringResource(R.string.action_start_journey),
                icon = AppIcons.Truck,
                onClick = {
                    val drafts = buildList {
                        if (pickup.isNotBlank()) add(JourneyRepository.StopDraft(StopKind.PICKUP, pickup.trim()))
                        if (dropoff.isNotBlank()) add(JourneyRepository.StopDraft(StopKind.DROPOFF, dropoff.trim()))
                    }
                    onStart(drafts)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun StopField(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    placeholder: String,
    value: String,
    onValue: (String) -> Unit,
    onMic: (() -> Unit)? = null,
    onImeSearch: (() -> Unit)? = null,
) {
    val c = LocalTrubbiColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (c.isDark) 0.55f else 0.8f))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = c.secondaryLabel, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = c.secondaryLabel)
            }
            BasicTextField(
                value = value,
                onValueChange = onValue,
                singleLine = true,
                keyboardOptions = if (onImeSearch != null) {
                    androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search)
                } else {
                    androidx.compose.foundation.text.KeyboardOptions.Default
                },
                keyboardActions = if (onImeSearch != null) {
                    androidx.compose.foundation.text.KeyboardActions(onSearch = { onImeSearch() })
                } else {
                    androidx.compose.foundation.text.KeyboardActions.Default
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (onMic != null) {
            Spacer(Modifier.width(8.dp))
            Icon(
                AppIcons.Mic,
                contentDescription = stringResource(R.string.action_voice_input),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp).pressable(onClick = onMic),
            )
        }
    }
}

// ── 출발 연출 오버레이

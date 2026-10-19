package com.naury.trubbi.feature.journey

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.trubbi.R
import com.naury.trubbi.core.i18n.label
import com.naury.trubbi.core.voice.VoiceAction
import com.naury.trubbi.core.voice.VoiceContext
import com.naury.trubbi.core.voice.VoiceIntent
import com.naury.trubbi.core.voice.rememberSpeechInput
import com.naury.trubbi.data.i18n.displayName
import com.naury.trubbi.data.local.JourneyStopEntity
import com.naury.trubbi.domain.model.StopKind
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.DragReorderColumn
import com.naury.trubbi.ui.designsystem.PillButton
import com.naury.trubbi.ui.designsystem.pressable
import com.naury.trubbi.ui.theme.LocalTrubbiColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 아직 가지 않은 정차지의 순서를 바꾸는 전체 화면 시트.
 *
 * 손잡이 드래그와 음성("○○ 먼저") 두 경로로 순서를 바꾼다.
 * 이미 도착·완료한 정차지는 상단에 고정되며 순서를 바꿀 수 없다.
 *
 * @param onApply 확정된 '아직 안 간' 정차지 id 순서
 * @param speakAwait 안내 음성 (말이 끝날 때까지 대기)
 * @param interpret 인식 문장을 의도로 바꾸는 함수
 * @param transcribe 서버 STT. null 이면 기기 내 인식만 쓴다
 */
@Composable
fun StopReorderSheet(
    stops: List<JourneyStopEntity>,
    onApply: (orderedPendingIds: List<String>) -> Unit,
    onRemove: (String) -> Unit,
    onAddStops: () -> Unit,
    onDismiss: () -> Unit,
    speakAwait: suspend (String) -> Unit = {},
    interpret: suspend (String, VoiceContext) -> VoiceIntent = { _, _ -> VoiceIntent(VoiceAction.NONE) },
    transcribe: (suspend (ByteArray, Int) -> List<String>)? = null,
) {
    val colors = LocalTrubbiColors.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val fixed = remember(stops) { stops.filter { it.arrivedAt != null }.sortedBy { it.seq } }
    // 미방문 정차지만 스냅숏으로 복사해 로컬에서 재배치한다.
    // 편집 중 DB 갱신이 순서를 덮어쓰지 않는다.
    val pending = remember {
        mutableStateListOf<JourneyStopEntity>().also { list ->
            list.addAll(stops.filter { it.arrivedAt == null }.sortedBy { it.seq })
        }
    }
    val speech = rememberSpeechInput(transcribe)

    fun apply() {
        onApply(pending.map { it.stopId })
        onDismiss()
    }

    fun onVoice(text: String) {
        scope.launch {
            val intent = interpret(
                text,
                VoiceContext(step = "reorder", candidates = pending.map { it.displayName(context) }),
            )
            when (intent.action) {
                VoiceAction.CANCEL -> onDismiss()
                VoiceAction.START -> apply()
                VoiceAction.ADD_STOP, VoiceAction.PLACE -> onAddStops()
                VoiceAction.MOVE, VoiceAction.SELECT -> {
                    val target = (intent.place ?: text).trim()
                    // 인식 결과와 정차지 이름이 정확히 같기를 기대할 수 없어 양방향 부분 일치로 찾는다.
                    val index = pending.indexOfFirst { stop ->
                        val name = stop.displayName(context)
                        name.contains(target) ||
                            target.contains(name) ||
                            stop.name?.let { target.contains(it) || it.contains(target) } == true
                    }
                    when {
                        index > 0 -> {
                            val moved = pending.removeAt(index)
                            pending.add(0, moved)
                            speakAwait(resources.getString(R.string.reorder_moved_first, moved.displayName(context)))
                        }

                        index == 0 ->
                            speakAwait(resources.getString(R.string.reorder_already_first, pending[0].displayName(context)))

                        else -> speakAwait(resources.getString(R.string.reorder_not_found, target))
                    }
                }

                else -> Unit
            }
        }
    }

    fun listen() {
        scope.launch {
            speakAwait(resources.getString(R.string.reorder_voice_prompt))
            // 안내가 끝나자마자 마이크를 열면 TTS 꼬리가 그대로 인식된다.
            delay(200)
            speech.start(::onVoice)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).pressable(onClick = onDismiss))
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            ) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(percent = 50)).background(colors.separator))
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.reorder_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Row(
                            Modifier.clip(
                                RoundedCornerShape(percent = 50),
                            ).background(MaterialTheme.colorScheme.surfaceVariant).pressable(onClick = onDismiss).padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(AppIcons.Close, contentDescription = null, tint = colors.secondaryLabel, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.action_close), style = MaterialTheme.typography.labelLarge, color = colors.secondaryLabel)
                        }
                    }

                    Column(
                        Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
                    ) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(if (speech.listening) R.string.reorder_listening else R.string.reorder_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (speech.listening) MaterialTheme.colorScheme.primary else colors.secondaryLabel,
                        )
                        Spacer(Modifier.height(12.dp))

                        // 이미 다녀온 정차지는 흐리게 고정해 둔다.
                        fixed.forEach { stop ->
                            ReorderRow(stop = stop, order = null, dim = true, handle = null, onRemove = null)
                        }
                        DragReorderColumn(
                            items = pending.toList(),
                            keyOf = { it.stopId },
                            rowHeight = 56.dp,
                            onReorder = { newOrder ->
                                pending.clear()
                                pending.addAll(newOrder)
                            },
                        ) { index, stop, handle, _ ->
                            ReorderRow(
                                stop = stop,
                                order = fixed.size + index + 1,
                                dim = false,
                                handle = handle,
                                onRemove = {
                                    val id = stop.stopId
                                    pending.remove(stop)
                                    onRemove(id)
                                },
                            )
                        }
                        if (pending.isEmpty()) {
                            Text(
                                stringResource(R.string.reorder_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.secondaryLabel,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                    }

                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 18.dp).navigationBarsPadding()) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.separator.copy(alpha = 0.5f)))
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PillButton(
                                text = stringResource(if (speech.listening) R.string.action_listening else R.string.action_by_voice),
                                icon = AppIcons.Mic,
                                onClick = ::listen,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f).height(56.dp),
                            )
                            PillButton(
                                text = stringResource(R.string.action_add_stop),
                                icon = AppIcons.Add,
                                onClick = onAddStops,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f).height(56.dp),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        PillButton(
                            text = stringResource(R.string.action_done),
                            icon = AppIcons.Check,
                            onClick = ::apply,
                            modifier = Modifier.fillMaxWidth().height(58.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReorderRow(
    stop: JourneyStopEntity,
    order: Int?,
    dim: Boolean,
    handle: Modifier?,
    onRemove: (() -> Unit)?,
) {
    val colors = LocalTrubbiColors.current
    // 드래그 가능한 행은 컬럼이 잡아 준 높이를 채우고, 고정된 행은 스스로 높이를 정한다.
    val base = if (handle != null) Modifier.fillMaxSize() else Modifier.fillMaxWidth().padding(vertical = 4.dp)
    Row(
        base
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (dim) 0.4f else 0.7f))
            .padding(horizontal = 12.dp, vertical = if (handle != null) 0.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(26.dp).clip(RoundedCornerShape(percent = 50))
                .background(if (dim) colors.separator else MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            if (dim) {
                Icon(AppIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
            } else {
                Text("$order", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onPrimary)
            }
        }
        Spacer(Modifier.width(10.dp))
        Icon(
            if (stop.kindEnum == StopKind.PICKUP) AppIcons.Pickup else AppIcons.Dropoff,
            contentDescription = null,
            tint = colors.secondaryLabel,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "${stop.kindEnum.label()} · ${stop.displayName()}",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = if (dim) colors.secondaryLabel else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (onRemove != null) {
            Icon(
                AppIcons.Close,
                contentDescription = stringResource(R.string.action_delete),
                tint = colors.secondaryLabel,
                modifier = Modifier.size(16.dp).pressable(onClick = onRemove),
            )
            Spacer(Modifier.width(10.dp))
        }
        if (handle != null) {
            Icon(
                AppIcons.DragHandle,
                contentDescription = stringResource(R.string.cd_reorder_handle),
                tint = colors.secondaryLabel,
                modifier = handle.size(24.dp),
            )
        }
    }
}

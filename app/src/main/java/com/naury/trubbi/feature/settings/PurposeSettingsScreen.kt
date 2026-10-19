package com.naury.trubbi.feature.settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naury.trubbi.R
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.GlassTopBar
import com.naury.trubbi.ui.theme.LocalTrubbiColors
import kotlin.math.roundToInt

private val ROW_HEIGHT = 56.dp
private val ROW_GAP = 8.dp

/**
 * 용도 관리 화면 — 목록을 추가·삭제하고 드래그로 순서를 바꾼다.
 *
 * 여기서 정한 순서가 촬영 화면의 용도 칩 순서가 된다.
 *
 * `protected_purposes` 에 정의된 용도(상차·하차)는 여정 단계 판정에 쓰이므로 삭제할 수 없다.
 *
 * @param onBack 뒤로 갈 때
 * @param contentPadding 시스템 바를 피하기 위한 여백
 * @param viewModel 설정 상태
 */
@Composable
fun PurposeSettingsScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val purposes by viewModel.purposes.collectAsStateWithLifecycle()
    // 여정 증빙과 묶여 있어 지울 수 없는 용도. 목록은 저장소가 단독으로 관리한다.
    val protectedPurposes = viewModel.protectedPurposes
    val c = LocalTrubbiColors.current
    var newTag by remember { mutableStateOf("") }

    var dragging by remember { mutableIntStateOf(-1) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val rowTotalPx = with(density) { (ROW_HEIGHT + ROW_GAP).toPx() }

    // 드래그 중엔 로컬 리스트를 즉시 재배치(부드럽게·여러 칸 한 번에), 끝날 때만 저장한다.
    // (매 이동마다 DataStore에 쓰면 비동기 반영이 늦어 '한 칸씩'만 움직이던 문제 해결)
    val items = remember { mutableStateListOf<String>() }
    LaunchedEffect(purposes) {
        if (dragging < 0) {
            items.clear()
            items.addAll(purposes)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        GlassTopBar(title = stringResource(R.string.settings_manage_purposes), onBack = onBack)

        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = contentPadding.calculateBottomPadding() + 24.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(ROW_GAP),
        ) {
            Text(
                stringResource(R.string.purposes_intro_1) +
                    stringResource(R.string.purposes_intro_2),
                style = MaterialTheme.typography.bodySmall,
                color = c.secondaryLabel,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )

            items.forEachIndexed { index, p ->
                val isDragging = index == dragging
                val protectedTag = p.trim() in protectedPurposes
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(ROW_HEIGHT)
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragging) offsetY else 0f }
                        .shadow(if (isDragging) 8.dp else 0.dp, RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isDragging) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        )
                        .padding(start = 6.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .pointerInput(p) {
                                // 드래그 중엔 리스트를 건드리지 않고 이 행만 떠서 움직인다(제스처 끊김 방지).
                                // 손을 떼는 순간 전체 이동량으로 목표 위치를 계산해 '한 번에' 재배치 + 저장.
                                detectDragGestures(
                                    onDragStart = {
                                        dragging = index
                                        offsetY = 0f
                                    },
                                    onDrag = { change, drag ->
                                        change.consume()
                                        offsetY += drag.y
                                    },
                                    onDragEnd = {
                                        val from = dragging
                                        if (from in items.indices) {
                                            val shift = (offsetY / rowTotalPx).roundToInt()
                                            val target = (from + shift).coerceIn(0, items.lastIndex)
                                            if (target != from) {
                                                items.add(target, items.removeAt(from))
                                                viewModel.setPurposes(items.toList())
                                            }
                                        }
                                        dragging = -1
                                        offsetY = 0f
                                    },
                                    onDragCancel = {
                                        dragging = -1
                                        offsetY = 0f
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            AppIcons.DragHandle,
                            contentDescription = stringResource(R.string.cd_reorder_handle),
                            tint = c.secondaryLabel,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Text(
                        p,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).padding(start = 6.dp),
                    )
                    if (protectedTag) {
                        // 여정 연동 용도 — 삭제 불가 표시(자물쇠).
                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                AppIcons.Lock,
                                contentDescription = stringResource(R.string.purposes_cannot_delete),
                                tint = c.separator,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(percent = 50))
                                .clickable { viewModel.removePurpose(p) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(AppIcons.Close, contentDescription = stringResource(R.string.action_delete), tint = c.red, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newTag,
                    onValueChange = { newTag = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.purposes_add_new)) },
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable {
                            viewModel.addPurpose(newTag)
                            newTag = ""
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Add, contentDescription = stringResource(R.string.action_add), tint = MaterialTheme.colorScheme.onPrimary)
                }
            }

            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { viewModel.resetPurposes() }
                    .padding(vertical = 8.dp, horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.purposes_reset), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

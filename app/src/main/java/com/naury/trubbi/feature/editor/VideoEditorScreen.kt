package com.naury.trubbi.feature.editor
import android.net.Uri
import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.naury.trubbi.R
import com.naury.trubbi.core.i18n.label
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.GlassIconButton
import com.naury.trubbi.ui.designsystem.PillButton
import com.naury.trubbi.ui.designsystem.SegmentedControl
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * 영상 편집 화면 — 구간 자르기·회전·반전·속도·해상도.
 *
 * 편집 결과를 미리보기에 실시간으로 반영한다. 내보내기가 몇십 초 걸리는 작업이라,
 * 다 끝난 뒤에야 잘못된 걸 알면 그 시간을 통째로 버리게 된다.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoEditorScreen(
    onClose: () -> Unit,
    onSaved: () -> Unit,
    viewModel: VideoEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val exo = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(viewModel.mediaUri)))
            prepare()
            playWhenReady = false
        }
    }
    var isPlaying by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { exo.release() } }

    // 미리보기에 편집 결과가 바로 반영된다.
    LaunchedEffect(state.muted) { exo.volume = if (state.muted) 0f else 1f }
    LaunchedEffect(state.trimStartMs) { exo.seekTo(state.trimStartMs) }
    // 잘라 낸 구간만 반복 재생한다. 어디를 남겼는지 확인하려면 그 구간이 계속 돌아야 한다.
    LaunchedEffect(isPlaying, state.trimStartMs, state.trimEndMs) {
        while (isPlaying) {
            val end = if (state.trimEndMs > 0) state.trimEndMs else state.durationMs
            if (end > 0 && exo.currentPosition >= end - 30) exo.seekTo(state.trimStartMs)
            delay(120)
        }
    }

    // 넓은 화면에서는 왼쪽에 미리보기, 오른쪽에 도구 패널로 나눈다.
    val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 720

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(AppIcons.Close, onClose, stringResource(R.string.action_close), tint = Color.White, size = 40.dp)
            Text(
                stringResource(R.string.video_editor_title),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            PillButton(
                if (state.saving) stringResource(R.string.video_saving) else stringResource(R.string.action_save),
                onClick = { if (!state.saving && !state.loading) viewModel.save(onSaved) },
            )
        }

        val previewArea: @Composable () -> Unit = {
            BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                val aspect = (if (state.srcWidth > 0 && state.srcHeight > 0) state.srcWidth.toFloat() / state.srcHeight else 16f / 9f)
                    .coerceIn(0.3f, 3f)
                // SurfaceView 는 graphicsLayer 변형을 무시해서 회전·반전이 미리보기에 안 나타난다.
                // 그래서 TextureView 를 쓴다 — 성능은 조금 손해지만 편집 결과가 보이는 게 먼저다.
                BoxWithConstraints(Modifier.aspectRatio(aspect)) {
                    val wpx = constraints.maxWidth.toFloat()
                    val hpx = constraints.maxHeight.toFloat()
                    AndroidView(
                        factory = { ctx -> TextureView(ctx).also { exo.setVideoTextureView(it) } },
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                rotationZ = state.rotation.toFloat()
                                scaleX = if (state.flipH) -1f else 1f
                            },
                    )
                    // Crop guideline (draggable) while the 비율변경 tool is active; otherwise keep the
                    // 잘려 나갈 바깥 영역을 덮어 자르기가 걸려 있음을 계속 표시한다.
                    if (!state.saving) {
                        if (state.tool == VideoTool.CROP) {
                            VideoCropOverlay(
                                crop = state.cropRect,
                                offX = 0f,
                                offY = 0f,
                                drawW = wpx,
                                drawH = hpx,
                                onChange = viewModel::setCropRect,
                                onMove = viewModel::moveCropRect,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else if (!state.cropRect.isFull) {
                            CropMaskOverlay(state.cropRect, wpx, hpx, Modifier.fillMaxSize())
                        }
                    }
                }

                if (!state.saving && state.tool != VideoTool.CROP) {
                    GlassIconButton(
                        if (isPlaying) AppIcons.Pause else AppIcons.PlayArrow,
                        contentDescription = stringResource(
                            if (isPlaying) R.string.action_pause else R.string.detail_play,
                        ),
                        onClick = {
                            if (isPlaying) {
                                exo.pause()
                                isPlaying = false
                            } else {
                                if (exo.currentPosition < state.trimStartMs) exo.seekTo(state.trimStartMs)
                                exo.play()
                                isPlaying = true
                            }
                        },
                        tint = Color.White,
                        size = 64.dp,
                    )
                }

                if (state.saving) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(progress = { state.progress.coerceIn(0.02f, 1f) }, color = Color.White)
                            Text(
                                stringResource(R.string.video_exporting, (state.progress * 100).toInt()),
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        }

        val errorText: @Composable () -> Unit = {
            state.error?.let {
                Text(
                    it,
                    color = Color(0xFFFF6B6B),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
        }

        if (wide) {
            // 2단 배치 — 미리보기와 도구 패널.
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) { previewArea() }
                    errorText()
                }
                VideoToolbar(state, viewModel, onSeek = { exo.seekTo(it) }, side = true)
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) { previewArea() }
            errorText()
            VideoToolbar(state, viewModel, onSeek = { exo.seekTo(it) })
        }
    }
}

@Composable
private fun VideoToolbar(state: VideoEditorState, vm: VideoEditorViewModel, onSeek: (Long) -> Unit, side: Boolean = false) {
    Column(
        (if (side) Modifier.width(380.dp).fillMaxHeight() else Modifier.fillMaxWidth())
            .clip(if (side) RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp) else RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(Color(0xFF1C1C1E))
            .navigationBarsPadding()
            .then(if (side) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SegmentedControl(
            options = VideoTool.entries.map { it.label() },
            selectedIndex = state.tool.ordinal,
            onSelected = { vm.setTool(VideoTool.entries[it]) },
            onDark = true,
        )
        when (state.tool) {
            VideoTool.TRIM -> TrimControls(state, vm, onSeek)
            VideoTool.CROP -> CropControls(state, vm)
            VideoTool.ROTATE -> RotateControls(state, vm)
            VideoTool.SPEED -> SpeedControls(state, vm)
            VideoTool.AUDIO -> AudioControls(state, vm)
            VideoTool.QUALITY -> QualityControls(state, vm)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(AppIcons.Refresh, vm::reset, stringResource(R.string.action_refresh), tint = Color.White, size = 38.dp)
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun TrimControls(state: VideoEditorState, vm: VideoEditorViewModel, onSeek: (Long) -> Unit) {
    Text(stringResource(R.string.video_trim_hint), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
    TrimBar(
        durationMs = state.durationMs,
        startMs = state.trimStartMs,
        endMs = state.trimEndMs,
        onChange = vm::setTrim,
        onChangeFinished = { onSeek(state.trimStartMs) },
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatTime(state.trimStartMs), style = MaterialTheme.typography.labelMedium, color = Color.White)
        Text(
            stringResource(R.string.video_length, formatTime(state.trimEndMs - state.trimStartMs)),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.7f),
        )
        Text(formatTime(state.trimEndMs), style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

/**
 * 구간 자르기 막대. 양 끝을 끌면 길이가 바뀌고, 가운데를 끌면 구간이 통째로 움직인다.
 *
 * 가운데 드래그는 구간 길이를 유지한 채 위치만 이동시킨다.
 */
@Composable
private fun TrimBar(
    durationMs: Long,
    startMs: Long,
    endMs: Long,
    onChange: (Long, Long) -> Unit,
    onChangeFinished: () -> Unit,
) {
    val curStart by rememberUpdatedState(startMs)
    val curEnd by rememberUpdatedState(endMs)
    val dur = durationMs.coerceAtLeast(1L)
    // 최소 구간 길이. 영상 길이를 아직 못 읽었을 때도 범위가 깨지지 않도록 클립보다 크지 않게 잡는다.
    val minLen = (dur / 50L).coerceAtLeast(300L).coerceAtMost(dur)
    val handle = 16.dp
    BoxWithConstraints(Modifier.fillMaxWidth().height(56.dp)) {
        val density = LocalDensity.current
        val fullPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { handle.toPx() }
        val usable = (fullPx - hPx * 2f).coerceAtLeast(1f)
        fun msToX(ms: Long) = hPx + (ms.toFloat() / dur) * usable
        val startX = msToX(curStart)
        val endX = msToX(curEnd)

        Box(
            Modifier.fillMaxWidth().height(6.dp).align(Alignment.CenterStart)
                .clip(RoundedCornerShape(3.dp)).background(Color(0xFF3A3A3C)),
        )

        // 가운데 띠 — 구간 전체를 옮긴다.
        Box(
            Modifier
                .offset { IntOffset(startX.roundToInt(), 0) }
                .width(with(density) { (endX - startX).coerceAtLeast(1f).toDp() })
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x33FFFFFF))
                .border(1.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                .pointerInput(dur, usable) {
                    var acc = 0f
                    var baseS = 0L
                    var baseE = 0L
                    detectDragGestures(
                        onDragStart = {
                            acc = 0f
                            baseS = curStart
                            baseE = curEnd
                        },
                        onDrag = { ch, d ->
                            ch.consume()
                            acc += d.x
                            val len = baseE - baseS
                            val ns = (baseS + (acc / usable * dur).toLong()).coerceIn(0L, (dur - len).coerceAtLeast(0L))
                            onChange(ns, ns + len)
                        },
                        onDragEnd = { onChangeFinished() },
                        onDragCancel = { onChangeFinished() },
                    )
                },
        )

        // 시작 손잡이
        Box(
            Modifier
                .offset { IntOffset((startX - hPx).roundToInt(), 0) }
                .width(handle).fillMaxHeight()
                .clip(RoundedCornerShape(8.dp)).background(Color.White)
                .pointerInput(dur, usable) {
                    var acc = 0f
                    var base = 0L
                    detectDragGestures(
                        onDragStart = {
                            acc = 0f
                            base = curStart
                        },
                        onDrag = { ch, d ->
                            ch.consume()
                            acc += d.x
                            val ms = (base + (acc / usable * dur).toLong()).coerceIn(0L, (curEnd - minLen).coerceAtLeast(0L))
                            onChange(ms, curEnd)
                        },
                        onDragEnd = { onChangeFinished() },
                        onDragCancel = { onChangeFinished() },
                    )
                },
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.width(3.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF1C1C1E))) }

        // 끝 손잡이
        Box(
            Modifier
                .offset { IntOffset(endX.roundToInt(), 0) }
                .width(handle).fillMaxHeight()
                .clip(RoundedCornerShape(8.dp)).background(Color.White)
                .pointerInput(dur, usable) {
                    var acc = 0f
                    var base = 0L
                    detectDragGestures(
                        onDragStart = {
                            acc = 0f
                            base = curEnd
                        },
                        onDrag = { ch, d ->
                            ch.consume()
                            acc += d.x
                            val ms = (base + (acc / usable * dur).toLong()).coerceIn((curStart + minLen).coerceAtMost(dur), dur)
                            onChange(curStart, ms)
                        },
                        onDragEnd = { onChangeFinished() },
                        onDragCancel = { onChangeFinished() },
                    )
                },
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.width(3.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF1C1C1E))) }
    }
}

@Composable
private fun RotateControls(state: VideoEditorState, vm: VideoEditorViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        GlassIconButton(AppIcons.RotateLeft, vm::rotateLeft, stringResource(R.string.action_rotate_left), tint = Color.White)
        GlassIconButton(AppIcons.RotateRight, vm::rotateRight, stringResource(R.string.action_rotate_right), tint = Color.White)
        GlassIconButton(AppIcons.Flip, vm::toggleFlip, stringResource(R.string.action_flip), tint = Color.White)
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(R.string.editor_rotation_state, state.rotation) + if (state.flipH) stringResource(R.string.editor_rotation_flipped) else "",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
        )
    }
}

@Composable
private fun CropControls(state: VideoEditorState, vm: VideoEditorViewModel) {
    Text(stringResource(R.string.video_crop_hint), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(CropRatio.entries) { c ->
            val sel = c == state.cropPreset
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (sel) Color.White else Color(0xFF2C2C2E))
                    .clickable { vm.setCropPreset(c) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(c.label(), style = MaterialTheme.typography.labelLarge, color = if (sel) Color.Black else Color.White)
            }
        }
    }
}

@Composable
private fun SpeedControls(state: VideoEditorState, vm: VideoEditorViewModel) {
    Text(stringResource(R.string.video_speed_hint), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(SpeedOption.entries) { sp ->
            val sel = sp == state.speed
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (sel) Color.White else Color(0xFF2C2C2E))
                    .clickable { vm.setSpeed(sp) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(sp.label, style = MaterialTheme.typography.labelLarge, color = if (sel) Color.Black else Color.White)
            }
        }
    }
}

@Composable
private fun AudioControls(state: VideoEditorState, vm: VideoEditorViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        GlassIconButton(
            if (state.muted) AppIcons.VolumeOff else AppIcons.VolumeOn,
            vm::toggleMute,
            stringResource(if (state.muted) R.string.video_unmuted else R.string.video_muted),
            tint = Color.White,
        )
        Text(
            if (state.muted) stringResource(R.string.video_muted) else stringResource(R.string.video_unmuted),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
        )
    }
}

@Composable
private fun QualityControls(state: VideoEditorState, vm: VideoEditorViewModel) {
    Text(stringResource(R.string.video_quality_hint), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(VideoQuality.entries) { q ->
            val sel = q == state.quality
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (sel) Color.White else Color(0xFF2C2C2E))
                    .clickable { vm.setQuality(q) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(q.label(), style = MaterialTheme.typography.labelLarge, color = if (sel) Color.Black else Color.White)
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

// 자르기 손잡이 식별자
private const val VC_NONE = -1
private const val VC_TL = 0
private const val VC_TR = 1
private const val VC_BL = 2
private const val VC_BR = 3
private const val VC_MOVE = 4
private const val VC_LEFT = 5
private const val VC_TOP = 6
private const val VC_RIGHT = 7
private const val VC_BOTTOM = 8
private val VC_KNOB = Color(0xFF0A84FF)

/** Read-only dimmed mask of the chosen crop region (shown outside the 비율변경 tool). */
@Composable
private fun CropMaskOverlay(crop: NormRect, drawW: Float, drawH: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val l = crop.left * drawW
        val t = crop.top * drawH
        val r = crop.right * drawW
        val b = crop.bottom * drawH
        val dim = Color(0x99000000)
        drawRect(dim, Offset(0f, 0f), Size(drawW, t))
        drawRect(dim, Offset(0f, b), Size(drawW, drawH - b))
        drawRect(dim, Offset(0f, t), Size(l, b - t))
        drawRect(dim, Offset(r, t), Size(drawW - r, b - t))
        drawRect(Color.White.copy(alpha = 0.9f), Offset(l, t), Size(r - l, b - t), style = Stroke(width = 2f))
    }
}

/** 영상 위에 얹는 자르기 안내선. 이미지 편집기와 같은 방식으로 다룬다. */
@Composable
private fun VideoCropOverlay(
    crop: NormRect,
    offX: Float,
    offY: Float,
    drawW: Float,
    drawH: Float,
    onChange: (NormRect) -> Unit,
    onMove: (NormRect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cropState = rememberUpdatedState(crop)
    var active by remember { mutableStateOf(VC_NONE) }
    var startRect by remember { mutableStateOf(crop) }
    var startPos by remember { mutableStateOf(Offset.Zero) }

    fun handles(c: NormRect): List<Pair<Int, Offset>> {
        val l = offX + c.left * drawW
        val t = offY + c.top * drawH
        val r = offX + c.right * drawW
        val b = offY + c.bottom * drawH
        val cx = (l + r) / 2f
        val cy = (t + b) / 2f
        return listOf(
            VC_TL to Offset(l, t),
            VC_TR to Offset(r, t),
            VC_BL to Offset(l, b),
            VC_BR to Offset(r, b),
            VC_LEFT to Offset(l, cy),
            VC_TOP to Offset(cx, t),
            VC_RIGHT to Offset(r, cy),
            VC_BOTTOM to Offset(cx, b),
        )
    }
    fun inside(pos: Offset, c: NormRect): Boolean {
        val l = offX + c.left * drawW
        val t = offY + c.top * drawH
        val r = offX + c.right * drawW
        val b = offY + c.bottom * drawH
        return pos.x in l..r && pos.y in t..b
    }

    Canvas(
        modifier.pointerInput(offX, offY, drawW, drawH) {
            detectDragGestures(
                onDragStart = { pos ->
                    startPos = pos
                    startRect = cropState.value
                    var nearest = VC_NONE
                    var best = 130f
                    handles(cropState.value).forEach { (id, c) ->
                        val d = hypot(pos.x - c.x, pos.y - c.y)
                        if (d < best) {
                            best = d
                            nearest = id
                        }
                    }
                    active = if (nearest != VC_NONE) {
                        nearest
                    } else if (inside(pos, cropState.value)) {
                        VC_MOVE
                    } else {
                        VC_NONE
                    }
                },
                onDrag = { change, _ ->
                    if (active != VC_NONE) {
                        change.consume()
                        val dxN = (change.position.x - startPos.x) / drawW
                        val dyN = (change.position.y - startPos.y) / drawH
                        val s = startRect
                        val nr = when (active) {
                            VC_TL -> s.copy(left = s.left + dxN, top = s.top + dyN)
                            VC_TR -> s.copy(right = s.right + dxN, top = s.top + dyN)
                            VC_BL -> s.copy(left = s.left + dxN, bottom = s.bottom + dyN)
                            VC_BR -> s.copy(right = s.right + dxN, bottom = s.bottom + dyN)
                            VC_LEFT -> s.copy(left = s.left + dxN)
                            VC_RIGHT -> s.copy(right = s.right + dxN)
                            VC_TOP -> s.copy(top = s.top + dyN)
                            VC_BOTTOM -> s.copy(bottom = s.bottom + dyN)
                            VC_MOVE -> {
                                val w = s.right - s.left
                                val h = s.bottom - s.top
                                val nl = (s.left + dxN).coerceIn(0f, 1f - w)
                                val nt = (s.top + dyN).coerceIn(0f, 1f - h)
                                NormRect(nl, nt, nl + w, nt + h)
                            }
                            else -> s
                        }
                        if (active == VC_MOVE) onMove(nr) else onChange(nr)
                    }
                },
                onDragEnd = { active = VC_NONE },
                onDragCancel = { active = VC_NONE },
            )
        },
    ) {
        val l = offX + crop.left * drawW
        val t = offY + crop.top * drawH
        val r = offX + crop.right * drawW
        val b = offY + crop.bottom * drawH
        val dim = Color(0x99000000)
        drawRect(dim, Offset(offX, offY), Size(drawW, t - offY))
        drawRect(dim, Offset(offX, b), Size(drawW, (offY + drawH) - b))
        drawRect(dim, Offset(offX, t), Size(l - offX, b - t))
        drawRect(dim, Offset(r, t), Size((offX + drawW) - r, b - t))
        val guide = Color(0x66FFFFFF)
        for (i in 1..2) {
            val gx = l + (r - l) * i / 3f
            val gy = t + (b - t) * i / 3f
            drawLine(guide, Offset(gx, t), Offset(gx, b), strokeWidth = 1f)
            drawLine(guide, Offset(l, gy), Offset(r, gy), strokeWidth = 1f)
        }
        drawRect(Color.White, Offset(l, t), Size(r - l, b - t), style = Stroke(width = 3f))
        val cx = (l + r) / 2f
        val cy = (t + b) / 2f
        listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b)).forEach { cnr ->
            drawCircle(Color.White, 14f, cnr)
            drawCircle(VC_KNOB, 6f, cnr)
        }
        listOf(Offset(l, cy), Offset(r, cy), Offset(cx, t), Offset(cx, b)).forEach { e ->
            drawCircle(Color.White, 10f, e)
            drawCircle(VC_KNOB, 4f, e)
        }
    }
}

package com.naury.trubbi.feature.editor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naury.trubbi.R
import com.naury.trubbi.core.i18n.label
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.GlassIconButton
import com.naury.trubbi.ui.designsystem.PillButton
import com.naury.trubbi.ui.designsystem.SegmentedControl
import com.naury.trubbi.ui.designsystem.TrubbiConfirmDialog
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * 이미지 편집 화면 — 자르기·회전·밝기·가리기.
 *
 * 가리기는 증빙 사진에 함께 찍힌 제3자 정보를 부분적으로 마스킹하기 위한 도구다.
 *
 * 편집 결과는 항상 새 파일로 저장하고 원본은 그대로 둔다.
 */
@Composable
fun EditorScreen(
    onClose: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
    // 공유 화면의 1회성 편집 모드 — 저장 대신 임시 파일로 출력해 [onShareResult](uri, 가로, 세로)로 돌려준다.
    shareMode: Boolean = false,
    onShareResult: (String, Int, Int) -> Unit = { _, _, _ -> },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val source by viewModel.source.collectAsStateWithLifecycle()
    val redactPreview by viewModel.redactedPreview.collectAsStateWithLifecycle()
    val geometryPreview by viewModel.geometryPreview.collectAsStateWithLifecycle()
    var showSaveConfirm by remember { mutableStateOf(false) }
    // 넓은 화면에서는 왼쪽에 미리보기, 오른쪽에 도구 패널로 나눈다.
    val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 720

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(AppIcons.Close, onClose, stringResource(R.string.action_close), tint = Color.White, size = 40.dp)
            Text(
                if (shareMode) stringResource(R.string.editor_title_apply) else stringResource(R.string.editor_title_photo),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            PillButton(
                if (state.saving) {
                    stringResource(R.string.editor_processing)
                } else if (shareMode) {
                    stringResource(R.string.action_apply)
                } else {
                    stringResource(R.string.action_save)
                },
                onClick = {
                    if (!state.saving) {
                        if (shareMode) viewModel.saveForShare(onShareResult) else showSaveConfirm = true
                    }
                },
            )
        }

        val preview: @Composable () -> Unit = {
            val bmp = source
            when {
                state.loading -> CircularProgressIndicator(color = Color.White)
                bmp == null -> Text(stringResource(R.string.editor_load_failed), color = Color.White)
                else -> {
                    val showAdjust = state.tool == EditorTool.ADJUST
                    val cm = ColorMatrix(
                        floatArrayOf(
                            state.contrast, 0f, 0f, 0f, (-0.5f * state.contrast + 0.5f) * 255f + state.brightness * 2.55f,
                            0f, state.contrast, 0f, 0f, (-0.5f * state.contrast + 0.5f) * 255f + state.brightness * 2.55f,
                            0f, 0f, state.contrast, 0f, (-0.5f * state.contrast + 0.5f) * 255f + state.brightness * 2.55f,
                            0f, 0f, 0f, 1f, 0f,
                        ),
                    )
                    BoxWithConstraints(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp)) {
                        val density = LocalDensity.current
                        val cw = with(density) { maxWidth.toPx() }
                        val ch = with(density) { maxHeight.toPx() }
                        // 탭별 표시 비트맵: CROP/REDACT은 가림 적용된 '전체'(오버레이 좌표 일치), ADJUST는 크롭+가림 합성본.
                        val displayBmp = when (state.tool) {
                            EditorTool.ADJUST -> geometryPreview ?: bmp
                            EditorTool.REDACT -> redactPreview ?: bmp
                            EditorTool.CROP -> redactPreview ?: bmp
                        }
                        val aspect = displayBmp.width.toFloat() / displayBmp.height
                        val drawW: Float
                        val drawH: Float
                        if (cw / ch > aspect) {
                            drawH = ch
                            drawW = ch * aspect
                        } else {
                            drawW = cw
                            drawH = cw / aspect
                        }
                        val offX = (cw - drawW) / 2f
                        val offY = (ch - drawH) / 2f

                        Image(
                            bitmap = displayBmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            colorFilter = ColorFilter.colorMatrix(cm),
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    rotationZ = if (showAdjust) state.rotation.toFloat() else 0f,
                                    scaleX = if (showAdjust && state.flipH) -1f else 1f,
                                ),
                        )

                        when (state.tool) {
                            EditorTool.CROP -> CropOverlay(
                                crop = state.cropRect,
                                offX = offX,
                                offY = offY,
                                drawW = drawW,
                                drawH = drawH,
                                onChange = viewModel::setCropRect,
                                onMove = viewModel::moveCropRect,
                                modifier = Modifier.fillMaxSize(),
                            )
                            EditorTool.REDACT -> RedactOverlay(
                                offX = offX,
                                offY = offY,
                                drawW = drawW,
                                drawH = drawH,
                                redactions = state.redactions,
                                onAdd = viewModel::addRedaction,
                                modifier = Modifier.fillMaxSize(),
                            )
                            EditorTool.ADJUST -> {}
                        }

                        if (state.tool == EditorTool.CROP) {
                            CropPreview(
                                bmp = bmp,
                                crop = state.cropRect,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp),
                            )
                        }
                    }
                }
            }
        }

        if (wide) {
            // 2단 배치 — 미리보기와 도구 패널.
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { preview() }
                EditorToolbar(state, viewModel, side = true)
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { preview() }
            EditorToolbar(state, viewModel)
        }
    }

    if (showSaveConfirm) {
        TrubbiConfirmDialog(
            title = stringResource(R.string.editor_save_title),
            message = stringResource(R.string.editor_save_message),
            confirmText = stringResource(R.string.action_save),
            icon = AppIcons.Save,
            onDismiss = { showSaveConfirm = false },
            onConfirm = {
                showSaveConfirm = false
                viewModel.save(onSaved)
            },
        )
    }
}

// 자르기 손잡이 식별자
private const val H_NONE = -1
private const val H_TL = 0
private const val H_TR = 1
private const val H_BL = 2
private const val H_BR = 3
private const val H_MOVE = 4
private const val H_LEFT = 5
private const val H_TOP = 6
private const val H_RIGHT = 7
private const val H_BOTTOM = 8

@Composable
private fun CropOverlay(
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
    var active by remember { mutableStateOf(H_NONE) }
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
            H_TL to Offset(l, t),
            H_TR to Offset(r, t),
            H_BL to Offset(l, b),
            H_BR to Offset(r, b),
            H_LEFT to Offset(l, cy),
            H_TOP to Offset(cx, t),
            H_RIGHT to Offset(r, cy),
            H_BOTTOM to Offset(cx, b),
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
                    val threshold = 130f
                    var nearest = H_NONE
                    var best = threshold
                    handles(cropState.value).forEach { (id, c) ->
                        val d = hypot(pos.x - c.x, pos.y - c.y)
                        if (d < best) {
                            best = d
                            nearest = id
                        }
                    }
                    active = if (nearest != H_NONE) {
                        nearest
                    } else if (inside(pos, cropState.value)) {
                        H_MOVE
                    } else {
                        H_NONE
                    }
                },
                onDrag = { change, _ ->
                    if (active != H_NONE) {
                        change.consume()
                        val dxN = (change.position.x - startPos.x) / drawW
                        val dyN = (change.position.y - startPos.y) / drawH
                        val s = startRect
                        val nr = when (active) {
                            H_TL -> s.copy(left = s.left + dxN, top = s.top + dyN)
                            H_TR -> s.copy(right = s.right + dxN, top = s.top + dyN)
                            H_BL -> s.copy(left = s.left + dxN, bottom = s.bottom + dyN)
                            H_BR -> s.copy(right = s.right + dxN, bottom = s.bottom + dyN)
                            H_LEFT -> s.copy(left = s.left + dxN)
                            H_RIGHT -> s.copy(right = s.right + dxN)
                            H_TOP -> s.copy(top = s.top + dyN)
                            H_BOTTOM -> s.copy(bottom = s.bottom + dyN)
                            H_MOVE -> {
                                val w = s.right - s.left
                                val h = s.bottom - s.top
                                val nl = (s.left + dxN).coerceIn(0f, 1f - w)
                                val nt = (s.top + dyN).coerceIn(0f, 1f - h)
                                NormRect(nl, nt, nl + w, nt + h)
                            }
                            else -> s
                        }
                        // 옮기기만 하면 비율이 그대로라 프리셋 표시를 남기고, 크기를 바꾸면 해제한다.
                        if (active == H_MOVE) onMove(nr) else onChange(nr)
                    }
                },
                onDragEnd = { active = H_NONE },
                onDragCancel = { active = H_NONE },
            )
        },
    ) {
        val l = offX + crop.left * drawW
        val t = offY + crop.top * drawH
        val r = offX + crop.right * drawW
        val b = offY + crop.bottom * drawH

        val dim = Color(0x99000000)
        drawRect(dim, topLeft = Offset(offX, offY), size = Size(drawW, t - offY))
        drawRect(dim, topLeft = Offset(offX, b), size = Size(drawW, (offY + drawH) - b))
        drawRect(dim, topLeft = Offset(offX, t), size = Size(l - offX, b - t))
        drawRect(dim, topLeft = Offset(r, t), size = Size((offX + drawW) - r, b - t))

        val guide = Color(0x66FFFFFF)
        for (i in 1..2) {
            val gx = l + (r - l) * i / 3f
            val gy = t + (b - t) * i / 3f
            drawLine(guide, Offset(gx, t), Offset(gx, b), strokeWidth = 1f)
            drawLine(guide, Offset(l, gy), Offset(r, gy), strokeWidth = 1f)
        }

        drawRect(Color.White, topLeft = Offset(l, t), size = Size(r - l, b - t), style = Stroke(width = 3f))

        val cx = (l + r) / 2f
        val cy = (t + b) / 2f
        // 모서리 손잡이
        listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b)).forEach { c ->
            drawCircle(Color.White, radius = 14f, center = c)
            drawCircle(MaterialColorPrimaryStub, radius = 6f, center = c)
        }
        // 변 손잡이
        listOf(Offset(l, cy), Offset(r, cy), Offset(cx, t), Offset(cx, b)).forEach { e ->
            drawCircle(Color.White, radius = 10f, center = e)
            drawCircle(MaterialColorPrimaryStub, radius = 4f, center = e)
        }
    }
}

private val MaterialColorPrimaryStub = Color(0xFF0A84FF)

/** 자를 영역만 크게 확대해 보여 주는 미리보기. 손가락에 가려 안 보이는 부분을 확인하는 용도. */
@Composable
private fun CropPreview(bmp: android.graphics.Bitmap, crop: NormRect, modifier: Modifier = Modifier) {
    val image = remember(bmp) { bmp.asImageBitmap() }
    val cropWpx = ((crop.right - crop.left) * bmp.width)
    val cropHpx = ((crop.bottom - crop.top) * bmp.height)
    if (cropWpx < 2f || cropHpx < 2f) return
    val aspect = (cropWpx / cropHpx).coerceIn(0.4f, 2.5f)
    val maxSide = 132.dp
    val w = if (aspect >= 1f) maxSide else maxSide * aspect
    val h = if (aspect >= 1f) maxSide / aspect else maxSide

    Box(
        modifier
            .width(w)
            .height(h)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(12.dp)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val sl = (crop.left * bmp.width).toInt().coerceIn(0, bmp.width - 1)
            val st = (crop.top * bmp.height).toInt().coerceIn(0, bmp.height - 1)
            val sw = cropWpx.toInt().coerceIn(1, bmp.width - sl)
            val sh = cropHpx.toInt().coerceIn(1, bmp.height - st)
            drawImage(
                image = image,
                srcOffset = IntOffset(sl, st),
                srcSize = IntSize(sw, sh),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            )
        }
    }
}

@Composable
private fun RedactOverlay(
    offX: Float,
    offY: Float,
    drawW: Float,
    drawH: Float,
    redactions: List<NormRect>,
    onAdd: (NormRect) -> Unit,
    modifier: Modifier = Modifier,
) {
    var startPt by remember { mutableStateOf<Offset?>(null) }
    var currentPt by remember { mutableStateOf<Offset?>(null) }

    fun toNorm(a: Offset, b: Offset): NormRect {
        val left = ((min(a.x, b.x) - offX) / drawW).coerceIn(0f, 1f)
        val top = ((min(a.y, b.y) - offY) / drawH).coerceIn(0f, 1f)
        val right = ((max(a.x, b.x) - offX) / drawW).coerceIn(0f, 1f)
        val bottom = ((max(a.y, b.y) - offY) / drawH).coerceIn(0f, 1f)
        return NormRect(left, top, right, bottom)
    }

    Canvas(
        modifier.pointerInput(offX, offY, drawW, drawH) {
            detectDragGestures(
                onDragStart = {
                    startPt = it
                    currentPt = it
                },
                onDrag = { change, _ ->
                    currentPt = change.position
                    change.consume()
                },
                onDragEnd = {
                    val s = startPt
                    val c = currentPt
                    if (s != null && c != null && abs(s.x - c.x) > 8f && abs(s.y - c.y) > 8f) onAdd(toNorm(s, c))
                    startPt = null
                    currentPt = null
                },
                onDragCancel = {
                    startPt = null
                    currentPt = null
                },
            )
        },
    ) {
        // 실제 모자이크는 미리보기 비트맵에 이미 그려져 있다. 여기서는 어디를 가렸는지
        // 알아보기 쉽도록 테두리만 덧그린다.
        redactions.forEach { n ->
            val topLeft = Offset(offX + n.left * drawW, offY + n.top * drawH)
            val size = Size((n.right - n.left) * drawW, (n.bottom - n.top) * drawH)
            val corner = CornerRadius(minOf(size.width, size.height) * 0.15f)
            drawRoundRect(Color.White.copy(alpha = 0.9f), topLeft = topLeft, size = size, cornerRadius = corner, style = Stroke(width = 3f))
        }
        val s = startPt
        val c = currentPt
        if (s != null && c != null) {
            drawRect(
                Color(0xAAFF3B30),
                topLeft = Offset(min(s.x, c.x), min(s.y, c.y)),
                size = Size(abs(c.x - s.x), abs(c.y - s.y)),
                style = Stroke(width = 3f),
            )
        }
    }
}

@Composable
private fun EditorToolbar(state: EditorState, vm: EditorViewModel, side: Boolean = false) {
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
            options = EditorTool.entries.map { it.label() },
            selectedIndex = state.tool.ordinal,
            onSelected = { vm.setTool(EditorTool.entries[it]) },
            onDark = true,
        )

        when (state.tool) {
            EditorTool.ADJUST -> AdjustControls(state, vm)
            EditorTool.CROP -> CropControls(state, vm)
            EditorTool.REDACT -> RedactControls(state, vm)
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun AdjustControls(state: EditorState, vm: EditorViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        GlassIconButton(AppIcons.RotateLeft, vm::rotateLeft, stringResource(R.string.action_rotate_left), tint = Color.White)
        GlassIconButton(AppIcons.RotateRight, vm::rotateRight, stringResource(R.string.action_rotate_right), tint = Color.White)
        GlassIconButton(AppIcons.Flip, vm::toggleFlip, stringResource(R.string.action_flip), tint = Color.White)
        Spacer(Modifier.weight(1f))
        GlassIconButton(AppIcons.Refresh, vm::reset, stringResource(R.string.action_refresh), tint = Color.White)
    }
    Column {
        Text(stringResource(R.string.editor_brightness), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
        Slider(value = state.brightness, onValueChange = vm::setBrightness, valueRange = -100f..100f)
        Text(stringResource(R.string.editor_contrast), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
        Slider(value = state.contrast, onValueChange = vm::setContrast, valueRange = 0.5f..1.5f)
    }
}

@Composable
private fun CropControls(state: EditorState, vm: EditorViewModel) {
    Text(
        stringResource(R.string.editor_crop_hint),
        style = MaterialTheme.typography.labelMedium,
        color = Color.White.copy(alpha = 0.8f),
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(CropPreset.entries) { preset ->
            val sel = preset == state.cropPreset
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (sel) Color.White else Color(0xFF2C2C2E))
                    .clickable { vm.setCropPreset(preset) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(preset.label(), style = MaterialTheme.typography.labelLarge, color = if (sel) Color.Black else Color.White)
            }
        }
    }
}

@Composable
private fun RedactControls(state: EditorState, vm: EditorViewModel) {
    Text(
        stringResource(R.string.editor_redact_hint),
        style = MaterialTheme.typography.labelMedium,
        color = Color.White.copy(alpha = 0.8f),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RedactStyle.entries.forEach { style ->
            val sel = style == state.redactStyle
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (sel) Color.White else Color(0xFF2C2C2E))
                    .clickable { vm.setRedactStyle(style) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(style.label(), style = MaterialTheme.typography.labelLarge, color = if (sel) Color.Black else Color.White)
            }
        }
        Spacer(Modifier.weight(1f))
        GlassIconButton(AppIcons.Undo, vm::undoRedaction, stringResource(R.string.action_undo), tint = Color.White)
        GlassIconButton(AppIcons.Delete, vm::clearRedactions, stringResource(R.string.action_delete), tint = Color.White)
    }
}

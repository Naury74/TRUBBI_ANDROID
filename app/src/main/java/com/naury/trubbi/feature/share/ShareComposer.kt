package com.naury.trubbi.feature.share

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.naury.trubbi.R
import com.naury.trubbi.core.file.WatermarkStyle
import com.naury.trubbi.core.i18n.label
import com.naury.trubbi.core.util.DateUtils
import com.naury.trubbi.data.settings.WatermarkDefaults
import com.naury.trubbi.feature.editor.rememberShareEditor
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.PillButton
import com.naury.trubbi.ui.designsystem.pressable
import java.util.Calendar
import kotlinx.coroutines.launch

/** 공유 대상 한 장(이미지) + 워터마크에 쓸 메타데이터. */
data class ShareComposerItem(
    val id: String,
    val imageUri: String,
    val date: Long,
    val purposeLabel: String? = null,
    val locationName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val savedName: String? = null,
    val typeLabel: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val editable: Boolean = false,
    val isVideo: Boolean = false,
    val thumbUri: String? = null,
) {
    val hasLocation: Boolean get() = !locationName.isNullOrBlank() || (latitude != null && longitude != null)
    val locationText: String
        get() = locationName?.takeIf { it.isNotBlank() }
            ?: if (latitude != null && longitude != null) "%.5f, %.5f".format(latitude, longitude) else ""
}

/** 공유 시 한 항목에 실제로 적용할 (이미지/영상 uri, 새길 줄들, 스타일, 영상 여부). */
data class ShareSelection(val imageUri: String, val lines: List<String>, val style: WatermarkStyle, val isVideo: Boolean = false)

/** 항목별 워터마크 편집 상태 — 날짜는 시각, 나머지는 텍스트로 보관. */
private data class ItemWm(
    val dateOn: Boolean,
    val dateMillis: Long,
    val purposeOn: Boolean,
    val purpose: String,
    val locationOn: Boolean,
    val location: String,
    val nameOn: Boolean,
    val name: String,
    val customOn: Boolean,
    val custom: String,
    val style: WatermarkStyle,
)

private fun ItemWm.lines(): List<String> = listOfNotNull(
    DateUtils.formatDateTime(dateMillis).takeIf { dateOn },
    purpose.takeIf { purposeOn && it.isNotBlank() },
    location.takeIf { locationOn && it.isNotBlank() },
    name.takeIf { nameOn && it.isNotBlank() },
    custom.takeIf { customOn && it.isNotBlank() },
)

private fun initWm(item: ShareComposerItem, d: WatermarkDefaults): ItemWm = ItemWm(
    dateOn = d.date, dateMillis = item.date,
    purposeOn = d.purpose && !item.purposeLabel.isNullOrBlank(), purpose = item.purposeLabel.orEmpty(),
    locationOn = d.location && item.hasLocation, location = item.locationText,
    nameOn = d.name && !item.savedName.isNullOrBlank(), name = item.savedName.orEmpty(),
    customOn = d.custom, custom = d.customText,
    style = WatermarkStyle.BAR,
)

/**
 * 공유 준비 화면 — 위에 큰 미리보기, 아래에 편집 패널.
 *
 * 미리보기에 워터마크를 **실제로 그린다**. 체크박스만 보여 주고 결과는 공유한 뒤에야 확인되는
 * 방식이면, 글자가 사진의 중요한 부분을 덮었는지 알 길이 없다.
 *
 * 항목마다 워터마크 설정을 따로 들고 간다. 한 번에 여러 장을 보낼 때 사진마다 가릴 것이 다르다.
 *
 * @param items 공유할 항목들
 * @param defaults 설정에 저장된 워터마크 기본 선택값
 * @param onClose 닫을 때
 * @param onShare 공유를 누를 때 항목별 최종 선택과 함께 불린다
 * @param purposes 용도 선택 목록
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ShareComposerScreen(
    items: List<ShareComposerItem>,
    defaults: WatermarkDefaults,
    onClose: () -> Unit,
    onShare: (List<ShareSelection>) -> Unit,
    purposes: List<String> = emptyList(),
) {
    if (items.isEmpty()) {
        onClose()
        return
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val pager = rememberPagerState(pageCount = { items.size })
    val sel = remember { mutableStateMapOf<String, ItemWm>() }
    fun wmOf(item: ShareComposerItem) = sel[item.id] ?: initWm(item, defaults)
    val current = items[pager.currentPage.coerceIn(0, items.size - 1)]
    val curWm = wmOf(current)

    // 1회성 편집 — 편집본은 공유 캐시에만 두고 기록에는 남기지 않는다(원본 미변경). 편집 시 이 uri로 미리보기/공유.
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    val editedUris = remember { mutableStateMapOf<String, String>() }
    // 편집본의 실제 가로/세로 — 크롭 후 미리보기 비율이 맞도록.
    val editedDims = remember { mutableStateMapOf<String, Pair<Int, Int>>() }
    val shareEditor = rememberShareEditor { uri, w, h ->
        val id = editId ?: return@rememberShareEditor
        editedUris[id] = uri
        if (w > 0 && h > 0) editedDims[id] = w to h
        editId = null
    }

    // 미리보기는 썸네일(영상), 전송/편집은 원본 파일. 편집본이 있으면 그걸 우선.
    fun previewUri(item: ShareComposerItem) = editedUris[item.id] ?: item.thumbUri ?: item.imageUri
    fun shareUriOf(item: ShareComposerItem) = editedUris[item.id] ?: item.imageUri
    fun dimsOf(item: ShareComposerItem): Pair<Int, Int> = editedDims[item.id] ?: (item.width to item.height)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            // ── 상단 바: 닫기 + 묶음/인덱스 + (편집) + 공유.
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleBtn(AppIcons.Close, onClose)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (items.size >
                            1
                        ) {
                            pluralStringResource(R.plurals.share_composer_title, items.size, items.size)
                        } else {
                            stringResource(R.string.action_share)
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        "${pager.currentPage + 1} / ${items.size}",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                if (current.editable && !current.isVideo) {
                    CircleBtn(AppIcons.Edit) {
                        editId = current.id
                        shareEditor(shareUriOf(current))
                    }
                    Spacer(Modifier.width(8.dp))
                }
                PillButton(
                    text = stringResource(R.string.action_share),
                    icon = AppIcons.Share,
                    onClick = {
                        onShare(
                            items.map {
                                val w = wmOf(it)
                                // 영상은 워터마크를 굽지 않고 원본 전송.
                                ShareSelection(shareUriOf(it), if (it.isVideo) emptyList() else w.lines(), w.style, it.isVideo)
                            },
                        )
                    },
                    modifier = Modifier.height(44.dp),
                )
            }

            // ── 미리보기(weight) — 남은 공간을 유동적으로 차지. 워터마크는 사진 영역 안에 그려진다.
            Box(Modifier.fillMaxWidth().weight(1f)) {
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                    val item = items[page]
                    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        // 실제 사진 사각형에 맞춘 박스(레터박스 영역 제외) — 그 안에 워터마크를 올린다.
                        val (iw, ih) = dimsOf(item)
                        val ratio = if (iw > 0 && ih > 0) iw.toFloat() / ih else 0f
                        val boxMod = if (ratio > 0f) {
                            val boxRatio = maxWidth / maxHeight
                            if (ratio >= boxRatio) {
                                Modifier.fillMaxWidth().aspectRatio(ratio)
                            } else {
                                Modifier.fillMaxHeight().aspectRatio(ratio)
                            }
                        } else {
                            Modifier.fillMaxSize()
                        }
                        Box(boxMod) {
                            AsyncImage(
                                model = previewUri(item),
                                contentDescription = null,
                                contentScale = if (ratio > 0f) ContentScale.FillBounds else ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (item.isVideo) {
                                Box(
                                    Modifier.align(Alignment.Center).size(56.dp)
                                        .clip(RoundedCornerShape(percent = 50)).background(Color.Black.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        AppIcons.PlayArrow,
                                        contentDescription = stringResource(R.string.share_label_video),
                                        tint = Color.White,
                                        modifier = Modifier.size(30.dp),
                                    )
                                }
                            } else {
                                WatermarkPreview(wmOf(item).lines(), wmOf(item).style)
                            }
                        }
                    }
                }
                Box(
                    Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                        .clip(RoundedCornerShape(percent = 50)).background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text(stringResource(R.string.share_preview_note), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
                }
            }

            // ── 하단 편집 시트. 미리보기를 가리지 않도록 영역을 나눠 둔다.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 핸들.
                Box(
                    Modifier.align(Alignment.CenterHorizontally).size(width = 38.dp, height = 4.dp)
                        .clip(RoundedCornerShape(percent = 50)).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                )

                // 현재 항목 간략 정보(타입 · 이름 · 날짜).
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    current.typeLabel?.let { InfoTag(it) }
                    if (items.size > 1) InfoTag(stringResource(R.string.share_batch_index, pager.currentPage + 1, items.size))
                    Text(
                        listOfNotNull(current.savedName?.takeIf { it.isNotBlank() }, DateUtils.formatDateTime(current.date)).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }

                if (current.isVideo) {
                    Box(
                        Modifier.fillMaxWidth().clip(
                            RoundedCornerShape(12.dp),
                        ).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)).padding(14.dp),
                    ) {
                        Text(
                            stringResource(R.string.share_video_original_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text(
                        stringResource(R.string.share_watermark_section),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // 날짜 — 날짜/시간 픽커.
                    WmPickRow(
                        stringResource(R.string.watermark_field_date),
                        curWm.dateOn,
                        DateUtils.formatDateTime(curWm.dateMillis),
                        onToggle = { sel[current.id] = curWm.copy(dateOn = !curWm.dateOn) },
                        onPick = {
                            val cal = Calendar.getInstance().apply { timeInMillis = curWm.dateMillis }
                            DatePickerDialog(context, { _, y, mo, d ->
                                cal.set(y, mo, d)
                                TimePickerDialog(context, { _, h, mi ->
                                    cal.set(Calendar.HOUR_OF_DAY, h)
                                    cal.set(Calendar.MINUTE, mi)
                                    sel[current.id] = wmOf(current).copy(dateMillis = cal.timeInMillis, dateOn = true)
                                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
                            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                        },
                    )

                    // 용도 — 목록에서 선택.
                    var purposeMenu by remember { mutableStateOf(false) }
                    Box {
                        WmPickRow(
                            stringResource(R.string.watermark_field_purpose),
                            curWm.purposeOn,
                            curWm.purpose.ifBlank { stringResource(R.string.share_purpose_none) },
                            onToggle = { sel[current.id] = curWm.copy(purposeOn = !curWm.purposeOn) },
                            onPick = { if (purposes.isNotEmpty()) purposeMenu = true },
                        )
                        DropdownMenu(expanded = purposeMenu, onDismissRequest = { purposeMenu = false }) {
                            purposes.forEach { p ->
                                DropdownMenuItem(text = { Text(p) }, onClick = {
                                    purposeMenu = false
                                    sel[current.id] = wmOf(current).copy(purpose = p, purposeOn = true)
                                })
                            }
                        }
                    }

                    // 위치 / 이름 / 문구 — 직접 편집.
                    WmTextRow(
                        stringResource(R.string.watermark_field_location),
                        curWm.locationOn,
                        curWm.location,
                        enabled = current.hasLocation,
                        onToggle = { sel[current.id] = curWm.copy(locationOn = !curWm.locationOn) },
                        onText = { sel[current.id] = curWm.copy(location = it) },
                    )
                    WmTextRow(
                        stringResource(R.string.field_name),
                        curWm.nameOn,
                        curWm.name,
                        enabled = true,
                        onToggle = { sel[current.id] = curWm.copy(nameOn = !curWm.nameOn) },
                        onText = { sel[current.id] = curWm.copy(name = it) },
                    )
                    WmTextRow(
                        stringResource(R.string.share_field_text),
                        curWm.customOn,
                        curWm.custom,
                        enabled = true,
                        onToggle = { sel[current.id] = curWm.copy(customOn = !curWm.customOn) },
                        onText = { sel[current.id] = curWm.copy(custom = it) },
                    )

                    // 스타일.
                    Text(
                        stringResource(R.string.share_style_section),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WatermarkStyle.entries.forEach { s -> StyleChip(s.label(), curWm.style == s) { sel[current.id] = curWm.copy(style = s) } }
                    }
                } // end else (비-영상 워터마크 편집)

                if (items.size > 1) {
                    Text(
                        stringResource(R.string.share_apply_all),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.clip(RoundedCornerShape(percent = 50)).pressable {
                            items.forEach { item ->
                                val base = initWm(item, defaults)
                                sel[item.id] = base.copy(
                                    dateOn = curWm.dateOn,
                                    purposeOn = curWm.purposeOn,
                                    purpose = if (curWm.purposeOn) curWm.purpose else base.purpose,
                                    locationOn = curWm.locationOn && item.hasLocation,
                                    nameOn = curWm.nameOn,
                                    customOn = curWm.customOn,
                                    custom = if (curWm.customOn) curWm.custom else base.custom,
                                    style = curWm.style,
                                )
                            }
                        }.padding(vertical = 2.dp),
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(items, key = { _, entry -> entry.id }) { i, item ->
                            val active = i == pager.currentPage
                            AsyncImage(
                                model = previewUri(item),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(54.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .padding(if (active) 2.dp else 0.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .pressable { scope.launch { pager.animateScrollToPage(i) } },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 이미지 위 워터마크 미리보기 — 굽기와 동일 스타일/위치(사진 영역 안에 그려짐). */
@Composable
private fun WatermarkPreview(lines: List<String>, style: WatermarkStyle) {
    if (lines.isEmpty()) return
    val shadow = Shadow(color = Color.Black.copy(alpha = 0.9f), offset = Offset(0f, 2f), blurRadius = 6f)
    val textStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color.White, shadow = shadow)
    when (style) {
        WatermarkStyle.BAR -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                    .padding(start = 14.dp, end = 14.dp, top = 30.dp, bottom = 12.dp),
            ) { lines.forEach { Text(it, style = textStyle) } }
        }
        WatermarkStyle.MINIMAL -> Column(
            Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.End,
        ) { lines.forEach { Text(it, style = textStyle, textAlign = TextAlign.End) } }
        WatermarkStyle.CARD -> Column(
            Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Column(
                Modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) { lines.forEach { Text(it, style = textStyle.copy(shadow = null)) } }
        }
    }
}

/** 토글 + 값 선택(픽커) 행 — 날짜/용도처럼 직접 입력이 아니라 골라야 하는 항목. */
@Composable
private fun WmPickRow(label: String, on: Boolean, value: String, onToggle: () -> Unit, onPick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ToggleTag(label, on, true, onToggle)
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (on) 0.7f else 0.3f))
                .pressable(onClick = onPick).padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Icon(AppIcons.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
    }
}

/** 토글 + 자유 텍스트 편집 행 — 위치/이름/문구. */
@Composable
private fun WmTextRow(label: String, on: Boolean, text: String, enabled: Boolean, onToggle: () -> Unit, onText: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ToggleTag(label, on, enabled, onToggle)
        Box(
            Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (on && enabled) 0.7f else 0.3f))
                .padding(horizontal = 12.dp, vertical = 11.dp),
        ) {
            if (text.isBlank()) {
                Text(
                    stringResource(if (enabled) R.string.share_enter_text else R.string.share_no_info),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            BasicTextField(
                value = text,
                onValueChange = onText,
                enabled = on && enabled,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = if (on &&
                        enabled
                    ) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ToggleTag(label: String, on: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val bg = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        on -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        on -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        Modifier.width(56.dp).clip(RoundedCornerShape(percent = 50)).background(bg)
            .then(if (enabled) Modifier.pressable(onClick = onToggle) else Modifier)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = fg, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)) }
}

@Composable
private fun StyleChip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(percent = 50))
            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .pressable(onClick = onClick).padding(horizontal = 15.dp, vertical = 9.dp),
    ) {
        Text(
            label,
            color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Medium),
        )
    }
}

@Composable
private fun InfoTag(label: String) {
    Box(
        Modifier.clip(RoundedCornerShape(percent = 50)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun CircleBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(percent = 50)).background(Color.White.copy(alpha = 0.16f))
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
    }
}

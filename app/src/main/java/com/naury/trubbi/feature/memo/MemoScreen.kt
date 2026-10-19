package com.naury.trubbi.feature.memo

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor
import com.naury.trubbi.R
import com.naury.trubbi.domain.memo.MemoBlock
import com.naury.trubbi.domain.memo.MemoContent
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.GlassIconButton
import com.naury.trubbi.ui.designsystem.GlassTopBar
import com.naury.trubbi.ui.theme.LocalTrubbiColors
import java.util.concurrent.atomic.AtomicLong

/**
 * 편집기가 들고 있는 블록 하나.
 *
 * 편집 상태([RichTextState])는 블록이 화면에 보이는 동안에만 유지한다. 모든 블록의 상태를
 * 동시에 살려 두면 입력마다 전체가 재계산된다. 화면을 벗어나면 HTML 스냅숏만 남긴다.
 *
 * 도구 모음이 현재 커서 블록의 선택 범위에 스타일을 적용하므로, 이 참조는 관찰 가능한
 * 상태여야 한다.
 */
private sealed class EditBlock(val id: Long) {
    class Rich(id: Long, html: String) : EditBlock(id) {
        var html: String = html

        // 편집 상태가 붙는 순간 도구 모음이 다시 그려져야 해서 관찰 가능한 상태로 둔다.
        var state by mutableStateOf<RichTextState?>(null)
        fun currentHtml(): String = state?.toHtml() ?: html
    }

    class Img(id: Long, val uri: String) : EditBlock(id)

    class Table(id: Long, rows: List<List<String>>) : EditBlock(id) {
        var rows by mutableStateOf(rows)
    }
}

@Composable
fun MemoScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: MemoViewModel = hiltViewModel(),
) {
    val c = LocalTrubbiColors.current
    val initial by viewModel.initialBlocks.collectAsStateWithLifecycle()

    val blocks = remember { mutableStateListOf<EditBlock>() }
    val nextId = remember { AtomicLong(1L) }
    var loaded by remember { mutableStateOf(false) }
    var focusedId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(initial) {
        val init = initial
        if (!loaded && init != null) {
            blocks.clear()
            init.forEach { b ->
                when (b) {
                    is MemoBlock.Html -> blocks.add(EditBlock.Rich(nextId.getAndIncrement(), b.html))
                    // 옛 문단 스타일은 처음 열 때 한 번 HTML 로 옮긴다.
                    is MemoBlock.Text -> blocks.add(EditBlock.Rich(nextId.getAndIncrement(), MemoContent.textToHtml(b)))
                    is MemoBlock.Image -> blocks.add(EditBlock.Img(nextId.getAndIncrement(), b.uri))
                    is MemoBlock.Table -> blocks.add(EditBlock.Table(nextId.getAndIncrement(), b.rows))
                }
            }
            if (blocks.none { it is EditBlock.Rich }) blocks.add(0, EditBlock.Rich(nextId.getAndIncrement(), ""))
            focusedId = blocks.firstOrNull { it is EditBlock.Rich }?.id
            loaded = true
        }
    }

    val focusedRich = blocks.firstOrNull { it.id == focusedId } as? EditBlock.Rich
    val rich: RichTextState? = focusedRich?.state

    /**
     * 커서가 있는 블록 다음에 끼워 넣는다.
     *
     * @return 끼워 넣은 자리의 인덱스
     */
    fun insertAfterFocused(block: EditBlock): Int {
        val idx = blocks.indexOfFirst { it.id == focusedId }
        val at = if (idx >= 0) idx + 1 else blocks.size
        blocks.add(at, block)
        if (block is EditBlock.Rich) focusedId = block.id
        return at
    }

    /**
     * 표·이미지를 본문 흐름에 끼워 넣는다.
     *
     * 빈 문단을 함께 삽입한다. 표·이미지가 문서 마지막 블록이면 그 뒤에 커서를 둘 수 없다.
     */
    fun insertAttachment(block: EditBlock) {
        val at = insertAfterFocused(block)
        val follow = EditBlock.Rich(nextId.getAndIncrement(), "")
        blocks.add(at + 1, follow)
        focusedId = follow.id
    }

    // 편집기 배경 색조 선택지.
    val bgColors = listOf(
        MaterialTheme.colorScheme.surface,
        Color(0xFFFFF1E8),
        Color(0xFFFFF6D9),
        Color(0xFFE7F7F2),
        Color(0xFFEAF0FF),
    )
    var bgColor by remember { mutableStateOf(bgColors.first()) }

    val onSurface = MaterialTheme.colorScheme.onSurface
    val textColors = listOf(
        onSurface,
        Color(0xFF23395D),
        Color(0xFF2BB6A3),
        Color(0xFFE0463B),
        Color(0xFFF59E0B),
        Color(0xFF7C5CF0),
    )
    val highlightColors = listOf(
        Color(0xFFFFF59D),
        Color(0xFFB9F6CA),
        Color(0xFFB3E5FC),
        Color(0xFFFFCCBC),
    )

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            viewModel.importImage(uri) { saved ->
                insertAttachment(EditBlock.Img(nextId.getAndIncrement(), saved))
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding())
            .consumeWindowInsets(contentPadding)
            .imePadding(),
    ) {
        GlassTopBar(
            title = if (viewModel.isEditing) stringResource(R.string.memo_edit_title) else stringResource(R.string.memo_new_title),
            onBack = onBack,
            actions = {
                GlassIconButton(AppIcons.Check, {
                    viewModel.save(
                        blocks.map { b ->
                            when (b) {
                                is EditBlock.Rich -> MemoBlock.Html(b.currentHtml())
                                is EditBlock.Img -> MemoBlock.Image(b.uri)
                                is EditBlock.Table -> MemoBlock.Table(b.rows)
                            }
                        },
                    ) { onBack() }
                }, stringResource(R.string.action_save), tint = MaterialTheme.colorScheme.primary)
            },
        )

        // ── 블록 편집 영역
        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(bgColor),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(blocks, key = { _, b -> b.id }) { index, block ->
                BlockRow(
                    block = block,
                    isSelected = block.id == focusedId,
                    onSelect = { focusedId = block.id },
                    onDelete = {
                        blocks.removeAt(index)
                        if (blocks.none { it is EditBlock.Rich }) {
                            blocks.add(EditBlock.Rich(nextId.getAndIncrement(), ""))
                        }
                        if (focusedId == block.id) focusedId = blocks.firstOrNull { it is EditBlock.Rich }?.id
                    },
                    onMoveUp = if (index > 0) {
                        (
                            {
                                val b = blocks.removeAt(index)
                                blocks.add(index - 1, b)
                            }
                            )
                    } else {
                        null
                    },
                    onMoveDown = if (index < blocks.lastIndex) {
                        (
                            {
                                val b = blocks.removeAt(index)
                                blocks.add(index + 1, b)
                            }
                            )
                    } else {
                        null
                    },
                )
            }
        }

        // ── 서식 도구 모음. 키보드 위에 붙어 있고, 지금 선택한 범위에만 적용된다.
        val cur = rich?.currentSpanStyle
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FormatToggle(AppIcons.FormatBold, cur?.fontWeight == FontWeight.Bold) {
                    rich?.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold))
                }
                FormatToggle(AppIcons.FormatItalic, cur?.fontStyle == FontStyle.Italic) {
                    rich?.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic))
                }
                FormatToggle(AppIcons.FormatUnderline, cur?.textDecoration?.contains(TextDecoration.Underline) == true) {
                    rich?.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                }
                FormatToggle(AppIcons.FormatStrike, cur?.textDecoration?.contains(TextDecoration.LineThrough) == true) {
                    rich?.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                }
                ToolbarDivider()
                val sizeLabel = stringResource(R.string.memo_format_char)
                listOf(sizeLabel to 14, sizeLabel to 17, sizeLabel to 22).forEachIndexed { i, (label, sz) ->
                    SizeToggle(label, sizeSp = listOf(12, 15, 19)[i], selected = cur?.fontSize == sz.sp) {
                        rich?.toggleSpanStyle(SpanStyle(fontSize = sz.sp))
                    }
                }
                ToolbarDivider()
                FormatToggle(AppIcons.ListBullet, rich?.isUnorderedList == true) { rich?.toggleUnorderedList() }
                FormatToggle(AppIcons.ListNumber, rich?.isOrderedList == true) { rich?.toggleOrderedList() }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.memo_group_text), style = MaterialTheme.typography.labelSmall, color = c.secondaryLabel)
                textColors.forEach { col ->
                    ColorDot(col, selected = cur?.color == col) {
                        rich?.toggleSpanStyle(SpanStyle(color = col))
                    }
                }
                ToolbarDivider()
                Text(stringResource(R.string.memo_group_highlight), style = MaterialTheme.typography.labelSmall, color = c.secondaryLabel)
                highlightColors.forEach { col ->
                    ColorDot(col, selected = cur?.background == col) {
                        rich?.toggleSpanStyle(SpanStyle(background = col))
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InsertChip(AppIcons.EditNote, stringResource(R.string.memo_group_paragraph)) {
                    insertAfterFocused(EditBlock.Rich(nextId.getAndIncrement(), ""))
                }
                InsertChip(AppIcons.ImageIcon, stringResource(R.string.memo_insert_image)) {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                InsertChip(AppIcons.TableIcon, stringResource(R.string.memo_insert_table)) {
                    insertAttachment(EditBlock.Table(nextId.getAndIncrement(), List(2) { List(2) { "" } }))
                }
                Spacer(Modifier.weight(1f))
                bgColors.forEach { col ->
                    ColorDot(col, selected = col == bgColor) { bgColor = col }
                }
            }
        }
    }
}

@Composable
private fun BlockRow(
    block: EditBlock,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
) {
    val c = LocalTrubbiColors.current
    val border = if (isSelected && block !is EditBlock.Rich) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .padding(2.dp),
    ) {
        when (block) {
            is EditBlock.Rich -> RichBlockEditor(block, onSelect)
            is EditBlock.Img -> {
                Box(Modifier.fillMaxWidth().clickable { onSelect() }) {
                    AsyncImage(
                        model = block.uri,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).clip(RoundedCornerShape(12.dp)),
                    )
                }
            }
            is EditBlock.Table -> TableEditor(block, onSelect)
        }

        // 블록별 조작 버튼은 선택된 표·이미지 블록에만 표시한다. 문단 블록에는 노출하지 않는다.
        if (block !is EditBlock.Rich || isSelected) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (block is EditBlock.Table) {
                    SmallActionText(stringResource(R.string.memo_table_add_row)) {
                        block.rows =
                            block.rows + listOf(List(block.rows.firstOrNull()?.size ?: 1) { "" })
                    }
                    SmallActionText(stringResource(R.string.memo_table_add_column)) { block.rows = block.rows.map { it + "" } }
                }
                onMoveUp?.let { IconBtn(AppIcons.ArrowUpward, it) }
                onMoveDown?.let { IconBtn(AppIcons.ArrowDownward, it) }
                IconBtn(AppIcons.Delete, onDelete, tint = c.red)
            }
        }
    }
}

/** 서식 있는 문단 하나. 화면에 있는 동안만 편집 상태를 붙이고, 벗어나면 HTML 로 되돌려 둔다. */
@Composable
private fun RichBlockEditor(block: EditBlock.Rich, onSelect: () -> Unit) {
    val c = LocalTrubbiColors.current
    val state = rememberRichTextState()
    LaunchedEffect(block) {
        state.setHtml(block.html)
        block.state = state
    }
    DisposableEffect(block) {
        onDispose {
            block.html = state.toHtml()
            if (block.state === state) block.state = null
        }
    }
    Box(Modifier.fillMaxWidth()) {
        if (state.annotatedString.text.isEmpty()) {
            Text(
                stringResource(R.string.memo_body_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = c.secondaryLabel,
                modifier = Modifier.padding(6.dp),
            )
        }
        BasicRichTextEditor(
            state = state,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
                .onFocusChanged { if (it.isFocused) onSelect() },
        )
    }
}

@Composable
private fun TableEditor(table: EditBlock.Table, onSelect: () -> Unit) {
    val c = LocalTrubbiColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, c.separator, RoundedCornerShape(8.dp))
            .clickable { onSelect() },
    ) {
        table.rows.forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEachIndexed { ci, cell ->
                    BasicTextField(
                        value = cell,
                        onValueChange = { newVal ->
                            table.rows = table.rows.mapIndexed { ri, rr ->
                                if (ri == r) rr.mapIndexed { cj, cc -> if (cj == ci) newVal else cc } else rr
                            }
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (r == 0) FontWeight.SemiBold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .then(if (r == 0) Modifier.background(MaterialTheme.colorScheme.surfaceVariant) else Modifier)
                            .border(0.5.dp, c.separator)
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FormatToggle(icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val c = LocalTrubbiColors.current
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else c.separator, RoundedCornerShape(10.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
private fun SizeToggle(label: String, sizeSp: Int, selected: Boolean, onClick: () -> Unit) {
    val c = LocalTrubbiColors.current
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else c.separator, RoundedCornerShape(10.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = sizeSp.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ToolbarDivider() {
    Box(
        Modifier
            .width(1.dp)
            .heightIn(min = 22.dp)
            .background(LocalTrubbiColors.current.separator),
    )
}

@Composable
private fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    val c = LocalTrubbiColors.current
    Box(
        Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else c.separator,
                CircleShape,
            )
            .clickable { onClick() },
    )
}

@Composable
private fun InsertChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SmallActionText(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun IconBtn(icon: ImageVector, onClick: () -> Unit, tint: Color = LocalTrubbiColors.current.secondaryLabel) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
    }
}

package com.naury.trubbi.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichText
import com.naury.trubbi.domain.memo.MemoBlock
import com.naury.trubbi.ui.theme.LocalTrubbiColors

/**
 * 메모 블록을 읽기 전용으로 그린다.
 *
 * 편집기와 같은 블록 목록을 받아 같은 모양으로 그린다. 상세 화면에서 보이는 모습이 방금
 * 편집기에서 보던 것과 달라지면 사용자는 저장이 잘못됐다고 느낀다.
 *
 * @param blocks 그릴 블록들
 * @param modifier 바깥 배치용
 */
@Composable
fun MemoBlocksView(blocks: List<MemoBlock>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MemoBlock.Text -> {
                    if (block.text.isNotBlank()) {
                        Text(
                            block.text,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = block.sizeSp.sp,
                                fontWeight = if (block.bold) FontWeight.Bold else FontWeight.Normal,
                                color = block.color?.let { Color(it) } ?: MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                    }
                }
                is MemoBlock.Html -> {
                    // 편집기와 같은 엔진으로 그린다. 다른 걸 쓰면 저장 전후 모양이 미묘하게 달라진다.
                    val state = rememberRichTextState()
                    LaunchedEffect(block.html) { state.setHtml(block.html) }
                    BasicRichText(
                        state = state,
                        style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is MemoBlock.Image -> {
                    AsyncImage(
                        model = block.uri,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .clip(RoundedCornerShape(14.dp)),
                    )
                }
                is MemoBlock.Table -> TableView(block)
            }
        }
    }
}

@Composable
private fun TableView(table: MemoBlock.Table) {
    val c = LocalTrubbiColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, c.separator, RoundedCornerShape(10.dp)),
    ) {
        table.rows.forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { cell ->
                    Text(
                        cell,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (r == 0) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .then(if (r == 0) Modifier.background(MaterialTheme.colorScheme.surfaceVariant) else Modifier)
                            .border(0.5.dp, c.separator)
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }
}

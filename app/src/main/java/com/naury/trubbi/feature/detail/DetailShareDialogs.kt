package com.naury.trubbi.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.trubbi.R
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.PillButton
import com.naury.trubbi.ui.designsystem.TrubbiPopupCard

/**
 * 상세 화면에서 여는 공유 팝업 — 사진 공유 옵션과 문서 공유 형식 선택.
 */
@Composable
internal fun ShareOptionsDialog(
    isBatch: Boolean,
    hasLocation: Boolean,
    canWatermark: Boolean,
    defaultText: (includeLocation: Boolean) -> String,
    onDismiss: () -> Unit,
    onShare: (allMedia: Boolean, text: String, watermark: Boolean, includeLoc: Boolean) -> Unit,
) {
    var allMedia by remember { mutableStateOf(false) }
    var includeLoc by remember { mutableStateOf(hasLocation) }
    var watermark by remember { mutableStateOf(false) }
    var text by remember(includeLoc) { mutableStateOf(defaultText(includeLoc)) }
    TrubbiPopupCard(onDismiss = onDismiss) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.detail_share_options),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    AppIcons.Close,
                    contentDescription = stringResource(R.string.action_close),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (isBatch) {
            com.naury.trubbi.ui.designsystem.SegmentedControl(
                options = listOf(stringResource(R.string.detail_share_current), stringResource(R.string.detail_share_all)),
                selectedIndex = if (allMedia) 1 else 0,
                onSelected = { allMedia = it == 1 },
            )
        }
        if (hasLocation) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(checked = includeLoc, onCheckedChange = { includeLoc = it })
                Text(stringResource(R.string.detail_include_address), color = MaterialTheme.colorScheme.onSurface)
            }
        }
        if (canWatermark) {
            ShareToggleRow(
                title = stringResource(R.string.detail_watermark_toggle),
                subtitle = stringResource(R.string.detail_watermark_sub),
                checked = watermark,
                onChange = { watermark = it },
            )
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.detail_message)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            shape = RoundedCornerShape(16.dp),
        )
        PillButton(stringResource(R.string.action_share), onClick = {
            onShare(allMedia, text, watermark, includeLoc)
        }, modifier = Modifier.fillMaxWidth(), icon = AppIcons.Share)
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).clickable { onDismiss() }.padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.action_cancel),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 문서 공유 팝업 — PDF 로 보낼지 이미지로 보낼지 고른다.
 *
 * @param hasLocation 위치가 있는 기록인지. 없으면 주소 포함 선택지를 숨긴다
 * @param onDismiss 닫을 때
 * @param onSharePdf PDF 로 공유를 누를 때
 * @param onShareImages 이미지로 공유를 누를 때 (워터마크 여부, 주소 포함 여부)
 */
@Composable
internal fun DocumentShareDialog(
    hasLocation: Boolean,
    onDismiss: () -> Unit,
    onSharePdf: () -> Unit,
    onShareImages: (watermark: Boolean, includeLoc: Boolean) -> Unit,
) {
    var watermark by remember { mutableStateOf(true) }
    var includeLoc by remember { mutableStateOf(hasLocation) }
    TrubbiPopupCard(onDismiss = onDismiss) {
        Text(
            stringResource(R.string.detail_share_document),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )
        PillButton(stringResource(R.string.detail_share_as_pdf), onClick = onSharePdf, modifier = Modifier.fillMaxWidth(), icon = AppIcons.Description)

        Text(stringResource(R.string.detail_or_share_image), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (hasLocation) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(checked = includeLoc, onCheckedChange = { includeLoc = it })
                Text(stringResource(R.string.detail_include_address), color = MaterialTheme.colorScheme.onSurface)
            }
        }
        ShareToggleRow(
            title = stringResource(R.string.detail_watermark_toggle),
            subtitle = stringResource(R.string.detail_watermark_sub),
            checked = watermark,
            onChange = { watermark = it },
        )
        PillButton(
            stringResource(R.string.detail_share_as_image),
            onClick = { onShareImages(watermark, includeLoc) },
            modifier = Modifier.fillMaxWidth(),
            icon = AppIcons.ImageIcon,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).clickable { onDismiss() }.padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.action_cancel),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ShareToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange)
    }
}

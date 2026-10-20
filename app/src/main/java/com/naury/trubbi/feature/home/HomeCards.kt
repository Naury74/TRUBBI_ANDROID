package com.naury.trubbi.feature.home

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.naury.trubbi.R
import com.naury.trubbi.core.util.DateUtils
import com.naury.trubbi.domain.model.RecordType
import com.naury.trubbi.navigation.QuickAction
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.glass
import com.naury.trubbi.ui.designsystem.pressable
import com.naury.trubbi.ui.theme.LocalTrubbiColors

/**
 * 홈의 바로가기 네 칸 — 문서 스캔·바코드·메모·사진 가져오기.
 *
 * 탭 바의 "+" 에도 있지만, 홈에서 한 번에 닿게 꺼내 둔다. 경로 계획은 바로 위 여정 카드가 맡으므로 넣지 않는다.
 */
@Composable
internal fun HomeQuickActions(onAction: (QuickAction) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalTrubbiColors.current
    val items = listOf(
        Triple(QuickAction.DOCUMENT, AppIcons.Description, R.string.quick_document_scan),
        Triple(QuickAction.BARCODE, AppIcons.QrCodeScanner, R.string.home_action_scan_barcode),
        Triple(QuickAction.MEMO, AppIcons.EditNote, R.string.home_action_quick_memo),
        Triple(QuickAction.IMPORT_PHOTO, AppIcons.PhotoLibrary, R.string.home_action_import_photo),
    )
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { (action, icon, label) ->
            QuickTile(icon, stringResource(label), c.isDark, Modifier.weight(1f)) { onAction(action) }
        }
    }
}

@Composable
private fun QuickTile(icon: ImageVector, label: String, dark: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Column(
        modifier
            .glass(RoundedCornerShape(20.dp), elevation = 2.dp)
            .pressable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(primary.copy(alpha = if (dark) 0.22f else 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = primary, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/** 오늘 남긴 기록 수. 종류마다 숫자 하나씩, 칸은 얇은 선으로만 나눈다. 0 인 칸은 흐리게. */
@Composable
internal fun TodaySummaryCard(
    photos: Int,
    videos: Int,
    documents: Int,
    barcodes: Int,
    memos: Int,
    onOpenType: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalTrubbiColors.current
    Column(modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), elevation = 2.dp).padding(vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.home_today),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(DateUtils.formatDate(DateUtils.todayIso()), style = MaterialTheme.typography.labelMedium, color = c.secondaryLabel)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            val cells = listOf(
                Triple("PHOTO", photos, RecordType.PHOTO.labelRes),
                Triple("VIDEO", videos, RecordType.VIDEO.labelRes),
                Triple("DOCUMENT", documents, RecordType.DOCUMENT.labelRes),
                Triple("BARCODE", barcodes, RecordType.BARCODE.labelRes),
                Triple("MEMO", memos, RecordType.MEMO.labelRes),
            )
            cells.forEachIndexed { i, (type, n, label) ->
                if (i > 0) {
                    Box(Modifier.width(1.dp).height(28.dp).background(c.separator.copy(alpha = 0.6f)))
                }
                Column(
                    Modifier.weight(1f).pressable { onOpenType(type) }.padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "$n",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (n > 0) MaterialTheme.colorScheme.primary else c.secondaryLabel.copy(alpha = 0.55f),
                    )
                    Text(
                        stringResource(label),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (n > 0) MaterialTheme.colorScheme.onSurface else c.secondaryLabel,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 페이지 배경 위의 구역 제목. */
@Composable
internal fun HomeSectionHeader(title: String, actionLabel: String?, onAction: () -> Unit = {}) {
    val c = LocalTrubbiColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null) {
            Row(Modifier.pressable(onClick = onAction), verticalAlignment = Alignment.CenterVertically) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge, color = c.secondaryLabel)
                Icon(AppIcons.ChevronRight, contentDescription = null, tint = c.secondaryLabel, modifier = Modifier.size(16.dp))
            }
        }
    }
}

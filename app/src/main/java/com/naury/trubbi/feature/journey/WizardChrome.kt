package com.naury.trubbi.feature.journey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.trubbi.R
import com.naury.trubbi.core.i18n.label
import com.naury.trubbi.domain.model.StopKind
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.pressable
import com.naury.trubbi.ui.theme.LocalTrubbiColors

/**
 * 마법사 공통 장식 — 단계 표시, 확정된 정차지 줄, 칩과 버튼.
 */
/** Three-step progress (하차지 → 상차지 → 경유지) shown at the top of the wizard. */
@Composable
internal fun StepIndicator(step: WizardStep) {
    val c = LocalTrubbiColors.current
    val steps = listOf(
        stringResource(R.string.wizard_step_dropoff) to WizardStep.DEST,
        stringResource(R.string.wizard_step_pickup) to WizardStep.ORIGIN,
        stringResource(R.string.wizard_step_via) to WizardStep.EXTRA,
    )
    val currentIdx = steps.indexOfFirst { it.second == step }
    Row(verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { i, (label, _) ->
            val done = i < currentIdx
            val active = i == currentIdx
            val color = when {
                active -> MaterialTheme.colorScheme.primary
                done -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else -> c.separator
            }
            Box(
                Modifier
                    .size(if (active) 24.dp else 22.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (active || done) color else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    Icon(AppIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(13.dp))
                } else {
                    Text(
                        "${i + 1}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (active) MaterialTheme.colorScheme.onPrimary else c.secondaryLabel,
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                ),
                color = if (active) MaterialTheme.colorScheme.onSurface else c.secondaryLabel,
            )
            if (i < steps.size - 1) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .height(2.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(if (i < currentIdx) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else c.separator),
                )
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}

/** 계획된 경로의 한 정차지 행 — 순번 + 종류 + 이름 + 삭제 + 드래그 손잡이. */
@Composable
internal fun PlannedStopRow(
    order: Int,
    kind: StopKind,
    name: String,
    handle: Modifier,
    dragging: Boolean,
    onRemove: () -> Unit,
) {
    val c = LocalTrubbiColors.current
    Row(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (dragging) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                },
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(24.dp).clip(RoundedCornerShape(percent = 50)).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text("$order", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            when (kind) {
                StopKind.ORIGIN -> AppIcons.MyLocation
                StopKind.PICKUP -> AppIcons.Pickup
                StopKind.DROPOFF -> AppIcons.Dropoff
            },
            contentDescription = null,
            tint = c.secondaryLabel,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "${kind.label()} · $name",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Icon(
            AppIcons.Close,
            contentDescription = stringResource(R.string.action_delete),
            tint = c.secondaryLabel,
            modifier = Modifier.size(16.dp).pressable(onClick = onRemove),
        )
        Spacer(Modifier.width(10.dp))
        Icon(AppIcons.DragHandle, contentDescription = stringResource(R.string.wizard_reorder), tint = c.secondaryLabel, modifier = handle.size(24.dp))
    }
}

/** Header chip showing the ROLE the picked place will take (출발지/경유지/도착지). */
@Composable
internal fun OrderTag(label: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
internal fun QuickChipSmall(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    label: String,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val c = LocalTrubbiColors.current
    Row(
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(
                if (accent) {
                    MaterialTheme.colorScheme.primary.copy(alpha = if (c.isDark) 0.28f else 0.14f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .pressable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun KindChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .pressable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Outlined, clearly-visible "나중에 입력하기 / 건너뛰기" action. */
@Composable
internal fun LaterButton(text: String, onClick: () -> Unit) {
    val c = LocalTrubbiColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (c.isDark) 0.7f else 0.9f))
            .pressable(onClick = onClick)
            .padding(vertical = 17.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.ArrowDownward, contentDescription = null, tint = c.secondaryLabel, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

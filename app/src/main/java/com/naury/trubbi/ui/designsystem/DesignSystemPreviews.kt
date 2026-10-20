package com.naury.trubbi.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.naury.trubbi.R
import com.naury.trubbi.ui.theme.LocalTrubbiColors
import com.naury.trubbi.ui.theme.TrubbiTheme

/**
 * 디자인 시스템 미리보기.
 *
 * 화면 하나를 통째로 띄우려면 Hilt·Room·위치 권한까지 붙어야 해서 미리보기가 거의 뜨지 않는다.
 * 그래서 상태가 없는 디자인 시스템 조각만 모아 미리보기를 붙였다.
 */
@Composable
private fun PreviewSurface(dark: Boolean, content: @Composable () -> Unit) {
    TrubbiTheme(darkTheme = dark) {
        Column(
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

@Preview(name = "버튼 · 라이트", showBackground = true)
@Composable
private fun ButtonsLightPreview() = PreviewSurface(dark = false) { ButtonSamples() }

@Preview(name = "버튼 · 다크", showBackground = true)
@Composable
private fun ButtonsDarkPreview() = PreviewSurface(dark = true) { ButtonSamples() }

@Composable
private fun ButtonSamples() {
    PillButton(text = "여정 시작", onClick = {}, icon = AppIcons.Truck, modifier = Modifier.fillMaxWidth())
    PillButton(text = "생성 중…", onClick = {}, loading = true, modifier = Modifier.fillMaxWidth())
    PillButton(
        text = "취소",
        onClick = {},
        enabled = false,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassIconButton(AppIcons.Share, onClick = {}, stringResource(R.string.action_share))
        GlassIconButton(AppIcons.Star, onClick = {}, stringResource(R.string.action_favorite), tint = LocalTrubbiColors.current.orange)
        GlassIconButton(AppIcons.Delete, onClick = {}, stringResource(R.string.action_delete), tint = LocalTrubbiColors.current.red)
        GlassPillButton(text = "지도", onClick = {}, icon = AppIcons.LocationOn)
    }
}

@Preview(name = "탭 · 칩 · 라이트", showBackground = true)
@Composable
private fun SelectorsLightPreview() = PreviewSurface(dark = false) { SelectorSamples() }

@Preview(name = "탭 · 칩 · 다크", showBackground = true)
@Composable
private fun SelectorsDarkPreview() = PreviewSurface(dark = true) { SelectorSamples() }

@Composable
private fun SelectorSamples() {
    SegmentedControl(
        options = listOf("타임라인", "기록", "비용", "지도"),
        selectedIndex = 1,
        onSelected = {},
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatPill(value = "12", label = "사진", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        StatPill(value = "0", label = "동영상", tint = LocalTrubbiColors.current.accent2, modifier = Modifier.weight(1f), active = false)
        StatPill(value = "3", label = "문서", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.weight(1f))
    }
}

@Preview(name = "카드 · 헤더 · 라이트", showBackground = true)
@Composable
private fun ContainersLightPreview() = PreviewSurface(dark = false) { ContainerSamples() }

@Preview(name = "카드 · 헤더 · 다크", showBackground = true)
@Composable
private fun ContainersDarkPreview() = PreviewSurface(dark = true) { ContainerSamples() }

@Composable
private fun ContainerSamples() {
    TrubbiLargeTitle(title = "기록", subtitle = "오늘 저장한 것들")
    GlassTopBar(title = "부산 신항 → 성남 센터", onBack = {})
    SectionCard(title = "백업") {
        Text(
            "마지막 백업: 어제 21:40",
            style = MaterialTheme.typography.bodyMedium,
            color = LocalTrubbiColors.current.secondaryLabel,
            modifier = Modifier.padding(16.dp),
        )
    }
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            "유리 카드 위의 본문",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(18.dp),
        )
    }
}

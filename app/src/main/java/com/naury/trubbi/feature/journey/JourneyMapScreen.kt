package com.naury.trubbi.feature.journey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.naury.trubbi.R
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.pressable

/**
 * 여정 경로 전체화면 지도.
 *
 * @param journeyId 볼 여정 id
 * @param onBack 뒤로 갈 때
 * @param onOpenRecord 마커를 눌러 기록 상세로 갈 때 (기록 id)
 */
@Composable
fun JourneyMapScreen(
    onBack: () -> Unit,
    onOpenRecord: (String) -> Unit,
    viewModel: JourneyDetailViewModel = hiltViewModel(),
) {
    val journey by viewModel.journey.collectAsState()
    val routePoints by viewModel.routePoints.collectAsState()
    val records by viewModel.records.collectAsState()
    val issues by viewModel.issues.collectAsState()
    val j = journey

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (j != null) {
            JourneyMapCard(
                stops = j.orderedStops,
                routePoints = routePoints,
                records = records,
                onRecordClick = onOpenRecord,
                interactive = true,
                issues = issues,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // 지도 위에 얹는 닫기 버튼.
        Box(
            Modifier
                .statusBarsPadding()
                .padding(12.dp)
                .size(40.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color.Black.copy(alpha = 0.55f))
                .pressable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Close, contentDescription = stringResource(R.string.action_close), tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

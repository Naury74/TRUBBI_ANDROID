package com.naury.trubbi.feature.journey

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naury.trubbi.R
import com.naury.trubbi.data.i18n.routeLabel
import com.naury.trubbi.data.local.JourneyWithStops
import com.naury.trubbi.data.repo.JourneyRepository
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.GlassTopBar
import com.naury.trubbi.ui.designsystem.appearOnce
import com.naury.trubbi.ui.designsystem.glass
import com.naury.trubbi.ui.designsystem.pressable
import com.naury.trubbi.ui.theme.LocalTrubbiColors
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 지난 여정 목록.
 *
 * 진행 중 여정은 홈 카드가 맡으므로 여기에는 완료된 것만 보여 준다.
 */
@HiltViewModel
class JourneyHistoryViewModel @Inject constructor(
    repo: JourneyRepository,
) : ViewModel() {
    /** 완료된 여정, 최신순. */
    val journeys = repo.observeJourneys()
        .map { list -> list.filter { it.journey.completedAt != null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<JourneyWithStops>())
}

/**
 * 지난 여정 화면 — 누르면 그 여정의 상세(타임라인·기록·비용·지도·리포트)로 간다.
 *
 * @param onBack 뒤로 갈 때. 하단 탭으로 열었으면 null(뒤로 버튼 없음)
 * @param onOpenJourney 여정 상세로 (여정 id)
 * @param onStartJourney 주면 맨 위에 "새 여정 시작" 버튼을 둔다
 * @param bottomPadding 하단 탭 바에 가리지 않도록 목록 끝에 더 둘 여백
 * @param viewModel 목록 상태
 */
@Composable
fun JourneyHistoryScreen(
    onBack: (() -> Unit)?,
    onOpenJourney: (String) -> Unit,
    onStartJourney: (() -> Unit)? = null,
    bottomPadding: androidx.compose.ui.unit.Dp = 8.dp,
    viewModel: JourneyHistoryViewModel = hiltViewModel(),
) {
    val c = LocalTrubbiColors.current
    val journeys by viewModel.journeys.collectAsState()
    val resources = LocalResources.current
    // 날짜 형식은 기기 언어를 따른다.
    val dayFmt = remember { SimpleDateFormat("M.d (E)", Locale.getDefault()) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val stopCountLabel: (Int) -> String = { count -> resources.getQuantityString(R.plurals.journey_stop_count, count, count) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        GlassTopBar(title = stringResource(R.string.journey_history_title), onBack = onBack)
        if (onStartJourney != null) {
            com.naury.kinetica.ui.KButton(
                text = stringResource(R.string.home_action_start_journey),
                onClick = onStartJourney,
                icon = com.naury.trubbi.ui.designsystem.AppIcons.Navigation,
                size = com.naury.kinetica.ui.KButtonSize.Large,
                fullWidth = true,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (journeys.isEmpty()) {
            com.naury.kinetica.ui.KEmptyState(
                title = stringResource(R.string.journey_history_empty),
                art = { com.naury.kinetica.ui.KEmptyIcon(com.naury.kinetica.ui.KEmptyIconKind.EmptyInbox) },
                modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
            )
            return
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(journeys, key = { _, entry -> entry.journey.journeyId }) { i, j ->
                val started = j.journey.startedAt
                val completed = j.journey.completedAt ?: started
                val sameDay = dayFmt.format(Date(started)) == dayFmt.format(Date(completed))
                Column(
                    Modifier
                        .appearOnce((i * 45).coerceAtMost(360))
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(20.dp))
                        .pressable { onOpenJourney(j.journey.journeyId) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            AppIcons.Truck,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            j.routeLabel(),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            AppIcons.KeyboardArrowRight,
                            contentDescription = null,
                            tint = c.secondaryLabel,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                    Spacer(Modifier.padding(top = 5.dp))
                    val period = if (sameDay) {
                        "${dayFmt.format(Date(started))} ${timeFmt.format(Date(started))} ~ ${timeFmt.format(Date(completed))}"
                    } else {
                        "${dayFmt.format(Date(started))} ${timeFmt.format(Date(started))} ~ " +
                            "${dayFmt.format(Date(completed))} ${timeFmt.format(Date(completed))}"
                    }
                    val stats = buildList {
                        add(period)
                        add(stopCountLabel(j.stops.size))
                        if (j.journey.totalDistanceM >= 1000) add("%.1fkm".format(j.journey.totalDistanceM / 1000f))
                    }
                    Text(
                        stats.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = c.secondaryLabel,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

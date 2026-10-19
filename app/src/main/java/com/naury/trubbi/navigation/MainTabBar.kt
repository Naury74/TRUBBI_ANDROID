package com.naury.trubbi.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.KActionSheet
import com.naury.kinetica.ui.KButton
import com.naury.kinetica.ui.KDialogAction
import com.naury.kinetica.ui.KFloatingTabBar
import com.naury.kinetica.ui.KIconButton
import com.naury.kinetica.ui.KTab
import com.naury.kinetica.ui.KVariant
import com.naury.trubbi.R
import com.naury.trubbi.ui.designsystem.AppIcons

/** 하단 탭 하나. 순서가 곧 탭 바의 순서다. */
enum class MainTab(val route: String) {
    HOME(Routes.HOME),
    RECORDS(Routes.RECORDS),
    JOURNEYS(Routes.JOURNEYS),
    SETTINGS(Routes.SETTINGS),
    ;

    companion object {
        fun of(route: String?): MainTab? = entries.firstOrNull { it.route == route }
    }
}

/** 탭 바 위 "+" 메뉴에서 고를 수 있는 동작. */
enum class QuickAction { PLAN_ROUTE, DOCUMENT, BARCODE, MEMO, IMPORT_PHOTO }

/**
 * 앱 아래에 떠 있는 탭 바. 위에는 촬영 버튼과 "+"(빠른 실행) 버튼이 붙는다.
 *
 * 화물차 기사가 가장 자주 하는 일(찍기)은 한 번에, 나머지(경로·문서·바코드·메모·사진 가져오기)는 "+" 한 번 더로
 * 닿게 한다. 예전에는 이 동작들이 홈 화면 독의 "+" 안에만 있어 다른 화면에서는 찾을 수 없었다.
 *
 * @param current 지금 탭
 * @param journeyActive 여정이 진행 중인지. 경로 동작의 이름이 "정차지 추가"로 바뀐다
 * @param onSelect 탭을 눌렀을 때
 * @param onCapture 촬영 버튼
 * @param onQuick "+" 메뉴에서 고른 동작
 */
@Composable
fun MainTabBar(
    current: MainTab,
    journeyActive: Boolean,
    onSelect: (MainTab) -> Unit,
    onCapture: () -> Unit,
    onQuick: (QuickAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var quickOpen by remember { mutableStateOf(false) }
    val tabs = listOf(
        KTab(stringResource(R.string.tab_home), AppIcons.Home),
        KTab(stringResource(R.string.tab_records), AppIcons.PhotoLibrary),
        KTab(stringResource(R.string.tab_journeys), AppIcons.Truck),
        KTab(stringResource(R.string.tab_settings), AppIcons.Settings),
    )
    KFloatingTabBar(
        tabs = tabs,
        selectedIndex = current.ordinal,
        onSelect = { onSelect(MainTab.entries[it]) },
        modifier = modifier.navigationBarsPadding().padding(bottom = 8.dp),
        accessory = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                KButton(stringResource(R.string.floating_capture), onCapture, icon = AppIcons.CameraAlt)
                KIconButton(AppIcons.Add, stringResource(R.string.tab_quick_more), onClick = { quickOpen = true }, variant = KVariant.Filled)
            }
        },
    )

    val planLabel = stringResource(if (journeyActive) R.string.planner_title_add else R.string.home_action_start_journey)
    val labels = mapOf(
        QuickAction.PLAN_ROUTE to planLabel,
        QuickAction.DOCUMENT to stringResource(R.string.quick_document_scan),
        QuickAction.BARCODE to stringResource(R.string.home_action_scan_barcode),
        QuickAction.MEMO to stringResource(R.string.home_action_quick_memo),
        QuickAction.IMPORT_PHOTO to stringResource(R.string.home_action_import_photo),
    )
    val icons = mapOf(
        QuickAction.PLAN_ROUTE to AppIcons.Navigation,
        QuickAction.DOCUMENT to AppIcons.Description,
        QuickAction.BARCODE to AppIcons.QrCodeScanner,
        QuickAction.MEMO to AppIcons.EditNote,
        QuickAction.IMPORT_PHOTO to AppIcons.PhotoLibrary,
    )
    KActionSheet(
        visible = quickOpen,
        onDismissRequest = { quickOpen = false },
        title = stringResource(R.string.tab_quick_title),
        actions = QuickAction.entries.map { a ->
            KDialogAction(label = labels.getValue(a), icon = icons.getValue(a), onClick = { onQuick(a) })
        } + KDialogAction(stringResource(R.string.action_cancel), com.naury.kinetica.ui.KDialogActionStyle.Cancel),
    )
}

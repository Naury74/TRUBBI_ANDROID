package com.naury.trubbi.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naury.trubbi.R
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.GlassIconButton

/**
 * 지도 위에 떠 있는 조작 버튼 묶음 — 현위치 복귀 + 확대/축소.
 *
 * 어떤 [KakaoMapView] 위에도 올려 쓸 수 있고, 카메라는 [MapHandle] 로 움직인다.
 *
 * @param onRecenter null 이면 현위치 버튼을 감춘다
 */
@Composable
fun MapControls(
    handle: MapHandle,
    modifier: Modifier = Modifier,
    onRecenter: (() -> Unit)? = null,
    onFitAll: (() -> Unit)? = null,
    recenterIcon: ImageVector = AppIcons.MyLocation,
    recenterLabel: String? = null,
) {
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (onRecenter != null) {
            GlassIconButton(recenterIcon, onRecenter, recenterLabel ?: stringResource(R.string.map_my_location), tint = MaterialTheme.colorScheme.primary)
        }
        if (onFitAll != null) {
            GlassIconButton(AppIcons.Route, onFitAll, stringResource(R.string.map_fit_all))
        }
        GlassIconButton(AppIcons.Add, { handle.zoomIn() }, stringResource(R.string.map_zoom_in))
        GlassIconButton(AppIcons.Remove, { handle.zoomOut() }, stringResource(R.string.map_zoom_out))
    }
}

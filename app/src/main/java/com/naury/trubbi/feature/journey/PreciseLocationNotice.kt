package com.naury.trubbi.feature.journey

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.naury.kinetica.ui.KButton
import com.naury.kinetica.ui.KButtonSize
import com.naury.kinetica.ui.KVariant
import com.naury.trubbi.R
import com.naury.trubbi.ui.designsystem.AppIcons

/**
 * 진행 중 여정에서 정밀 위치가 꺼져 있으면 띄우는 안내.
 *
 * 대략적 위치만 허용하면 좌표가 수백 m~수 km 씩 어긋나 경로가 거칠게 남고 도착 알림이 오지 않는다. 예전에는 이걸
 * 알 방법이 없어 기사는 기록이 왜 엉성한지 몰랐다. 버튼을 누르면 정밀 위치로 올려 달라고 다시 묻는다.
 * 설정에서 권한을 바꾸고 돌아오면 다시 확인해 사라진다.
 */
@Composable
fun PreciseLocationNotice(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    fun precise() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(precise()) }
    LifecycleResumeEffect(Unit) {
        granted = precise()
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = precise() }
    if (granted) return

    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.precise_location_off),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
        KButton(
            stringResource(R.string.precise_location_turn_on),
            {
                launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
            size = KButtonSize.Small,
            variant = KVariant.Tonal,
        )
    }
}

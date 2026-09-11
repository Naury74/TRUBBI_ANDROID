package com.naury.trubbi.ui.common

import android.app.Activity
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.naury.trubbi.R
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.GlassIconButton
import com.naury.trubbi.ui.designsystem.PillButton

/**
 * 창구에서 바로 찍히도록 바코드를 전체 화면으로 띄운다.
 *
 * 코드를 크게 띄우고 화면 밝기를 최대로 올린다. 밝기는 닫을 때 원래 값으로 되돌린다.
 *
 * @param bitmap 미리 렌더한 바코드 이미지. null 이면 실패 안내를 보여 준다
 * @param value 바코드 값
 * @param format 규격 이름 (EAN_13 등)
 * @param isTwoDimensional QR 처럼 2차원인지 — 표시 높이를 정한다
 */
@Composable
fun BarcodeFullScreenDialog(
    bitmap: Bitmap?,
    value: String,
    format: String?,
    isTwoDimensional: Boolean,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        val previous = window?.attributes?.screenBrightness
        window?.let { w ->
            w.attributes = w.attributes.apply { screenBrightness = 1f }
        }
        onDispose {
            window?.let { w ->
                w.attributes = w.attributes.apply {
                    screenBrightness = previous ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(24.dp),
        ) {
            GlassIconButton(
                AppIcons.Close,
                onDismiss,
                stringResource(R.string.action_close),
                size = 44.dp,
                tint = Color.Black,
                modifier = Modifier.align(Alignment.TopEnd),
            )
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.cd_barcode, value),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isTwoDimensional) 320.dp else 180.dp),
                    )
                } else {
                    Text(stringResource(R.string.barcode_render_failed), color = Color.Gray)
                }
                Spacer(Modifier.height(16.dp))
                Text(value, style = MaterialTheme.typography.titleLarge, color = Color.Black)
                format?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                }
                Spacer(Modifier.height(28.dp))
                PillButton(
                    stringResource(R.string.barcode_share_image),
                    onClick = onShare,
                    icon = AppIcons.Share,
                    modifier = Modifier.fillMaxWidth(0.7f),
                )
            }
        }
    }
}

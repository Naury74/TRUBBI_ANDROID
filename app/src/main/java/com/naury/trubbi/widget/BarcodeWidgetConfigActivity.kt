package com.naury.trubbi.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naury.trubbi.R
import com.naury.trubbi.ui.common.RecordListRow
import com.naury.trubbi.ui.designsystem.TrubbiLargeTitle
import com.naury.trubbi.ui.theme.TrubbiTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 바코드 위젯을 홈에 놓을 때 뜨는 설정 화면 — 어떤 바코드를 보여 줄지 고른다.
 */
@AndroidEntryPoint
class BarcodeWidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 먼저 취소로 결과를 세팅해 둔다. 고르지 않고 뒤로 나가면 위젯이 추가되지 않아야 한다.
        setResult(Activity.RESULT_CANCELED)

        val widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        enableEdgeToEdge()
        setContent {
            TrubbiTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ConfigContent(
                        onPick = { recordId ->
                            BarcodeWidgetPrefs.setRecordId(this, widgetId, recordId)
                            BarcodeViewWidget.updateOne(this, widgetId)
                            setResult(
                                Activity.RESULT_OK,
                                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
                            )
                            finish()
                        },
                    )
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ConfigContent(
    onPick: (String) -> Unit,
    viewModel: BarcodeWidgetConfigViewModel = hiltViewModel(),
) {
    val barcodes by viewModel.barcodes.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TrubbiLargeTitle(
            title = stringResource(R.string.widget_pick_barcode_title),
            subtitle = stringResource(R.string.widget_pick_barcode_sub),
        )
        if (barcodes.isEmpty()) {
            Text(
                stringResource(R.string.widget_no_barcode),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
            ) {
                items(barcodes, key = { it.record.recordId }) { record ->
                    RecordListRow(record, onClick = { onPick(record.record.recordId) })
                }
            }
        }
    }
}

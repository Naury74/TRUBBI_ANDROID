package com.naury.trubbi.feature.barcode
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.naury.trubbi.R
import com.naury.trubbi.core.i18n.purposeLabel
import com.naury.trubbi.core.util.DateUtils
import com.naury.trubbi.ui.common.CameraPermissionGate
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.GlassIconButton
import com.naury.trubbi.ui.designsystem.PillButton
import com.naury.trubbi.ui.designsystem.TrubbiPopupCard
import com.naury.trubbi.ui.designsystem.liquidGlass

/**
 * 바코드 스캔 화면.
 *
 * 운송장·컨테이너 번호를 손으로 옮겨 적다 틀리는 일이 잦아서, 찍어서 값만 저장하고
 * 나중에 위젯에서 바로 꺼내 보여 줄 수 있게 했다.
 *
 * @param purposeName 저장할 때 붙일 용도 토큰
 * @param onClose 닫을 때
 * @param onSaved 저장을 마쳤을 때 (만들어진 기록 id)
 * @param viewModel 저장 처리
 */
@Composable
fun BarcodeScreen(
    purposeName: String,
    onClose: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: BarcodeViewModel = hiltViewModel(),
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraPermissionGate {
            BarcodeContent(purposeLabel(purposeName), onClose, onSaved, viewModel)
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun BarcodeContent(
    initialPurposeLabel: String,
    onClose: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: BarcodeViewModel,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val purposes by viewModel.purposes.collectAsStateWithLifecycle()
    var purpose by remember { mutableStateOf(initialPurposeLabel) }
    var detectedValue by remember { mutableStateOf<String?>(null) }
    var detectedFormat by remember { mutableStateOf("") }
    var manualMode by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    // 코드를 인식하면 기본 이름을 채운다. 저장 전에 수정할 수 있다.
    val defaultName = stringResource(R.string.barcode_default_title, purpose, DateUtils.shortStamp())
    LaunchedEffect(detectedValue) {
        if (detectedValue != null && name.isBlank()) name = defaultName
    }

    val scanner = remember { BarcodeScanning.getClient() }
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(LifecycleCameraController.IMAGE_ANALYSIS)
        }
    }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        controller.setImageAnalysisAnalyzer(
            ContextCompat.getMainExecutor(context),
            ImageAnalysis.Analyzer { proxy: ImageProxy ->
                val mediaImage = proxy.image
                if (mediaImage == null || detectedValue != null) {
                    proxy.close()
                    return@Analyzer
                }
                val input = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
                scanner.process(input)
                    .addOnSuccessListener { barcodes ->
                        val first = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }
                        if (first != null && detectedValue == null) {
                            detectedValue = first.rawValue
                            detectedFormat = formatName(first.format)
                        }
                    }
                    .addOnCompleteListener { proxy.close() }
            },
        )
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            scanner.close()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    this.controller = controller
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // 조준 안내 틀
        Box(
            Modifier
                .align(Alignment.Center)
                .size(260.dp, 180.dp)
                .border(3.dp, Color.White, RoundedCornerShape(20.dp)),
        )

        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            GlassIconButton(AppIcons.Close, onClose, stringResource(R.string.action_close), tint = Color.White)
            GlassIconButton(AppIcons.Keyboard, onClick = { manualMode = true }, stringResource(R.string.action_keyboard), tint = Color.White)
        }

        val value = detectedValue
        if (value != null) {
            ResultSheet(
                value = value,
                format = detectedFormat,
                name = name,
                onName = { name = it },
                purposes = purposes,
                selected = purpose,
                onPurpose = { purpose = it },
                onRescan = {
                    detectedValue = null
                    name = ""
                },
                onSave = { viewModel.save(value, detectedFormat, purpose, name, onSaved) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    if (manualMode) {
        ManualInputDialog(
            onDismiss = { manualMode = false },
            onConfirm = { enteredValue, enteredName ->
                manualMode = false
                if (enteredValue.isNotBlank()) viewModel.save(enteredValue, "MANUAL", purpose, enteredName, onSaved)
            },
        )
    }
}

@Composable
private fun ResultSheet(
    value: String,
    format: String,
    name: String,
    onName: (String) -> Unit,
    purposes: List<String>,
    selected: String,
    onPurpose: (String) -> Unit,
    onRescan: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .liquidGlass(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .padding(20.dp),
    ) {
        Text(stringResource(R.string.barcode_recognized), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(format, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        val nameFocus = remember { androidx.compose.ui.focus.FocusRequester() }
        androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { nameFocus.requestFocus() } }
        OutlinedTextField(
            value = name,
            onValueChange = onName,
            modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
            singleLine = true,
            label = { Text(stringResource(R.string.field_name)) },
            placeholder = { Text(stringResource(R.string.barcode_name_hint)) },
        )
        Spacer(Modifier.height(14.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(purposes) { p ->
                val sel = p == selected
                Box(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onPurpose(p) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        p,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (sel) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton(
                stringResource(R.string.barcode_rescan),
                onClick = onRescan,
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
            PillButton(stringResource(R.string.action_save), onClick = onSave, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ManualInputDialog(onDismiss: () -> Unit, onConfirm: (value: String, name: String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val shape = RoundedCornerShape(16.dp)
    TrubbiPopupCard(onDismiss = onDismiss) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.barcode_manual_entry),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    AppIcons.Close,
                    contentDescription = stringResource(R.string.action_close),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = shape,
            label = { Text(stringResource(R.string.barcode_value_label)) },
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = shape,
            label = { Text(stringResource(R.string.barcode_name_optional)) },
        )
        PillButton(stringResource(R.string.action_save), onClick = { onConfirm(text, name) }, modifier = Modifier.fillMaxWidth(), icon = AppIcons.Save)
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).clickable { onDismiss() }.padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.action_cancel),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatName(format: Int): String = when (format) {
    Barcode.FORMAT_QR_CODE -> "QR_CODE"
    Barcode.FORMAT_CODE_128 -> "CODE_128"
    Barcode.FORMAT_CODE_39 -> "CODE_39"
    Barcode.FORMAT_CODE_93 -> "CODE_93"
    Barcode.FORMAT_EAN_13 -> "EAN_13"
    Barcode.FORMAT_EAN_8 -> "EAN_8"
    Barcode.FORMAT_UPC_A -> "UPC_A"
    Barcode.FORMAT_UPC_E -> "UPC_E"
    Barcode.FORMAT_PDF417 -> "PDF417"
    Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
    Barcode.FORMAT_AZTEC -> "AZTEC"
    Barcode.FORMAT_ITF -> "ITF"
    Barcode.FORMAT_CODABAR -> "CODABAR"
    else -> "UNKNOWN"
}

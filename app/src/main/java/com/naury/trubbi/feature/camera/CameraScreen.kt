package com.naury.trubbi.feature.camera
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.video.AudioConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.naury.trubbi.R
import com.naury.trubbi.core.i18n.label
import com.naury.trubbi.core.i18n.purposeLabel
import com.naury.trubbi.core.util.DateUtils
import com.naury.trubbi.domain.model.Labeled
import com.naury.trubbi.domain.model.RecordType
import com.naury.trubbi.ui.common.CameraPermissionGate
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.GlassIconButton
import com.naury.trubbi.ui.designsystem.PillButton
import com.naury.trubbi.ui.designsystem.SegmentedControl
import com.naury.trubbi.ui.designsystem.TrubbiPopupCard
import com.naury.trubbi.ui.designsystem.TrubbiToastHost
import com.naury.trubbi.ui.designsystem.rememberTrubbiToastState
import java.io.File
import kotlinx.coroutines.delay

/** 카메라 화면의 촬영 모드 탭. */
private enum class CamMode(@StringRes override val labelRes: Int) : Labeled {
    PHOTO(RecordType.PHOTO.labelRes),
    VIDEO(RecordType.VIDEO.labelRes),
    BURST(R.string.camera_mode_burst),
    DOCUMENT(RecordType.DOCUMENT.labelRes),
}

/**
 * 촬영 화면 — 사진·연속 촬영·영상·문서 스캔을 한 화면에서 모드로 전환한다.
 *
 * 찍고 나서 바로 다음 컷으로 넘어갈 수 있게, 저장은 화면을 붙잡지 않는다. 위치와 OCR 은
 * 저장이 끝난 뒤 뒤에서 붙는다.
 *
 * @param purposeName 처음 선택돼 있을 용도 토큰
 * @param onClose 닫을 때
 * @param onSaved 저장을 마쳤을 때 (기록 id, 미디어 uri, 이어서 편집할지)
 * @param startInDocumentMode true 면 문서 스캔 모드로 시작한다(위젯에서 바로 들어올 때)
 * @param viewModel 촬영 상태
 */
@Composable
fun CameraScreen(
    purposeName: String,
    onClose: () -> Unit,
    onSaved: (recordId: String, mediaUri: String, edit: Boolean) -> Unit,
    startInDocumentMode: Boolean = false,
    viewModel: CameraViewModel = hiltViewModel(),
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraPermissionGate {
            CameraContent(
                initialPurposeLabel = purposeLabel(purposeName),
                startInDocumentMode = startInDocumentMode,
                onClose = onClose,
                onSaved = onSaved,
                viewModel = viewModel,
            )
        }
    }
}

@Composable
private fun CameraContent(
    initialPurposeLabel: String,
    startInDocumentMode: Boolean,
    onClose: () -> Unit,
    onSaved: (recordId: String, mediaUri: String, edit: Boolean) -> Unit,
    viewModel: CameraViewModel,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val editDefault by viewModel.editAfterDefault.collectAsStateWithLifecycle()
    val saveLocation by viewModel.saveLocation.collectAsStateWithLifecycle()
    val purposes by viewModel.purposes.collectAsStateWithLifecycle()
    val suggestedPurpose by viewModel.suggestedPurpose.collectAsStateWithLifecycle()
    val locLabel by viewModel.locationLabel.collectAsStateWithLifecycle()
    val photoWord = stringResource(RecordType.PHOTO.labelRes)
    val videoWord = stringResource(RecordType.VIDEO.labelRes)
    val documentWord = stringResource(RecordType.DOCUMENT.labelRes)
    var purpose by remember { mutableStateOf(initialPurposeLabel) }
    // 사용자가 아직 직접 고르지 않았다면, 진행 중 여정의 현재 단계(상차/하차)를 자동 선택.
    var purposeTouched by remember { mutableStateOf(false) }
    LaunchedEffect(suggestedPurpose) {
        suggestedPurpose?.let { if (!purposeTouched) purpose = it }
    }
    var torchOn by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    // 탭 포커스/핀치 줌 — 기본 카메라처럼 동작 + 시각 피드백.
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var focusPoint by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    var focusKey by remember { mutableStateOf(0) }
    var zoomLabel by remember { mutableStateOf("") }
    var zoomKey by remember { mutableStateOf(0) }
    var zoomVisible by remember { mutableStateOf(false) }
    val focusAnim = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(focusKey) {
        if (focusKey > 0) {
            focusAnim.snapTo(1f)
            focusAnim.animateTo(0f, androidx.compose.animation.core.tween(900))
            focusPoint = null
        }
    }
    LaunchedEffect(zoomKey) {
        if (zoomKey > 0) {
            zoomVisible = true
            delay(1000)
            zoomVisible = false
        }
    }
    var editAfter by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(if (startInDocumentMode) CamMode.DOCUMENT else CamMode.PHOTO) }
    LaunchedEffect(editDefault) { editAfter = editDefault }

    // 영상 녹화 상태
    var recording by remember { mutableStateOf<Recording?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0) }
    LaunchedEffect(isRecording) {
        elapsed = 0
        while (isRecording) {
            delay(1000)
            elapsed++
        }
    }

    // 연속 촬영 버퍼 — 저장 전까지 여기 쌓인다
    val burstFiles = remember { mutableStateListOf<File>() }

    // 촬영 후 이름 짓기
    var pendingPhoto by remember { mutableStateOf<File?>(null) }
    var pendingVideo by remember { mutableStateOf<File?>(null) }
    var showBurstNaming by remember { mutableStateOf(false) }

    // 스캔 결과를 잠시 들고 있는다 — PDF 와 원본 페이지 이미지. 저장 시트에서 보관 여부를 고른다.
    var pendingScan by remember { mutableStateOf<Pair<android.net.Uri, List<android.net.Uri>>?>(null) }
    val launchScan = rememberDocumentScanLauncher { pdf, pages ->
        if (pdf != null) pendingScan = pdf to pages
    }
    // 문서 탭 선택(또는 딥링크로 문서 모드 진입) 시 별도 버튼 없이 곧바로 스캐너를 띄운다.
    LaunchedEffect(mode) {
        if (mode == CamMode.DOCUMENT) launchScan()
    }

    // 셔터 플래시와 토스트
    var flashKey by remember { mutableStateOf(0) }
    val flashAlpha = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(flashKey) {
        if (flashKey > 0) {
            flashAlpha.snapTo(0.85f)
            flashAlpha.animateTo(0f, androidx.compose.animation.core.tween(420))
        }
    }
    val toast = rememberTrubbiToastState()

    // 영상에 필요한 녹음 권한
    var audioGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val audioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { audioGranted = it }

    // 위치 저장을 켜 둔 사용자에게는 촬영 전에 권한을 먼저 묻는다. 찍고 나서 물으면
    // 그 장면의 위치는 이미 놓친 뒤다.
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(saveLocation) {
        if (saveLocation &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE)
        }
    }
    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }
    LaunchedEffect(mode) {
        controller.setEnabledUseCases(
            if (mode == CamMode.VIDEO) {
                LifecycleCameraController.VIDEO_CAPTURE
            } else {
                LifecycleCameraController.IMAGE_CAPTURE
            },
        )
        if (mode == CamMode.VIDEO && !audioGranted) audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun capturePhoto(into: (File) -> Unit) {
        capturing = true
        flashKey++ // trigger shutter flash
        val file = File(context.cacheDir, "cap_${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        controller.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    capturing = false
                    into(file)
                }
                override fun onError(exception: ImageCaptureException) {
                    capturing = false
                    toast.show(resources.getString(R.string.camera_capture_failed, exception.message.orEmpty()))
                }
            },
        )
    }

    fun toggleRecording() {
        val active = recording
        if (active != null) {
            active.stop()
            return
        }
        val file = File(context.cacheDir, "vid_${System.currentTimeMillis()}.mp4")
        val audioConfig = if (audioGranted) AudioConfig.create(true) else AudioConfig.AUDIO_DISABLED
        recording = controller.startRecording(
            FileOutputOptions.Builder(file).build(),
            audioConfig,
            ContextCompat.getMainExecutor(context),
        ) { event ->
            when (event) {
                is VideoRecordEvent.Start -> isRecording = true
                is VideoRecordEvent.Finalize -> {
                    isRecording = false
                    recording = null
                    if (event.hasError()) {
                        toast.show(resources.getString(R.string.camera_record_failed))
                    } else {
                        pendingVideo = file
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    this.controller = controller
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }.also { previewView = it }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // 탭 포커스 + 핀치 줌 — 미리보기 영역에서. (상/하단 컨트롤은 이 위에 그려져 우선 처리됨)
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(controller) {
                    detectTapGestures { off ->
                        val pv = previewView ?: return@detectTapGestures
                        runCatching {
                            val point = pv.meteringPointFactory.createPoint(off.x, off.y)
                            controller.cameraControl?.startFocusAndMetering(
                                FocusMeteringAction.Builder(point).build(),
                            )
                        }
                        focusPoint = off
                        focusKey++
                    }
                }
                .pointerInput(controller) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom == 1f) return@detectTransformGestures
                        val zs = controller.zoomState.value
                        val min = zs?.minZoomRatio ?: 1f
                        val max = zs?.maxZoomRatio ?: 1f
                        val cur = zs?.zoomRatio ?: 1f
                        val next = (cur * zoom).coerceIn(min, max)
                        runCatching { controller.setZoomRatio(next) }
                        zoomLabel = "%.1fx".format(next)
                        zoomKey++
                    }
                },
        )

        // 탭 포커스 링.
        focusPoint?.let { p ->
            val half = with(androidx.compose.ui.platform.LocalDensity.current) { 36.dp.toPx() }
            Box(
                Modifier
                    .offset { androidx.compose.ui.unit.IntOffset((p.x - half).toInt(), (p.y - half).toInt()) }
                    .size(72.dp)
                    .graphicsLayer {
                        val s = 0.8f + focusAnim.value * 0.5f
                        scaleX = s
                        scaleY = s
                        alpha = focusAnim.value
                    }
                    .border(2.dp, Color.White, RoundedCornerShape(percent = 50)),
            )
        }

        // 상단 조작부
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(AppIcons.Close, onClose, stringResource(R.string.action_close), tint = Color.White)
            if (mode == CamMode.PHOTO) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (editAfter) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.45f))
                        .clickable { editAfter = !editAfter }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                ) {
                    Text(
                        stringResource(if (editAfter) R.string.camera_edit_after_on else R.string.camera_edit_after_off),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                    )
                }
            } else if (isRecording) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text("● ${formatElapsed(elapsed)}", color = Color(0xFFFF3B30), style = MaterialTheme.typography.labelLarge)
                }
            } else {
                Spacer(Modifier.size(1.dp))
            }
            GlassIconButton(
                if (torchOn) AppIcons.FlashOn else AppIcons.FlashOff,
                contentDescription = stringResource(
                    if (torchOn) R.string.camera_flash_off else R.string.camera_flash_on,
                ),
                onClick = {
                    torchOn = !torchOn
                    controller.enableTorch(torchOn)
                },
                tint = Color.White,
            )
        }

        // 하단 조작부
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (mode == CamMode.DOCUMENT) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        stringResource(R.string.camera_document_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                }
            }
            if (mode == CamMode.BURST && burstFiles.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(burstFiles) { f ->
                        AsyncImage(
                            model = f,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)),
                        )
                    }
                }
            }

            PurposeSelector(purposes = purposes, selected = purpose, onSelect = {
                purpose = it
                purposeTouched = true
            })

            Box(Modifier.padding(horizontal = 48.dp).fillMaxWidth()) {
                SegmentedControl(
                    options = CamMode.entries.map { it.label() },
                    selectedIndex = mode.ordinal,
                    onSelected = { if (!isRecording) mode = CamMode.entries[it] },
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                when (mode) {
                    CamMode.PHOTO -> ShutterButton(enabled = !capturing, recording = false) {
                        capturePhoto { f -> pendingPhoto = f }
                    }
                    CamMode.VIDEO -> ShutterButton(enabled = true, recording = isRecording) {
                        toggleRecording()
                    }
                    CamMode.BURST -> {
                        Spacer(Modifier.size(64.dp))
                        ShutterButton(enabled = !capturing, recording = false) {
                            capturePhoto { f -> burstFiles.add(f) }
                        }
                        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                            if (burstFiles.isNotEmpty()) {
                                Box(
                                    Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(percent = 50))
                                        .background(MaterialTheme.colorScheme.primary)
                                        .clickable { showBurstNaming = true },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            AppIcons.Check,
                                            contentDescription = stringResource(R.string.camera_save_batch),
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(22.dp),
                                        )
                                        Text("${burstFiles.size}", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                    CamMode.DOCUMENT -> {
                        PillButton(
                            stringResource(R.string.camera_start_doc_scan),
                            onClick = { launchScan() },
                            icon = AppIcons.Description,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        // 줌 배율 표시(핀치 중 잠깐).
        if (zoomVisible) {
            Box(
                Modifier.align(Alignment.Center).offset { androidx.compose.ui.unit.IntOffset(0, -260) }
                    .clip(RoundedCornerShape(percent = 50)).background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(zoomLabel, color = Color.White, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
        }

        // 카메라 위에 겹치는 셔터 플래시와 토스트
        if (flashAlpha.value > 0f) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flashAlpha.value)))
        }
        TrubbiToastHost(toast, modifier = Modifier.align(Alignment.BottomCenter))
    }

    pendingPhoto?.let { file ->
        val suggested = remember(locLabel, purpose) { viewModel.suggestName(purpose, photoWord) }
        CaptureNameSheet(
            previewFile = file,
            isVideo = false,
            suggested = suggested,
            onDismiss = {
                pendingPhoto = null
                file.delete()
            },
            onConfirm = { name ->
                pendingPhoto = null
                viewModel.onPhotoCaptured(file, purpose, name) { id, uri -> onSaved(id, uri, editAfter) }
            },
        )
    }
    pendingVideo?.let { file ->
        val suggested = remember(locLabel, purpose) { viewModel.suggestName(purpose, videoWord) }
        CaptureNameSheet(
            previewFile = null,
            isVideo = true,
            suggested = suggested,
            onDismiss = {
                pendingVideo = null
                file.delete()
            },
            onConfirm = { name ->
                pendingVideo = null
                viewModel.onVideoCaptured(file, purpose, name) { id, uri -> onSaved(id, uri, false) }
            },
        )
    }
    pendingScan?.let { (pdfUri, pages) ->
        // 인식된 문서 제목을 기본 이름으로 제안(없으면 위치/시간 기반). OCR이 끝나면 자동 갱신.
        val docSuggest by androidx.compose.runtime.produceState(
            initialValue = remember(locLabel) { viewModel.suggestName(purpose, documentWord) },
            pages,
        ) {
            pages.firstOrNull()?.let { viewModel.documentTitle(it) }?.takeIf { it.isNotBlank() }?.let { value = it }
        }
        DocumentSaveSheet(
            pageCount = pages.size,
            suggested = docSuggest,
            onDismiss = { pendingScan = null },
            onConfirm = { name, keepOriginals ->
                pendingScan = null
                viewModel.saveDocument(pdfUri, pages, keepOriginals, purpose, name) { id, uri ->
                    onSaved(id, uri, false)
                }
            },
        )
    }
    if (showBurstNaming) {
        val groupSuggested = remember(locLabel, purpose) {
            if (!locLabel.isNullOrBlank()) {
                resources.getString(R.string.camera_batch_with_location, locLabel, purpose)
            } else {
                resources.getString(R.string.camera_batch_default, purpose, DateUtils.shortStamp())
            }
        }
        BurstNamingSheet(
            files = burstFiles,
            purpose = purpose,
            groupSuggested = groupSuggested,
            onDismiss = { showBurstNaming = false },
            onConfirm = { group, names ->
                showBurstNaming = false
                viewModel.savePhotoBatch(burstFiles.toList(), purpose, group, names) { id, uri ->
                    burstFiles.clear()
                    onSaved(id, uri, false)
                }
            },
        )
    }
}

@Composable
private fun CaptureNameSheet(
    previewFile: File?,
    isVideo: Boolean,
    suggested: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var edited by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(suggested) }
    // 위치 등으로 제안 이름이 늦게 들어오면, 사용자가 직접 고치지 않은 경우에만 자동 갱신.
    LaunchedEffect(suggested) { if (!edited) name = suggested }
    // 찍은 사진이 미리보기 칸으로 날아 들어가는 효과.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (shown) 1f else 0.4f,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 380f),
        label = "captureScale",
    )
    TrubbiPopupCard(onDismiss = onDismiss) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(150.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                if (previewFile != null) {
                    AsyncImage(
                        model = previewFile,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(AppIcons.Videocam, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                }
            }
        }
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                edited = true
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            label = { Text(stringResource(if (isVideo) R.string.camera_video_name else R.string.camera_photo_name)) },
        )
        PillButton(stringResource(R.string.action_save), onClick = { onConfirm(name) }, modifier = Modifier.fillMaxWidth(), icon = AppIcons.Save)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BurstNamingSheet(
    files: List<File>,
    purpose: String,
    groupSuggested: String,
    onDismiss: () -> Unit,
    onConfirm: (group: String, names: List<String>) -> Unit,
) {
    var group by remember { mutableStateOf(groupSuggested) }
    val names = remember(files.size) {
        mutableStateListOf<String>().apply { repeat(files.size) { add("$purpose ${it + 1}") } }
    }
    val shape = RoundedCornerShape(16.dp)
    TrubbiPopupCard(onDismiss = onDismiss) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.camera_save_batch_count, files.size),
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
            value = group,
            onValueChange = { group = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = shape,
            label = { Text(stringResource(R.string.camera_batch_name)) },
        )
        Text(stringResource(R.string.camera_photo_names), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(files.indices.toList()) { i ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AsyncImage(
                        model = files[i],
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                    )
                    OutlinedTextField(
                        value = names.getOrElse(i) { "" },
                        onValueChange = { if (i < names.size) names[i] = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = shape,
                    )
                }
            }
        }
        PillButton(stringResource(R.string.action_save), onClick = {
            onConfirm(group, names.toList())
        }, modifier = Modifier.fillMaxWidth(), icon = AppIcons.Save)
    }
}

@Composable
private fun DocumentSaveSheet(
    pageCount: Int,
    suggested: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, keepOriginals: Boolean) -> Unit,
) {
    var edited by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(suggested) }
    // OCR 제목 인식이 끝나면, 사용자가 직접 고치지 않은 경우에만 자동 갱신.
    LaunchedEffect(suggested) { if (!edited) name = suggested }
    // 기본 ON: 페이지 이미지도 함께 저장하면 사진 묶음과 동일하게 스와이프로 열람된다.
    var keepOriginals by remember { mutableStateOf(true) }
    TrubbiPopupCard(onDismiss = onDismiss) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(AppIcons.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Text(
                stringResource(R.string.camera_save_document),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.camera_page_count, pageCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            stringResource(R.string.camera_doc_save_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                edited = true
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            label = { Text(stringResource(R.string.camera_document_name)) },
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { keepOriginals = !keepOriginals }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.camera_keep_page_images), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    stringResource(R.string.camera_keep_page_images_sub),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            androidx.compose.material3.Switch(checked = keepOriginals, onCheckedChange = { keepOriginals = it })
        }
        PillButton(stringResource(R.string.action_save), onClick = { onConfirm(name, keepOriginals) }, modifier = Modifier.fillMaxWidth(), icon = AppIcons.Save)
    }
}

@Composable
private fun PurposeSelector(purposes: List<String>, selected: String, onSelect: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(purposes) { p ->
            val isSel = p == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.4f))
                    .clickable { onSelect(p) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(p, style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
        }
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, recording: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(78.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(Color.White.copy(alpha = if (enabled) 0.25f else 0.1f))
            .border(3.dp, Color.White, RoundedCornerShape(percent = 50))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(if (recording) 32.dp else 60.dp)
                .clip(if (recording) RoundedCornerShape(8.dp) else RoundedCornerShape(percent = 50))
                .background(if (recording) Color(0xFFFF3B30) else Color.White),
        )
    }
}

private fun formatElapsed(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}

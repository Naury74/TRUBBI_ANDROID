package com.naury.trubbi.feature.detail

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import com.naury.trubbi.R
import com.naury.trubbi.core.barcode.BarcodeRenderer
import com.naury.trubbi.core.i18n.purposeLabel
import com.naury.trubbi.core.util.DateUtils
import com.naury.trubbi.data.local.MediaEntity
import com.naury.trubbi.data.local.RecordWithMedia
import com.naury.trubbi.domain.model.RecordType
import com.naury.trubbi.ui.common.BarcodeFullScreenDialog
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.TrubbiConfirmDialog
import com.naury.trubbi.ui.designsystem.TrubbiInputDialog
import com.naury.trubbi.ui.designsystem.TrubbiToastHost
import com.naury.trubbi.ui.designsystem.pressable
import com.naury.trubbi.ui.designsystem.rememberTrubbiToastState
import com.naury.trubbi.ui.designsystem.sharedMedia
import com.naury.trubbi.ui.theme.LocalTrubbiColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch
/**
 * 전체화면 상세 화면.
 *
 * 기록 종류별로 화면을 나누지 않고 하나의 페이저에 모두 펼친다. 사진·문서·메모가 섞여 있어도
 * 좌우 스와이프로 연속 탐색된다. 한 번 탭하면 조작 UI 가 숨겨진다.
 *
 * @param item 처음 열 기록. 페이저는 이 항목 위치에서 시작한다
 * @param pages 전체 목록을 펼친 페이지들. 비어 있으면 [item] 것만 보여 준다
 * @param onBack 뒤로 갈 때
 * @param onEditImage 이미지 편집으로 갈 때 (기록 id, 미디어 uri)
 * @param onEditVideo 영상 편집으로 갈 때 (기록 id, 미디어 uri)
 * @param onEditMemo 메모 편집으로 갈 때 (기록 id)
 * @param onShareBarcode 바코드 공유를 누를 때
 * @param viewModel 상세 화면 상태
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImmersiveDetailScreen(
    item: RecordWithMedia,
    pages: List<DetailPage>,
    onBack: () -> Unit,
    onEditImage: (recordId: String, mediaUri: String) -> Unit,
    onEditVideo: (recordId: String, mediaUri: String) -> Unit,
    onEditMemo: (recordId: String) -> Unit,
    onShareBarcode: (item: RecordWithMedia) -> Unit,
    viewModel: DetailViewModel,
) {
    val context = LocalContext.current
    // 폴더블 펼침·태블릿에서는 2단으로 나눈다. 왼쪽에 사진과 필름스트립, 오른쪽에 정보와 버튼.
    // 넓은 화면에서 세로 배치를 그대로 늘리면 사진이 가운데만 차지하고 양옆이 텅 빈다.
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val wide = configuration.screenWidthDp >= 720
    val rightPanelWidth = if (wide) 392.dp else 0.dp
    // 기록 경계를 없앤 평면 목록. 묶음의 마지막 페이지에서 넘기면 다음 기록으로 이어진다.
    val list: List<DetailPage> = if (pages.isNotEmpty()) {
        pages
    } else {
        remember(item) { item.toDetailPages() }
    }
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.detail_no_records), color = Color.White)
        }
        return
    }
    val initial = remember(list) {
        list.indexOfFirst { it.record.record.recordId == item.record.recordId }.coerceAtLeast(0)
    }
    // 목록 크기가 바뀌면(간이 목록 → 전체 목록) 페이저를 다시 만들되 열었던 항목 위치를 지킨다.
    val pagerState = key(list.size) { rememberPagerState(initialPage = initial) { list.size } }
    val scope = rememberCoroutineScope()
    val toast = rememberTrubbiToastState()

    var chromeVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(200)
        chromeVisible = true
    }
    val sheets = remember { DetailSheets() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val currentPage = pagerState.currentPage.coerceIn(0, list.lastIndex)
    val curPage = list[currentPage]
    val curRW = curPage.record
    val curRec = curRW.record
    val curMedia = (curPage as? MediaPage)?.media
    val curBarcode = curRec.barcodeValue?.takeIf { it.isNotBlank() }
    val isCardPage = curPage is CardPage
    val isDoc = RecordType.fromName(curRec.type) == RecordType.DOCUMENT
    val docPdfUri = curRW.pdfMedia?.uri
    // 현재 페이지가 OCR PDF 미리보기인지(아니면 원본 사진 — 이미지 타입과 동일하게 다룬다).
    val curIsPdf = curMedia?.isPdf == true

    // 지금 기록에 속한 파일들의 전체 인덱스. 필름스트립과 ‘3/8’ 표시에 쓴다.
    val recordEntries = remember(list, curRec.recordId) {
        list.withIndex()
            .mapNotNull { iv -> (iv.value as? MediaPage)?.takeIf { it.record.record.recordId == curRec.recordId }?.let { iv.index to it.media } }
    }
    val posInRecord = recordEntries.indexOfFirst { it.first == currentPage }.coerceAtLeast(0)

    val shareChooserTitle = stringResource(R.string.action_share)
    val msgCopied = stringResource(R.string.toast_copied)
    val msgCannotPlay = stringResource(R.string.detail_cannot_play)
    val msgRenamed = stringResource(R.string.detail_renamed)
    val msgSaved = stringResource(R.string.toast_saved)

    // 공유: 문서 → PDF/이미지 선택, 일반 이미지 → 워터마크 옵션 다이얼로그, 메모/카드 → 텍스트 공유.
    val shareAction: () -> Unit = {
        when {
            isDoc && docPdfUri != null -> sheets.showDocShare = true
            curPage is MediaPage -> sheets.showShare = true
            recordEntries.size > 1 -> sheets.showShare = true
            else -> context.startActivity(Intent.createChooser(viewModel.shareIntent(curRW), shareChooserTitle))
        }
    }

    val hazeState = remember { HazeState() }
    // 버튼이 보이면 카드가 위아래 버튼 사이에 들어가고, 숨기면 화면 전체로 커진다.
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topReserve by animateDpAsState(if (chromeVisible) statusTop + 74.dp else 0.dp, label = "topReserve")
    val bottomReserve by animateDpAsState(
        when {
            !chromeVisible -> 0.dp
            wide -> if (recordEntries.size > 1) 116.dp else 24.dp
            recordEntries.size > 1 -> 198.dp
            isCardPage -> 168.dp
            else -> 106.dp
        },
        label = "bottomReserve",
    )
    val sideReserve by animateDpAsState(if (chromeVisible) 16.dp else 0.dp, label = "sideReserve")
    val cardRadius by animateDpAsState(if (chromeVisible) 24.dp else 0.dp, label = "cardRadius")
    val peekPad by animateDpAsState(if (chromeVisible && recordEntries.size > 1) 28.dp else 0.dp, label = "peek")

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (curMedia != null) {
            AsyncImage(
                model = rememberMediaModel(curMedia),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(30.dp).hazeSource(hazeState),
            )
        } else {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF202022), Color(0xFF000000)))).hazeSource(hazeState))
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = peekPad),
            pageSpacing = 12.dp,
        ) { page ->
            val mp = list[page]
            val isCurrent = page == pagerState.currentPage
            BoxWithConstraints(
                Modifier.fillMaxSize().padding(start = sideReserve, end = sideReserve + rightPanelWidth, top = topReserve, bottom = bottomReserve),
                contentAlignment = Alignment.Center,
            ) {
                val scaleMod = Modifier.graphicsLayer {
                    // 넘기는 동안 매 프레임 바뀌는 값이라 그리기 단계에서만 읽는다(재구성 없이 크기만 바뀐다).
                    val off = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                    val a = off.absoluteValue.coerceIn(0f, 1f)
                    val s = lerp(0.92f, 1f, 1f - a)
                    scaleX = s
                    scaleY = s
                    alpha = lerp(0.6f, 1f, 1f - a)
                }
                when (mp) {
                    is MediaPage -> {
                        val media = mp.media
                        // 카드를 미디어 비율에 맞춘다. 비율을 모르는 영상은 16:9 로 두고, 극단적인
                        // 값은 잘라 낸다 — 그러지 않으면 파노라마 사진에서 카드가 실선처럼 납작해진다.
                        val w = media.width
                        val h = media.height
                        val rawAspect = if (w != null && w > 0 && h != null && h > 0) {
                            w.toFloat() / h.toFloat()
                        } else if (media.isVideo) {
                            16f / 9f
                        } else {
                            1f
                        }
                        val imgAspect = rememberDisplayAspect(media.uri, media.isVideo, rawAspect.coerceIn(0.42f, 2.4f))
                        val containerAspect = if (maxHeight.value > 0f) maxWidth / maxHeight else 1f
                        val sizeMod = if (imgAspect >= containerAspect) {
                            Modifier.fillMaxWidth().aspectRatio(imgAspect)
                        } else {
                            Modifier.fillMaxHeight().aspectRatio(imgAspect)
                        }
                        Box(
                            sizeMod.then(scaleMod)
                                .clip(RoundedCornerShape(cardRadius))
                                .then(if (isCurrent) Modifier.sharedMedia(mp.record.record.recordId) else Modifier),
                        ) {
                            if (media.isVideo) {
                                VideoPage(media, hazeState, onToggleChrome = { chromeVisible = !chromeVisible }) {
                                    val intent = viewModel.videoPlayIntent(media.uri)
                                    if (intent != null) context.startActivity(intent) else toast.show(msgCannotPlay)
                                }
                            } else {
                                // `.pdf` 는 직접 디코드할 수 없어 만들어 둔 썸네일을 대신 띄운다.
                                val pageUri = if (media.isPdf) (media.thumbnailUri ?: media.uri) else media.uri
                                ZoomableImage(uri = pageUri, onToggleChrome = { chromeVisible = !chromeVisible })
                            }

                            // 정보는 카드 안쪽 아래에 넣는다. 2단 배치에서는 오른쪽 패널로 옮겨 간다.
                            if (isCurrent && !wide) {
                                AnimatedVisibility(
                                    visible = chromeVisible,
                                    enter = fadeIn(),
                                    exit = fadeOut(),
                                    modifier = Modifier.align(Alignment.BottomStart),
                                ) {
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.30f), Color.Black.copy(alpha = 0.72f)),
                                                ),
                                            )
                                            .padding(start = 18.dp, end = 18.dp, top = 28.dp, bottom = 14.dp),
                                        verticalArrangement = Arrangement.spacedBy(5.dp),
                                    ) {
                                        ImmersiveTag(hazeState, purposeLabel(curRec.purpose))
                                        Text(
                                            media.name?.takeIf { it.isNotBlank() } ?: curRec.title,
                                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.pressable { sheets.showRename = true },
                                        )
                                        Text(
                                            DateUtils.formatDateTime(curRec.createdAt),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = Color.White.copy(alpha = 0.85f),
                                        )
                                        Spacer(Modifier.size(4.dp))
                                        val memoText = curRec.memo?.takeIf { it.isNotBlank() }
                                        if (memoText != null) {
                                            Text(
                                                memoText,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color.White.copy(alpha = 0.82f),
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.pressable { sheets.showInfo = true },
                                            )
                                        } else {
                                            // 메모가 없으면 쓰기 시작할 자리를 대신 보여 준다.
                                            Row(
                                                Modifier.pressable { sheets.showInfo = true },
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Icon(
                                                    AppIcons.Edit,
                                                    contentDescription = null,
                                                    tint = Color.White.copy(alpha = 0.8f),
                                                    modifier = Modifier.size(15.dp),
                                                )
                                                Text(
                                                    stringResource(R.string.detail_write_memo),
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = Color.White.copy(alpha = 0.8f),
                                                )
                                            }
                                        }
                                        val loc = curRec.locationName
                                        if (!loc.isNullOrBlank()) {
                                            Spacer(Modifier.size(4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(
                                                    AppIcons.LocationOn,
                                                    contentDescription = null,
                                                    tint = Color.White.copy(alpha = 0.8f),
                                                    modifier = Modifier.size(15.dp),
                                                )
                                                Text(
                                                    loc,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = Color.White.copy(alpha = 0.82f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    is CardPage -> {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = maxHeight)
                                .then(scaleMod)
                                .clip(RoundedCornerShape(cardRadius))
                                .then(if (isCurrent) Modifier.sharedMedia(mp.record.record.recordId) else Modifier)
                                .background(Color.White),
                        ) {
                            CardPageContent(mp.record) { chromeVisible = !chromeVisible }
                        }
                    }
                }
            }
        }

        // 왼쪽 위 — 뒤로 버튼.
        AnimatedVisibility(
            visible = chromeVisible,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Box(Modifier.statusBarsPadding().padding(12.dp)) {
                ImmersiveCircle(hazeState, AppIcons.ArrowBackIos, Color.White, 44.dp) { onBack() }
            }
        }

        // 오른쪽 위 — 삭제·즐겨찾기. 2단 배치에서는 오른쪽 패널로 간다.
        AnimatedVisibility(
            visible = chromeVisible && !wide,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Row(
                Modifier.statusBarsPadding().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ImmersiveCircle(hazeState, AppIcons.Delete, Color(0xFFFF6B6B), 44.dp) {
                    sheets.showDeleteConfirm = true
                }
                ImmersiveCircle(
                    hazeState,
                    if (curRec.isFavorite) AppIcons.Star else AppIcons.StarBorder,
                    if (curRec.isFavorite) LocalTrubbiColors.current.orange else Color.White,
                    44.dp,
                ) { viewModel.toggleFavorite(curRW) }
            }
        }

        // 2단 배치의 오른쪽 패널 — 정보와 동작 버튼.
        AnimatedVisibility(
            visible = chromeVisible && wide,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            DetailSidePanel(
                hazeState = hazeState,
                record = curRW,
                media = curMedia,
                barcode = curBarcode,
                isCardPage = isCardPage,
                isPdfPage = curIsPdf && docPdfUri != null,
                onOpenPdf = { sheets.showPdf = true },
                panelWidth = rightPanelWidth,
                onRename = { sheets.showRename = true },
                onInfo = { sheets.showInfo = true },
                onToggleFavorite = { viewModel.toggleFavorite(curRW) },
                onDelete = { sheets.showDeleteConfirm = true },
                onEditImage = { curMedia?.let { onEditImage(curRec.recordId, it.uri) } },
                onEditVideo = { curMedia?.let { onEditVideo(curRec.recordId, it.uri) } },
                onEditMemo = { onEditMemo(curRec.recordId) },
                onShare = { shareAction() },
                onShareBarcode = { onShareBarcode(curRW) },
                onBarcodeFull = { sheets.showBarcodeFull = true },
                onCopyBarcode = {
                    curBarcode?.let {
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("barcode", it))
                        toast.show(msgCopied)
                    }
                },
            )
        }

        // 카드 바깥 아래 — 편집·공유, 묶음이면 인덱스와 필름스트립.
        AnimatedVisibility(
            visible = chromeVisible,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 18.dp, end = 18.dp + rightPanelWidth, top = 8.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 2단 배치에서는 정보·버튼이 오른쪽으로 가고 아래에는 필름스트립만 남는다.
                if (!wide) {
                    if (isCardPage) {
                        // 카드가 밝아서 정보는 어두운 바탕 위에 올린다.
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            ImmersiveTag(hazeState, purposeLabel(curRec.purpose))
                            Text(
                                curRec.title,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                DateUtils.formatDateTime(curRec.createdAt),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White.copy(alpha = 0.85f),
                            )
                            val loc = curRec.locationName
                            if (!loc.isNullOrBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(AppIcons.LocationOn, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(15.dp))
                                    Text(
                                        loc,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White.copy(alpha = 0.82f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        if (curBarcode != null) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ImmersiveChip(
                                    hazeState,
                                    stringResource(R.string.detail_enlarge),
                                    AppIcons.Fullscreen,
                                    large = true,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    sheets.showBarcodeFull =
                                        true
                                }
                                ImmersiveChip(
                                    hazeState,
                                    stringResource(R.string.detail_copy_value),
                                    AppIcons.ContentCopy,
                                    large = true,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    cm.setPrimaryClip(android.content.ClipData.newPlainText("barcode", curBarcode))
                                    toast.show(msgCopied)
                                }
                                ImmersiveChip(hazeState, stringResource(R.string.action_share), AppIcons.Share, large = true, modifier = Modifier.weight(1f)) {
                                    onShareBarcode(curRW)
                                }
                            }
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ImmersiveChip(
                                    hazeState,
                                    stringResource(R.string.detail_edit_memo),
                                    AppIcons.Edit,
                                    large = true,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    onEditMemo(curRec.recordId)
                                }
                                ImmersiveChip(
                                    hazeState,
                                    stringResource(R.string.action_share_long),
                                    AppIcons.Share,
                                    large = true,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    shareAction()
                                }
                            }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (curIsPdf && docPdfUri != null) {
                                ImmersiveChip(
                                    hazeState,
                                    stringResource(R.string.detail_view_pdf),
                                    AppIcons.Description,
                                    large = true,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    sheets.showPdf = true
                                }
                            } else if (curMedia != null && !curMedia.isVideo) {
                                ImmersiveChip(
                                    hazeState,
                                    stringResource(R.string.detail_edit_image),
                                    AppIcons.Edit,
                                    large = true,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    onEditImage(curRec.recordId, curMedia.uri)
                                }
                            }
                            if (curMedia != null && curMedia.isVideo) {
                                ImmersiveChip(
                                    hazeState,
                                    stringResource(R.string.detail_edit_video),
                                    AppIcons.Edit,
                                    large = true,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    onEditVideo(curRec.recordId, curMedia.uri)
                                }
                            }
                            ImmersiveChip(hazeState, stringResource(R.string.action_share_long), AppIcons.Share, large = true, modifier = Modifier.weight(1f)) {
                                shareAction()
                            }
                        }
                    }
                }
                if (recordEntries.size > 1) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(
                                if (curMedia?.isVideo == true) AppIcons.Videocam else AppIcons.PhotoLibrary,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(15.dp),
                            )
                            Text("${posInRecord + 1} / ${recordEntries.size}", style = MaterialTheme.typography.labelMedium, color = Color.White)
                        }
                        Filmstrip(
                            entries = recordEntries,
                            currentGlobal = currentPage,
                            onSelect = { gi -> scope.launch { pagerState.animateScrollToPage(gi) } },
                        )
                    }
                }
            }
        }

        TrubbiToastHost(toast, modifier = Modifier.align(Alignment.BottomCenter))

        // 문서: 이미지 페이지 갤러리 위에 실제 PDF를 전체화면으로 덮어 보여준다(동일 화면 내 분기).
        if (sheets.showPdf && docPdfUri != null) {
            com.naury.trubbi.feature.pdf.PdfViewerScreen(
                pdfUri = docPdfUri,
                title = curRec.title,
                onBack = { sheets.showPdf = false },
                onShare = { context.startActivity(Intent.createChooser(viewModel.shareIntent(curRW), shareChooserTitle)) },
            )
        }
    }

    DetailDialogs(
        sheets = sheets,
        viewModel = viewModel,
        current = curRW,
        currentMedia = curMedia,
        mediaCount = recordEntries.size,
        toast = toast,
        onBack = onBack,
        onShareBarcode = onShareBarcode,
    )
}

/**
 * 상세 화면 위에 뜨는 시트·팝업들.
 *
 * 이름 변경 · 삭제 확인 · 이미지 공유 · 문서 공유 · 정보 시트 · 바코드 전체화면 여섯 가지다.
 * 표시 여부는 모두 [sheets] 가 들고 있다.
 *
 * @param sheets 어떤 시트를 띄울지 담은 상태
 * @param current 지금 보고 있는 기록
 * @param currentMedia 지금 페이지의 미디어. 카드형 기록이면 null
 * @param mediaCount 같은 기록 안의 미디어 개수. 1보다 크면 전체 공유 선택지를 준다
 * @param toast 결과를 알릴 토스트
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailDialogs(
    sheets: DetailSheets,
    viewModel: DetailViewModel,
    current: RecordWithMedia,
    currentMedia: MediaEntity?,
    mediaCount: Int,
    toast: com.naury.trubbi.ui.designsystem.TrubbiToastState,
    onBack: () -> Unit,
    onShareBarcode: (RecordWithMedia) -> Unit,
) {
    val context = LocalContext.current
    val curRW = current
    val curRec = current.record
    val curMedia = currentMedia
    val curBarcode = curRec.barcodeValue?.takeIf { it.isNotBlank() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val shareChooserTitle = stringResource(R.string.action_share)
    val msgRenamed = stringResource(R.string.detail_renamed)
    val msgSaved = stringResource(R.string.toast_saved)
    if (sheets.showRename) {
        TrubbiInputDialog(
            title = stringResource(R.string.detail_rename),
            initial = curMedia?.name?.takeIf { it.isNotBlank() } ?: curRec.title,
            label = stringResource(R.string.field_name),
            icon = AppIcons.Edit,
            onDismiss = { sheets.showRename = false },
            onConfirm = { newName ->
                viewModel.rename(curRec.recordId, newName, curRec.purpose, curRec.jobDate)
                sheets.showRename = false
                toast.show(msgRenamed)
            },
        )
    }

    if (sheets.showDeleteConfirm) {
        TrubbiConfirmDialog(
            title = stringResource(R.string.detail_delete_title),
            message = stringResource(R.string.detail_delete_message),
            confirmText = stringResource(R.string.action_delete),
            destructive = true,
            icon = AppIcons.Delete,
            onDismiss = { sheets.showDeleteConfirm = false },
            onConfirm = {
                sheets.showDeleteConfirm = false
                viewModel.delete(curRec.recordId) { onBack() }
            },
        )
    }

    if (sheets.showShare) {
        val composerItems = remember(curRW.record.recordId) { viewModel.shareComposerItems(curRW) }
        if (composerItems.isNotEmpty()) {
            val wmDefaults by viewModel.watermarkDefaults.collectAsState()
            val sharePurposes by viewModel.purposes.collectAsState()
            // 공유 갤러리 — 가로 스와이프 + 항목별 워터마크 지정 후 공유.
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { sheets.showShare = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            ) {
                com.naury.trubbi.feature.share.ShareComposerScreen(
                    items = composerItems,
                    defaults = wmDefaults,
                    purposes = sharePurposes,
                    onClose = { sheets.showShare = false },
                    onShare = { selections ->
                        viewModel.shareWithWatermark(selections) { intent ->
                            context.startActivity(Intent.createChooser(intent, shareChooserTitle))
                        }
                        sheets.showShare = false
                    },
                )
            }
        } else {
            ShareOptionsDialog(
                isBatch = mediaCount > 1,
                hasLocation = !curRec.locationName.isNullOrBlank(),
                canWatermark = !curRW.hasVideo,
                defaultText = { includeLoc -> viewModel.defaultShareText(curRW, includeLoc) },
                onDismiss = { sheets.showShare = false },
                onShare = { allMedia, text, watermark, includeLoc ->
                    val uris = if (allMedia) {
                        curRW.media.filter { !it.isPdf }.sortedBy { it.createdAt }.map { it.uri }
                    } else {
                        listOfNotNull(curMedia?.uri)
                    }
                    context.startActivity(
                        Intent.createChooser(viewModel.shareImagesIntent(curRW, uris, text, watermark, includeLoc), shareChooserTitle),
                    )
                    sheets.showShare = false
                },
            )
        }
    }

    if (sheets.showDocShare) {
        DocumentShareDialog(
            hasLocation = !curRec.locationName.isNullOrBlank(),
            onDismiss = { sheets.showDocShare = false },
            onSharePdf = {
                viewModel.sharePdfIntent(curRW)?.let { context.startActivity(Intent.createChooser(it, shareChooserTitle)) }
                sheets.showDocShare = false
            },
            onShareImages = { _, _ ->
                // 문서의 이미지 페이지도 동일한 공유 갤러리(워터마크 화면)로 진행.
                sheets.showDocShare = false
                sheets.showShare = true
            },
        )
    }

    if (sheets.showInfo) {
        ModalBottomSheet(
            onDismissRequest = { sheets.showInfo = false },
            sheetState = sheetState,
            containerColor = Color.Transparent,
        ) {
            InfoSheet(curRW, viewModel, onSaved = {
                sheets.showInfo = false
                toast.show(msgSaved)
            })
        }
    }

    if (sheets.showBarcodeFull && curBarcode != null) {
        BarcodeFullScreenDialog(
            bitmap = remember(curBarcode, curRec.barcodeFormat) {
                val twoD = BarcodeRenderer.isTwoDimensional(curRec.barcodeFormat)
                BarcodeRenderer.render(curBarcode, curRec.barcodeFormat, if (twoD) 800 else 1000, if (twoD) 800 else 400)
            },
            value = curBarcode,
            format = curRec.barcodeFormat,
            isTwoDimensional = BarcodeRenderer.isTwoDimensional(curRec.barcodeFormat),
            onShare = {
                sheets.showBarcodeFull = false
                onShareBarcode(curRW)
            },
            onDismiss = { sheets.showBarcodeFull = false },
        )
    }
}

/**
 * 상세 화면에서 열리는 시트·팝업의 표시 상태.
 *
 * 플래그가 일곱 개라 화면 함수에 늘어놓으면 어떤 값이 무엇을 띄우는지 흩어진다.
 */
@Stable
private class DetailSheets {
    /** 정보 시트(메모·태그 편집). */
    var showInfo by mutableStateOf(false)

    /** 이름 변경 팝업. */
    var showRename by mutableStateOf(false)

    /** 삭제 확인 팝업. */
    var showDeleteConfirm by mutableStateOf(false)

    /** 이미지 공유 — 워터마크 지정 화면 또는 옵션 팝업. */
    var showShare by mutableStateOf(false)

    /** 문서 공유 — PDF 로 보낼지 이미지로 보낼지. */
    var showDocShare by mutableStateOf(false)

    /** 바코드 전체화면. */
    var showBarcodeFull by mutableStateOf(false)

    /** PDF 뷰어 오버레이. */
    var showPdf by mutableStateOf(false)
}

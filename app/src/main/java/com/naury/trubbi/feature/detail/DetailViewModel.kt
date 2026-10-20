package com.naury.trubbi.feature.detail

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naury.trubbi.core.barcode.BarcodeRenderer
import com.naury.trubbi.core.file.MediaStorage
import com.naury.trubbi.core.i18n.label
import com.naury.trubbi.core.i18n.purposeLabel
import com.naury.trubbi.core.location.LocationProvider
import com.naury.trubbi.core.ui.StringProvider
import com.naury.trubbi.data.RecordOrderHolder
import com.naury.trubbi.data.local.MediaEntity
import com.naury.trubbi.data.local.RecordWithMedia
import com.naury.trubbi.data.repo.RecordRepository
import com.naury.trubbi.data.settings.SettingsRepository
import com.naury.trubbi.data.settings.WatermarkDefaults
import com.naury.trubbi.domain.model.RecordType
import com.naury.trubbi.feature.share.ShareComposerItem
import com.naury.trubbi.feature.share.ShareSelection
import com.naury.trubbi.feature.share.ShareUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 이머시브 뷰어에서 좌우로 넘기는 한 장.
 */
sealed interface DetailPage {
    val record: RecordWithMedia
}

/** 사진·영상 한 장. 묶음이면 미디어마다 한 장씩 나온다. */
data class MediaPage(override val record: RecordWithMedia, val media: MediaEntity) : DetailPage

/** 이미지가 없는 기록(바코드·메모)을 카드로 보여 주는 장. */
data class CardPage(override val record: RecordWithMedia) : DetailPage

/**
 * 기록 하나를 넘길 수 있는 장들로 펼친다.
 *
 * 사진·영상은 미디어마다 한 장, 문서는 OCR 된 PDF 를 앞에 두고 원본 사진을 뒤에 붙인다.
 * 그 밖의 종류는 카드 한 장이다. 목록에서 열든 단건으로 열든 같은 함수를 써서
 * 화면 넘김이 어디서나 똑같이 동작한다.
 */
fun RecordWithMedia.toDetailPages(): List<DetailPage> {
    val type = RecordType.fromName(record.type)
    return when {
        (type == RecordType.PHOTO || type == RecordType.VIDEO) && media.isNotEmpty() ->
            media.sortedBy { it.createdAt }.map { MediaPage(this, it) }

        type == RecordType.DOCUMENT -> {
            val pdf = media.firstOrNull { it.isPdf }
            val originals = media.filter { !it.isPdf }.sortedBy { it.createdAt }
            val ordered = listOfNotNull(pdf) + originals
            if (ordered.isNotEmpty()) ordered.map { MediaPage(this, it) } else listOf(CardPage(this))
        }

        else -> listOf(CardPage(this))
    }
}

/**
 * 기록 상세 화면 상태.
 *
 * 열려 있는 기록 하나뿐 아니라 앨범이 보여 주던 순서 전체를 함께 들고 있어서, 상세에서 좌우로
 * 넘기면 목록에서 보던 순서 그대로 이동한다.
 *
 * @property repo 기록 조회·수정
 * @property mediaStorage 공유용 파일·워터마크 굽기
 * @property strings 공유 본문에 들어가는 문구
 */
@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repo: RecordRepository,
    private val mediaStorage: MediaStorage,
    private val strings: StringProvider,
    private val location: LocationProvider,
    orderHolder: RecordOrderHolder,
    settings: SettingsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** 설정에서 사용자가 관리하는 용도 목록 — 상세에서 용도 칩을 바꿀 때 쓴다. */
    val purposes = settings.purposes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 저장된 기록의 용도를 바꾼다. 제목과 업무일은 건드리지 않는다. */
    fun setPurpose(item: RecordWithMedia, purposeLabel: String) = viewModelScope.launch {
        repo.updateMeta(item.record.recordId, item.record.title, purposeLabel, item.record.jobDate)
    }

    /** 공유 갤러리가 처음 켤 워터마크 항목. */
    val watermarkDefaults = settings.watermarkDefaults
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WatermarkDefaults())

    /** 기록의 이미지·영상을 공유 갤러리 항목으로 바꾼다. PDF 는 워터마크를 새길 수 없어 뺀다. */
    fun shareComposerItems(item: RecordWithMedia): List<ShareComposerItem> {
        val r = item.record
        return item.media.filter { !it.isPdf }.sortedBy { it.createdAt }.map { media ->
            ShareComposerItem(
                id = media.mediaId,
                imageUri = media.uri,
                date = r.createdAt,
                purposeLabel = purposeLabel(strings, r.purpose),
                locationName = r.locationName,
                latitude = r.latitude,
                longitude = r.longitude,
                savedName = r.title,
                typeLabel = if (media.isVideo) {
                    RecordType.VIDEO.label(strings)
                } else {
                    RecordType.fromName(r.type).label(strings)
                },
                width = media.width ?: 0,
                height = media.height ?: 0,
                editable = !media.isVideo,
                isVideo = media.isVideo,
                thumbUri = media.thumbnailUri,
            )
        }
    }

    /** 공유 갤러리에서 정한 대로 워터마크를 구운 뒤 외부 공유 인텐트를 만든다. */
    fun shareWithWatermark(selections: List<ShareSelection>, onReady: (Intent) -> Unit) {
        viewModelScope.launch {
            val uris = withContext(Dispatchers.IO) {
                selections.mapNotNullTo(ArrayList()) { selection ->
                    runCatching {
                        if (selection.lines.isNotEmpty()) {
                            mediaStorage.watermarkForShare(selection.imageUri, selection.lines, selection.style)
                        } else {
                            mediaStorage.shareUri(selection.imageUri)
                        }
                    }.getOrNull()
                }
            }
            if (uris.isEmpty()) return@launch
            // 영상이 섞이면 image/jpeg 로는 받는 앱이 걸러 낸다.
            val mime = if (selections.any { it.isVideo }) "*/*" else "image/jpeg"
            onReady(sendIntent(uris, mime))
        }
    }

    private fun sendIntent(uris: ArrayList<Uri>, mime: String, text: String? = null): Intent =
        if (uris.size > 1) {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mime
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                text?.let { putExtra(Intent.EXTRA_TEXT, it) }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uris[0])
                text?.let { putExtra(Intent.EXTRA_TEXT, it) }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

    private val recordId = MutableStateFlow(savedStateHandle.get<String>("recordId"))

    fun setRecordId(id: String?) {
        recordId.value = id
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val record = recordId
        .flatMapLatest { id -> if (id == null) flowOf(null) else repo.observeRecord(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * 모든 기록을 넘길 수 있는 장들로 펼친 목록.
     *
     * 앨범이 보여 주던 순서를 그대로 따르되, 그 순서에 지금 연 기록이 없으면(홈·검색에서 바로 들어온 경우)
     * 최신순으로 되돌린다.
     */
    val detailPages = combine(
        repo.observeRecords(),
        orderHolder.orderedRecordIds,
        recordId,
    ) { records, order, openedId ->
        val byId = records.associateBy { it.record.recordId }
        val source = if (order.isNotEmpty() && (openedId == null || order.contains(openedId))) {
            order.mapNotNull { byId[it] }
        } else {
            records
        }
        source.flatMap { it.toDetailPages() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleFavorite(item: RecordWithMedia) = viewModelScope.launch {
        repo.toggleFavorite(item.record.recordId, !item.record.isFavorite)
    }

    /** 위치 고르기 화면의 장소 검색. */
    suspend fun searchPlaces(query: String, nearLat: Double?, nearLng: Double?) = location.searchPlaces(query, nearLat = nearLat, nearLng = nearLng)

    /** 고른 좌표의 주소. */
    suspend fun describePlace(lat: Double, lng: Double) = location.describe(lat, lng)

    /** 기록 위치를 고친다. */
    fun setLocation(id: String, lat: Double, lng: Double, address: String?) = viewModelScope.launch { repo.setLocation(id, lat, lng, address) }

    fun saveMemo(id: String, memo: String) = viewModelScope.launch { repo.updateMemo(id, memo.ifBlank { null }) }
    fun saveTags(id: String, tags: List<String>) = viewModelScope.launch { repo.updateTags(id, tags) }
    fun updateMeta(id: String, title: String, purposeLabel: String, jobDate: String) =
        viewModelScope.launch { repo.updateMeta(id, title, purposeLabel, jobDate) }

    fun delete(id: String, onDone: () -> Unit) = viewModelScope.launch {
        repo.softDelete(id)
        onDone()
    }

    /** 저장된 영상을 외부 플레이어로 재생하는 인텐트. */
    fun videoPlayIntent(uri: String): Intent? {
        val content = runCatching { mediaStorage.shareUri(uri) }.getOrNull() ?: return null
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(content, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** 저장된 바코드 값을 다시 이미지로 그려 그 이미지를 공유한다. 값만 보내면 상대가 스캔할 수 없다. */
    fun shareBarcodeIntent(item: RecordWithMedia): Intent? {
        val r = item.record
        val value = r.barcodeValue?.takeIf { it.isNotBlank() } ?: return null
        val twoD = BarcodeRenderer.isTwoDimensional(r.barcodeFormat)
        val bmp = BarcodeRenderer.render(
            value = value,
            formatName = r.barcodeFormat,
            widthPx = if (twoD) 800 else 1000,
            heightPx = if (twoD) 800 else 400,
        ) ?: return null
        val uri = runCatching { mediaStorage.cacheBitmapForShare(bmp, "barcode_${r.recordId}") }.getOrNull() ?: return null
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, ShareUtils.buildText(strings, item))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** 공유 옵션 시트가 처음 보여 줄 본문. */
    fun defaultShareText(item: RecordWithMedia, includeLocation: Boolean): String =
        ShareUtils.buildText(strings, item, includeLocation)

    /** 문서 기록의 PDF 파일을 그대로 공유한다. */
    fun sharePdfIntent(item: RecordWithMedia): Intent? {
        val pdf = item.media.firstOrNull { it.isPdf } ?: return null
        val uri = runCatching { mediaStorage.shareUri(pdf.uri) }.getOrNull() ?: return null
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, ShareUtils.buildText(strings, item))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * 이미지를 공유한다.
     *
     * [watermark] 가 true 면 시간·위치를 픽셀에 굽는다. 본문 텍스트를 전달하지 않는
     * 수신 앱에서도 정보가 유지된다.
     */
    fun shareImagesIntent(
        item: RecordWithMedia,
        mediaUris: List<String>,
        text: String,
        watermark: Boolean,
        includeLocation: Boolean = true,
    ): Intent {
        val lines = ShareUtils.watermarkLines(item, includeLocation)
        val uris = mediaUris.mapNotNullTo(ArrayList()) { uri ->
            runCatching {
                if (watermark) mediaStorage.watermarkForShare(uri, lines) else mediaStorage.shareUri(uri)
            }.getOrNull()
        }
        return if (uris.isEmpty()) textIntent(text) else sendIntent(uris, "image/jpeg", text)
    }

    /** 고른 미디어와 (사용자가 손본) 본문으로 공유 인텐트를 만든다. */
    fun customShareIntent(mediaUris: List<String>, text: String): Intent {
        val uris = mediaUris.mapNotNullTo(ArrayList()) { uri ->
            runCatching { mediaStorage.shareUri(uri) }.getOrNull()
        }
        return if (uris.isEmpty()) textIntent(text) else sendIntent(uris, "image/*", text)
    }

    private fun textIntent(text: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }

    fun rename(id: String, title: String, purpose: String, jobDate: String) =
        viewModelScope.launch {
            repo.updateMeta(id, title.ifBlank { strings[RecordType.PHOTO.labelRes] }, purpose, jobDate)
        }

    fun shareIntent(item: RecordWithMedia): Intent {
        val text = ShareUtils.buildText(strings, item)
        val imageUri: Uri? = item.media.firstOrNull()?.uri?.let {
            runCatching { mediaStorage.shareUri(it) }.getOrNull()
        }
        return if (imageUri != null) {
            sendIntent(arrayListOf(imageUri), "image/jpeg", text)
        } else {
            textIntent(text)
        }
    }
}

package com.naury.trubbi.feature.album

import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naury.trubbi.R
import com.naury.trubbi.core.file.MediaStorage
import com.naury.trubbi.core.i18n.label
import com.naury.trubbi.core.i18n.purposeLabel
import com.naury.trubbi.core.ui.StringProvider
import com.naury.trubbi.data.AlbumEntryRequest
import com.naury.trubbi.data.RecordOrderHolder
import com.naury.trubbi.data.local.RecordWithMedia
import com.naury.trubbi.data.repo.RecordRepository
import com.naury.trubbi.data.settings.SettingsRepository
import com.naury.trubbi.data.settings.WatermarkDefaults
import com.naury.trubbi.domain.model.Labeled
import com.naury.trubbi.domain.model.RecordType
import com.naury.trubbi.feature.share.ShareComposerItem
import com.naury.trubbi.feature.share.ShareSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 앨범 상단의 종류 필터.
 */
enum class AlbumFilter(@StringRes override val labelRes: Int) : Labeled {
    ALL(R.string.album_filter_all),
    PHOTO(RecordType.PHOTO.labelRes),
    VIDEO(RecordType.VIDEO.labelRes),
    DOCUMENT(RecordType.DOCUMENT.labelRes),
    BARCODE(RecordType.BARCODE.labelRes),
    MEMO(RecordType.MEMO.labelRes),
    FAVORITE(R.string.album_filter_favorite),
}

/**
 * 앨범 정렬 방식.
 */
enum class SortMode(@StringRes override val labelRes: Int) : Labeled {
    NEWEST(R.string.sort_newest),
    OLDEST(R.string.sort_oldest),
    PURPOSE(R.string.sort_purpose),
}

/**
 * 검색어가 일치한 위치. 결과 요약에서 항목별 건수를 표시하는 데 쓴다.
 */
enum class SearchField(@StringRes override val labelRes: Int) : Labeled {
    NAME(R.string.search_field_name),
    PURPOSE(R.string.search_field_purpose),
    MEMO(R.string.search_field_memo),
    TAG(R.string.search_field_tag),
    DATE(R.string.search_field_date),
    TYPE(R.string.search_field_type),
    BARCODE(R.string.search_field_barcode),
    CONTENT(R.string.search_field_content),
}

/**
 * 앨범 화면 상태 — 필터·정렬·기간·검색·다중 선택.
 *
 * 필터를 각각 별도의 [MutableStateFlow] 로 두고 [combine] 으로 합친다. 단일 상태 객체로 묶으면
 * 값 하나를 바꿀 때마다 전체가 재계산된다.
 *
 * @property repo 기록 조회·삭제·즐겨찾기
 * @property orderHolder 지금 보이는 순서를 상세 화면에 넘겨 좌우 스와이프를 맞춘다
 * @property entryRequest 홈에서 넘어올 때 미리 걸어 둘 필터
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val repo: RecordRepository,
    private val settingsRepo: SettingsRepository,
    private val mediaStorage: MediaStorage,
    private val orderHolder: RecordOrderHolder,
    private val entryRequest: AlbumEntryRequest,
    private val strings: StringProvider,
) : ViewModel() {

    private val isoFmt = DateTimeFormatter.ISO_LOCAL_DATE

    val filter = MutableStateFlow(AlbumFilter.ALL)
    val purposeFilter = MutableStateFlow<String?>(null)
    val sortMode = MutableStateFlow(SortMode.NEWEST)

    /** 시작·끝을 모두 포함하는 업무일 기간 필터. */
    val dateRange = MutableStateFlow<Pair<String, String>?>(null)
    val searchQuery = MutableStateFlow("")
    val selection = MutableStateFlow<Set<String>>(emptySet())

    /** 묶음을 펼쳐 낱장으로 고른 미디어. */
    val selectedMedia = MutableStateFlow<Set<String>>(emptySet())

    /** 지금 펼쳐 둔 묶음 기록 id. */
    val expandedBatch = MutableStateFlow<String?>(null)

    /** 0이면 화면 폭에 맞춰 자동, 1 이상이면 사용자가 고른 열 수. */
    val columns = settingsRepo.gridColumns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val month = MutableStateFlow(YearMonth.now())

    val monthCounts = month.flatMapLatest { ym ->
        repo.observeDateCounts(ym.atDay(1).format(isoFmt), ym.atEndOfMonth().format(isoFmt))
    }.map { list -> list.associate { it.jobDate to it.count } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val purposes = settingsRepo.purposes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val filtered = combine(
        repo.observeRecords(),
        filter,
        purposeFilter,
        dateRange,
    ) { records, typeFilter, purpose, range ->
        var list = when (typeFilter) {
            AlbumFilter.ALL -> records
            AlbumFilter.PHOTO -> records.filter { it.record.type == RecordType.PHOTO.name }
            AlbumFilter.VIDEO -> records.filter { it.record.type == RecordType.VIDEO.name }
            AlbumFilter.DOCUMENT -> records.filter { it.record.type == RecordType.DOCUMENT.name }
            AlbumFilter.BARCODE -> records.filter { it.record.type == RecordType.BARCODE.name }
            AlbumFilter.MEMO -> records.filter { it.record.type == RecordType.MEMO.name }
            AlbumFilter.FAVORITE -> records.filter { it.record.isFavorite }
        }
        if (purpose != null) list = list.filter { purposeLabel(strings, it.record.purpose) == purpose }
        if (range != null) {
            list = list.filter { it.record.jobDate >= range.first && it.record.jobDate <= range.second }
        }
        list
    }

    val records = combine(filtered, sortMode, searchQuery) { list, sort, query ->
        val searched = if (query.isBlank()) list else list.filter { matches(it, query.trim()) }
        when (sort) {
            SortMode.NEWEST -> searched.sortedByDescending { it.record.createdAt }
            SortMode.OLDEST -> searched.sortedBy { it.record.createdAt }
            SortMode.PURPOSE -> searched.sortedWith(
                compareBy<RecordWithMedia> { purposeLabel(strings, it.record.purpose) }
                    .thenByDescending { it.record.createdAt },
            )
        }
    }
        // 상세 화면이 같은 순서로 스와이프하도록 현재 표시 순서를 전달한다.
        .onEach { orderHolder.orderedRecordIds.value = it.map { record -> record.record.recordId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 기본값 말고 걸려 있는 필터가 하나라도 있는지 — 필터 버튼을 강조할지 판단한다. */
    val hasActiveFilter = combine(filter, purposeFilter, dateRange) { typeFilter, purpose, range ->
        typeFilter != AlbumFilter.ALL || purpose != null || range != null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 검색어가 어느 항목에서 몇 건 걸렸는지. 결과가 왜 나왔는지 보여 주기 위한 요약이다. */
    val searchBreakdown = combine(records, searchQuery) { recs, rawQuery ->
        val query = rawQuery.trim()
        if (query.isBlank()) {
            emptyList()
        } else {
            val counts = linkedMapOf<SearchField, Int>()
            recs.forEach { record ->
                matchFields(record, query).forEach { field -> counts[field] = (counts[field] ?: 0) + 1 }
            }
            SearchField.entries.mapNotNull { field -> counts[field]?.let { field to it } }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val requestedFilterName get() = entryRequest.requestedFilter
    val requestedPurposeName get() = entryRequest.requestedPurpose
    val requestOpenSearch get() = entryRequest.openSearch

    /** 홈에서 넘어오며 남긴 진입 요청을 적용하고 비운다. */
    fun applyRequestedFilter() {
        entryRequest.requestedFilter.value?.let { name ->
            runCatching { AlbumFilter.valueOf(name) }.getOrNull()?.let { filter.value = it }
            entryRequest.requestedFilter.value = null
        }
        // 용도 필터 요청은 종류 필터를 해제하고 해당 용도 전체를 표시한다.
        entryRequest.requestedPurpose.value?.let { purpose ->
            filter.value = AlbumFilter.ALL
            purposeFilter.value = purpose
            entryRequest.requestedPurpose.value = null
        }
    }

    fun consumeOpenSearch() {
        entryRequest.openSearch.value = false
    }

    private fun matches(record: RecordWithMedia, query: String): Boolean {
        val r = record.record
        val haystack = buildString {
            append(r.title).append(' ')
            append(purposeLabel(strings, r.purpose)).append(' ')
            append(r.memo.orEmpty()).append(' ')
            append(r.tags.joinToString(" ")).append(' ')
            append(r.jobDate).append(' ')
            append(RecordType.fromName(r.type).label(strings)).append(' ')
            append(r.barcodeValue.orEmpty()).append(' ')
            append(r.ocrText.orEmpty()).append(' ')
            append(r.aiLabels.orEmpty()).append(' ')
            append(record.media.mapNotNull { it.name }.joinToString(" "))
        }
        return haystack.contains(query, ignoreCase = true)
    }

    private fun matchFields(record: RecordWithMedia, query: String): List<SearchField> {
        val r = record.record
        fun has(value: String?) = !value.isNullOrBlank() && value.contains(query, ignoreCase = true)
        return buildList {
            if (has(r.title) || record.media.any { has(it.name) }) add(SearchField.NAME)
            if (has(purposeLabel(strings, r.purpose))) add(SearchField.PURPOSE)
            if (has(r.memo)) add(SearchField.MEMO)
            if (r.tags.any { has(it) }) add(SearchField.TAG)
            if (has(r.jobDate)) add(SearchField.DATE)
            if (has(RecordType.fromName(r.type).label(strings))) add(SearchField.TYPE)
            if (has(r.barcodeValue)) add(SearchField.BARCODE)
            if (has(r.ocrText) || has(r.aiLabels)) add(SearchField.CONTENT)
        }
    }

    fun setFilter(f: AlbumFilter) {
        filter.value = f
    }
    fun setPurposeFilter(p: String?) {
        purposeFilter.value = p
    }
    fun setSort(mode: SortMode) {
        sortMode.value = mode
    }
    fun setSearchQuery(q: String) {
        searchQuery.value = q
    }
    fun setColumns(n: Int) = viewModelScope.launch { settingsRepo.setGridColumns(n) }

    fun setDateRange(start: String, end: String) {
        dateRange.value = if (start <= end) start to end else end to start
    }

    fun clearDateRange() {
        dateRange.value = null
    }
    fun previousMonth() {
        month.value = month.value.minusMonths(1)
    }
    fun nextMonth() {
        month.value = month.value.plusMonths(1)
    }
    fun setMonth(m: YearMonth) {
        month.value = m
    }

    fun toggleSelect(id: String) {
        val current = selection.value
        selection.value = if (id in current) current - id else current + id
    }

    fun toggleMedia(mediaId: String) {
        val current = selectedMedia.value
        selectedMedia.value = if (mediaId in current) current - mediaId else current + mediaId
    }

    fun toggleExpand(recordId: String) {
        expandedBatch.value = if (expandedBatch.value == recordId) null else recordId
    }

    fun clearSelection() {
        selection.value = emptySet()
        selectedMedia.value = emptySet()
        expandedBatch.value = null
    }

    fun deleteSelected() {
        val recordIds = selection.value
        val mediaIds = selectedMedia.value
        clearSelection()
        viewModelScope.launch {
            recordIds.forEach { repo.softDelete(it) }
            mediaIds.forEach { repo.deleteMedia(it) }
        }
    }

    fun favoriteSelected() {
        val ids = selection.value
        viewModelScope.launch { ids.forEach { repo.toggleFavorite(it, true) } }
        clearSelection()
    }

    /** 공유 갤러리가 처음 켤 워터마크 항목. */
    val watermarkDefaults = settingsRepo.watermarkDefaults
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WatermarkDefaults())

    /** 선택된 기록·미디어로 공유 갤러리 항목을 만든다. PDF 는 워터마크를 새길 수 없어 뺀다. */
    fun shareComposerItems(): List<ShareComposerItem> = buildList {
        records.value.forEach { record ->
            val r = record.record
            val media = when {
                r.recordId in selection.value -> record.media
                else -> record.media.filter { it.mediaId in selectedMedia.value }
            }.filter { !it.isPdf }

            media.forEach { item ->
                add(
                    ShareComposerItem(
                        id = item.mediaId,
                        imageUri = item.uri,
                        date = r.createdAt,
                        purposeLabel = purposeLabel(strings, r.purpose),
                        locationName = r.locationName,
                        latitude = r.latitude,
                        longitude = r.longitude,
                        savedName = r.title,
                        typeLabel = if (item.isVideo) {
                            RecordType.VIDEO.label(strings)
                        } else {
                            RecordType.fromName(r.type).label(strings)
                        },
                        width = item.width ?: 0,
                        height = item.height ?: 0,
                        editable = !item.isVideo,
                        isVideo = item.isVideo,
                        thumbUri = item.thumbnailUri,
                    ),
                )
            }
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
            // 영상이 포함되면 `image/jpeg` 로는 수신 앱이 필터링한다.
            val mime = if (selections.any { it.isVideo }) "*/*" else "image/jpeg"
            onReady(sendIntent(uris, mime))
            clearSelection()
        }
    }

    /** 워터마크 없이 원본 그대로 보내는 공유 인텐트. */
    fun shareSelectedIntent(): Intent? {
        val uris = ArrayList<Uri>()
        records.value.forEach { record ->
            val media = when {
                record.record.recordId in selection.value -> record.media
                else -> record.media.filter { it.mediaId in selectedMedia.value }
            }
            media.forEach { item ->
                runCatching { mediaStorage.shareUri(item.uri) }.getOrNull()?.let { uris.add(it) }
            }
        }
        if (uris.isEmpty()) return null
        return sendIntent(uris, "image/*")
    }

    private fun sendIntent(uris: ArrayList<Uri>, mime: String): Intent =
        if (uris.size > 1) {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mime
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uris[0])
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
}

package com.naury.trubbi.feature.home

import android.content.Intent
import android.net.Uri
import com.naury.trubbi.R
import com.naury.trubbi.core.file.MediaStorage
import com.naury.trubbi.core.i18n.label
import com.naury.trubbi.core.i18n.purposeLabel
import com.naury.trubbi.core.ui.StringProvider
import com.naury.trubbi.core.util.DateUtils
import com.naury.trubbi.data.local.RecordWithMedia
import com.naury.trubbi.data.repo.RecordRepository
import com.naury.trubbi.data.settings.SettingsRepository
import com.naury.trubbi.data.settings.WatermarkDefaults
import com.naury.trubbi.domain.model.Purpose
import com.naury.trubbi.domain.model.RecordType
import com.naury.trubbi.domain.record.HomeState
import com.naury.trubbi.domain.record.HomeTimelineBuilder
import com.naury.trubbi.domain.record.RecordFilter
import com.naury.trubbi.feature.share.ShareComposerItem
import com.naury.trubbi.feature.share.ShareSelection
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.time.YearMonth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 홈 화면의 기록 목록 — 필터, 달력, 다중 선택, 공유, 가져오기.
 *
 * 목록을 좁히는 규칙 자체는 [HomeTimelineBuilder] 에 있고, 이 클래스는 화면이 바꾸는 상태를
 * 흐름으로 들고 있다가 그 규칙에 흘려보내는 역할만 한다.
 *
 * 수명은 생성자로 받은 [scope] 를 따른다.
 *
 * @property scope 목록 구독과 저장·공유 작업을 돌릴 스코프
 */
class HomeLibraryController @AssistedInject constructor(
    @Assisted private val scope: CoroutineScope,
    private val repo: RecordRepository,
    private val mediaStorage: MediaStorage,
    private val settingsRepo: SettingsRepository,
    private val timeline: HomeTimelineBuilder,
    private val strings: StringProvider,
) {
    /** 스코프를 인자로 받아야 하므로 Hilt 가 직접 만들지 못한다. 뷰모델이 이 팩토리로 만든다. */
    @AssistedFactory
    interface Factory {
        fun create(scope: CoroutineScope): HomeLibraryController
    }

    // ── 검색·필터 ─────────────────────────────────────────────────────────────

    /**
     * 타임라인에 걸린 조건 전체.
     *
     * 조건을 흐름 하나로 묶어 둔다. 네 개로 나눠 두면 화면이 네 번 구독하고, 조건 두 개를
     * 한 번에 바꿀 때 중간 상태로 한 번 더 그려진다.
     */
    val filter = MutableStateFlow(RecordFilter())

    /** 달력이 보고 있는 달. */
    val calendarMonth = MutableStateFlow(YearMonth.now())

    /** 타임라인에서 펼쳐 둔 날짜. */
    val expandedDate = MutableStateFlow<String?>(null)

    fun setSearch(query: String) = update { it.copy(query = query) }

    fun clearSearch() = update { it.copy(query = "") }

    /** 같은 칩을 다시 누르면 해제한다. */
    fun setTypeFilter(type: String?) = update { it.copy(type = if (it.type == type) null else type) }

    /** 같은 칩을 다시 눌러도 풀지 않고 그 종류로 맞춘다(홈 탭에서 기록 탭으로 넘어갈 때). */
    fun showType(type: String?) = update { it.copy(type = type) }

    /** 같은 칩을 다시 누르면 해제한다. */
    fun setPurposeFilter(purpose: String?) =
        update { it.copy(purpose = if (it.purpose == purpose) null else purpose) }

    /** 달력에서 고른 업무일 범위. 순서가 뒤집혀 들어와도 바로잡는다. */
    fun setDateRange(from: String, to: String) =
        update { it.copy(dateRange = if (from <= to) from to to else to to from) }

    fun clearDateRange() = update { it.copy(dateRange = null) }

    fun clearFilters() {
        filter.value = RecordFilter()
    }

    fun setMonth(month: YearMonth) {
        calendarMonth.value = month
    }

    fun previousMonth() {
        calendarMonth.value = calendarMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        calendarMonth.value = calendarMonth.value.plusMonths(1)
    }

    fun toggleDate(date: String) {
        expandedDate.value = if (expandedDate.value == date) null else date
    }

    private inline fun update(block: (RecordFilter) -> RecordFilter) {
        filter.value = block(filter.value)
    }

    // ── 목록 ──────────────────────────────────────────────────────────────────

    /** 홈이 그리는 목록 전체 — 오늘 요약, 최근, 즐겨찾기, 날짜별 타임라인. */
    val state = combine(repo.observeRecords(), filter) { records, current ->
        timeline.build(records, current, DateUtils.todayIso())
    }.stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIBE_KEEP_MS), HomeState())

    // ── 다중 선택 ─────────────────────────────────────────────────────────────

    val selection = HomeSelectionState()

    fun favoriteSelected() {
        val ids = selection.records.value
        selection.clear()
        scope.launch { ids.forEach { repo.toggleFavorite(it, true) } }
    }

    fun deleteSelected() {
        val records = selection.records.value
        val media = selection.media.value
        selection.clear()
        scope.launch {
            records.forEach { repo.softDelete(it) }
            media.forEach { repo.deleteMedia(it) }
        }
    }

    // ── 공유 ──────────────────────────────────────────────────────────────────

    /** 공유 갤러리가 처음 켤 워터마크 항목. */
    val watermarkDefaults = settingsRepo.watermarkDefaults
        .stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIBE_KEEP_MS), WatermarkDefaults())

    /**
     * 워터마크 없이 원본 그대로 보내는 공유 인텐트.
     *
     * @return 고른 것이 없거나 공유 가능한 파일이 하나도 없으면 null
     */
    fun shareSelectedIntent(): Intent? {
        val uris = ArrayList<Uri>()
        selectedRecords().forEach { record ->
            pickedMedia(record).forEach { item ->
                runCatching { mediaStorage.shareUri(item.uri) }.getOrNull()?.let { uris.add(it) }
            }
        }
        return if (uris.isEmpty()) null else sendIntent(uris, MIME_IMAGE_ANY)
    }

    /** 공유 갤러리에 올릴 항목. PDF 는 워터마크를 새길 수 없어 뺀다. */
    fun shareComposerItems(): List<ShareComposerItem> = buildList {
        selectedRecords().forEach { record ->
            val r = record.record
            pickedMedia(record).filter { !it.isPdf }.forEach { item ->
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

    /**
     * 공유 갤러리에서 정한 대로 워터마크를 구운 뒤 외부 공유 인텐트를 만든다.
     *
     * 굽는 동안 화면을 막지 않도록 IO 로 넘긴다. 한 장이라도 실패하면 그 장만 빠진다.
     *
     * @param selections 항목별 워터마크 문구와 스타일. 문구가 비면 원본을 그대로 보낸다
     * @param onReady 인텐트가 준비되면 부른다. 한 장도 못 만들면 호출되지 않는다
     */
    fun shareWithWatermark(selections: List<ShareSelection>, onReady: (Intent) -> Unit) {
        scope.launch {
            val uris = withContext(Dispatchers.IO) {
                selections.mapNotNullTo(ArrayList()) { item ->
                    runCatching {
                        if (item.lines.isNotEmpty()) {
                            mediaStorage.watermarkForShare(item.imageUri, item.lines, item.style)
                        } else {
                            mediaStorage.shareUri(item.imageUri)
                        }
                    }.getOrNull()
                }
            }
            if (uris.isEmpty()) return@launch
            // 영상이 섞이면 image/jpeg 로는 받는 앱이 목록에서 걸러 버린다.
            val mime = if (selections.any { it.isVideo }) MIME_ANY else MIME_JPEG
            onReady(sendIntent(uris, mime))
            selection.clear()
        }
    }

    // ── 가져오기 ──────────────────────────────────────────────────────────────

    /**
     * 갤러리에서 고른 사진을 앱 기록으로 들여온다.
     *
     * @param onSaved 저장된 기록 id. 호출부가 곧바로 상세 화면으로 넘어가는 데 쓴다
     */
    fun importPhoto(uri: Uri, onSaved: (String) -> Unit) {
        scope.launch {
            val purpose = strings[Purpose.ETC.labelRes]
            val saved = mediaStorage.copyFromUri(uri, purpose)
            val id = repo.createPhotoRecord(
                media = saved,
                purposeLabel = purpose,
                title = strings[R.string.imported_photo_title],
            )
            onSaved(id)
        }
    }

    // ── 내부 ──────────────────────────────────────────────────────────────────

    /** 화면에 보이는 기록 중 선택에 걸린 것들. 목록 밖의 기록은 공유 대상이 아니다. */
    private fun selectedRecords(): List<RecordWithMedia> =
        state.value.dateSections.flatMap { it.items }.distinctBy { it.record.recordId }

    /** 기록을 통째로 골랐으면 전체, 낱장만 골랐으면 그 낱장만. */
    private fun pickedMedia(record: RecordWithMedia) =
        if (record.record.recordId in selection.records.value) {
            record.media
        } else {
            record.media.filter { it.mediaId in selection.media.value }
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

    private companion object {
        const val SUBSCRIBE_KEEP_MS = 5_000L
        const val MIME_IMAGE_ANY = "image/*"
        const val MIME_JPEG = "image/jpeg"
        const val MIME_ANY = "*/*"
    }
}

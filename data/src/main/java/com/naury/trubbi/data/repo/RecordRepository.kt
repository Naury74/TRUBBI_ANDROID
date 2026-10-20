package com.naury.trubbi.data.repo

import com.naury.trubbi.core.file.MediaStorage
import com.naury.trubbi.core.file.SavedMedia
import com.naury.trubbi.core.location.CapturedLocation
import com.naury.trubbi.core.location.LocationProvider
import com.naury.trubbi.core.log.logFailure
import com.naury.trubbi.core.ml.MediaAnalyzer
import com.naury.trubbi.core.ui.StringProvider
import com.naury.trubbi.core.util.DateUtils
import com.naury.trubbi.data.DataChangeNotifier
import com.naury.trubbi.data.R
import com.naury.trubbi.data.local.DateCount
import com.naury.trubbi.data.local.MediaEntity
import com.naury.trubbi.data.local.RecordEntity
import com.naury.trubbi.data.local.RecordWithMedia
import com.naury.trubbi.data.local.TrubbiDao
import com.naury.trubbi.domain.model.Purpose
import com.naury.trubbi.domain.model.RecordType
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * 기록 저장·조회의 단일 창구.
 *
 * 저장 함수는 기록 종류별로 나뉘어 있다. 종류마다 딸리는 파일 구성이 다르다.
 * 사진은 이미지 n장, 문서는 원본 페이지들과 PDF, 바코드는 값만 저장한다.
 *
 * 저장 호출은 파일 쓰기와 행 삽입까지만 수행하고 반환한다. 위치 캡처와 OCR·라벨링은
 * 앱 스코프에서 별도로 실행돼 나중에 같은 행을 갱신한다.
 *
 * @property dao 기록·미디어 테이블
 * @property mediaStorage 파일 저장과 디코딩
 * @property analyzer 검색용 OCR·라벨링
 * @property notifier 저장이 끝난 뒤 바탕화면 위젯에 갱신을 알린다
 * @property locationProvider 저장 직후 위치 보강
 * @property appScope 화면 수명과 무관하게 도는 후처리용 스코프
 * @property journeyRepo 진행 중 여정에 기록을 이어 붙일지 판단
 * @property strings 기본 제목 등 문구용 리소스 접근자
 */
@Singleton
class RecordRepository @Inject constructor(
    private val dao: TrubbiDao,
    private val mediaStorage: MediaStorage,
    private val analyzer: MediaAnalyzer,
    private val notifier: DataChangeNotifier,
    private val locationProvider: LocationProvider,
    private val appScope: CoroutineScope,
    private val journeyRepo: JourneyRepository,
    private val strings: StringProvider,
) {

    /**
     * 위치·주소를 뒤에서 받아 와 나중에 기록에 채워 넣는다.
     *
     * GPS 측위에 수 초가 걸리므로 저장 경로를 대기시키지 않고 분리한다.
     * 카메라 화면이 닫혀도 완료되도록 앱 스코프에서 실행한다.
     *
     * @param recordId 위치를 채워 넣을 기록 id
     */
    fun attachLocationAsync(recordId: String) {
        appScope.launch {
            val loc = runCatching { locationProvider.capture() }
                .logFailure(TAG, "저장 후 위치 보강 실패")
                .getOrNull() ?: return@launch
            saveLocation(recordId, loc)
        }
    }

    /**
     * 이미 아는 좌표(앨범 사진의 GPS 등)로 주소·동네·건물 이름을 찾아 기록에 채운다.
     *
     * 주소를 못 구해도 좌표는 남긴다. 그래야 "이 근처 기록" 검색에 걸린다.
     */
    fun attachLocationAtAsync(recordId: String, latitude: Double, longitude: Double) {
        appScope.launch {
            val loc = runCatching { locationProvider.describe(latitude, longitude) }
                .logFailure(TAG, "사진 위치 주소 찾기 실패")
                .getOrNull() ?: CapturedLocation(latitude, longitude, null)
            saveLocation(recordId, loc)
        }
    }

    /** 기록 위치를 사용자가 고친 값으로 바꾼다. 주소·동네·건물 이름을 모두 남겨 어느 이름으로도 찾을 수 있게 한다. */
    suspend fun setLocation(recordId: String, location: CapturedLocation) = saveLocation(recordId, location)

    private suspend fun saveLocation(recordId: String, loc: CapturedLocation) {
        dao.updateLocation(
            recordId,
            loc.latitude,
            loc.longitude,
            loc.address ?: loc.building,
            loc.regionLabel,
            loc.building,
            DateUtils.now(),
        )
    }

    private val backfill = Mutex()

    /**
     * 동네·건물 이름 칸이 생기기 전에 저장된 기록을 좌표로 채운다.
     *
     * 앱을 열 때 한 번씩 부른다. 한 번에 [BACKFILL_MAX] 건까지만, 사이사이 쉬면서 묻는다.
     * 주소 조회가 통째로 실패하면(오프라인·키 없음) 그 회차는 바로 멈추고 다음에 다시 한다.
     */
    fun backfillPlaceNamesAsync() {
        appScope.launch {
            if (!backfill.tryLock()) return@launch
            try {
                var done = 0
                while (done < BACKFILL_MAX) {
                    val batch = dao.recordsMissingPlaceNames(BACKFILL_BATCH)
                    if (batch.isEmpty()) break
                    for (r in batch) {
                        val lat = r.latitude ?: continue
                        val lng = r.longitude ?: continue
                        val loc = runCatching { locationProvider.describe(lat, lng) }.getOrNull()
                        if (loc == null || (loc.address == null && loc.region == null)) return@launch
                        dao.fillPlaceNames(r.recordId, loc.regionLabel.orEmpty(), loc.building)
                        done++
                        delay(BACKFILL_GAP_MS)
                    }
                }
            } finally {
                backfill.unlock()
            }
        }
    }

    fun observeRecords(): Flow<List<RecordWithMedia>> = dao.observeRecords()
    fun observeBarcodes(): Flow<List<RecordWithMedia>> = dao.observeBarcodes()
    fun observeDeleted(): Flow<List<RecordWithMedia>> = dao.observeRecordsByDeleted(true)
    fun observeRecord(id: String): Flow<RecordWithMedia?> = dao.observeRecord(id)
    fun observeByDate(date: String): Flow<List<RecordWithMedia>> = dao.observeRecordsByDate(date)
    fun search(q: String): Flow<List<RecordWithMedia>> = dao.search(q)
    fun observeDateCounts(start: String, end: String): Flow<List<DateCount>> =
        dao.observeDateCounts(start, end)

    /** 사진 한 장짜리 기록. [linkToJourney] 가 false 면 진행 중 여정에 붙이지 않는다(앨범에서 가져온 지난 사진). */
    suspend fun createPhotoRecord(
        media: SavedMedia,
        purposeLabel: String,
        title: String,
        memo: String? = null,
        tags: List<String> = emptyList(),
        jobDate: String = DateUtils.todayIso(),
        locationName: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        linkToJourney: Boolean = true,
    ): String {
        val now = DateUtils.now()
        val recordId = UUID.randomUUID().toString()
        dao.insertRecord(
            RecordEntity(
                recordId = recordId,
                type = RecordType.PHOTO.name,
                title = title,
                purpose = purposeLabel,
                memo = memo,
                jobDate = jobDate,
                createdAt = now,
                updatedAt = now,
                latitude = latitude,
                longitude = longitude,
                locationName = locationName,
                tags = tags,
                barcodeValue = null,
                barcodeFormat = null,
            ),
        )
        dao.insertMedia(media.toEntity(recordId, now))
        triggerAnalysis(recordId, media.uri, tags)
        if (linkToJourney) journeyRepo.onRecordSaved(recordId, title)
        return recordId
    }

    /** Creates one record holding several captured photos (burst / 묶음 저장). */
    suspend fun createPhotoBatchRecord(
        mediaList: List<SavedMedia>,
        purposeLabel: String,
        title: String,
        names: List<String> = emptyList(),
        jobDate: String = DateUtils.todayIso(),
        locationName: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
    ): String {
        val now = DateUtils.now()
        val recordId = UUID.randomUUID().toString()
        dao.insertRecord(
            RecordEntity(
                recordId = recordId,
                type = RecordType.PHOTO.name,
                title = title,
                purpose = purposeLabel,
                memo = null,
                jobDate = jobDate,
                createdAt = now,
                updatedAt = now,
                latitude = latitude,
                longitude = longitude,
                locationName = locationName,
                tags = emptyList(),
                barcodeValue = null,
                barcodeFormat = null,
            ),
        )
        mediaList.forEachIndexed { index, m ->
            // createdAt 이 같으면 정렬이 불안정해진다. 인덱스만큼 더해 촬영 순서를 고정한다.
            dao.insertMedia(m.toEntity(recordId, now + index).copy(name = names.getOrNull(index)))
        }
        mediaList.firstOrNull()?.let { triggerAnalysis(recordId, it.uri, emptyList()) }
        journeyRepo.onRecordSaved(recordId, title)
        return recordId
    }

    /**
     * 영상 기록을 만든다.
     *
     * @param media 저장이 끝난 영상 파일 정보
     * @param purposeLabel 용도 라벨
     * @param title 제목. 비우면 기본 제목을 만들어 쓴다
     * @param jobDate 업무 기준 날짜 `yyyy-MM-dd`. 기본은 오늘
     * @param locationName 촬영 위치 주소(선택)
     * @param latitude 촬영 위도(선택)
     * @param longitude 촬영 경도(선택)
     * @return 만들어진 기록 id
     */
    suspend fun createVideoRecord(
        media: SavedMedia,
        purposeLabel: String,
        title: String,
        jobDate: String = DateUtils.todayIso(),
        locationName: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
    ): String {
        val now = DateUtils.now()
        val recordId = UUID.randomUUID().toString()
        dao.insertRecord(
            RecordEntity(
                recordId = recordId,
                type = RecordType.VIDEO.name,
                title = title,
                purpose = purposeLabel,
                memo = null,
                jobDate = jobDate,
                createdAt = now,
                updatedAt = now,
                latitude = latitude,
                longitude = longitude,
                locationName = locationName,
                tags = emptyList(),
                barcodeValue = null,
                barcodeFormat = null,
            ),
        )
        dao.insertMedia(media.toEntity(recordId, now))
        journeyRepo.onRecordSaved(recordId, title)
        return recordId
    }

    /**
     * 스캔한 문서 기록을 만든다. PDF 와 원본 페이지 사진들이 기록 하나에 함께 들어간다.
     *
     * 대표 파일은 [com.naury.trubbi.data.local.RecordWithMedia.displayMedia] 규칙에 따라
     * `createdAt` 이 가장 큰 항목이 된다. PDF 를 마지막에 삽입해야 대표 파일이 PDF 가 된다.
     *
     * @param pdf 보정이 끝난 PDF
     * @param pages 원본 페이지 사진들. 비어 있어도 된다
     * @param purposeLabel 용도 라벨
     * @param title 제목. 비우면 용도로 기본 제목을 만든다
     * @param jobDate 업무 기준 날짜 `yyyy-MM-dd`. 기본은 오늘
     * @param locationName 촬영 위치 주소(선택)
     * @param latitude 촬영 위도(선택)
     * @param longitude 촬영 경도(선택)
     * @param recognizedText PDF 를 만들며 모든 쪽에서 읽은 글자. 있으면 검색 색인에 그대로 쓴다
     * @return 만들어진 기록 id
     */
    suspend fun createDocumentRecord(
        pdf: SavedMedia,
        pages: List<SavedMedia> = emptyList(),
        purposeLabel: String,
        title: String,
        jobDate: String = DateUtils.todayIso(),
        locationName: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        recognizedText: String? = null,
    ): String {
        val now = DateUtils.now()
        val recordId = UUID.randomUUID().toString()
        dao.insertRecord(
            RecordEntity(
                recordId = recordId,
                type = RecordType.DOCUMENT.name,
                title = title.ifBlank { strings[R.string.default_title_document, purposeLabel] },
                purpose = purposeLabel,
                memo = null,
                jobDate = jobDate,
                createdAt = now,
                updatedAt = now,
                latitude = latitude,
                longitude = longitude,
                locationName = locationName,
                tags = emptyList(),
                barcodeValue = null,
                barcodeFormat = null,
            ),
        )
        // 원본 페이지를 먼저, PDF 를 마지막에 삽입해 대표 파일이 PDF 가 되게 한다.
        pages.forEachIndexed { index, m ->
            dao.insertMedia(m.toEntity(recordId, now + index).copy(name = strings[R.string.page_index, index + 1]))
        }
        dao.insertMedia(pdf.toEntity(recordId, now + pages.size + 1))
        // PDF 를 만들 때 모든 쪽의 글자를 이미 읽었으면 그걸 색인에 쓰고, 라벨링만 첫 쪽으로 돌린다.
        // 없으면 첫 쪽만 OCR 한다. 전체 쪽을 다시 처리하면 저장 후 부하가 길게 이어진다.
        val ocrSource = pages.firstOrNull()?.uri ?: pdf.thumbnailUri
        triggerAnalysis(recordId, ocrSource, emptyList(), ocrOverride = recognizedText?.takeIf { it.isNotBlank() })
        journeyRepo.onRecordSaved(recordId, title.ifBlank { strings[R.string.default_title_document, purposeLabel] })
        return recordId
    }

    /**
     * OCR·라벨링을 뒤에서 돌려 검색 색인만 채운다.
     *
     * 인식 결과를 태그에 자동으로 추가하지 않는다. 태그는 사용자가 입력한 값만 유지되고,
     * 인식 결과는 `ocrText`·`aiLabels` 컬럼에만 저장돼 검색에 쓰인다.
     *
     * @param recordId 갱신할 기록 id
     * @param mediaUri 분석할 이미지 uri
     * @param baseTags 그대로 유지할 사용자 태그
     */
    private fun triggerAnalysis(recordId: String, mediaUri: String, baseTags: List<String>, ocrOverride: String? = null) {
        appScope.launch {
            val bitmap = runCatching { mediaStorage.decodeForEditing(mediaUri) }
                .logFailure(TAG, "검색 색인용 이미지 디코딩 실패")
                .getOrNull() ?: return@launch
            val result = analyzer.analyze(bitmap)
            dao.updateAnalysis(
                id = recordId,
                ocrText = ocrOverride ?: result.ocrText,
                aiLabels = result.labelKeywords.joinToString(" "),
                tags = baseTags,
                now = DateUtils.now(),
            )
        }
    }

    suspend fun createBarcodeRecord(
        value: String,
        format: String,
        media: SavedMedia?,
        purposeLabel: String,
        title: String = value,
        memo: String? = null,
        tags: List<String> = emptyList(),
        jobDate: String = DateUtils.todayIso(),
    ): String {
        val now = DateUtils.now()
        val recordId = UUID.randomUUID().toString()
        dao.insertRecord(
            RecordEntity(
                recordId = recordId,
                type = RecordType.BARCODE.name,
                title = title.ifBlank { value },
                purpose = purposeLabel,
                memo = memo,
                jobDate = jobDate,
                createdAt = now,
                updatedAt = now,
                latitude = null,
                longitude = null,
                locationName = null,
                tags = tags,
                barcodeValue = value,
                barcodeFormat = format,
            ),
        )
        media?.let { dao.insertMedia(it.toEntity(recordId, now)) }
        notifier.barcodesChanged()
        journeyRepo.onRecordSaved(recordId, title.ifBlank { value })
        return recordId
    }

    suspend fun createMemoRecord(
        text: String,
        purposeLabel: String? = null,
        jobDate: String = DateUtils.todayIso(),
        title: String? = null,
    ): String {
        val now = DateUtils.now()
        val recordId = UUID.randomUUID().toString()
        val finalTitle = title ?: text.lineSequence().firstOrNull { it.isNotBlank() }?.take(40) ?: strings[RecordType.MEMO.labelRes]
        dao.insertRecord(
            RecordEntity(
                recordId = recordId,
                type = RecordType.MEMO.name,
                title = finalTitle,
                purpose = purposeLabel ?: strings[Purpose.ETC.labelRes],
                memo = text,
                jobDate = jobDate,
                createdAt = now,
                updatedAt = now,
                latitude = null,
                longitude = null,
                locationName = null,
                tags = emptyList(),
                barcodeValue = null,
                barcodeFormat = null,
            ),
        )
        journeyRepo.onRecordSaved(recordId, finalTitle)
        return recordId
    }

    suspend fun addEditedMedia(recordId: String, edited: SavedMedia, originalUri: String) {
        val now = DateUtils.now()
        dao.insertMedia(
            MediaEntity(
                mediaId = UUID.randomUUID().toString(),
                recordId = recordId,
                uri = edited.uri,
                thumbnailUri = edited.thumbnailUri,
                mimeType = edited.mimeType,
                width = edited.width,
                height = edited.height,
                fileSize = edited.fileSize,
                isEdited = true,
                originalUri = originalUri,
                durationMs = edited.durationMs,
                createdAt = now,
            ),
        )
    }

    suspend fun toggleFavorite(id: String, fav: Boolean) = dao.setFavorite(id, fav, DateUtils.now())
    suspend fun updateMemo(id: String, memo: String?) = dao.updateMemo(id, memo, DateUtils.now())

    /** 메모 본문과 파생 제목을 한 트랜잭션으로 갱신한다. */
    suspend fun updateMemoContent(id: String, title: String, content: String) =
        dao.updateMemoAndTitle(id, title, content, DateUtils.now())
    suspend fun updateTags(id: String, tags: List<String>) = dao.updateTags(id, tags, DateUtils.now())
    suspend fun updateMeta(id: String, title: String, purposeLabel: String, jobDate: String) =
        dao.updateMeta(id, title, purposeLabel, jobDate, DateUtils.now())

    /** 휴지통으로 이동한다. 파일은 삭제하지 않아 복원할 수 있다. */
    suspend fun softDelete(id: String) {
        dao.softDelete(id, DateUtils.now())
        notifier.barcodesChanged()
    }

    /** 휴지통에서 되돌린다. */
    suspend fun restore(id: String) = dao.restore(id, DateUtils.now())

    /** 행을 완전히 삭제한다. 휴지통 비우기에서만 호출한다. */
    suspend fun hardDelete(id: String) = dao.hardDelete(id)
    suspend fun deleteMedia(mediaId: String) = dao.deleteMedia(mediaId)

    private fun SavedMedia.toEntity(recordId: String, now: Long) = MediaEntity(
        mediaId = UUID.randomUUID().toString(),
        recordId = recordId,
        uri = uri,
        thumbnailUri = thumbnailUri,
        mimeType = mimeType,
        width = width,
        height = height,
        fileSize = fileSize,
        isEdited = false,
        originalUri = null,
        durationMs = durationMs,
        createdAt = now,
    )

    private companion object {
        const val TAG = "RecordRepository"
        const val BACKFILL_MAX = 120
        const val BACKFILL_BATCH = 20
        const val BACKFILL_GAP_MS = 150L
    }
}

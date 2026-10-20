package com.naury.trubbi.feature.camera

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naury.framekit.android.output.FrameKitOutputs
import com.naury.trubbi.R
import com.naury.trubbi.core.file.MediaStorage
import com.naury.trubbi.core.ui.StringProvider
import com.naury.trubbi.data.repo.RecordRepository
import com.naury.trubbi.data.settings.SettingsRepository
import com.naury.trubbi.domain.document.DocumentPdfBuilder
import com.naury.trubbi.domain.model.Purpose
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 촬영 화면 상태.
 *
 * 저장명과 용도의 초기값을 현재 위치·진행 중 여정 단계에서 유추해 제공한다.
 * 두 값 모두 촬영 화면에서 그대로 수정할 수 있다.
 *
 * @property repo 기록 저장
 * @property mediaStorage 파일 저장과 OCR 제목 추출
 * @param settingsRepo 화질·촬영 후 편집 등 설정
 * @param journeyRepo 진행 중 여정의 단계로 용도를 추천
 * @property locationProvider 저장명에 넣을 짧은 위치 라벨
 * @property strings 추천 용도 라벨을 현재 언어로 읽는다
 */
@HiltViewModel
class CameraViewModel @Inject constructor(
    private val repo: RecordRepository,
    private val mediaStorage: MediaStorage,
    settingsRepo: SettingsRepository,
    journeyRepo: com.naury.trubbi.data.repo.JourneyRepository,
    private val locationProvider: com.naury.trubbi.core.location.LocationProvider,
    private val strings: StringProvider,
    private val pdfBuilder: DocumentPdfBuilder,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /**
     * 짧은 현재 위치 라벨(예: "강남구 역삼동") — 화면이 열릴 때 한 번 캡처해 저장명 자동 제안에 쓴다.
     * 위치 저장 설정이 켜져 있을 때만 캡처(개인정보·권한 일관성).
     */
    val locationLabel = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            val save = runCatching { settingsRepo.settings.map { it.saveLocation }.first() }.getOrDefault(false)
            if (save) {
                locationLabel.value = runCatching { locationProvider.capture()?.regionLabel }.getOrNull()
            }
        }
    }

    /** 위치가 있으면 "위치 용도", 없으면 "용도 종류 시각" 형태로 저장명을 제안. */
    fun suggestName(purposeLabel: String, typeWord: String): String {
        val loc = locationLabel.value
        return if (!loc.isNullOrBlank()) {
            "$loc $purposeLabel"
        } else {
            "$purposeLabel $typeWord ${com.naury.trubbi.core.util.DateUtils.shortStamp()}"
        }
    }

    /** OCR로 문서 제목 추출(첫 의미 있는 줄). 실패 시 null. */
    suspend fun documentTitle(firstPageUri: android.net.Uri): String? =
        runCatching { mediaStorage.ocrTitle(firstPageUri) }.getOrNull()

    /**
     * 진행 중 여정의 현재 단계가 상차·하차면 해당 용도를 추천값으로 흘린다.
     * 여정이 없거나 단계를 특정할 수 없으면 null 이고, 이때는 설정의 기본 용도가 쓰인다.
     */
    val suggestedPurpose = journeyRepo.activeJourney
        .map { j ->
            when (j?.currentStop?.kindEnum) {
                com.naury.trubbi.domain.model.StopKind.PICKUP -> strings[Purpose.LOADING.labelRes]
                com.naury.trubbi.domain.model.StopKind.DROPOFF -> strings[Purpose.UNLOADING.labelRes]
                else -> null
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val settings = settingsRepo.settings

    val quality = settings
        .map { it.photoQuality.jpegQuality }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 90)

    val editAfterDefault = settings
        .map { it.editAfterCapture }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val saveLocation = settings
        .map { it.saveLocation }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val purposes = settingsRepo.purposes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 찍은 사진 한 장을 저장한다.
     *
     * @param file 카메라가 만든 임시 파일. 저장 후 지운다
     * @param purposeLabel 용도 라벨
     * @param title 기록 제목. 비우면 용도로 기본 제목을 만든다
     * @param onSaved 저장이 끝나면 (기록 id, 저장된 미디어 uri) 로 불린다
     */
    fun onPhotoCaptured(file: File, purposeLabel: String, title: String, onSaved: (recordId: String, mediaUri: String) -> Unit) {
        viewModelScope.launch {
            val bitmap = mediaStorage.decodeNormalized(file)
            file.delete()
            val saved = mediaStorage.saveBitmap(bitmap, purposeLabel, quality = quality.value)
            val id = repo.createPhotoRecord(
                media = saved,
                purposeLabel = purposeLabel,
                title = title.ifBlank { strings[R.string.default_title_photo, purposeLabel] },
            )
            if (saveLocation.value) repo.attachLocationAsync(id)
            onSaved(id, saved.uri)
        }
    }

    /**
     * 연속으로 찍은 여러 장을 **한 기록**으로 묶어 저장한다.
     *
     * 파손 부위를 여러 각도로 찍으면 그건 사진 다섯 건이 아니라 증빙 한 건이다.
     * 목록에서도 한 덩어리로 보이고, 여정에 붙일 때도 함께 움직인다.
     *
     * @param files 카메라가 만든 임시 파일들. 촬영 순서대로 들어온다
     * @param purposeLabel 용도 라벨
     * @param groupTitle 묶음 제목. 비우면 용도와 장수로 만든다
     * @param names 사진 한 장씩의 이름. [files] 와 같은 순서
     * @param onSaved 저장이 끝나면 (기록 id, 첫 장의 uri) 로 불린다
     */
    fun savePhotoBatch(
        files: List<File>,
        purposeLabel: String,
        groupTitle: String,
        names: List<String>,
        onSaved: (recordId: String, mediaUri: String) -> Unit,
    ) {
        if (files.isEmpty()) return
        viewModelScope.launch {
            val saved = files.map { f ->
                val bitmap = mediaStorage.decodeNormalized(f)
                f.delete()
                mediaStorage.saveBitmap(bitmap, purposeLabel, quality = quality.value)
            }
            val id = repo.createPhotoBatchRecord(
                mediaList = saved,
                purposeLabel = purposeLabel,
                title = groupTitle.ifBlank { strings[R.string.default_title_batch, purposeLabel, saved.size] },
                names = names,
            )
            if (saveLocation.value) repo.attachLocationAsync(id)
            onSaved(id, saved.first().uri)
        }
    }

    /**
     * 스캔한 문서를 저장한다. 쪽 사진을 그대로 담고 검색용 글자 레이어를 겹친 PDF 를 만든다.
     *
     * 다 저장하면(실패해도) 스캐너가 만든 쪽 파일은 지운다.
     *
     * @param pageUris 스캐너가 펴서 돌려준 쪽 사진들(쪽 순서)
     * @param keepOriginals true 면 쪽 사진도 기록에 함께 남긴다
     * @param purposeLabel 용도 라벨
     * @param title 기록 제목. 비우면 용도로 기본 제목을 만든다
     * @param onSaved 저장이 끝나면 (기록 id, PDF uri) 로 불린다
     */
    fun saveDocument(
        pageUris: List<android.net.Uri>,
        keepOriginals: Boolean,
        purposeLabel: String,
        title: String,
        onSaved: (recordId: String, pdfUri: String) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val pdf = pdfBuilder.build(pageUris) ?: return@launch
                val savedPdf = try {
                    mediaStorage.savePdf(pdf.uri, purposeLabel)
                } finally {
                    FrameKitOutputs.deleteOutput(context, pdf.uri)
                }
                // 쪽 사진은 스캐너가 이미 펴고 보정한 JPEG 라 다시 인코딩하지 않고 옮긴다.
                val savedPages = if (keepOriginals) {
                    pageUris.map { mediaStorage.importEditedImage(it, "jpg", "image/jpeg", 0, 0, label = "scan") }
                } else {
                    emptyList()
                }
                val id = repo.createDocumentRecord(
                    pdf = savedPdf,
                    pages = savedPages,
                    purposeLabel = purposeLabel,
                    // 비어 있으면 리포지토리가 용도로 기본 제목을 만든다.
                    title = title,
                    recognizedText = pdf.recognizedText,
                )
                if (saveLocation.value) repo.attachLocationAsync(id)
                onSaved(id, savedPdf.uri)
            } finally {
                discardScan(pageUris)
            }
        }
    }

    /** 저장하지 않기로 한 스캔 결과를 지운다. */
    fun discardScan(pageUris: List<android.net.Uri>) {
        pageUris.forEach { FrameKitOutputs.deleteOutput(context, it) }
    }

    /**
     * 촬영한 영상을 저장한다.
     *
     * @param file 카메라가 만든 임시 mp4. 저장 후 지운다
     * @param purposeLabel 용도 라벨
     * @param title 기록 제목. 비우면 용도로 기본 제목을 만든다
     * @param onSaved 저장이 끝나면 (기록 id, 미디어 uri) 로 불린다
     */
    fun onVideoCaptured(file: File, purposeLabel: String, title: String, onSaved: (recordId: String, mediaUri: String) -> Unit) {
        viewModelScope.launch {
            val saved = mediaStorage.saveVideo(file, purposeLabel)
            file.delete()
            val id = repo.createVideoRecord(
                media = saved,
                purposeLabel = purposeLabel,
                title = title.ifBlank { strings[R.string.default_title_video, purposeLabel] },
            )
            if (saveLocation.value) repo.attachLocationAsync(id)
            onSaved(id, saved.uri)
        }
    }
}

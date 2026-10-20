package com.naury.trubbi.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * 기록·연락처·미디어 테이블과 화면이 함께 읽는 조합들.
 *
 * 별도 도메인 모델과 매퍼를 두지 않고 엔티티를 그대로 화면까지 노출한다. 필드 구성이 같아
 * 매퍼 계층이 변환 없는 복사만 하기 때문이다. 플랫폼과 무관한 규칙은 `domain/model` 의
 * 열거형에 둔다.
 */

/**
 * 기록 한 건. 사진·영상·문서·바코드·메모가 모두 이 테이블에 저장된다.
 *
 * @property recordId 기록 식별자(UUID)
 * @property type [com.naury.trubbi.domain.model.RecordType] 이름
 * @property title 목록에 보이는 제목
 * @property purpose 용도 라벨. 사용자 정의가 가능해 열거형이 아닌 문자열이다
 * @property memo 메모 본문(HTML). 메모 기록이 아니면 null
 * @property jobDate 업무 기준 날짜 `yyyy-MM-dd`. 심야 작업을 전날로 묶기 위해 [createdAt] 과 분리한다
 * @property createdAt 저장 시각(epoch ms)
 * @property updatedAt 마지막 수정 시각(epoch ms)
 * @property latitude 촬영 위도. 위치 저장을 껐으면 null
 * @property longitude 촬영 경도
 * @property locationName 역지오코딩한 주소 문자열
 * @property locationRegion 행정동 기준 동네 이름("중구 명동"). 도로명 주소에는 동이 없어 따로 둔다. 검색용
 * @property locationPlace 건물·장소 이름("서울특별시청"). 검색용
 * @property tags 사용자 태그
 * @property barcodeValue 스캔한 바코드 값
 * @property barcodeFormat 바코드 형식 이름
 * @property isFavorite 즐겨찾기 여부
 * @property isDeleted 휴지통 이동 여부. 실제 행 삭제는 휴지통 비우기에서만 일어난다
 * @property ocrText 첨부 이미지에서 읽은 글자. 검색 색인용
 * @property aiLabels 온디바이스 라벨 + 한국어 동의어를 공백으로 이은 것. 검색 색인용
 * @property journeyId 속한 여정 id. null 이면 여정에 속하지 않은 단일 기록
 * @property stopId 촬영 시점의 정차지 id. 확정할 수 없으면 null
 * @property evidenceCategory [com.naury.trubbi.domain.model.EvidenceCategory] 이름. 여정에 묶였을 때만
 */
@Entity(tableName = "records")
data class RecordEntity(
    @PrimaryKey val recordId: String,
    val type: String,
    val title: String,
    val purpose: String,
    val memo: String?,
    val jobDate: String,
    val createdAt: Long,
    val updatedAt: Long,
    val latitude: Double?,
    val longitude: Double?,
    val locationName: String?,
    val tags: List<String>,
    val barcodeValue: String?,
    val barcodeFormat: String?,
    val isFavorite: Boolean = false,
    val isDeleted: Boolean = false,
    val ocrText: String? = null,
    val aiLabels: String? = null,
    val journeyId: String? = null,
    val stopId: String? = null,
    val evidenceCategory: String? = null,
    val locationRegion: String? = null,
    val locationPlace: String? = null,
)

/**
 * 거래처·기사 연락처. 기기 주소록에서 가져온 항목과 앱에서 만든 항목이 함께 저장된다.
 *
 * @property contactId 연락처 식별자(UUID)
 * @property deviceContactId 기기 주소록에서 가져왔다면 원본 id. 직접 만들었으면 null
 * @property name 이름
 * @property company 회사·소속
 * @property type 분류(화주·기사·정비 등)
 * @property phones 전화번호 목록
 * @property memo 메모
 * @property tags 태그
 * @property createdAt 저장 시각(epoch ms)
 * @property updatedAt 마지막 수정 시각(epoch ms)
 */
@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val contactId: String,
    val deviceContactId: String?,
    val name: String,
    val company: String?,
    val type: String?,
    val phones: List<String>,
    val memo: String?,
    val tags: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "media",
    indices = [Index("recordId")],
)
/**
 * 기록에 딸린 파일 한 개. 연속 촬영·다중 페이지 문서는 한 기록에 여러 행이 붙는다.
 *
 * @property mediaId 미디어 식별자(UUID)
 * @property recordId 소속 기록
 * @property uri 실제 파일 uri
 * @property thumbnailUri 목록용 축소본. null 이면 화면이 [uri] 를 사용한다
 * @property mimeType `image/jpeg` · `video/mp4` · `application/pdf`
 * @property width 표시 기준 가로 픽셀
 * @property height 표시 기준 세로 픽셀
 * @property fileSize 바이트 수
 * @property isEdited true 면 [uri] 는 편집본이고 원본은 [originalUri] 에 있다
 * @property originalUri 편집 전 원본 uri
 * @property durationMs 영상 길이(ms). 이미지면 null
 * @property name 파일 단위 이름. 연속 촬영 묶음에서 장별 이름을 붙일 때 쓴다
 * @property createdAt 저장 시각(epoch ms). 같은 기록 내 정렬 기준이다
 */
data class MediaEntity(
    @PrimaryKey val mediaId: String,
    val recordId: String,
    val uri: String,
    val thumbnailUri: String?,
    val mimeType: String,
    val width: Int?,
    val height: Int?,
    val fileSize: Long?,
    val isEdited: Boolean = false,
    val originalUri: String? = null,
    val durationMs: Long? = null,
    val name: String? = null,
    val createdAt: Long,
) {
    val isVideo: Boolean get() = mimeType.startsWith("video")
    val isPdf: Boolean get() = mimeType == "application/pdf"
}

/**
 * 기록과 딸린 파일들을 함께 조회한 결과. 화면이 다루는 기본 단위다.
 *
 * @property record 기록 본체
 * @property media 딸린 파일들. 조회 순서가 저장 순서와 다르므로 정렬은 아래 프로퍼티가 담당한다
 */
data class RecordWithMedia(
    @Embedded val record: RecordEntity,
    @Relation(parentColumn = "recordId", entityColumn = "recordId")
    val media: List<MediaEntity>,
) {
    /** [createdAt] 이 가장 큰 파일. 편집본이 있으면 편집본이 된다. */
    val displayMedia: MediaEntity?
        get() = media.maxByOrNull { it.createdAt }

    /** [createdAt] 이 가장 작은 파일. 최초 촬영본이다. */
    val originalMedia: MediaEntity?
        get() = media.minByOrNull { it.createdAt }

    val primaryThumb: String?
        get() = displayMedia?.let { it.thumbnailUri ?: it.uri }

    /** 딸린 파일들의 바이트 합계. */
    val totalSize: Long
        get() = media.sumOf { it.fileSize ?: 0L }

    /** 영상이 하나라도 포함돼 있는지. 워터마크 적용 가능 여부 판단에 쓴다. */
    val hasVideo: Boolean
        get() = media.any { it.isVideo }

    /** 딸린 스캔 PDF. 없으면 null. */
    val pdfMedia: MediaEntity?
        get() = media.firstOrNull { it.isPdf }

    /** 스캔 PDF 가 포함돼 있는지. */
    val hasPdf: Boolean
        get() = media.any { it.isPdf }
}

/** 달력 날짜 배지용 최소 조회 결과. 기록 본문과 미디어를 읽지 않는다. */
data class DateCount(
    val jobDate: String,
    val count: Int,
)

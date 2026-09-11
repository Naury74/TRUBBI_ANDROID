package com.naury.trubbi.domain.model

import androidx.annotation.StringRes

/**
 * 저장된 기록의 종류.
 *
 * 홈 피드·앨범 필터·파일명 접두어에 쓰인다. DB 에는 enum 이름(String)으로 저장하므로
 * 순서를 바꾸거나 값을 지우면 기존 데이터가 [PHOTO] 로 떨어진다.
 *
 * @property labelRes 화면에 보여줄 이름 (다국어)
 */
enum class RecordType(@StringRes override val labelRes: Int) : Labeled {
    PHOTO(R.string.record_type_photo),
    VIDEO(R.string.record_type_video),
    DOCUMENT(R.string.record_type_document),
    BARCODE(R.string.record_type_barcode),
    MEMO(R.string.record_type_memo),
    FILE(R.string.record_type_file),
    ;

    companion object {
        /** DB 문자열 → enum. 모르는 값이면 [PHOTO]. */
        fun fromName(value: String?): RecordType =
            entries.firstOrNull { it.name == value } ?: PHOTO
    }
}

/**
 * 기록에 붙는 업무 용도.
 *
 * 지금은 사용자가 설정에서 자유롭게 추가/삭제하는 자유 라벨이라 DB 에는 라벨 문자열이
 * 그대로 들어간다. 이 enum 은 초기 버전에서 저장된 값을 읽어오기 위한 호환용이다.
 *
 * @property labelRes 기본 제공 용도의 표시 이름 (다국어)
 */
enum class Purpose(@StringRes override val labelRes: Int) : Labeled {
    WAYBILL(R.string.purpose_waybill),
    LOADING(R.string.purpose_loading),
    UNLOADING(R.string.purpose_unloading),
    INBOUND(R.string.purpose_inbound),
    OUTBOUND(R.string.purpose_outbound),
    MAINTENANCE(R.string.purpose_maintenance),
    TIRE(R.string.purpose_tire),
    DAMAGE(R.string.purpose_damage),
    ETC(R.string.purpose_etc),
    ;

    companion object {
        /** DB 문자열 → enum. 모르는 값이면 [ETC]. */
        fun fromName(value: String?): Purpose =
            entries.firstOrNull { it.name == value } ?: ETC

        /**
         * 위젯·딥링크에서 넘어온 용도 토큰을 해석한다.
         *
         * @param value `trubbi://capture?purpose=loading` 의 값. 영/한 모두 받는다.
         */
        fun fromDeepLink(value: String?): Purpose = when (value?.lowercase()) {
            "loading", "상차" -> LOADING
            "unloading", "하차" -> UNLOADING
            "waybill", "운송장" -> WAYBILL
            "maintenance", "정비" -> MAINTENANCE
            "tire", "타이어" -> TIRE
            else -> ETC
        }
    }
}

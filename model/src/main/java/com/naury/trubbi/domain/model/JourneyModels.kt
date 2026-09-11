package com.naury.trubbi.domain.model

import androidx.annotation.StringRes

/**
 * 정차지의 성격 — 출발지인지, 상차인지, 하차인지.
 *
 * [ORIGIN] 은 여정이 시작되는 지점이라 상/하차 작업도, 대기·완료 미니 사이클도 없다.
 * 여정을 시작하는 순간 바로 완료 처리된다.
 *
 * @property labelRes 표시 이름 ("상차")
 * @property shortRes 노드/배지에 쓰는 한 글자 약칭 ("상")
 */
enum class StopKind(
    @StringRes override val labelRes: Int,
    @StringRes val shortRes: Int,
) : Labeled {
    ORIGIN(R.string.stop_kind_origin, R.string.stop_kind_origin_short),
    PICKUP(R.string.stop_kind_pickup, R.string.stop_kind_pickup_short),
    DROPOFF(R.string.stop_kind_dropoff, R.string.stop_kind_dropoff_short),
    ;

    /** 순수 출발지인지 — 작업/대기 단계가 없고 자동 완료된다. */
    val isOrigin: Boolean get() = this == ORIGIN

    companion object {
        /** DB 문자열 → enum. 모르는 값이면 [PICKUP]. */
        fun fromName(value: String?): StopKind =
            entries.firstOrNull { it.name == value } ?: PICKUP
    }
}

/**
 * 여정 중에 찍은 기록에 붙는 증빙 분류.
 *
 * 대부분 현재 정차지/단계에서 자동으로 정해진다. 파손·서류·계근표·영수증만
 * 사용자가 직접 선택한다.
 *
 * @property labelRes 표시 이름
 */
enum class EvidenceCategory(@StringRes override val labelRes: Int) : Labeled {
    PICKUP_BEFORE(R.string.evidence_pickup_before),
    PICKUP_IN_PROGRESS(R.string.evidence_pickup_in_progress),
    PICKUP_COMPLETED(R.string.evidence_pickup_completed),
    DROPOFF_BEFORE(R.string.evidence_dropoff_before),
    DROPOFF_IN_PROGRESS(R.string.evidence_dropoff_in_progress),
    DROPOFF_COMPLETED(R.string.evidence_dropoff_completed),
    DAMAGE(R.string.evidence_damage),
    DOCUMENT(R.string.evidence_document),
    WEIGHBRIDGE(R.string.evidence_weighbridge),
    RECEIPT(R.string.evidence_receipt),
    ETC(R.string.evidence_etc),
    ;

    companion object {
        /** DB 문자열 → enum. 모르는 값이면 null(증빙 분류 없음). */
        fun fromName(value: String?): EvidenceCategory? =
            entries.firstOrNull { it.name == value }
    }
}

/**
 * 여정에서 발생한 비용의 분류.
 *
 * @property labelRes 표시 이름
 */
enum class ExpenseCategory(@StringRes override val labelRes: Int) : Labeled {
    FUEL(R.string.expense_fuel),
    UREA(R.string.expense_urea),
    TOLL(R.string.expense_toll),
    PARKING(R.string.expense_parking),
    MEAL(R.string.expense_meal),
    LODGING_SHOWER(R.string.expense_lodging_shower),
    MAINTENANCE(R.string.expense_maintenance),
    CAR_WASH(R.string.expense_car_wash),
    LOADING_EXTRA(R.string.expense_loading_extra),
    ETC(R.string.expense_etc),
    ;

    companion object {
        /** DB 문자열 → enum. 모르는 값이면 [ETC]. */
        fun fromName(value: String?): ExpenseCategory =
            entries.firstOrNull { it.name == value } ?: ETC
    }
}

/**
 * 여정 중 발생한 이슈(파손·지연·분쟁 등)의 종류.
 *
 * @property labelRes 표시 이름
 */
enum class IssueType(@StringRes override val labelRes: Int) : Labeled {
    DAMAGE(R.string.issue_type_damage),
    CONTAMINATION(R.string.issue_type_contamination),
    QUANTITY_MISMATCH(R.string.issue_type_quantity_mismatch),
    REJECTION(R.string.issue_type_rejection),
    DELAY(R.string.issue_type_delay),
    SITE_PROBLEM(R.string.issue_type_site_problem),
    COST_PROBLEM(R.string.issue_type_cost_problem),
    ETC(R.string.issue_type_etc),
    ;

    companion object {
        /** DB 문자열 → enum. 모르는 값이면 [ETC]. */
        fun fromName(value: String?): IssueType =
            entries.firstOrNull { it.name == value } ?: ETC
    }
}

/**
 * 이슈 처리 상태.
 *
 * @property labelRes 표시 이름
 */
enum class IssueStatus(@StringRes override val labelRes: Int) : Labeled {
    OPEN(R.string.issue_status_open),
    IN_PROGRESS(R.string.issue_status_in_progress),
    RESOLVED(R.string.issue_status_resolved),
    ;

    companion object {
        /** DB 문자열 → enum. 모르는 값이면 [OPEN]. */
        fun fromName(value: String?): IssueStatus =
            entries.firstOrNull { it.name == value } ?: OPEN
    }
}

/**
 * 타임라인에 자동으로 쌓이는 여정 이벤트의 종류.
 *
 * @property labelRes 표시 이름
 */
enum class JourneyEventType(
    @StringRes override val labelRes: Int,
    /**
     * 인자를 받는 종류의 제목 서식. 받지 않으면 null.
     *
     * 인자는 저장할 때 번역하지 않고 열거형 이름으로만 남기고, 읽는 쪽에서 이 서식에
     * 끼워 넣는다 — 그래서 앱 언어를 바꾸면 지난 기록의 제목도 함께 바뀐다.
     */
    @StringRes private val titleFormat: Int? = null,
) : Labeled {
    JOURNEY_STARTED(R.string.event_journey_started),
    STOP_ADDED(R.string.event_stop_added, R.string.journey_event_stop_added),
    ARRIVED(R.string.event_arrived, R.string.journey_event_arrived),
    WORK_STARTED(R.string.event_work_started, R.string.journey_event_work_started),
    STOP_COMPLETED(R.string.event_stop_completed, R.string.journey_event_work_done),
    RECORD_LINKED(R.string.event_record_linked),
    EXPENSE_ADDED(R.string.event_expense_added, R.string.journey_event_expense),
    ISSUE_ADDED(R.string.event_issue_added, R.string.journey_event_issue),
    JOURNEY_COMPLETED(R.string.event_journey_completed),
    NOTE(R.string.event_note),
    ;

    /** 타임라인에 쓰는 제목 서식. 인자를 받지 않는 종류는 라벨이 곧 제목이다. */
    @get:StringRes
    val titleRes: Int get() = titleFormat ?: labelRes

    /** 제목에 인자가 들어가는 종류인지. */
    val takesArg: Boolean get() = titleFormat != null

    companion object {
        /** DB 문자열 → enum. 모르는 값이면 [NOTE]. */
        fun fromName(value: String?): JourneyEventType =
            entries.firstOrNull { it.name == value } ?: NOTE
    }
}

/**
 * 현재 정차지의 진행 단계.
 *
 * 저장하지 않고 정차지의 도착/작업/완료 시각에서 매번 계산한다.
 *
 * @property labelRes 표시 이름
 */
enum class StopPhase(@StringRes override val labelRes: Int) : Labeled {
    MOVING(R.string.stop_phase_moving),
    WAITING(R.string.stop_phase_waiting),
    WORKING(R.string.stop_phase_working),
    DONE(R.string.stop_phase_done),
}

/**
 * 여정을 완료하기 전에 확인하는 필수 증빙 항목.
 *
 * 리포지토리는 무엇이 빠졌는지만 알려 주고, 문구 조립은 화면에 맡긴다.
 *
 * @property labelRes 사용자에게 보여줄 항목 이름
 */
enum class MissingEvidence(@StringRes override val labelRes: Int) : Labeled {
    PICKUP_PHOTO(R.string.missing_evidence_pickup),
    DROPOFF_PHOTO(R.string.missing_evidence_dropoff),
    ISSUE_PHOTO(R.string.missing_evidence_issue),
}

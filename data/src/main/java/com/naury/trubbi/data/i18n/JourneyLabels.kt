package com.naury.trubbi.data.i18n

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.naury.trubbi.core.i18n.label
import com.naury.trubbi.core.ui.StringProvider
import com.naury.trubbi.data.R
import com.naury.trubbi.data.local.JourneyEventEntity
import com.naury.trubbi.data.local.JourneyStopEntity
import com.naury.trubbi.data.local.JourneyWithStops
import com.naury.trubbi.domain.model.EvidenceCategory
import com.naury.trubbi.domain.model.ExpenseCategory
import com.naury.trubbi.domain.model.IssueType
import com.naury.trubbi.domain.model.JourneyEventType
import com.naury.trubbi.domain.model.StopKind

/**
 * 여정·정차지 엔티티를 화면 문구로 바꾸는 확장 모음.
 *
 * 엔티티 자체는 리소스를 모르게 두고(순수 데이터), 언어가 필요한 표시 문구만 여기서 만든다.
 */

/** 정차지 표시 이름. 이름·주소가 모두 비면 "상차지" 같은 기본 문구로 채운다. */
fun JourneyStopEntity.displayName(context: Context): String =
    rawName ?: context.getString(R.string.stop_default_name, context.getString(kindEnum.labelRes))

/** 정차지 표시 이름 — 뷰모델·리포지토리용. */
fun JourneyStopEntity.displayName(strings: StringProvider): String =
    rawName ?: strings[R.string.stop_default_name, strings[kindEnum.labelRes]]

/** 정차지 표시 이름 — 컴포저블용. */
@Composable
fun JourneyStopEntity.displayName(): String = displayName(LocalContext.current)

/**
 * 여정 한 줄 요약 — "부산 신항 → 성남 센터 외 1곳".
 *
 * 정차지가 없으면 여정 제목, 그것도 없으면 "새 여정".
 */
fun JourneyWithStops.routeLabel(context: Context): String {
    val ordered = orderedStops
    if (ordered.isEmpty()) return journey.title ?: context.getString(R.string.journey_untitled)
    val first = ordered.first().displayName(context)
    if (ordered.size == 1) return first
    val last = ordered.last().displayName(context)
    val extra = ordered.size - 2
    return if (extra > 0) {
        context.getString(R.string.journey_route_more, first, last, extra)
    } else {
        context.getString(R.string.journey_route, first, last)
    }
}

/** 여정 한 줄 요약 — 뷰모델·리포지토리용. */
fun JourneyWithStops.routeLabel(strings: StringProvider): String {
    val ordered = orderedStops
    if (ordered.isEmpty()) return journey.title ?: strings[R.string.journey_untitled]
    val first = ordered.first().displayName(strings)
    if (ordered.size == 1) return first
    val last = ordered.last().displayName(strings)
    val extra = ordered.size - 2
    return if (extra > 0) {
        strings[R.string.journey_route_more, first, last, extra]
    } else {
        strings[R.string.journey_route, first, last]
    }
}

/** 여정 한 줄 요약 — 컴포저블용. */
@Composable
fun JourneyWithStops.routeLabel(): String = routeLabel(LocalContext.current)

/**
 * 타임라인 이벤트 제목.
 *
 * 버전 7 이전에 저장된 행에는 완성된 문구가 들어 있어 그대로 쓴다. 그 뒤 행은 비어 있고,
 * 종류([JourneyEventType])와 번역하지 않은 인자만 저장돼 있어 여기서 조립한다.
 * 그래서 앱 언어를 바꾸면 지난 기록의 제목도 함께 바뀐다.
 */
fun JourneyEventEntity.displayTitle(strings: StringProvider): String {
    if (title.isNotBlank()) return title
    val kind = JourneyEventType.fromName(type)
    val arg = titleArg?.let { kind.argLabel(it, strings) }
    // titleRes 는 인자를 받지 않는 종류에는 라벨을 돌려준다. 서식 여부는 takesArg 로 본다.
    val format = kind.titleRes.takeIf { kind.takesArg }
    return when {
        format != null && titleAmount != null && arg != null ->
            strings[format, arg, "%,d".format(titleAmount)]

        format != null && arg != null -> strings[format, arg]
        arg != null -> arg
        else -> strings[kind.labelRes]
    }
}

/** 타임라인 이벤트 제목 — [Context] 만 있는 곳. */
fun JourneyEventEntity.displayTitle(context: Context): String =
    displayTitle(StringProvider(context.applicationContext))

/**
 * 저장된 인자 이름을 표시 문구로 바꾼다.
 *
 * 종류마다 읽는 열거형이 다르다. 모르는 값이면 저장된 이름을 그대로 돌려줘, 열거형에서
 * 항목이 빠져도 제목이 통째로 사라지지는 않게 한다.
 */
private fun JourneyEventType.argLabel(arg: String, strings: StringProvider): String = when (this) {
    JourneyEventType.STOP_ADDED,
    JourneyEventType.ARRIVED,
    JourneyEventType.WORK_STARTED,
    JourneyEventType.STOP_COMPLETED,
    -> StopKind.entries.firstOrNull { it.name == arg }?.label(strings)

    JourneyEventType.ISSUE_ADDED -> IssueType.entries.firstOrNull { it.name == arg }?.label(strings)
    JourneyEventType.EXPENSE_ADDED -> ExpenseCategory.entries.firstOrNull { it.name == arg }?.label(strings)
    JourneyEventType.RECORD_LINKED -> EvidenceCategory.entries.firstOrNull { it.name == arg }?.label(strings)
    else -> null
} ?: arg

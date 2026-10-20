package com.naury.trubbi.domain.journey

import com.naury.trubbi.R
import com.naury.trubbi.core.location.CapturedLocation
import com.naury.trubbi.core.location.PlaceNames
import com.naury.trubbi.core.ui.StringProvider
import com.naury.trubbi.core.weather.Weather
import com.naury.trubbi.core.weather.WeatherCondition
import com.naury.trubbi.data.repo.JourneyRepository
import com.naury.trubbi.domain.model.StopKind
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 여정 안내 음성으로 읽을 문장을 만든다.
 *
 * 날씨·위치·운전자 이름을 전부 파라미터로 받는다. 기기 상태를 직접 읽지 않으므로
 * 문구 조합 규칙만 단위 테스트로 검증할 수 있다.
 *
 * @property strings 문구 리소스 조회
 */
@Singleton
class JourneyAnnouncer @Inject constructor(
    private val strings: StringProvider,
) {
    /**
     * 여정 시작 오버레이에서 읽는 짧은 인사.
     *
     * @param driverName 저장된 운전자 이름. 비어 있으면 호칭을 뺀다
     * @param weather 현재 날씨. null 이면 날씨 기반 안전 문구를 뺀다
     * @param at 안전 문구의 시간대 판단 기준 (테스트에서 고정할 수 있도록 파라미터로 둔다)
     */
    fun startMessage(
        driverName: String?,
        weather: Weather?,
        at: LocalTime = LocalTime.now(),
    ): String = strings[R.string.start_message, namePrefix(driverName), safetyTip(weather, at)]

    /**
     * 여정을 시작할 때 읽어 주는 전체 안내 — 경로, 경유지, 현재 위치, 날씨를 한 문장으로 합친다.
     *
     * 각 조각은 값이 없으면 통째로 빠진다. 출발지만 있거나 도착지만 있어도 문장이 성립한다.
     *
     * @param stops 시작 시점의 정차지 목록. 비어 있으면 경로 부분이 빠진다
     * @param driverName 저장된 운전자 이름. 비어 있으면 호칭을 뺀다
     * @param weather 현재 날씨. null 이면 날씨 부분이 빠진다
     * @param here 현재 위치. null 이거나 주소가 없으면 위치 부분이 빠진다
     */
    fun startAnnouncement(
        stops: List<JourneyRepository.StopDraft>,
        driverName: String?,
        weather: Weather?,
        here: CapturedLocation?,
    ): String {
        val origin = nameOf(stops.firstOrNull { it.kind == StopKind.ORIGIN || it.kind == StopKind.PICKUP })
        val destination = nameOf(stops.lastOrNull { it.kind == StopKind.DROPOFF })
        val route = when {
            origin != null && destination != null ->
                strings[R.string.announce_route_from_to, origin, destination]

            destination != null -> strings[R.string.announce_route_to, destination]
            origin != null -> strings[R.string.announce_route_from, origin]
            else -> ""
        }
        val via = stops.drop(1).dropLast(1).mapNotNull { nameOf(it) }
            .takeIf { it.isNotEmpty() }
            ?.let { strings[R.string.announce_via, it.joinToString(", ")] }
            .orEmpty()
        val place = (here?.region ?: here?.address?.let(::shortAddress))?.let { strings[R.string.announce_location, it] }.orEmpty()
        val sky = weather
            ?.let { strings[R.string.announce_weather, strings[it.labelRes], it.temperatureC] }
            .orEmpty()

        return strings[R.string.announce_start, route + via, place, sky, namePrefix(driverName)]
    }

    /**
     * 시간대와 날씨에 따른 안전 문구. 해당하는 조건이 없으면 빈 문자열.
     *
     * 날씨 조건을 시간대보다 먼저 본다. 비 오는 새벽에는 빗길 문구를 읽는다.
     */
    fun safetyTip(weather: Weather?, at: LocalTime = LocalTime.now()): String {
        val res = when {
            weather?.condition == WeatherCondition.RAIN -> R.string.safety_rain
            weather?.condition == WeatherCondition.SNOW -> R.string.safety_snow
            weather?.condition == WeatherCondition.FOG -> R.string.safety_fog
            at.hour in 0..4 -> R.string.safety_late_night
            at.hour in 22..23 -> R.string.safety_night
            else -> return ""
        }
        return strings[res]
    }

    /**
     * 전체 주소에서 읽어 줄 동네 이름만 남긴다.
     *
     * 예: "경기도 성남시 분당구 삼평동 123" → "분당구 삼평동".
     */
    fun shortAddress(address: String): String = PlaceNames.region(address) ?: address

    private fun namePrefix(driverName: String?): String =
        driverName?.takeIf { it.isNotBlank() }?.let { strings[R.string.name_prefix, it] }.orEmpty()

    private fun nameOf(draft: JourneyRepository.StopDraft?): String? =
        draft?.name?.takeIf { it.isNotBlank() }
}

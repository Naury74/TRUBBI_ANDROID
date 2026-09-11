package com.naury.trubbi.core.weather

import androidx.annotation.StringRes
import com.naury.trubbi.core.R

/** 애니메이션 배경과 아이콘을 고르기 위한 굵직한 날씨 구분. */
enum class WeatherCondition { CLEAR, CLOUDS, RAIN, SNOW, THUNDER, FOG }

/**
 * 현재 날씨 한 덩이.
 *
 * @property temperatureC 섭씨 기온
 * @property code Open-Meteo 의 WMO 날씨 코드 (원본 보존용)
 * @property labelRes "맑음", "소나기" 처럼 화면에 쓰는 문구
 * @property place 역지오코딩한 지명. 아직 모르면 null
 */
data class Weather(
    val temperatureC: Int,
    val code: Int,
    val condition: WeatherCondition,
    val isDay: Boolean,
    @StringRes val labelRes: Int,
    val place: String? = null,
)

/**
 * WMO 날씨 코드(Open-Meteo)를 앱이 쓰는 구분과 문구로 옮긴다.
 *
 * 코드 범위는 WMO 4677 표를 따른다. 표에 없는 값은 흐림으로 둔다.
 */
fun weatherFromCode(code: Int, isDay: Boolean, tempC: Int, place: String? = null): Weather {
    val (condition, labelRes) = when (code) {
        0 -> WeatherCondition.CLEAR to if (isDay) R.string.weather_clear else R.string.weather_clear_night
        1, 2 -> WeatherCondition.CLOUDS to R.string.weather_partly_cloudy
        3 -> WeatherCondition.CLOUDS to R.string.weather_cloudy
        45, 48 -> WeatherCondition.FOG to R.string.weather_fog
        in 51..57 -> WeatherCondition.RAIN to R.string.weather_drizzle
        in 61..67 -> WeatherCondition.RAIN to R.string.weather_rain
        in 71..77 -> WeatherCondition.SNOW to R.string.weather_snow
        in 80..82 -> WeatherCondition.RAIN to R.string.weather_showers
        85, 86 -> WeatherCondition.SNOW to R.string.weather_snow
        95, 96, 99 -> WeatherCondition.THUNDER to R.string.weather_thunder
        else -> WeatherCondition.CLOUDS to R.string.weather_cloudy
    }
    return Weather(tempC, code, condition, isDay, labelRes, place)
}

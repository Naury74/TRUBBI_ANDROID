package com.naury.trubbi.core.weather

import com.naury.trubbi.core.location.LocationProvider
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 현재 위치의 날씨를 Open-Meteo 에서 가져온다.
 *
 * 키가 없어도 되고 상업적 사용이 허용돼 골랐다. 호출 한 곳뿐이라 HTTP 라이브러리를 더 붙이지 않고
 * `HttpURLConnection` + `org.json` 으로 끝낸다.
 */
@Singleton
class WeatherRepository @Inject constructor(
    private val locationProvider: LocationProvider,
) {
    /**
     * 현재 위치의 날씨. 못 가져오면 null.
     *
     * 측위 실패와 조회 실패를 모두 삼킨다. 날씨는 홈 상단 표시와 안내 문구에만 쓰는 부가 정보라
     * 없으면 그 부분만 빠지면 되고, 실패를 올려 보내면 호출부마다 같은 무시 코드를 써야 한다.
     * 기록·여정 저장은 날씨와 무관하게 진행된다.
     */
    suspend fun current(): Weather? = withContext(Dispatchers.IO) {
        val loc = runCatching { locationProvider.capture() }.getOrNull() ?: return@withContext null
        runCatching { fetch(loc.latitude, loc.longitude, loc.regionLabel ?: loc.address) }.getOrNull()
    }

    private fun fetch(lat: Double, lng: Double, place: String?): Weather? {
        val url = URL(
            String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,is_day&timezone=auto",
                lat,
                lng,
            ),
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 6000
            requestMethod = "GET"
        }
        return try {
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val current = JSONObject(body).optJSONObject("current") ?: return null
            val temp = current.optDouble("temperature_2m", Double.NaN)
            if (temp.isNaN()) return null
            weatherFromCode(
                code = current.optInt("weather_code", 3),
                isDay = current.optInt("is_day", 1) == 1,
                tempC = temp.roundToInt(),
                place = place,
            )
        } finally {
            conn.disconnect()
        }
    }
}

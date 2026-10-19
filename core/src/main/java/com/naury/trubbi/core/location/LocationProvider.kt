package com.naury.trubbi.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** 잡아 온 위치 — 좌표와, 가능하면 역지오코딩한 주소. */
data class CapturedLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String?,
)

/**
 * 장소 검색 결과 한 건. 입력·음성으로 들어온 장소명을 확인시켜 줄 때 쓴다.
 *
 * @property category 업종(예: "물류센터", "주유소"). 주소 검색 결과나 Geocoder 결과에는 없다
 * @property distanceM 검색 기준 위치에서의 직선거리(m). 기준 위치를 넘기지 않았으면 null
 */
data class PlaceCandidate(
    val name: String,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val category: String? = null,
    val distanceM: Int? = null,
)

/**
 * 현재 위치와 장소 검색을 담당한다.
 *
 * 좌표는 Fused Location 을 먼저 쓰고, Play 서비스가 없는 기기를 위해 [LocationManager] 로 내려간다.
 * 주소는 카카오 로컬 API 가 있으면 그쪽을(한글 도로명이 정확하다), 없으면 [Geocoder] 로 폴백한다.
 * 권한이 없거나 측위가 안 되면 항상 null 을 돌려주므로, 호출부는 위치 없이도 저장을 계속할 수 있다.
 */
@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keys: LocationKeys,
) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private val kakaoKey: String get() = keys.kakaoRest
    private val kakaoEnabled: Boolean get() = kakaoKey.isNotBlank()

    /** 현재 위치와 주소. 못 구하면 null. */
    suspend fun capture(): CapturedLocation? {
        if (!hasPermission()) return null
        val location = currentLocation() ?: return null
        val address = withContext(Dispatchers.IO) {
            // 카카오 역지오코딩이 한글 도로명 주소를 더 정확히 준다. 키가 없으면 Geocoder.
            kakaoCoord2Address(location.latitude, location.longitude)
                ?: runCatching { reverseGeocode(location.latitude, location.longitude) }.getOrNull()
        }
        return CapturedLocation(location.latitude, location.longitude, address)
    }

    @Suppress("MissingPermission")
    private suspend fun currentLocation(): Location? {
        // GPS·와이파이·센서를 합치는 Fused 가 가장 빠르고 정확하다.
        fusedCurrent()?.let { return it }
        // Play 서비스가 없는 기기용 폴백 — 단발 측위 + 마지막 위치 중 정확한 쪽.
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val fresh = withTimeoutOrNull(8000) { requestSingle(lm) }
        val lastKnown = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .minByOrNull { it.accuracy.takeIf { a -> a > 0f } ?: Float.MAX_VALUE }
        return when {
            fresh == null -> lastKnown
            lastKnown == null -> fresh
            lastKnown.accuracy in 0.1f..fresh.accuracy -> lastKnown
            else -> fresh
        }
    }

    /** Fused 측위 — 새 좌표를 먼저 받아 보고, 안 되면 마지막 위치. Play 서비스가 없으면 null. */
    @Suppress("MissingPermission")
    private suspend fun fusedCurrent(): Location? = runCatching {
        val client = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
        val cts = com.google.android.gms.tasks.CancellationTokenSource()
        // 실패를 단계마다 삼키는 것은 폴백 사슬이기 때문이다.
        // 새 측위(8초 제한) → 마지막 위치 → (호출부에서) LocationManager 순으로 내려간다.
        // 여기서 예외를 올리면 Play 서비스가 없는 기기에서 다음 경로를 시도할 수 없다.
        val fresh = withTimeoutOrNull(8000) {
            runCatching {
                client.getCurrentLocation(
                    com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                    cts.token,
                ).await()
            }.getOrNull()
        }
        if (fresh == null) cts.cancel()
        fresh ?: runCatching { client.lastLocation.await() }.getOrNull()
    }.getOrNull()

    @Suppress("MissingPermission")
    private suspend fun requestSingle(lm: LocationManager): Location? = suspendCancellableCoroutine { cont ->
        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> {
                if (cont.isActive) cont.resume(null)
                return@suspendCancellableCoroutine
            }
        }
        val executor = ContextCompat.getMainExecutor(context)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = android.os.CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                lm.getCurrentLocation(provider, signal, executor) { loc ->
                    if (cont.isActive) cont.resume(loc)
                }
            } else {
                if (cont.isActive) cont.resume(null)
            }
        }.onFailure { if (cont.isActive) cont.resume(null) }
    }

    /**
     * Multi-result place search: 지명/건물명/주소 후보들을 보여주고 고르게 하는 명칭 보정용.
     *
     * 카카오맵 앱과 동등한 결과를 내려면 **키워드(상호/건물명) 검색**과 **주소(지번/도로명) 검색**을
     * 모두 호출해 합쳐야 한다. 키워드 검색만으로는 "금산리 1457-14" 같은 순수 지번 주소가 안 나온다.
     * 음성으로 들어온 숫자 발음("일사오칠 다시 일사")도 "1457-14"로 정규화해 함께 질의한다.
     * 카카오 키가 없으면 Geocoder로 폴백.
     */
    suspend fun searchPlaces(
        query: String,
        max: Int = 6,
        nearLat: Double? = null,
        nearLng: Double? = null,
    ): List<PlaceCandidate> = withContext(Dispatchers.IO) {
        val found = searchPlacesRaw(query, max, nearLat, nearLng)
        // 결과 순서는 이름 정확도를 따르고, 거리는 표시용으로만 붙인다. 가까운 순으로 바꾸면
        // "부산신항"처럼 먼 곳을 찾을 때 근처의 엉뚱한 상호가 위로 올라온다.
        if (nearLat == null || nearLng == null) {
            found
        } else {
            found.map { p -> if (p.distanceM != null) p else p.copy(distanceM = distanceM(nearLat, nearLng, p.latitude, p.longitude)) }
        }
    }

    private fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Int {
        val out = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, out)
        return out[0].toInt()
    }

    private fun searchPlacesRaw(query: String, max: Int, nearLat: Double?, nearLng: Double?): List<PlaceCandidate> {
        if (query.isBlank()) return emptyList()
        if (!kakaoEnabled) return geocoderSearch(query, max)
        val near = if (nearLat != null && nearLng != null) "&x=$nearLng&y=$nearLat" else ""

        val normalized = normalizeAddressNumbers(query)
        // 숫자/행정구역 접미사가 보이면 주소 검색을 먼저(상단에) 노출한다.
        val addressLike = normalized.any { it.isDigit() } ||
            listOf("리", "동", "읍", "면", "로", "길", "번지", "가").any { query.contains(it) }

        // 같은 좌표 후보는 한 번만(키워드/주소가 동일 장소를 중복 반환하는 경우 정리).
        val out = LinkedHashMap<String, PlaceCandidate>()
        fun add(list: List<PlaceCandidate>?) {
            list?.forEach { p -> out.putIfAbsent("%.6f,%.6f".format(p.latitude, p.longitude), p) }
        }
        if (addressLike) {
            // 정규화된 지번/도로명 주소를 최우선으로. (원문은 한글 숫자라 주소로는 무의미 → 제외)
            add(kakaoAddressSearch(normalized, max))
            add(kakaoSearch(normalized, max, near))
            if (normalized != query) add(kakaoSearch(query, max, near))
        } else {
            add(kakaoSearch(query, max, near))
            add(kakaoAddressSearch(query, max))
        }
        val merged = out.values.toList()
        if (merged.isNotEmpty()) return merged.take(max)
        return geocoderSearch(normalized, max).ifEmpty { geocoderSearch(query, max) }
    }

    /**
     * 음성 받아쓰기로 들어오는 한글 숫자 발음을 주소용 숫자로 정규화한다.
     *  - "일사오칠" → "1457", "일사" → "14" (한 토막이 전부 한자리 숫자 음절일 때만; "이마트" 등은 보존)
     *  - "1457 다시 14" / "1457 에 14" → "1457-14" (지번 '-' 받아쓰기 보정)
     *  - "1457 14" 처럼 번지 숫자가 띄어 들어와도 → "1457-14" (본번-부번을 한 지번으로)
     */
    private fun normalizeAddressNumbers(query: String): String {
        val sino = mapOf(
            '공' to '0', '영' to '0', '일' to '1', '이' to '2', '삼' to '3', '사' to '4',
            '오' to '5', '육' to '6', '륙' to '6', '칠' to '7', '팔' to '8', '구' to '9',
        )
        val mapped = query.trim().split(Regex("\\s+")).joinToString(" ") { tok ->
            if (tok.length >= 2 && tok.all { it in sino }) tok.map { sino[it] }.joinToString("") else tok
        }
        return mapped
            .replace(Regex("(\\d)\\s*(?:다시|에|의)\\s*(\\d)"), "$1-$2")
            .replace(Regex("(\\d)\\s+(\\d)"), "$1-$2")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * 표시용 주소 축약 — 도/시 같은 상위 행정구역을 떼고 동/리/로/길 + 번지부터 보여준다.
     * 예: "경남 양산시 동면 금산리 1457-14" → "금산리 1457-14", "서울 강남구 테헤란로 152" → "테헤란로 152".
     * 건물명/상호가 없는 주소 결과의 '이름'으로 사용(인식된 문장을 이름으로 쓰지 않기 위함).
     */
    private fun abbreviateAddress(addr: String): String {
        val tokens = addr.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.size <= 2) return addr.trim()
        val anchor = tokens.indexOfLast { t ->
            t.endsWith("동") || t.endsWith("리") || t.endsWith("로") || t.endsWith("길") || t.endsWith("가")
        }
        return if (anchor in 0 until tokens.size) {
            tokens.subList(anchor, tokens.size).joinToString(" ")
        } else {
            tokens.takeLast(2).joinToString(" ")
        }
    }

    private fun geocoderSearch(query: String, max: Int): List<PlaceCandidate> =
        runCatching {
            val geocoder = Geocoder(context, Locale.KOREAN)

            @Suppress("DEPRECATION")
            val results = geocoder.getFromLocationName(query, max) ?: return@runCatching emptyList()
            results.mapNotNull { a ->
                val address = a.getAddressLine(0)?.removePrefix("대한민국 ")?.trim()?.ifBlank { null }
                val feature = a.featureName?.takeIf { it.isNotBlank() && !it.all { ch -> ch.isDigit() || ch == '-' } }
                // 건물명/상호가 없으면 인식 문장이 아니라 '축약 주소'를 이름으로.
                val name = feature ?: address?.let { abbreviateAddress(it) } ?: return@mapNotNull null
                PlaceCandidate(
                    name = name,
                    address = address,
                    latitude = a.latitude,
                    longitude = a.longitude,
                )
            }.distinctBy { it.address ?: "${it.latitude},${it.longitude}" }
        }.getOrElse { emptyList() }

    /** 카카오 로컬 키워드 검색 — 건물명·상호로 좌표와 주소를 찾는다. 키가 없으면 null. */
    private fun kakaoSearch(query: String, max: Int, near: String = ""): List<PlaceCandidate>? {
        if (!kakaoEnabled) return null
        return runCatching {
            val url = URL(
                "https://dapi.kakao.com/v2/local/search/keyword.json?size=$max$near&query=" +
                    URLEncoder.encode(query, "UTF-8"),
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "KakaoAK $kakaoKey")
                connectTimeout = 5000
                readTimeout = 5000
            }
            if (conn.responseCode != 200) return@runCatching null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val docs = JSONObject(body).optJSONArray("documents") ?: return@runCatching emptyList<PlaceCandidate>()
            (0 until docs.length()).mapNotNull { i ->
                val d = docs.getJSONObject(i)
                val lat = d.optString("y").toDoubleOrNull() ?: return@mapNotNull null
                val lng = d.optString("x").toDoubleOrNull() ?: return@mapNotNull null
                val road = d.optString("road_address_name").ifBlank { null }
                val jibun = d.optString("address_name").ifBlank { null }
                // 상호명이 없으면 인식 문장이 아니라 '축약 주소'를 이름으로.
                val name = d.optString("place_name").ifBlank { (road ?: jibun)?.let { abbreviateAddress(it) } }
                    ?: return@mapNotNull null
                PlaceCandidate(
                    name = name,
                    address = road ?: jibun,
                    latitude = lat,
                    longitude = lng,
                    // "서비스,산업 > 물류,유통 > 물류센터" 에서 마지막 칸만 보여 준다.
                    category = d.optString("category_name").substringAfterLast('>').trim().ifBlank { null },
                    distanceM = d.optString("distance").toIntOrNull(),
                )
            }
        }.getOrNull()
    }

    /**
     * Kakao Local 주소 검색 — 지번/도로명 주소로 좌표를 찾는다(키워드 검색이 못 잡는 순수 주소).
     * 예: "경남 양산시 동면 금산리 1457-14". 키 없으면 null.
     */
    private fun kakaoAddressSearch(query: String, max: Int): List<PlaceCandidate>? {
        if (!kakaoEnabled) return null
        return runCatching {
            val url = URL(
                "https://dapi.kakao.com/v2/local/search/address.json?size=$max&query=" +
                    URLEncoder.encode(query, "UTF-8"),
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "KakaoAK $kakaoKey")
                connectTimeout = 5000
                readTimeout = 5000
            }
            if (conn.responseCode != 200) return@runCatching null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val docs = JSONObject(body).optJSONArray("documents") ?: return@runCatching emptyList<PlaceCandidate>()
            (0 until docs.length()).mapNotNull { i ->
                val d = docs.getJSONObject(i)
                val lat = d.optString("y").toDoubleOrNull() ?: return@mapNotNull null
                val lng = d.optString("x").toDoubleOrNull() ?: return@mapNotNull null
                val road = d.optJSONObject("road_address")
                val building = road?.optString("building_name")?.ifBlank { null }
                val roadName = road?.optString("address_name")?.ifBlank { null }
                val jibun = d.optJSONObject("address")?.optString("address_name")?.ifBlank { null }
                val full = d.optString("address_name").ifBlank { null }
                // 제목: 건물명 우선, 없으면 '축약 주소'(인식 문장은 절대 쓰지 않음). 보조: 전체 주소.
                val fullAddr = roadName ?: jibun ?: full ?: return@mapNotNull null
                val primary = building ?: abbreviateAddress(fullAddr)
                val secondary = fullAddr.takeIf { it != primary }
                PlaceCandidate(name = primary, address = secondary, latitude = lat, longitude = lng)
            }
        }.getOrNull()
    }

    /** 카카오 좌표 → 도로명 주소. 키가 없거나 실패하면 null. */
    private fun kakaoCoord2Address(lat: Double, lng: Double): String? {
        if (!kakaoEnabled) return null
        return runCatching {
            val url = URL("https://dapi.kakao.com/v2/local/geo/coord2address.json?x=$lng&y=$lat")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "KakaoAK $kakaoKey")
                connectTimeout = 5000
                readTimeout = 5000
            }
            if (conn.responseCode != 200) return@runCatching null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val docs = JSONObject(body).optJSONArray("documents") ?: return@runCatching null
            if (docs.length() == 0) return@runCatching null
            val d = docs.getJSONObject(0)
            val road = d.optJSONObject("road_address")?.optString("address_name")?.ifBlank { null }
            val jibun = d.optJSONObject("address")?.optString("address_name")?.ifBlank { null }
            road ?: jibun
        }.getOrNull()
    }

    /**
     * 장소명·주소를 좌표로 바꾼다.
     *
     * 좌표 없이 이름·주소만 있는 정차지에 좌표를 채워 도착 감지와 지도 표시를 가능하게 한다.
     */
    suspend fun geocode(query: String): CapturedLocation? = withContext(Dispatchers.IO) {
        // 키워드·주소를 모두 조회하는 통합 검색을 재사용한다. 지번 주소도 좌표가 반환된다.
        searchPlaces(query, 1).firstOrNull()?.let {
            return@withContext CapturedLocation(it.latitude, it.longitude, it.address)
        }
        legacyGeocode(query)
    }

    private fun legacyGeocode(query: String): CapturedLocation? =
        runCatching {
            val geocoder = Geocoder(context, Locale.KOREAN)

            @Suppress("DEPRECATION")
            val a = geocoder.getFromLocationName(query, 1)?.firstOrNull() ?: return@runCatching null
            CapturedLocation(
                latitude = a.latitude,
                longitude = a.longitude,
                address = a.getAddressLine(0)?.removePrefix("대한민국 ")?.trim()?.ifBlank { null },
            )
        }.getOrNull()

    private fun reverseGeocode(lat: Double, lng: Double): String? {
        val geocoder = Geocoder(context, Locale.KOREAN)

        @Suppress("DEPRECATION")
        val results = geocoder.getFromLocation(lat, lng, 1) ?: return null
        val a = results.firstOrNull() ?: return null
        val line = a.getAddressLine(0)
        return line?.removePrefix("대한민국 ")?.trim()?.ifBlank { null }
    }
}

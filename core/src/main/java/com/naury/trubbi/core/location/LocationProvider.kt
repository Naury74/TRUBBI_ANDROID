package com.naury.trubbi.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
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

/**
 * 잡아 온 위치 — 좌표와, 가능하면 역지오코딩한 주소.
 *
 * @property region 행정동 기준 동네 이름("중구 명동"). 도로명 주소에는 동이 없어 따로 받는다
 * @property building 건물 이름(있으면). 정차지 이름으로 쓰면 주소보다 알아보기 쉽다
 */
data class CapturedLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String?,
    val region: String? = null,
    val building: String? = null,
) {
    /** 홈 헤더·안내에 쓰는 동네 이름. */
    val regionLabel: String? get() = region ?: PlaceNames.region(address)

    /** 출발지·정차지 이름으로 쓰는 짧은 이름. 건물 이름이 있으면 그것. */
    val placeLabel: String? get() = building ?: PlaceNames.street(address)
}

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
    private val live: LiveLocation,
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
        // 여정 추적 중이면 몇 초 전에 받은 정확한 좌표가 있다. 새로 측위하면 몇 초 늦어 그사이 차가 움직인다.
        val location = live.fresh()?.let { LatLng(it.latitude, it.longitude) }
            ?: currentLocation()?.let { LatLng(it.latitude, it.longitude) }
            ?: return null
        return withContext(Dispatchers.IO) {
            // 카카오 역지오코딩이 한글 도로명 주소를 더 정확히 준다. 키가 없으면 Geocoder.
            val kakao = kakaoCoord2Address(location.latitude, location.longitude)
            val address = kakao?.first ?: runCatching { reverseGeocode(location.latitude, location.longitude) }.getOrNull()
            CapturedLocation(
                latitude = location.latitude,
                longitude = location.longitude,
                address = address,
                region = kakaoRegion(location.latitude, location.longitude),
                building = kakao?.second,
            )
        }
    }

    @Suppress("MissingPermission")
    private suspend fun currentLocation(): Location? {
        // GPS·와이파이·센서를 합치는 Fused 가 가장 빠르고 정확하다.
        fusedCurrent()?.let { return it }
        // Play 서비스가 없는 기기용 폴백 — 단발 측위 + 마지막 위치 중 정확한 쪽.
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val fresh = withTimeoutOrNull(8000) { requestSingle(lm) }
        // 마지막 위치는 몇 시간·며칠 전 것일 수 있다. 최근 것만 쓰고, 그중 정확한 쪽을 고른다.
        // (예전에는 나이를 보지 않아 어제 GPS 좌표가 오늘 네트워크 좌표를 이겼다.)
        val lastKnown = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .filter(::isRecent)
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
        fresh ?: runCatching { client.lastLocation.await() }.getOrNull()?.takeIf(::isRecent)
    }.getOrNull()

    /** 마지막 위치를 지금 위치로 써도 될 만큼 최근인지. */
    private fun isRecent(location: Location): Boolean =
        (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000 <= LAST_KNOWN_MAX_AGE_MS

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

    /** 건물명·상호가 없는 결과의 이름. 인식된 문장 대신 찾아갈 수 있는 짧은 주소를 쓴다. */
    private fun abbreviateAddress(addr: String): String = PlaceNames.street(addr) ?: addr.trim()

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

    /**
     * 좌표의 주소·동네·건물 이름. 위치를 손으로 고를 때 고른 곳이 어디인지 보여 주는 데 쓴다.
     *
     * @return 주소를 못 구해도 좌표는 담아 돌려준다
     */
    suspend fun describe(lat: Double, lng: Double): CapturedLocation = withContext(Dispatchers.IO) {
        val kakao = kakaoCoord2Address(lat, lng)
        CapturedLocation(
            latitude = lat,
            longitude = lng,
            address = kakao?.first ?: runCatching { reverseGeocode(lat, lng) }.getOrNull(),
            region = kakaoRegion(lat, lng),
            building = kakao?.second,
        )
    }

    /** 카카오 좌표 → (도로명 주소, 건물 이름). 키가 없거나 실패하면 null. */
    private fun kakaoCoord2Address(lat: Double, lng: Double): Pair<String, String?>? {
        val d = kakaoGet("https://dapi.kakao.com/v2/local/geo/coord2address.json?x=$lng&y=$lat")?.firstOrNull() ?: return null
        val road = d.optJSONObject("road_address")
        val roadName = road?.optString("address_name")?.ifBlank { null }
        val jibun = d.optJSONObject("address")?.optString("address_name")?.ifBlank { null }
        val building = road?.optString("building_name")?.trim()?.ifBlank { null }
        return (roadName ?: jibun ?: return null) to building
    }

    /**
     * 카카오 좌표 → 행정동 기준 동네 이름("중구 명동"). 키가 없거나 실패하면 null.
     *
     * 도로명 주소에는 동이 없어 홈 헤더에 '중구'만 나오던 것을 바로잡는다.
     */
    private fun kakaoRegion(lat: Double, lng: Double): String? {
        val docs = kakaoGet("https://dapi.kakao.com/v2/local/geo/coord2regioncode.json?x=$lng&y=$lat") ?: return null
        // H = 행정동(사람들이 부르는 이름), B = 법정동. 행정동이 없으면 법정동.
        val d = docs.firstOrNull { it.optString("region_type") == "H" } ?: docs.firstOrNull() ?: return null
        val second = d.optString("region_2depth_name").trim()
        val third = d.optString("region_3depth_name").trim()
        return listOf(second, third).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }
    }

    /** 카카오 로컬 GET → documents. 키가 없거나 실패하면 null. */
    private fun kakaoGet(url: String): List<JSONObject>? {
        if (!kakaoEnabled) return null
        return runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "KakaoAK $kakaoKey")
                connectTimeout = 5000
                readTimeout = 5000
            }
            if (conn.responseCode != 200) return@runCatching null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val docs = JSONObject(body).optJSONArray("documents") ?: return@runCatching emptyList()
            (0 until docs.length()).map { docs.getJSONObject(it) }
        }.getOrNull()
    }

    /**
     * 장소명·주소를 좌표로 바꾼다.
     *
     * 좌표 없이 이름·주소만 있는 정차지에 좌표를 채워 도착 감지와 지도 표시를 가능하게 한다.
     */
    suspend fun geocode(query: String): CapturedLocation? = withContext(Dispatchers.IO) {
        // 키워드·주소를 모두 조회하는 통합 검색을 재사용한다. 지번 주소도 좌표가 반환된다.
        // 최근 위치를 기준점으로 넘긴다. 같은 이름의 다른 지역 장소가 정차지 좌표로 들어가면 도착 알림이 영영 오지 않는다.
        val near = live.fresh(maxAgeMs = NEAR_HINT_MAX_AGE_MS)
        val found = searchPlaces(query, GEOCODE_CANDIDATES, near?.latitude, near?.longitude)
        val top = found.firstOrNull()
        // 이름이 같은 지점(예: 여러 지역의 "○○ 물류센터")이 여럿이면 가장 가까운 곳을 고른다.
        val sameName = top?.let { t -> found.filter { it.name.replace(" ", "") == t.name.replace(" ", "") } }.orEmpty()
        (sameName.mapNotNull { p -> p.distanceM?.let { p to it } }.minByOrNull { it.second }?.first ?: top)?.let {
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
        // 에뮬레이터·해외에서는 '미국' 같은 값이 온다. 한국 주소가 아니면 표시하지 않는다.
        if (a.countryCode != null && a.countryCode != "KR") return null
        val line = a.getAddressLine(0)
        return line?.removePrefix("대한민국 ")?.trim()?.ifBlank { null }
    }
}

private data class LatLng(val latitude: Double, val longitude: Double)

/** 공유받은 장소를 좌표로 바꿀 때 살펴보는 후보 수. */
private const val GEOCODE_CANDIDATES = 5

/** 공유받은 장소를 좌표로 바꿀 때 기준점으로 쓰는 위치의 최대 나이. */
private const val NEAR_HINT_MAX_AGE_MS = 30 * 60_000L

/** 마지막 위치를 받아들이는 최대 나이. 차는 2분이면 2~3km 를 가지만, 위치가 아예 없는 것보다는 낫다. */
private const val LAST_KNOWN_MAX_AGE_MS = 120_000L

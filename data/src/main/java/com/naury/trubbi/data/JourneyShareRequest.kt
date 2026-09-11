package com.naury.trubbi.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow

/** 다른 앱(카카오맵·네이버지도·티맵·메신저)에서 공유해 들어온 장소. */
data class SharedPlace(
    val name: String,
    val address: String?,
)

/** 홈이 읽어 갈 때까지 마지막으로 공유된 장소를 들고 있는다. */
@Singleton
class JourneyShareRequest @Inject constructor() {
    val place = MutableStateFlow<SharedPlace?>(null)
}

/**
 * 지도앱이 공유하는 텍스트에서 장소명과 주소를 뽑아낸다.
 *
 * 앱마다 형식이 조금씩 다르지만 대체로 이렇게 생겼다.
 * ```
 * 카카오맵  OO물류센터 / 경기 성남시 … / http://kko.to/…
 * 네이버    [네이버 지도] / OO물류센터 / 경기도 성남시 … / https://naver.me/…
 * 티맵      OO물류센터 / 경기 성남시 … / https://surl.tmap.co.kr/…
 * ```
 * 공통 규칙만 취한다 — URL 과 대괄호 머리말을 버리고, 첫 줄을 이름으로, 주소처럼 보이는
 * 첫 줄을 주소로 본다. 정규 형식이 아니므로 실패하면 조용히 null 이다.
 */
object SharedPlaceParser {

    fun parse(text: String?): SharedPlace? {
        if (text.isNullOrBlank()) return null
        val lines = text.lines()
            .map { it.trim().trim('"', '\'', '<', '>') }
            .filter { it.isNotBlank() }
            .filterNot { isUrl(it) }
            .filterNot { it.startsWith("[") && it.endsWith("]") }
        if (lines.isEmpty()) return null
        val name = lines.first().take(60)
        val address = lines.drop(1).firstOrNull { looksLikeAddress(it) }?.take(120)
        return SharedPlace(name = name, address = address)
    }

    private fun isUrl(s: String): Boolean =
        s.startsWith("http://") || s.startsWith("https://") || s.contains("://")

    /** 한글 주소 판별 — 행정구역 표시가 있거나, 도로·지번 뒤에 숫자가 붙는 형태. */
    private val roadPattern = Regex("[가-힣0-9]+(로|길|동|읍|면|리)\\s?\\d")

    private fun looksLikeAddress(s: String): Boolean {
        if (!s.any { it.isDigit() } || s.length < 8) return false
        val region = listOf("특별시", "광역시", "도 ", "시 ", "군 ", "구 ")
        return region.any { s.contains(it) } || roadPattern.containsMatchIn(s)
    }
}

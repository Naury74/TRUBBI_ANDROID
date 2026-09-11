package com.naury.trubbi.data

import com.naury.trubbi.core.weather.WeatherCondition
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 자동 테마가 참고하는 앱 전역 분위기 상태.
 *
 * 홈 뷰모델이 최신 날씨를 흘려 넣고, 최상위 테마가 이를 읽어 자동 전환 여부를 판단한다.
 */
@Singleton
class AmbientSignal @Inject constructor() {
    val weather = MutableStateFlow<WeatherCondition?>(null)
}

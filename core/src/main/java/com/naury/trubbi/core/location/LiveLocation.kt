package com.naury.trubbi.core.location

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 측위 한 점.
 *
 * @property accuracyM 수평 정확도 반경(m). 모르면 null
 * @property timeMs 측위 시각(벽시계, epoch ms)
 * @property bearing 진행 방향(도). 모르면 null
 */
data class Fix(
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Float?,
    val timeMs: Long,
    val speedMps: Float? = null,
    val bearing: Float? = null,
)

/**
 * 여정 추적 중 마지막으로 걸러 낸 좌표를 앱 전체에 나눠 준다.
 *
 * 추적 서비스가 이미 몇 초마다 정확한 좌표를 받고 있으므로, 사진을 찍을 때 다시 측위하지 않고 이 값을 쓰면
 * 셔터 순간의 위치가 남는다(따로 측위하면 몇 초 늦고, 그사이 차가 움직인다). 지도는 이 값으로 현위치를 그린다.
 */
@Singleton
class LiveLocation @Inject constructor() {
    private val state = MutableStateFlow<Fix?>(null)

    /** 마지막 좌표. 추적 중이 아니면 null 이거나 오래된 값이다. */
    val latest: StateFlow<Fix?> = state.asStateFlow()

    fun publish(fix: Fix) {
        state.value = fix
    }

    fun clear() {
        state.value = null
    }

    /** [maxAgeMs] 안에 받은 좌표만. */
    fun fresh(nowMs: Long = System.currentTimeMillis(), maxAgeMs: Long = FRESH_MS): Fix? =
        state.value?.takeIf { nowMs - it.timeMs in 0..maxAgeMs }

    private companion object {
        const val FRESH_MS = 30_000L
    }
}

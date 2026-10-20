package com.naury.trubbi.domain.tracking

import com.naury.trubbi.core.location.Fix
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 경로와 주행 거리에 넣기 전에 튀는 좌표를 걸러 낸다.
 *
 * 도심 고층 건물 사이, 터널 출구, 와이파이·기지국 측위에서는 수백 m 떨어진 좌표가 섞여 들어온다. 그대로 넣으면
 * 경로가 지그재그가 되고, 주차 중에도 주행 거리가 늘어난다. 세 가지를 본다.
 *  - 정확도: 반경이 [maxAccuracyM] 보다 크면 버린다.
 *  - 나이: [maxAgeMs] 보다 오래된 좌표(캐시된 값)는 버린다.
 *  - 속도: 직전에 받아들인 점에서 [maxSpeedKmh] 보다 빨라야 닿는 곳이면 순간이동이라 버린다.
 *
 * 속도 검사는 직전 점이 틀렸을 때 이후 점을 모두 막아 버릴 수 있다. 그래서 [maxRejectRun] 번 연달아 버리면
 * 직전 점이 틀렸다고 보고 새 점을 받아들인다.
 */
class FixFilter(
    private val maxAccuracyM: Float = 50f,
    private val maxAgeMs: Long = 30_000L,
    private val maxSpeedKmh: Float = 160f,
    private val maxRejectRun: Int = 3,
) {
    private var last: Fix? = null
    private var rejectRun = 0

    /** 직전에 받아들인 점. */
    val lastAccepted: Fix? get() = last

    /**
     * @param ageMs 측위가 이뤄진 뒤 지난 시간
     * @return 경로에 넣어도 되면 true
     */
    fun accept(fix: Fix, ageMs: Long): Boolean {
        val ok = isPlausible(fix, ageMs)
        if (ok || (rejectRun >= maxRejectRun && fix.accuracyM.let { it == null || it <= maxAccuracyM })) {
            last = fix
            rejectRun = 0
            return true
        }
        rejectRun++
        return false
    }

    /** 새 여정을 시작할 때. */
    fun reset() {
        last = null
        rejectRun = 0
    }

    private fun isPlausible(fix: Fix, ageMs: Long): Boolean {
        if (ageMs > maxAgeMs) return false
        val accuracy = fix.accuracyM
        if (accuracy != null && accuracy > maxAccuracyM) return false
        val prev = last ?: return true
        val seconds = (fix.timeMs - prev.timeMs) / 1000.0
        if (seconds <= 0) return false
        val kmh = distanceM(prev, fix) / seconds * 3.6
        return kmh <= maxSpeedKmh
    }
}

/** 두 점 사이 거리(m). */
fun distanceM(a: Fix, b: Fix): Float = distanceM(a.latitude, a.longitude, b.latitude, b.longitude)

/** 두 좌표 사이 거리(m). 하버사인 공식이라 수 km 안에서는 [android.location.Location.distanceBetween] 과 1m 안쪽으로 같다. */
fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    return (2 * EARTH_RADIUS_M * asin(sqrt(h))).toFloat()
}

private const val EARTH_RADIUS_M = 6_371_008.8

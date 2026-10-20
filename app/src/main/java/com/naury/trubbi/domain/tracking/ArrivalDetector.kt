package com.naury.trubbi.domain.tracking

import com.naury.trubbi.core.location.Fix

/**
 * 정차지에 다가가고 도착했는지 판정한다.
 *
 * 예전에는 반경 안 좌표가 한 번만 들어와도 도착으로 알렸다. 그러면 물류센터 옆 도로를 지나가기만 해도, 측위가
 * 한 번 튀기만 해도 "도착" 알림이 갔고, 한 번 알린 곳은 다시 알리지 않아 정작 실제로 도착했을 때는 조용했다.
 *
 * 지금은 이렇게 본다.
 *  - 예고: 반경 밖, [headsUpM] 안에 처음 들어오면 한 번. 처음 받은 좌표가 이미 반경 안이면 건너뛴다.
 *  - 도착: 정확도가 충분한 좌표가 반경 안에 [dwellMs] 이상 머물렀거나, 반경 안에서 두 번 연달아 거의 멈춰 있을 때.
 *  - 다시 알림: 도착을 알린 뒤 반경의 1.5배 밖으로 나가면 다시 알릴 수 있게 된다(지나쳤다가 돌아온 경우).
 *
 * 정차지가 바뀌면 상태를 새로 시작한다.
 */
class ArrivalDetector(
    private val dwellMs: Long = 45_000L,
    private val stoppedKmh: Float = 10f,
) {
    /** 판정 결과. */
    enum class Event { NONE, APPROACHING, ARRIVED }

    private var stopId: String? = null
    private var seenOutside = false
    private var announcedApproach = false
    private var arrived = false
    private var insideSinceMs: Long? = null
    private var stoppedInsideRun = 0

    /**
     * @param stopId 지금 향하는 정차지
     * @param distanceM 정차지까지 거리
     * @param radiusM 도착 반경(설정값)
     */
    fun update(stopId: String, distanceM: Float, radiusM: Int, fix: Fix): Event {
        if (stopId != this.stopId) reset(stopId)
        val headsUpM = (radiusM * HEADS_UP_FACTOR).coerceAtLeast(MIN_HEADS_UP_M)
        val inside = distanceM <= radiusM

        if (arrived) {
            // 반경의 1.5배 밖으로 나가면 다시 알릴 수 있게 한다.
            if (distanceM > radiusM * REARM_FACTOR) {
                arrived = false
                insideSinceMs = null
                stoppedInsideRun = 0
            }
            return Event.NONE
        }

        if (!inside) {
            seenOutside = true
            insideSinceMs = null
            stoppedInsideRun = 0
            if (distanceM <= headsUpM && !announcedApproach) {
                announcedApproach = true
                return Event.APPROACHING
            }
            return Event.NONE
        }

        // 반경 안. 정확도가 반경의 절반보다 나쁘면 안인지 밖인지 믿을 수 없어 판단을 미룬다.
        val accuracy = fix.accuracyM
        if (accuracy != null && accuracy > (radiusM / 2f).coerceAtLeast(MIN_TRUSTED_ACCURACY_M)) return Event.NONE
        // 이미 반경 안에서 시작했다면 예고는 건너뛴다.
        if (!seenOutside) announcedApproach = true

        val since = insideSinceMs ?: fix.timeMs.also { insideSinceMs = it }
        val speedKmh = fix.speedMps?.times(3.6f)
        stoppedInsideRun = if (speedKmh != null && speedKmh < stoppedKmh) stoppedInsideRun + 1 else 0
        if (fix.timeMs - since >= dwellMs || stoppedInsideRun >= 2) {
            arrived = true
            return Event.ARRIVED
        }
        return Event.NONE
    }

    /** 새 정차지(또는 새 여정)로 상태를 비운다. */
    fun reset(stopId: String? = null) {
        this.stopId = stopId
        seenOutside = false
        announcedApproach = false
        arrived = false
        insideSinceMs = null
        stoppedInsideRun = 0
    }

    private companion object {
        const val HEADS_UP_FACTOR = 4
        const val MIN_HEADS_UP_M = 1000
        const val REARM_FACTOR = 1.5f
        const val MIN_TRUSTED_ACCURACY_M = 50f
    }
}

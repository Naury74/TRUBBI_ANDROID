package com.naury.trubbi.data.repo

import com.naury.trubbi.data.local.RoutePointEntity
import kotlin.math.cos
import kotlin.math.hypot

/**
 * 끝난 여정의 GPS 자취를 모양은 그대로 두고 점 수만 줄인다(Douglas–Peucker).
 *
 * 주행 중에는 60m 마다 한 점을 남겨 500km 를 달리면 수천 행이 쌓인다. 직선 구간의 점은 지도에 아무것도 더하지
 * 않으므로, 여정이 끝나면 선에서 [toleranceM] 보다 덜 벗어난 점을 지운다. 굽은 길·교차로의 점은 남는다.
 * 시간이 크게 빈 곳(터널·추적 중단)의 앞뒤 점은 지도에서 끊어 그려야 하므로 항상 남긴다.
 */
object RouteSimplifier {

    /** @return 남길 점들의 id */
    fun keep(points: List<RoutePointEntity>, toleranceM: Double = 15.0, gapMs: Long = GAP_MS): Set<Long> {
        if (points.size <= 2) return points.map { it.id }.toSet()
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.lastIndex] = true
        for (i in 1 until points.size) {
            if (points[i].at - points[i - 1].at >= gapMs) {
                keep[i - 1] = true
                keep[i] = true
            }
        }
        // 기준 위도에서 m 단위 평면 좌표로 바꿔 계산한다(여정 범위에서는 오차가 무시할 만하다).
        val lat0 = Math.toRadians(points.first().latitude)
        val xs = DoubleArray(points.size) { points[it].longitude * M_PER_DEG * cos(lat0) }
        val ys = DoubleArray(points.size) { points[it].latitude * M_PER_DEG }
        // 이미 남기기로 한 점 사이 구간마다 따로 줄인다.
        var start = 0
        for (end in 1 until points.size) {
            if (keep[end]) {
                simplify(xs, ys, start, end, toleranceM, keep)
                start = end
            }
        }
        return points.indices.filter { keep[it] }.map { points[it].id }.toSet()
    }

    private fun simplify(xs: DoubleArray, ys: DoubleArray, first: Int, last: Int, tol: Double, keep: BooleanArray) {
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(first to last)
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast()
            if (b - a < 2) continue
            var maxD = -1.0
            var idx = -1
            for (i in a + 1 until b) {
                val d = distanceToSegment(xs[i], ys[i], xs[a], ys[a], xs[b], ys[b])
                if (d > maxD) {
                    maxD = d
                    idx = i
                }
            }
            if (maxD > tol) {
                keep[idx] = true
                stack.addLast(a to idx)
                stack.addLast(idx to b)
            }
        }
    }

    private fun distanceToSegment(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        if (len2 == 0.0) return hypot(px - ax, py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    /** 이만큼 기록이 비면 끊긴 구간으로 본다. */
    const val GAP_MS = 5 * 60_000L

    private const val M_PER_DEG = 111_320.0
}

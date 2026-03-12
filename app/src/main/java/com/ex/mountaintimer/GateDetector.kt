package com.ex.mountaintimer

import kotlin.math.abs


/**
 * 幾何工具：判斷「車子的移動線段」是否穿越某個 Gate（線段）
 *
 * 車子的軌跡：prev -> curr
 * Gate：a -> b
 *
 * 若兩條線段相交，視為「通過 Gate」
 */
object GateDetector {

    /**
     * 線段相交判斷（含端點相觸）
     */
    fun crossedGate(prev: GeoPoint, curr: GeoPoint, gate: Gate): Boolean {
        return segmentsIntersect(prev, curr, gate.a, gate.b)
    }

    // ---- 以下是線段相交的通用幾何算法 ----

    private const val EPS = 1e-12

    private fun segmentsIntersect(p1: GeoPoint, p2: GeoPoint, q1: GeoPoint, q2: GeoPoint): Boolean {
        val o1 = orientation(p1, p2, q1)
        val o2 = orientation(p1, p2, q2)
        val o3 = orientation(q1, q2, p1)
        val o4 = orientation(q1, q2, p2)

        // 一般相交情況
        if (o1 != o2 && o3 != o4) return true

        // 特殊情況：共線 + 在線段上
        if (o1 == 0 && onSegment(p1, q1, p2)) return true
        if (o2 == 0 && onSegment(p1, q2, p2)) return true
        if (o3 == 0 && onSegment(q1, p1, q2)) return true
        if (o4 == 0 && onSegment(q1, p2, q2)) return true

        return false
    }

    /**
     * 回傳：
     * 0 -> 共線
     * 1 -> 順時針
     * 2 -> 逆時針
     *
     * 這裡把 GeoPoint 當成 (x=lng, y=lat) 來算（平面近似）
     * 對「是否穿越 gate」這個用途很夠用。
     */
    private fun orientation(a: GeoPoint, b: GeoPoint, c: GeoPoint): Int {
        val ax = a.lng
        val ay = a.lat
        val bx = b.lng
        val by = b.lat
        val cx = c.lng
        val cy = c.lat

        val value = (by - ay) * (cx - bx) - (bx - ax) * (cy - by)

        if (abs(value) < EPS) return 0
        return if (value > 0) 1 else 2
    }

    private fun onSegment(a: GeoPoint, b: GeoPoint, c: GeoPoint): Boolean {
        // b 是否在 a-c 的矩形範圍內（且已共線）
        return b.lng <= maxOf(a.lng, c.lng) + EPS &&
                b.lng + EPS >= minOf(a.lng, c.lng) &&
                b.lat <= maxOf(a.lat, c.lat) + EPS &&
                b.lat + EPS >= minOf(a.lat, c.lat)
    }
}

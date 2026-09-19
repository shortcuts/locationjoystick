package com.locationjoystick.core.map.projection

import com.locationjoystick.core.model.LatLng
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * WGS-84 ⇄ GCJ-02 datum conversion (the "Mars coordinates" offset applied by every mainland-China
 * map provider, including Amap). Pure math, no Android dependencies.
 *
 * Forward ([fromWgs84]) is the standard public-domain polynomial. Reverse ([toWgs84]) iterates the
 * forward transform until it converges (typically 2–3 rounds) instead of the common single-step
 * approximation, so a round trip is accurate to well under a metre.
 *
 * Points outside mainland China are returned unchanged — Amap itself serves un-shifted tiles there.
 */
object Gcj02 {
    private const val A = 6378245.0
    private const val EE = 0.00669342162296594323
    private const val MAX_ITERATIONS = 6
    private const val CONVERGENCE_DEGREES = 1e-8

    fun fromWgs84(point: LatLng): LatLng {
        if (isOutsideChina(point.latitude, point.longitude)) return point
        val (dLat, dLon) = delta(point.latitude, point.longitude)
        return LatLng(point.latitude + dLat, point.longitude + dLon)
    }

    fun toWgs84(point: LatLng): LatLng {
        if (isOutsideChina(point.latitude, point.longitude)) return point
        var lat = point.latitude
        var lon = point.longitude
        repeat(MAX_ITERATIONS) {
            val (dLat, dLon) = delta(lat, lon)
            val nextLat = point.latitude - dLat
            val nextLon = point.longitude - dLon
            val converged = abs(nextLat - lat) < CONVERGENCE_DEGREES && abs(nextLon - lon) < CONVERGENCE_DEGREES
            lat = nextLat
            lon = nextLon
            if (converged) return LatLng(lat, lon)
        }
        return LatLng(lat, lon)
    }

    /** Coarse bounding box — same heuristic the reference implementations use. */
    fun isOutsideChina(
        lat: Double,
        lon: Double,
    ): Boolean = lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271

    /** Offset (dLat, dLon) in degrees to add to a WGS-84 point to obtain GCJ-02. */
    private fun delta(
        lat: Double,
        lon: Double,
    ): Pair<Double, Double> {
        var dLat = transformLat(lon - 105.0, lat - 35.0)
        var dLon = transformLon(lon - 105.0, lat - 35.0)
        val radLat = lat / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        dLon = (dLon * 180.0) / (A / sqrtMagic * cos(radLat) * PI)
        return dLat to dLon
    }

    private fun transformLat(
        x: Double,
        y: Double,
    ): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * PI) + 320 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLon(
        x: Double,
        y: Double,
    ): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }
}

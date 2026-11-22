package com.example.agopengps.navigation

import kotlin.math.*

data class GeoPoint(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0
)

object GeoUtils {
    const val EARTH_RADIUS_M = 6378137.0

    fun geoToLocalMeters(target: GeoPoint, origin: GeoPoint): Vec2 {
        val latRad = Math.toRadians(origin.latitude)
        val dLat = Math.toRadians(target.latitude - origin.latitude)
        val dLon = Math.toRadians(target.longitude - origin.longitude)

        val y = dLat * EARTH_RADIUS_M
        val x = dLon * EARTH_RADIUS_M * cos(latRad)
        return Vec2(x, y)
    }

    fun localMetersToGeo(local: Vec2, origin: GeoPoint): GeoPoint {
        val latRad = Math.toRadians(origin.latitude)
        val dLat = local.y / EARTH_RADIUS_M
        val dLon = local.x / (EARTH_RADIUS_M * cos(latRad))

        val lat = origin.latitude + Math.toDegrees(dLat)
        val lon = origin.longitude + Math.toDegrees(dLon)
        return GeoPoint(lat, lon, origin.altitude)
    }

    fun distanceBetweenMeters(p1: GeoPoint, p2: GeoPoint): Double {
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)
        val lat1 = Math.toRadians(p1.latitude)
        val lat2 = Math.toRadians(p2.latitude)

        val a = sin(dLat / 2.0).pow(2) + sin(dLon / 2.0).pow(2) * cos(lat1) * cos(lat2)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return EARTH_RADIUS_M * c
    }

    fun normalizeAngleDeg(angle: Double): Double {
        var a = angle % 360.0
        if (a > 180.0) a -= 360.0
        if (a < -180.0) a += 360.0
        return a
    }

    fun bearingDegrees(p1: GeoPoint, p2: GeoPoint): Double {
        val lat1 = Math.toRadians(p1.latitude)
        val lat2 = Math.toRadians(p2.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val brngRad = atan2(y, x)
        return (Math.toDegrees(brngRad) + 360.0) % 360.0
    }

    fun calculatePolygonAreaAcres(points: List<Vec2>): Double {
        if (points.size < 3) return 0.0
        var area = 0.0
        for (i in points.indices) {
            val j = (i + 1) % points.size
            area += points[i].x * points[j].y
            area -= points[j].x * points[i].y
        }
        val sqMeters = abs(area) / 2.0
        return sqMeters / 4046.8564224 // 1 acre = 4046.856 m2
    }
}

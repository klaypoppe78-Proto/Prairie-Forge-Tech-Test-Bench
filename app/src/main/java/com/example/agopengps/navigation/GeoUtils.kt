package com.example.agopengps.navigation

import kotlin.math.*

/**
 * Coordinate transformations between WGS84 (Lat, Lon) and Local Tangent Plane (meters).
 * Uses high-precision ellipsoidal projection centered on a local field origin.
 */
object GeoUtils {
    private const val EARTH_RADIUS_M = 6378137.0 // WGS84 semi-major axis
    private const val WGS84_F = 1.0 / 298.257223563
    private const val WGS84_E2 = 2.0 * WGS84_F - WGS84_F * WGS84_F

    /**
     * Converts GeoPoint to local Easting (x in meters) and Northing (y in meters)
     * relative to a local origin point [origin].
     */
    fun geoToLocalMeters(geo: GeoPoint, origin: GeoPoint): Vec2 {
        val latRad = Math.toRadians(geo.latitude)
        val lonRad = Math.toRadians(geo.longitude)
        val lat0Rad = Math.toRadians(origin.latitude)
        val lon0Rad = Math.toRadians(origin.longitude)

        val dLat = latRad - lat0Rad
        val dLon = lonRad - lon0Rad

        // Radius of curvature in the prime vertical
        val sinLat0 = sin(lat0Rad)
        val rn = EARTH_RADIUS_M / sqrt(1.0 - WGS84_E2 * sinLat0 * sinLat0)
        // Radius of curvature in the meridian
        val rm = rn * (1.0 - WGS84_E2) / (1.0 - WGS84_E2 * sinLat0 * sinLat0)

        val x = dLon * rn * cos(lat0Rad)
        val y = dLat * rm

        return Vec2(x, y)
    }

    /**
     * Converts local Easting (x in meters) and Northing (y in meters)
     * back to WGS84 GeoPoint relative to [origin].
     */
    fun localMetersToGeo(local: Vec2, origin: GeoPoint): GeoPoint {
        val lat0Rad = Math.toRadians(origin.latitude)

        val sinLat0 = sin(lat0Rad)
        val rn = EARTH_RADIUS_M / sqrt(1.0 - WGS84_E2 * sinLat0 * sinLat0)
        val rm = rn * (1.0 - WGS84_E2) / (1.0 - WGS84_E2 * sinLat0 * sinLat0)

        val dLat = local.y / rm
        val dLon = local.x / (rn * cos(lat0Rad))

        val lat = origin.latitude + Math.toDegrees(dLat)
        val lon = origin.longitude + Math.toDegrees(dLon)

        return GeoPoint(lat, lon, origin.altitude)
    }

    /**
     * Calculates great-circle distance in meters between two GeoPoints.
     */
    fun distanceBetweenMeters(p1: GeoPoint, p2: GeoPoint): Double {
        val lat1 = Math.toRadians(p1.latitude)
        val lat2 = Math.toRadians(p2.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(p2.longitude - p1.longitude)

        val a = sin(dLat / 2.0).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2.0).pow(2)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return EARTH_RADIUS_M * c
    }

    /**
     * Calculates initial bearing in degrees [0, 360) from p1 to p2.
     * 0 = North, 90 = East, 180 = South, 270 = West.
     */
    fun bearingDegrees(p1: GeoPoint, p2: GeoPoint): Double {
        val lat1 = Math.toRadians(p1.latitude)
        val lat2 = Math.toRadians(p2.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val brng = Math.toDegrees(atan2(y, x))
        return (brng + 360.0) % 360.0
    }

    /**
     * Calculates the enclosed area of a polygon defined by local 2D vertices in Acres (Shoelace formula).
     */
    fun calculatePolygonAreaAcres(vertices: List<Vec2>): Double {
        if (vertices.size < 3) return 0.0
        var sum = 0.0
        val n = vertices.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            sum += (vertices[i].x * vertices[j].y) - (vertices[j].x * vertices[i].y)
        }
        val areaSqMeters = abs(sum) / 2.0
        return areaSqMeters * 0.000247105 // 1 sq meter = 0.000247105 acres
    }

    /**
     * Normalizes an angle in degrees to [-180, 180).
     */
    fun normalizeAngleDeg(degrees: Double): Double {
        var angle = degrees % 360.0
        if (angle >= 180.0) angle -= 360.0
        if (angle < -180.0) angle += 360.0
        return angle
    }

    /**
     * Normalizes an angle in radians to [-PI, PI).
     */
    fun normalizeAngleRad(radians: Double): Double {
        var angle = radians % (2.0 * PI)
        if (angle >= PI) angle -= 2.0 * PI
        if (angle < -PI) angle += 2.0 * PI
        return angle
    }
}

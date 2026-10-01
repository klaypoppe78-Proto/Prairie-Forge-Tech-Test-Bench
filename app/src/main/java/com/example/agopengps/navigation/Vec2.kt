package com.example.agopengps.navigation

import kotlin.math.*

/**
 * 2D vector and coordinate math for precision agriculture guidance algorithms.
 */
data class Vec2(val x: Double, val y: Double) {
    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
    operator fun times(scalar: Double) = Vec2(x * scalar, y * scalar)
    operator fun div(scalar: Double) = Vec2(x / scalar, y / scalar)

    fun length(): Double = hypot(x, y)
    fun lengthSquared(): Double = x * x + y * y

    fun normalized(): Vec2 {
        val len = length()
        return if (len > 1e-9) Vec2(x / len, y / len) else Vec2(0.0, 0.0)
    }

    fun dot(other: Vec2): Double = x * other.x + y * other.y
    fun cross(other: Vec2): Double = x * other.y - y * other.x

    /**
     * Perpendicular vector rotated 90 degrees clockwise.
     */
    fun perpClockwise(): Vec2 = Vec2(y, -x)

    /**
     * Perpendicular vector rotated 90 degrees counter-clockwise.
     */
    fun perpCounterClockwise(): Vec2 = Vec2(-y, x)

    fun distanceTo(other: Vec2): Double = hypot(x - other.x, y - other.y)

    /**
     * Projects this point onto the infinite line passing through [lineStart] in direction [lineDir].
     * [lineDir] must be normalized.
     */
    fun projectOntoLine(lineStart: Vec2, lineDir: Vec2): Vec2 {
        val toPoint = this - lineStart
        val projLength = toPoint.dot(lineDir)
        return lineStart + (lineDir * projLength)
    }

    /**
     * Signed perpendicular distance from line defined by [lineStart] and unit direction [lineDir].
     * Positive is to the right of line direction, negative is to the left.
     */
    fun signedDistanceToLine(lineStart: Vec2, lineDir: Vec2): Double {
        val normal = lineDir.perpClockwise()
        val toPoint = this - lineStart
        return toPoint.dot(normal)
    }
}

/**
 * Geographic WGS84 coordinate.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0
)

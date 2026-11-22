package com.example.agopengps.navigation

import kotlin.math.*

data class Vec2(val x: Double = 0.0, val y: Double = 0.0) {
    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
    operator fun times(scalar: Double) = Vec2(x * scalar, y * scalar)
    operator fun div(scalar: Double) = Vec2(x / scalar, y / scalar)
    operator fun unaryMinus() = Vec2(-x, -y)

    fun length() = sqrt(x * x + y * y)
    fun lengthSquared() = x * x + y * y
    fun distanceTo(other: Vec2) = (this - other).length()
    fun distanceSquaredTo(other: Vec2) = (this - other).lengthSquared()

    fun normalized(): Vec2 {
        val len = length()
        return if (len > 1e-9) Vec2(x / len, y / len) else Vec2(0.0, 0.0)
    }

    fun dot(other: Vec2) = x * other.x + y * other.y
    fun cross(other: Vec2) = x * other.y - y * other.x

    fun rotate(angleRad: Double): Vec2 {
        val cosA = cos(angleRad)
        val sinA = sin(angleRad)
        return Vec2(x * cosA - y * sinA, x * sinA + y * cosA)
    }

    fun rotateDeg(angleDeg: Double) = rotate(Math.toRadians(angleDeg))

    fun perpClockwise() = Vec2(y, -x)
    fun perpCounterClockwise() = Vec2(-y, x)

    fun projectOntoLine(lineOrigin: Vec2, lineDir: Vec2): Vec2 {
        val toPoint = this - lineOrigin
        val dist = toPoint.dot(lineDir)
        return lineOrigin + (lineDir * dist)
    }
}

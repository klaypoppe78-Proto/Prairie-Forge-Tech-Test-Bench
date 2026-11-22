package com.example.agopengps.navigation

import kotlin.math.*

class VehicleSimulator(
    var localPosition: Vec2 = Vec2(0.0, 0.0),
    var headingDeg: Double = 0.0,
    var speedKmh: Double = 7.2, // ~4.5 mph default tractor field speed
    var maxSteerAngleDeg: Double = 42.0,
    var wheelbase: Double = 2.845,
    var manualSteerInputDeg: Double = 0.0,
    var isReverse: Boolean = false
) {
    var actualSteerAngleDeg: Double = 0.0

    fun update(dtSec: Double, targetSteerDeg: Double, isAutoSteerEngaged: Boolean) {
        val desiredSteer = if (isAutoSteerEngaged) targetSteerDeg else manualSteerInputDeg
        val steerRateDegPerSec = 45.0 // Hydraulic cylinder slewing rate

        val steerDiff = desiredSteer - actualSteerAngleDeg
        val maxStep = steerRateDegPerSec * dtSec
        actualSteerAngleDeg += steerDiff.coerceIn(-maxStep, maxStep)
        actualSteerAngleDeg = actualSteerAngleDeg.coerceIn(-maxSteerAngleDeg, maxSteerAngleDeg)

        val speedMs = (if (isReverse) -speedKmh else speedKmh) / 3.6
        val steerRad = Math.toRadians(actualSteerAngleDeg)

        // Bicycle kinematic model: dHeading/dt = (v / L) * tan(delta)
        val yawRateRad = (speedMs / wheelbase) * tan(steerRad)
        headingDeg = (headingDeg + Math.toDegrees(yawRateRad * dtSec) + 360.0) % 360.0

        val headingRad = Math.toRadians(headingDeg)
        val fwd = Vec2(sin(headingRad), cos(headingRad))
        localPosition += (fwd * (speedMs * dtSec))
    }

    fun resetTo(pos: Vec2, heading: Double) {
        localPosition = pos
        headingDeg = heading
        actualSteerAngleDeg = 0.0
    }
}

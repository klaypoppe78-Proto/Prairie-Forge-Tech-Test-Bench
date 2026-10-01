package com.example.agopengps.io

import com.example.agopengps.navigation.*
import kotlin.math.*

/**
 * Built-in tractor kinematic simulation engine.
 * Allows realistic indoor testing, tuning, and demonstration of Stanley and Pure Pursuit
 * navigation algorithms without requiring physical tractor hardware or live RTK satellite lock.
 */
class SimulatorEngine(
    val originGeo: GeoPoint = GeoPoint(44.9141, -95.7153, 310.0)
) {
    // Current simulated state
    var localPivot = Vec2(0.0, 0.0)
    var headingDeg = 0.0 // 0 = North, 90 = East
    var speedKmh = 7.2   // Standard 2 m/s tractor field speed (4.5 mph)
    var actualSteerAngleDeg = 0.0
    var manualSteerInputDeg = 0.0
    var isReverse = false
    var simulatedRollDeg = 0.0

    private val maxSteerSpeedDegPerSec = 30.0 // Steering actuator rate limit

    fun resetTo(pos: Vec2, heading: Double) {
        localPivot = pos
        headingDeg = heading
        actualSteerAngleDeg = 0.0
        manualSteerInputDeg = 0.0
    }

    /**
     * Advances simulation by [dtSec] seconds.
     *
     * @param targetSteerAngleDeg Steering angle commanded by auto-steer controller.
     * @param isAutoSteerEngaged Whether auto-steer controller is actively driving the wheels.
     * @param vehicle Vehicle geometric dimensions.
     */
    fun update(
        dtSec: Double,
        targetSteerAngleDeg: Double,
        isAutoSteerEngaged: Boolean,
        vehicle: VehicleConfig
    ) {
        val effectiveTargetSteer = if (isAutoSteerEngaged) {
            targetSteerAngleDeg
        } else {
            manualSteerInputDeg
        }.coerceIn(-vehicle.maxSteerAngleDeg, vehicle.maxSteerAngleDeg)

        // Servo actual wheel steer angle toward target steer angle with motor rate limit
        val steerDiff = effectiveTargetSteer - actualSteerAngleDeg
        val maxSteerStep = maxSteerSpeedDegPerSec * dtSec
        if (abs(steerDiff) <= maxSteerStep) {
            actualSteerAngleDeg = effectiveTargetSteer
        } else {
            actualSteerAngleDeg += sign(steerDiff) * maxSteerStep
        }

        val effectiveSpeedKmh = if (isReverse) -speedKmh else speedKmh
        val speedMps = effectiveSpeedKmh / 3.6

        // Bicycle kinematic model:
        // Angular velocity: omega = (v / wheelbase) * tan(steerAngle)
        val steerRad = Math.toRadians(actualSteerAngleDeg)
        val angularVelocityRad = (speedMps / vehicle.wheelbase) * tan(steerRad)
        val deltaHeadingDeg = Math.toDegrees(angularVelocityRad * dtSec)

        headingDeg = (headingDeg + deltaHeadingDeg + 360.0) % 360.0

        // Position update along vehicle heading
        val headingRad = Math.toRadians(headingDeg)
        val forwardDir = Vec2(sin(headingRad), cos(headingRad))
        val deltaPos = forwardDir * (speedMps * dtSec)
        localPivot += deltaPos

        // Add subtle simulated ground roll from terrain wobble and steering centrifugal force
        val centrifugalRoll = -0.05 * speedMps * actualSteerAngleDeg
        simulatedRollDeg = centrifugalRoll + 0.3 * sin(localPivot.y * 0.1)
    }

    fun getCurrentGeoPosition(): GeoPoint {
        return GeoUtils.localMetersToGeo(localPivot, originGeo)
    }
}

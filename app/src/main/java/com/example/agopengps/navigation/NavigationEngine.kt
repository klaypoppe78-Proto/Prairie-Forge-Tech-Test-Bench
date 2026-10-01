package com.example.agopengps.navigation

import kotlin.math.*

/**
 * Supported path-tracking steering algorithms.
 */
enum class SteeringAlgorithm(val label: String) {
    STANLEY("Stanley Steering Control"),
    PURE_PURSUIT("Pure Pursuit Arc Control")
}

/**
 * Configuration options and tuning parameters for the NavigationEngine.
 */
data class NavigationEngineConfig(
    val algorithm: SteeringAlgorithm = SteeringAlgorithm.STANLEY,
    val wheelbaseMeters: Double = 2.845,          // Default New Holland 8870 (112 in)
    val maxSteerAngleDeg: Double = 42.0,          // Maximum wheel steering lock
    val deadbandMeters: Double = 0.02,            // 2 cm deadband for minor GPS noise

    // --- Stanley Controller Parameters ---
    val stanleyGainK: Double = 1.25,              // Cross-track error proportional gain
    val stanleyIntegralGainKi: Double = 0.08,     // Side-slope bias / implement pull compensation
    val stanleyDerivativeGainKd: Double = 0.12,   // Damping gain for cross-track rate of change
    val stanleySofteningSpeedMps: Double = 0.5,   // Softening constant (v0) preventing division by zero at low speeds
    val stanleyMaxIntegralDeg: Double = 8.0,      // Anti-windup clamping limit for integral correction

    // --- Pure Pursuit Controller Parameters ---
    val purePursuitLookaheadGainSec: Double = 0.85, // Lookahead time coefficient (L = v * t)
    val purePursuitMinLookaheadMeters: Double = 2.0, // Minimum lookahead distance
    val purePursuitMaxLookaheadMeters: Double = 12.0, // Maximum lookahead distance
    val purePursuitCurvatureDamping: Double = 1.0,    // Damping factor on curvature

    // --- Hillside Topography & Terrain Compensation Parameters ---
    val terrainSideDraftGain: Double = 0.4            // Proactive uphill counter-steer deg per degree of side roll
)

/**
 * Input state provided to the NavigationEngine for guidance computation.
 */
data class NavigationInput(
    val currentPosition: Vec2,                   // Ground position (tractor rear axle pivot or antenna compensated)
    val headingDeg: Double,                      // Current vehicle heading in degrees [0, 360)
    val speedKmh: Double,                        // Ground speed in km/h
    val targetABLine: ABLine,                    // The reference AB guidance line
    val swathWidthMeters: Double = 6.0,          // Swath pass width in meters
    val forcedSwathIndex: Int? = null,           // Optional manual swath lock, or null for auto-select
    val dtSec: Double = 0.05,                    // Elapsed time step (default 50ms = 20Hz)
    val terrainRollDeg: Double = 0.0,            // Real-time ground slope roll from 0.5-mile topography DEM
    val isTerrainCompensationEnabled: Boolean = false, // Master switch for hillside slope compensation
    val terrainSideDraftGain: Double = 0.4,      // Uphill counter-steer gain in deg/slope
    val terrainSquare: com.example.agopengps.map.TerrainSquare? = null,
    val antennaHeightMeters: Double = 3.2
)

/**
 * Result produced by the NavigationEngine containing steering command and tracking telemetry.
 */
data class NavigationOutput(
    val targetSteerAngleDeg: Double,             // Commanded wheel steering angle (negative = left, positive = right)
    val crossTrackErrorMeters: Double,           // Signed lateral distance from path (positive = right, negative = left)
    val crossTrackErrorInches: Double,           // Lateral distance in U.S. Imperial inches
    val headingErrorDeg: Double,                 // Signed heading error from path direction [-180, +180]
    val activeSwathIndex: Int,                   // Swath pass index (0 = master AB line, ±1, ±2... = parallel passes)
    val swathOrigin: Vec2,                       // Point on the active swath line
    val swathDirection: Vec2,                    // Forward unit vector of the active swath
    val projectedPoint: Vec2,                    // Current position projected onto the active swath
    val isReversePass: Boolean,                  // True if vehicle is driving in opposite direction of original AB vector
    val algorithmUsed: SteeringAlgorithm,        // The algorithm used for this calculation
    // Detailed Stanley diagnostics
    val stanleyCrossTrackCorrectionDeg: Double = 0.0,
    val stanleyHeadingCorrectionDeg: Double = 0.0,
    val stanleyIntegralCorrectionDeg: Double = 0.0,
    val stanleyDerivativeCorrectionDeg: Double = 0.0,
    // Detailed Pure Pursuit diagnostics
    val purePursuitLookaheadDistanceMeters: Double = 0.0,
    val purePursuitLookaheadPoint: Vec2? = null,
    val purePursuitCurvature: Double = 0.0,
    // Hillside Topography Compensation Diagnostics
    val terrainRollDeg: Double = 0.0,
    val terrainUphillCounterSteerDeg: Double = 0.0
)

/**
 * Interface contract for agricultural guidance navigation engines.
 */
interface INavigationEngine {
    val config: NavigationEngineConfig
    fun calculate(input: NavigationInput): NavigationOutput
    fun calculateStanley(input: NavigationInput): NavigationOutput
    fun calculatePurePursuit(input: NavigationInput): NavigationOutput
    fun resetIntegral()
    fun reset()
}

/**
 * High-performance, modular Navigation Engine implementing the Stanley and Pure Pursuit
 * path-tracking algorithms for agricultural auto-steer systems.
 *
 * References:
 * - Stanford Stanley: Thrun et al., "Stanley: The Robot That Won the DARPA Grand Challenge", JFR 2006.
 * - Pure Pursuit: Coulter, R. Craig, "Implementation of the Pure Pursuit Path Tracking Algorithm", CMU-RI-TR-92-01.
 * - AgOpenGPS Guidance System: Brian Tischler, Open Source Precision Ag Navigation.
 */
class NavigationEngine(
    initialConfig: NavigationEngineConfig = NavigationEngineConfig()
) : INavigationEngine {

    override var config: NavigationEngineConfig = initialConfig
        private set

    // Internal state tracking
    private var integralErrorAccumulatorMetersSec: Double = 0.0
    private var previousCrossTrackErrorMeters: Double = 0.0
    private var isPreviousErrorValid: Boolean = false

    /**
     * Updates the engine configuration parameters.
     */
    fun updateConfig(newConfig: NavigationEngineConfig) {
        config = newConfig
    }

    /**
     * Sets the active steering algorithm.
     */
    fun setAlgorithm(algorithm: SteeringAlgorithm) {
        config = config.copy(algorithm = algorithm)
    }

    /**
     * Computes the steering angle and tracking metrics using the currently configured algorithm.
     */
    override fun calculate(input: NavigationInput): NavigationOutput {
        return when (config.algorithm) {
            SteeringAlgorithm.STANLEY -> calculateStanley(input)
            SteeringAlgorithm.PURE_PURSUIT -> calculatePurePursuit(input)
        }
    }

    /**
     * Resolves the active swath pass geometry (index, origin, direction, and projection).
     */
    fun calculateSwathGeometry(
        position: Vec2,
        tractorHeadingDeg: Double,
        abLine: ABLine,
        swathWidth: Double,
        forcedIndex: Int? = null
    ): SwathResult {
        val safeSwathWidth = max(swathWidth, 0.1)
        val toPivot = position - abLine.aLocal
        val perpDist = toPivot.dot(abLine.normalVector)

        val swathIndex = forcedIndex ?: round(perpDist / safeSwathWidth).toInt()
        val swathOrigin = abLine.aLocal + (abLine.normalVector * (swathIndex * safeSwathWidth))

        val deltaHeading = GeoUtils.normalizeAngleDeg(tractorHeadingDeg - abLine.headingDeg)
        val isReversePass = abs(deltaHeading) > 90.0

        val targetHeadingDeg: Double
        val activeNormal: Vec2
        val activeDir: Vec2

        if (isReversePass) {
            targetHeadingDeg = (abLine.headingDeg + 180.0) % 360.0
            activeDir = abLine.dirVector * -1.0
            activeNormal = activeDir.perpClockwise()
        } else {
            targetHeadingDeg = abLine.headingDeg
            activeDir = abLine.dirVector
            activeNormal = abLine.normalVector
        }

        val toPivotActive = position - swathOrigin
        val crossTrackError = toPivotActive.dot(activeNormal)
        val headingErrorDeg = GeoUtils.normalizeAngleDeg(tractorHeadingDeg - targetHeadingDeg)
        val projectedOnSwath = position.projectOntoLine(swathOrigin, activeDir)

        return SwathResult(
            activeSwathIndex = swathIndex,
            swathOrigin = swathOrigin,
            swathDir = activeDir,
            crossTrackErrorMeters = crossTrackError,
            headingErrorDeg = headingErrorDeg,
            targetHeadingDeg = targetHeadingDeg,
            projectedPoint = projectedOnSwath,
            isReversePass = isReversePass
        )
    }

    /**
     * Calculates steering command using the Stanley path-following controller.
     *
     * The Stanley controller combines:
     * 1. Heading error correction: delta_theta = -headingError
     * 2. Cross-track error correction: delta_e = -atan((k * e) / (v + v0))
     * 3. Integral error correction: delta_i = -ki * integral(e) dt (anti-windup limited)
     * 4. Derivative error damping: delta_d = -kd * (de / dt)
     *
     * Steering angle: delta = delta_theta + delta_e + delta_i - delta_d, clamped to [-maxSteer, maxSteer]
     */
    override fun calculateStanley(input: NavigationInput): NavigationOutput {
        val swath = calculateSwathGeometry(
            position = input.currentPosition,
            tractorHeadingDeg = input.headingDeg,
            abLine = input.targetABLine,
            swathWidth = input.swathWidthMeters,
            forcedIndex = input.forcedSwathIndex
        )

        val rawCte = swath.crossTrackErrorMeters
        val headingErrorDeg = swath.headingErrorDeg
        val headingErrorRad = Math.toRadians(headingErrorDeg)

        // Effective cross-track error with deadband
        val deadband = config.deadbandMeters
        val effectiveCte = when {
            abs(rawCte) <= deadband -> 0.0
            rawCte > 0 -> rawCte - deadband
            else -> rawCte + deadband
        }

        val speedMps = max(abs(input.speedKmh) / 3.6, 0.05)
        val dt = max(input.dtSec, 0.001)

        // Update integral accumulator with anti-windup clamping
        integralErrorAccumulatorMetersSec = (integralErrorAccumulatorMetersSec + (effectiveCte * dt))
            .coerceIn(-config.stanleyMaxIntegralDeg / max(config.stanleyIntegralGainKi, 0.001),
                       config.stanleyMaxIntegralDeg / max(config.stanleyIntegralGainKi, 0.001))

        // Derivative of cross-track error for damping
        val cteRate = if (isPreviousErrorValid) {
            (effectiveCte - previousCrossTrackErrorMeters) / dt
        } else {
            0.0
        }
        previousCrossTrackErrorMeters = effectiveCte
        isPreviousErrorValid = true

        // 1. Heading Correction (radians)
        val headingCorrectionRad = -headingErrorRad

        // 2. Cross-track Correction (radians)
        // Note: positive CTE means vehicle is right of swath -> needs to steer left (negative delta)
        val crossTrackCorrectionRad = -atan2(
            config.stanleyGainK * effectiveCte,
            speedMps + config.stanleySofteningSpeedMps
        )

        // 3. Integral Correction (radians)
        val integralCorrectionRad = -Math.toRadians(
            (config.stanleyIntegralGainKi * integralErrorAccumulatorMetersSec)
                .coerceIn(-config.stanleyMaxIntegralDeg, config.stanleyMaxIntegralDeg)
        )

        // 4. Derivative Damping (radians)
        val derivativeCorrectionRad = -(config.stanleyDerivativeGainKd * cteRate)

        // 5. Hillside Ground Slope Gravity Feedforward Bias with Predictive Lookahead (radians)
        // If tractor is tilted right on a hill (terrainRollDeg > 0), gravity pulls it downhill to the right.
        // Uphill counter-steer is negative (steer left uphill) to hold dead-center on the pass.
        val uphillCorrectionDeg = if (input.isTerrainCompensationEnabled) {
            val lookaheadDist = max(abs(input.speedKmh) / 3.6, 0.5) * 1.5 // 1.5-second time horizon
            val lookaheadPos = input.currentPosition + (swath.swathDir * lookaheadDist)
            val futureAttitude = input.terrainSquare?.getTerrainAttitude(
                pos = lookaheadPos,
                headingDeg = input.headingDeg,
                antennaHeightMeters = input.antennaHeightMeters,
                sideDraftGain = input.terrainSideDraftGain.takeIf { it > 0.0 } ?: config.terrainSideDraftGain
            )
            val gain = input.terrainSideDraftGain.takeIf { it > 0.0 } ?: config.terrainSideDraftGain
            if (futureAttitude != null) {
                val deltaRoll = futureAttitude.rollDeg - input.terrainRollDeg
                -(futureAttitude.rollDeg * gain) - (deltaRoll * 0.25)
            } else {
                -input.terrainRollDeg * gain
            }
        } else {
            0.0
        }
        val uphillCorrectionRad = Math.toRadians(uphillCorrectionDeg)

        // Sum components
        val totalSteerRad = headingCorrectionRad + crossTrackCorrectionRad + integralCorrectionRad + derivativeCorrectionRad + uphillCorrectionRad
        val rawSteerDeg = Math.toDegrees(totalSteerRad)

        // Reverse gear inversion (if tractor is moving backward)
        val finalSteerDeg = if (input.speedKmh < -0.1) {
            -rawSteerDeg
        } else {
            rawSteerDeg
        }.coerceIn(-config.maxSteerAngleDeg, config.maxSteerAngleDeg)

        return NavigationOutput(
            targetSteerAngleDeg = finalSteerDeg,
            crossTrackErrorMeters = rawCte,
            crossTrackErrorInches = rawCte * 39.3701,
            headingErrorDeg = headingErrorDeg,
            activeSwathIndex = swath.activeSwathIndex,
            swathOrigin = swath.swathOrigin,
            swathDirection = swath.swathDir,
            projectedPoint = swath.projectedPoint,
            isReversePass = swath.isReversePass,
            algorithmUsed = SteeringAlgorithm.STANLEY,
            stanleyCrossTrackCorrectionDeg = Math.toDegrees(crossTrackCorrectionRad),
            stanleyHeadingCorrectionDeg = Math.toDegrees(headingCorrectionRad),
            stanleyIntegralCorrectionDeg = Math.toDegrees(integralCorrectionRad),
            stanleyDerivativeCorrectionDeg = Math.toDegrees(derivativeCorrectionRad),
            terrainRollDeg = input.terrainRollDeg,
            terrainUphillCounterSteerDeg = uphillCorrectionDeg
        )
    }

    /**
     * Calculates steering command using the Pure Pursuit geometric arc controller.
     *
     * Pure Pursuit finds a target point on the path at lookahead distance L_d,
     * computes the curvature of the circular arc connecting the rear axle to the target,
     * and sets steering angle: delta = atan(curvature * wheelbase).
     */
    override fun calculatePurePursuit(input: NavigationInput): NavigationOutput {
        val swath = calculateSwathGeometry(
            position = input.currentPosition,
            tractorHeadingDeg = input.headingDeg,
            abLine = input.targetABLine,
            swathWidth = input.swathWidthMeters,
            forcedIndex = input.forcedSwathIndex
        )

        val rawCte = swath.crossTrackErrorMeters
        val speedMps = max(abs(input.speedKmh) / 3.6, 0.1)

        // Speed-dependent dynamic lookahead distance
        val lookaheadDist = (speedMps * config.purePursuitLookaheadGainSec)
            .coerceIn(config.purePursuitMinLookaheadMeters, config.purePursuitMaxLookaheadMeters)

        // Lookahead target point along active swath direction
        val lookaheadTarget = swath.projectedPoint + (swath.swathDir * lookaheadDist)

        // Vector from current position to target lookahead point
        val toTarget = lookaheadTarget - input.currentPosition
        val targetBearingRad = atan2(toTarget.x, toTarget.y)
        val targetBearingDeg = (Math.toDegrees(targetBearingRad) + 360.0) % 360.0

        // Alpha is angle between vehicle heading and vector to target point
        val alphaDeg = GeoUtils.normalizeAngleDeg(targetBearingDeg - input.headingDeg)
        val alphaRad = Math.toRadians(alphaDeg)

        val actualLookahead = toTarget.length().coerceAtLeast(0.5)

        // Curvature kappa = 2 * sin(alpha) / lookaheadDistance
        val curvature = (2.0 * sin(alphaRad)) / actualLookahead * config.purePursuitCurvatureDamping

        // Steering angle delta = atan(curvature * wheelbase)
        val steerAngleRad = atan(curvature * config.wheelbaseMeters)
        val baseSteerDeg = Math.toDegrees(steerAngleRad)

        // Hillside Ground Slope Gravity Feedforward Bias with Predictive Lookahead
        val uphillCorrectionDeg = if (input.isTerrainCompensationEnabled) {
            val terrainLookaheadDist = max(abs(input.speedKmh) / 3.6, 0.5) * 1.5
            val terrainLookaheadPos = input.currentPosition + (swath.swathDir * terrainLookaheadDist)
            val futureAttitude = input.terrainSquare?.getTerrainAttitude(
                pos = terrainLookaheadPos,
                headingDeg = input.headingDeg,
                antennaHeightMeters = input.antennaHeightMeters,
                sideDraftGain = input.terrainSideDraftGain.takeIf { it > 0.0 } ?: config.terrainSideDraftGain
            )
            val gain = input.terrainSideDraftGain.takeIf { it > 0.0 } ?: config.terrainSideDraftGain
            if (futureAttitude != null) {
                val deltaRoll = futureAttitude.rollDeg - input.terrainRollDeg
                -(futureAttitude.rollDeg * gain) - (deltaRoll * 0.25)
            } else {
                -input.terrainRollDeg * gain
            }
        } else {
            0.0
        }
        val rawSteerDeg = baseSteerDeg + uphillCorrectionDeg

        // Reverse gear handling
        val finalSteerDeg = if (input.speedKmh < -0.1) {
            -rawSteerDeg
        } else {
            rawSteerDeg
        }.coerceIn(-config.maxSteerAngleDeg, config.maxSteerAngleDeg)

        return NavigationOutput(
            targetSteerAngleDeg = finalSteerDeg,
            crossTrackErrorMeters = rawCte,
            crossTrackErrorInches = rawCte * 39.3701,
            headingErrorDeg = swath.headingErrorDeg,
            activeSwathIndex = swath.activeSwathIndex,
            swathOrigin = swath.swathOrigin,
            swathDirection = swath.swathDir,
            projectedPoint = swath.projectedPoint,
            isReversePass = swath.isReversePass,
            algorithmUsed = SteeringAlgorithm.PURE_PURSUIT,
            purePursuitLookaheadDistanceMeters = lookaheadDist,
            purePursuitLookaheadPoint = lookaheadTarget,
            purePursuitCurvature = curvature,
            terrainRollDeg = input.terrainRollDeg,
            terrainUphillCounterSteerDeg = uphillCorrectionDeg
        )
    }

    /**
     * Resets the integral error accumulator (e.g. when acquiring a new pass or turning).
     */
    override fun resetIntegral() {
        integralErrorAccumulatorMetersSec = 0.0
    }

    /**
     * Resets all internal controller state (integral and derivative filters).
     */
    override fun reset() {
        integralErrorAccumulatorMetersSec = 0.0
        previousCrossTrackErrorMeters = 0.0
        isPreviousErrorValid = false
    }
}

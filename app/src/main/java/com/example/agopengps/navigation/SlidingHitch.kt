package com.example.agopengps.navigation

import com.example.agopengps.io.GnssReport
import kotlin.math.*

/**
 * Active Implement Guidance / Sliding Hitch Type.
 * Corrects lateral implement drift on side-hills, curves, and contours
 * independently of tractor steering.
 */
enum class SlidingHitchType(val displayName: String, val typicalStrokeCm: Double) {
    THREE_POINT_SIDE_SHIFT("3-Point Side-Shift Frame", 30.0),
    SLIDING_DRAWBAR("Sliding Drawbar / Tongue", 25.0),
    STEERABLE_COULTER_DISC("Steerable Coulters / Discs", 20.0)
}

/**
 * Data connection source for the second GPS receiver mounted on the implement / sliding hitch.
 */
enum class SecondGpsSourceMode(val displayName: String) {
    UDP_PORT_SECONDARY("UDP Port 9998 (AgIO / Network)"),
    USB_SERIAL_SECONDARY("USB Serial (Teensy / F9P / FTDI)"),
    UM982_DUAL_ANTENNA_SLAVE("UM982 / F9P Implement Slave Antenna"),
    SIMULATOR_IMPLEMENT("Simulated Trailing Implement (Active Kinematics)")
}

/**
 * Configuration for the active sliding hitch controller.
 */
data class SlidingHitchConfig(
    val isEnabled: Boolean = true,
    val hitchType: SlidingHitchType = SlidingHitchType.THREE_POINT_SIDE_SHIFT,
    val sourceMode: SecondGpsSourceMode = SecondGpsSourceMode.SIMULATOR_IMPLEMENT,
    val udpPort: Int = 9998,
    val usbBaudRate: Int = 115200,
    val maxStrokeCm: Double = 25.0,            // ±25 cm (10 inches) lateral travel limit
    val deadbandCm: Double = 0.5,              // Ignore errors under 0.5 cm to avoid valve jitter
    val proportionalGain: Double = 1.4,        // Kp gain for hydraulic displacement
    val integralGain: Double = 0.06,           // Ki gain for steady side-hill drift
    val hydraulicSpeedCmPerSec: Double = 9.0,  // Maximum cylinder extension speed in cm/s
    val antennaOffsetAheadMeters: Double = 0.0,// Distance from toolbar center to antenna (ahead is positive)
    val antennaLateralOffsetMeters: Double = 0.0, // Lateral mount offset from implement centerline
    val invertDirection: Boolean = false,      // Invert cylinder direction if plumbed opposite
    val autoCenterOnDisengage: Boolean = true, // Center cylinder when auto-guidance is disengaged
    val isAutoHitchEngaged: Boolean = true     // Automatic active side-shifting engaged
)

/**
 * Dynamic operational state of the sliding hitch and secondary GNSS receiver.
 */
data class SlidingHitchState(
    val isConnected: Boolean = true,
    val secondGpsFixQuality: FixQuality = FixQuality.RTK_FIX,
    val secondGpsSatellites: Int = 24,
    val secondGpsHdop: Double = 0.6,
    val secondGpsGeo: GeoPoint = GeoPoint(44.9141, -95.7153, 310.0),
    val secondGpsLocalPos: Vec2 = Vec2(0.0, 0.0),
    val implementCrossTrackErrorMeters: Double = 0.0, // Positive = Right of line, Negative = Left
    val currentHitchShiftCm: Double = 0.0,            // Current lateral cylinder stroke (positive = right)
    val targetHitchShiftCm: Double = 0.0,             // Commanded cylinder stroke
    val hydraulicPwmOutput: Int = 0,                  // Valve PWM command: -255 (full left) to +255 (full right)
    val isStrokeLimitReached: Boolean = false,        // True if cylinder is pegged at stroke limit
    val totalCorrectionsCount: Long = 0L,
    val lastPacketTimeMs: Long = System.currentTimeMillis(),
    val statusSummary: String = "ACTIVE RTK IMPLEMENT GUIDANCE"
) {
    val implementCrossTrackErrorCm: Double get() = implementCrossTrackErrorMeters * 100.0
    val strokePercentage: Float get() = (currentHitchShiftCm / 25.0).coerceIn(-1.0, 1.0).toFloat()
}

/**
 * Active Implement Guidance Controller.
 * Computes the real-time implement cross-track error using the secondary GPS receiver
 * and drives the sliding hitch hydraulic valve to keep the implement centered on the AB line,
 * even when the tractor crabs, wanders, or encounters side-hill roll.
 */
class SlidingHitchController {

    private var integralErrorAccumulator: Double = 0.0
    private var simulatedCrabAngleDeg: Double = 1.4 // Trailing implement slip on side-hills
    private var trailingImplementHeadingDeg: Double = 0.0
    private var lastTractorPos: Vec2? = null

    fun resetIntegral() {
        integralErrorAccumulator = 0.0
    }

    /**
     * Executes one cycle of the active sliding hitch control loop.
     */
    fun update(
        dtSec: Double,
        tractorPivot: Vec2,
        tractorHeadingDeg: Double,
        tractorSpeedKmh: Double,
        hitchLengthBehindAxle: Double,
        implementOffsetBehindHitch: Double,
        currentABLine: ABLine?,
        activeSwathIndex: Int,
        swathWidth: Double,
        config: SlidingHitchConfig,
        currentState: SlidingHitchState,
        externalSecondGpsReport: GnssReport?,
        originGeo: GeoPoint
    ): SlidingHitchState {
        val dt = dtSec.coerceIn(0.01, 0.25)
        val headingRad = Math.toRadians(tractorHeadingDeg)
        val sinH = sin(headingRad)
        val cosH = cos(headingRad)
        val fwdVec = Vec2(sinH, cosH)
        val rightVec = Vec2(cosH, -sinH)

        // 1. Calculate physical hitch pin location on tractor drawbar / 3-point
        val hitchPinPos = tractorPivot - (fwdVec * abs(hitchLengthBehindAxle))

        // 2. Determine Implement Position and Second GPS Antenna Location
        var actualImplementAntennaPos: Vec2
        var fixQuality = currentState.secondGpsFixQuality
        var satellites = currentState.secondGpsSatellites
        var hdop = currentState.secondGpsHdop
        var secondGeo = currentState.secondGpsGeo

        val currentShiftMeters = (currentState.currentHitchShiftCm / 100.0)

        if (externalSecondGpsReport != null && config.sourceMode != SecondGpsSourceMode.SIMULATOR_IMPLEMENT) {
            // Real second GNSS hardware connected (via UDP or USB)
            secondGeo = externalSecondGpsReport.geoPoint
            actualImplementAntennaPos = GeoUtils.geoToLocalMeters(secondGeo, originGeo)
            fixQuality = externalSecondGpsReport.fixQuality
            satellites = externalSecondGpsReport.satellites
            hdop = externalSecondGpsReport.hdop
        } else {
            // High-fidelity trailing implement physics simulation
            // Trailing toolbar lags tractor heading and crabs slightly on side-hill/ground resistance
            val targetTrailingHeading = tractorHeadingDeg + (if (abs(tractorSpeedKmh) > 0.5) simulatedCrabAngleDeg else 0.0)
            trailingImplementHeadingDeg += (targetTrailingHeading - trailingImplementHeadingDeg) * (dt * 3.0)

            val trailRad = Math.toRadians(trailingImplementHeadingDeg)
            val trailFwd = Vec2(sin(trailRad), cos(trailRad))
            val trailRight = Vec2(cos(trailRad), -sin(trailRad))

            // Nominal toolbar position before sliding hitch extension
            val nominalToolbarCenter = hitchPinPos - (trailFwd * implementOffsetBehindHitch)

            // The sliding hitch shifts the implement laterally along the hitch carriage
            val shiftedToolbarCenter = nominalToolbarCenter + (trailRight * currentShiftMeters)

            actualImplementAntennaPos = shiftedToolbarCenter +
                    (trailFwd * config.antennaOffsetAheadMeters) +
                    (trailRight * config.antennaLateralOffsetMeters)

            secondGeo = GeoUtils.localMetersToGeo(actualImplementAntennaPos, originGeo)
            fixQuality = FixQuality.RTK_FIX
            satellites = 26
            hdop = 0.5
        }

        // 3. Compute Implement Cross-Track Error relative to active AB Swath
        var implementXteMeters = 0.0
        var targetShiftCm = currentState.currentHitchShiftCm

        if (currentABLine != null) {
            val lineDir = currentABLine.dirVector
            val lineNormal = currentABLine.normalVector
            val swathOrigin = currentABLine.aLocal + (lineNormal * (activeSwathIndex * swathWidth))

            // Vector from swath baseline point to implement antenna
            val toImplement = actualImplementAntennaPos - swathOrigin
            // Perpendicular lateral distance to line (positive = right of line, negative = left of line)
            implementXteMeters = toImplement.x * lineNormal.x + toImplement.y * lineNormal.y
            val implementXteCm = implementXteMeters * 100.0

            if (config.isEnabled && config.isAutoHitchEngaged && abs(tractorSpeedKmh) > 0.3) {
                // To correct an implement deviation to the right (+cm), the hitch must shift left (-cm)
                val rawErrorToCorrect = -implementXteCm * (if (config.invertDirection) -1.0 else 1.0)

                val effectiveError = if (abs(rawErrorToCorrect) < config.deadbandCm) {
                    0.0
                } else {
                    rawErrorToCorrect
                }

                // Proportional-Integral controller for hydraulic slide
                if (abs(implementXteCm) < config.maxStrokeCm * 1.5) {
                    integralErrorAccumulator += effectiveError * dt
                    integralErrorAccumulator = integralErrorAccumulator.coerceIn(-config.maxStrokeCm * 0.4, config.maxStrokeCm * 0.4)
                }

                val commandedCorrection = (effectiveError * config.proportionalGain) + (integralErrorAccumulator * config.integralGain)
                // Desired target stroke
                targetShiftCm = (currentState.currentHitchShiftCm + commandedCorrection * (dt * 4.0))
                    .coerceIn(-config.maxStrokeCm, config.maxStrokeCm)
            } else if (!config.isAutoHitchEngaged && config.autoCenterOnDisengage) {
                // Auto-center sliding hitch when disengaged
                targetShiftCm = 0.0
                integralErrorAccumulator = 0.0
            }
        } else {
            // No active AB line: center sliding hitch
            if (config.autoCenterOnDisengage) {
                targetShiftCm = 0.0
            }
        }

        // 4. Hydraulic Cylinder Rate Limiting (Actuator Speed)
        val maxDeltaPerCycle = config.hydraulicSpeedCmPerSec * dt
        val errorToTarget = targetShiftCm - currentState.currentHitchShiftCm
        val step = errorToTarget.coerceIn(-maxDeltaPerCycle, maxDeltaPerCycle)
        val newHitchShiftCm = (currentState.currentHitchShiftCm + step)
            .coerceIn(-config.maxStrokeCm, config.maxStrokeCm)

        // 5. Valve PWM output (-255 to +255)
        val hydraulicPwm = if (abs(errorToTarget) > 0.1) {
            val normalizedRatio = (step / maxDeltaPerCycle).coerceIn(-1.0, 1.0)
            (normalizedRatio * 255.0).roundToInt().coerceIn(-255, 255)
        } else {
            0
        }

        val limitReached = abs(newHitchShiftCm) >= (config.maxStrokeCm - 0.2)

        val status = when {
            !config.isEnabled -> "HITCH DISABLED"
            !config.isAutoHitchEngaged -> "MANUAL (CENTERED: ${String.format("%.1f", newHitchShiftCm)} cm)"
            limitReached -> "STROKE LIMIT (PEGGED ${String.format("%.1f", newHitchShiftCm)} cm)"
            abs(implementXteMeters * 100.0) < 1.0 -> "RTK LOCKED ON LINE (±${String.format("%.1f", abs(implementXteMeters * 100.0))} cm)"
            else -> "CORRECTING XTE ${String.format("%+.1f", implementXteMeters * 100.0)} cm -> SHIFT ${String.format("%+.1f", newHitchShiftCm)} cm"
        }

        return currentState.copy(
            isConnected = true,
            secondGpsFixQuality = fixQuality,
            secondGpsSatellites = satellites,
            secondGpsHdop = hdop,
            secondGpsGeo = secondGeo,
            secondGpsLocalPos = actualImplementAntennaPos,
            implementCrossTrackErrorMeters = implementXteMeters,
            currentHitchShiftCm = newHitchShiftCm,
            targetHitchShiftCm = targetShiftCm,
            hydraulicPwmOutput = hydraulicPwm,
            isStrokeLimitReached = limitReached,
            totalCorrectionsCount = currentState.totalCorrectionsCount + 1,
            lastPacketTimeMs = System.currentTimeMillis(),
            statusSummary = status
        )
    }

    /**
     * Builds AgOpenGPS standard Implement Steering PGN 230 (0xE6) binary packet
     * to transmit to physical Teensy/ESP32 sliding hitch hydraulic controller.
     */
    fun buildImplementSteerPacket(
        shiftCm: Double,
        pwm: Int,
        isAutoActive: Boolean
    ): ByteArray {
        val packet = ByteArray(14)
        packet[0] = 0x80.toByte() // AgOpenGPS header 0x80
        packet[1] = 0x81.toByte() // 0x81
        packet[2] = 0x7F.toByte() // Source: Cab App
        packet[3] = 0xE6.toByte() // PGN 230: Implement Steering
        packet[4] = 8            // Length

        val shiftMm = (shiftCm * 10.0).roundToInt().coerceIn(-32768, 32767)
        packet[5] = (shiftMm and 0xFF).toByte()
        packet[6] = ((shiftMm shr 8) and 0xFF).toByte()

        val clampedPwm = (pwm + 255).coerceIn(0, 510) // 255 is center
        packet[7] = (clampedPwm and 0xFF).toByte()
        packet[8] = ((clampedPwm shr 8) and 0xFF).toByte()

        packet[9] = if (isAutoActive) 1 else 0
        packet[10] = 0
        packet[11] = 0
        packet[12] = 0

        // Checksum
        var sum = 0
        for (i in 2..12) {
            sum += (packet[i].toInt() and 0xFF)
        }
        packet[13] = (sum and 0xFF).toByte()
        return packet
    }
}

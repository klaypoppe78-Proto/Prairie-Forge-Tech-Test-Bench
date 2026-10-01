package com.example.agopengps.navigation

/**
 * RTK GNSS Fix quality matching NMEA 0183 standard and AgOpenGPS PAOGI.
 */
enum class FixQuality(val code: Int, val label: String) {
    INVALID(0, "No Fix"),
    GPS(1, "GPS Single"),
    DGPS(2, "WAAS / DGPS"),
    TABLET_INTERNAL(3, "Tablet GNSS"),
    RTK_FIX(4, "RTK Fixed"),
    RTK_FLOAT(5, "RTK Float"),
    SIMULATOR(9, "Simulator");

    companion object {
        fun fromCode(code: Int): FixQuality = when (code) {
            1 -> GPS
            2 -> DGPS
            3 -> TABLET_INTERNAL
            4 -> RTK_FIX
            5 -> RTK_FLOAT
            9 -> SIMULATOR
            else -> INVALID
        }
    }
}

/**
 * Type of agricultural implement attachment.
 */
enum class ImplementType(val label: String) {
    THREE_POINT_MOUNTED("3-Point Hitch Mounted"),
    TRAILING_TOOLBAR("Following Toolbar (Trailing)")
}

/**
 * Proportional hydraulic or motor steering valve types.
 * Fully supports Danfoss proportional valve setups from Trimble Nav Controller II / FM-750 replacements.
 */
enum class SteerValveType(val label: String) {
    DUAL_SOLENOID_PWM("Dual-Solenoid PWM / Trimble Manifold (PWM)"),
    PROPORTIONAL_DANFOSS_PVEA("Danfoss PVEA / PVG Proportional Valve"),
    PROPORTIONAL_PWM_VALVE("Proportional PWM Hydraulic Valve"),
    MOTOR_CYTRON("Electric Motor - Cytron MD13S"),
    MOTOR_IBT2("Electric Motor - IBT-2 Driver")
}

/**
 * GNSS Receiver Type on the AgOpenGPS All-In-One (AIO) Board.
 */
enum class GnssReceiverType(val label: String) {
    AUTO_DETECT("Auto-Detect Receiver"),
    UNICORE_UM982("Unicore UM982 (Dual-Antenna RTK Heading)"),
    UBLOX_F9P("u-blox ZED-F9P (RTK GNSS)"),
    AGOPENGPS_PAOGI("AgOpenGPS AIO Fused (\$PAOGI)")
}

/**
 * Steer valve tuning for New Holland 8870 and Trimble Nav II / FM-750 hydraulic valve replacements.
 */
data class SteerValveConfig(
    val valveType: SteerValveType = SteerValveType.DUAL_SOLENOID_PWM,
    val proportionalGainKp: Int = 65,      // Proportional Gain Kp (typically 50-80 for Danfoss valves)
    val integralGainKi: Int = 10,          // Integral Gain Ki
    val derivativeGainKd: Int = 20,        // Derivative Gain Kd
    val minPwmDeadband: Int = 30,          // Valve crack-open deadband (minimum PWM to start oil flow)
    val maxPwmLimit: Int = 225,            // Maximum PWM limit (prevents hydraulic shock on steering cylinders)
    val wasZeroOffsetDeg: Double = 0.0,    // Wheel Angle Sensor zero center calibration in degrees
    val wasCountsPerDeg: Int = 125,        // Wheel Angle Sensor counts per degree (standard Trimble WAS)
    val isWasInverted: Boolean = false,    // Inverts WAS reading if sensor is mounted reverse
    val isMotorInverted: Boolean = false,  // Inverts valve output direction (left vs right)
    val disengagePressurePsi: Int = 320,   // Hydraulic kickout pressure threshold when operator turns steering wheel
    val isNewHolland8870Preset: Boolean = true
)

/**
 * Tractor vehicle geometry and kinematic parameters.
 * Defaults match the New Holland 8870 Genesis tractor (112 in wheelbase, 82 in track, 10.5 ft cab height).
 */
data class VehicleConfig(
    val wheelbase: Double = 2.845,         // Wheelbase in meters (112.0 inches = 2.845 m for NH 8870)
    val trackWidth: Double = 2.083,        // Wheel track width in meters (82.0 inches = 2.083 m)
    val antennaHeight: Double = 3.20,      // GPS antenna height in meters (~10.5 ft cab roof)
    val antennaPivotOffset: Double = 0.20, // Antenna offset ahead of rear axle in meters (~8 inches)
    val hitchLength: Double = -1.22,       // Rear axle to 3-point / drawbar pin in meters (~4.0 ft behind axle)
    val maxSteerAngleDeg: Double = 42.0,   // Maximum steering angle in degrees (NH 8870 MFWD / SuperSteer)
    // Internal tablet mount 3D offset relative to rear axle center
    val tabletForwardOffset: Double = 0.45, // Tablet mount distance ahead of rear axle in meters (~18 inches)
    val tabletRightOffset: Double = 0.25,   // Tablet lateral offset right of centerline in meters (~10 inches)
    val tabletHeightAboveAxle: Double = 1.90 // Tablet height above rear axle center in meters (~6.2 ft)
)

/**
 * Agricultural implement (toolbar, boom sprayer, planter, seeder, tillage) configuration.
 */
data class ImplementConfig(
    val implementType: ImplementType = ImplementType.THREE_POINT_MOUNTED,
    val toolWidth: Double = 9.144,         // Total working width in meters (default 30.0 feet = 9.144 m)
    val offsetBehindTractor: Double = 0.914, // Distance from tractor 3-point/hitch to toolbar in meters (3.0 ft)
    val numSections: Int = 6,              // Number of independent boom/row sections
    val overlap: Double = 0.15,            // Swath overlap margin in meters (~6 inches)
    val lateralOffset: Double = 0.0,       // Lateral offset from tractor center in meters (positive = right)
    val isMasterActive: Boolean = true,    // Master section switch
    val isAutoSectionControl: Boolean = true // Automatic section shut-off over applied areas
) {
    /**
     * Effective swath width accounting for overlap.
     */
    val swathWidth: Double get() = (toolWidth - overlap).coerceAtLeast(0.5)

    /**
     * Calculates the lateral span (left offset, right offset in meters relative to tool center)
     * for each individual section.
     */
    fun getSectionSpans(): List<Pair<Double, Double>> {
        val count = numSections.coerceIn(1, 48)
        val sectionWidth = toolWidth / count
        val halfTotal = toolWidth / 2.0
        return (0 until count).map { i ->
            val left = -halfTotal + i * sectionWidth + lateralOffset
            val right = left + sectionWidth
            Pair(left, right)
        }
    }
}

enum class ControllerType {
    STANLEY,
    PURE_PURSUIT
}

/**
 * Auto-steer controller tuning parameters.
 */
data class GuidanceSettings(
    val controllerType: ControllerType = ControllerType.STANLEY,
    val stanleyGain: Double = 1.25,             // Proportional cross-track gain k
    val stanleyIntegralGain: Double = 0.08,     // Integral gain for constant drift / side-hill bias
    val purePursuitLookaheadGain: Double = 0.85, // Lookahead time coefficient in seconds
    val purePursuitMinLookahead: Double = 2.0,  // Minimum lookahead in meters
    val purePursuitMaxLookahead: Double = 12.0, // Maximum lookahead in meters
    val tabletGpsLookaheadSec: Double = 3.0,    // Extended lookahead time for Tablet GPS WAAS (1.5s - 5.0s)
    val deadbandCm: Double = 2.0,               // Steer deadband in centimeters
    val rollZeroOffsetDeg: Double = 0.0,        // Calibration zero point for cab roll IMU
    val terrainSideDraftGain: Double = 0.4      // Degrees uphill steer per degree of side-hill roll
)

/**
 * Real-time dynamic state of the tractor.
 */
data class VehicleState(
    val geoPosition: GeoPoint = GeoPoint(44.9141, -95.7153, 310.0), // Default center location (Montevideo, MN Farm)
    val localPivotPosition: Vec2 = Vec2(0.0, 0.0),                  // Ground rear axle position in local meters
    val headingDeg: Double = 0.0,                                   // Heading in degrees (0 = North, 90 = East)
    val speedKmh: Double = 0.0,                                     // Forward ground speed in km/h
    val rollDeg: Double = 0.0,                                      // Cab roll angle (positive = tilt right)
    val pitchDeg: Double = 0.0,                                     // Cab pitch angle
    val baselineLengthMeters: Double = 1.25,                        // Dual antenna baseline separation
    val actualSteerAngleDeg: Double = 0.0,                          // Current wheel angle from angle sensor
    val targetSteerAngleDeg: Double = 0.0,                          // Guidance commanded steering angle
    val isAutoSteerEngaged: Boolean = false,                        // True when Auto-Steer solenoid/motor is active
    val fixQuality: FixQuality = FixQuality.SIMULATOR,
    val satellites: Int = 18,
    val ageOfCorrectionSec: Double = 0.8,                           // RTK latency
    val crossTrackErrorMeters: Double = 0.0,                        // Distance off-line (negative = left, positive = right)
    val headingErrorDeg: Double = 0.0,                              // Difference from target swath heading
    val activeSwathIndex: Int = 0,                                  // Current swath pass index (0 = base AB line)
    val sectionStates: List<Boolean> = List(6) { true }             // Current ON/OFF state of each section
) {
    val speedMps: Double get() = speedKmh / 3.6
    val crossTrackErrorCm: Double get() = crossTrackErrorMeters * 100.0
}

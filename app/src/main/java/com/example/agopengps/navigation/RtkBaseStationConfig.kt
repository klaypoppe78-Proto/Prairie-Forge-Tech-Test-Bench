package com.example.agopengps.navigation

/**
 * RTK Base Station configuration & survey location.
 * Allows setting base station coordinates at user-defined latitude/longitude,
 * current vehicle RTK position, or local easting/northing offsets.
 */
data class RtkBaseStationConfig(
    val isEnabled: Boolean = true,
    val name: String = "Farmstead RTK Base",
    val latitude: Double = 44.954200,
    val longitude: Double = -96.082500,
    val elevationMslMeters: Double = 317.0, // ~1040 ft MSL
    val relativeX: Double = -240.0,
    val relativeY: Double = -220.0,
    val correctionSource: String = "NTRIP Caster & 915MHz LoRa Radio",
    val casterMountpoint: String = "LACQUIPARLE_RTK",
    val radioFrequencyMhz: Double = 915.0,
    val antennaHeightMeters: Double = 12.0 // ~40 ft tower / shop roof
) {
    val elevationFeet: Double get() = elevationMslMeters * 3.28084
    val antennaHeightFeet: Double get() = antennaHeightMeters * 3.28084
}

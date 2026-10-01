package com.example.agopengps.navigation

import androidx.compose.ui.graphics.Color

/**
 * Type of field hazard or obstacle flagged on the guidance map.
 */
enum class ObstacleType(
    val label: String,
    val defaultRadiusMeters: Double,
    val colorHex: Long,
    val description: String
) {
    ROCK("Rock / Boulder", 2.5, 0xFFFF9800, "Surface or subsurface field stone/boulder"),
    TILE_INLET("Tile Inlet / Drain", 3.0, 0xFF00E5FF, "Field drainage intake / tile standpipe"),
    POWER_POLE("Power Pole / Guy Wire", 4.5, 0xFFFF5252, "Utility power pole, transmission tower, or guy wire"),
    WASHOUT("Washout / Ditch", 5.0, 0xFFFF7043, "Erosion washout, deep rut, or open ditch"),
    TREE_STUMP("Tree / Stump", 3.5, 0xFF8D6E63, "Tree, tree line corner, or hidden stump"),
    IRRIGATION_RISER("Irrigation Riser", 3.0, 0xFF448AFF, "Center pivot riser or hydrologic hydrant"),
    CUSTOM("Hazard / Obstacle", 3.0, 0xFFFFD700, "General point of interest or custom hazard");

    val displayColor: Color get() = Color(colorHex)
}

/**
 * Data model for a field obstacle/hazard pinned on the field.
 */
data class FieldObstacle(
    val id: Long = 0,
    val fieldId: Long = 0,
    val name: String,
    val type: ObstacleType = ObstacleType.ROCK,
    val localPos: Vec2,
    val geoPos: GeoPoint,
    val radiusMeters: Double = 3.0,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * In-Cab display theme mode.
 */
enum class DisplayThemeMode(val label: String, val shortName: String) {
    STANDARD_AG("AgOpenGPS Emerald", "STD"),
    SUNLIGHT_HIGH_CONTRAST("Sunlight High-Contrast", "SUN"),
    CAB_OLED_NIGHT("OLED Cab Night", "NIGHT")
}

/**
 * Turn guidance indicator for headland transitions.
 */
enum class TurnDirection(val label: String) {
    NONE("STRAIGHT"),
    TURN_LEFT("TURN LEFT"),
    TURN_RIGHT("TURN RIGHT"),
    U_TURN("U-TURN")
}

data class HeadlandTurnInfo(
    val direction: TurnDirection = TurnDirection.NONE,
    val distanceToTurnMeters: Double = 0.0,
    val targetSwathIndex: Int = 0,
    val isApproachingHeadland: Boolean = false
)

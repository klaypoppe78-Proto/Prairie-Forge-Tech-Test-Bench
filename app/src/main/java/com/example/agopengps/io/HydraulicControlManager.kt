package com.example.agopengps.io

import org.json.JSONArray
import org.json.JSONObject

/**
 * 3-Point Hitch position and operating state.
 */
enum class ThreePointState(val label: String) {
    LOWERED("Lowered (Working)"),
    RAISED("Raised (Headland)"),
    FLOAT("Float Mode"),
    TRANSPORT("Transport Lock")
}

data class ThreePointHitchConfig(
    val isEnabled: Boolean = true,
    val state: ThreePointState = ThreePointState.LOWERED,
    val currentHeightPercent: Int = 15, // 0 = lowest ground depth, 100 = full raise
    val targetWorkDepthPercent: Int = 15,
    val targetHeadlandRaisePercent: Int = 85,
    val raiseTimeSeconds: Double = 2.2,
    val lowerTimeSeconds: Double = 1.8,
    val autoWorkswitchEnabled: Boolean = true // Auto start/stop sections when height passes threshold
)

/**
 * SCV (Selective Control Valve) state for hydraulic remotes.
 */
enum class ScvValveState(val label: String) {
    NEUTRAL("Neutral"),
    EXTEND("Extend"),
    RETRACT("Retract"),
    FLOAT("Float")
}

data class ScvValve(
    val id: Int, // 1 to 4
    val name: String,
    val state: ScvValveState = ScvValveState.NEUTRAL,
    val flowRatePercent: Int = 75,
    val detentTimeSeconds: Double = 3.0,
    val isContinuousFlow: Boolean = false,
    val activeTimeRemainingMs: Long = 0L
)

/**
 * Triggers that can initiate actions (In-cab soft keys, workswitches, or automated events).
 */
enum class CabTrigger(
    val code: String,
    val title: String,
    val category: String
) {
    SOFTKEY_1("SOFTKEY_1", "Cab Soft-Key #1", "In-Cab Soft Keys"),
    SOFTKEY_2("SOFTKEY_2", "Cab Soft-Key #2", "In-Cab Soft Keys"),
    SOFTKEY_3("SOFTKEY_3", "Cab Soft-Key #3", "In-Cab Soft Keys"),
    SOFTKEY_4("SOFTKEY_4", "Cab Soft-Key #4", "In-Cab Soft Keys"),
    SOFTKEY_5("SOFTKEY_5", "Cab Soft-Key #5", "In-Cab Soft Keys"),
    SOFTKEY_6("SOFTKEY_6", "Cab Soft-Key #6", "In-Cab Soft Keys"),
    THREE_POINT_DROP("THREE_POINT_DROP", "When 3-Point Hitch Lowers", "Hitch Automation"),
    THREE_POINT_RAISE("THREE_POINT_RAISE", "When 3-Point Hitch Raises", "Hitch Automation"),
    HEADLAND_ENTER("HEADLAND_ENTER", "On Headland Entry", "Field Automation"),
    HEADLAND_EXIT("HEADLAND_EXIT", "On Headland Exit", "Field Automation"),
    U_TURN_START("U_TURN_START", "On U-Turn Start", "Steering Automation"),
    U_TURN_FINISH("U_TURN_FINISH", "On U-Turn Align", "Steering Automation"),
    CUSTOM_TRIGGER("CUSTOM_TRIGGER", "Custom User Trigger", "Custom");

    companion object {
        fun fromCode(code: String): CabTrigger {
            return values().firstOrNull { it.code.equals(code, ignoreCase = true) } ?: SOFTKEY_1
        }
    }
}

/**
 * Actions that can be assigned to soft keys or automated triggers.
 */
enum class CabAction(
    val code: String,
    val title: String,
    val category: String
) {
    LOWER_3_POINT("LOWER_3_POINT", "Lower 3-Point to Work Depth", "3-Point Hitch"),
    RAISE_3_POINT("RAISE_3_POINT", "Raise 3-Point to Headland Height", "3-Point Hitch"),
    TOGGLE_3_POINT("TOGGLE_3_POINT", "Toggle 3-Point (Raise / Lower)", "3-Point Hitch"),
    EXTEND_SCV_1("EXTEND_SCV_1", "SCV 1: Extend (e.g. Lift Cultivator Gangs)", "Hydraulics SCV 1"),
    RETRACT_SCV_1("RETRACT_SCV_1", "SCV 1: Retract (e.g. Lower Cultivator Gangs)", "Hydraulics SCV 1"),
    FLOAT_SCV_1("FLOAT_SCV_1", "SCV 1: Float", "Hydraulics SCV 1"),
    EXTEND_SCV_2("EXTEND_SCV_2", "SCV 2: Extend (e.g. Downforce / Markers)", "Hydraulics SCV 2"),
    RETRACT_SCV_2("RETRACT_SCV_2", "SCV 2: Retract", "Hydraulics SCV 2"),
    EXTEND_SCV_3("EXTEND_SCV_3", "SCV 3: Extend (e.g. Sliding Hitch Left)", "Hydraulics SCV 3"),
    RETRACT_SCV_3("RETRACT_SCV_3", "SCV 3: Retract (e.g. Sliding Hitch Right)", "Hydraulics SCV 3"),
    EXTEND_SCV_4("EXTEND_SCV_4", "SCV 4: Extend / Auxiliary", "Hydraulics SCV 4"),
    RETRACT_SCV_4("RETRACT_SCV_4", "SCV 4: Retract / Auxiliary", "Hydraulics SCV 4"),
    TOGGLE_AUTO_STEER("TOGGLE_AUTO_STEER", "Toggle Auto-Steer Engage", "Guidance"),
    TOGGLE_SECTION_MASTER("TOGGLE_SECTION_MASTER", "Toggle Implement Sections Master", "Implement"),
    TOGGLE_CULTIVATOR_SPLIT("TOGGLE_CULTIVATOR_SPLIT", "Toggle 12R Cultivator Pass (Left/Right)", "Organic Cultivator"),
    NUDGE_HALF_ROW_11_INCH("NUDGE_HALF_ROW_11_INCH", "Nudge 11\" (Half 22\" Row)", "Organic Nudge"),
    NUDGE_FULL_ROW_22_INCH("NUDGE_FULL_ROW_22_INCH", "Nudge 22\" (Full 22\" Row)", "Organic Nudge"),
    EXECUTE_HEADLAND_SEQUENCE("EXECUTE_HEADLAND_SEQUENCE", "Run Complete Headland Sequence (Lift+Turn+Lower)", "Sequences"),
    SNAP_AB_LINE("SNAP_AB_LINE", "Snap AB Line to Tractor", "Guidance"),
    CENTER_GPS("CENTER_GPS", "Recenter GPS Map", "Display"),
    CYCLE_THEME("CYCLE_THEME", "Cycle OLED Night / Emerald Theme", "Display");

    companion object {
        fun fromCode(code: String): CabAction {
            return values().firstOrNull { it.code.equals(code, ignoreCase = true) } ?: LOWER_3_POINT
        }
    }
}

/**
 * Keybinding / Trigger Mapping definition.
 */
data class TriggerBinding(
    val id: Long = 0L,
    val label: String,
    val trigger: CabTrigger,
    val action: CabAction,
    val delayMs: Long = 0L,
    val isEnabled: Boolean = true,
    val isCustom: Boolean = false
)

object HydraulicDefaults {
    fun createDefaultValves(): List<ScvValve> {
        return listOf(
            ScvValve(1, "SCV 1: Cultivator Gangs Lift", ScvValveState.NEUTRAL, flowRatePercent = 80, detentTimeSeconds = 3.0),
            ScvValve(2, "SCV 2: Planter Downforce", ScvValveState.NEUTRAL, flowRatePercent = 65, detentTimeSeconds = 2.5),
            ScvValve(3, "SCV 3: Sliding Hitch Ram", ScvValveState.NEUTRAL, flowRatePercent = 50, detentTimeSeconds = 2.0),
            ScvValve(4, "SCV 4: Auxiliary Remote", ScvValveState.NEUTRAL, flowRatePercent = 100, isContinuousFlow = true)
        )
    }

    fun createDefaultTriggerBindings(): List<TriggerBinding> {
        return listOf(
            TriggerBinding(1, "SoftKey 1: 3-Point Hitch Toggle", CabTrigger.SOFTKEY_1, CabAction.TOGGLE_3_POINT),
            TriggerBinding(2, "SoftKey 2: SCV 1 Lift Cultivator", CabTrigger.SOFTKEY_2, CabAction.EXTEND_SCV_1),
            TriggerBinding(3, "SoftKey 3: SCV 1 Lower Cultivator", CabTrigger.SOFTKEY_3, CabAction.RETRACT_SCV_1),
            TriggerBinding(4, "SoftKey 4: Toggle 12R Cultivator Pass", CabTrigger.SOFTKEY_4, CabAction.TOGGLE_CULTIVATOR_SPLIT),
            TriggerBinding(5, "SoftKey 5: 11\" Half-Row Nudge", CabTrigger.SOFTKEY_5, CabAction.NUDGE_HALF_ROW_11_INCH),
            TriggerBinding(6, "SoftKey 6: Toggle Auto-Steer", CabTrigger.SOFTKEY_6, CabAction.TOGGLE_AUTO_STEER),
            TriggerBinding(7, "Auto: Lower 3-Point on Headland Exit", CabTrigger.HEADLAND_EXIT, CabAction.LOWER_3_POINT, delayMs = 300),
            TriggerBinding(8, "Auto: Raise 3-Point on Headland Entry", CabTrigger.HEADLAND_ENTER, CabAction.RAISE_3_POINT, delayMs = 0),
            TriggerBinding(9, "Auto: Engage Sections when 3-Point Lowers", CabTrigger.THREE_POINT_DROP, CabAction.TOGGLE_SECTION_MASTER, delayMs = 200)
        )
    }
}

package com.example.agopengps.io

import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Gamepad hardware button identifier.
 */
enum class GamepadButton(
    val code: String,
    val label: String,
    val ps4Label: String,
    val keyCodes: List<Int>
) {
    CROSS("CROSS", "Button A / Cross", "✕ Cross", listOf(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER)),
    CIRCLE("CIRCLE", "Button B / Circle", "○ Circle", listOf(KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK)),
    SQUARE("SQUARE", "Button X / Square", "□ Square", listOf(KeyEvent.KEYCODE_BUTTON_X)),
    TRIANGLE("TRIANGLE", "Button Y / Triangle", "△ Triangle", listOf(KeyEvent.KEYCODE_BUTTON_Y)),
    L1("L1", "Left Bumper (L1)", "L1 Bumper", listOf(KeyEvent.KEYCODE_BUTTON_L1)),
    R1("R1", "Right Bumper (R1)", "R1 Bumper", listOf(KeyEvent.KEYCODE_BUTTON_R1)),
    L2("L2", "Left Trigger (L2)", "L2 Trigger", listOf(KeyEvent.KEYCODE_BUTTON_L2)),
    R2("R2", "Right Trigger (R2)", "R2 Trigger", listOf(KeyEvent.KEYCODE_BUTTON_R2)),
    DPAD_UP("DPAD_UP", "D-Pad Up", "D-Pad ↑", listOf(KeyEvent.KEYCODE_DPAD_UP)),
    DPAD_DOWN("DPAD_DOWN", "D-Pad Down", "D-Pad ↓", listOf(KeyEvent.KEYCODE_DPAD_DOWN)),
    DPAD_LEFT("DPAD_LEFT", "D-Pad Left", "D-Pad ←", listOf(KeyEvent.KEYCODE_DPAD_LEFT)),
    DPAD_RIGHT("DPAD_RIGHT", "D-Pad Right", "D-Pad →", listOf(KeyEvent.KEYCODE_DPAD_RIGHT)),
    L3_THUMB("L3_THUMB", "Left Stick Click (L3)", "L3 Click", listOf(KeyEvent.KEYCODE_BUTTON_THUMBL)),
    R3_THUMB("R3_THUMB", "Right Stick Click (R3)", "R3 Click", listOf(KeyEvent.KEYCODE_BUTTON_THUMBR)),
    SHARE_SELECT("SHARE_SELECT", "Share / Back Button", "Share", listOf(KeyEvent.KEYCODE_BUTTON_SELECT)),
    OPTIONS_START("OPTIONS_START", "Options / Start Button", "Options", listOf(KeyEvent.KEYCODE_BUTTON_START)),
    TOUCHPAD("TOUCHPAD", "Touchpad Button", "Touchpad", listOf(KeyEvent.KEYCODE_BUTTON_MODE, KeyEvent.KEYCODE_MEDIA_RECORD));

    companion object {
        fun fromKeyCode(keyCode: Int): GamepadButton? {
            return values().firstOrNull { it.keyCodes.contains(keyCode) }
        }

        fun fromCode(code: String): GamepadButton? {
            return values().firstOrNull { it.code.equals(code, ignoreCase = true) }
        }
    }
}

/**
 * Agricultural actions that can be mapped to gamepad buttons.
 */
enum class AgAction(
    val code: String,
    val title: String,
    val category: String,
    val defaultButton: GamepadButton?
) {
    TOGGLE_AUTO_STEER("TOGGLE_AUTO_STEER", "Toggle Auto-Steer Engage", "Steering", GamepadButton.CROSS),
    SNAP_AB_LINE("SNAP_AB_LINE", "Snap AB Line to Tractor", "Steering", GamepadButton.TRIANGLE),
    NUDGE_LEFT_1_INCH("NUDGE_LEFT_1_INCH", "Nudge 1\" Left", "Swath Nudge", GamepadButton.DPAD_LEFT),
    NUDGE_RIGHT_1_INCH("NUDGE_RIGHT_1_INCH", "Nudge 1\" Right", "Swath Nudge", GamepadButton.DPAD_RIGHT),
    NUDGE_HALF_ROW_11_INCH("NUDGE_HALF_ROW_11_INCH", "Nudge 11\" (Half 22\" Row)", "Organic 22\" Nudge", GamepadButton.L1),
    NUDGE_FULL_ROW_22_INCH("NUDGE_FULL_ROW_22_INCH", "Nudge 22\" (Full 22\" Row)", "Organic 22\" Nudge", GamepadButton.R1),
    CYCLE_SWATH_LEFT("CYCLE_SWATH_LEFT", "Skip Pass -1 Left", "Swath Control", GamepadButton.L2),
    CYCLE_SWATH_RIGHT("CYCLE_SWATH_RIGHT", "Skip Pass +1 Right", "Swath Control", GamepadButton.R2),
    TOGGLE_CULTIVATOR_SPLIT("TOGGLE_CULTIVATOR_SPLIT", "Toggle 12R Cultivator Pass (A/B)", "Organic Cultivator", GamepadButton.SQUARE),
    TOGGLE_SECTION_MASTER("TOGGLE_SECTION_MASTER", "Toggle Implement Master On/Off", "Implement", GamepadButton.CIRCLE),
    TRIGGER_U_TURN("TRIGGER_U_TURN", "Trigger Auto U-Turn", "Steering", GamepadButton.OPTIONS_START),
    CENTER_GPS("CENTER_GPS", "Recenter GPS Map", "Navigation", GamepadButton.SHARE_SELECT),
    RECORD_PATH_POINT("RECORD_PATH_POINT", "Drop Hazard Flag / Breadcrumb", "Mapping", GamepadButton.R3_THUMB),
    CYCLE_CAMERA_VIEW("CYCLE_CAMERA_VIEW", "Cycle Camera 3D / 2D / North", "Display", GamepadButton.L3_THUMB),
    CYCLE_DISPLAY_THEME("CYCLE_DISPLAY_THEME", "Cycle OLED Night / Emerald Theme", "Display", GamepadButton.TOUCHPAD),
    SLIDING_HITCH_LEFT("SLIDING_HITCH_LEFT", "Sliding Hitch Manual Left", "Sliding Hitch", null),
    SLIDING_HITCH_RIGHT("SLIDING_HITCH_RIGHT", "Sliding Hitch Manual Right", "Sliding Hitch", null),
    ZERO_WAS("ZERO_WAS", "Zero Wheel Angle Sensor", "Calibration", null);

    companion object {
        fun fromCode(code: String): AgAction? {
            return values().firstOrNull { it.code.equals(code, ignoreCase = true) }
        }
    }
}

/**
 * Active button binding state.
 */
data class GamepadBinding(
    val id: Long = 0L,
    val button: GamepadButton,
    val action: AgAction,
    val customLabel: String = "",
    val isCustom: Boolean = false
) {
    val displayLabel: String
        get() = customLabel.ifBlank { action.title }
}

/**
 * Default Gamepad Profiles.
 */
object GamepadProfileFactory {
    fun createDefaultPs4Bindings(): List<GamepadBinding> {
        return listOf(
            GamepadBinding(1, GamepadButton.CROSS, AgAction.TOGGLE_AUTO_STEER),
            GamepadBinding(2, GamepadButton.CIRCLE, AgAction.TOGGLE_SECTION_MASTER),
            GamepadBinding(3, GamepadButton.SQUARE, AgAction.TOGGLE_CULTIVATOR_SPLIT),
            GamepadBinding(4, GamepadButton.TRIANGLE, AgAction.SNAP_AB_LINE),
            GamepadBinding(5, GamepadButton.L1, AgAction.NUDGE_HALF_ROW_11_INCH),
            GamepadBinding(6, GamepadButton.R1, AgAction.NUDGE_FULL_ROW_22_INCH),
            GamepadBinding(7, GamepadButton.L2, AgAction.CYCLE_SWATH_LEFT),
            GamepadBinding(8, GamepadButton.R2, AgAction.CYCLE_SWATH_RIGHT),
            GamepadBinding(9, GamepadButton.DPAD_LEFT, AgAction.NUDGE_LEFT_1_INCH),
            GamepadBinding(10, GamepadButton.DPAD_RIGHT, AgAction.NUDGE_RIGHT_1_INCH),
            GamepadBinding(11, GamepadButton.DPAD_UP, AgAction.CENTER_GPS),
            GamepadBinding(12, GamepadButton.DPAD_DOWN, AgAction.CYCLE_DISPLAY_THEME),
            GamepadBinding(13, GamepadButton.OPTIONS_START, AgAction.TRIGGER_U_TURN),
            GamepadBinding(14, GamepadButton.SHARE_SELECT, AgAction.CENTER_GPS),
            GamepadBinding(15, GamepadButton.L3_THUMB, AgAction.CYCLE_CAMERA_VIEW),
            GamepadBinding(16, GamepadButton.R3_THUMB, AgAction.RECORD_PATH_POINT)
        )
    }
}

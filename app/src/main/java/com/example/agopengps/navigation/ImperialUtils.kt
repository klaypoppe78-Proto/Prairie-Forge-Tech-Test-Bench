package com.example.agopengps.navigation

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * U.S. Imperial Unit Conversion Utilities for precision agriculture.
 * Converts internal metric calculations (meters, km/h, m2) to
 * standard American farm units (feet, inches, mph, acres).
 */
object ImperialUtils {

    const val METERS_TO_FEET = 3.280839895
    const val FEET_TO_METERS = 0.3048
    const val METERS_TO_INCHES = 39.37007874
    const val INCHES_TO_METERS = 0.0254
    const val KMH_TO_MPH = 0.6213711922
    const val MPH_TO_KMH = 1.609344
    const val M2_TO_ACRES = 0.00024710538
    const val ACRES_TO_M2 = 4046.8564224

    fun metersToFeet(meters: Double): Double = meters * METERS_TO_FEET
    fun feetToMeters(feet: Double): Double = feet * FEET_TO_METERS

    fun metersToInches(meters: Double): Double = meters * METERS_TO_INCHES
    fun inchesToMeters(inches: Double): Double = inches * INCHES_TO_METERS

    fun kmhToMph(kmh: Double): Double = kmh * KMH_TO_MPH
    fun mphToKmh(mph: Double): Double = mph * MPH_TO_KMH

    fun m2ToAcres(m2: Double): Double = m2 * M2_TO_ACRES
    fun acresToM2(acres: Double): Double = acres * ACRES_TO_M2

    /**
     * Formats speed in mph (e.g. "5.4 mph")
     */
    fun formatSpeedMph(kmh: Double): String {
        val mph = kmhToMph(kmh)
        return String.format(Locale.US, "%.1f mph", abs(mph))
    }

    /**
     * Formats speed value number only in mph
     */
    fun formatSpeedValue(kmh: Double): String {
        val mph = kmhToMph(kmh)
        return String.format(Locale.US, "%.1f", abs(mph))
    }

    /**
     * Formats distance in feet (e.g. "30.0 ft")
     */
    fun formatFeet(meters: Double, decimals: Int = 1): String {
        val feet = metersToFeet(meters)
        return String.format(Locale.US, "%.${decimals}f ft", feet)
    }

    /**
     * Formats distance in inches (e.g. "1.4 in")
     */
    fun formatInches(meters: Double, decimals: Int = 1): String {
        val inches = metersToInches(meters)
        return String.format(Locale.US, "%.${decimals}f in", inches)
    }

    /**
     * Formats distance in feet & inches (e.g. "9 ft 4 in")
     */
    fun formatFeetAndInches(meters: Double): String {
        val totalInches = (metersToInches(meters)).roundToInt()
        val feet = totalInches / 12
        val inches = totalInches % 12
        return if (inches == 0) "${feet} ft" else "${feet} ft ${inches} in"
    }

    /**
     * Formats cross-track error with directional indicator in inches:
     * e.g. "◀ 0.8 in", "0.0 in", "▶ 1.4 in"
     */
    fun formatCrossTrackImperial(crossTrackMeters: Double): String {
        val inches = metersToInches(crossTrackMeters)
        val absInches = abs(inches)
        return when {
            absInches < 0.2 -> "0.0 in"
            inches < 0.0 -> String.format(Locale.US, "◀ %.1f in", absInches)
            else -> String.format(Locale.US, "▶ %.1f in", absInches)
        }
    }

    /**
     * Formats area in acres (e.g. "14.8 ac")
     */
    fun formatAcres(acres: Double): String {
        return String.format(Locale.US, "%.2f ac", acres)
    }
}

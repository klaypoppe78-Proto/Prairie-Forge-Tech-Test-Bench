package com.example

import com.example.agopengps.navigation.DisplayThemeMode
import com.example.agopengps.navigation.VehicleState
import org.junit.Assert.assertEquals
import org.junit.Test

class GreetingScreenshotTest {

    @Test
    fun vehicleState_and_themeMode_defaults() {
        val state = VehicleState(
            crossTrackErrorMeters = 0.024,
            actualSteerAngleDeg = -2.1,
            targetSteerAngleDeg = -2.3,
            speedKmh = 7.5
        )
        assertEquals(0.024, state.crossTrackErrorMeters, 0.0001)
        assertEquals(DisplayThemeMode.CAB_OLED_NIGHT, DisplayThemeMode.valueOf("CAB_OLED_NIGHT"))
    }
}

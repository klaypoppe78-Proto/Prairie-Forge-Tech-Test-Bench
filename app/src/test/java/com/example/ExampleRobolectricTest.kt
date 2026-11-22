package com.example

import com.example.agopengps.map.MapTileProvider
import com.example.agopengps.map.SatelliteTileRepository
import com.example.agopengps.navigation.ABLine
import com.example.agopengps.navigation.DisplayThemeMode
import com.example.agopengps.navigation.GeoPoint
import com.example.agopengps.navigation.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleRobolectricTest {

    @Test
    fun `satellite tile repository computes render zoom up to z21 and fetch zoom within safe max`() {
        val repo = SatelliteTileRepository()
        val fetchZoom = repo.getSafeZoomLevel(
            pixelsPerMeter = 80.0f,
            provider = MapTileProvider.ESRI_WORLD_IMAGERY,
            latitude = 42.0308
        )
        val renderZoom = repo.getRenderZoomLevel(
            pixelsPerMeter = 80.0f,
            provider = MapTileProvider.ESRI_WORLD_IMAGERY,
            latitude = 42.0308
        )
        assertTrue("Fetch zoom should be capped at safe maxZoom (17)", fetchZoom <= 17)
        assertTrue("Render zoom should scale up for high zoom (19..21)", renderZoom >= 19)
    }

    @Test
    fun `ab line creation from point A and point B computes valid heading and direction`() {
        val pointA = Vec2(0.0, 0.0)
        val pointB = Vec2(0.0, 50.0)
        val geoA = GeoPoint(42.0308, -93.6319)
        val geoB = GeoPoint(42.0312, -93.6319)
        val abLine = ABLine.fromPoints(pointA, pointB, geoA, geoB, "Pass 1")
        assertEquals(0.0, abLine.headingDeg, 0.01)
        assertEquals(1.0, abLine.dirVector.y, 0.001)
    }

    @Test
    fun `oled night mode display theme exists`() {
        val mode = DisplayThemeMode.CAB_OLED_NIGHT
        assertEquals("CAB_OLED_NIGHT", mode.name)
    }
}

package com.example

import com.example.agopengps.navigation.AppliedSwathSegment
import com.example.agopengps.navigation.CoverageChunkStore
import com.example.agopengps.navigation.Vec2
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class CoverageChunkStoreTest {

    /**
     * Simulates 120+ acres (~490,000 m^2) of painted 12m swaths across a 700m x 700m field
     * (8,000+ segments) and verifies that 2D viewport culling and 3D frustum culling return
     * only the local visible subset rather than iterating the full field.
     */
    @Test
    fun testLargeField120AcreChunkingAndCulling() {
        val store = CoverageChunkStore(chunkSizeMeters = 60.0)
        val swathWidth = 12.0
        val segLength = 5.0
        val fieldWidthM = 720.0  // 60 passes x 12m
        val fieldLengthM = 700.0 // 140 segments x 5m = 8,400 segments (~124.5 acres)

        var totalInserted = 0
        var passX = 0.0
        while (passX < fieldWidthM) {
            var y = 0.0
            while (y < fieldLengthM) {
                val ls = Vec2(passX, y)
                val rs = Vec2(passX + swathWidth, y)
                val le = Vec2(passX, y + segLength)
                val re = Vec2(passX + swathWidth, y + segLength)
                val center = Vec2(passX + swathWidth * 0.5, y + segLength * 0.5)
                store.addSegment(
                    AppliedSwathSegment(
                        leftStart = ls,
                        rightStart = rs,
                        leftEnd = le,
                        rightEnd = re,
                        center = center,
                        radius = swathWidth * 0.5
                    )
                )
                totalInserted++
                y += segLength
            }
            passX += swathWidth
        }

        assertEquals(8400, totalInserted)
        assertEquals(8400, store.totalSegmentCount)
        assertTrue("Expected > 100 spatial chunks for 124+ acre field", store.chunkCount >= 100)

        // 1. 2D Viewport Culling around center (360, 350) with +/-50m window
        var visible2DCount = 0
        store.forEachVisibleIn2D(
            minX = 310.0,
            maxX = 410.0,
            minY = 300.0,
            maxY = 400.0
        ) {
            visible2DCount++
        }
        assertTrue("2D viewport should see local segments", visible2DCount > 100)
        assertTrue("2D culling must reject majority of 124-acre field ($visible2DCount / $totalInserted)", visible2DCount < 600)

        // 2. 3D Frustum Culling from (360, 100) looking North (heading 0 deg) with far=150m, lateral=80m
        val headingRad = Math.toRadians(0.0)
        var visible3DCount = 0
        store.forEachVisibleIn3DFrustum(
            pivotX = 360.0,
            pivotY = 100.0,
            sinH = sin(headingRad),
            cosH = cos(headingRad),
            nearLimit = -20.0,
            farLimit = 150.0,
            maxLateral = 80.0
        ) {
            visible3DCount++
        }
        assertTrue("3D frustum should see forward segments", visible3DCount > 100)
        assertTrue("3D frustum culling must discard distant & behind chunks ($visible3DCount / $totalInserted)", visible3DCount < 1200)

        // 3. Clear resets store cleanly
        val versionBeforeClear = store.version
        store.clear()
        assertEquals(0, store.totalSegmentCount)
        assertEquals(0, store.chunkCount)
        assertNull(store.lastSegment)
        assertTrue(store.version > versionBeforeClear)
    }
}

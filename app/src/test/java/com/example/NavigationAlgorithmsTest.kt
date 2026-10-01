package com.example

import com.example.agopengps.io.NmeaParser
import com.example.agopengps.navigation.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class NavigationAlgorithmsTest {

    @Test
    fun testAntennaRollCompensation() {
        val vehicle = VehicleConfig(
            wheelbase = 3.0,
            antennaHeight = 3.0,
            antennaPivotOffset = 0.0
        )
        val antennaPos = Vec2(0.0, 0.0)
        val headingDeg = 0.0 // Facing North

        // Tilted 5 degrees to the right (positive roll)
        val rollDeg = 5.0
        val pivot = GuidanceAlgorithms.compensateAntennaPosition(
            antennaLocal = antennaPos,
            headingDeg = headingDeg,
            rollDeg = rollDeg,
            vehicle = vehicle
        )

        // When tilted right while facing North, the cab roof moves East (right, +x).
        // Therefore the true ground axle pivot is to the West (left, -x).
        assertTrue("Pivot x should be negative due to right roll", pivot.x < 0)
        val expectedShift = 3.0 * Math.sin(Math.toRadians(5.0))
        assertEquals(-expectedShift, pivot.x, 0.05)
    }

    @Test
    fun testABLineTrackingAndStanleySteering() {
        val a = Vec2(0.0, -100.0)
        val b = Vec2(0.0, 100.0)
        val aGeo = GeoPoint(41.0, -87.0)
        val bGeo = GeoPoint(41.01, -87.0)
        val abLine = ABLine.fromPoints(a, b, aGeo, bGeo)

        val swathWidth = 9.0
        // Tractor is at x = 0.5m (50cm to the right of line), heading North (0 deg)
        val tractorPos = Vec2(0.5, 10.0)
        val tracking = GuidanceAlgorithms.calculateSwathTracking(
            pivotPosition = tractorPos,
            tractorHeadingDeg = 0.0,
            abLine = abLine,
            swathWidth = swathWidth
        )

        assertEquals(0, tracking.activeSwathIndex)
        assertEquals(0.5, tracking.crossTrackErrorMeters, 0.001)
        assertEquals(0.0, tracking.headingErrorDeg, 0.001)

        // Stanley Controller should command negative steer angle (steer left)
        val vehicle = VehicleConfig()
        val settings = GuidanceSettings(stanleyGain = 1.25)
        val steerAngle = GuidanceAlgorithms.calculateStanleySteering(
            crossTrackErrorMeters = tracking.crossTrackErrorMeters,
            headingErrorDeg = tracking.headingErrorDeg,
            speedKmh = 7.2, // 2 m/s
            settings = settings,
            vehicle = vehicle
        )

        assertTrue("Tractor to the right of line should steer left (negative angle)", steerAngle < 0)
        assertTrue("Steer angle should be within vehicle limits", abs(steerAngle) <= vehicle.maxSteerAngleDeg)
    }

    @Test
    fun testPurePursuitSteering() {
        val a = Vec2(0.0, -100.0)
        val b = Vec2(0.0, 100.0)
        val aGeo = GeoPoint(41.0, -87.0)
        val bGeo = GeoPoint(41.01, -87.0)
        val abLine = ABLine.fromPoints(a, b, aGeo, bGeo)

        val vehicle = VehicleConfig(wheelbase = 2.85)
        val settings = GuidanceSettings()

        // Tractor is at x = 1.0m, heading North
        val steerAngle = GuidanceAlgorithms.calculatePurePursuitSteering(
            pivotPosition = Vec2(1.0, 0.0),
            tractorHeadingDeg = 0.0,
            swathOrigin = Vec2(0.0, 0.0),
            swathDir = Vec2(0.0, 1.0),
            speedKmh = 7.2,
            settings = settings,
            vehicle = vehicle
        )

        assertTrue("Pure pursuit should steer towards line (steer left)", steerAngle < 0)
    }

    @Test
    fun testNmeaParserPaogi() {
        val paogi = "\$PAOGI,123456.00,42.123456,-88.654321,4,18,0.8,240.5,0.6,1.25,-0.50,185.4,0.1*45"
        val report = NmeaParser.parse(paogi)

        assertNotNull(report)
        assertEquals(42.123456, report!!.geoPoint.latitude, 0.00001)
        assertEquals(-88.654321, report.geoPoint.longitude, 0.00001)
        assertEquals(FixQuality.RTK_FIX, report.fixQuality)
        assertEquals(18, report.satellites)
        assertEquals(1.25, report.rollDeg, 0.01)
        assertEquals(185.4, report.headingDeg ?: 0.0, 0.1)
    }

    @Test
    fun testAPlusLineGeneration() {
        val aLocal = Vec2(10.0, 20.0)
        val aGeo = GeoPoint(41.0, -87.0)
        val heading = 90.0 // Due East

        val aPlus = ABLine.fromAPlus(aLocal, heading, aGeo, "East A+")

        assertEquals(90.0, aPlus.headingDeg, 0.001)
        assertTrue(aPlus.isAPlusLine)
        // Vector pointing East should have x = 1, y = 0
        assertEquals(1.0, aPlus.dirVector.x, 0.001)
        assertEquals(0.0, aPlus.dirVector.y, 0.001)
    }

    @Test
    fun testCurveContourTracking() {
        val pts = listOf(
            Vec2(0.0, 0.0),
            Vec2(0.0, 50.0),
            Vec2(10.0, 100.0),
            Vec2(30.0, 150.0)
        )
        val curve = CurveLine(name = "Contour 1", points = pts)

        // Tractor is at x = 2.0, y = 25.0
        val res = GuidanceAlgorithms.calculateCurveTracking(
            pivotPosition = Vec2(2.0, 25.0),
            tractorHeadingDeg = 0.0,
            curveLine = curve,
            swathWidth = 6.0
        )

        assertNotNull(res)
        assertEquals(0, res!!.activeSwathIndex)
        assertEquals(2.0, res.crossTrackErrorMeters, 0.1)
    }

    @Test
    fun testShoelaceAreaAndHeadland() {
        // 100m x 100m square = 10,000 m2
        val square = listOf(
            Vec2(0.0, 0.0),
            Vec2(100.0, 0.0),
            Vec2(100.0, 100.0),
            Vec2(0.0, 100.0)
        )

        val areaM2 = GuidanceAlgorithms.calculatePolygonAreaM2(square)
        assertEquals(10000.0, areaM2, 0.1)

        val headland = GuidanceAlgorithms.generateHeadlandPolygon(square, 10.0)
        assertEquals(4, headland.size)
        val headlandArea = GuidanceAlgorithms.calculatePolygonAreaM2(headland)
        assertTrue("Inner headland area must be smaller than boundary", headlandArea < areaM2)
    }

    @Test
    fun testSectionOverrides() {
        val vehicle = VehicleConfig()
        val implement = ImplementConfig(toolWidth = 12.0, numSections = 4)
        val overrides = listOf(
            SectionOverride.FORCED_ON,
            SectionOverride.FORCED_OFF,
            SectionOverride.AUTO,
            SectionOverride.AUTO
        )

        val status = GuidanceAlgorithms.calculateImplementSections(
            pivotPosition = Vec2(0.0, 0.0),
            tractorHeadingDeg = 0.0,
            vehicle = vehicle,
            implement = implement,
            fieldBoundary = null,
            appliedPolygons = emptyList(),
            sectionOverrides = overrides
        )

        assertEquals(4, status.sectionStates.size)
        assertTrue("Section 1 is FORCED_ON", status.sectionStates[0])
        assertFalse("Section 2 is FORCED_OFF", status.sectionStates[1])
    }

    @Test
    fun testCentrifugalRollCompensation() {
        val rawRoll = 5.0
        val speedKmh = 14.4 // 4.0 m/s
        val yawRateRad = 0.1 // turning right

        val compensatedRoll = GuidanceAlgorithms.calculateCentrifugalCompensatedRoll(
            rawAccelRollDeg = rawRoll,
            speedKmh = speedKmh,
            yawRateRadPerSec = yawRateRad
        )

        // Centrifugal acceleration during a turn creates apparent tilt; compensation corrects it
        assertNotEquals(rawRoll, compensatedRoll, 0.001)
    }

    @Test
    fun testTabletPositionOffsetCompensation() {
        val tabletPos = Vec2(0.0, 0.0)
        val headingDeg = 0.0 // North
        val vehicle = VehicleConfig(
            tabletForwardOffset = 1.5,
            tabletRightOffset = 0.5,
            tabletHeightAboveAxle = 2.0
        )

        val compensated = GuidanceAlgorithms.compensateTabletPosition(
            tabletLocal = tabletPos,
            headingDeg = headingDeg,
            rollDeg = 0.0,
            vehicle = vehicle
        )

        // When tablet is 1.5m forward and 0.5m right facing North, ground pivot behind and left
        assertEquals(-0.5, compensated.x, 0.01)
        assertEquals(-1.5, compensated.y, 0.01)
    }
}

package com.example

import com.example.agopengps.navigation.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

/**
 * Unit tests verifying the NavigationEngine class with the Stanley and Pure Pursuit algorithms.
 */
class NavigationEngineTest {

    private lateinit var engine: NavigationEngine
    private lateinit var testABLine: ABLine

    @Before
    fun setUp() {
        engine = NavigationEngine(
            NavigationEngineConfig(
                wheelbaseMeters = 2.845,
                maxSteerAngleDeg = 42.0,
                deadbandMeters = 0.02,
                stanleyGainK = 1.25,
                stanleyIntegralGainKi = 0.08,
                stanleyDerivativeGainKd = 0.1,
                purePursuitLookaheadGainSec = 0.85,
                purePursuitMinLookaheadMeters = 2.0,
                purePursuitMaxLookaheadMeters = 12.0
            )
        )

        // North-South line through x = 0 (origin)
        val aLocal = Vec2(0.0, -100.0)
        val bLocal = Vec2(0.0, 100.0)
        val aGeo = GeoPoint(44.9141, -95.7153)
        val bGeo = GeoPoint(44.9159, -95.7153)
        testABLine = ABLine.fromPoints(aLocal, bLocal, aGeo, bGeo, name = "Test North-South AB")
    }

    @Test
    fun testInitializationAndConfig() {
        assertNotNull(engine.config)
        assertEquals(2.845, engine.config.wheelbaseMeters, 0.001)
        assertEquals(42.0, engine.config.maxSteerAngleDeg, 0.001)
        assertEquals(SteeringAlgorithm.STANLEY, engine.config.algorithm)

        engine.setAlgorithm(SteeringAlgorithm.PURE_PURSUIT)
        assertEquals(SteeringAlgorithm.PURE_PURSUIT, engine.config.algorithm)
    }

    @Test
    fun testSwathGeometryCalculation() {
        val swathWidth = 6.0
        // Position on master line
        val onLinePos = Vec2(0.0, 10.0)
        val swath0 = engine.calculateSwathGeometry(onLinePos, 0.0, testABLine, swathWidth)
        assertEquals(0, swath0.activeSwathIndex)
        assertEquals(0.0, swath0.crossTrackErrorMeters, 0.001)
        assertEquals(0.0, swath0.headingErrorDeg, 0.001)
        assertFalse(swath0.isReversePass)

        // Position 6 meters to the right (Pass +1)
        val pass1Pos = Vec2(6.0, 20.0)
        val swath1 = engine.calculateSwathGeometry(pass1Pos, 0.0, testABLine, swathWidth)
        assertEquals(1, swath1.activeSwathIndex)
        assertEquals(0.0, swath1.crossTrackErrorMeters, 0.001)

        // Position 12 meters to the left (Pass -2)
        val passMinus2Pos = Vec2(-12.0, 30.0)
        val swathMinus2 = engine.calculateSwathGeometry(passMinus2Pos, 0.0, testABLine, swathWidth)
        assertEquals(-2, swathMinus2.activeSwathIndex)
        assertEquals(0.0, swathMinus2.crossTrackErrorMeters, 0.001)

        // Reverse pass (facing South, heading 180 deg)
        val reversePos = Vec2(0.0, 50.0)
        val swathReverse = engine.calculateSwathGeometry(reversePos, 180.0, testABLine, swathWidth)
        assertTrue(swathReverse.isReversePass)
        assertEquals(180.0, swathReverse.targetHeadingDeg, 0.001)
        assertEquals(0.0, swathReverse.headingErrorDeg, 0.001)
    }

    @Test
    fun testStanleyOnLineZeroSteer() {
        val input = NavigationInput(
            currentPosition = Vec2(0.0, 10.0),
            headingDeg = 0.0,
            speedKmh = 10.0,
            targetABLine = testABLine,
            swathWidthMeters = 6.0
        )

        val output = engine.calculateStanley(input)
        assertEquals(0.0, output.crossTrackErrorMeters, 0.001)
        assertEquals(0.0, output.headingErrorDeg, 0.001)
        assertEquals(0.0, output.targetSteerAngleDeg, 0.05)
        assertEquals(SteeringAlgorithm.STANLEY, output.algorithmUsed)
    }

    @Test
    fun testStanleyRightOfLineSteersLeft() {
        // Vehicle is 0.50m (50 cm) to the right of the line, facing North (0 deg)
        val input = NavigationInput(
            currentPosition = Vec2(0.50, 10.0),
            headingDeg = 0.0,
            speedKmh = 10.0,
            targetABLine = testABLine,
            swathWidthMeters = 6.0
        )

        val output = engine.calculateStanley(input)
        assertEquals(0.50, output.crossTrackErrorMeters, 0.001)
        assertEquals(19.685, output.crossTrackErrorInches, 0.01)
        // Since vehicle is to the right (+CTE), it must steer LEFT (negative angle) to acquire the line
        assertTrue("Steer angle must be negative to correct right error", output.targetSteerAngleDeg < 0.0)
        assertTrue("Cross-track correction component should be negative", output.stanleyCrossTrackCorrectionDeg < 0.0)
    }

    @Test
    fun testStanleyLeftOfLineSteersRight() {
        // Vehicle is 0.50m to the left of the line, facing North (0 deg)
        val input = NavigationInput(
            currentPosition = Vec2(-0.50, 10.0),
            headingDeg = 0.0,
            speedKmh = 10.0,
            targetABLine = testABLine,
            swathWidthMeters = 6.0
        )

        val output = engine.calculateStanley(input)
        assertEquals(-0.50, output.crossTrackErrorMeters, 0.001)
        // Since vehicle is to the left (-CTE), it must steer RIGHT (positive angle)
        assertTrue("Steer angle must be positive to correct left error", output.targetSteerAngleDeg > 0.0)
        assertTrue("Cross-track correction component should be positive", output.stanleyCrossTrackCorrectionDeg > 0.0)
    }

    @Test
    fun testStanleyHeadingCorrection() {
        // Vehicle is exactly on the line (x = 0), but angled 10 degrees to the East/Right (heading = 10.0)
        val input = NavigationInput(
            currentPosition = Vec2(0.0, 10.0),
            headingDeg = 10.0,
            speedKmh = 10.0,
            targetABLine = testABLine,
            swathWidthMeters = 6.0
        )

        val output = engine.calculateStanley(input)
        assertEquals(10.0, output.headingErrorDeg, 0.01)
        // Must steer left (negative angle) to counter the 10-degree right heading deviation
        assertTrue("Must steer left when pointing right of line", output.targetSteerAngleDeg < 0.0)
        assertEquals(-10.0, output.stanleyHeadingCorrectionDeg, 0.05)
    }

    @Test
    fun testStanleyDeadband() {
        // Offset is 1.5 cm, which is within the 2 cm deadband
        val input = NavigationInput(
            currentPosition = Vec2(0.015, 10.0),
            headingDeg = 0.0,
            speedKmh = 10.0,
            targetABLine = testABLine,
            swathWidthMeters = 6.0
        )

        val output = engine.calculateStanley(input)
        // Within deadband, crossTrackCorrection should be exactly 0
        assertEquals(0.0, output.stanleyCrossTrackCorrectionDeg, 0.0001)
        assertEquals(0.0, output.targetSteerAngleDeg, 0.0001)
    }

    @Test
    fun testStanleyIntegralAccumulationAndReset() {
        engine.reset()

        // Apply sustained 50 cm cross-track error for 60 iterations (3 seconds)
        val input = NavigationInput(
            currentPosition = Vec2(0.50, 10.0),
            headingDeg = 0.0,
            speedKmh = 8.0,
            targetABLine = testABLine,
            swathWidthMeters = 6.0,
            dtSec = 0.05
        )

        var lastOutput: NavigationOutput = engine.calculateStanley(input)
        for (i in 1..60) {
            lastOutput = engine.calculateStanley(input)
        }

        // Integral correction should have accumulated
        assertTrue("Integral correction should be non-zero", abs(lastOutput.stanleyIntegralCorrectionDeg) > 0.05)

        // Reset integral
        engine.resetIntegral()
        val resetOutput = engine.calculateStanley(input)
        // Immediately after reset, integral correction for single dt is negligible
        assertTrue("Integral correction should be near zero right after reset", abs(resetOutput.stanleyIntegralCorrectionDeg) < 0.01)
    }

    @Test
    fun testStanleyMaxSteerClamping() {
        // Enormous cross-track error on base swath line (forcedSwathIndex = 0)
        val input = NavigationInput(
            currentPosition = Vec2(100.0, 10.0),
            headingDeg = 45.0,
            speedKmh = 10.0,
            targetABLine = testABLine,
            swathWidthMeters = 6.0,
            forcedSwathIndex = 0
        )

        val output = engine.calculateStanley(input)
        assertEquals(-engine.config.maxSteerAngleDeg, output.targetSteerAngleDeg, 0.001)
    }

    @Test
    fun testPurePursuitOnLineZeroSteer() {
        val input = NavigationInput(
            currentPosition = Vec2(0.0, 10.0),
            headingDeg = 0.0,
            speedKmh = 10.0,
            targetABLine = testABLine,
            swathWidthMeters = 6.0
        )

        val output = engine.calculatePurePursuit(input)
        assertEquals(0.0, output.crossTrackErrorMeters, 0.001)
        assertEquals(0.0, output.targetSteerAngleDeg, 0.01)
        assertEquals(SteeringAlgorithm.PURE_PURSUIT, output.algorithmUsed)
        assertNotNull(output.purePursuitLookaheadPoint)
        assertTrue(output.purePursuitLookaheadDistanceMeters >= engine.config.purePursuitMinLookaheadMeters)
    }

    @Test
    fun testPurePursuitRightOfLineSteersLeft() {
        // 1 meter to the right of line
        val input = NavigationInput(
            currentPosition = Vec2(1.0, 10.0),
            headingDeg = 0.0,
            speedKmh = 10.0,
            targetABLine = testABLine,
            swathWidthMeters = 6.0
        )

        val output = engine.calculatePurePursuit(input)
        assertEquals(1.0, output.crossTrackErrorMeters, 0.001)
        assertTrue("Pure pursuit should steer left when right of line", output.targetSteerAngleDeg < 0.0)
        assertTrue("Curvature should be negative", output.purePursuitCurvature < 0.0)
    }

    @Test
    fun testPurePursuitLeftOfLineSteersRight() {
        // 1 meter to the left of line
        val input = NavigationInput(
            currentPosition = Vec2(-1.0, 10.0),
            headingDeg = 0.0,
            speedKmh = 10.0,
            targetABLine = testABLine,
            swathWidthMeters = 6.0
        )

        val output = engine.calculatePurePursuit(input)
        assertEquals(-1.0, output.crossTrackErrorMeters, 0.001)
        assertTrue("Pure pursuit should steer right when left of line", output.targetSteerAngleDeg > 0.0)
        assertTrue("Curvature should be positive", output.purePursuitCurvature > 0.0)
    }

    @Test
    fun testPurePursuitSpeedAdaptiveLookahead() {
        val slowInput = NavigationInput(
            currentPosition = Vec2(0.5, 10.0),
            headingDeg = 0.0,
            speedKmh = 3.6, // 1 m/s -> lookahead = max(1.0 * 0.85, 2.0) = 2.0m
            targetABLine = testABLine
        )
        val slowOutput = engine.calculatePurePursuit(slowInput)
        assertEquals(engine.config.purePursuitMinLookaheadMeters, slowOutput.purePursuitLookaheadDistanceMeters, 0.05)

        val fastInput = NavigationInput(
            currentPosition = Vec2(0.5, 10.0),
            headingDeg = 0.0,
            speedKmh = 20.0, // 5.55 m/s -> lookahead = 5.55 * 0.85 = ~4.72m
            targetABLine = testABLine
        )
        val fastOutput = engine.calculatePurePursuit(fastInput)
        assertTrue("Fast lookahead should exceed slow lookahead",
            fastOutput.purePursuitLookaheadDistanceMeters > slowOutput.purePursuitLookaheadDistanceMeters)
    }

    @Test
    fun testAlgorithmDispatchViaCalculate() {
        val input = NavigationInput(
            currentPosition = Vec2(0.8, 10.0),
            headingDeg = 0.0,
            speedKmh = 10.0,
            targetABLine = testABLine
        )

        engine.setAlgorithm(SteeringAlgorithm.STANLEY)
        val stanleyOut = engine.calculate(input)
        assertEquals(SteeringAlgorithm.STANLEY, stanleyOut.algorithmUsed)

        engine.setAlgorithm(SteeringAlgorithm.PURE_PURSUIT)
        val ppOut = engine.calculate(input)
        assertEquals(SteeringAlgorithm.PURE_PURSUIT, ppOut.algorithmUsed)

        // Both algorithms steer left to correct right error
        assertTrue(stanleyOut.targetSteerAngleDeg < 0.0)
        assertTrue(ppOut.targetSteerAngleDeg < 0.0)
    }
}

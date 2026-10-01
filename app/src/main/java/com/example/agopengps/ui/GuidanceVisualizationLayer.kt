package com.example.agopengps.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.agopengps.map.TerrainAttitude
import com.example.agopengps.navigation.*
import kotlin.math.*

/**
 * Canvas-based visualization layer using Jetpack Compose to draw:
 * 1. The active AB guidance line (target swath, direction arrows, parallel passes, orthogonal cross-track error vector).
 * 2. The tractor's current real-time position overlay (steered front wheels at actual angle, dual rear wheels,
 *    antenna RTK puck, implement toolbar with section sprayers, and lookahead steering point).
 */
@Composable
fun ActiveGuidanceOverlayCanvas(
    modifier: Modifier = Modifier,
    vehicleState: VehicleState,
    vehicleConfig: VehicleConfig,
    implementConfig: ImplementConfig,
    currentABLine: ABLine?,
    navigationOutput: NavigationOutput?,
    isTerrainCompensationEnabled: Boolean = false,
    terrainAttitude: TerrainAttitude? = null,
    slidingHitchConfig: SlidingHitchConfig = SlidingHitchConfig(),
    slidingHitchState: SlidingHitchState = SlidingHitchState(),
    pixelsPerMeter: Float = 12.0f,
    panOffset: Offset = Offset.Zero,
    isHeadUpMode: Boolean = true
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag("guidance_visualization_canvas")
    ) {
        val centerScreen = Offset(size.width / 2f + panOffset.x, size.height / 2f + panOffset.y)
        val canvasRotation = if (isHeadUpMode) -vehicleState.headingDeg.toFloat() else 0f

        withTransform({
            rotate(degrees = canvasRotation, pivot = centerScreen)
        }) {
            fun worldToScreen(worldPoint: Vec2): Offset {
                val dx = (worldPoint.x - vehicleState.localPivotPosition.x).toFloat() * pixelsPerMeter
                val dy = (worldPoint.y - vehicleState.localPivotPosition.y).toFloat() * pixelsPerMeter
                return Offset(centerScreen.x + dx, centerScreen.y - dy)
            }

            // 1. Draw Active AB Line and Parallel Swath Grid
            if (currentABLine != null) {
                drawActiveABLineGuidance(
                    abLine = currentABLine,
                    activeSwathIndex = vehicleState.activeSwathIndex,
                    swathWidth = implementConfig.swathWidth,
                    tractorPivot = vehicleState.localPivotPosition,
                    tractorHeadingDeg = vehicleState.headingDeg,
                    worldToScreen = ::worldToScreen
                )
            }

            // 2. Draw Cross-Track Error Vector & Lookahead Guidance Point
            if (navigationOutput != null && currentABLine != null) {
                drawCrossTrackErrorIndicator(
                    tractorPivotScreen = centerScreen,
                    projectedPointScreen = worldToScreen(navigationOutput.projectedPoint),
                    crossTrackErrorMeters = navigationOutput.crossTrackErrorMeters,
                    crossTrackErrorInches = navigationOutput.crossTrackErrorInches,
                    lookaheadPointScreen = navigationOutput.purePursuitLookaheadPoint?.let { worldToScreen(it) }
                )
            }

            // 3. Draw Real-Time Tractor and Implement Overlay
            val tractorAngle = vehicleState.headingDeg.toFloat()
            withTransform({
                rotate(degrees = tractorAngle, pivot = centerScreen)
            }) {
                drawTractorRealTimeOverlay(
                    center = centerScreen,
                    pixelsPerMeter = pixelsPerMeter,
                    vehicleConfig = vehicleConfig,
                    implementConfig = implementConfig,
                    actualSteerDeg = vehicleState.actualSteerAngleDeg,
                    targetSteerDeg = vehicleState.targetSteerAngleDeg,
                    isAutoSteerEngaged = vehicleState.isAutoSteerEngaged,
                    fixQuality = vehicleState.fixQuality,
                    sectionStates = vehicleState.sectionStates,
                    slidingHitchConfig = slidingHitchConfig,
                    slidingHitchState = slidingHitchState,
                    isTerrainCompensationEnabled = isTerrainCompensationEnabled,
                    terrainAttitude = terrainAttitude
                )
            }
        }
    }
}

/**
 * Draws the active AB line, parallel swaths, and pass direction arrows.
 */
fun DrawScope.drawActiveABLineGuidance(
    abLine: ABLine,
    activeSwathIndex: Int,
    swathWidth: Double,
    tractorPivot: Vec2,
    tractorHeadingDeg: Double,
    worldToScreen: (Vec2) -> Offset
) {
    val lineDir = abLine.dirVector
    val lineNormal = abLine.normalVector
    val swathW = max(swathWidth, 0.5)

    // Heading dot product to point arrows in travel direction
    val headingRad = Math.toRadians(tractorHeadingDeg)
    val fwdX = sin(headingRad)
    val fwdY = cos(headingRad)
    val dotTravel = lineDir.x * fwdX + lineDir.y * fwdY
    val effectiveDir = if (dotTravel >= 0.0) lineDir else (lineDir * -1.0)

    val lineExtentMeters = 800.0 // Full 0.5 mile coverage line

    // Render adjacent and active swaths
    for (passOffset in -6..6) {
        val passIndex = activeSwathIndex + passOffset
        val swathOrigin = abLine.aLocal + (lineNormal * (passIndex * swathW))

        // Center line relative to tractor position along the track
        val toTractor = tractorPivot - swathOrigin
        val distAlong = toTractor.dot(effectiveDir)
        val centerOnTrack = swathOrigin + (effectiveDir * distAlong)

        val pStart = centerOnTrack - (effectiveDir * (lineExtentMeters * 0.4))
        val pEnd = centerOnTrack + (effectiveDir * (lineExtentMeters * 0.6))

        val sStart = worldToScreen(pStart)
        val sEnd = worldToScreen(pEnd)

        if (passOffset == 0) {
            // Master Active Swath Pass: Neon Green with outer glow halo
            drawLine(
                color = Color(0x3300E676),
                start = sStart,
                end = sEnd,
                strokeWidth = 10f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFF00E676),
                start = sStart,
                end = sEnd,
                strokeWidth = 4.2f,
                cap = StrokeCap.Round
            )

            // Dynamic Forward Guidance Chevrons
            val numChevrons = 6
            val stepMeters = 25.0
            for (step in 1..numChevrons) {
                val ptAhead = centerOnTrack + (effectiveDir * (step * stepMeters))
                val sp = worldToScreen(ptAhead)
                val perp = effectiveDir.perpClockwise() * 2.5
                val leftWing = worldToScreen(ptAhead - (effectiveDir * 2.2) - perp)
                val rightWing = worldToScreen(ptAhead - (effectiveDir * 2.2) + perp)

                drawLine(color = Color(0xFF00E676), start = leftWing, end = sp, strokeWidth = 2.8f)
                drawLine(color = Color(0xFF00E676), start = rightWing, end = sp, strokeWidth = 2.8f)
            }
        } else {
            // Parallel Passes: High-visibility cyan dashed lines
            val isEven = (passIndex % 2 == 0)
            val passColor = if (isEven) Color(0x8800E5FF) else Color(0x5500E5FF)
            drawLine(
                color = passColor,
                start = sStart,
                end = sEnd,
                strokeWidth = 1.8f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
            )
        }
    }
}

/**
 * Draws the real-time cross-track error vector and target pursuit reticle.
 */
fun DrawScope.drawCrossTrackErrorIndicator(
    tractorPivotScreen: Offset,
    projectedPointScreen: Offset,
    crossTrackErrorMeters: Double,
    crossTrackErrorInches: Double,
    lookaheadPointScreen: Offset?
) {
    val cteColor = if (abs(crossTrackErrorInches) < 1.0) Color(0xFF00E676)
                   else if (abs(crossTrackErrorInches) < 4.0) Color(0xFFFFD600)
                   else Color(0xFFFF5252)

    // Orthogonal cross-track error line connecting tractor rear axle directly to active line
    drawLine(
        color = cteColor,
        start = tractorPivotScreen,
        end = projectedPointScreen,
        strokeWidth = 2.4f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f), 0f)
    )

    // Dot at projected ground foot
    drawCircle(
        color = cteColor,
        radius = 4.5f,
        center = projectedPointScreen
    )

    // Pure pursuit target lookahead point reticle
    if (lookaheadPointScreen != null) {
        drawCircle(
            color = Color(0x66FFD600),
            radius = 12f,
            center = lookaheadPointScreen
        )
        drawCircle(
            color = Color(0xFFFFD600),
            radius = 4.5f,
            center = lookaheadPointScreen
        )
        // Guidance steering vector line from tractor to lookahead point
        drawLine(
            color = Color(0x88FFD600),
            start = tractorPivotScreen,
            end = lookaheadPointScreen,
            strokeWidth = 1.8f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
        )
    }
}

/**
 * Draws the detailed real-time tractor vehicle model and implement overlay.
 */
fun DrawScope.drawTractorRealTimeOverlay(
    center: Offset,
    pixelsPerMeter: Float,
    vehicleConfig: VehicleConfig,
    implementConfig: ImplementConfig,
    actualSteerDeg: Double,
    targetSteerDeg: Double,
    isAutoSteerEngaged: Boolean,
    fixQuality: FixQuality,
    sectionStates: List<Boolean>,
    slidingHitchConfig: SlidingHitchConfig = SlidingHitchConfig(),
    slidingHitchState: SlidingHitchState = SlidingHitchState(),
    isTerrainCompensationEnabled: Boolean = false,
    terrainAttitude: TerrainAttitude? = null
) {
    val wheelbasePx = (vehicleConfig.wheelbase * pixelsPerMeter).toFloat()
    val halfTrackPx = (vehicleConfig.trackWidth * 0.5 * pixelsPerMeter).toFloat()
    val tireWidthPx = (0.52 * pixelsPerMeter).toFloat().coerceAtLeast(5f)
    val tireLengthPx = (1.25 * pixelsPerMeter).toFloat().coerceAtLeast(10f)
    val dualSpacingPx = (0.65 * pixelsPerMeter).toFloat().coerceAtLeast(7f)

    val tireColor = Color(0xFF212121)
    val chassisColor = Color(0xFF1E88E5) // Classic New Holland Blue
    val hoodColor = Color(0xFF1565C0)
    val cabColor = Color(0xEEFFFFFF)

    // 1. Rear Dual Wheels (Left & Right)
    for (side in listOf(-1f, 1f)) {
        val innerX = center.x + (side * halfTrackPx)
        val outerX = center.x + (side * (halfTrackPx + dualSpacingPx))
        val y = center.y

        drawRoundRect(
            color = tireColor,
            topLeft = Offset(innerX - tireWidthPx / 2f, y - tireLengthPx / 2f),
            size = Size(tireWidthPx, tireLengthPx),
            cornerRadius = CornerRadius(2.5f, 2.5f)
        )
        drawRoundRect(
            color = tireColor,
            topLeft = Offset(outerX - tireWidthPx / 2f, y - tireLengthPx / 2f),
            size = Size(tireWidthPx, tireLengthPx),
            cornerRadius = CornerRadius(2.5f, 2.5f)
        )
        // Axle spacer hub
        drawLine(
            color = Color(0xFF37474F),
            start = Offset(innerX, y),
            end = Offset(outerX, y),
            strokeWidth = 3f
        )
    }

    // Heavy Cast Rear Axle
    drawLine(
        color = Color(0xFF1B2428),
        start = Offset(center.x - halfTrackPx, center.y),
        end = Offset(center.x + halfTrackPx, center.y),
        strokeWidth = 4.5f
    )

    // 2. Steered Front Wheels
    val frontAxleY = center.y - wheelbasePx
    val frontTireWidth = tireWidthPx * 0.82f
    val frontTireLength = tireLengthPx * 0.85f

    for (side in listOf(-1f, 1f)) {
        val fx = center.x + (side * halfTrackPx * 0.9f)
        withTransform({
            rotate(degrees = actualSteerDeg.toFloat(), pivot = Offset(fx, frontAxleY))
        }) {
            drawRoundRect(
                color = tireColor,
                topLeft = Offset(fx - frontTireWidth / 2f, frontAxleY - frontTireLength / 2f),
                size = Size(frontTireWidth, frontTireLength),
                cornerRadius = CornerRadius(2f, 2f)
            )
            // Wheel hub
            drawCircle(color = Color(0xFFECEFF1), radius = 2f, center = Offset(fx, frontAxleY))
        }
    }

    // Front Axle Bar
    drawLine(
        color = Color(0xFF37474F),
        start = Offset(center.x - halfTrackPx * 0.9f, frontAxleY),
        end = Offset(center.x + halfTrackPx * 0.9f, frontAxleY),
        strokeWidth = 3.5f
    )

    // 3. Tractor Chassis, Hood & Cab Body
    val halfHoodW = halfTrackPx * 0.45f
    val hoodPath = Path().apply {
        moveTo(center.x - halfHoodW, center.y - wheelbasePx * 0.35f)
        lineTo(center.x + halfHoodW, center.y - wheelbasePx * 0.35f)
        lineTo(center.x + halfHoodW * 0.8f, frontAxleY - 4f)
        lineTo(center.x - halfHoodW * 0.8f, frontAxleY - 4f)
        close()
    }
    drawPath(path = hoodPath, color = hoodColor, style = Fill)

    // Cab Glass Enclosure
    val cabHalfW = halfTrackPx * 0.65f
    val cabTop = center.y - wheelbasePx * 0.55f
    val cabBottom = center.y - wheelbasePx * 0.15f
    drawRoundRect(
        color = cabColor,
        topLeft = Offset(center.x - cabHalfW, cabTop),
        size = Size(cabHalfW * 2f, cabBottom - cabTop),
        cornerRadius = CornerRadius(4f, 4f)
    )
    drawRoundRect(
        color = chassisColor,
        topLeft = Offset(center.x - cabHalfW, cabTop),
        size = Size(cabHalfW * 2f, cabBottom - cabTop),
        cornerRadius = CornerRadius(4f, 4f),
        style = Stroke(width = 2f)
    )

    // 4. Primary GNSS Antenna Puck (Roof Mount)
    val antennaOffsetPx = (vehicleConfig.antennaPivotOffset * pixelsPerMeter).toFloat()
    val antennaPos = Offset(center.x, center.y - antennaOffsetPx)

    // Fix Quality Color
    val fixColor = when (fixQuality) {
        FixQuality.RTK_FIX -> Color(0xFF00E676)
        FixQuality.RTK_FLOAT -> Color(0xFFFFD600)
        FixQuality.DGPS, FixQuality.GPS -> Color(0xFFFF9100)
        else -> Color(0xFF00E5FF)
    }

    drawCircle(color = fixColor.copy(alpha = 0.3f), radius = 8f, center = antennaPos)
    drawCircle(color = fixColor, radius = 4f, center = antennaPos)
    drawCircle(color = Color.Black, radius = 1.5f, center = antennaPos)

    // 5. Rear Axle Ground Pivot Crosshair
    drawCircle(color = Color(0xFFFF5252), radius = 3f, center = center)
    drawLine(color = Color(0xFFFF5252), start = Offset(center.x - 6f, center.y), end = Offset(center.x + 6f, center.y), strokeWidth = 1.2f)
    drawLine(color = Color(0xFFFF5252), start = Offset(center.x, center.y - 6f), end = Offset(center.x, center.y + 6f), strokeWidth = 1.2f)

    // 6. Hitch and Trailing Implement Boom
    val hitchLengthPx = (-vehicleConfig.hitchLength * pixelsPerMeter).toFloat()
    val hitchPin = Offset(center.x, center.y + hitchLengthPx)
    drawLine(color = Color(0xFF455A64), start = center, end = hitchPin, strokeWidth = 3f)

    // Draw Implement Toolbar (with sliding hitch shift if enabled)
    val isHitchEnabled = slidingHitchConfig.isEnabled
    val lateralShiftPx = if (isHitchEnabled) ((slidingHitchState.currentHitchShiftCm / 100.0) * pixelsPerMeter).toFloat() else 0f
    val implementOffsetPx = (implementConfig.offsetBehindTractor * pixelsPerMeter).toFloat()
    val boomY = hitchPin.y + implementOffsetPx
    val toolbarCenterX = center.x + lateralShiftPx

    // Toolbar Beam
    val halfToolWidthPx = (implementConfig.toolWidth * 0.5 * pixelsPerMeter).toFloat()
    drawLine(
        color = Color(0xFF37474F),
        start = Offset(toolbarCenterX - halfToolWidthPx, boomY),
        end = Offset(toolbarCenterX + halfToolWidthPx, boomY),
        strokeWidth = 4.5f,
        cap = StrokeCap.Round
    )

    // Implement Boom Sections & Spray Nozzles
    val spans = implementConfig.getSectionSpans()
    for (i in spans.indices) {
        val (leftM, rightM) = spans[i]
        val lx = toolbarCenterX + (leftM * pixelsPerMeter).toFloat()
        val rx = toolbarCenterX + (rightM * pixelsPerMeter).toFloat()
        val active = sectionStates.getOrElse(i) { true }
        val sectionColor = if (active) Color(0xFF00E676) else Color(0xFFFF5252)

        drawLine(color = sectionColor, start = Offset(lx, boomY), end = Offset(rx, boomY), strokeWidth = 3.8f, cap = StrokeCap.Round)
        if (active) {
            drawCircle(color = Color(0xFF80D8FF), radius = 2.2f, center = Offset((lx + rx) / 2f, boomY + 3f))
        }
    }

    // 7. AutoSteer Engagement Status Beacon
    val steerBeaconColor = if (isAutoSteerEngaged) Color(0xFF00E676) else Color(0xFFFFD600)
    drawCircle(color = steerBeaconColor, radius = 3.5f, center = Offset(center.x, cabTop - 4f))

    // 8. Hillside Slope & Terrain Attitude Indicator
    if (isTerrainCompensationEnabled && terrainAttitude != null && abs(terrainAttitude.rollDeg) > 0.3) {
        // Draw side-slope tilt vector arrow across rear axle
        val rollRight = terrainAttitude.rollDeg > 0
        val arrowStart = Offset(center.x, center.y - 12f)
        val arrowEnd = Offset(if (rollRight) center.x + 22f else center.x - 22f, center.y - 12f)
        drawLine(
            color = Color(0xFFFFD600),
            start = arrowStart,
            end = arrowEnd,
            strokeWidth = 2.0f
        )
    }
}

package com.example.agopengps.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.example.agopengps.io.ThreePointState
import com.example.agopengps.navigation.DisplayThemeMode
import com.example.agopengps.navigation.ImplementConfig
import com.example.agopengps.navigation.VehicleConfig
import kotlin.math.abs

/**
 * Pre-allocated scratch buffers, reusable [Path]s, [PathEffect]s, [Stroke]s, and native [Paint]s
 * so the 20 Hz / 60-120 FPS guidance canvas runs with zero per-frame heap allocations.
 */
internal class CanvasScratchBuffers {
    val coverageBatchPath = Path()
    val indexContourPath = Path()
    val minorContourPath = Path()
    val boundaryPath = Path()
    val headlandPath = Path()
    val uTurnPath = Path()
    val scratchPath = Path()
    val bodyPath = Path()
    val cabPath = Path()
    val cabRoofPath = Path()
    val hoodHighlightPath = Path()
    val hoodGrillePath = Path()
    val windshieldGlintPath = Path()
    val leftFenderPath = Path()
    val rightFenderPath = Path()
    val plumePath = Path()
    val compassNorthPath = Path()
    val compassSouthPath = Path()

    // Primitive scratch arrays for zero-allocation 3D quad near/far plane clipping (max 8 vertices)
    val quadInX = DoubleArray(4)
    val quadInY = DoubleArray(4)
    val clipX = DoubleArray(8)
    val clipY = DoubleArray(8)
    val projX = FloatArray(8)
    val projY = FloatArray(8)

    // Pre-allocated PathEffects & Strokes
    val dashParallel = PathEffect.dashPathEffect(floatArrayOf(14f, 9f), 0f)
    val dashHeadland = PathEffect.dashPathEffect(floatArrayOf(11f, 7f), 0f)
    val dashTopoBox = PathEffect.dashPathEffect(floatArrayOf(14f, 8f), 0f)
    val dashTurnArc = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f)
    val dashCorridor = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)

    val strokeCoverageEdge = Stroke(width = 1.0f, join = StrokeJoin.Miter)
    val strokeThin = Stroke(width = 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val strokeTireOutline = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val strokeMedium = Stroke(width = 2.0f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val strokeBold = Stroke(width = 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val strokeBoundary = Stroke(width = 3.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val strokeHeadland = Stroke(width = 2.2f, pathEffect = dashHeadland, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val strokeTopoBox = Stroke(width = 2.2f, pathEffect = dashTopoBox)
    val strokeIndexContour = Stroke(width = 2.3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val strokeMinorContour = Stroke(width = 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val strokeTurnHalo = Stroke(width = 8.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val strokeTurnDash = Stroke(width = 4.2f, pathEffect = dashTurnArc, cap = StrokeCap.Round, join = StrokeJoin.Round)

    val boldTypeface: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    val textPaint = Paint().apply {
        isAntiAlias = true
        textSize = 24f
        typeface = boldTypeface
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
    }
}

// Signature New Holland T7 / T8 Row-Crop Tractor Palette
private val NH_BLUE_PRIMARY = Color(0xFF005CB9)
private val NH_BLUE_HIGHLIGHT = Color(0xFF288CF2)
private val NH_BLUE_SHADOW = Color(0xFF003B7A)
private val NH_BLUE_NIGHT = Color(0xFF142D4C)
private val NH_BLUE_NIGHT_HIGHLIGHT = Color(0xFF264E7C)
private val NH_RIM_WHITE = Color(0xFFF1F5F9)
private val NH_RIM_NIGHT = Color(0xFF9A6700)
private val NH_YELLOW_STRIPE = Color(0xFFFACC15)
private val NH_STRIPE_WHITE = Color(0xFFF8FAFC)
private val NH_CHASSIS_DARK = Color(0xFF111827)
private val NH_GRILLE_BLACK = Color(0xFF0B1220)
private val NH_TIRE_RUBBER = Color(0xFF0D1117)
private val NH_TIRE_LUG = Color(0xFF263140)
private val NH_TIRE_LUG_NIGHT = Color(0xFF3E260C)
private val NH_GLASS_DARK = Color(0xFF0A192F)
private val NH_GLASS_GLINT = Color(0x887DD3FC)

/**
 * Authentic New Holland T7/T8-inspired 3D overhead-chase tractor & implement renderer:
 * - True row-crop tractor T-silhouette: front ballast weights, narrow sculpted New Holland Blue
 *   sloped hood with black cat-eye grille & yellow/white racing stripes, exposed front axle,
 *   steered front MFWD tires with rotating fenders & white rims, oversized lugged rear ag tires
 *   with blue/black molded rear fenders, right-side exhaust stack, panoramic 4-pillar cab with
 *   overhanging blue roof cap, side mirrors, amber roof beacon & RTK GNSS dome.
 * - Explicit 3-point hitch linkage & working vs raised/transport toolbar geometry.
 * - Zero per-frame heap allocations.
 */
internal fun DrawScope.drawTractorIn3D(
    center: Offset,
    pixelsPerMeter: Float,
    vehicleConfig: VehicleConfig,
    implementConfig: ImplementConfig,
    actualSteerDeg: Double,
    isAutoSteerEngaged: Boolean,
    isMasterActive: Boolean,
    threePointState: ThreePointState,
    sectionStates: List<Boolean>,
    themeMode: DisplayThemeMode,
    scratch: CanvasScratchBuffers
) {
    val isDaylight = themeMode == DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST
    val isOledNight = themeMode == DisplayThemeMode.CAB_OLED_NIGHT
    val isHitchLowered = threePointState == ThreePointState.LOWERED || threePointState == ThreePointState.FLOAT
    val isWorking = isHitchLowered && (isMasterActive || isAutoSteerEngaged)

    val scale = (pixelsPerMeter / 13.5f).coerceIn(0.68f, 2.05f)
    val tractorW = 40f * scale
    val tractorL = 70f * scale
    val steerClamped = actualSteerDeg.toFloat().coerceIn(-45f, 45f)

    // Theme-aware New Holland colors
    val nhBlue = if (isOledNight) NH_BLUE_NIGHT else NH_BLUE_PRIMARY
    val nhBlueLight = if (isOledNight) NH_BLUE_NIGHT_HIGHLIGHT else NH_BLUE_HIGHLIGHT
    val nhBlueDark = if (isOledNight) Color(0xFF0A1626) else NH_BLUE_SHADOW
    val nhRim = if (isOledNight) NH_RIM_NIGHT else NH_RIM_WHITE
    val nhStripeYellow = if (isOledNight) Color(0xFFD97706) else NH_YELLOW_STRIPE
    val nhStripeWhite = if (isOledNight) Color(0xFF94A3B8) else NH_STRIPE_WHITE
    val tireLug = if (isOledNight) NH_TIRE_LUG_NIGHT else NH_TIRE_LUG
    val bodyBorder = when {
        isOledNight -> if (isAutoSteerEngaged) Color(0xFFFFB300) else Color(0xFF475569)
        isDaylight -> Color(0xFF0B1F3A)
        else -> Color(0xFF061529)
    }
    val steerRingAccent = when {
        isOledNight -> if (isAutoSteerEngaged) Color(0xFFFFB300) else Color(0xFF9A6700)
        isAutoSteerEngaged -> Color(0xFF00F59B)
        else -> Color(0xFFF59E0B)
    }

    // 1. Implement Toolbar & 3-Point Hitch Assembly (drawn behind tractor)
    val baseToolbarY = center.y + (implementConfig.offsetBehindTractor.toFloat() * pixelsPerMeter * 0.52f).coerceIn(24f, 60f)
    val toolbarWidth = (implementConfig.toolWidth.toFloat() * pixelsPerMeter * 0.42f).coerceIn(76f, 340f)
    val hitchLiftOffsetY = if (isHitchLowered) 0f else -8.5f * scale
    val toolbarY = baseToolbarY + hitchLiftOffsetY

    // Ground shadow beneath implement (detached & softer when raised)
    drawLine(
        color = if (isDaylight) Color(0x380F172A) else Color(0x55000000),
        start = Offset(center.x - toolbarWidth * 0.5f, baseToolbarY + 4f),
        end = Offset(center.x + toolbarWidth * 0.5f, baseToolbarY + 4f),
        strokeWidth = if (isHitchLowered) 7f else 5f,
        cap = StrokeCap.Round
    )

    // 3-Point Hitch Draft Links (Left, Right, Center Top Link)
    val hitchAnchorY = center.y + tractorL * 0.28f
    val linkColor = if (isHitchLowered) Color(0xFF64748B) else Color(0xFFF59E0B)
    drawLine(linkColor, Offset(center.x - tractorW * 0.24f, hitchAnchorY), Offset(center.x - tractorW * 0.40f, toolbarY), strokeWidth = 2.6f * scale, cap = StrokeCap.Round)
    drawLine(linkColor, Offset(center.x + tractorW * 0.24f, hitchAnchorY), Offset(center.x + tractorW * 0.40f, toolbarY), strokeWidth = 2.6f * scale, cap = StrokeCap.Round)
    drawLine(Color(0xFF94A3B8), Offset(center.x, hitchAnchorY - 4f), Offset(center.x, toolbarY), strokeWidth = 2.2f * scale, cap = StrokeCap.Round)

    // Main Implement Toolbar Frame
    val toolbarColor = when {
        !isHitchLowered -> Color(0xFFF59E0B)
        isWorking -> if (isOledNight) Color(0xFFFF9100) else Color(0xFF00E676)
        else -> Color(0xFF38BDF8)
    }
    drawLine(
        color = Color(0xCC0F172A),
        start = Offset(center.x - toolbarWidth * 0.5f, toolbarY),
        end = Offset(center.x + toolbarWidth * 0.5f, toolbarY),
        strokeWidth = 8.5f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = toolbarColor,
        start = Offset(center.x - toolbarWidth * 0.5f, toolbarY),
        end = Offset(center.x + toolbarWidth * 0.5f, toolbarY),
        strokeWidth = 4.5f,
        cap = StrokeCap.Round
    )

    // Toolbar Wing End-Caps / Raised State Indicators
    val capH = if (isHitchLowered) 5f * scale else 8f * scale
    drawLine(toolbarColor, Offset(center.x - toolbarWidth * 0.5f, toolbarY - capH * 0.5f), Offset(center.x - toolbarWidth * 0.5f, toolbarY + capH * 0.5f), strokeWidth = 3f, cap = StrokeCap.Round)
    drawLine(toolbarColor, Offset(center.x + toolbarWidth * 0.5f, toolbarY - capH * 0.5f), Offset(center.x + toolbarWidth * 0.5f, toolbarY + capH * 0.5f), strokeWidth = 3f, cap = StrokeCap.Round)

    // Section Nozzles & Active Spray/Tillage Plumes
    val numSec = implementConfig.numSections.coerceIn(1, 16)
    val secW = toolbarWidth / numSec
    for (i in 0 until numSec) {
        val secCenterX = (center.x - toolbarWidth * 0.5f) + (i + 0.5f) * secW
        val secOn = isWorking && (sectionStates.isEmpty() || sectionStates.getOrElse(i) { true })
        val dotColor = when {
            !isHitchLowered -> Color(0xFFFBBF24)
            secOn -> if (isOledNight) Color(0xFFFFB300) else Color(0xFF00F59B)
            else -> Color(0xFF64748B)
        }
        drawCircle(color = Color(0xFF0F172A), radius = 4.4f, center = Offset(secCenterX, toolbarY))
        drawCircle(color = dotColor, radius = 3.0f, center = Offset(secCenterX, toolbarY))
        if (secOn) {
            scratch.plumePath.reset()
            scratch.plumePath.moveTo(secCenterX, toolbarY + 2f)
            scratch.plumePath.lineTo(secCenterX - secW * 0.44f, toolbarY + 13f * scale)
            scratch.plumePath.lineTo(secCenterX + secW * 0.44f, toolbarY + 13f * scale)
            scratch.plumePath.close()
            drawPath(
                path = scratch.plumePath,
                color = if (isOledNight) Color(0x44FF9100) else Color(0x5500E676)
            )
        }
    }

    // Center Hitch Pivot Pin + Raised Up-Arrow Marker
    drawCircle(color = Color(0xFF0F172A), radius = 6.2f, center = Offset(center.x, toolbarY))
    drawCircle(color = if (isHitchLowered) Color(0xFF00F59B) else Color(0xFFF59E0B), radius = 4.2f, center = Offset(center.x, toolbarY))
    if (!isHitchLowered) {
        val chevY = toolbarY - 12f * scale
        drawLine(Color(0xFFFBBF24), Offset(center.x - 6f * scale, chevY + 5f), Offset(center.x, chevY), strokeWidth = 2.4f, cap = StrokeCap.Round)
        drawLine(Color(0xFFFBBF24), Offset(center.x + 6f * scale, chevY + 5f), Offset(center.x, chevY), strokeWidth = 2.4f, cap = StrokeCap.Round)
    }

    // =========================================================================
    // 2. NEW HOLLAND TRACTOR GROUND CONTACT SHADOW (T-shaped realistic footprint)
    // =========================================================================
    val shadowColor = if (isDaylight) Color(0x330F172A) else Color(0x66000000)
    // Rear axle + cab shadow
    drawRoundRect(
        color = shadowColor,
        topLeft = Offset(center.x - tractorW * 0.78f, center.y - tractorL * 0.14f + 4f * scale),
        size = Size(tractorW * 1.56f, tractorL * 0.48f),
        cornerRadius = CornerRadius(10f * scale, 10f * scale)
    )
    // Forward hood + front axle shadow
    drawRoundRect(
        color = shadowColor,
        topLeft = Offset(center.x - tractorW * 0.62f, center.y - tractorL * 0.68f + 4f * scale),
        size = Size(tractorW * 1.24f, tractorL * 0.56f),
        cornerRadius = CornerRadius(8f * scale, 8f * scale)
    )

    // =========================================================================
    // 3. EXPOSED FRONT MFWD AXLE & DYNAMICALLY STEERED FRONT TIRES (White Rims)
    // =========================================================================
    val frontAxleY = center.y - tractorL * 0.44f
    val frontTrackHalf = tractorW * 0.49f
    val frontWheelW = 8.4f * scale
    val frontWheelL = 19.5f * scale

    // Heavy cast-iron front axle housing & steering tie-rod
    drawLine(
        color = Color(0xFF1F2937),
        start = Offset(center.x - frontTrackHalf, frontAxleY),
        end = Offset(center.x + frontTrackHalf, frontAxleY),
        strokeWidth = 4.6f * scale,
        cap = StrokeCap.Round
    )
    drawLine(
        color = Color(0xFF64748B),
        start = Offset(center.x - frontTrackHalf * 0.88f, frontAxleY + 2.5f * scale),
        end = Offset(center.x + frontTrackHalf * 0.88f, frontAxleY + 2.5f * scale),
        strokeWidth = 1.8f * scale,
        cap = StrokeCap.Round
    )

    // Left & Right Steered Front MFWD Tires (pivot with actualSteerDeg, with chevron lugs & white rim edge)
    drawNewHollandFrontTire(
        pivot = Offset(center.x - frontTrackHalf, frontAxleY),
        steerDeg = steerClamped,
        isLeftWheel = true,
        wheelW = frontWheelW,
        wheelL = frontWheelL,
        lugColor = tireLug,
        rimColor = nhRim,
        steerAccent = if (abs(steerClamped) > 1.2f) steerRingAccent else nhRim,
        scratch = scratch
    )
    drawNewHollandFrontTire(
        pivot = Offset(center.x + frontTrackHalf, frontAxleY),
        steerDeg = steerClamped,
        isLeftWheel = false,
        wheelW = frontWheelW,
        wheelL = frontWheelL,
        lugColor = tireLug,
        rimColor = nhRim,
        steerAccent = if (abs(steerClamped) > 1.2f) steerRingAccent else nhRim,
        scratch = scratch
    )

    // =========================================================================
    // 4. OVERSIZED LUGGED REAR AG TIRES (Classic big-rear tractor silhouette)
    // =========================================================================
    val rearAxleY = center.y + tractorL * 0.08f
    val rearWheelW = 13.5f * scale
    val rearWheelL = 29.5f * scale
    val rearInnerEdge = tractorW * 0.42f

    // Heavy rear axle beam + differential housing
    drawLine(
        color = NH_CHASSIS_DARK,
        start = Offset(center.x - rearInnerEdge - rearWheelW * 0.5f, rearAxleY),
        end = Offset(center.x + rearInnerEdge + rearWheelW * 0.5f, rearAxleY),
        strokeWidth = 6.5f * scale,
        cap = StrokeCap.Round
    )

    drawNewHollandRearTire(
        left = center.x - rearInnerEdge - rearWheelW,
        top = rearAxleY - rearWheelL * 0.5f,
        wheelW = rearWheelW,
        wheelL = rearWheelL,
        isLeftWheel = true,
        lugColor = tireLug,
        rimColor = nhRim,
        scratch = scratch
    )
    drawNewHollandRearTire(
        left = center.x + rearInnerEdge,
        top = rearAxleY - rearWheelL * 0.5f,
        wheelW = rearWheelW,
        wheelL = rearWheelL,
        isLeftWheel = false,
        lugColor = tireLug,
        rimColor = nhRim,
        scratch = scratch
    )

    // =========================================================================
    // 5. FRONT BALLAST SUITCASE WEIGHT PACK + NARROW SCULPTED NEW HOLLAND BLUE HOOD
    // =========================================================================
    // Front suitcase ballast weight block protruding ahead of nose
    val weightW = tractorW * 0.34f
    val weightL = 7.2f * scale
    val noseTipY = center.y - tractorL * 0.65f
    drawRoundRect(
        color = nhBlueDark,
        topLeft = Offset(center.x - weightW * 0.5f, noseTipY - weightL),
        size = Size(weightW, weightL + 2f * scale),
        cornerRadius = CornerRadius(2.4f * scale, 2.4f * scale)
    )
    drawRoundRect(
        color = bodyBorder,
        topLeft = Offset(center.x - weightW * 0.5f, noseTipY - weightL),
        size = Size(weightW, weightL + 2f * scale),
        cornerRadius = CornerRadius(2.4f * scale, 2.4f * scale),
        style = scratch.strokeThin
    )
    // Individual suitcase weight slab dividers
    for (s in -2..2) {
        val sx = center.x + s * (weightW * 0.17f)
        drawLine(
            color = Color(0x88000000),
            start = Offset(sx, noseTipY - weightL + 1f),
            end = Offset(sx, noseTipY - 1f),
            strokeWidth = 1.1f * scale
        )
    }

    // Main central tractor frame / engine chassis under hood & cab
    val hoodBackY = center.y - tractorL * 0.14f
    val hoodBackHalfW = tractorW * 0.25f
    val hoodFrontHalfW = tractorW * 0.17f

    // Narrow Tapered New Holland Blue Engine Hood
    scratch.bodyPath.reset()
    scratch.bodyPath.moveTo(center.x - hoodBackHalfW, hoodBackY)
    scratch.bodyPath.lineTo(center.x - hoodBackHalfW * 0.94f, center.y - tractorL * 0.42f)
    scratch.bodyPath.lineTo(center.x - hoodFrontHalfW, noseTipY + 3f * scale)
    scratch.bodyPath.lineTo(center.x - hoodFrontHalfW * 0.65f, noseTipY)
    scratch.bodyPath.lineTo(center.x + hoodFrontHalfW * 0.65f, noseTipY)
    scratch.bodyPath.lineTo(center.x + hoodFrontHalfW, noseTipY + 3f * scale)
    scratch.bodyPath.lineTo(center.x + hoodBackHalfW * 0.94f, center.y - tractorL * 0.42f)
    scratch.bodyPath.lineTo(center.x + hoodBackHalfW, hoodBackY)
    scratch.bodyPath.close()
    drawPath(path = scratch.bodyPath, color = nhBlue)

    // Upper sculpted hood ridge highlight (signature New Holland curved bonnet crown)
    scratch.hoodHighlightPath.reset()
    scratch.hoodHighlightPath.moveTo(center.x - hoodBackHalfW * 0.56f, hoodBackY - 2f * scale)
    scratch.hoodHighlightPath.lineTo(center.x - hoodFrontHalfW * 0.48f, noseTipY + 4f * scale)
    scratch.hoodHighlightPath.lineTo(center.x + hoodFrontHalfW * 0.48f, noseTipY + 4f * scale)
    scratch.hoodHighlightPath.lineTo(center.x + hoodBackHalfW * 0.56f, hoodBackY - 2f * scale)
    scratch.hoodHighlightPath.close()
    drawPath(path = scratch.hoodHighlightPath, color = nhBlueLight)

    // Black sculpted front radiator grille & side cooling louver vents
    scratch.hoodGrillePath.reset()
    scratch.hoodGrillePath.moveTo(center.x - hoodFrontHalfW * 0.58f, noseTipY + 1.2f * scale)
    scratch.hoodGrillePath.lineTo(center.x + hoodFrontHalfW * 0.58f, noseTipY + 1.2f * scale)
    scratch.hoodGrillePath.lineTo(center.x + hoodFrontHalfW * 0.44f, noseTipY + 7.5f * scale)
    scratch.hoodGrillePath.lineTo(center.x - hoodFrontHalfW * 0.44f, noseTipY + 7.5f * scale)
    scratch.hoodGrillePath.close()
    drawPath(path = scratch.hoodGrillePath, color = NH_GRILLE_BLACK)

    // Signature New Holland Yellow + White hood side accent stripes
    drawLine(
        color = nhStripeYellow,
        start = Offset(center.x - hoodBackHalfW * 0.85f, hoodBackY - 3f * scale),
        end = Offset(center.x - hoodFrontHalfW * 0.86f, noseTipY + 10f * scale),
        strokeWidth = 2.1f * scale,
        cap = StrokeCap.Round
    )
    drawLine(
        color = nhStripeYellow,
        start = Offset(center.x + hoodBackHalfW * 0.85f, hoodBackY - 3f * scale),
        end = Offset(center.x + hoodFrontHalfW * 0.86f, noseTipY + 10f * scale),
        strokeWidth = 2.1f * scale,
        cap = StrokeCap.Round
    )
    drawLine(
        color = nhStripeWhite.copy(alpha = 0.85f),
        start = Offset(center.x - hoodBackHalfW * 0.72f, hoodBackY - 3f * scale),
        end = Offset(center.x - hoodFrontHalfW * 0.72f, noseTipY + 11f * scale),
        strokeWidth = 1.0f * scale,
        cap = StrokeCap.Round
    )
    drawLine(
        color = nhStripeWhite.copy(alpha = 0.85f),
        start = Offset(center.x + hoodBackHalfW * 0.72f, hoodBackY - 3f * scale),
        end = Offset(center.x + hoodFrontHalfW * 0.72f, noseTipY + 11f * scale),
        strokeWidth = 1.0f * scale,
        cap = StrokeCap.Round
    )

    // Cat-eye angled front LED headlights
    val headlightColor = if (isOledNight) Color(0xFFFFB300) else Color(0xFFFEF9C3)
    drawLine(
        color = headlightColor,
        start = Offset(center.x - hoodFrontHalfW * 0.90f, noseTipY + 5.0f * scale),
        end = Offset(center.x - hoodFrontHalfW * 0.52f, noseTipY + 1.8f * scale),
        strokeWidth = 2.4f * scale,
        cap = StrokeCap.Round
    )
    drawLine(
        color = headlightColor,
        start = Offset(center.x + hoodFrontHalfW * 0.90f, noseTipY + 5.0f * scale),
        end = Offset(center.x + hoodFrontHalfW * 0.52f, noseTipY + 1.8f * scale),
        strokeWidth = 2.4f * scale,
        cap = StrokeCap.Round
    )

    // Crisp hood outline
    drawPath(path = scratch.bodyPath, color = bodyBorder, style = scratch.strokeMedium)

    // Right-side vertical exhaust stack (by right A-pillar) & left-side cab steps/fuel tank
    val exhaustCenter = Offset(center.x + hoodBackHalfW + 3.8f * scale, hoodBackY - 2f * scale)
    drawCircle(color = Color(0xFF0F172A), radius = 3.2f * scale, center = exhaustCenter)
    drawCircle(color = Color(0xFF94A3B8), radius = 1.6f * scale, center = exhaustCenter)
    // Left-side fuel tank & cab ladder step box
    drawRoundRect(
        color = NH_CHASSIS_DARK,
        topLeft = Offset(center.x - hoodBackHalfW - 6.5f * scale, hoodBackY - 4f * scale),
        size = Size(6.5f * scale, 10.5f * scale),
        cornerRadius = CornerRadius(2f * scale, 2f * scale)
    )
    drawLine(
        color = nhBlueLight,
        start = Offset(center.x - hoodBackHalfW - 5.5f * scale, hoodBackY + 1f * scale),
        end = Offset(center.x - hoodBackHalfW - 1.0f * scale, hoodBackY + 1f * scale),
        strokeWidth = 1.4f * scale
    )

    // =========================================================================
    // 6. MOLDED NEW HOLLAND BLUE & BLACK REAR WHEEL FENDERS (With Tail Lights)
    // =========================================================================
    val fenderTopY = rearAxleY - rearWheelL * 0.44f
    val fenderBotY = rearAxleY + rearWheelL * 0.48f
    val fenderInnerX = tractorW * 0.34f
    val fenderOuterX = rearInnerEdge + rearWheelW * 0.54f

    // Left Rear Fender (New Holland blue inner body + black mud-extension lip)
    scratch.leftFenderPath.reset()
    scratch.leftFenderPath.moveTo(center.x - fenderInnerX, fenderTopY)
    scratch.leftFenderPath.lineTo(center.x - fenderOuterX, fenderTopY + 3f * scale)
    scratch.leftFenderPath.lineTo(center.x - fenderOuterX, fenderBotY - 2f * scale)
    scratch.leftFenderPath.lineTo(center.x - fenderInnerX, fenderBotY)
    scratch.leftFenderPath.close()
    drawPath(path = scratch.leftFenderPath, color = nhBlue)
    drawPath(path = scratch.leftFenderPath, color = bodyBorder, style = scratch.strokeThin)
    // Black outer mudguard trim + red tail light
    drawLine(NH_CHASSIS_DARK, Offset(center.x - fenderOuterX, fenderTopY + 3f * scale), Offset(center.x - fenderOuterX, fenderBotY - 2f * scale), strokeWidth = 2.8f * scale, cap = StrokeCap.Round)
    drawLine(Color(0xFFEF4444), Offset(center.x - fenderOuterX + 2f * scale, fenderBotY - 1.5f * scale), Offset(center.x - fenderInnerX - 2f * scale, fenderBotY - 0.5f * scale), strokeWidth = 2.0f * scale, cap = StrokeCap.Round)

    // Right Rear Fender
    scratch.rightFenderPath.reset()
    scratch.rightFenderPath.moveTo(center.x + fenderInnerX, fenderTopY)
    scratch.rightFenderPath.lineTo(center.x + fenderOuterX, fenderTopY + 3f * scale)
    scratch.rightFenderPath.lineTo(center.x + fenderOuterX, fenderBotY - 2f * scale)
    scratch.rightFenderPath.lineTo(center.x + fenderInnerX, fenderBotY)
    scratch.rightFenderPath.close()
    drawPath(path = scratch.rightFenderPath, color = nhBlue)
    drawPath(path = scratch.rightFenderPath, color = bodyBorder, style = scratch.strokeThin)
    drawLine(NH_CHASSIS_DARK, Offset(center.x + fenderOuterX, fenderTopY + 3f * scale), Offset(center.x + fenderOuterX, fenderBotY - 2f * scale), strokeWidth = 2.8f * scale, cap = StrokeCap.Round)
    drawLine(Color(0xFFEF4444), Offset(center.x + fenderInnerX + 2f * scale, fenderBotY - 0.5f * scale), Offset(center.x + fenderOuterX - 2f * scale, fenderBotY - 1.5f * scale), strokeWidth = 2.0f * scale, cap = StrokeCap.Round)

    // =========================================================================
    // 7. PANORAMIC 4-PILLAR HORIZON™ CAB + BLUE ROOF CAP + MIRRORS + RTK DOME
    // =========================================================================
    val cabFrontY = center.y - tractorL * 0.18f
    val cabRearY = center.y + tractorL * 0.28f
    val cabFrontHalfW = tractorW * 0.34f
    val cabRearHalfW = tractorW * 0.40f

    // Telescoping Side-View Mirrors extending from cab A-pillars
    val mirrorY = cabFrontY + 2f * scale
    drawLine(Color(0xFF334155), Offset(center.x - cabFrontHalfW, mirrorY), Offset(center.x - cabFrontHalfW - 7.5f * scale, mirrorY - 2f * scale), strokeWidth = 1.6f * scale, cap = StrokeCap.Round)
    drawRoundRect(Color(0xFF0F172A), Offset(center.x - cabFrontHalfW - 9.5f * scale, mirrorY - 4.2f * scale), Size(3.6f * scale, 5.2f * scale), CornerRadius(1.2f, 1.2f))
    drawLine(Color(0xFF334155), Offset(center.x + cabFrontHalfW, mirrorY), Offset(center.x + cabFrontHalfW + 7.5f * scale, mirrorY - 2f * scale), strokeWidth = 1.6f * scale, cap = StrokeCap.Round)
    drawRoundRect(Color(0xFF0F172A), Offset(center.x + cabFrontHalfW + 5.9f * scale, mirrorY - 4.2f * scale), Size(3.6f * scale, 5.2f * scale), CornerRadius(1.2f, 1.2f))

    // Wrap-around dark tinted panoramic cab glass footprint
    scratch.cabPath.reset()
    scratch.cabPath.moveTo(center.x - cabRearHalfW, cabRearY)
    scratch.cabPath.lineTo(center.x - cabFrontHalfW, cabFrontY)
    scratch.cabPath.lineTo(center.x + cabFrontHalfW, cabFrontY)
    scratch.cabPath.lineTo(center.x + cabRearHalfW, cabRearY)
    scratch.cabPath.close()
    drawPath(path = scratch.cabPath, color = if (isOledNight) Color(0xFF1A1005) else NH_GLASS_DARK)
    drawPath(path = scratch.cabPath, color = Color(0xFF0F172A), style = scratch.strokeBold)

    // Front Windshield Sky Glint (so cab reads clearly as angled glass)
    scratch.windshieldGlintPath.reset()
    scratch.windshieldGlintPath.moveTo(center.x - cabFrontHalfW * 0.82f, cabFrontY + 1.5f * scale)
    scratch.windshieldGlintPath.lineTo(center.x + cabFrontHalfW * 0.82f, cabFrontY + 1.5f * scale)
    scratch.windshieldGlintPath.lineTo(center.x + cabFrontHalfW * 0.72f, cabFrontY + 7.5f * scale)
    scratch.windshieldGlintPath.lineTo(center.x - cabFrontHalfW * 0.72f, cabFrontY + 7.5f * scale)
    scratch.windshieldGlintPath.close()
    drawPath(path = scratch.windshieldGlintPath, color = if (isOledNight) Color(0x44FF9100) else NH_GLASS_GLINT)

    // Overhanging Signature New Holland Blue Cab Roof Cap (inset slightly so glass edges show!)
    val roofFrontY = cabFrontY + 5.5f * scale
    val roofRearY = cabRearY - 4.0f * scale
    val roofFrontHalfW = cabFrontHalfW * 0.84f
    val roofRearHalfW = cabRearHalfW * 0.84f

    scratch.cabRoofPath.reset()
    scratch.cabRoofPath.moveTo(center.x - roofRearHalfW, roofRearY)
    scratch.cabRoofPath.lineTo(center.x - roofFrontHalfW, roofFrontY)
    scratch.cabRoofPath.lineTo(center.x + roofFrontHalfW, roofFrontY)
    scratch.cabRoofPath.lineTo(center.x + roofRearHalfW, roofRearY)
    scratch.cabRoofPath.close()
    drawPath(path = scratch.cabRoofPath, color = nhBlue)
    // Roof top bevel highlight
    drawRoundRect(
        color = nhBlueLight.copy(alpha = 0.65f),
        topLeft = Offset(center.x - roofFrontHalfW * 0.72f, roofFrontY + 2.5f * scale),
        size = Size(roofFrontHalfW * 1.44f, (roofRearY - roofFrontY) * 0.68f),
        cornerRadius = CornerRadius(4f * scale, 4f * scale)
    )
    drawPath(path = scratch.cabRoofPath, color = nhBlueDark, style = scratch.strokeMedium)

    // Cab Roof Front LED Work-Light Pods
    drawLine(headlightColor, Offset(center.x - roofFrontHalfW * 0.72f, roofFrontY + 1f), Offset(center.x - roofFrontHalfW * 0.35f, roofFrontY + 1f), strokeWidth = 2.0f * scale, cap = StrokeCap.Round)
    drawLine(headlightColor, Offset(center.x + roofFrontHalfW * 0.35f, roofFrontY + 1f), Offset(center.x + roofFrontHalfW * 0.72f, roofFrontY + 1f), strokeWidth = 2.0f * scale, cap = StrokeCap.Round)

    // Left-Rear Rotating Amber Roof Beacon
    val beaconCenter = Offset(center.x - roofRearHalfW * 0.76f, roofRearY - 3.5f * scale)
    drawCircle(color = Color(0x44F59E0B), radius = 4.6f * scale, center = beaconCenter)
    drawCircle(color = Color(0xFFF59E0B), radius = 2.4f * scale, center = beaconCenter)

    // Front-Center PLM / RTK GNSS Receiver Dome (shows Autosteer lock status cleanly!)
    val domeCenter = Offset(center.x, roofFrontY + 6.5f * scale)
    if (isAutoSteerEngaged) {
        drawCircle(color = steerRingAccent.copy(alpha = 0.30f), radius = 8.4f * scale, center = domeCenter)
    }
    drawCircle(color = nhRim, radius = 5.4f * scale, center = domeCenter)
    drawCircle(color = Color(0xFF0F172A), radius = 4.4f * scale, center = domeCenter)
    drawCircle(color = steerRingAccent, radius = 3.2f * scale, center = domeCenter)
}

/**
 * Authentic New Holland T7/T8-inspired 2D overhead bird's-eye tractor & implement renderer:
 * Matches the 3D tractor's unmistakable T-silhouette (front weights, narrow New Holland Blue hood
 * with yellow/white stripes, steered front MFWD wheels with white rims, large lugged rear ag tires,
 * blue/black rear fenders, panoramic cab with blue roof cap, mirrors & RTK dome).
 */
internal fun DrawScope.drawTractorIn2D(
    center: Offset,
    pixelsPerMeter: Float,
    vehicleConfig: VehicleConfig,
    implementConfig: ImplementConfig,
    actualSteerDeg: Double,
    isAutoSteerEngaged: Boolean,
    isMasterActive: Boolean,
    threePointState: ThreePointState,
    sectionStates: List<Boolean>,
    themeMode: DisplayThemeMode,
    scratch: CanvasScratchBuffers
) {
    val isDaylight = themeMode == DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST
    val isOledNight = themeMode == DisplayThemeMode.CAB_OLED_NIGHT
    val isHitchLowered = threePointState == ThreePointState.LOWERED || threePointState == ThreePointState.FLOAT
    val isWorking = isHitchLowered && (isMasterActive || isAutoSteerEngaged)

    val tractorLenPx = (vehicleConfig.wheelbase.toFloat() * 1.65f * pixelsPerMeter).coerceIn(32f, 114f)
    val tractorWidthPx = (vehicleConfig.trackWidth.toFloat() * pixelsPerMeter).coerceIn(22f, 70f)
    val steerClamped = actualSteerDeg.toFloat().coerceIn(-45f, 45f)

    val nhBlue = if (isOledNight) NH_BLUE_NIGHT else NH_BLUE_PRIMARY
    val nhBlueLight = if (isOledNight) NH_BLUE_NIGHT_HIGHLIGHT else NH_BLUE_HIGHLIGHT
    val nhBlueDark = if (isOledNight) Color(0xFF0A1626) else NH_BLUE_SHADOW
    val nhRim = if (isOledNight) NH_RIM_NIGHT else NH_RIM_WHITE
    val nhStripeYellow = if (isOledNight) Color(0xFFD97706) else NH_YELLOW_STRIPE
    val tireLug = if (isOledNight) NH_TIRE_LUG_NIGHT else NH_TIRE_LUG
    val bodyBorder = when {
        isOledNight -> if (isAutoSteerEngaged) Color(0xFFFFB300) else Color(0xFF475569)
        isDaylight -> Color(0xFF0B1F3A)
        else -> Color(0xFF061529)
    }
    val steerRingAccent = when {
        isOledNight -> if (isAutoSteerEngaged) Color(0xFFFFB300) else Color(0xFF9A6700)
        isAutoSteerEngaged -> Color(0xFF00F59B)
        else -> Color(0xFFF59E0B)
    }

    // 1. Implement Toolbar & 3-Point Hitch State in 2D
    val baseToolbarY = center.y + (implementConfig.offsetBehindTractor.toFloat() * pixelsPerMeter).coerceAtLeast(tractorLenPx * 0.46f)
    val toolbarWidthPx = (implementConfig.toolWidth.toFloat() * pixelsPerMeter).coerceAtLeast(tractorWidthPx * 1.65f)
    val toolbarY = if (isHitchLowered) baseToolbarY else baseToolbarY - 4f

    if (!isHitchLowered) {
        drawLine(
            color = if (isDaylight) Color(0x380F172A) else Color(0x55000000),
            start = Offset(center.x - toolbarWidthPx * 0.5f, baseToolbarY + 4f),
            end = Offset(center.x + toolbarWidthPx * 0.5f, baseToolbarY + 4f),
            strokeWidth = 5f,
            cap = StrokeCap.Round
        )
    }

    // 3-Point Hitch Links
    val hitchAnchorY = center.y + tractorLenPx * 0.28f
    val linkColor = if (isHitchLowered) Color(0xFF64748B) else Color(0xFFF59E0B)
    drawLine(linkColor, Offset(center.x - tractorWidthPx * 0.24f, hitchAnchorY), Offset(center.x - tractorWidthPx * 0.38f, toolbarY), strokeWidth = 2.2f)
    drawLine(linkColor, Offset(center.x + tractorWidthPx * 0.24f, hitchAnchorY), Offset(center.x + tractorWidthPx * 0.38f, toolbarY), strokeWidth = 2.2f)

    // Toolbar Beam
    val toolbarColor = when {
        !isHitchLowered -> Color(0xFFF59E0B)
        isWorking -> if (isOledNight) Color(0xFFFF9100) else Color(0xFF00E676)
        else -> Color(0xFF38BDF8)
    }
    drawLine(Color(0xCC0F172A), Offset(center.x - toolbarWidthPx * 0.5f, toolbarY), Offset(center.x + toolbarWidthPx * 0.5f, toolbarY), strokeWidth = 7.5f, cap = StrokeCap.Round)
    drawLine(toolbarColor, Offset(center.x - toolbarWidthPx * 0.5f, toolbarY), Offset(center.x + toolbarWidthPx * 0.5f, toolbarY), strokeWidth = 4.2f, cap = StrokeCap.Round)

    val numSec = implementConfig.numSections.coerceIn(1, 16)
    val secW = toolbarWidthPx / numSec
    for (i in 0 until numSec) {
        val sx = (center.x - toolbarWidthPx * 0.5f) + (i + 0.5f) * secW
        val secOn = isWorking && (sectionStates.isEmpty() || sectionStates.getOrElse(i) { true })
        drawCircle(
            color = if (!isHitchLowered) Color(0xFFFBBF24) else if (secOn) Color(0xFF00F59B) else Color(0xFF64748B),
            radius = 2.6f,
            center = Offset(sx, toolbarY)
        )
    }
    drawCircle(color = Color(0xFF0F172A), radius = 5.5f, center = Offset(center.x, toolbarY))
    drawCircle(color = if (isHitchLowered) Color(0xFF00F59B) else Color(0xFFF59E0B), radius = 3.6f, center = Offset(center.x, toolbarY))

    // 2. Exposed Front Axle & Dynamically Steered MFWD Front Tires
    val frontAxleY = center.y - tractorLenPx * 0.44f
    val frontWheelW = (tractorWidthPx * 0.21f).coerceIn(5.5f, 12.5f)
    val frontWheelL = (tractorLenPx * 0.28f).coerceIn(10f, 27f)
    val frontTrackHalf = tractorWidthPx * 0.50f

    drawLine(Color(0xFF334155), Offset(center.x - frontTrackHalf, frontAxleY), Offset(center.x + frontTrackHalf, frontAxleY), strokeWidth = 3.2f)
    drawNewHollandFrontTire(
        pivot = Offset(center.x - frontTrackHalf, frontAxleY),
        steerDeg = steerClamped,
        isLeftWheel = true,
        wheelW = frontWheelW,
        wheelL = frontWheelL,
        lugColor = tireLug,
        rimColor = nhRim,
        steerAccent = if (abs(steerClamped) > 1.2f) steerRingAccent else nhRim,
        scratch = scratch
    )
    drawNewHollandFrontTire(
        pivot = Offset(center.x + frontTrackHalf, frontAxleY),
        steerDeg = steerClamped,
        isLeftWheel = false,
        wheelW = frontWheelW,
        wheelL = frontWheelL,
        lugColor = tireLug,
        rimColor = nhRim,
        steerAccent = if (abs(steerClamped) > 1.2f) steerRingAccent else nhRim,
        scratch = scratch
    )

    // 3. Large Rear Agricultural Tires
    val rearAxleY = center.y + tractorLenPx * 0.08f
    val rearW = (frontWheelW * 1.48f).coerceIn(8f, 18f)
    val rearL = (frontWheelL * 1.44f).coerceIn(14f, 39f)
    val rearInnerEdge = tractorWidthPx * 0.42f

    drawLine(NH_CHASSIS_DARK, Offset(center.x - rearInnerEdge - rearW * 0.5f, rearAxleY), Offset(center.x + rearInnerEdge + rearW * 0.5f, rearAxleY), strokeWidth = 4.5f)
    drawNewHollandRearTire(
        left = center.x - rearInnerEdge - rearW,
        top = rearAxleY - rearL * 0.5f,
        wheelW = rearW,
        wheelL = rearL,
        isLeftWheel = true,
        lugColor = tireLug,
        rimColor = nhRim,
        scratch = scratch
    )
    drawNewHollandRearTire(
        left = center.x + rearInnerEdge,
        top = rearAxleY - rearL * 0.5f,
        wheelW = rearW,
        wheelL = rearL,
        isLeftWheel = false,
        lugColor = tireLug,
        rimColor = nhRim,
        scratch = scratch
    )

    // 4. Front Weights & Narrow Sculpted New Holland Blue Hood
    val noseTipY = center.y - tractorLenPx * 0.65f
    val hoodBackY = center.y - tractorLenPx * 0.14f
    val hoodBackHalfW = tractorWidthPx * 0.26f
    val hoodFrontHalfW = tractorWidthPx * 0.18f

    val weightW = tractorWidthPx * 0.34f
    val weightL = (tractorLenPx * 0.095f).coerceIn(3.5f, 9f)
    drawRoundRect(nhBlueDark, Offset(center.x - weightW * 0.5f, noseTipY - weightL), Size(weightW, weightL + 1.5f), CornerRadius(2f, 2f))
    drawRoundRect(bodyBorder, Offset(center.x - weightW * 0.5f, noseTipY - weightL), Size(weightW, weightL + 1.5f), CornerRadius(2f, 2f), style = scratch.strokeThin)

    scratch.bodyPath.reset()
    scratch.bodyPath.moveTo(center.x - hoodBackHalfW, hoodBackY)
    scratch.bodyPath.lineTo(center.x - hoodFrontHalfW, noseTipY + 2.5f)
    scratch.bodyPath.lineTo(center.x - hoodFrontHalfW * 0.65f, noseTipY)
    scratch.bodyPath.lineTo(center.x + hoodFrontHalfW * 0.65f, noseTipY)
    scratch.bodyPath.lineTo(center.x + hoodFrontHalfW, noseTipY + 2.5f)
    scratch.bodyPath.lineTo(center.x + hoodBackHalfW, hoodBackY)
    scratch.bodyPath.close()
    drawPath(path = scratch.bodyPath, color = nhBlue)

    // Sculpted center bonnet highlight
    scratch.hoodHighlightPath.reset()
    scratch.hoodHighlightPath.moveTo(center.x - hoodBackHalfW * 0.52f, hoodBackY - 1.5f)
    scratch.hoodHighlightPath.lineTo(center.x - hoodFrontHalfW * 0.45f, noseTipY + 3f)
    scratch.hoodHighlightPath.lineTo(center.x + hoodFrontHalfW * 0.45f, noseTipY + 3f)
    scratch.hoodHighlightPath.lineTo(center.x + hoodBackHalfW * 0.52f, hoodBackY - 1.5f)
    scratch.hoodHighlightPath.close()
    drawPath(path = scratch.hoodHighlightPath, color = nhBlueLight)

    // Yellow New Holland side stripes
    drawLine(nhStripeYellow, Offset(center.x - hoodBackHalfW * 0.84f, hoodBackY - 2f), Offset(center.x - hoodFrontHalfW * 0.84f, noseTipY + 6f), strokeWidth = 1.8f, cap = StrokeCap.Round)
    drawLine(nhStripeYellow, Offset(center.x + hoodBackHalfW * 0.84f, hoodBackY - 2f), Offset(center.x + hoodFrontHalfW * 0.84f, noseTipY + 6f), strokeWidth = 1.8f, cap = StrokeCap.Round)
    drawPath(path = scratch.bodyPath, color = bodyBorder, style = scratch.strokeMedium)

    // Right-side exhaust stack
    drawCircle(Color(0xFF0F172A), radius = (tractorWidthPx * 0.06f).coerceIn(2f, 3.8f), center = Offset(center.x + hoodBackHalfW + 2.5f, hoodBackY - 2f))

    // 5. Molded New Holland Blue Rear Fenders
    val fenderTopY = rearAxleY - rearL * 0.42f
    val fenderBotY = rearAxleY + rearL * 0.46f
    val fenderInnerX = tractorWidthPx * 0.34f
    val fenderOuterX = rearInnerEdge + rearW * 0.50f
    drawRect(nhBlue, Offset(center.x - fenderOuterX, fenderTopY), Size(fenderOuterX - fenderInnerX, fenderBotY - fenderTopY))
    drawRect(nhBlue, Offset(center.x + fenderInnerX, fenderTopY), Size(fenderOuterX - fenderInnerX, fenderBotY - fenderTopY))

    // 6. Panoramic Cab + Blue Roof Cap + RTK GPS Dome
    val cabFrontY = center.y - tractorLenPx * 0.18f
    val cabRearY = center.y + tractorLenPx * 0.28f
    val cabFrontHalfW = tractorWidthPx * 0.35f
    val cabRearHalfW = tractorWidthPx * 0.41f

    scratch.cabPath.reset()
    scratch.cabPath.moveTo(center.x - cabRearHalfW, cabRearY)
    scratch.cabPath.lineTo(center.x - cabFrontHalfW, cabFrontY)
    scratch.cabPath.lineTo(center.x + cabFrontHalfW, cabFrontY)
    scratch.cabPath.lineTo(center.x + cabRearHalfW, cabRearY)
    scratch.cabPath.close()
    drawPath(path = scratch.cabPath, color = if (isOledNight) Color(0xFF1A1005) else NH_GLASS_DARK)
    drawPath(path = scratch.cabPath, color = bodyBorder, style = scratch.strokeMedium)

    // Blue Roof Cap inset over cab glass
    val roofInsetX = tractorWidthPx * 0.06f
    val roofInsetY = tractorLenPx * 0.065f
    scratch.cabRoofPath.reset()
    scratch.cabRoofPath.moveTo(center.x - (cabRearHalfW - roofInsetX), cabRearY - roofInsetY * 0.7f)
    scratch.cabRoofPath.lineTo(center.x - (cabFrontHalfW - roofInsetX), cabFrontY + roofInsetY)
    scratch.cabRoofPath.lineTo(center.x + (cabFrontHalfW - roofInsetX), cabFrontY + roofInsetY)
    scratch.cabRoofPath.lineTo(center.x + (cabRearHalfW - roofInsetX), cabRearY - roofInsetY * 0.7f)
    scratch.cabRoofPath.close()
    drawPath(path = scratch.cabRoofPath, color = nhBlue)
    drawPath(path = scratch.cabRoofPath, color = nhBlueDark, style = scratch.strokeThin)

    // Roof Beacon + RTK Dome
    val domeRadius = (tractorWidthPx * 0.13f).coerceIn(3f, 6.2f)
    val domeCenter = Offset(center.x, cabFrontY + roofInsetY + domeRadius + 1.5f)
    if (isAutoSteerEngaged) {
        drawCircle(color = steerRingAccent.copy(alpha = 0.28f), radius = domeRadius + 3.2f, center = domeCenter)
    }
    drawCircle(color = nhRim, radius = domeRadius + 1.6f, center = domeCenter)
    drawCircle(color = Color(0xFF0F172A), radius = domeRadius + 0.6f, center = domeCenter)
    drawCircle(color = steerRingAccent, radius = domeRadius - 0.4f, center = domeCenter)
}

/**
 * Draws a steered MFWD front tractor tire with chevron ag tread lugs, a bright New Holland white
 * outer rim flank, and a pivoting front mudguard fender. Zero per-frame allocations.
 */
private fun DrawScope.drawNewHollandFrontTire(
    pivot: Offset,
    steerDeg: Float,
    isLeftWheel: Boolean,
    wheelW: Float,
    wheelL: Float,
    lugColor: Color,
    rimColor: Color,
    steerAccent: Color,
    scratch: CanvasScratchBuffers
) {
    rotate(degrees = steerDeg, pivot = pivot) {
        val left = pivot.x - wheelW * 0.5f
        val top = pivot.y - wheelL * 0.5f

        // Chunky rubber tire carcass
        drawRoundRect(
            color = NH_TIRE_RUBBER,
            topLeft = Offset(left, top),
            size = Size(wheelW, wheelL),
            cornerRadius = CornerRadius(wheelW * 0.28f, wheelW * 0.28f)
        )

        // Chevron agricultural tread lugs across the tire crown
        val lugCount = 5
        val step = wheelL / (lugCount + 1)
        for (i in 1..lugCount) {
            val ly = top + i * step
            drawLine(
                color = lugColor,
                start = Offset(left + 1f, ly + 1.4f),
                end = Offset(pivot.x, ly - 1.2f),
                strokeWidth = 1.6f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = lugColor,
                start = Offset(pivot.x, ly - 1.2f),
                end = Offset(left + wheelW - 1f, ly + 1.4f),
                strokeWidth = 1.6f,
                cap = StrokeCap.Round
            )
        }

        // Signature New Holland White wheel rim visible on outer sidewall edge
        val outerEdgeX = if (isLeftWheel) left + 1.1f else left + wheelW - 1.1f
        drawLine(
            color = steerAccent,
            start = Offset(outerEdgeX, top + wheelL * 0.24f),
            end = Offset(outerEdgeX, top + wheelL * 0.76f),
            strokeWidth = (wheelW * 0.26f).coerceIn(1.6f, 3.2f),
            cap = StrokeCap.Round
        )

        // Pivoting black front mudguard covering the rear-top quadrant of the wheel
        drawRoundRect(
            color = Color(0xCC1E293B),
            topLeft = Offset(left - 0.6f, pivot.y + wheelL * 0.08f),
            size = Size(wheelW + 1.2f, wheelL * 0.36f),
            cornerRadius = CornerRadius(2f, 2f)
        )

        // Crisp tire outline
        drawRoundRect(
            color = if (abs(steerDeg) > 1.2f) steerAccent.copy(alpha = 0.7f) else rimColor.copy(alpha = 0.25f),
            topLeft = Offset(left, top),
            size = Size(wheelW, wheelL),
            cornerRadius = CornerRadius(wheelW * 0.28f, wheelW * 0.28f),
            style = scratch.strokeTireOutline
        )
    }
}

/**
 * Draws a massive rear agricultural drive tire with deep V-chevron lugs and signature New Holland
 * white outer rim edge. Zero per-frame allocations.
 */
private fun DrawScope.drawNewHollandRearTire(
    left: Float,
    top: Float,
    wheelW: Float,
    wheelL: Float,
    isLeftWheel: Boolean,
    lugColor: Color,
    rimColor: Color,
    scratch: CanvasScratchBuffers
) {
    val midX = left + wheelW * 0.5f
    drawRoundRect(
        color = NH_TIRE_RUBBER,
        topLeft = Offset(left, top),
        size = Size(wheelW, wheelL),
        cornerRadius = CornerRadius(wheelW * 0.24f, wheelW * 0.24f)
    )

    // Deep V-chevron ag tread bars
    val lugCount = 6
    val step = wheelL / (lugCount + 1)
    for (i in 1..lugCount) {
        val ly = top + i * step
        drawLine(
            color = lugColor,
            start = Offset(left + 1.2f, ly + 2.0f),
            end = Offset(midX, ly - 1.6f),
            strokeWidth = 2.0f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = lugColor,
            start = Offset(midX, ly - 1.6f),
            end = Offset(left + wheelW - 1.2f, ly + 2.0f),
            strokeWidth = 2.0f,
            cap = StrokeCap.Round
        )
    }

    // Signature New Holland White rear wheel rim highlight along outer edge
    val outerEdgeX = if (isLeftWheel) left + 1.4f else left + wheelW - 1.4f
    drawLine(
        color = rimColor,
        start = Offset(outerEdgeX, top + wheelL * 0.20f),
        end = Offset(outerEdgeX, top + wheelL * 0.80f),
        strokeWidth = (wheelW * 0.22f).coerceIn(2.0f, 3.8f),
        cap = StrokeCap.Round
    )

    drawRoundRect(
        color = Color(0x55334155),
        topLeft = Offset(left, top),
        size = Size(wheelW, wheelL),
        cornerRadius = CornerRadius(wheelW * 0.24f, wheelW * 0.24f),
        style = scratch.strokeThin
    )
}

internal fun DrawScope.drawCompass(
    center: Offset,
    headingDeg: Double,
    isHeadUpMode: Boolean,
    themeMode: DisplayThemeMode,
    scratch: CanvasScratchBuffers
) {
    val isDaylight = themeMode == DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST
    val compassRadius = 22.dp.toPx()
    drawCircle(color = if (isDaylight) Color(0xE8F8FAFC) else Color(0xB30F172A), radius = compassRadius, center = center)
    drawCircle(color = if (isDaylight) Color(0xFF334155) else Color(0x7738BDF8), radius = compassRadius, center = center, style = scratch.strokeThin)

    val needleRotation = if (isHeadUpMode) -headingDeg.toFloat() else 0f
    rotate(degrees = needleRotation, pivot = center) {
        scratch.compassNorthPath.reset()
        scratch.compassNorthPath.moveTo(center.x, center.y - compassRadius + 4.dp.toPx())
        scratch.compassNorthPath.lineTo(center.x - 4.5.dp.toPx(), center.y)
        scratch.compassNorthPath.lineTo(center.x + 4.5.dp.toPx(), center.y)
        scratch.compassNorthPath.close()
        drawPath(path = scratch.compassNorthPath, color = Color(0xFFDC2626))

        scratch.compassSouthPath.reset()
        scratch.compassSouthPath.moveTo(center.x, center.y + compassRadius - 4.dp.toPx())
        scratch.compassSouthPath.lineTo(center.x - 4.5.dp.toPx(), center.y)
        scratch.compassSouthPath.lineTo(center.x + 4.5.dp.toPx(), center.y)
        scratch.compassSouthPath.close()
        drawPath(path = scratch.compassSouthPath, color = if (isDaylight) Color(0xFF475569) else Color(0xFF94A3B8))
    }
}

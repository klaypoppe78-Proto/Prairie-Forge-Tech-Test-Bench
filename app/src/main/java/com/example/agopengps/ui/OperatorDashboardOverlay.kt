package com.example.agopengps.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agopengps.CameraViewMode
import java.util.Locale
import com.example.agopengps.GnssSourceMode
import com.example.agopengps.navigation.ABLine
import com.example.agopengps.navigation.FixQuality
import com.example.agopengps.navigation.SlidingHitchConfig
import com.example.agopengps.navigation.SlidingHitchState
import com.example.agopengps.navigation.VehicleConfig
import com.example.agopengps.navigation.VehicleState
import kotlin.math.*

enum class DashboardMode {
    COMPACT_BAR,
    FULL_COCKPIT
}

/**
 * Dedicated high-contrast Operator Dashboard Overlay.
 * Designed for immediate glanceability from the tractor cab seat in full sunlight:
 * - Current Steering Angle (Actual vs Commanded Target, WAS Gauge)
 * - Cross-Track Error (XTE / CTE) in inches & cm with left/right directional guidance
 * - Heading Error (Δ Heading off AB line in degrees with turn bias)
 *
 * Provides a clean, dead-simple operator interface while offering instant 1-tap access
 * to all deep submenus and calibration features (Settings, NMEA Diagnostics, UM982 RTK,
 * Sliding Hitch, WAS Calibration, AgIO Hardware, and Field Setup Wizard).
 */
@Composable
fun OperatorDashboardOverlay(
    vehicleState: VehicleState,
    vehicleConfig: VehicleConfig,
    currentABLine: ABLine?,
    activeSwathIndex: Int,
    gnssSource: GnssSourceMode,
    isNightMode: Boolean,
    cameraViewMode: CameraViewMode,
    slidingHitchConfig: SlidingHitchConfig? = null,
    slidingHitchState: SlidingHitchState? = null,
    // Operator Guidance Actions
    onToggleAutoSteer: () -> Unit,
    onSnapABLine: () -> Unit,
    onNudgeSwath: (Double) -> Unit,
    onNudgeHalfRow22: (Boolean) -> Unit = {},
    onNudgeFullRow22: (Boolean) -> Unit = {},
    onZeroWas: () -> Unit = {},
    onCycleSwath: (Int) -> Unit,
    onToggleCameraMode: () -> Unit,
    // Deep Control Submenus
    onOpenSettings: () -> Unit,
    onOpenNmeaDiagnostics: () -> Unit,
    onOpenUm982Dialog: () -> Unit,
    onOpenSlidingHitchDialog: () -> Unit,
    onOpenWasCalibrationDialog: () -> Unit,
    onOpenAgIoDialog: () -> Unit,
    onOpenFieldWizard: () -> Unit,
    onOpenVirtualDrive: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dashboardMode by remember { mutableStateOf(DashboardMode.FULL_COCKPIT) }
    var showDeepControlSheet by remember { mutableStateOf(false) }

    // Guidance Metrics
    val actualSteerDeg = vehicleState.actualSteerAngleDeg
    val targetSteerDeg = vehicleState.targetSteerAngleDeg
    val steerDiffDeg = targetSteerDeg - actualSteerDeg

    val cteMeters = vehicleState.crossTrackErrorMeters
    val cteCm = vehicleState.crossTrackErrorCm
    val cteInches = cteMeters * 39.3701
    val absCteInches = abs(cteInches)
    val absCteCm = abs(cteCm)

    val headingErrorDeg = vehicleState.headingErrorDeg
    val absHeadingErrorDeg = abs(headingErrorDeg)

    // AgOpenGPS Color Codes for sunlight outdoor contrast:
    // Green: On Track (<1.2 in / 3cm)
    // Amber: Moderate Deviation (1.2 - 4.0 in)
    // Red: Off Track (>4.0 in)
    val cteStatusColor = when {
        absCteInches < 1.2 -> Color(0xFF00E676)
        absCteInches < 4.0 -> Color(0xFFFFD600)
        else -> Color(0xFFFF1744)
    }

    val steerStatusColor = when {
        abs(steerDiffDeg) < 0.5 -> Color(0xFF00E676)
        abs(steerDiffDeg) < 2.0 -> Color(0xFFFFD600)
        else -> Color(0xFFFF9100)
    }

    val headingStatusColor = when {
        absHeadingErrorDeg < 0.8 -> Color(0xFF00E676)
        absHeadingErrorDeg < 2.5 -> Color(0xFFFFD600)
        else -> Color(0xFFFF9100)
    }

    val panelBackground = if (isNightMode) Color(0xF20F1612) else Color(0xF5141E18)
    val cardBackground = if (isNightMode) Color(0xFF16211C) else Color(0xFF1E2E25)
    val textPrimary = Color.White
    val textMuted = Color(0xFF90A4AE)

    Column(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("operator_dashboard_overlay")
    ) {
        // Main Operator Cockpit Surface
        Surface(
            color = panelBackground,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF2E4639)),
            shadowElevation = 10.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {

                // Top Operator Header: Status, Mode, Swath, and Expand/Collapse Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: RTK & Swath Information
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = when (vehicleState.fixQuality) {
                                FixQuality.RTK_FIX -> Color(0xFF003816)
                                FixQuality.RTK_FLOAT -> Color(0xFF3E3000)
                                else -> Color(0xFF3E1200)
                            },
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                when (vehicleState.fixQuality) {
                                    FixQuality.RTK_FIX -> Color(0xFF00E676)
                                    FixQuality.RTK_FLOAT -> Color(0xFFFFD600)
                                    else -> Color(0xFFFF5252)
                                }
                            ),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (vehicleState.fixQuality) {
                                                FixQuality.RTK_FIX -> Color(0xFF00E676)
                                                FixQuality.RTK_FLOAT -> Color(0xFFFFD600)
                                                else -> Color(0xFFFF5252)
                                            }
                                        )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = vehicleState.fixQuality.name.replace("_", " "),
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // Swath Identifier
                        val swathLabel = if (currentABLine != null) {
                            val passOffset = if (activeSwathIndex == 0) "BASE 0" else String.format(Locale.US, "PASS %+d", activeSwathIndex)
                            "${currentABLine.name} ($passOffset)"
                        } else {
                            "NO AB LINE (SET A-B)"
                        }
                        Text(
                            text = swathLabel,
                            color = Color(0xFF81D4FA),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }

                    // Right: Mode Switcher & Deep Control Launcher
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Quick NMEA Diagnostics Button
                        Button(
                            onClick = onOpenNmeaDiagnostics,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0277BD)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.Troubleshoot, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("NMEA DIAG", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        // Deep Controls Hub Button
                        IconButton(
                            onClick = { showDeepControlSheet = !showDeepControlSheet },
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (showDeepControlSheet) Color(0xFF00E676) else Color(0xFF263930))
                        ) {
                            Icon(
                                Icons.Default.Apps,
                                contentDescription = "Submenus",
                                tint = if (showDeepControlSheet) Color.Black else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Compact / Full Toggle
                        IconButton(
                            onClick = {
                                dashboardMode = if (dashboardMode == DashboardMode.FULL_COCKPIT)
                                    DashboardMode.COMPACT_BAR
                                else
                                    DashboardMode.FULL_COCKPIT
                            },
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF263930))
                        ) {
                            Icon(
                                imageVector = if (dashboardMode == DashboardMode.FULL_COCKPIT)
                                    Icons.Default.ExpandLess
                                else
                                    Icons.Default.ExpandMore,
                                contentDescription = "Toggle Dashboard Size",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // --- CORE GUIDANCE METRICS GRID (STEERING ANGLE | CROSS-TRACK ERROR | HEADING ERROR) ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. STEERING ANGLE CARD
                    Surface(
                        color = cardBackground,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A3D33)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "STEER ANGLE",
                                    color = textMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = if (actualSteerDeg < -0.1) "LEFT" else if (actualSteerDeg > 0.1) "RIGHT" else "CENTER",
                                    color = steerStatusColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            // Large High-Contrast Digital Readout
                            Text(
                                text = String.format(Locale.US, "%+.1f°", actualSteerDeg),
                                color = Color.White,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.testTag("steer_angle_readout")
                            )

                            // Analog Steer Angle Arc / Wheel Position Bar
                            SteerAngleBar(
                                actualDeg = actualSteerDeg,
                                targetDeg = targetSteerDeg,
                                maxDeg = vehicleConfig.maxSteerAngleDeg,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(18.dp)
                                    .padding(vertical = 2.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "TGT: ${String.format(Locale.US, "%+.1f°", targetSteerDeg)}",
                                    color = Color(0xFF81C784),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "Δ ${String.format(Locale.US, "%+.1f°", steerDiffDeg)}",
                                    color = steerStatusColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    // 2. CROSS-TRACK ERROR (XTE / CTE) CARD
                    Surface(
                        color = cardBackground,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, cteStatusColor.copy(alpha = 0.7f)),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "CROSS-TRACK ERROR",
                                    color = textMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                val dirText = when {
                                    cteInches < -0.2 -> "◀ STEER RIGHT"
                                    cteInches > 0.2 -> "STEER LEFT ▶"
                                    else -> "◆ ON LINE"
                                }
                                Text(
                                    text = dirText,
                                    color = cteStatusColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            // Giant High-Contrast Value (Imperial Inches & Metric cm)
                            val arrowSymbol = when {
                                cteInches < -0.2 -> "◀ "
                                cteInches > 0.2 -> "▶ "
                                else -> "◆ "
                            }
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "$arrowSymbol${String.format(Locale.US, "%.1f", absCteInches)}\"",
                                    color = cteStatusColor,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.testTag("cte_large_display")
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "(${String.format(Locale.US, "%.1f", absCteCm)} cm)",
                                    color = textMuted,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }

                            // Linear Lightbar Deviation Bar
                            CrossTrackDeviationBar(
                                cteInches = cteInches,
                                maxRangeInches = 6.0,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(18.dp)
                                    .padding(vertical = 2.dp)
                            )

                            // Status subtext
                            Text(
                                text = if (absCteInches < 1.2) "SUB-INCH ACCURACY" else if (absCteInches < 4.0) "CORRECTING TO SWATH" else "EXCESSIVE CROSS-TRACK",
                                color = cteStatusColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // 3. HEADING ERROR (Δ HEADING) CARD
                    Surface(
                        color = cardBackground,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A3D33)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "HEADING ERROR",
                                    color = textMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = if (absHeadingErrorDeg < 0.5) "PARALLEL" else if (headingErrorDeg > 0) "POINTING R" else "POINTING L",
                                    color = headingStatusColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            // Large Digital Heading Error Readout
                            Text(
                                text = String.format(Locale.US, "%+.1f°", headingErrorDeg),
                                color = Color.White,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.testTag("heading_error_readout")
                            )

                            // Heading Alignment Gauge Bar
                            HeadingErrorBar(
                                headingErrorDeg = headingErrorDeg,
                                maxRangeDeg = 10.0,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(18.dp)
                                    .padding(vertical = 2.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "HDG: ${String.format(Locale.US, "%.1f°", vehicleState.headingDeg)}",
                                    color = textMuted,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                val biasText = if (headingErrorDeg > 0.3) "Turn Left" else if (headingErrorDeg < -0.3) "Turn Right" else "Aligned"
                                Text(
                                    text = biasText,
                                    color = headingStatusColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // If in Full Cockpit Mode: Show Operator Tactical Bar (Big AutoSteer Button, Swath Nudge & Snapping)
                AnimatedVisibility(
                    visible = dashboardMode == DashboardMode.FULL_COCKPIT,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // GIANT AUTO-STEER ENGAGE / DISENGAGE BUTTON
                            val isEngaged = vehicleState.isAutoSteerEngaged
                            val autoSteerBtnColor = if (isEngaged) Color(0xFF00C853) else Color(0xFFD32F2F)
                            val autoSteerTextColor = if (isEngaged) Color.Black else Color.White

                            Button(
                                onClick = onToggleAutoSteer,
                                colors = ButtonDefaults.buttonColors(containerColor = autoSteerBtnColor),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(48.dp)
                                    .testTag("dashboard_autosteer_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (isEngaged) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = autoSteerTextColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isEngaged) "AUTO-STEER ENGAGED" else "ENGAGE AUTO-STEER",
                                        color = autoSteerTextColor,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            // Snap Swath to Tractor Quick Action
                            OutlinedButton(
                                onClick = onSnapABLine,
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF)),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF002A35)),
                                modifier = Modifier
                                    .weight(0.9f)
                                    .height(48.dp)
                                    .testTag("dashboard_snap_ab_button")
                            ) {
                                Icon(Icons.Default.FilterCenterFocus, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("SNAP LINE", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }

                            // Swath Nudge Controls: -1" and +1"
                            Row(
                                modifier = Modifier.weight(1.1f),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Button(
                                    onClick = { onNudgeSwath(-2.54) }, // -1 inch in cm
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF263238)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Text("◀ -1\"", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                                Button(
                                    onClick = { onNudgeSwath(2.54) }, // +1 inch in cm
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF263238)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Text("+1\" ▶", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }

                            // Skip Pass Controls: -1 Pass / +1 Pass
                            Row(
                                modifier = Modifier.weight(1.1f),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onCycleSwath(-1) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Text("PASS -1", color = Color(0xFF81D4FA), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                }
                                OutlinedButton(
                                    onClick = { onCycleSwath(1) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Text("PASS +1", color = Color(0xFF81D4FA), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                }
                            }

                            // 2D / 3D Perspective Toggle
                            IconButton(
                                onClick = onToggleCameraMode,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF004D40))
                            ) {
                                Icon(
                                    imageVector = if (cameraViewMode == CameraViewMode.CAB_3D) Icons.Default.Explore else Icons.Default.ViewInAr,
                                    contentDescription = "Toggle 2D/3D",
                                    tint = Color(0xFF80CBC4),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // --- 22-INCH ORGANIC ROW-CROP NUDGE & WAS ZERO STRIP ---
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Half-Row 22" Nudge (-11" / +11")
                            Row(
                                modifier = Modifier.weight(1.2f),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Button(
                                    onClick = { onNudgeHalfRow22(false) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                ) {
                                    Text("◀ -11\"", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                                Button(
                                    onClick = { onNudgeHalfRow22(true) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                ) {
                                    Text("+11\" ▶", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }

                            // Full-Row 22" Nudge (-22" / +22")
                            Row(
                                modifier = Modifier.weight(1.2f),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Button(
                                    onClick = { onNudgeFullRow22(false) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF34D399)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                ) {
                                    Text("◀ -22\"", color = Color(0xFF34D399), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                                Button(
                                    onClick = { onNudgeFullRow22(true) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF34D399)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                ) {
                                    Text("+22\" ▶", color = Color(0xFF34D399), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }

                            // 1-Tap Zero WAS Action Button
                            Button(
                                onClick = onZeroWas,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier
                                    .weight(0.9f)
                                    .height(40.dp)
                                    .testTag("zero_was_quick_button")
                            ) {
                                Icon(Icons.Default.CenterFocusStrong, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("ZERO WAS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 10.sp)
                            }
                        }
                    }
                }

                // --- DEEP CONTROL SUBMENUS SHORTCUT DRAWER ---
                AnimatedVisibility(
                    visible = showDeepControlSheet,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = Color(0xFF263238), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "DEEP CONTROL & FIELD SETUP SUBMENUS:",
                            color = Color(0xFF81D4FA),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // Grid of Submenu Shortcuts
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // 1. Settings & Tuning
                            SubmenuLaunchCard(
                                icon = Icons.Default.Settings,
                                title = "TUNING",
                                subtitle = "PID & Valve",
                                color = Color(0xFF37474F),
                                onClick = {
                                    showDeepControlSheet = false
                                    onOpenSettings()
                                },
                                modifier = Modifier.weight(1f)
                            )

                            // 2. NMEA Field Diagnostics
                            SubmenuLaunchCard(
                                icon = Icons.Default.Troubleshoot,
                                title = "NMEA DIAG",
                                subtitle = "Stream & Hz",
                                color = Color(0xFF0277BD),
                                onClick = {
                                    showDeepControlSheet = false
                                    onOpenNmeaDiagnostics()
                                },
                                modifier = Modifier.weight(1f)
                            )

                            // 3. UM982 Dual-Antenna
                            SubmenuLaunchCard(
                                icon = Icons.Default.SatelliteAlt,
                                title = "UM982 RTK",
                                subtitle = "Dual Heading",
                                color = Color(0xFF1B5E20),
                                onClick = {
                                    showDeepControlSheet = false
                                    onOpenUm982Dialog()
                                },
                                modifier = Modifier.weight(1f)
                            )

                            // 4. Sliding Hitch
                            SubmenuLaunchCard(
                                icon = Icons.AutoMirrored.Filled.CompareArrows,
                                title = "SLIDING HITCH",
                                subtitle = "Implement Steer",
                                color = Color(0xFF006064),
                                onClick = {
                                    showDeepControlSheet = false
                                    onOpenSlidingHitchDialog()
                                },
                                modifier = Modifier.weight(1f)
                            )

                            // 5. WAS Calibration
                            SubmenuLaunchCard(
                                icon = Icons.Default.Tune,
                                title = "WAS ZERO",
                                subtitle = "Oscilloscope",
                                color = Color(0xFF4E342E),
                                onClick = {
                                    showDeepControlSheet = false
                                    onOpenWasCalibrationDialog()
                                },
                                modifier = Modifier.weight(1f)
                            )

                            // 6. AgIO Network
                            SubmenuLaunchCard(
                                icon = Icons.Default.Router,
                                title = "AgIO HUB",
                                subtitle = "UDP & USB",
                                color = Color(0xFF283593),
                                onClick = {
                                    showDeepControlSheet = false
                                    onOpenAgIoDialog()
                                },
                                modifier = Modifier.weight(1f)
                            )

                            // 7. Field Wizard
                            SubmenuLaunchCard(
                                icon = Icons.Default.AutoFixHigh,
                                title = "WIZARD",
                                subtitle = "Field & AB",
                                color = Color(0xFF2E7D32),
                                onClick = {
                                    showDeepControlSheet = false
                                    onOpenFieldWizard()
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubmenuLaunchCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = color,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .height(54.dp)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.height(2.dp))
            Text(title, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(subtitle, color = Color.White.copy(alpha = 0.7f), fontSize = 8.sp, maxLines = 1)
        }
    }
}

/**
 * Analog Steer Angle horizontal gauge bar showing actual vs commanded target.
 */
@Composable
private fun SteerAngleBar(
    actualDeg: Double,
    targetDeg: Double,
    maxDeg: Double,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val centerX = width / 2f

        // Track background
        drawRoundRect(
            color = Color(0xFF101915),
            size = Size(width, height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
        )

        // Center zero line
        drawLine(
            color = Color.White.copy(alpha = 0.6f),
            start = Offset(centerX, 0f),
            end = Offset(centerX, height),
            strokeWidth = 2f
        )

        val safeMax = maxDeg.coerceAtLeast(15.0)

        // Target steer line (Cyan notch)
        val targetRatio = (targetDeg / safeMax).coerceIn(-1.0, 1.0).toFloat()
        val targetX = centerX + targetRatio * (width / 2f)
        drawLine(
            color = Color(0xFF00E5FF),
            start = Offset(targetX, 0f),
            end = Offset(targetX, height),
            strokeWidth = 3f
        )

        // Actual steer bar (Vivid Green / Amber)
        val actualRatio = (actualDeg / safeMax).coerceIn(-1.0, 1.0).toFloat()
        val actualX = centerX + actualRatio * (width / 2f)
        val barColor = if (abs(targetDeg - actualDeg) < 0.8) Color(0xFF00E676) else Color(0xFFFFB300)

        if (actualRatio >= 0) {
            drawRect(
                color = barColor,
                topLeft = Offset(centerX, 2f),
                size = Size(actualX - centerX, height - 4f)
            )
        } else {
            drawRect(
                color = barColor,
                topLeft = Offset(actualX, 2f),
                size = Size(centerX - actualX, height - 4f)
            )
        }

        // Pointer diamond on actual
        val path = Path().apply {
            moveTo(actualX, 0f)
            lineTo(actualX + 4f, 4f)
            lineTo(actualX, 8f)
            lineTo(actualX - 4f, 4f)
            close()
        }
        drawPath(path, Color.White)
    }
}

/**
 * Cross-track error deviation lightbar scale.
 */
@Composable
private fun CrossTrackDeviationBar(
    cteInches: Double,
    maxRangeInches: Double,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val centerX = width / 2f

        // Background
        drawRoundRect(
            color = Color(0xFF0D1411),
            size = Size(width, height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
        )

        // Center line (Perfect Swath)
        drawLine(
            color = Color(0xFF00E676),
            start = Offset(centerX, 0f),
            end = Offset(centerX, height),
            strokeWidth = 3f
        )

        // Tolerance marks: 1.2 inches (sub-inch green band)
        val subInchRatio = (1.2 / maxRangeInches).toFloat()
        val leftSubInchX = centerX - subInchRatio * (width / 2f)
        val rightSubInchX = centerX + subInchRatio * (width / 2f)

        drawRect(
            color = Color(0xFF00E676).copy(alpha = 0.15f),
            topLeft = Offset(leftSubInchX, 0f),
            size = Size(rightSubInchX - leftSubInchX, height)
        )

        // Vehicle error indicator
        val ratio = (cteInches / maxRangeInches).coerceIn(-1.0, 1.0).toFloat()
        val vehX = centerX + ratio * (width / 2f)
        val absInches = abs(cteInches)
        val color = when {
            absInches < 1.2 -> Color(0xFF00E676)
            absInches < 4.0 -> Color(0xFFFFD600)
            else -> Color(0xFFFF1744)
        }

        // Fill error bar
        if (ratio >= 0) {
            drawRect(
                color = color.copy(alpha = 0.8f),
                topLeft = Offset(centerX, 2f),
                size = Size(vehX - centerX, height - 4f)
            )
        } else {
            drawRect(
                color = color.copy(alpha = 0.8f),
                topLeft = Offset(vehX, 2f),
                size = Size(centerX - vehX, height - 4f)
            )
        }

        // Triangle Marker on top
        val markerPath = Path().apply {
            moveTo(vehX, height)
            lineTo(vehX - 5f, height - 8f)
            lineTo(vehX + 5f, height - 8f)
            close()
        }
        drawPath(markerPath, Color.White)
    }
}

/**
 * Heading error alignment bar showing heading divergence from target AB line.
 */
@Composable
private fun HeadingErrorBar(
    headingErrorDeg: Double,
    maxRangeDeg: Double,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerX = width / 2f

        drawRoundRect(
            color = Color(0xFF0D1411),
            size = Size(width, height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
        )

        // Center zero heading line
        drawLine(
            color = Color.White.copy(alpha = 0.7f),
            start = Offset(centerX, 0f),
            end = Offset(centerX, height),
            strokeWidth = 2f
        )

        val ratio = (headingErrorDeg / maxRangeDeg).coerceIn(-1.0, 1.0).toFloat()
        val headX = centerX + ratio * (width / 2f)
        val absDeg = abs(headingErrorDeg)
        val barColor = when {
            absDeg < 0.8 -> Color(0xFF00E676)
            absDeg < 2.5 -> Color(0xFFFFD600)
            else -> Color(0xFFFF9100)
        }

        if (ratio >= 0) {
            drawRect(
                color = barColor.copy(alpha = 0.8f),
                topLeft = Offset(centerX, 3f),
                size = Size(headX - centerX, height - 6f)
            )
        } else {
            drawRect(
                color = barColor.copy(alpha = 0.8f),
                topLeft = Offset(headX, 3f),
                size = Size(centerX - headX, height - 6f)
            )
        }

        // Notch
        drawLine(
            color = Color.White,
            start = Offset(headX, 0f),
            end = Offset(headX, height),
            strokeWidth = 2.5f
        )
    }
}

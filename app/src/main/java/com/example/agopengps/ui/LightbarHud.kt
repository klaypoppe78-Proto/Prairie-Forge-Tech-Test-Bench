package com.example.agopengps.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agopengps.GnssSourceMode
import com.example.agopengps.navigation.FixQuality
import com.example.agopengps.navigation.SlidingHitchConfig
import com.example.agopengps.navigation.SlidingHitchState
import com.example.agopengps.navigation.VehicleState
import kotlin.math.abs

/**
 * Aero Dynamic HUD & Telemetry Bar.
 * Designed with modern aerospace & luxury automotive aesthetics:
 * - Floating frosted-glass capsule
 * - High-precision curved deviation meter with glowing status indicators
 * - Clean telemetry chips for Speed, Heading/Steer, and Worked Acres
 */
@Composable
fun LightbarHud(
    vehicleState: VehicleState,
    workedAcres: Double,
    isNightMode: Boolean,
    gnssSource: GnssSourceMode = GnssSourceMode.SIMULATOR,
    slidingHitchState: SlidingHitchState? = null,
    slidingHitchConfig: SlidingHitchConfig? = null,
    uTurnPrompt: String? = null,
    onToggleSimulation: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenSlidingHitch: () -> Unit = {},
    onToggleDashboard: () -> Unit = {},
    onOpenNmeaDiagnostics: () -> Unit = {},
    onRecenterGps: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val cteCm = vehicleState.crossTrackErrorCm
    val cteInches = vehicleState.crossTrackErrorMeters * 39.3701
    val absCteInches = abs(cteInches)

    // High-fidelity non-industrial status palette:
    // Glowing Emerald (< 1.2 in), Warm Amber (1.2 - 4.0 in), Coral Red (> 4.0 in)
    val statusColor = when {
        absCteInches < 1.2 -> Color(0xFF00F59B) // Luminous Emerald
        absCteInches < 4.0 -> Color(0xFFFBBF24) // Warm Amber Gold
        else -> Color(0xFFF43F5E)               // Coral Rose
    }

    val glassBackground = if (isNightMode) {
        Color(0xEB0A0F1D)
    } else {
        Color(0xEB0F172A)
    }
    val glassBorder = Color(0x3394A3B8)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag("lightbar_hud")
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = glassBackground,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, glassBorder),
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Top Telemetry Pod: RTK Fix | Cross-Track Error | Steer | Speed | Area
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left Cluster: RTK Badge & Simulation/Source Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RtkBadge(
                            fixQuality = vehicleState.fixQuality,
                            sats = vehicleState.satellites,
                            age = vehicleState.ageOfCorrectionSec
                        )

                        val (modeBg, modeTextColor, modeLabel, modeIcon) = when (gnssSource) {
                            GnssSourceMode.SIMULATOR -> Quad(Color(0x33F59E0B), Color(0xFFFBBF24), "SIM ON", Icons.Default.SmartToy)
                            GnssSourceMode.ANDROID_GPS -> Quad(Color(0x3338BDF8), Color(0xFF38BDF8), "TABLET GPS", Icons.Default.TabletAndroid)
                            GnssSourceMode.USB_AIO_TEENSY -> Quad(Color(0x3310B981), Color(0xFF34D399), "USB AIO", Icons.Default.Usb)
                            GnssSourceMode.UDP_NETWORK -> Quad(Color(0x33818CF8), Color(0xFFA5B4FC), "UDP AgIO", Icons.Default.Wifi)
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = modeBg,
                            border = BorderStroke(1.dp, modeTextColor.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .clickable { onToggleSimulation() }
                                .testTag("sim_mode_badge")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = modeIcon,
                                    contentDescription = null,
                                    tint = modeTextColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = modeLabel,
                                    color = modeTextColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        // Coordinates & One-Tap Recenter GPS Pill
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0x2238BDF8),
                            border = BorderStroke(1.dp, Color(0x4438BDF8)),
                            modifier = Modifier
                                .clickable { onRecenterGps() }
                                .testTag("top_recenter_gps_badge")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MyLocation,
                                    contentDescription = "Center on GPS",
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = String.format(java.util.Locale.US, "%.4f°, %.4f°", vehicleState.geoPosition.latitude, vehicleState.geoPosition.longitude),
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Sliding Hitch Pill (if active)
                        if (slidingHitchConfig?.isEnabled == true) {
                            val hitchCteCm = slidingHitchState?.implementCrossTrackErrorCm ?: 0.0
                            val hitchShiftCm = slidingHitchState?.currentHitchShiftCm ?: 0.0
                            val hitchColor = when {
                                abs(hitchCteCm) < 1.0 -> Color(0xFF00F59B)
                                abs(hitchCteCm) < 3.0 -> Color(0xFFFBBF24)
                                else -> Color(0xFFF43F5E)
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0x330284C7),
                                border = BorderStroke(1.dp, Color(0x6638BDF8)),
                                modifier = Modifier
                                    .clickable { onOpenSlidingHitch() }
                                    .testTag("sliding_hitch_badge")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CompareArrows,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "${String.format("%+.1f", hitchShiftCm)}cm",
                                        color = Color(0xFFE2E8F0),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "IMP: ${String.format("%+.1f", hitchCteCm)}cm",
                                        color = hitchColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Center Cluster: Modern Precision Deviation Hero Readout
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        val directionSymbol = when {
                            cteInches < -0.2 -> "◀ "
                            cteInches > 0.2 -> "▶ "
                            else -> "◆ "
                        }
                        val directionLabel = when {
                            cteInches < -0.2 -> "LEFT"
                            cteInches > 0.2 -> "RIGHT"
                            else -> "ON TRACK"
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(statusColor.copy(alpha = 0.12f))
                                .border(1.dp, statusColor.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "$directionSymbol${String.format("%.1f", absCteInches)} in",
                                color = statusColor,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.testTag("cte_display")
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = directionLabel,
                                color = statusColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp
                            )
                        }
                    }

                    // Right Cluster: Speed, Steer Angle & Coverage Area
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Steer Angle chip
                        Column(horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Navigation,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = String.format("%+.1f°", vehicleState.actualSteerAngleDeg),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "tgt ${String.format("%+.1f°", vehicleState.targetSteerAngleDeg)}",
                                color = Color(0xFF34D399),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Speed Pill
                        val speedMph = vehicleState.speedKmh * 0.621371
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0x2238BDF8),
                            border = BorderStroke(1.dp, Color(0x4438BDF8))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${String.format("%.1f", speedMph)} mph",
                                    color = Color(0xFFF1F5F9),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Worked Area Pill
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0x22F59E0B),
                            border = BorderStroke(1.dp, Color(0x44F59E0B))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Terrain,
                                    contentDescription = null,
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${String.format("%.1f", workedAcres)} ac",
                                    color = Color(0xFFFBBF24),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // High-Precision Aero LED Lightbar Ribbon
                AeroLedGuidanceBar(cteCm = cteCm)

                // U-Turn Prompt Banner (Integrated with smooth rounded design)
                if (uTurnPrompt != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val isLift = uTurnPrompt.contains("LIFT", ignoreCase = true)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .testTag("uturn_operator_prompt"),
                        color = if (isLift) Color(0xDDF97316) else Color(0xDD059669)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isLift) Icons.Default.Warning else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = uTurnPrompt,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Aero LED Guidance Bar with smooth curved capsules and luminous glow.
 */
@Composable
private fun AeroLedGuidanceBar(cteCm: Double) {
    val segmentsPerSide = 14
    val maxDisplayCm = 28.0
    val step = maxDisplayCm / segmentsPerSide

    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0x55030712))
    ) {
        val totalWidth = size.width
        val barHeight = size.height
        val centerWidth = 16f
        val remainingWidth = totalWidth - centerWidth
        val segWidth = ((remainingWidth / (segmentsPerSide * 2f)) - 2f).coerceAtLeast(1f)
        val segHeight = barHeight - 2f

        // Left segments (from outer towards center)
        for (i in segmentsPerSide downTo 1) {
            val segThreshold = i * step
            val isLit = cteCm < -0.4 && abs(cteCm) >= (segThreshold - step)
            val segColor = when {
                i <= 3 -> Color(0xFF00F59B) // Neon Emerald
                i <= 7 -> Color(0xFFFBBF24) // Amber Gold
                else -> Color(0xFFF43F5E)   // Coral Red
            }
            val slotIndex = segmentsPerSide - i
            val x = slotIndex * (segWidth + 2f) + 1f
            drawRoundRect(
                color = if (isLit) segColor else Color(0x33334155),
                topLeft = androidx.compose.ui.geometry.Offset(x, 1f),
                size = androidx.compose.ui.geometry.Size(segWidth, segHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
            )
        }

        // Center Zero Target Marker
        val isCenterLit = abs(cteCm) < 1.5
        val centerX = (segmentsPerSide * (segWidth + 2f)) + 1f
        drawRoundRect(
            color = if (isCenterLit) Color(0xFF00F59B) else Color(0x66475569),
            topLeft = androidx.compose.ui.geometry.Offset(centerX, 1f),
            size = androidx.compose.ui.geometry.Size(centerWidth - 2f, segHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
        )

        // Right segments (from center towards outer)
        val rightStartX = centerX + centerWidth
        for (i in 1..segmentsPerSide) {
            val segThreshold = i * step
            val isLit = cteCm > 0.4 && cteCm >= (segThreshold - step)
            val segColor = when {
                i <= 3 -> Color(0xFF00F59B) // Neon Emerald
                i <= 7 -> Color(0xFFFBBF24) // Amber Gold
                else -> Color(0xFFF43F5E)   // Coral Red
            }
            val slotIndex = i - 1
            val x = rightStartX + slotIndex * (segWidth + 2f)
            drawRoundRect(
                color = if (isLit) segColor else Color(0x33334155),
                topLeft = androidx.compose.ui.geometry.Offset(x, 1f),
                size = androidx.compose.ui.geometry.Size(segWidth, segHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
            )
        }
    }
}

/**
 * Modern Sleek RTK Fix Badge with luminous beacon dot.
 */
@Composable
private fun RtkBadge(
    fixQuality: FixQuality,
    sats: Int,
    age: Double
) {
    val (beaconColor, badgeBg, textColor) = when (fixQuality) {
        FixQuality.RTK_FIX -> Triple(Color(0xFF00F59B), Color(0x2210B981), Color(0xFFA7F3D0))
        FixQuality.RTK_FLOAT -> Triple(Color(0xFFFBBF24), Color(0x22F59E0B), Color(0xFFFDE68A))
        FixQuality.DGPS, FixQuality.GPS -> Triple(Color(0xFF38BDF8), Color(0x220284C7), Color(0xFFBAE6FD))
        FixQuality.TABLET_INTERNAL -> Triple(Color(0xFF2DD4BF), Color(0x220D9488), Color(0xFF99F6E4))
        FixQuality.SIMULATOR -> Triple(Color(0xFFA855F7), Color(0x229333EA), Color(0xFFE9D5FF))
        FixQuality.INVALID -> Triple(Color(0xFFF43F5E), Color(0x22E11D48), Color(0xFFFECDD3))
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = badgeBg,
        border = BorderStroke(1.dp, beaconColor.copy(alpha = 0.35f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
        ) {
            // Luminous Beacon Indicator
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(beaconColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${fixQuality.label} • $sats sats",
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

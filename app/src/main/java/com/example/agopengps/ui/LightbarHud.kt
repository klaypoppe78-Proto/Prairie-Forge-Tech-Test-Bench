package com.example.agopengps.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agopengps.GnssSourceMode
import com.example.agopengps.LiveTelemetryState
import com.example.agopengps.navigation.DisplayThemeMode
import com.example.agopengps.navigation.FixQuality
import com.example.agopengps.navigation.SlidingHitchConfig
import com.example.agopengps.navigation.SlidingHitchState
import com.example.agopengps.navigation.VehicleState
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import kotlin.math.abs

/**
 * Scoped wrapper that collects 20 Hz [LiveTelemetryState] locally so top-level
 * cab controls and dialogs never recompose at 20 Hz.
 */
@Composable
fun ScopedLightbarHud(
    telemetryFlow: StateFlow<LiveTelemetryState>,
    isNightMode: Boolean,
    themeMode: DisplayThemeMode = DisplayThemeMode.STANDARD_AG,
    gnssSource: GnssSourceMode = GnssSourceMode.SIMULATOR,
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
    val telemetry by telemetryFlow.collectAsStateWithLifecycle()
    LightbarHud(
        vehicleState = telemetry.vehicleState,
        workedAcres = telemetry.workedAcres,
        isNightMode = isNightMode,
        themeMode = themeMode,
        gnssSource = gnssSource,
        slidingHitchState = telemetry.slidingHitchState,
        slidingHitchConfig = slidingHitchConfig,
        uTurnPrompt = uTurnPrompt,
        onToggleSimulation = onToggleSimulation,
        onOpenSettings = onOpenSettings,
        onOpenSlidingHitch = onOpenSlidingHitch,
        onToggleDashboard = onToggleDashboard,
        onOpenNmeaDiagnostics = onOpenNmeaDiagnostics,
        onRecenterGps = onRecenterGps,
        modifier = modifier
    )
}

/**
 * AgSteer Cab-Ready Top Telemetry HUD & Segmented LED Lightbar.
 * High-contrast peripheral-vision design tuned for rough ground and full sun.
 */
@Composable
fun LightbarHud(
    vehicleState: VehicleState,
    workedAcres: Double,
    isNightMode: Boolean,
    themeMode: DisplayThemeMode = DisplayThemeMode.STANDARD_AG,
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
    val isDaylight = themeMode == DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST
    val isOledNight = themeMode == DisplayThemeMode.CAB_OLED_NIGHT

    val cteCm = vehicleState.crossTrackErrorCm
    val cteInches = vehicleState.crossTrackErrorMeters * 39.3701
    val absCteInches = abs(cteInches)

    val statusColor = when {
        isOledNight -> when {
            absCteInches < 1.2 -> Color(0xFFFF9100)
            absCteInches < 4.0 -> Color(0xFFFF5722)
            else -> Color(0xFFD50000)
        }
        isDaylight -> when {
            absCteInches < 1.2 -> Color(0xFF047857) // Deep Emerald Ink
            absCteInches < 4.0 -> Color(0xFFB45309) // Deep Amber Ink
            else -> Color(0xFFBE123C)               // Deep Crimson Ink
        }
        else -> when {
            absCteInches < 1.2 -> Color(0xFF00F59B) // Luminous Emerald
            absCteInches < 4.0 -> Color(0xFFFBBF24) // Warm Amber Gold
            else -> Color(0xFFF43F5E)               // Coral Rose
        }
    }

    val statusFillColor = when {
        isDaylight -> when {
            absCteInches < 1.2 -> Color(0xFFD1FAE5)
            absCteInches < 4.0 -> Color(0xFFFEF3C7)
            else -> Color(0xFFFFE4E6)
        }
        else -> statusColor.copy(alpha = 0.14f)
    }

    val glassBackground = when (themeMode) {
        DisplayThemeMode.CAB_OLED_NIGHT -> Color(0xF5000000)
        DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST -> Color(0xF7F8FAFC)
        DisplayThemeMode.STANDARD_AG -> if (isNightMode) Color(0xEE0A0F1D) else Color(0xEE0F172A)
    }
    val glassBorder = when (themeMode) {
        DisplayThemeMode.CAB_OLED_NIGHT -> Color(0x55FFB300)
        DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST -> Color(0xFF334155)
        DisplayThemeMode.STANDARD_AG -> Color(0x4494A3B8)
    }
    val primaryTextColor = when {
        isOledNight -> Color(0xFFFFE0B2)
        isDaylight -> Color(0xFF0F172A)
        else -> Color(0xFFF8FAFC)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("lightbar_hud")
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = glassBackground,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(if (isDaylight) 1.5.dp else 1.dp, glassBorder),
            shadowElevation = if (isOledNight) 0.dp else 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                // Top Telemetry Pod: RTK Fix | Cross-Track Error | Steer Actual/Target | Speed | Area
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left Cluster: RTK Badge, Source Pill, Coordinates & Hitch
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RtkBadge(
                            fixQuality = vehicleState.fixQuality,
                            sats = vehicleState.satellites,
                            age = vehicleState.ageOfCorrectionSec,
                            themeMode = themeMode
                        )

                        val (modeBg, modeTextColor, modeLabel, modeIcon) = when {
                            isOledNight -> when (gnssSource) {
                                GnssSourceMode.SIMULATOR -> Quad(Color(0x33FF9100), Color(0xFFFFB300), "SIM ON", Icons.Default.SmartToy)
                                GnssSourceMode.ANDROID_GPS -> Quad(Color(0x33FF9100), Color(0xFFFF9100), "TABLET GPS", Icons.Default.TabletAndroid)
                                GnssSourceMode.USB_AIO_TEENSY -> Quad(Color(0x33FF9100), Color(0xFFFF9100), "USB AIO", Icons.Default.Usb)
                                GnssSourceMode.UDP_NETWORK -> Quad(Color(0x33FF9100), Color(0xFFFFB300), "UDP AgIO", Icons.Default.Wifi)
                            }
                            isDaylight -> when (gnssSource) {
                                GnssSourceMode.SIMULATOR -> Quad(Color(0xFFFEF3C7), Color(0xFF92400E), "SIM ON", Icons.Default.SmartToy)
                                GnssSourceMode.ANDROID_GPS -> Quad(Color(0xFFE0F2FE), Color(0xFF075985), "TABLET GPS", Icons.Default.TabletAndroid)
                                GnssSourceMode.USB_AIO_TEENSY -> Quad(Color(0xFFD1FAE5), Color(0xFF065F46), "USB AIO", Icons.Default.Usb)
                                GnssSourceMode.UDP_NETWORK -> Quad(Color(0xFFE0E7FF), Color(0xFF3730A3), "UDP AgIO", Icons.Default.Wifi)
                            }
                            else -> when (gnssSource) {
                                GnssSourceMode.SIMULATOR -> Quad(Color(0x33F59E0B), Color(0xFFFBBF24), "SIM ON", Icons.Default.SmartToy)
                                GnssSourceMode.ANDROID_GPS -> Quad(Color(0x3338BDF8), Color(0xFF38BDF8), "TABLET GPS", Icons.Default.TabletAndroid)
                                GnssSourceMode.USB_AIO_TEENSY -> Quad(Color(0x3310B981), Color(0xFF34D399), "USB AIO", Icons.Default.Usb)
                                GnssSourceMode.UDP_NETWORK -> Quad(Color(0x33818CF8), Color(0xFFA5B4FC), "UDP AgIO", Icons.Default.Wifi)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = modeBg,
                            border = BorderStroke(if (isDaylight) 1.2.dp else 1.dp, modeTextColor.copy(alpha = if (isDaylight) 0.55f else 0.4f)),
                            modifier = Modifier
                                .heightIn(min = 32.dp)
                                .clickable { onToggleSimulation() }
                                .testTag("sim_mode_badge")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = modeIcon,
                                    contentDescription = modeLabel,
                                    tint = modeTextColor,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = modeLabel,
                                    color = modeTextColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.4.sp
                                )
                            }
                        }

                        // Coordinates & One-Tap Recenter GPS Pill
                        val coordBg = when {
                            isOledNight -> Color(0x33FF9100)
                            isDaylight -> Color(0xFFE2E8F0)
                            else -> Color(0x2238BDF8)
                        }
                        val coordBorder = when {
                            isOledNight -> Color(0x66FF9100)
                            isDaylight -> Color(0xFF64748B)
                            else -> Color(0x4438BDF8)
                        }
                        val coordTint = when {
                            isOledNight -> Color(0xFFFF9100)
                            isDaylight -> Color(0xFF0369A1)
                            else -> Color(0xFF38BDF8)
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = coordBg,
                            border = BorderStroke(1.dp, coordBorder),
                            modifier = Modifier
                                .heightIn(min = 32.dp)
                                .clickable { onRecenterGps() }
                                .testTag("top_recenter_gps_badge")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MyLocation,
                                    contentDescription = "Center on GPS",
                                    tint = coordTint,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = String.format(Locale.US, "%.4f°, %.4f°", vehicleState.geoPosition.latitude, vehicleState.geoPosition.longitude),
                                    color = primaryTextColor,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Sliding Hitch Pill (if active)
                        if (slidingHitchConfig?.isEnabled == true) {
                            val hitchCteCm = slidingHitchState?.implementCrossTrackErrorCm ?: 0.0
                            val hitchShiftCm = slidingHitchState?.currentHitchShiftCm ?: 0.0
                            val hitchColor = when {
                                abs(hitchCteCm) < 1.0 -> if (isDaylight) Color(0xFF047857) else Color(0xFF00F59B)
                                abs(hitchCteCm) < 3.0 -> if (isDaylight) Color(0xFFB45309) else Color(0xFFFBBF24)
                                else -> if (isDaylight) Color(0xFFBE123C) else Color(0xFFF43F5E)
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isDaylight) Color(0xFFE0F2FE) else Color(0x330284C7),
                                border = BorderStroke(1.dp, if (isDaylight) Color(0xFF0284C7) else Color(0x6638BDF8)),
                                modifier = Modifier
                                    .heightIn(min = 32.dp)
                                    .clickable { onOpenSlidingHitch() }
                                    .testTag("sliding_hitch_badge")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.CompareArrows,
                                        contentDescription = "Sliding Hitch",
                                        tint = if (isDaylight) Color(0xFF0369A1) else Color(0xFF38BDF8),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "${String.format(Locale.US, "%+.1f", hitchShiftCm)}cm",
                                        color = primaryTextColor,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "IMP:${String.format(Locale.US, "%+.1f", hitchCteCm)}",
                                        color = hitchColor,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }

                    // Center Cluster: High-Contrast Precision Deviation Hero Readout
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
                                .background(statusFillColor)
                                .border(1.5.dp, statusColor.copy(alpha = if (isDaylight) 0.75f else 0.45f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "$directionSymbol${String.format(Locale.US, "%.1f", absCteInches)} in",
                                color = statusColor,
                                fontSize = 19.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.testTag("cte_display")
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = directionLabel,
                                color = statusColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.7.sp
                            )
                        }
                    }

                    // Right Cluster: Peripheral-Vision Steer Angle (Actual vs Target), Speed & Area
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Upgraded High-Contrast Actual vs. Target Steering Angle Readout
                        val steerDiffDeg = abs(vehicleState.actualSteerAngleDeg - vehicleState.targetSteerAngleDeg)
                        val tgtAccent = when {
                            isOledNight -> Color(0xFFFFB300)
                            isDaylight -> if (steerDiffDeg > 2.5) Color(0xFFB45309) else Color(0xFF047857)
                            else -> if (steerDiffDeg > 2.5) Color(0xFFFBBF24) else Color(0xFF00F59B)
                        }
                        val steerPillBg = when {
                            isOledNight -> Color(0x28FF9100)
                            isDaylight -> Color(0xFFFFFFFF)
                            else -> Color(0xFF152238)
                        }
                        val steerPillBorder = when {
                            isOledNight -> Color(0x77FF9100)
                            isDaylight -> Color(0xFF334155)
                            else -> tgtAccent.copy(alpha = 0.55f)
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = steerPillBg,
                            border = BorderStroke(1.5.dp, steerPillBorder),
                            modifier = Modifier.testTag("steer_angle_readout")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Navigation,
                                    contentDescription = "Wheel Steer Angle",
                                    tint = tgtAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = String.format(Locale.US, "%+.1f°", vehicleState.actualSteerAngleDeg),
                                    color = primaryTextColor,
                                    fontSize = 15.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = tgtAccent.copy(alpha = if (isDaylight) 0.16f else 0.22f),
                                    border = BorderStroke(1.dp, tgtAccent.copy(alpha = if (isDaylight) 0.65f else 0.5f))
                                ) {
                                    Text(
                                        text = "TGT ${String.format(Locale.US, "%+.1f°", vehicleState.targetSteerAngleDeg)}",
                                        color = tgtAccent,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        // Speed Pill
                        val speedMph = vehicleState.speedKmh * 0.621371
                        val speedBg = when {
                            isOledNight -> Color(0x33FF9100)
                            isDaylight -> Color(0xFFE0F2FE)
                            else -> Color(0x2838BDF8)
                        }
                        val speedBorder = when {
                            isOledNight -> Color(0x66FF9100)
                            isDaylight -> Color(0xFF0369A1)
                            else -> Color(0x5538BDF8)
                        }
                        val speedTint = when {
                            isOledNight -> Color(0xFFFF9100)
                            isDaylight -> Color(0xFF0369A1)
                            else -> Color(0xFF38BDF8)
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = speedBg,
                            border = BorderStroke(1.dp, speedBorder)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = "Speed",
                                    tint = speedTint,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", speedMph)} mph",
                                    color = primaryTextColor,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        // Worked Area Pill
                        val areaBg = when {
                            isOledNight -> Color(0x28FF9100)
                            isDaylight -> Color(0xFFFEF3C7)
                            else -> Color(0x26F59E0B)
                        }
                        val areaBorder = when {
                            isOledNight -> Color(0x66FF9100)
                            isDaylight -> Color(0xFFB45309)
                            else -> Color(0x55F59E0B)
                        }
                        val areaText = when {
                            isOledNight -> Color(0xFFFFB300)
                            isDaylight -> Color(0xFF92400E)
                            else -> Color(0xFFFBBF24)
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = areaBg,
                            border = BorderStroke(1.dp, areaBorder)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Terrain,
                                    contentDescription = "Worked Area",
                                    tint = areaText,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", workedAcres)} ac",
                                    color = areaText,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(7.dp))

                // Bold, High-Visibility Segmented LED Lightbar
                AeroLedGuidanceBar(cteCm = cteCm, themeMode = themeMode)

                // U-Turn Prompt Banner
                if (uTurnPrompt != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val isLift = uTurnPrompt.contains("LIFT", ignoreCase = true)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .testTag("uturn_operator_prompt"),
                        color = if (isLift) Color(0xE8EA580C) else Color(0xE8047857)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isLift) Icons.Default.Warning else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = uTurnPrompt,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Bold, High-Visibility Segmented LED-Style Cab Lightbar.
 * - Dark recessed optical bezel ensures maximum daylight contrast on all themes.
 * - Progressive scaling: tight 1.5cm resolution near center expanding smoothly to 36cm (~14 in).
 * - Zero per-frame allocations inside Canvas draw scope.
 */
@Composable
private fun AeroLedGuidanceBar(cteCm: Double, themeMode: DisplayThemeMode = DisplayThemeMode.STANDARD_AG) {
    val segmentsPerSide = 12
    val isOledNight = themeMode == DisplayThemeMode.CAB_OLED_NIGHT
    val isDaylight = themeMode == DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST

    val bezelBg = when {
        isOledNight -> Color(0xFF050200)
        isDaylight -> Color(0xFF0F172A)
        else -> Color(0xFF030712)
    }
    val bezelBorder = when {
        isOledNight -> Color(0x66FF9100)
        isDaylight -> Color(0xFF334155)
        else -> Color(0x4464748B)
    }
    val unlitSlotColor = when {
        isOledNight -> Color(0xFF1F1205)
        isDaylight -> Color(0xFF1E293B)
        else -> Color(0xFF172033)
    }

    val greenLed = if (isOledNight) Color(0xFFFFA000) else Color(0xFF00F59B)
    val amberLed = if (isOledNight) Color(0xFFFF5722) else Color(0xFFFBBF24)
    val redLed = if (isOledNight) Color(0xFFD50000) else Color(0xFFFF3B5C)
    val centerActiveColor = if (isOledNight) Color(0xFFFFB300) else Color(0xFF00FF9D)
    val centerIdleColor = Color(0xFF334155)
    val specularCap = Color(0x55FFFFFF)
    val tickColor = if (isOledNight) Color(0x66FFB300) else Color(0x6694A3B8)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(bezelBg)
            .border(1.dp, bezelBorder, RoundedCornerShape(7.dp))
            .testTag("segmented_led_lightbar")
    ) {
        val totalWidth = size.width
        val barHeight = size.height
        val padH = 6f
        val padTop = 3f
        val padBottom = 5f
        val segHeight = (barHeight - padTop - padBottom).coerceAtLeast(6f)
        val gap = 3.5f
        val centerWidth = 26f
        val sideWidth = ((totalWidth - padH * 2f - centerWidth - gap * 2f) * 0.5f).coerceAtLeast(24f)
        val segWidth = ((sideWidth - gap * (segmentsPerSide - 1)) / segmentsPerSide).coerceAtLeast(2f)
        val radius = CornerRadius(4f, 4f)

        val absCte = abs(cteCm)
        // Progressive threshold: segments 1..4 = 1.5 cm/LED (fine RTK), 5..8 = 3.0 cm/LED, 9..12 = 4.5 cm/LED (total 36 cm)
        fun thresholdForIndex(idx: Int): Double = when {
            idx <= 4 -> (idx - 1) * 1.5 + 0.6
            idx <= 8 -> 6.0 + (idx - 5) * 3.0
            else -> 18.0 + (idx - 9) * 4.5
        }

        // Left LED Bank (outermost idx=12 -> center idx=1)
        for (i in segmentsPerSide downTo 1) {
            val isLit = cteCm < -0.5 && absCte >= thresholdForIndex(i)
            val segColor = when {
                i <= 3 -> greenLed
                i <= 7 -> amberLed
                else -> redLed
            }
            val slotIndex = segmentsPerSide - i
            val x = padH + slotIndex * (segWidth + gap)
            drawRoundRect(
                color = if (isLit) segColor else unlitSlotColor,
                topLeft = Offset(x, padTop),
                size = Size(segWidth, segHeight),
                cornerRadius = radius
            )
            if (isLit) {
                // Crisp top highlight cap for physical LED lens depth
                drawRoundRect(
                    color = specularCap,
                    topLeft = Offset(x + 1.5f, padTop + 1f),
                    size = Size((segWidth - 3f).coerceAtLeast(1f), (segHeight * 0.28f).coerceAtLeast(2f)),
                    cornerRadius = CornerRadius(2f, 2f)
                )
            }
            // Subtle scale tick marks at key zone boundaries (indices 3, 7, 12)
            if (i == 3 || i == 7 || i == 12) {
                val tx = x + segWidth * 0.5f
                drawLine(tickColor, Offset(tx, barHeight - 3.5f), Offset(tx, barHeight - 1f), strokeWidth = 1.5f)
            }
        }

        // Center On-Track Bullseye Block
        val isCenterLit = absCte < 1.8
        val centerX = padH + sideWidth + gap
        drawRoundRect(
            color = if (isCenterLit) centerActiveColor else centerIdleColor,
            topLeft = Offset(centerX, padTop - 0.5f),
            size = Size(centerWidth, segHeight + 1f),
            cornerRadius = CornerRadius(5f, 5f)
        )
        // Center vertical crosshair notch
        val midX = centerX + centerWidth * 0.5f
        drawLine(
            color = if (isCenterLit) Color(0xCC022C22) else Color(0x88E2E8F0),
            start = Offset(midX, padTop + 2f),
            end = Offset(midX, padTop + segHeight - 2f),
            strokeWidth = 2f
        )

        // Right LED Bank (center idx=1 -> outermost idx=12)
        val rightStartX = centerX + centerWidth + gap
        for (i in 1..segmentsPerSide) {
            val isLit = cteCm > 0.5 && absCte >= thresholdForIndex(i)
            val segColor = when {
                i <= 3 -> greenLed
                i <= 7 -> amberLed
                else -> redLed
            }
            val slotIndex = i - 1
            val x = rightStartX + slotIndex * (segWidth + gap)
            drawRoundRect(
                color = if (isLit) segColor else unlitSlotColor,
                topLeft = Offset(x, padTop),
                size = Size(segWidth, segHeight),
                cornerRadius = radius
            )
            if (isLit) {
                drawRoundRect(
                    color = specularCap,
                    topLeft = Offset(x + 1.5f, padTop + 1f),
                    size = Size((segWidth - 3f).coerceAtLeast(1f), (segHeight * 0.28f).coerceAtLeast(2f)),
                    cornerRadius = CornerRadius(2f, 2f)
                )
            }
            if (i == 3 || i == 7 || i == 12) {
                val tx = x + segWidth * 0.5f
                drawLine(tickColor, Offset(tx, barHeight - 3.5f), Offset(tx, barHeight - 1f), strokeWidth = 1.5f)
            }
        }
    }
}

/**
 * RTK Fix Badge tuned for Emerald Dark, OLED Night, and High-Sun Daylight.
 */
@Composable
private fun RtkBadge(
    fixQuality: FixQuality,
    sats: Int,
    age: Double,
    themeMode: DisplayThemeMode = DisplayThemeMode.STANDARD_AG
) {
    val isOledNight = themeMode == DisplayThemeMode.CAB_OLED_NIGHT
    val isDaylight = themeMode == DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST

    val (beaconColor, badgeBg, textColor) = when {
        isOledNight -> when (fixQuality) {
            FixQuality.RTK_FIX -> Triple(Color(0xFFFF9100), Color(0x33FF9100), Color(0xFFFFE0B2))
            FixQuality.RTK_FLOAT -> Triple(Color(0xFFFFB300), Color(0x33FFB300), Color(0xFFFFF8E1))
            FixQuality.DGPS, FixQuality.GPS -> Triple(Color(0xFFFF9800), Color(0x33FF9800), Color(0xFFFFE0B2))
            FixQuality.TABLET_INTERNAL -> Triple(Color(0xFFFF8F00), Color(0x33FF8F00), Color(0xFFFFE0B2))
            FixQuality.SIMULATOR -> Triple(Color(0xFFFFB74D), Color(0x33FF9800), Color(0xFFFFF3E0))
            FixQuality.INVALID -> Triple(Color(0xFFD50000), Color(0x33D50000), Color(0xFFFFCDD2))
        }
        isDaylight -> when (fixQuality) {
            FixQuality.RTK_FIX -> Triple(Color(0xFF047857), Color(0xFFD1FAE5), Color(0xFF064E3B))
            FixQuality.RTK_FLOAT -> Triple(Color(0xFFB45309), Color(0xFFFEF3C7), Color(0xFF78350F))
            FixQuality.DGPS, FixQuality.GPS -> Triple(Color(0xFF0369A1), Color(0xFFE0F2FE), Color(0xFF0C4A6E))
            FixQuality.TABLET_INTERNAL -> Triple(Color(0xFF0F766E), Color(0xFFCCFBF1), Color(0xFF134E4A))
            FixQuality.SIMULATOR -> Triple(Color(0xFF6D28D9), Color(0xFFEDE9FE), Color(0xFF4C1D95))
            FixQuality.INVALID -> Triple(Color(0xFFBE123C), Color(0xFFFFE4E6), Color(0xFF881337))
        }
        else -> when (fixQuality) {
            FixQuality.RTK_FIX -> Triple(Color(0xFF00F59B), Color(0x2810B981), Color(0xFFA7F3D0))
            FixQuality.RTK_FLOAT -> Triple(Color(0xFFFBBF24), Color(0x28F59E0B), Color(0xFFFDE68A))
            FixQuality.DGPS, FixQuality.GPS -> Triple(Color(0xFF38BDF8), Color(0x280284C7), Color(0xFFBAE6FD))
            FixQuality.TABLET_INTERNAL -> Triple(Color(0xFF2DD4BF), Color(0x280D9488), Color(0xFF99F6E4))
            FixQuality.SIMULATOR -> Triple(Color(0xFFA855F7), Color(0x289333EA), Color(0xFFE9D5FF))
            FixQuality.INVALID -> Triple(Color(0xFFF43F5E), Color(0x28E11D48), Color(0xFFFECDD3))
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = badgeBg,
        border = BorderStroke(if (isDaylight) 1.2.dp else 1.dp, beaconColor.copy(alpha = if (isDaylight) 0.6f else 0.4f)),
        modifier = Modifier.heightIn(min = 32.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(beaconColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${fixQuality.label} • $sats sats",
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.3.sp
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

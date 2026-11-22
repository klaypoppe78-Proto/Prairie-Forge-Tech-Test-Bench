package com.example.agopengps.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agopengps.GnssSourceMode
import com.example.agopengps.navigation.*
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun GuidanceHud(
    isAutoSteerEngaged: Boolean,
    crossTrackErrorMeters: Double,
    actualSteerAngleDeg: Double,
    targetSteerAngleDeg: Double,
    speedKmh: Double,
    headingDeg: Double,
    workedAcres: Double,
    activeSwathIndex: Int,
    gnssSource: GnssSourceMode,
    geoPosition: GeoPoint,
    statusMessage: String,
    uTurnPrompt: String? = null,
    isNightMode: Boolean = true,
    themeMode: DisplayThemeMode = DisplayThemeMode.STANDARD_AG,
    slidingHitchState: SlidingHitchState = SlidingHitchState(),
    onToggleAutoSteer: () -> Unit = {},
    onZeroWas: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val speedMph = abs(speedKmh) * 0.621371
    val crossTrackInches = crossTrackErrorMeters * 39.3701
    val crossTrackCm = crossTrackErrorMeters * 100.0

    val hudBg = when (themeMode) {
        DisplayThemeMode.CAB_OLED_NIGHT -> Color(0xF2000000)
        DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST -> Color(0xF2F8FAFC)
        DisplayThemeMode.STANDARD_AG -> Color(0xEE0F172A)
    }
    val hudBorder = when (themeMode) {
        DisplayThemeMode.CAB_OLED_NIGHT -> Color(0x33FFB300)
        DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST -> Color(0x3364748B)
        DisplayThemeMode.STANDARD_AG -> Color(0x3394A3B8)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp, start = 12.dp, end = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // TOP PRIMARY INSTRUMENT CLUSTER
        Surface(
            color = hudBg,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, hudBorder),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. GNSS Fix Status Badge
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isAutoSteerEngaged) Color(0xFF00F59B) else Color(0xFF38BDF8))
                    )
                    Text(
                        text = if (gnssSource == GnssSourceMode.SIMULATOR) "SIMULATOR" else "RTK FIX (Dual UM982)",
                        color = Color(0xFF00F59B),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.5f", geoPosition.latitude)}°, ${String.format(Locale.US, "%.5f", geoPosition.longitude)}°",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // 2. Center Lateral Error Deviation Pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (abs(crossTrackInches) <= 1.0) Color(0x3300F59B) else if (abs(crossTrackInches) <= 3.0) Color(0x33FBBF24) else Color(0x33F43F5E),
                    border = BorderStroke(1.dp, if (abs(crossTrackInches) <= 1.0) Color(0xFF00F59B) else if (abs(crossTrackInches) <= 3.0) Color(0xFFFBBF24) else Color(0xFFF43F5E))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (crossTrackInches > 0) Icons.Default.ArrowForward else Icons.Default.ArrowBack,
                            contentDescription = null,
                            tint = if (abs(crossTrackInches) <= 1.0) Color(0xFF00F59B) else if (abs(crossTrackInches) <= 3.0) Color(0xFFFBBF24) else Color(0xFFF43F5E),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${String.format(Locale.US, "%+.1f", crossTrackInches)} in (${String.format(Locale.US, "%+.1f", crossTrackCm)} cm)",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // 3. Right Stats: Steer Angle, Speed, Worked Acres
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Steer Angle
                    Text(
                        text = "WAS: ${String.format(Locale.US, "%+.1f°", actualSteerAngleDeg)}",
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    // Ground Speed
                    Surface(
                        color = Color(0x2238BDF8),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(horizontal = 2.dp)
                    ) {
                        Text(
                            text = "${String.format(Locale.US, "%.1f", speedMph)} mph",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Worked Acres
                    Surface(
                        color = Color(0x2200F59B),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${String.format(Locale.US, "%.1f", workedAcres)} ac",
                            color = Color(0xFF00F59B),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // U-Turn Alert Banner (if active)
        AnimatedVisibility(
            visible = uTurnPrompt != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                color = Color(0xEED97706),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.5.dp, Color(0xFFFBBF24)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = uTurnPrompt ?: "",
                        color = Color.Black,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

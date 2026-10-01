package com.example.agopengps.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.agopengps.io.SecondGpsHardwareStats
import com.example.agopengps.navigation.*
import kotlin.math.abs

@Composable
fun SlidingHitchDialog(
    config: SlidingHitchConfig,
    state: SlidingHitchState,
    tractorXteCm: Double,
    secondGpsStats: SecondGpsHardwareStats,
    onSaveConfig: (SlidingHitchConfig) -> Unit,
    onCenterHitch: () -> Unit,
    onNudgeHitch: (Double) -> Unit,
    onToggleAutoHitch: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var isEnabled by remember(config) { mutableStateOf(config.isEnabled) }
    var selectedHitchType by remember(config) { mutableStateOf(config.hitchType) }
    var selectedSourceMode by remember(config) { mutableStateOf(config.sourceMode) }
    var udpPortText by remember(config) { mutableStateOf(config.udpPort.toString()) }
    var maxStrokeText by remember(config) { mutableStateOf(String.format("%.1f", config.maxStrokeCm)) }
    var deadbandText by remember(config) { mutableStateOf(String.format("%.1f", config.deadbandCm)) }
    var kpText by remember(config) { mutableStateOf(String.format("%.2f", config.proportionalGain)) }
    var kiText by remember(config) { mutableStateOf(String.format("%.2f", config.integralGain)) }
    var speedText by remember(config) { mutableStateOf(String.format("%.1f", config.hydraulicSpeedCmPerSec)) }
    var antennaAheadText by remember(config) { mutableStateOf(String.format("%.2f", config.antennaOffsetAheadMeters)) }
    var invertDirection by remember(config) { mutableStateOf(config.invertDirection) }
    var autoCenterOnDisengage by remember(config) { mutableStateOf(config.autoCenterOnDisengage) }
    var isAutoHitchEngaged by remember(config) { mutableStateOf(config.isAutoHitchEngaged) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, Color(0xFF00E5FF), RoundedCornerShape(16.dp)),
            color = Color(0xFF101920)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF00E5FF).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.CompareArrows,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "ACTIVE IMPLEMENT GUIDANCE & SLIDING HITCH",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Secondary GNSS Receiver & Hydraulic Side-Shift Lateral Edge Control",
                                color = Color(0xFF80DEEA),
                                fontSize = 12.sp
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    thickness = 1.dp,
                    color = Color(0xFF1F333F)
                )

                // Live Telemetry Banner
                Surface(
                    color = Color(0xFF16252F),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Implement XTE
                        Column(modifier = Modifier.weight(1f)) {
                            Text("IMPLEMENT XTE", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            val impXte = state.implementCrossTrackErrorCm
                            val impColor = when {
                                abs(impXte) < 1.0 -> Color(0xFF00E676)
                                abs(impXte) < 3.0 -> Color(0xFFFFD54F)
                                else -> Color(0xFFFF5252)
                            }
                            Text(
                                text = String.format("%+.1f cm", impXte),
                                color = impColor,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                if (abs(impXte) < 1.0) "DEAD-CENTER ON AB LINE" else if (impXte > 0) "RIGHT OF SWATH" else "LEFT OF SWATH",
                                color = impColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Tractor XTE comparison
                        Column(modifier = Modifier.weight(1f)) {
                            Text("TRACTOR XTE", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = String.format("%+.1f cm", tractorXteCm),
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                "Tractor Crab / Drift",
                                color = Color.LightGray,
                                fontSize = 10.sp
                            )
                        }

                        // Sliding Hitch Cylinder Extension
                        Column(modifier = Modifier.weight(1.3f)) {
                            Text("SLIDING CYLINDER EXTENSION", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = String.format("%+.1f cm / ±%.0f cm", state.currentHitchShiftCm, config.maxStrokeCm),
                                color = Color(0xFF00E5FF),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            // Stroke position visualizer bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color(0xFF0C161C))
                            ) {
                                // Center line
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .width(2.dp)
                                        .fillMaxHeight()
                                        .background(Color.White)
                                )
                                // Active carriage indicator
                                val fraction = (state.currentHitchShiftCm / config.maxStrokeCm).coerceIn(-1.0, 1.0).toFloat()
                                val alignmentBias = fraction
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .offset(x = (fraction * 45).dp)
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(if (state.isStrokeLimitReached) Color(0xFFFF5252) else Color(0xFF00E5FF))
                                )
                            }
                        }

                        // 2nd GNSS Fix Quality
                        Column(modifier = Modifier.weight(1.1f), horizontalAlignment = Alignment.End) {
                            Surface(
                                color = Color(0xFF00E676).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676))
                            ) {
                                Text(
                                    "2ND GNSS: ${state.secondGpsFixQuality.name}",
                                    color = Color(0xFF00E676),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "${state.secondGpsSatellites} Sats | HDOP ${String.format("%.1f", state.secondGpsHdop)}",
                                color = Color(0xFF80DEEA),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Quick Action & Manual Jog Row
                    Surface(
                        color = Color(0xFF18232C),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = isAutoHitchEngaged,
                                        onCheckedChange = {
                                            isAutoHitchEngaged = it
                                            onToggleAutoHitch(it)
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color(0xFF00E5FF),
                                            checkedTrackColor = Color(0xFF006064)
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        if (isAutoHitchEngaged) "AUTO-HITCH STEER: ACTIVE" else "AUTO-HITCH STEER: PAUSED / MANUAL",
                                        color = if (isAutoHitchEngaged) Color(0xFF00E5FF) else Color.Gray,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { onNudgeHitch(-1.0) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text("< NUDGE LEFT 1 cm", color = Color(0xFF00E5FF), fontSize = 11.sp)
                                    }
                                    Button(
                                        onClick = onCenterHitch,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00838F)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.CenterFocusStrong, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("1-TAP CENTER", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = { onNudgeHitch(1.0) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text("NUDGE RIGHT 1 cm >", color = Color(0xFF00E5FF), fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Section: Secondary GPS Hardware Module Source
                    Text("SECOND GPS CONTROLLER MODULE CONFIGURATION", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SecondGpsSourceMode.values().forEach { mode ->
                            val selected = (selectedSourceMode == mode)
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedSourceMode = mode },
                                color = if (selected) Color(0xFF004D40) else Color(0xFF1A2630),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (selected) Color(0xFF00E676) else Color(0xFF2C3E50)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        when (mode) {
                                            SecondGpsSourceMode.UDP_PORT_SECONDARY -> Icons.Default.Wifi
                                            SecondGpsSourceMode.USB_SERIAL_SECONDARY -> Icons.Default.Usb
                                            SecondGpsSourceMode.UM982_DUAL_ANTENNA_SLAVE -> Icons.Default.Satellite
                                            SecondGpsSourceMode.SIMULATOR_IMPLEMENT -> Icons.Default.PlayArrow
                                        },
                                        contentDescription = null,
                                        tint = if (selected) Color(0xFF00E676) else Color.Gray,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        mode.displayName,
                                        color = if (selected) Color.White else Color.Gray,
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    // Source Details (Port, Antenna Offsets)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = udpPortText,
                            onValueChange = { udpPortText = it },
                            label = { Text("Secondary UDP Port", color = Color.Gray, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )
                        OutlinedTextField(
                            value = antennaAheadText,
                            onValueChange = { antennaAheadText = it },
                            label = { Text("Antenna Ahead/Behind (m)", color = Color.Gray, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )
                        OutlinedTextField(
                            value = maxStrokeText,
                            onValueChange = { maxStrokeText = it },
                            label = { Text("Max Cylinder Stroke (±cm)", color = Color.Gray, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )
                    }

                    // Section: Mechanical Hitch Hardware Type
                    Text("MECHANICAL SLIDING HITCH TYPE", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SlidingHitchType.values().forEach { hitch ->
                            val selected = (selectedHitchType == hitch)
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedHitchType = hitch },
                                color = if (selected) Color(0xFF006064) else Color(0xFF1A2630),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (selected) Color(0xFF00E5FF) else Color(0xFF2C3E50)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = { selectedHitchType = hitch },
                                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF00E5FF))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(hitch.displayName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text("Typical Stroke: ±${hitch.typicalStrokeCm.toInt()} cm", color = Color.LightGray, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Section: Proportional Hydraulic Tuning & Deadband
                    Text("HYDRAULIC VALVE & CONTROLLER TUNING", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = kpText,
                            onValueChange = { kpText = it },
                            label = { Text("Proportional Gain (Kp)", color = Color.Gray, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )
                        OutlinedTextField(
                            value = kiText,
                            onValueChange = { kiText = it },
                            label = { Text("Integral Gain (Ki)", color = Color.Gray, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )
                        OutlinedTextField(
                            value = deadbandText,
                            onValueChange = { deadbandText = it },
                            label = { Text("Deadband (cm)", color = Color.Gray, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )
                        OutlinedTextField(
                            value = speedText,
                            onValueChange = { speedText = it },
                            label = { Text("Cylinder Speed (cm/s)", color = Color.Gray, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )
                    }

                    // Toggles: Invert Direction & Auto-Center
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Checkbox(
                                checked = invertDirection,
                                onCheckedChange = { invertDirection = it },
                                colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00E5FF))
                            )
                            Text("Invert Hydraulic Direction (Reversed Hoses)", color = Color.White, fontSize = 12.sp)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Checkbox(
                                checked = autoCenterOnDisengage,
                                onCheckedChange = { autoCenterOnDisengage = it },
                                colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00E5FF))
                            )
                            Text("Auto-Center Hitch On Disengage", color = Color.White, fontSize = 12.sp)
                        }
                    }

                    // NMEA Diagnostics Snippet
                    if (secondGpsStats.lastSentence.isNotBlank()) {
                        Surface(
                            color = Color(0xFF0A1218),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("LATEST RAW SECONDARY GNSS SENTENCE:", color = Color.Gray, fontSize = 10.sp)
                                Text(
                                    secondGpsStats.lastSentence,
                                    color = Color(0xFF00E676),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("CANCEL", color = Color.White)
                    }

                    Button(
                        onClick = {
                            val parsedPort = udpPortText.toIntOrNull() ?: config.udpPort
                            val parsedStroke = maxStrokeText.toDoubleOrNull() ?: config.maxStrokeCm
                            val parsedDeadband = deadbandText.toDoubleOrNull() ?: config.deadbandCm
                            val parsedKp = kpText.toDoubleOrNull() ?: config.proportionalGain
                            val parsedKi = kiText.toDoubleOrNull() ?: config.integralGain
                            val parsedSpeed = speedText.toDoubleOrNull() ?: config.hydraulicSpeedCmPerSec
                            val parsedAhead = antennaAheadText.toDoubleOrNull() ?: config.antennaOffsetAheadMeters

                            val updated = config.copy(
                                isEnabled = isEnabled,
                                hitchType = selectedHitchType,
                                sourceMode = selectedSourceMode,
                                udpPort = parsedPort,
                                maxStrokeCm = parsedStroke,
                                deadbandCm = parsedDeadband,
                                proportionalGain = parsedKp,
                                integralGain = parsedKi,
                                hydraulicSpeedCmPerSec = parsedSpeed,
                                antennaOffsetAheadMeters = parsedAhead,
                                invertDirection = invertDirection,
                                autoCenterOnDisengage = autoCenterOnDisengage,
                                isAutoHitchEngaged = isAutoHitchEngaged
                            )
                            onSaveConfig(updated)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("APPLY & ACTIVATE SLIDING HITCH", color = Color.Black, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

package com.example.agopengps.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.agopengps.io.Um982ConfigPresets
import com.example.agopengps.navigation.VehicleState

/**
 * Diagnostic & Calibration Wizard for the Unicore UM982 Dual-Antenna RTK Receiver.
 * Displays live dual-antenna RTK heading, baseline separation distance, pitch/roll,
 * and allows 1-tap transmission of UM982 configuration sentences ($KSXT, 10Hz/20Hz modes, SAVECONFIG).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Um982DiagnosticsDialog(
    vehicleState: VehicleState,
    lastSentence: String,
    onSendCommand: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var customCommandText by remember { mutableStateOf("") }
    var commandSentFeedback by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101915)),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(8.dp)
                .testTag("um982_diagnostics_dialog")
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SatelliteAlt, contentDescription = null, tint = Color(0xFF00E676))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Unicore UM982 Dual-Antenna RTK Hub",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 17.sp
                            )
                            Text(
                                text = "Dual Heading Baseline & KSXT High-Speed Engine",
                                color = Color(0xFF81C784),
                                fontSize = 11.sp
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Telemetry summary grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // True Dual Heading
                    Surface(
                        color = Color(0xFF18241F),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("DUAL TRUE HEADING", color = Color(0xFF90A4AE), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${String.format("%.1f", vehicleState.headingDeg)}°",
                                color = Color(0xFF00E676),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                            Text("Dual Carrier Phase Fixed", color = Color(0xFF81C784), fontSize = 10.sp)
                        }
                    }

                    // Antenna Baseline Distance
                    Surface(
                        color = Color(0xFF18241F),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("ANTENNA BASELINE", color = Color(0xFF90A4AE), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            val baselineInches = vehicleState.baselineLengthMeters * 39.3701
                            Text(
                                text = "${String.format("%.2f", vehicleState.baselineLengthMeters)} m",
                                color = Color(0xFF80D8FF),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                            Text("${String.format("%.1f", baselineInches)} in (Ant1-Ant2)", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                        }
                    }

                    // Cab Roll & Pitch
                    Surface(
                        color = Color(0xFF18241F),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("ROLL & PITCH", color = Color(0xFF90A4AE), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${String.format("%+.1f", vehicleState.rollDeg)}° / ${String.format("%+.1f", vehicleState.pitchDeg)}°",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text("Real-Time Terrain Tilt", color = Color(0xFFB0BEC5), fontSize = 10.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Raw Sentence Monitor Card
                Surface(
                    color = Color(0xFF0A100E),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF263238)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "LATEST INCOMING SENTENCE (\$KSXT / NMEA):",
                            color = Color(0xFF81C784),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (lastSentence.isNotEmpty()) lastSentence else "\$KSXT,20260919183015.00,-95.715300,44.914100,180.20,135.40,0.80,1.25,4*4A",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 2
                        )
                    }
                }

                if (commandSentFeedback != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Sent: $commandSentFeedback",
                        color = Color(0xFF00E676),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "1-TAP CONFIGURATION PRESETS FOR TEENSY AIO & UM982:",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Presets List
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(Um982ConfigPresets.COMMAND_PRESETS) { preset ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2621)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(preset.label, color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(preset.description, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                                    Text(
                                        text = preset.commandString.trim(),
                                        color = Color(0xFF80D8FF),
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Button(
                                    onClick = {
                                        onSendCommand(preset.commandString)
                                        commandSentFeedback = preset.label
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("SEND", fontWeight = FontWeight.Black, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // Custom Command Transmitter
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2824)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Transmit Custom Unicore Binary / ASCII Command:", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = customCommandText,
                                        onValueChange = { customCommandText = it },
                                        placeholder = { Text("e.g. CONFIG HEADING ANT1 ANT2", fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )

                                    Button(
                                        onClick = {
                                            if (customCommandText.isNotBlank()) {
                                                val cmd = if (customCommandText.endsWith("\r\n")) customCommandText else "$customCommandText\r\n"
                                                onSendCommand(cmd)
                                                commandSentFeedback = customCommandText
                                                customCommandText = ""
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                                    ) {
                                        Text("EXEC", color = Color.Black, fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F))) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

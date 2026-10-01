package com.example.agopengps.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.agopengps.io.SteerHardwareStatus
import com.example.agopengps.navigation.SteerValveConfig
import com.example.agopengps.navigation.SteerValveType
import kotlin.math.roundToInt

/**
 * Visual Zero-Calibration & Live PID / PWM oscilloscope wizard for the Wheel Angle Sensor (WAS)
 * and Danfoss PVEA / PWM proportional hydraulic valve.
 *
 * Lets you verify steering response, WAS linearity, center zero offset, and Cytron/Danfoss
 * PWM drive signals on the tablet bench before connecting high-pressure hydraulics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WasCalibrationDialog(
    hardwareStatus: SteerHardwareStatus,
    targetSteerAngleDeg: Double,
    steerValveConfig: SteerValveConfig,
    onUpdateSteerValveConfig: (SteerValveConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var countsPerDegree by remember { mutableIntStateOf(steerValveConfig.wasCountsPerDeg) }
    var wasZeroOffsetDeg by remember { mutableDoubleStateOf(steerValveConfig.wasZeroOffsetDeg) }
    var isWasInverted by remember { mutableStateOf(steerValveConfig.isWasInverted) }
    var isMotorInverted by remember { mutableStateOf(steerValveConfig.isMotorInverted) }
    var minPwm by remember { mutableIntStateOf(steerValveConfig.minPwmDeadband) }
    var maxPwm by remember { mutableIntStateOf(steerValveConfig.maxPwmLimit) }

    // Rolling history for live PID / PWM oscilloscope
    val targetHistory = remember { mutableStateListOf<Float>() }
    val actualHistory = remember { mutableStateListOf<Float>() }

    LaunchedEffect(targetSteerAngleDeg, hardwareStatus.actualSteerAngleDeg) {
        targetHistory.add(targetSteerAngleDeg.toFloat())
        if (targetHistory.size > 80) targetHistory.removeAt(0)

        actualHistory.add(hardwareStatus.actualSteerAngleDeg.toFloat())
        if (actualHistory.size > 80) actualHistory.removeAt(0)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF121B17)),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(8.dp)
                .testTag("was_calibration_dialog")
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = Color(0xFF00E676))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Wheel Angle Sensor & Valve Calibration",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 17.sp
                            )
                            Text(
                                text = "Live Bench Oscilloscope & Zero-Point Alignment",
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

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Live Oscilloscope Graph: Target vs Actual Steer Angle
                    item {
                        Surface(
                            color = Color(0xFF0A100D),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF263238)),
                            modifier = Modifier.fillMaxWidth().height(150.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "LIVE OSCILLOSCOPE (TARGET VS ACTUAL WAS)",
                                        color = Color(0xFF90A4AE),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("● Target (Cyan)", color = Color(0xFF80D8FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Text("● Actual WAS (Green)", color = Color(0xFF00E676), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Canvas(modifier = Modifier.fillMaxSize().padding(top = 6.dp)) {
                                    val w = size.width
                                    val h = size.height
                                    val midY = h / 2f

                                    // Center line (0 degrees)
                                    drawLine(
                                        color = Color(0xFF37474F),
                                        start = Offset(0f, midY),
                                        end = Offset(w, midY),
                                        strokeWidth = 1f
                                    )

                                    val maxDegRange = 35f // +/- 35 deg vertical range

                                    // Target line path
                                    if (targetHistory.size > 1) {
                                        val pathTarget = Path()
                                        val stepX = w / 80f
                                        for (i in targetHistory.indices) {
                                            val x = i * stepX
                                            val y = midY - (targetHistory[i] / maxDegRange) * (h / 2f)
                                            if (i == 0) pathTarget.moveTo(x, y) else pathTarget.lineTo(x, y)
                                        }
                                        drawPath(pathTarget, Color(0xFF80D8FF), style = Stroke(width = 2.5f))
                                    }

                                    // Actual WAS line path
                                    if (actualHistory.size > 1) {
                                        val pathActual = Path()
                                        val stepX = w / 80f
                                        for (i in actualHistory.indices) {
                                            val x = i * stepX
                                            val y = midY - (actualHistory[i] / maxDegRange) * (h / 2f)
                                            if (i == 0) pathActual.moveTo(x, y) else pathActual.lineTo(x, y)
                                        }
                                        drawPath(pathActual, Color(0xFF00E676), style = Stroke(width = 2.5f))
                                    }
                                }
                            }
                        }
                    }

                    // 2. Real-Time Telemetry Cards
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                color = Color(0xFF1B2621),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).padding(2.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("ACTUAL STEER ANGLE", color = Color(0xFFB0BEC5), fontSize = 9.sp)
                                    Text(
                                        text = "${String.format("%+.2f", hardwareStatus.actualSteerAngleDeg)}°",
                                        color = Color(0xFF00E676),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Surface(
                                color = Color(0xFF1B2621),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).padding(2.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("RAW 12-BIT ADC", color = Color(0xFFB0BEC5), fontSize = 9.sp)
                                    Text(
                                        text = "${hardwareStatus.rawAdcCount} (${String.format("%.2f", hardwareStatus.sensorVoltage)}V)",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Surface(
                                color = Color(0xFF1B2621),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).padding(2.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("TARGET COMMAND", color = Color(0xFFB0BEC5), fontSize = 9.sp)
                                    Text(
                                        text = "${String.format("%+.2f", targetSteerAngleDeg)}°",
                                        color = Color(0xFF80D8FF),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }

                    // 3. One-Click Zero Calibration Wizard
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2B26))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Step 1: Wheel Center Alignment",
                                    color = Color(0xFF00E676),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Align the tractor wheels pointing dead-straight ahead along a straight pass or concrete seam, then tap 'CALIBRATE ZERO POINT'.",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 11.sp
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            // The offset to zero is the negative of the current raw steer angle
                                            wasZeroOffsetDeg = -hardwareStatus.actualSteerAngleDeg
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                                        modifier = Modifier.weight(1.5f)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("CALIBRATE ZERO POINT", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }

                                    OutlinedButton(
                                        onClick = { wasZeroOffsetDeg = 0.0 },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("RESET (0.0°)", fontSize = 11.sp, color = Color.White)
                                    }
                                }

                                Text(
                                    text = "Current Zero Offset: ${String.format("%+.2f°", wasZeroOffsetDeg)}",
                                    color = Color(0xFF80D8FF),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // 4. Counts Per Degree & Inversion
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2B26))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Step 2: Counts Per Degree (Scale)", color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("$countsPerDegree cts/°", color = Color.White, fontWeight = FontWeight.Black)
                                }
                                Text(
                                    text = "Turn wheels full left, then full right. Measure physical tire angle to adjust counts/degree.",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.sp
                                )
                                Slider(
                                    value = countsPerDegree.toFloat(),
                                    onValueChange = { countsPerDegree = it.roundToInt() },
                                    valueRange = 30f..250f
                                )

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = isWasInverted,
                                        onClick = { isWasInverted = !isWasInverted },
                                        label = { Text("Invert WAS (Left/Right)") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = isMotorInverted,
                                        onClick = { isMotorInverted = !isMotorInverted },
                                        label = { Text("Invert Valve Flow") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // 5. Valve PWM Deadband & Max Limits
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2B26))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Step 3: Valve PWM Crack-Open & Maximum Safety Limit", color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 13.sp)

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Minimum PWM Deadband: $minPwm", color = Color.White, fontSize = 11.sp)
                                    Text("Max PWM Limit: $maxPwm", color = Color.White, fontSize = 11.sp)
                                }

                                Slider(
                                    value = minPwm.toFloat(),
                                    onValueChange = { minPwm = it.roundToInt() },
                                    valueRange = 5f..80f
                                )
                                Slider(
                                    value = maxPwm.toFloat(),
                                    onValueChange = { maxPwm = it.roundToInt() },
                                    valueRange = 150f..255f
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onUpdateSteerValveConfig(
                                steerValveConfig.copy(
                                    wasCountsPerDeg = countsPerDegree,
                                    wasZeroOffsetDeg = wasZeroOffsetDeg,
                                    isWasInverted = isWasInverted,
                                    isMotorInverted = isMotorInverted,
                                    minPwmDeadband = minPwm,
                                    maxPwmLimit = maxPwm
                                )
                            )
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                    ) {
                        Text("Save Calibration", color = Color.Black, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

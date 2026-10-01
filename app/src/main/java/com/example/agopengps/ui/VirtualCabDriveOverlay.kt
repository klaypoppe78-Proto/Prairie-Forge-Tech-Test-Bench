package com.example.agopengps.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Interactive on-screen Virtual Steering Wheel & Throttle overlay.
 * Allows complete bench-testing of the autosteer system, line acquisition, and turn response
 * on the tablet without needing to connect to the tractor or GNSS hardware.
 */
@Composable
fun VirtualCabDriveOverlay(
    isAutoSteerEngaged: Boolean,
    simSpeedKmh: Double,
    actualSteerAngleDeg: Double,
    targetSteerAngleDeg: Double,
    isReverse: Boolean,
    maxSteerDeg: Double = 40.0,
    onManualSteerChange: (Double) -> Unit,
    onManualSteerRelease: () -> Unit,
    onSetSimSpeed: (Double) -> Unit,
    onToggleReverse: () -> Unit,
    onToggleAutoSteer: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dragSteerAngle by remember { mutableDoubleStateOf(0.0) }
    var isTouchingWheel by remember { mutableStateOf(false) }

    // When auto-steer is engaged, show the wheel turning automatically
    val displayedWheelAngle = if (isAutoSteerEngaged) {
        targetSteerAngleDeg * 4.0 // Amplify visual steering wheel rotation
    } else if (isTouchingWheel) {
        dragSteerAngle * 4.0
    } else {
        actualSteerAngleDeg * 4.0
    }

    Surface(
        color = Color(0xDD101A15),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF00E676).copy(alpha = 0.6f)),
        modifier = modifier
            .shadow(16.dp, RoundedCornerShape(16.dp))
            .testTag("virtual_cab_drive_overlay")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SportsMotorsports,
                        contentDescription = null,
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "TABLET VIRTUAL DRIVE",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFFB0BEC5), modifier = Modifier.size(16.dp))
                }
            }

            // Interactive Steering Wheel
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E2824))
                    .border(4.dp, if (isAutoSteerEngaged) Color(0xFF00E676) else Color(0xFF78909C), CircleShape)
                    .pointerInput(isAutoSteerEngaged) {
                        if (!isAutoSteerEngaged) {
                            detectDragGestures(
                                onDragStart = { isTouchingWheel = true },
                                onDragEnd = {
                                    isTouchingWheel = false
                                    dragSteerAngle = 0.0
                                    onManualSteerRelease()
                                },
                                onDragCancel = {
                                    isTouchingWheel = false
                                    dragSteerAngle = 0.0
                                    onManualSteerRelease()
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    // Horizontal drag steers left/right
                                    dragSteerAngle = (dragSteerAngle + (dragAmount.x * 0.4))
                                        .coerceIn(-maxSteerDeg, maxSteerDeg)
                                    onManualSteerChange(dragSteerAngle)
                                }
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // Steering Wheel Graphic
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.88f)
                        .rotate(displayedWheelAngle.toFloat()),
                    contentAlignment = Alignment.Center
                ) {
                    // Wheel Outer Rim
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(6.dp, Color(0xFF455A64), CircleShape)
                    )
                    // Spokes
                    Box(modifier = Modifier.width(6.dp).fillMaxHeight().background(Color(0xFF37474F)))
                    Box(modifier = Modifier.height(6.dp).fillMaxWidth().background(Color(0xFF37474F)))

                    // Center Hub Button: Engages/Disengages Auto-Steer
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (isAutoSteerEngaged) Color(0xFF00E676) else Color(0xFFD32F2F))
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isAutoSteerEngaged) "AUTO" else "MANUAL",
                            color = if (isAutoSteerEngaged) Color.Black else Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 8.sp
                        )
                    }
                }
            }

            // Wheel Angle readout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ACTUAL WAS", color = Color(0xFFB0BEC5), fontSize = 9.sp)
                    Text(
                        text = "${String.format("%+.1f", actualSteerAngleDeg)}°",
                        color = Color(0xFF00E676),
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("TARGET PGN 254", color = Color(0xFFB0BEC5), fontSize = 9.sp)
                    Text(
                        text = "${String.format("%+.1f", targetSteerAngleDeg)}°",
                        color = Color(0xFF80D8FF),
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }

            // Quick Center & Steer Nudge Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedButton(
                    onClick = { onManualSteerChange(-10.0) },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    modifier = Modifier.weight(1f).height(28.dp)
                ) {
                    Text("◀ -10°", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Button(
                    onClick = {
                        dragSteerAngle = 0.0
                        onManualSteerRelease()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F)),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    modifier = Modifier.weight(1f).height(28.dp)
                ) {
                    Text("CENTER", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { onManualSteerChange(10.0) },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    modifier = Modifier.weight(1f).height(28.dp)
                ) {
                    Text("+10° ▶", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            // Throttle & Direction Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onToggleReverse,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isReverse) Color(0xFFE65100) else Color(0xFF1B5E20)
                    ),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(if (isReverse) "REV" else "FWD", fontSize = 10.sp, fontWeight = FontWeight.Black)
                }

                val speedMph = simSpeedKmh * 0.621371
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Throttle: ${String.format("%.1f", speedMph)} mph (${String.format("%.1f", simSpeedKmh)} km/h)",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Slider(
                        value = simSpeedKmh.toFloat(),
                        onValueChange = { onSetSimSpeed(it.toDouble()) },
                        valueRange = 0f..25f,
                        modifier = Modifier.height(20.dp)
                    )
                }
            }

            // Quick Preset Speeds (Field, Transport, Stop)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AssistChip(
                    onClick = { onSetSimSpeed(0.0) },
                    label = { Text("0 mph", fontSize = 9.sp) },
                    modifier = Modifier.weight(1f)
                )
                AssistChip(
                    onClick = { onSetSimSpeed(7.2) }, // ~4.5 mph field speed
                    label = { Text("4.5 mph (Plant)", fontSize = 9.sp) },
                    modifier = Modifier.weight(1.4f)
                )
                AssistChip(
                    onClick = { onSetSimSpeed(16.0) }, // ~10 mph spray
                    label = { Text("10 mph (Spray)", fontSize = 9.sp) },
                    modifier = Modifier.weight(1.4f)
                )
            }
        }
    }
}

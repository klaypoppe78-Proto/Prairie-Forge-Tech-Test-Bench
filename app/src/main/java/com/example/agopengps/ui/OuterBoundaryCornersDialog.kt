package com.example.agopengps.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.agopengps.navigation.Vec2
import com.example.agopengps.navigation.GeoUtils

@Composable
fun OuterBoundaryCornersDialog(
    capturedCorners: List<Vec2>,
    currentTractorPos: Vec2,
    swathWidthMeters: Double,
    onCaptureCorner: () -> Unit,
    onClearCorners: () -> Unit,
    onConfirmCorners: (fieldName: String, farmName: String) -> Unit,
    onDismiss: () -> Unit
) {
    var fieldName by remember { mutableStateOf("Boundary Field 1") }
    var farmName by remember { mutableStateOf("Lac qui Parle Home") }

    val liveCorners = capturedCorners + listOf(currentTractorPos)
    val calculatedAcres = if (liveCorners.size >= 3) {
        GeoUtils.calculatePolygonAreaAcres(liveCorners)
    } else 0.0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .testTag("outer_boundary_corners_dialog"),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF131D17),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF00897B))
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = Color(0xFF00897B),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PinDrop, contentDescription = null, tint = Color.White)
                            }
                        }
                        Column {
                            Text(
                                text = "Set Outer Boundaries (4 Corners)",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Drive or walk to each outer boundary corner and tap 'Capture Corner'",
                                color = Color(0xFF80CBC4),
                                fontSize = 12.sp
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Current tractor position info & Capture Card
                    Surface(
                        color = Color(0xFF1A2B23),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E4035)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("CURRENT VEHICLE / ANTENNA GPS LOCATION", color = Color(0xFF90A4AE), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Local Meters: X = ${String.format("%.2f", currentTractorPos.x)}m, Y = ${String.format("%.2f", currentTractorPos.y)}m",
                                color = Color(0xFF00E676),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onCaptureCorner,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                    modifier = Modifier.weight(1.5f).height(46.dp)
                                ) {
                                    Icon(Icons.Default.AddLocation, contentDescription = null, tint = Color.Black)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "CAPTURE CORNER #${capturedCorners.size + 1}",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp
                                    )
                                }
                                if (capturedCorners.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = onClearCorners,
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                                        modifier = Modifier.weight(1f).height(46.dp)
                                    ) {
                                        Text("CLEAR ALL", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Farm & Field Identity
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = farmName,
                            onValueChange = { farmName = it },
                            label = { Text("Farm / Client") },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00897B),
                                unfocusedBorderColor = Color(0xFF37474F)
                            ),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = fieldName,
                            onValueChange = { fieldName = it },
                            label = { Text("Field Name") },
                            modifier = Modifier.weight(1.2f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00897B),
                                unfocusedBorderColor = Color(0xFF37474F)
                            ),
                            singleLine = true
                        )
                    }

                    // Captured Corners List
                    Surface(
                        color = Color(0xFF141F1A),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF263238)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "CAPTURED PERIMETER CORNERS (${capturedCorners.size})",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "Calculated Area: ${String.format("%.2f AC", calculatedAcres)}",
                                    color = Color(0xFF00E676),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            if (capturedCorners.isEmpty()) {
                                Text(
                                    text = "No corners captured yet. Drive tractor or walk antenna to Corner 1 (e.g. NW corner) and tap 'Capture Corner'.",
                                    color = Color(0xFF78909C),
                                    fontSize = 12.sp
                                )
                            } else {
                                capturedCorners.forEachIndexed { idx, pt ->
                                    val cornerLetter = when (idx) {
                                        0 -> "Corner A (SW)"
                                        1 -> "Corner B (SE)"
                                        2 -> "Corner C (NE)"
                                        3 -> "Corner D (NW)"
                                        else -> "Corner ${idx + 1}"
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("• $cornerLetter", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(
                                            "X: ${String.format("%.1fm", pt.x)}, Y: ${String.format("%.1fm", pt.y)}",
                                            color = Color(0xFF81C784),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Guidance Note
                    Surface(
                        color = Color(0xFF0D1F16),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "💡 Tip: Setting 4 outer boundary corners closes the field polygon without needing to drive the entire fence line. Headlands are automatically buffered inside by swath width.",
                            color = Color(0xFFB0BEC5),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("CANCEL", color = Color.White)
                    }

                    Button(
                        onClick = { onConfirmCorners(fieldName, farmName) },
                        enabled = capturedCorners.size >= 3,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E676),
                            disabledContainerColor = Color(0xFF263238)
                        ),
                        modifier = Modifier.weight(1.8f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SAVE OUTER BOUNDARY (${capturedCorners.size} PTS)",
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

package com.example.agopengps.ui

import androidx.compose.animation.*
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.agopengps.navigation.ABLine
import com.example.agopengps.navigation.CultivatorSplitPass
import com.example.agopengps.navigation.RecordedPath

/**
 * Organic Cultivator Pass Splitting Engine & Visual Geometry Calculator.
 * Splits a 24-Row @ 22" (44ft) Planter Swath or Curvilinear Path into two perfectly matched
 * 12-Row @ 22" (22ft) Cultivator passes with precision knife offset adjustment.
 */
@Composable
fun OrganicCultivatorSplitDialog(
    planterAbLine: ABLine?,
    planterRecordedPath: RecordedPath?,
    activeSplitPass: CultivatorSplitPass,
    onApplySplitAbLine: (ABLine, CultivatorSplitPass, Double) -> Unit,
    onApplySplitRecordedPath: (RecordedPath, CultivatorSplitPass, Double) -> Unit,
    onDismiss: () -> Unit
) {
    var planterRows by remember { mutableStateOf(24) }
    var cultivatorRows by remember { mutableStateOf(12) }
    var rowSpacingInches by remember { mutableStateOf(22.0) }
    var knifeOffsetInches by remember { mutableStateOf(0.0) }
    var selectedPass by remember { mutableStateOf(activeSplitPass) }

    val planterWidthFeet = (planterRows * rowSpacingInches) / 12.0
    val cultivatorWidthFeet = (cultivatorRows * rowSpacingInches) / 12.0
    val lateralShiftFeet = (planterWidthFeet - cultivatorWidthFeet) / 2.0 // Exactly 11.0 ft for 24R -> 12R @ 22"
    val lateralShiftMeters = lateralShiftFeet * 0.3048

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .testTag("organic_cultivator_split_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0F172A),
            border = BorderStroke(1.dp, Color(0x4434D399)),
            shadowElevation = 24.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(22.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0x2234D399),
                            border = BorderStroke(1.dp, Color(0x4434D399)),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Filled.CompareArrows,
                                    contentDescription = null,
                                    tint = Color(0xFF34D399),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                "Organic Cultivator Pass Splitting Engine",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp
                            )
                            Text(
                                "Split 24-Row 22\" Planter Pass → Two 12-Row Cultivator Paths",
                                color = Color(0xFF34D399),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Interactive 24-Row to 12-Row Visual Geometry Diagram
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0x221E293B),
                    border = BorderStroke(1.dp, Color(0x4494A3B8)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "PASS GEOMETRY: 24-ROW PLANTER ($planterWidthFeet FT) → TWO 12-ROW PASSES ($cultivatorWidthFeet FT)",
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Graphic representation of the 2 passes
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left 12 Rows Box (Pass A)
                            val isLeftActive = selectedPass == CultivatorSplitPass.PASS_A_LEFT
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isLeftActive) Color(0x3334D399) else Color(0x1F64748B),
                                border = BorderStroke(2.dp, if (isLeftActive) Color(0xFF34D399) else Color(0x4464748B)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedPass = CultivatorSplitPass.PASS_A_LEFT }
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("PASS A (LEFT 12R)", color = if (isLeftActive) Color(0xFF34D399) else Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Rows 1 to 12", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                    Text("Shift: -${String.format("%.1f", lateralShiftFeet)} ft (-${String.format("%.2f", lateralShiftMeters)}m)", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Center Planter Line Marker
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 8.dp)
                                    .width(2.dp)
                                    .height(50.dp)
                                    .background(Color(0xFFFBBF24))
                            )

                            // Right 12 Rows Box (Pass B)
                            val isRightActive = selectedPass == CultivatorSplitPass.PASS_B_RIGHT
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isRightActive) Color(0x3338BDF8) else Color(0x1F64748B),
                                border = BorderStroke(2.dp, if (isRightActive) Color(0xFF38BDF8) else Color(0x4464748B)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedPass = CultivatorSplitPass.PASS_B_RIGHT }
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("PASS B (RIGHT 12R)", color = if (isRightActive) Color(0xFF38BDF8) else Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Rows 13 to 24", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                    Text("Shift: +${String.format("%.1f", lateralShiftFeet)} ft (+${String.format("%.2f", lateralShiftMeters)}m)", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Yellow Line = 24-Row Planter Baseline • Green/Blue = 12-Row Cultivator Center",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Fine Tuning / Knife Inter-Row Offset Controls
                Text("CULTIVATOR KNIFE INTER-ROW CENTERING ADJUSTMENT", color = Color(0xFF34D399), fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.5.sp)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(-5.5, -2.0, 0.0, 2.0, 5.5).forEach { offset ->
                        val isSel = knifeOffsetInches == offset
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSel) Color(0x3300F59B) else Color(0x1F64748B),
                            border = BorderStroke(1.dp, if (isSel) Color(0xFF00F59B) else Color(0x3394A3B8)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { knifeOffsetInches = offset }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    if (offset == 0.0) "Centered" else "${if (offset > 0) "+" else ""}${offset}\"",
                                    color = if (isSel) Color(0xFF00F59B) else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (offset == 0.0) "0\" knife" else if (offset < 0) "Left" else "Right",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = Color(0xFF94A3B8))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Apply Pass A (Left)
                        Button(
                            onClick = {
                                if (planterRecordedPath != null) {
                                    onApplySplitRecordedPath(planterRecordedPath, CultivatorSplitPass.PASS_A_LEFT, knifeOffsetInches)
                                } else if (planterAbLine != null) {
                                    onApplySplitAbLine(planterAbLine, CultivatorSplitPass.PASS_A_LEFT, knifeOffsetInches)
                                }
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Engage Pass A (Left 12R)", fontWeight = FontWeight.Bold)
                        }

                        // Apply Pass B (Right)
                        Button(
                            onClick = {
                                if (planterRecordedPath != null) {
                                    onApplySplitRecordedPath(planterRecordedPath, CultivatorSplitPass.PASS_B_RIGHT, knifeOffsetInches)
                                } else if (planterAbLine != null) {
                                    onApplySplitAbLine(planterAbLine, CultivatorSplitPass.PASS_B_RIGHT, knifeOffsetInches)
                                }
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Engage Pass B (Right 12R)", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

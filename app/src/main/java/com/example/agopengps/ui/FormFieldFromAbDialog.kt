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
import com.example.agopengps.navigation.ABLine

enum class FieldSideChoice(val label: String, val desc: String) {
    RIGHT("Right of AB Line", "Field spans outward to the right side of the baseline path"),
    LEFT("Left of AB Line", "Field spans outward to the left side of the baseline path"),
    BOTH_SIDES("Centered (Both Sides)", "AB Line runs down the exact centerline; swaths expand equally")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormFieldFromAbDialog(
    abLine: ABLine?,
    currentSwathWidthMeters: Double,
    onConfirmFormField: (
        fieldName: String,
        farmName: String,
        cropType: String,
        sideChoice: FieldSideChoice,
        extentAcres: Double,
        headlandPasses: Int,
        useAbLineLength: Boolean
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var fieldName by remember { mutableStateOf("South 80 Swath Section") }
    var farmName by remember { mutableStateOf("Lac qui Parle Home") }
    var cropType by remember { mutableStateOf("Corn") }
    var selectedSide by remember { mutableStateOf(FieldSideChoice.RIGHT) }
    var selectedAcreage by remember { mutableStateOf(80.0) }
    var headlandPasses by remember { mutableStateOf(2) }
    var useAbLineLength by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.90f)
                .testTag("form_field_from_ab_dialog"),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF131D17),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF2E7D32))
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
                            color = Color(0xFF00E676),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AspectRatio,
                                    contentDescription = null,
                                    tint = Color.Black
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Form Field Boundary from AB Line",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Constructs outer boundary using your AB line baseline with tractor at Point A corner",
                                color = Color(0xFF81C784),
                                fontSize = 12.sp
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
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
                    // AB Line Baseline Info Card
                    Surface(
                        color = Color(0xFF1E2E24),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E4035)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("ACTIVE BASELINE REFERENCE", color = Color(0xFF90A4AE), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = abLine?.name ?: "Current AB Swath",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Heading: ${String.format("%.1f°", abLine?.headingDeg ?: 0.0)} • Tool: ${String.format("%.1f ft", currentSwathWidthMeters * 3.28084)}",
                                    color = Color(0xFF00E676),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Surface(
                                color = Color(0xFF0D331D),
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676))
                            ) {
                                Text(
                                    text = "BASELINE LOCKED",
                                    color = Color(0xFF00E676),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
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
                                focusedBorderColor = Color(0xFF00E676),
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
                                focusedBorderColor = Color(0xFF00E676),
                                unfocusedBorderColor = Color(0xFF37474F)
                            ),
                            singleLine = true
                        )
                    }

                    // Field Acreage Presets (Standard Ag Sections)
                    Column {
                        Text("TARGET FIELD ACREAGE", color = Color(0xFFB0BEC5), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                40.0 to "40 ACRES\n(1/4 × 1/4 mi)",
                                80.0 to "80 ACRES\n(1/2 × 1/4 mi)",
                                160.0 to "160 ACRES\n(1/2 × 1/2 mi)",
                                320.0 to "320 ACRES\n(1/2 × 1 mi)"
                            ).forEach { (acres, label) ->
                                val selected = selectedAcreage == acres
                                Surface(
                                    color = if (selected) Color(0xFF2E7D32) else Color(0xFF1B2620),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Color(0xFF00E676) else Color(0xFF263238)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedAcreage = acres }
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (selected) Color.White else Color(0xFF90A4AE),
                                            fontWeight = if (selected) FontWeight.Black else FontWeight.Normal,
                                            fontSize = 11.sp,
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Field Side Selection relative to AB line
                    Column {
                        Text("FIELD SPAN DIRECTION RELATIVE TO AB LINE", color = Color(0xFFB0BEC5), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        FieldSideChoice.values().forEach { side ->
                            val sel = side == selectedSide
                            Surface(
                                color = if (sel) Color(0xFF1E3829) else Color(0xFF16211B),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (sel) Color(0xFF00E676) else Color(0xFF263238)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedSide = side }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    RadioButton(
                                        selected = sel,
                                        onClick = { selectedSide = side },
                                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF00E676))
                                    )
                                    Column {
                                        Text(side.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(side.desc, color = Color(0xFF81C784), fontSize = 11.sp)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    // Headland Passes Selection
                    Column {
                        Text("HEADLAND BUFFER BOUNDARY", color = Color(0xFFB0BEC5), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                0 to "No Headlands",
                                1 to "1 Pass (${String.format("%.0fft", currentSwathWidthMeters * 3.28084)})",
                                2 to "2 Passes (${String.format("%.0fft", currentSwathWidthMeters * 3.28084 * 2)})",
                                3 to "3 Passes (${String.format("%.0fft", currentSwathWidthMeters * 3.28084 * 3)})"
                            ).forEach { (passes, label) ->
                                val selected = headlandPasses == passes
                                Surface(
                                    color = if (selected) Color(0xFF00796B) else Color(0xFF1B2620),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Color(0xFF4DD0E1) else Color(0xFF263238)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { headlandPasses = passes }
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (selected) Color.White else Color(0xFF90A4AE),
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Geometry summary box
                    Surface(
                        color = Color(0xFF0D1F16),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1B5E20)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("FIELD GEOMETRY CONFIRMATION", color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• Corner 1 / Entrance is located at Point A (tractor location).\n" +
                                        "• Baseline edge tracks AB line forward.\n" +
                                        "• Outer boundary extends ${selectedSide.label.lowercase()} to achieve ${String.format("%.1f", selectedAcreage)} acres.\n" +
                                        "• All subsequent passes and coverage data will automatically be saved to this field.",
                                color = Color(0xFFC8E6C9),
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
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
                        onClick = {
                            onConfirmFormField(
                                fieldName,
                                farmName,
                                cropType,
                                selectedSide,
                                selectedAcreage,
                                headlandPasses,
                                useAbLineLength
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                        modifier = Modifier
                            .weight(1.8f)
                            .testTag("confirm_form_field_from_ab_button")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "CREATE & START FIELD (${selectedAcreage.toInt()} AC)",
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

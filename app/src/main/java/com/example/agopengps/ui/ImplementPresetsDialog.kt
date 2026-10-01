package com.example.agopengps.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.agopengps.navigation.ImplementConfig
import com.example.agopengps.navigation.ImplementPreset
import com.example.agopengps.navigation.ImplementType
import java.util.Locale

@Composable
fun ImplementPresetsDialog(
    currentConfig: ImplementConfig,
    onSelectPreset: (ImplementPreset) -> Unit,
    onCustomConfigSave: (ImplementConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf("All") }
    var showCustomEditor by remember { mutableStateOf(false) }

    // Custom editor states
    var customWidthFeet by remember { mutableStateOf(String.format(Locale.US, "%.1f", currentConfig.toolWidth * 3.28084)) }
    var customSections by remember { mutableStateOf(currentConfig.numSections.toString()) }
    var customOverlapInches by remember { mutableStateOf(String.format(Locale.US, "%.1f", currentConfig.overlap * 39.3701)) }
    var customHitchFeet by remember { mutableStateOf(String.format(Locale.US, "%.1f", currentConfig.offsetBehindTractor * 3.28084)) }
    var customImplementType by remember { mutableStateOf(currentConfig.implementType) }

    val bgDark = Color(0xFF141E18)
    val cardBg = Color(0xFF1B2820)
    val accentGreen = Color(0xFF00E676)

    val categories = listOf("All", "Planting & Seeding", "Spraying & Chemical", "Tillage", "Harvest & Hauling")
    val presets = ImplementPreset.FACTORY_PRESETS.filter {
        selectedCategory == "All" || it.category == selectedCategory
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .testTag("implement_presets_dialog"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = bgDark)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
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
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(accentGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = null,
                                tint = Color.Black
                            )
                        }
                        Column {
                            Text(
                                text = "Implement & Tool Profiles",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Current: ${String.format(Locale.US, "%.1f ft (%.2fm)", currentConfig.toolWidth * 3.28084, currentConfig.toolWidth)} • ${currentConfig.numSections} Sections",
                                color = Color(0xFF81C784),
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Mode toggle between Presets vs Custom
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showCustomEditor = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!showCustomEditor) accentGreen else cardBg
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "PRESET LIBRARY",
                            color = if (!showCustomEditor) Color.Black else Color.LightGray,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = { showCustomEditor = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (showCustomEditor) accentGreen else cardBg
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "CUSTOM TOOL GEOMETRY",
                            color = if (showCustomEditor) Color.Black else Color.LightGray,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!showCustomEditor) {
                    // Category chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { cat ->
                            val isSel = cat == selectedCategory
                            Surface(
                                color = if (isSel) accentGreen else cardBg,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .clickable { selectedCategory = cat }
                                    .padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = cat,
                                    color = if (isSel) Color.Black else Color.LightGray,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Presets List
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(presets, key = { it.id }) { p ->
                            val isCurrent = (currentConfig.toolWidth - p.toolWidthMeters).let { kotlin.math.abs(it) < 0.05 } &&
                                    currentConfig.numSections == p.numSections

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isCurrent) Color(0xFF1E3A2B) else cardBg
                                ),
                                shape = RoundedCornerShape(10.dp),
                                border = if (isCurrent) androidx.compose.foundation.BorderStroke(1.5.dp, accentGreen) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectPreset(p)
                                        onDismiss()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = p.name,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            if (isCurrent) {
                                                Surface(
                                                    color = accentGreen,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "ACTIVE",
                                                        color = Color.Black,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${p.category} • ${p.implementType.label}",
                                            color = Color(0xFF81C784),
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = p.description,
                                            color = Color.LightGray,
                                            fontSize = 11.sp
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            onSelectPreset(p)
                                            onDismiss()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isCurrent) Color(0xFF2E7D32) else accentGreen
                                        )
                                    ) {
                                        Text(
                                            text = if (isCurrent) "LOADED" else "SELECT",
                                            color = if (isCurrent) Color.White else Color.Black,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Custom Configuration Editor
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Hitch Mounting Style:",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { customImplementType = ImplementType.THREE_POINT_MOUNTED },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (customImplementType == ImplementType.THREE_POINT_MOUNTED) accentGreen else cardBg
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "3-Point Hitch",
                                    color = if (customImplementType == ImplementType.THREE_POINT_MOUNTED) Color.Black else Color.White
                                )
                            }
                            Button(
                                onClick = { customImplementType = ImplementType.TRAILING_TOOLBAR },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (customImplementType == ImplementType.TRAILING_TOOLBAR) accentGreen else cardBg
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Trailing Drawbar",
                                    color = if (customImplementType == ImplementType.TRAILING_TOOLBAR) Color.Black else Color.White
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customWidthFeet,
                                onValueChange = { customWidthFeet = it },
                                label = { Text("Tool Width (Feet)") },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = accentGreen
                                )
                            )
                            OutlinedTextField(
                                value = customSections,
                                onValueChange = { customSections = it },
                                label = { Text("Boom / Row Sections (1-48)") },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = accentGreen
                                )
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customOverlapInches,
                                onValueChange = { customOverlapInches = it },
                                label = { Text("Swath Overlap (Inches)") },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = accentGreen
                                )
                            )
                            OutlinedTextField(
                                value = customHitchFeet,
                                onValueChange = { customHitchFeet = it },
                                label = { Text("Hitch-to-Tool (Feet)") },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = accentGreen
                                )
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Button(
                            onClick = {
                                val widthMeters = (customWidthFeet.toDoubleOrNull() ?: 30.0) / 3.28084
                                val sections = (customSections.toIntOrNull() ?: 6).coerceIn(1, 48)
                                val overlapMeters = (customOverlapInches.toDoubleOrNull() ?: 6.0) / 39.3701
                                val hitchMeters = (customHitchFeet.toDoubleOrNull() ?: 3.0) / 3.28084

                                onCustomConfigSave(
                                    currentConfig.copy(
                                        implementType = customImplementType,
                                        toolWidth = widthMeters.coerceAtLeast(1.0),
                                        numSections = sections,
                                        overlap = overlapMeters.coerceIn(0.0, 1.0),
                                        offsetBehindTractor = hitchMeters
                                    )
                                )
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accentGreen)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "APPLY CUSTOM IMPLEMENT",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

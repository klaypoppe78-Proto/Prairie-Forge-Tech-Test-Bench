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
import com.example.agopengps.navigation.FieldObstacle
import com.example.agopengps.navigation.ObstacleType
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObstacleDialog(
    obstacles: List<FieldObstacle>,
    currentLat: Double,
    currentLon: Double,
    onAddObstacleAtVehicle: (type: ObstacleType, name: String, radius: Double, notes: String) -> Unit,
    onDeleteObstacle: (Long) -> Unit,
    onClearAllObstacles: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = Drop / Add, 1 = Manage List
    var selectedType by remember { mutableStateOf(ObstacleType.ROCK) }
    var obstacleName by remember { mutableStateOf("") }
    var safetyRadiusMeters by remember { mutableStateOf(3.0) }
    var obstacleNotes by remember { mutableStateOf("") }

    val bgDark = Color(0xFF141E18)
    val cardBg = Color(0xFF1B2820)
    val accentGreen = Color(0xFF00E676)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .testTag("obstacle_manager_dialog"),
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
                                .background(Color(0xFFFF9800)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Obstacles",
                                tint = Color.Black
                            )
                        }
                        Column {
                            Text(
                                text = "Field Obstacles & Hazards",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "GPS tagged rocks, standpipes, washouts, and hazards",
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

                // Tab Switcher
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = cardBg,
                    contentColor = accentGreen
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("DROP HAZARD HERE", fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.AddLocation, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("HAZARD LIST (${obstacles.size})", fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.List, contentDescription = null) }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (selectedTab == 0) {
                    // --- DROP / ADD HAZARD TAB ---
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Select Hazard Category:",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        // Grid of Hazard Types
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ObstacleTypeItem(
                                    type = ObstacleType.ROCK,
                                    isSelected = selectedType == ObstacleType.ROCK,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        selectedType = ObstacleType.ROCK
                                        safetyRadiusMeters = ObstacleType.ROCK.defaultRadiusMeters
                                        if (obstacleName.isEmpty()) obstacleName = "Rock"
                                    }
                                )
                                ObstacleTypeItem(
                                    type = ObstacleType.TILE_INLET,
                                    isSelected = selectedType == ObstacleType.TILE_INLET,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        selectedType = ObstacleType.TILE_INLET
                                        safetyRadiusMeters = ObstacleType.TILE_INLET.defaultRadiusMeters
                                        if (obstacleName.isEmpty()) obstacleName = "Tile Drain"
                                    }
                                )
                                ObstacleTypeItem(
                                    type = ObstacleType.POWER_POLE,
                                    isSelected = selectedType == ObstacleType.POWER_POLE,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        selectedType = ObstacleType.POWER_POLE
                                        safetyRadiusMeters = ObstacleType.POWER_POLE.defaultRadiusMeters
                                        if (obstacleName.isEmpty()) obstacleName = "Power Pole"
                                    }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ObstacleTypeItem(
                                    type = ObstacleType.WASHOUT,
                                    isSelected = selectedType == ObstacleType.WASHOUT,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        selectedType = ObstacleType.WASHOUT
                                        safetyRadiusMeters = ObstacleType.WASHOUT.defaultRadiusMeters
                                        if (obstacleName.isEmpty()) obstacleName = "Washout"
                                    }
                                )
                                ObstacleTypeItem(
                                    type = ObstacleType.TREE_STUMP,
                                    isSelected = selectedType == ObstacleType.TREE_STUMP,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        selectedType = ObstacleType.TREE_STUMP
                                        safetyRadiusMeters = ObstacleType.TREE_STUMP.defaultRadiusMeters
                                        if (obstacleName.isEmpty()) obstacleName = "Tree Stump"
                                    }
                                )
                                ObstacleTypeItem(
                                    type = ObstacleType.IRRIGATION_RISER,
                                    isSelected = selectedType == ObstacleType.IRRIGATION_RISER,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        selectedType = ObstacleType.IRRIGATION_RISER
                                        safetyRadiusMeters = ObstacleType.IRRIGATION_RISER.defaultRadiusMeters
                                        if (obstacleName.isEmpty()) obstacleName = "Pivot Riser"
                                    }
                                )
                            }
                        }

                        // Inputs
                        OutlinedTextField(
                            value = obstacleName,
                            onValueChange = { obstacleName = it },
                            label = { Text("Hazard Label / Name") },
                            placeholder = { Text("e.g. Big Granite Rock / 12\" Standpipe") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = accentGreen,
                                unfocusedBorderColor = Color.DarkGray
                            )
                        )

                        // Safety Radius Slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Proximity Alert Radius:",
                                    color = Color.LightGray,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", safetyRadiusMeters)} m (${String.format(Locale.US, "%.0f", safetyRadiusMeters * 3.28084)} ft)",
                                    color = Color(0xFFFFD700),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            Slider(
                                value = safetyRadiusMeters.toFloat(),
                                onValueChange = { safetyRadiusMeters = it.toDouble() },
                                valueRange = 1.0f..15.0f,
                                steps = 27,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFFF9800),
                                    activeTrackColor = Color(0xFFFF9800)
                                )
                            )
                        }

                        OutlinedTextField(
                            value = obstacleNotes,
                            onValueChange = { obstacleNotes = it },
                            label = { Text("Notes (Optional)") },
                            placeholder = { Text("e.g. Marked in Spring, need backhoe to remove") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 2,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = accentGreen,
                                unfocusedBorderColor = Color.DarkGray
                            )
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        // Drop Button
                        Button(
                            onClick = {
                                val finalName = if (obstacleName.isNotBlank()) obstacleName.trim() else selectedType.label
                                onAddObstacleAtVehicle(selectedType, finalName, safetyRadiusMeters, obstacleNotes.trim())
                                obstacleName = ""
                                obstacleNotes = ""
                                selectedTab = 1
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("drop_obstacle_confirm_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                        ) {
                            Icon(Icons.Default.Place, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "FLAG AT CURRENT GPS POSITION",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    // --- HAZARD LIST TAB ---
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (obstacles.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF81C784),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No obstacles flagged in this field.",
                                        color = Color.LightGray,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(obstacles, key = { it.id }) { obs ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = cardBg),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .clip(CircleShape)
                                                        .background(obs.type.displayColor),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Warning,
                                                        contentDescription = null,
                                                        tint = Color.Black,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                                Column {
                                                    Text(
                                                        text = obs.name,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp
                                                    )
                                                    Text(
                                                        text = "${obs.type.label} • Radius: ${obs.radiusMeters}m (${String.format(Locale.US, "%.4f, %.4f", obs.geoPos.latitude, obs.geoPos.longitude)})",
                                                        color = Color.LightGray,
                                                        fontSize = 11.sp
                                                    )
                                                    if (obs.notes.isNotBlank()) {
                                                        Text(
                                                            text = obs.notes,
                                                            color = Color(0xFF81C784),
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }
                                            }

                                            IconButton(onClick = { onDeleteObstacle(obs.id) }) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Hazard",
                                                    tint = Color(0xFFFF5252)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = onClearAllObstacles,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252))
                                ) {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("CLEAR ALL HAZARDS")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ObstacleTypeItem(
    type: ObstacleType,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) Color(0xFFFF9800) else Color(0xFF2E3D35)
    val bgColor = if (isSelected) Color(0x33FF9800) else Color(0xFF1B2820)

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        modifier = modifier
            .clickable { onClick() }
            .padding(vertical = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(type.displayColor)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = type.label.split("/")[0].trim(),
                color = if (isSelected) Color.White else Color.LightGray,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}

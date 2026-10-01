package com.example.agopengps.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.agopengps.navigation.GeoPoint
import com.example.agopengps.navigation.RtkBaseStationConfig
import com.example.agopengps.navigation.VehicleState
import kotlin.math.*

@Composable
fun RtkBaseStationDialog(
    config: RtkBaseStationConfig,
    vehicleState: VehicleState,
    originGeo: GeoPoint,
    onSaveConfig: (RtkBaseStationConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var isEnabled by remember { mutableStateOf(config.isEnabled) }
    var baseName by remember { mutableStateOf(config.name) }
    var latText by remember { mutableStateOf(String.format("%.7f", config.latitude)) }
    var lonText by remember { mutableStateOf(String.format("%.7f", config.longitude)) }
    var elevMetersText by remember { mutableStateOf(String.format("%.1f", config.elevationMslMeters)) }
    var antennaHeightText by remember { mutableStateOf(String.format("%.1f", config.antennaHeightMeters)) }
    var correctionSource by remember { mutableStateOf(config.correctionSource) }
    var radioFreqText by remember { mutableStateOf(String.format("%.1f", config.radioFrequencyMhz)) }

    // Live distance from tractor to base
    val dx = vehicleState.localPivotPosition.x - config.relativeX
    val dy = vehicleState.localPivotPosition.y - config.relativeY
    val distanceM = sqrt(dx * dx + dy * dy)
    val distanceMiles = distanceM * 0.000621371

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141C18)),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .padding(8.dp)
                .testTag("rtk_base_station_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
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
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E676)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CellTower, contentDescription = null, tint = Color.Black)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "RTK Base Station Survey & Location",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 17.sp
                            )
                            Text(
                                text = "Configure reference station coordinates, tower height & correction links",
                                color = Color.LightGray,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Live Status Card
                    Surface(
                        color = Color(0xFF1E2B23),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E4035)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(if (isEnabled) Color(0xFF00E676) else Color.Red, CircleShape)
                                    )
                                    Text(
                                        text = if (isEnabled) "BASE BROADCASTING ACTIVE" else "BASE OVERLAY DISABLED",
                                        color = if (isEnabled) Color(0xFF00E676) else Color.Red,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Baseline: ${String.format("%.2f miles (%.0f m)", distanceMiles, distanceM)} • Latency: 0.8s",
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Map Display", color = Color.LightGray, fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Switch(
                                    checked = isEnabled,
                                    onCheckedChange = { isEnabled = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E676))
                                )
                            }
                        }
                    }

                    // Quick Location Capture Buttons
                    Text("CHOOSE BASE STATION LOCATION", color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                val currentLat = vehicleState.geoPosition.latitude
                                val currentLon = vehicleState.geoPosition.longitude
                                val currentAlt = vehicleState.geoPosition.altitude
                                latText = String.format("%.7f", currentLat)
                                lonText = String.format("%.7f", currentLon)
                                elevMetersText = String.format("%.1f", if (currentAlt > 10.0) currentAlt else 317.0)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0072CE)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("CURRENT TRACTOR GPS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                latText = String.format("%.7f", originGeo.latitude)
                                lonText = String.format("%.7f", originGeo.longitude)
                                elevMetersText = String.format("%.1f", originGeo.altitude)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("FIELD ORIGIN (0,0)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                latText = "44.9542000"
                                lonText = "-96.0825000"
                                elevMetersText = "317.0"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("CUSTOM / RESET", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Coordinate Input Fields
                    Surface(
                        color = Color(0xFF1A241E),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("SURVEY COORDINATES", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                            OutlinedTextField(
                                value = baseName,
                                onValueChange = { baseName = it },
                                label = { Text("Base Station Name / Description") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = latText,
                                    onValueChange = { latText = it },
                                    label = { Text("Latitude (° N)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                                )
                                OutlinedTextField(
                                    value = lonText,
                                    onValueChange = { lonText = it },
                                    label = { Text("Longitude (° W)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = elevMetersText,
                                    onValueChange = { elevMetersText = it },
                                    label = { Text("Elevation MSL (Meters)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                                )
                                val elevFt = (elevMetersText.toDoubleOrNull() ?: 317.0) * 3.28084
                                Box(modifier = Modifier.weight(1f).align(Alignment.CenterVertically)) {
                                    Text(
                                        text = "= ${String.format("%.1f ft MSL", elevFt)}",
                                        color = Color(0xFF00E676),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    // Hardware & Antenna Setup
                    Surface(
                        color = Color(0xFF1A241E),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("BROADCAST & TOWER CONFIGURATION", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = antennaHeightText,
                                    onValueChange = { antennaHeightText = it },
                                    label = { Text("Tower Height (Meters)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                                )
                                OutlinedTextField(
                                    value = radioFreqText,
                                    onValueChange = { radioFreqText = it },
                                    label = { Text("Radio Freq (MHz)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                                )
                            }

                            OutlinedTextField(
                                value = correctionSource,
                                onValueChange = { correctionSource = it },
                                label = { Text("Correction Link Protocol") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action Buttons
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
                            val parsedLat = latText.toDoubleOrNull() ?: config.latitude
                            val parsedLon = lonText.toDoubleOrNull() ?: config.longitude
                            val parsedElev = elevMetersText.toDoubleOrNull() ?: config.elevationMslMeters
                            val parsedHeight = antennaHeightText.toDoubleOrNull() ?: config.antennaHeightMeters
                            val parsedFreq = radioFreqText.toDoubleOrNull() ?: config.radioFrequencyMhz

                            // Compute relative XY in meters from origin datum
                            val latRad = Math.toRadians(originGeo.latitude)
                            val metersPerDegLat = 111132.92 - 559.82 * cos(2 * latRad) + 1.175 * cos(4 * latRad)
                            val metersPerDegLon = (Math.PI / 180.0) * 6378137.0 * cos(latRad)

                            val relX = (parsedLon - originGeo.longitude) * metersPerDegLon
                            val relY = (parsedLat - originGeo.latitude) * metersPerDegLat

                            val updated = config.copy(
                                isEnabled = isEnabled,
                                name = baseName.ifBlank { "RTK Base Station" },
                                latitude = parsedLat,
                                longitude = parsedLon,
                                elevationMslMeters = parsedElev,
                                relativeX = relX,
                                relativeY = relY,
                                antennaHeightMeters = parsedHeight,
                                correctionSource = correctionSource,
                                radioFrequencyMhz = parsedFreq
                            )
                            onSaveConfig(updated)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SAVE & APPLY BASE", color = Color.Black, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

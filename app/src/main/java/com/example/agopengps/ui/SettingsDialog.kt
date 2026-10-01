package com.example.agopengps.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.example.agopengps.GnssSourceMode
import com.example.agopengps.io.UsbConnectionState
import com.example.agopengps.io.UsbHardwareStats
import com.example.agopengps.navigation.*
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SettingsDialog(
    guidanceSettings: GuidanceSettings,
    vehicleConfig: VehicleConfig,
    implementConfig: ImplementConfig,
    steerValveConfig: SteerValveConfig,
    gnssReceiverType: GnssReceiverType,
    gnssSource: GnssSourceMode,
    usbStats: UsbHardwareStats,
    currentRoll: Double,
    onUpdateGuidanceSettings: (GuidanceSettings) -> Unit,
    onUpdateVehicleConfig: (VehicleConfig) -> Unit,
    onUpdateImplementConfig: (ImplementConfig) -> Unit,
    onUpdateSteerValveConfig: (SteerValveConfig) -> Unit,
    onSetGnssReceiverType: (GnssReceiverType) -> Unit,
    onSelectGnssSource: (GnssSourceMode) -> Unit,
    onConnectUsb: () -> Unit,
    onDisconnectUsb: () -> Unit,
    isMockLoopbackActive: Boolean = false,
    onToggleMockLoopback: () -> Unit = {},
    onOpenUm982Dialog: () -> Unit = {},
    isTerrainCompensationEnabled: Boolean = false,
    showTopographyMap: Boolean = false,
    show3DTerrainMesh: Boolean = false,
    onToggleTerrainCompensation: (Boolean) -> Unit = {},
    onToggleTopographyMap: (Boolean) -> Unit = {},
    onToggle3DTerrainMesh: (Boolean) -> Unit = {},
    onResetToBasicGps: () -> Unit = {},
    onActivateAllTerrain: () -> Unit = {},
    onLoadNewHolland8870Preset: () -> Unit,
    onResetCoverage: () -> Unit,
    currentGeoPosition: com.example.agopengps.navigation.GeoPoint = com.example.agopengps.navigation.GeoPoint(0.0, 0.0, 0.0),
    onSetManualLocation: (Double, Double, Double, String) -> Unit = { _, _, _, _ -> },
    onRecenterToTabletGps: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var inputLat by remember(currentGeoPosition) { mutableStateOf(String.format(java.util.Locale.US, "%.6f", currentGeoPosition.latitude)) }
    var inputLon by remember(currentGeoPosition) { mutableStateOf(String.format(java.util.Locale.US, "%.6f", currentGeoPosition.longitude)) }
    var inputAlt by remember(currentGeoPosition) { mutableStateOf(String.format(java.util.Locale.US, "%.1f", currentGeoPosition.altitude)) }

    // --- Steering & Guidance State ---
    var stanleyGain by remember { mutableStateOf(guidanceSettings.stanleyGain) }
    var stanleyIntegral by remember { mutableStateOf(guidanceSettings.stanleyIntegralGain) }
    var controllerType by remember { mutableStateOf(guidanceSettings.controllerType) }
    var deadbandInches by remember { mutableStateOf(guidanceSettings.deadbandCm * 0.393701) }
    var lookaheadGain by remember { mutableStateOf(guidanceSettings.purePursuitLookaheadGain) }
    var tabletGpsLookahead by remember { mutableStateOf(guidanceSettings.tabletGpsLookaheadSec) }
    var terrainSideDraftGain by remember { mutableStateOf(guidanceSettings.terrainSideDraftGain) }
    var isTerrainCompActive by remember { mutableStateOf(isTerrainCompensationEnabled) }
    var isTopoMapActive by remember { mutableStateOf(showTopographyMap) }
    var is3DTerrainActive by remember { mutableStateOf(show3DTerrainMesh) }

    // --- Tractor Dimensions in Imperial (Inches / Feet) ---
    var wheelbaseInches by remember { mutableStateOf(vehicleConfig.wheelbase * 39.3701) }
    var trackWidthInches by remember { mutableStateOf(vehicleConfig.trackWidth * 39.3701) }
    var antennaHeightFeet by remember { mutableStateOf(vehicleConfig.antennaHeight * 3.28084) }
    var antennaOffsetInches by remember { mutableStateOf(vehicleConfig.antennaPivotOffset * 39.3701) }
    var hitchLengthFeet by remember { mutableStateOf(-vehicleConfig.hitchLength * 3.28084) }
    // Internal Android Tablet Mount 3D Offsets (relative to rear axle center)
    var tabletForwardOffsetInches by remember { mutableStateOf(vehicleConfig.tabletForwardOffset * 39.3701) }
    var tabletRightOffsetInches by remember { mutableStateOf(vehicleConfig.tabletRightOffset * 39.3701) }
    var tabletHeightFeet by remember { mutableStateOf(vehicleConfig.tabletHeightAboveAxle * 3.28084) }

    // --- Implement Parameters in Imperial (Feet / Inches) ---
    var implementType by remember { mutableStateOf(implementConfig.implementType) }
    var toolWidthFeet by remember { mutableStateOf(implementConfig.toolWidth * 3.28084) }
    var offsetBehindTractorFeet by remember { mutableStateOf(implementConfig.offsetBehindTractor * 3.28084) }
    var numSections by remember { mutableStateOf(implementConfig.numSections) }

    // --- Steer Valve Hardware Parameters (Trimble Nav II / FM-750 Retrofit) ---
    var valveType by remember { mutableStateOf(steerValveConfig.valveType) }
    var steerKp by remember { mutableStateOf(steerValveConfig.proportionalGainKp) }
    var steerKi by remember { mutableStateOf(steerValveConfig.integralGainKi) }
    var steerKd by remember { mutableStateOf(steerValveConfig.derivativeGainKd) }
    var minPwmDeadband by remember { mutableStateOf(steerValveConfig.minPwmDeadband) }
    var maxPwmLimit by remember { mutableStateOf(steerValveConfig.maxPwmLimit) }
    var countsPerDegree by remember { mutableStateOf(steerValveConfig.wasCountsPerDeg) }
    var wasZeroOffsetDeg by remember { mutableStateOf(steerValveConfig.wasZeroOffsetDeg) }
    var isWasInverted by remember { mutableStateOf(steerValveConfig.isWasInverted) }
    var isMotorInverted by remember { mutableStateOf(steerValveConfig.isMotorInverted) }

    // --- GNSS Receiver Profile ---
    var receiverType by remember { mutableStateOf(gnssReceiverType) }

    val isUsbConnected = usbStats.connectionState == UsbConnectionState.CONNECTED

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .testTag("settings_dialog")
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "AgSteer Hardware & Vehicle Setup",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "All units displayed in U.S. Imperial (Feet, Inches, MPH)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Scrollable Tab Row
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                        Text("Implement", modifier = Modifier.padding(10.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                        Text("Tractor (NH 8870)", modifier = Modifier.padding(10.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) {
                        Text("Steer Valve (Trimble/Danfoss)", modifier = Modifier.padding(10.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }) {
                        Text("AIO USB & GNSS", modifier = Modifier.padding(10.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Tab(selected = selectedTab == 4, onClick = { selectedTab = 4 }) {
                        Text("Steering Tuning", modifier = Modifier.padding(10.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Tab(selected = selectedTab == 5, onClick = { selectedTab = 5 }) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(10.dp)) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Setup Wizard", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF00E676))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    when (selectedTab) {
                        // ==========================================
                        // TAB 0: IMPLEMENT CONFIGURATION (3-POINT HITCH vs TRAILING)
                        // ==========================================
                        0 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text(
                                    text = "Implement Hitch & Mounting Style",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    FilterChip(
                                        selected = implementType == ImplementType.THREE_POINT_MOUNTED,
                                        onClick = { implementType = ImplementType.THREE_POINT_MOUNTED },
                                        label = { Text("3-Point Hitch Mounted Toolbar", fontWeight = FontWeight.Bold) },
                                        leadingIcon = {
                                            if (implementType == ImplementType.THREE_POINT_MOUNTED) {
                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )

                                    FilterChip(
                                        selected = implementType == ImplementType.TRAILING_TOOLBAR,
                                        onClick = { implementType = ImplementType.TRAILING_TOOLBAR },
                                        label = { Text("Trailing Drawbar Toolbar", fontWeight = FontWeight.Bold) },
                                        leadingIcon = {
                                            if (implementType == ImplementType.TRAILING_TOOLBAR) {
                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = if (implementType == ImplementType.THREE_POINT_MOUNTED)
                                                "3-Point Hitch Mounted: Toolbar rigidly locks to 3-point lift arms. Swivels directly with tractor chassis rotation."
                                            else
                                                "Trailing Toolbar: Pivot articulates around drawbar hitch pin with trailing wheel trail dynamics.",
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                // Implement Working Width in FEET
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Implement Working Width:", fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "${String.format("%.1f", toolWidthFeet)} ft (${String.format("%.1f", toolWidthFeet * 12)} in / ${String.format("%.2f", toolWidthFeet * 0.3048)} m)",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Slider(
                                        value = toolWidthFeet.toFloat(),
                                        onValueChange = { toolWidthFeet = it.toDouble() },
                                        valueRange = 8f..90f,
                                        steps = 81
                                    )
                                    // Quick common imperial width presets
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf(15.0 to "15 ft (6-row)", 20.0 to "20 ft (8-row)", 30.0 to "30 ft (12-row)", 40.0 to "40 ft (16-row)", 60.0 to "60 ft (24-row)").forEach { (width, label) ->
                                            AssistChip(
                                                onClick = { toolWidthFeet = width },
                                                label = { Text(label, fontSize = 10.sp) }
                                            )
                                        }
                                    }
                                }

                                // Offset Behind Tractor in FEET / INCHES
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Offset Behind Tractor Hitch:", fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "${String.format("%.1f", offsetBehindTractorFeet)} ft (${String.format("%.0f", offsetBehindTractorFeet * 12)} inches)",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Text(
                                        text = "Distance from tractor rear 3-point lift arm hitch pin to center of implement toolbar.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Slider(
                                        value = offsetBehindTractorFeet.toFloat(),
                                        onValueChange = { offsetBehindTractorFeet = it.toDouble() },
                                        valueRange = 0f..25f
                                    )
                                }

                                // Number of Sections
                                Column {
                                    Text("Boom Section Count: $numSections Sections", fontWeight = FontWeight.Bold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf(1, 2, 4, 6, 8, 12, 16).forEach { count ->
                                            FilterChip(
                                                selected = numSections == count,
                                                onClick = { numSections = count },
                                                label = { Text("$count") }
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider()

                                Button(
                                    onClick = onResetCoverage,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Clear Infield Worked Coverage Map")
                                }
                            }
                        }

                        // ==========================================
                        // TAB 1: TRACTOR DIMENSIONS & MACHINE PROFILES
                        // ==========================================
                        1 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("Select Machine Profile Preset:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = {
                                            onLoadNewHolland8870Preset()
                                            wheelbaseInches = 112.0
                                            trackWidthInches = 82.0
                                            antennaHeightFeet = 10.5
                                            antennaOffsetInches = 8.0
                                            hitchLengthFeet = 4.0
                                            implementType = ImplementType.THREE_POINT_MOUNTED
                                            toolWidthFeet = 30.0
                                            offsetBehindTractorFeet = 3.0
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0072CE)),
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("NH 8870", fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color.White)
                                            Text("Genesis 210HP", fontSize = 9.5.sp, color = Color(0xFFFFF9C4), fontWeight = FontWeight.Medium)
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            wheelbaseInches = 120.0
                                            trackWidthInches = 88.0
                                            antennaHeightFeet = 11.2
                                            antennaOffsetInches = 12.0
                                            hitchLengthFeet = 4.5
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("JD 8R 310", fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color.White)
                                            Text("Row Crop", fontSize = 9.5.sp, color = Color(0xFFFFEB3B), fontWeight = FontWeight.Medium)
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            wheelbaseInches = 118.0
                                            trackWidthInches = 84.0
                                            antennaHeightFeet = 10.8
                                            antennaOffsetInches = 10.0
                                            hitchLengthFeet = 4.2
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Magnum 340", fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color.White)
                                            Text("Case IH", fontSize = 9.5.sp, color = Color(0xFFFFCDD2), fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }

                                // Wheelbase
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Wheelbase (Front to Rear Axle):", fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "${String.format("%.1f", wheelbaseInches)} in (${String.format("%.2f", wheelbaseInches / 12.0)} ft)",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Slider(
                                        value = wheelbaseInches.toFloat(),
                                        onValueChange = { wheelbaseInches = it.toDouble() },
                                        valueRange = 70f..180f
                                    )
                                }

                                // Track Width
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Track Width (Tire Center-to-Center):", fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "${String.format("%.1f", trackWidthInches)} in (${String.format("%.1f", trackWidthInches / 12.0)} ft)",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Slider(
                                        value = trackWidthInches.toFloat(),
                                        onValueChange = { trackWidthInches = it.toDouble() },
                                        valueRange = 60f..130f
                                    )
                                }

                                // Antenna Height
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Antenna Height (Cab Roof):", fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "${String.format("%.1f", antennaHeightFeet)} ft (${String.format("%.0f", antennaHeightFeet * 12)} in)",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Slider(
                                        value = antennaHeightFeet.toFloat(),
                                        onValueChange = { antennaHeightFeet = it.toDouble() },
                                        valueRange = 5f..16f
                                    )
                                }

                                // Antenna Forward Offset Ahead of Axle
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Antenna Offset Ahead of Rear Axle:", fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "${String.format("%.1f", antennaOffsetInches)} in",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Slider(
                                        value = antennaOffsetInches.toFloat(),
                                        onValueChange = { antennaOffsetInches = it.toDouble() },
                                        valueRange = -20f..60f
                                    )
                                }

                                // 3-Point Hitch Length Behind Axle
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("3-Point Hitch Pivot Behind Rear Axle:", fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "${String.format("%.1f", hitchLengthFeet)} ft (${String.format("%.0f", hitchLengthFeet * 12)} in)",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Slider(
                                        value = hitchLengthFeet.toFloat(),
                                        onValueChange = { hitchLengthFeet = it.toDouble() },
                                        valueRange = 2f..10f
                                    )
                                }

                                HorizontalDivider()

                                // Internal Android Tablet Mount 3D Offsets (X, Y, Z coordinates in ft & inches)
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.TabletAndroid, contentDescription = null, tint = Color(0xFF00E676))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text(
                                                "Internal Tablet Cab Mount 3D Offsets (WAAS / Internal GPS Auto-Steer)",
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF00E676)
                                            )
                                            Text(
                                                "Calibrates the tablet antenna position relative to the tractor rear axle & drawbar/3-point hitch.",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Y: Tablet Forward Distance ahead of rear axle
                                    val tabletFwdFt = tabletForwardOffsetInches / 12.0
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Y Forward (Ahead of Rear Axle):", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                            Text(
                                                text = "${String.format("%.1f", tabletFwdFt)} ft (${String.format("%.1f", tabletForwardOffsetInches)} in)",
                                                color = Color(0xFF81D4FA),
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 12.sp
                                            )
                                        }
                                        Slider(
                                            value = tabletForwardOffsetInches.toFloat(),
                                            onValueChange = { tabletForwardOffsetInches = it.toDouble() },
                                            valueRange = 0f..72f
                                        )
                                    }

                                    // X: Tablet Lateral Offset (Right/Left of centerline)
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            val dirStr = if (tabletRightOffsetInches > 0.5) "Right" else if (tabletRightOffsetInches < -0.5) "Left" else "Center"
                                            Text("X Lateral (Offset from Centerline):", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                            Text(
                                                text = "${String.format("%.1f", abs(tabletRightOffsetInches))} in $dirStr",
                                                color = Color(0xFF81D4FA),
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 12.sp
                                            )
                                        }
                                        Slider(
                                            value = tabletRightOffsetInches.toFloat(),
                                            onValueChange = { tabletRightOffsetInches = it.toDouble() },
                                            valueRange = -30f..30f
                                        )
                                    }

                                    // Z: Tablet Height above rear axle
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Z Height (Above Rear Axle):", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                            Text(
                                                text = "${String.format("%.1f", tabletHeightFeet)} ft (${String.format("%.0f", tabletHeightFeet * 12)} in)",
                                                color = Color(0xFF81D4FA),
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 12.sp
                                            )
                                        }
                                        Slider(
                                            value = tabletHeightFeet.toFloat(),
                                            onValueChange = { tabletHeightFeet = it.toDouble() },
                                            valueRange = 3f..10f
                                        )
                                    }
                                }
                            }
                        }

                        // ==========================================
                        // TAB 2: STEER VALVE & TRIMBLE RETROFIT TUNING
                        // ==========================================
                        2 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text(
                                    text = "Proportional Steering Valve Setup (Trimble Nav II / FM-750 Retrofit)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )

                                // Calibration & Auto-Tune Presets Card
                                Surface(
                                    color = Color(0xFF1B2922),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("QUICK CALIBRATION PRESETS", color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    steerKp = 65
                                                    steerKi = 10
                                                    steerKd = 20
                                                    minPwmDeadband = 30
                                                    maxPwmLimit = 225
                                                    countsPerDegree = 125
                                                    wasZeroOffsetDeg = 0.0
                                                },
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text("DANFOSS PVEA", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    steerKp = 75
                                                    steerKi = 14
                                                    steerKd = 28
                                                    minPwmDeadband = 45
                                                    maxPwmLimit = 240
                                                    countsPerDegree = 115
                                                },
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text("PWM VALVE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    steerKp = 45
                                                    steerKi = 5
                                                    steerKd = 12
                                                    minPwmDeadband = 25
                                                    maxPwmLimit = 210
                                                    countsPerDegree = 135
                                                },
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text("MOTOR DRIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                // Valve Type Selector
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        FilterChip(
                                            selected = valveType == SteerValveType.DUAL_SOLENOID_PWM,
                                            onClick = { valveType = SteerValveType.DUAL_SOLENOID_PWM },
                                            label = { Text("Dual-Solenoid PWM (Trimble/Cytron)") },
                                            modifier = Modifier.weight(1f)
                                        )
                                        FilterChip(
                                            selected = valveType == SteerValveType.PROPORTIONAL_DANFOSS_PVEA,
                                            onClick = { valveType = SteerValveType.PROPORTIONAL_DANFOSS_PVEA },
                                            label = { Text("Danfoss PVEA / PVG") },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        FilterChip(
                                            selected = valveType == SteerValveType.PROPORTIONAL_PWM_VALVE,
                                            onClick = { valveType = SteerValveType.PROPORTIONAL_PWM_VALVE },
                                            label = { Text("Standard PWM Valve") },
                                            modifier = Modifier.weight(1f)
                                        )
                                        FilterChip(
                                            selected = valveType == SteerValveType.MOTOR_CYTRON || valveType == SteerValveType.MOTOR_IBT2,
                                            onClick = { valveType = SteerValveType.MOTOR_CYTRON },
                                            label = { Text("Electric Motor (Cytron/IBT-2)") },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                // Proportional P Gain (Kp)
                                Column {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Steer Kp (Proportional Gain):", fontWeight = FontWeight.Bold)
                                        Text("$steerKp", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                                    }
                                    Slider(
                                        value = steerKp.toFloat(),
                                        onValueChange = { steerKp = it.roundToInt() },
                                        valueRange = 5f..90f
                                    )
                                }

                                // Integral I Gain (Ki)
                                Column {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Steer Ki (Integral Gain):", fontWeight = FontWeight.Bold)
                                        Text("$steerKi", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                                    }
                                    Slider(
                                        value = steerKi.toFloat(),
                                        onValueChange = { steerKi = it.roundToInt() },
                                        valueRange = 0f..30f
                                    )
                                }

                                // Derivative D Gain (Kd)
                                Column {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Steer Kd (Derivative Gain):", fontWeight = FontWeight.Bold)
                                        Text("$steerKd", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                                    }
                                    Slider(
                                        value = steerKd.toFloat(),
                                        onValueChange = { steerKd = it.roundToInt() },
                                        valueRange = 0f..60f
                                    )
                                }

                                // Minimum PWM Deadband
                                Column {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Minimum PWM Deadband (Overcomes Valve Spool Friction):", fontWeight = FontWeight.Bold)
                                        Text("$minPwmDeadband", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                                    }
                                    Slider(
                                        value = minPwmDeadband.toFloat(),
                                        onValueChange = { minPwmDeadband = it.roundToInt() },
                                        valueRange = 5f..60f
                                    )
                                }

                                // Maximum PWM Limit
                                Column {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Maximum PWM Valve Limit:", fontWeight = FontWeight.Bold)
                                        Text("$maxPwmLimit", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                                    }
                                    Slider(
                                        value = maxPwmLimit.toFloat(),
                                        onValueChange = { maxPwmLimit = it.roundToInt() },
                                        valueRange = 150f..255f
                                    )
                                }

                                // Wheel Angle Sensor (WAS) Calibration & Zero Offset
                                Column {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("WAS Counts Per Degree:", fontWeight = FontWeight.Bold)
                                        Text("$countsPerDegree cts/°", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                                    }
                                    Slider(
                                        value = countsPerDegree.toFloat(),
                                        onValueChange = { countsPerDegree = it.roundToInt() },
                                        valueRange = 30f..250f
                                    )
                                }

                                Column {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("WAS Center Zero Offset:", fontWeight = FontWeight.Bold)
                                        Text("${String.format("%.2f", wasZeroOffsetDeg)}°", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Slider(
                                            value = wasZeroOffsetDeg.toFloat(),
                                            onValueChange = { wasZeroOffsetDeg = it.toDouble() },
                                            valueRange = -15f..15f,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Button(
                                            onClick = { wasZeroOffsetDeg = 0.0 },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF455A64))
                                        ) {
                                            Text("ZERO", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                // Inversions
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    FilterChip(
                                        selected = isWasInverted,
                                        onClick = { isWasInverted = !isWasInverted },
                                        label = { Text("Invert WAS Sensor Direction") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = isMotorInverted,
                                        onClick = { isMotorInverted = !isMotorInverted },
                                        label = { Text("Invert Valve Flow / Motor") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // ==========================================
                        // TAB 3: AIO BOARD USB & GNSS RECEIVER (TEENSY 4.1 + UM982 / F9P)
                        // ==========================================
                        3 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("AgSteer All-In-One (AIO) Hardware Interface", fontWeight = FontWeight.Bold)

                                // USB OTG Hardware Status Card
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isUsbConnected) Color(0xFF1B5E20) else Color(0xFF263238)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Usb,
                                                    contentDescription = null,
                                                    tint = if (isUsbConnected) Color(0xFF00E676) else Color(0xFFB0BEC5)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = if (isUsbConnected) "USB Connected: ${usbStats.deviceName}" else "USB AIO Board: Disconnected",
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }

                                            if (isUsbConnected) {
                                                Button(
                                                    onClick = onDisconnectUsb,
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Disconnect")
                                                }
                                            } else {
                                                Button(
                                                    onClick = onConnectUsb,
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Connect USB", color = Color.Black, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Packets: In ${usbStats.rxPacketsCount} | Out ${usbStats.txPacketsCount} | Bytes: ${usbStats.rxBytesTotal} | Steer PGN 253: ${usbStats.pgn253ReceivedCount}",
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color.White.copy(alpha = 0.85f)
                                        )
                                        if (usbStats.lastSentence.isNotEmpty()) {
                                            Text(
                                                text = "Latest: ${usbStats.lastSentence}",
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = Color(0xFF81D4FA)
                                            )
                                        }
                                    }
                                }

                                // Mock Hardware Bench Loopback Card
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isMockLoopbackActive) Color(0xFF1B5E20) else Color(0xFF263238)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.Memory,
                                                    contentDescription = null,
                                                    tint = if (isMockLoopbackActive) Color(0xFF00E676) else Color(0xFFB0BEC5)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Bench Simulator: Teensy AIO Hardware Loopback",
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                            Text(
                                                text = "Emulates Teensy 4.1 autosteer controller internally over UDP port 8888/9999. Lets you bench-test PGN 254/253 on tablet before connecting physical hardware.",
                                                fontSize = 11.sp,
                                                color = Color(0xFFCFD8DC)
                                            )
                                        }
                                        Switch(
                                            checked = isMockLoopbackActive,
                                            onCheckedChange = { onToggleMockLoopback() }
                                        )
                                    }
                                }

                                Text("GNSS Dual-Antenna Receiver Module Profile", fontWeight = FontWeight.Bold)

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // UM982 Option
                                    Card(
                                        onClick = {
                                            receiverType = GnssReceiverType.UNICORE_UM982
                                            onSetGnssReceiverType(GnssReceiverType.UNICORE_UM982)
                                        },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (receiverType == GnssReceiverType.UNICORE_UM982) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(
                                                selected = receiverType == GnssReceiverType.UNICORE_UM982,
                                                onClick = {
                                                    receiverType = GnssReceiverType.UNICORE_UM982
                                                    onSetGnssReceiverType(GnssReceiverType.UNICORE_UM982)
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Unicore UM982 Dual-Antenna RTK (Recommended)", fontWeight = FontWeight.Bold)
                                                Text("High-speed dual heading & roll via \$KSXT sentence. Instant heading without tractor movement.", fontSize = 11.sp)
                                            }
                                            Button(
                                                onClick = onOpenUm982Dialog,
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00695C)),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Tools", fontSize = 11.sp)
                                            }
                                        }
                                    }

                                    // F9P Dual Option
                                    Card(
                                        onClick = {
                                            receiverType = GnssReceiverType.UBLOX_F9P
                                            onSetGnssReceiverType(GnssReceiverType.UBLOX_F9P)
                                        },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (receiverType == GnssReceiverType.UBLOX_F9P) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(
                                                selected = receiverType == GnssReceiverType.UBLOX_F9P,
                                                onClick = {
                                                    receiverType = GnssReceiverType.UBLOX_F9P
                                                    onSetGnssReceiverType(GnssReceiverType.UBLOX_F9P)
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text("u-blox ZED-F9P Dual Antenna Heading", fontWeight = FontWeight.Bold)
                                                Text("RelPosNED / \$GNHPR dual heading & roll with moving baseline.", fontSize = 11.sp)
                                            }
                                        }
                                    }

                                    // Auto-Detect
                                    Card(
                                        onClick = {
                                            receiverType = GnssReceiverType.AUTO_DETECT
                                            onSetGnssReceiverType(GnssReceiverType.AUTO_DETECT)
                                        },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (receiverType == GnssReceiverType.AUTO_DETECT) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(
                                                selected = receiverType == GnssReceiverType.AUTO_DETECT,
                                                onClick = {
                                                    receiverType = GnssReceiverType.AUTO_DETECT
                                                    onSetGnssReceiverType(GnssReceiverType.AUTO_DETECT)
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text("Auto-Detect Protocol (Standard NMEA / GGA / VTG / HDT)", fontWeight = FontWeight.Bold)
                                                Text("Automatically adapts to incoming sentence format.", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }

                                Text("Active Position Input Source", fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    FilterChip(
                                        selected = gnssSource == GnssSourceMode.ANDROID_GPS,
                                        onClick = { onSelectGnssSource(GnssSourceMode.ANDROID_GPS) },
                                        label = { Text("Tablet GNSS") },
                                        leadingIcon = {
                                            Icon(Icons.Default.TabletAndroid, contentDescription = null, modifier = Modifier.size(16.dp))
                                        },
                                        modifier = Modifier.weight(1.2f)
                                    )
                                    FilterChip(
                                        selected = gnssSource == GnssSourceMode.USB_AIO_TEENSY,
                                        onClick = { onSelectGnssSource(GnssSourceMode.USB_AIO_TEENSY) },
                                        label = { Text("Teensy USB AIO") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = gnssSource == GnssSourceMode.UDP_NETWORK,
                                        onClick = { onSelectGnssSource(GnssSourceMode.UDP_NETWORK) },
                                        label = { Text("UDP AgIO") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = gnssSource == GnssSourceMode.SIMULATOR,
                                        onClick = { onSelectGnssSource(GnssSourceMode.SIMULATOR) },
                                        label = { Text("Simulator") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                // Geographic Coordinates & Field Origin Center
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFF38BDF8))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Farm Field Origin & Coordinates", fontWeight = FontWeight.Bold)
                                            }
                                            Button(
                                                onClick = onRecenterToTabletGps,
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Icon(Icons.Default.MyLocation, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Detect My GPS", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }
                                        }

                                        Text(
                                            text = "Current Origin: ${String.format(java.util.Locale.US, "%.6f°, %.6f°", currentGeoPosition.latitude, currentGeoPosition.longitude)} (Alt: ${String.format(java.util.Locale.US, "%.1fm / %.0fft", currentGeoPosition.altitude, currentGeoPosition.altitude * 3.28084)})",
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8)
                                        )

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            OutlinedTextField(
                                                value = inputLat,
                                                onValueChange = { inputLat = it },
                                                label = { Text("Latitude") },
                                                modifier = Modifier.weight(1f),
                                                singleLine = true
                                            )
                                            OutlinedTextField(
                                                value = inputLon,
                                                onValueChange = { inputLon = it },
                                                label = { Text("Longitude") },
                                                modifier = Modifier.weight(1f),
                                                singleLine = true
                                            )
                                        }

                                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                            Button(
                                                onClick = {
                                                    val latVal = inputLat.toDoubleOrNull() ?: currentGeoPosition.latitude
                                                    val lonVal = inputLon.toDoubleOrNull() ?: currentGeoPosition.longitude
                                                    val altVal = inputAlt.toDoubleOrNull() ?: currentGeoPosition.altitude
                                                    onSetManualLocation(latVal, lonVal, altVal, "Custom Coordinates Field")
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8))
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Set Field Location", color = Color.Black, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ==========================================
                        // TAB 4: STEERING TUNING (STANLEY & PURE PURSUIT)
                        // ==========================================
                        4 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("Steering Controller Algorithm", fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = controllerType == ControllerType.STANLEY,
                                        onClick = { controllerType = ControllerType.STANLEY },
                                        label = { Text("Stanley (Recommended)") }
                                    )
                                    FilterChip(
                                        selected = controllerType == ControllerType.PURE_PURSUIT,
                                        onClick = { controllerType = ControllerType.PURE_PURSUIT },
                                        label = { Text("Pure Pursuit") }
                                    )
                                }

                                if (controllerType == ControllerType.STANLEY) {
                                    Text("Stanley Proportional Gain k: ${String.format("%.2f", stanleyGain)}")
                                    Slider(
                                        value = stanleyGain.toFloat(),
                                        onValueChange = { stanleyGain = it.toDouble() },
                                        valueRange = 0.5f..3.0f
                                    )

                                    Text("Stanley Integral Gain: ${String.format("%.2f", stanleyIntegral)}")
                                    Slider(
                                        value = stanleyIntegral.toFloat(),
                                        onValueChange = { stanleyIntegral = it.toDouble() },
                                        valueRange = 0.0f..0.3f
                                    )
                                } else {
                                    Text("Lookahead Time Factor: ${String.format("%.2f", lookaheadGain)} s")
                                    Slider(
                                        value = lookaheadGain.toFloat(),
                                        onValueChange = { lookaheadGain = it.toDouble() },
                                        valueRange = 0.4f..2.5f
                                    )
                                }

                                Text("Cross-Track Deadband: ${String.format("%.1f", deadbandInches)} in (${String.format("%.1f", deadbandInches * 2.54)} cm)")
                                Slider(
                                    value = deadbandInches.toFloat(),
                                    onValueChange = { deadbandInches = it.toDouble() },
                                    valueRange = 0.2f..3.0f
                                )

                                Surface(
                                    color = Color(0xFF1B2922),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "TABLET INTERNAL GNSS LOOKAHEAD ADAPTATION",
                                            color = Color(0xFF81D4FA),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = "Tablet GNSS Lookahead Time: ${String.format("%.1f", tabletGpsLookahead)} s (Auto-locks Pure Pursuit)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Slider(
                                            value = tabletGpsLookahead.toFloat(),
                                            onValueChange = { tabletGpsLookahead = it.toDouble() },
                                            valueRange = 1.5f..5.0f
                                        )
                                        Text(
                                            text = "Compensates for tablet GNSS/WAAS update latency by extending preview horizon.",
                                            fontSize = 10.sp,
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        onUpdateGuidanceSettings(
                                            guidanceSettings.copy(rollZeroOffsetDeg = currentRoll)
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Zero Roll Sensor (Current: ${String.format("%+.2f°", currentRoll)})")
                                }

                                // ========================================================
                                // HILLSIDE TOPOGRAPHY & 3D TERRAIN COMPENSATION (0.5 MILE DEM)
                                // ========================================================
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isTerrainCompActive) Color(0xFF1B3B2B) else Color(0xFF263238)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.Terrain,
                                                    contentDescription = null,
                                                    tint = if (isTerrainCompActive) Color(0xFF00E676) else Color(0xFFB0BEC5)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    "0.5-MILE TOPOGRAPHY & 3D TERRAIN",
                                                    fontWeight = FontWeight.Black,
                                                    color = Color.White,
                                                    fontSize = 12.sp
                                                )
                                            }
                                            Switch(
                                                checked = isTerrainCompActive,
                                                onCheckedChange = {
                                                    isTerrainCompActive = it
                                                    onToggleTerrainCompensation(it)
                                                }
                                            )
                                        }

                                        Text(
                                            text = "Dynamically generates and loads a 0.5 mile × 0.5 mile (160-acre quarter section) topographic DEM grid centered at your current GPS coordinates. Accurately compensates autosteer for downhill vehicle slide and side-draft.",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.85f)
                                        )

                                        HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

                                        // Sub-Toggles: Topo Map & 3D Mesh
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            FilterChip(
                                                selected = isTopoMapActive,
                                                onClick = {
                                                    val next = !isTopoMapActive
                                                    isTopoMapActive = next
                                                    onToggleTopographyMap(next)
                                                },
                                                label = { Text("0.5mi Topo Contours (2D)") },
                                                leadingIcon = {
                                                    if (isTopoMapActive) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                            FilterChip(
                                                selected = is3DTerrainActive,
                                                onClick = {
                                                    val next = !is3DTerrainActive
                                                    is3DTerrainActive = next
                                                    onToggle3DTerrainMesh(next)
                                                },
                                                label = { Text("3D Elevation Mesh (Cab)") },
                                                leadingIcon = {
                                                    if (is3DTerrainActive) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                        // Side-Draft Uphill Steer Gain Slider
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Side-Draft Uphill Gain:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.White)
                                                Text(
                                                    text = "${String.format("%.2f", terrainSideDraftGain)}° steer / ° roll",
                                                    color = Color(0xFF00E676),
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 12.sp
                                                )
                                            }
                                            Slider(
                                                value = terrainSideDraftGain.toFloat(),
                                                onValueChange = { terrainSideDraftGain = it.toDouble() },
                                                valueRange = 0.0f..1.0f,
                                                enabled = isTerrainCompActive
                                            )
                                            Text(
                                                text = "Feedforward counter-steer angle applied uphill to maintain tractor on the AB line across side-hills.",
                                                fontSize = 10.sp,
                                                color = Color.White.copy(alpha = 0.7f)
                                            )
                                        }

                                        // Quick Master Switches: Emergency Reset & Activate All
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    isTerrainCompActive = false
                                                    isTopoMapActive = false
                                                    is3DTerrainActive = false
                                                    onResetToBasicGps()
                                                },
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF8A80)),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Reset to Basic GPS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }

                                            Button(
                                                onClick = {
                                                    isTerrainCompActive = true
                                                    isTopoMapActive = true
                                                    is3DTerrainActive = true
                                                    onActivateAllTerrain()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.Terrain, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Activate All Topo", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ==========================================
                        // TAB 5: GUIDED HARDWARE & IN-CAB SETUP WIZARD
                        // ==========================================
                        5 -> {
                            var wizardStep by remember { mutableStateOf(1) }

                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Surface(
                                    color = Color(0xFF1B2922),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                "AGSTEER ALL-IN-ONE (AIO) COMMISSIONING WIZARD",
                                                fontWeight = FontWeight.Black,
                                                color = Color(0xFF00E676),
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                "Step $wizardStep of 4: " + when (wizardStep) {
                                                    1 -> "Tractor Profile & Geometry"
                                                    2 -> "Teensy 4.1 USB / AgIO Connection"
                                                    3 -> "WAS & Valve Deadband Tuning"
                                                    else -> "GNSS Dual Antenna RTK Verification"
                                                },
                                                fontSize = 11.sp,
                                                color = Color.White.copy(alpha = 0.8f)
                                            )
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            (1..4).forEach { stepNum ->
                                                Surface(
                                                    shape = CircleShape,
                                                    color = if (stepNum == wizardStep) Color(0xFF00E676) else if (stepNum < wizardStep) Color(0xFF2E7D32) else Color(0xFF424242),
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text("$stepNum", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (stepNum == wizardStep) Color.Black else Color.White)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                when (wizardStep) {
                                    1 -> {
                                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Text("Step 1: Choose Tractor Preset & Enter Dimensions", fontWeight = FontWeight.Bold)
                                                Text("Select your tractor model below to auto-fill factory wheelbase, track width, and hitch geometry:", fontSize = 12.sp)

                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                                    Button(
                                                        onClick = {
                                                            onLoadNewHolland8870Preset()
                                                            wheelbaseInches = 112.0
                                                            trackWidthInches = 82.0
                                                            antennaHeightFeet = 10.5
                                                            antennaOffsetInches = 8.0
                                                            hitchLengthFeet = 4.0
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0038A8)),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text("NH 8870 (112\")", fontSize = 11.sp)
                                                    }
                                                    Button(
                                                        onClick = {
                                                            wheelbaseInches = 120.0
                                                            trackWidthInches = 88.0
                                                            antennaHeightFeet = 11.2
                                                            antennaOffsetInches = 12.0
                                                            hitchLengthFeet = 4.5
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text("JD 8R (120\")", fontSize = 11.sp)
                                                    }
                                                    Button(
                                                        onClick = {
                                                            wheelbaseInches = 118.0
                                                            trackWidthInches = 84.0
                                                            antennaHeightFeet = 10.8
                                                            antennaOffsetInches = 10.0
                                                            hitchLengthFeet = 4.2
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text("Magnum (118\")", fontSize = 11.sp)
                                                    }
                                                }

                                                Text("Current Wheelbase: ${String.format("%.1f", wheelbaseInches)} in | Track Width: ${String.format("%.1f", trackWidthInches)} in", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                    2 -> {
                                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Text("Step 2: Plug Tablet USB OTG into Teensy 4.1 AIO Board", fontWeight = FontWeight.Bold)
                                                Text("Plug a USB-C OTG cable from this Android tablet into the Teensy 4.1 USB port on your AgOpenGPS All-In-One board. Then press 'Scan & Connect USB'.", fontSize = 12.sp)

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column {
                                                        Text("USB State: ${usbStats.connectionState}", fontWeight = FontWeight.Bold, color = if (usbStats.connectionState == UsbConnectionState.CONNECTED) Color(0xFF00E676) else Color(0xFFFFB74D))
                                                        Text("Device: ${usbStats.deviceName} (${usbStats.hardwareModel})", fontSize = 11.sp)
                                                    }
                                                    Button(
                                                        onClick = onConnectUsb,
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                                                    ) {
                                                        Icon(Icons.Default.Usb, contentDescription = null)
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Scan USB")
                                                    }
                                                }

                                                if (usbStats.connectionState != UsbConnectionState.CONNECTED) {
                                                    Surface(color = Color(0xFF263238), shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                                                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF81D4FA), modifier = Modifier.size(16.dp))
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text("Bench Testing tip: You can also use the integrated Teensy 4.1 Mock Loopback in the AIO USB & GNSS tab.", fontSize = 10.sp)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    3 -> {
                                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Text("Step 3: Wheel Angle Sensor (WAS) & Valve Tuning", fontWeight = FontWeight.Bold)
                                                Text("Center your front wheels straight ahead. If the wheel angle reads non-zero, tap 'Zero WAS'. Adjust counts-per-degree until front wheel degrees match actual steering angle.", fontSize = 12.sp)

                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                                    OutlinedButton(
                                                        onClick = { wasZeroOffsetDeg = 0.0 },
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text("Zero WAS Offset")
                                                    }
                                                    OutlinedButton(
                                                        onClick = { isWasInverted = !isWasInverted },
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text(if (isWasInverted) "Invert WAS: YES" else "Invert WAS: NO")
                                                    }
                                                }

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("Valve Deadband (crack-open):", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                    Text("PWM $minPwmDeadband", fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                                }
                                                Slider(
                                                    value = minPwmDeadband.toFloat(),
                                                    onValueChange = { minPwmDeadband = it.toInt() },
                                                    valueRange = 5f..80f
                                                )
                                            }
                                        }
                                    }
                                    else -> {
                                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Text("Step 4: Dual-Antenna GNSS & RTK Verification", fontWeight = FontWeight.Bold)
                                                Text("Ensure the Unicore UM982 or F9P receiver is receiving RTCM3 corrections from your local NTRIP caster / base station. Check antenna baseline length matches physical roof separation (e.g. 0.85m to 1.25m).", fontSize = 12.sp)

                                                Surface(color = Color(0xFF1B2922), shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Text("COMMISSIONING COMPLETE CHECKLIST:", color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                        Text("✓ Vehicle wheelbase & antenna offsets calibrated", fontSize = 10.sp)
                                                        Text("✓ Teensy 4.1 AIO steer controller communicating (PGN 254/253)", fontSize = 10.sp)
                                                        Text("✓ WAS zeroed & valve deadband tuned", fontSize = 10.sp)
                                                        Text("✓ End-of-pass audible turn chime active", fontSize = 10.sp)
                                                    }
                                                }

                                                Button(
                                                    onClick = onOpenUm982Dialog,
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D47A1)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(Icons.Default.GpsFixed, contentDescription = null)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Launch UM982 Diagnostics Tool")
                                                }
                                            }
                                        }
                                    }
                                }

                                // Wizard Next / Prev Controls
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    OutlinedButton(
                                        onClick = { if (wizardStep > 1) wizardStep-- },
                                        enabled = wizardStep > 1
                                    ) {
                                        Icon(Icons.Default.ChevronLeft, contentDescription = null)
                                        Text("Previous Step")
                                    }

                                    if (wizardStep < 4) {
                                        Button(
                                            onClick = { wizardStep++ },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                                        ) {
                                            Text("Next Step", color = Color.Black, fontWeight = FontWeight.Bold)
                                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Black)
                                        }
                                    } else {
                                        Button(
                                            onClick = { selectedTab = 0 },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Finish Wizard", color = Color.Black, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Action Buttons
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
                            // Apply guidance settings
                            onUpdateGuidanceSettings(
                                guidanceSettings.copy(
                                    controllerType = controllerType,
                                    stanleyGain = stanleyGain,
                                    stanleyIntegralGain = stanleyIntegral,
                                    deadbandCm = deadbandInches * 2.54,
                                    purePursuitLookaheadGain = lookaheadGain,
                                    tabletGpsLookaheadSec = tabletGpsLookahead,
                                    terrainSideDraftGain = terrainSideDraftGain
                                )
                            )
                            if (isTerrainCompActive != isTerrainCompensationEnabled) {
                                onToggleTerrainCompensation(isTerrainCompActive)
                            }
                            if (isTopoMapActive != showTopographyMap) {
                                onToggleTopographyMap(isTopoMapActive)
                            }
                            if (is3DTerrainActive != show3DTerrainMesh) {
                                onToggle3DTerrainMesh(is3DTerrainActive)
                            }
                            // Apply vehicle configuration (convert inches/feet back to meters)
                            onUpdateVehicleConfig(
                                vehicleConfig.copy(
                                    wheelbase = wheelbaseInches * 0.0254,
                                    trackWidth = trackWidthInches * 0.0254,
                                    antennaHeight = antennaHeightFeet * 0.3048,
                                    antennaPivotOffset = antennaOffsetInches * 0.0254,
                                    hitchLength = -hitchLengthFeet * 0.3048,
                                    tabletForwardOffset = tabletForwardOffsetInches * 0.0254,
                                    tabletRightOffset = tabletRightOffsetInches * 0.0254,
                                    tabletHeightAboveAxle = tabletHeightFeet * 0.3048
                                )
                            )
                            // Apply implement configuration (convert feet back to meters)
                            onUpdateImplementConfig(
                                implementConfig.copy(
                                    implementType = implementType,
                                    toolWidth = toolWidthFeet * 0.3048,
                                    offsetBehindTractor = offsetBehindTractorFeet * 0.3048,
                                    numSections = numSections
                                )
                            )
                            // Apply steer valve configuration
                            onUpdateSteerValveConfig(
                                steerValveConfig.copy(
                                    valveType = valveType,
                                    proportionalGainKp = steerKp,
                                    integralGainKi = steerKi,
                                    derivativeGainKd = steerKd,
                                    minPwmDeadband = minPwmDeadband,
                                    maxPwmLimit = maxPwmLimit,
                                    wasCountsPerDeg = countsPerDegree,
                                    wasZeroOffsetDeg = wasZeroOffsetDeg,
                                    isWasInverted = isWasInverted,
                                    isMotorInverted = isMotorInverted
                                )
                            )
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                    ) {
                        Text("Save & Apply Parameters", color = Color.Black, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

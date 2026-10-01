package com.example.agopengps.ui

import androidx.compose.animation.*
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
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.agopengps.navigation.ImplementConfig
import com.example.agopengps.navigation.ImplementPreset
import com.example.agopengps.navigation.ImplementType
import com.example.agopengps.navigation.Vec2
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

enum class WizardBoundaryMode(val title: String, val subtitle: String, val icon: ImageVector) {
    FORM_FROM_AB_LINE("Form Boundary from AB Line", "Tractor at baseline corner; boundary extends along AB line and outward", Icons.Default.AspectRatio),
    DRIVE_PERIMETER("Drive Perimeter Live", "Start recording GPS boundary points as you drive the headland pass around irregular edges", Icons.Default.DirectionsCar),
    QUARTER_SECTION_160("160-Acre Quarter (From Tractor Corner)", "Generate 1/2 mile × 1/2 mile section starting at vehicle corner", Icons.Default.CropFree),
    EIGHTY_ACRES("80-Acre Half-Quarter (From Tractor Corner)", "Generate 1/2 mile × 1/4 mile boundary starting at vehicle corner", Icons.Default.GridView),
    SET_4_CORNERS("Set 4 Outer Corners", "Capture 4 corners (A, B, C, D) around perimeter edges", Icons.Default.PinDrop),
    NO_BOUNDARY("Open Field (Pass Tracking Only)", "Proceed with swath guidance only; boundary formed by worked passes", Icons.Default.LayersClear)
}

enum class WizardGuidanceMode(val title: String, val subtitle: String, val icon: ImageVector) {
    SET_AB_DRIVE("Straight A-B Line (Drive)", "Point A sets now at vehicle; drive 50+ ft and press 'Set Point B'", Icons.Default.Timeline),
    A_PLUS_HEADING("A+ Heading Line (Instant)", "Point A sets now + lock in chosen compass heading (090° E-W, 000° N-S)", Icons.Default.Explore),
    ADAPTIVE_CURVE("Adaptive Curve Recording", "Record dynamic contour swaths along waterways or terraces", Icons.Default.Gesture),
    COPY_EXISTING("Parallel to Section Road", "Align swath exactly parallel to 176th Street / East-West baseline", Icons.AutoMirrored.Filled.AltRoute)
}

enum class WizardImplementMode {
    PRESETS,
    CUSTOM_BUILDER
}

@Composable
fun FieldSetupWizardDialog(
    currentTractorHeading: Double,
    currentTractorPos: Vec2,
    implementPresets: List<ImplementPreset>,
    currentImplementConfig: ImplementConfig = ImplementConfig(),
    onLaunchFieldJob: (
        fieldName: String,
        farmName: String,
        cropType: String,
        selectedPreset: ImplementPreset?,
        customImplementConfig: ImplementConfig?,
        boundaryMode: WizardBoundaryMode,
        guidanceMode: WizardGuidanceMode,
        aPlusHeadingDeg: Double
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var step by remember { mutableIntStateOf(1) }

    // Step 1: Identity State
    var farmName by remember { mutableStateOf("Lac qui Parle Farm") }
    var fieldName by remember { mutableStateOf("Field ${java.text.SimpleDateFormat("MM/dd", Locale.US).format(java.util.Date())}") }
    var cropType by remember { mutableStateOf("Sugar Beets") }

    // Implement Setup State in Wizard
    var implementMode by remember { mutableStateOf(WizardImplementMode.PRESETS) }
    var selectedPresetId by remember {
        mutableStateOf(implementPresets.firstOrNull { it.id == "planter_12r22" }?.id ?: implementPresets.firstOrNull()?.id ?: "")
    }
    var presetCategoryFilter by remember { mutableStateOf("All") }

    // Custom Implement Builder State
    var customToolWidthFeet by remember {
        mutableStateOf(String.format(Locale.US, "%.1f", currentImplementConfig.toolWidth * 3.28084))
    }
    var customSections by remember { mutableStateOf(currentImplementConfig.numSections.coerceIn(1, 48)) }
    var customOverlapInches by remember {
        mutableStateOf(String.format(Locale.US, "%.1f", currentImplementConfig.overlap * 39.3701))
    }
    var customHitchFeet by remember {
        mutableStateOf(String.format(Locale.US, "%.1f", currentImplementConfig.offsetBehindTractor * 3.28084))
    }
    var customImplementType by remember { mutableStateOf(currentImplementConfig.implementType) }
    var customAutoSectionControl by remember { mutableStateOf(currentImplementConfig.isAutoSectionControl) }
    var customLateralOffsetInches by remember {
        mutableStateOf(String.format(Locale.US, "%.1f", currentImplementConfig.lateralOffset * 39.3701))
    }

    // Row Calculator Helpers
    var selectedRowSpacingInches by remember { mutableDoubleStateOf(22.0) } // Default 22 inch rows
    var selectedRowCount by remember { mutableIntStateOf(12) }              // Default 12 rows

    // Step 2 State
    var selectedBoundaryMode by remember { mutableStateOf(WizardBoundaryMode.DRIVE_PERIMETER) }

    // Step 3 State
    var selectedGuidanceMode by remember { mutableStateOf(WizardGuidanceMode.SET_AB_DRIVE) }
    var aPlusHeadingDeg by remember { mutableStateOf(currentTractorHeading) }

    val crops = listOf("Sugar Beets", "Corn", "Soybeans", "Wheat", "Tillage", "Chemical Spray", "Grain Hauling")
    val presetCategories = listOf("All", "Planting & Seeding", "Harvest", "Tillage", "Spraying & Chemical", "Harvest & Hauling")

    fun applyRowCalculation(rows: Int, spacingInches: Double) {
        val totalInches = rows * spacingInches
        val feet = totalInches / 12.0
        customToolWidthFeet = String.format(Locale.US, "%.2f", feet)
        customSections = rows.coerceIn(1, 48)
    }

    fun loadPresetIntoCustomBuilder(preset: ImplementPreset) {
        customToolWidthFeet = String.format(Locale.US, "%.2f", preset.toolWidthFeet)
        customSections = preset.numSections
        customOverlapInches = String.format(Locale.US, "%.1f", preset.overlapMeters * 39.3701)
        customHitchFeet = String.format(Locale.US, "%.1f", preset.offsetBehindTractorMeters * 3.28084)
        customImplementType = preset.implementType
        customAutoSectionControl = preset.isAutoSectionControl
        implementMode = WizardImplementMode.CUSTOM_BUILDER
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101914)),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.94f)
                .padding(4.dp)
                .testTag("field_setup_wizard_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header & Stepper
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E676)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color.Black)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Field Forming & Guidance Setup Wizard",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 17.sp
                            )
                            Text(
                                text = "Step $step of 3: " + when (step) {
                                    1 -> "Client, Farm & Implement Profile Setup"
                                    2 -> "Boundary & Perimeter Strategy"
                                    else -> "AB Swath Line & Guidance Mode"
                                },
                                color = Color(0xFF81C784),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                // Step Indicator Progress Bar
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (s in 1..3) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (s <= step) Color(0xFF00E676) else Color(0xFF243329))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Step Content
                Box(modifier = Modifier.weight(1f)) {
                    when (step) {
                        1 -> {
                            // Step 1: Field Identity & Implement Setup
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // 1. Client & Field Info Card
                                Surface(
                                    color = Color(0xFF17241D),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("1. CLIENT, FARM & FIELD IDENTITY", color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 12.5.sp)

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            OutlinedTextField(
                                                value = farmName,
                                                onValueChange = { farmName = it },
                                                label = { Text("Farm / Grower Name") },
                                                modifier = Modifier.weight(1f),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White,
                                                    focusedBorderColor = Color(0xFF00E676)
                                                )
                                            )
                                            OutlinedTextField(
                                                value = fieldName,
                                                onValueChange = { fieldName = it },
                                                label = { Text("Field / Parcel Name") },
                                                modifier = Modifier.weight(1.2f),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White,
                                                    focusedBorderColor = Color(0xFF00E676)
                                                )
                                            )
                                        }

                                        Text("Crop / Task Operation:", color = Color.LightGray, fontSize = 11.5.sp)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            crops.forEach { c ->
                                                val sel = c == cropType
                                                Surface(
                                                    color = if (sel) Color(0xFF00E676) else Color(0xFF223429),
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.clickable { cropType = c }
                                                ) {
                                                    Text(
                                                        text = c,
                                                        color = if (sel) Color.Black else Color.White,
                                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                                        fontSize = 11.sp,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // 2. Attached Implement Profile Card
                                Surface(
                                    color = Color(0xFF17241D),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "2. ATTACHED IMPLEMENT SETUP",
                                                color = Color(0xFF00E676),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.5.sp
                                            )

                                            // Mode Selector: Presets vs Custom
                                            Row(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFF101914))
                                                    .padding(2.dp),
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Surface(
                                                    color = if (implementMode == WizardImplementMode.PRESETS) Color(0xFF00E676) else Color.Transparent,
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier.clickable { implementMode = WizardImplementMode.PRESETS }
                                                ) {
                                                    Text(
                                                        text = "PRESET LIBRARY",
                                                        color = if (implementMode == WizardImplementMode.PRESETS) Color.Black else Color.LightGray,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                                Surface(
                                                    color = if (implementMode == WizardImplementMode.CUSTOM_BUILDER) Color(0xFF00E676) else Color.Transparent,
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier.clickable { implementMode = WizardImplementMode.CUSTOM_BUILDER }
                                                ) {
                                                    Text(
                                                        text = "CUSTOM IMPLEMENT BUILDER",
                                                        color = if (implementMode == WizardImplementMode.CUSTOM_BUILDER) Color.Black else Color.LightGray,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }

                                        if (implementMode == WizardImplementMode.PRESETS) {
                                            // Category Filter Chips
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                presetCategories.forEach { cat ->
                                                    val sel = cat == presetCategoryFilter
                                                    Surface(
                                                        color = if (sel) Color(0xFF2E7D32) else Color(0xFF142018),
                                                        shape = RoundedCornerShape(10.dp),
                                                        modifier = Modifier.clickable { presetCategoryFilter = cat }
                                                    ) {
                                                        Text(
                                                            text = cat,
                                                            color = if (sel) Color.White else Color.Gray,
                                                            fontSize = 10.5.sp,
                                                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            // Presets List
                                            val filteredPresets = implementPresets.filter {
                                                presetCategoryFilter == "All" || it.category == presetCategoryFilter
                                            }

                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                filteredPresets.forEach { preset ->
                                                    val sel = preset.id == selectedPresetId
                                                    Surface(
                                                        color = if (sel) Color(0xFF1F3D2B) else Color(0xFF121E17),
                                                        shape = RoundedCornerShape(8.dp),
                                                        border = if (sel) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF00E676)) else null,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable { selectedPresetId = preset.id }
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(10.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                    Text(preset.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                                    if (preset.id == "planter_12r22" || preset.id == "planter_24r22") {
                                                                        Surface(
                                                                            color = Color(0xFF00E676),
                                                                            shape = RoundedCornerShape(4.dp)
                                                                        ) {
                                                                            Text("22\" SPACING", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                                                        }
                                                                    }
                                                                }
                                                                Text(
                                                                    "${String.format(Locale.US, "%.1f ft (%.2f m)", preset.toolWidthFeet, preset.toolWidthMeters)} • ${preset.numSections} Section Clutches • ${preset.implementType.label}",
                                                                    color = Color(0xFF81C784),
                                                                    fontSize = 11.sp
                                                                )
                                                                Text(preset.description, color = Color.LightGray, fontSize = 10.sp)
                                                            }

                                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                OutlinedButton(
                                                                    onClick = { loadPresetIntoCustomBuilder(preset) },
                                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                                    modifier = Modifier.height(28.dp)
                                                                ) {
                                                                    Text("Customize", fontSize = 10.sp, color = Color(0xFF81C784))
                                                                }

                                                                if (sel) {
                                                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00E676))
                                                                } else {
                                                                    Icon(Icons.Default.RadioButtonUnchecked, contentDescription = null, tint = Color.Gray)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            // ==========================================
                                            // CUSTOM IMPLEMENT BUILDER EMBEDDED IN WIZARD
                                            // ==========================================
                                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                // 1. Quick Row Calculator & Presets
                                                Surface(
                                                    color = Color(0xFF101914),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text("Quick Row Spacing & Count Calculator", color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                            val calculatedFeet = (selectedRowCount * selectedRowSpacingInches) / 12.0
                                                            val calculatedMeters = calculatedFeet * 0.3048
                                                            Text(
                                                                "$selectedRowCount Rows × ${selectedRowSpacingInches.toInt()}\" = ${String.format(Locale.US, "%.1f ft (%.2f m)", calculatedFeet, calculatedMeters)}",
                                                                color = Color.White,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 11.5.sp
                                                            )
                                                        }

                                                        // Row Spacing chips
                                                        Text("Row Spacing:", color = Color.LightGray, fontSize = 11.sp)
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            listOf(
                                                                22.0 to "22\" (Beets/Beans/Corn)",
                                                                30.0 to "30\" (Standard)",
                                                                15.0 to "15\" (Split-Row)",
                                                                20.0 to "20\"",
                                                                36.0 to "36\"",
                                                                38.0 to "38\""
                                                            ).forEach { (spacing, label) ->
                                                                val sel = selectedRowSpacingInches == spacing
                                                                Surface(
                                                                    color = if (sel) Color(0xFF00E676) else Color(0xFF1E2E24),
                                                                    shape = RoundedCornerShape(10.dp),
                                                                    modifier = Modifier.clickable {
                                                                        selectedRowSpacingInches = spacing
                                                                        applyRowCalculation(selectedRowCount, spacing)
                                                                    }
                                                                ) {
                                                                    Text(
                                                                        text = label,
                                                                        color = if (sel) Color.Black else Color.White,
                                                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                                                        fontSize = 10.sp,
                                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        // Row Count chips
                                                        Text("Number of Rows:", color = Color.LightGray, fontSize = 11.sp)
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            listOf(12, 16, 24, 32, 36, 48).forEach { rows ->
                                                                val sel = selectedRowCount == rows
                                                                Surface(
                                                                    color = if (sel) Color(0xFF00E676) else Color(0xFF1E2E24),
                                                                    shape = RoundedCornerShape(10.dp),
                                                                    modifier = Modifier.clickable {
                                                                        selectedRowCount = rows
                                                                        applyRowCalculation(rows, selectedRowSpacingInches)
                                                                    }
                                                                ) {
                                                                    Text(
                                                                        text = "$rows Rows",
                                                                        color = if (sel) Color.Black else Color.White,
                                                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                                                        fontSize = 10.5.sp,
                                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                // 2. Working Width & Number of Sections
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    OutlinedTextField(
                                                        value = customToolWidthFeet,
                                                        onValueChange = { customToolWidthFeet = it },
                                                        label = { Text("Swath Width (Feet)") },
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                        modifier = Modifier.weight(1f),
                                                        colors = OutlinedTextFieldDefaults.colors(
                                                            focusedTextColor = Color.White,
                                                            unfocusedTextColor = Color.White,
                                                            focusedBorderColor = Color(0xFF00E676)
                                                        )
                                                    )

                                                    val widthMeters = (customToolWidthFeet.toDoubleOrNull() ?: 22.0) * 0.3048
                                                    OutlinedTextField(
                                                        value = String.format(Locale.US, "%.2f m", widthMeters),
                                                        onValueChange = {},
                                                        readOnly = true,
                                                        label = { Text("Width (Metric)") },
                                                        modifier = Modifier.weight(0.9f),
                                                        colors = OutlinedTextFieldDefaults.colors(
                                                            focusedTextColor = Color(0xFF81C784),
                                                            unfocusedTextColor = Color(0xFF81C784)
                                                        )
                                                    )

                                                    // Sections Stepper
                                                    Column(modifier = Modifier.weight(1.1f)) {
                                                        Text("Boom / Row Sections: $customSections", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            IconButton(
                                                                onClick = { if (customSections > 1) customSections-- },
                                                                modifier = Modifier.size(36.dp)
                                                            ) {
                                                                Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Decrease", tint = Color.LightGray)
                                                            }
                                                            Surface(
                                                                color = Color(0xFF101914),
                                                                shape = RoundedCornerShape(6.dp),
                                                                modifier = Modifier.padding(horizontal = 4.dp)
                                                            ) {
                                                                Text(
                                                                    "$customSections",
                                                                    color = Color(0xFF00E676),
                                                                    fontWeight = FontWeight.Black,
                                                                    fontSize = 15.sp,
                                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                                )
                                                            }
                                                            IconButton(
                                                                onClick = { if (customSections < 48) customSections++ },
                                                                modifier = Modifier.size(36.dp)
                                                            ) {
                                                                Icon(Icons.Default.AddCircleOutline, contentDescription = "Increase", tint = Color.LightGray)
                                                            }
                                                        }
                                                    }
                                                }

                                                // Quick Width shortcut chips
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    listOf(
                                                        22.0 to "22.0 ft (12R-22\")",
                                                        44.0 to "44.0 ft (24R-22\")",
                                                        30.0 to "30 ft",
                                                        40.0 to "40 ft (16R-30\")",
                                                        60.0 to "60 ft (24R-30\")",
                                                        90.0 to "90 ft",
                                                        120.0 to "120 ft"
                                                    ).forEach { (w, lbl) ->
                                                        Surface(
                                                            color = Color(0xFF1E2E24),
                                                            shape = RoundedCornerShape(8.dp),
                                                            modifier = Modifier.clickable {
                                                                customToolWidthFeet = String.format(Locale.US, "%.1f", w)
                                                            }
                                                        ) {
                                                            Text(lbl, color = Color(0xFF81C784), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                                                        }
                                                    }
                                                }

                                                // 3. Mount Type & Hitch Distances
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    // Implement Type
                                                    Column(modifier = Modifier.weight(1.2f)) {
                                                        Text("Mounting Style:", color = Color.LightGray, fontSize = 11.sp)
                                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            ImplementType.values().forEach { t ->
                                                                val sel = t == customImplementType
                                                                Surface(
                                                                    color = if (sel) Color(0xFF00E676) else Color(0xFF101914),
                                                                    shape = RoundedCornerShape(6.dp),
                                                                    modifier = Modifier.clickable { customImplementType = t }
                                                                ) {
                                                                    Text(
                                                                        text = when (t) {
                                                                            ImplementType.THREE_POINT_MOUNTED -> "3-Point Mounted"
                                                                            ImplementType.TRAILING_TOOLBAR -> "Trailing Drawbar"
                                                                        },
                                                                        color = if (sel) Color.Black else Color.White,
                                                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                                                        fontSize = 10.5.sp,
                                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }

                                                    OutlinedTextField(
                                                        value = customHitchFeet,
                                                        onValueChange = { customHitchFeet = it },
                                                        label = { Text("Hitch Offset (Ft)") },
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                        modifier = Modifier.weight(0.9f),
                                                        colors = OutlinedTextFieldDefaults.colors(
                                                            focusedTextColor = Color.White,
                                                            unfocusedTextColor = Color.White
                                                        )
                                                    )

                                                    OutlinedTextField(
                                                        value = customOverlapInches,
                                                        onValueChange = { customOverlapInches = it },
                                                        label = { Text("Overlap (Inches)") },
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                        modifier = Modifier.weight(0.9f),
                                                        colors = OutlinedTextFieldDefaults.colors(
                                                            focusedTextColor = Color.White,
                                                            unfocusedTextColor = Color.White
                                                        )
                                                    )
                                                }

                                                // 4. Auto Section Control Switch
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(Color(0xFF101914), RoundedCornerShape(8.dp))
                                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column {
                                                        Text("Automatic Section Clutches / Shutoff", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                        Text("Auto-switches individual sections over headlands and applied passes", color = Color.LightGray, fontSize = 10.5.sp)
                                                    }
                                                    Switch(
                                                        checked = customAutoSectionControl,
                                                        onCheckedChange = { customAutoSectionControl = it },
                                                        colors = SwitchDefaults.colors(
                                                            checkedThumbColor = Color(0xFF00E676),
                                                            checkedTrackColor = Color(0xFF1F442C)
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        2 -> {
                            // Step 2: Boundary Strategy
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "SELECT FIELD PERIMETER STRATEGY",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )

                                WizardBoundaryMode.values().forEach { mode ->
                                    val sel = mode == selectedBoundaryMode
                                    Surface(
                                        color = if (sel) Color(0xFF1F3D2B) else Color(0xFF17241D),
                                        shape = RoundedCornerShape(12.dp),
                                        border = if (sel) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF00E676)) else null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedBoundaryMode = mode }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(if (sel) Color(0xFF00E676) else Color(0xFF223329)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(mode.icon, contentDescription = null, tint = if (sel) Color.Black else Color.White)
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(mode.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                                Text(mode.subtitle, color = Color.LightGray, fontSize = 11.sp)
                                            }
                                            if (sel) {
                                                Icon(Icons.Default.RadioButtonChecked, contentDescription = null, tint = Color(0xFF00E676))
                                            } else {
                                                Icon(Icons.Default.RadioButtonUnchecked, contentDescription = null, tint = Color.Gray)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        3 -> {
                            // Step 3: Guidance Line Strategy
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "SELECT SWATH GUIDANCE LINE STRATEGY",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )

                                WizardGuidanceMode.values().forEach { mode ->
                                    val sel = mode == selectedGuidanceMode
                                    Surface(
                                        color = if (sel) Color(0xFF1F3D2B) else Color(0xFF17241D),
                                        shape = RoundedCornerShape(12.dp),
                                        border = if (sel) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF00E676)) else null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedGuidanceMode = mode }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(if (sel) Color(0xFF00E676) else Color(0xFF223329)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(mode.icon, contentDescription = null, tint = if (sel) Color.Black else Color.White)
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(mode.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                                Text(mode.subtitle, color = Color.LightGray, fontSize = 11.sp)
                                            }
                                            if (sel) {
                                                Icon(Icons.Default.RadioButtonChecked, contentDescription = null, tint = Color(0xFF00E676))
                                            } else {
                                                Icon(Icons.Default.RadioButtonUnchecked, contentDescription = null, tint = Color.Gray)
                                            }
                                        }
                                    }
                                }

                                if (selectedGuidanceMode == WizardGuidanceMode.A_PLUS_HEADING) {
                                    Surface(
                                        color = Color(0xFF1E2D24),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("A+ Compass Heading Quick Options:", color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Button(
                                                    onClick = { aPlusHeadingDeg = 90.0 },
                                                    colors = ButtonDefaults.buttonColors(containerColor = if (aPlusHeadingDeg == 90.0) Color(0xFF00E676) else Color(0xFF26382D))
                                                ) {
                                                    Text("090° (East-West)", color = if (aPlusHeadingDeg == 90.0) Color.Black else Color.White, fontSize = 11.sp)
                                                }
                                                Button(
                                                    onClick = { aPlusHeadingDeg = 0.0 },
                                                    colors = ButtonDefaults.buttonColors(containerColor = if (aPlusHeadingDeg == 0.0) Color(0xFF00E676) else Color(0xFF26382D))
                                                ) {
                                                    Text("000° (North-South)", color = if (aPlusHeadingDeg == 0.0) Color.Black else Color.White, fontSize = 11.sp)
                                                }
                                                Button(
                                                    onClick = { aPlusHeadingDeg = currentTractorHeading },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26382D))
                                                ) {
                                                    Text("Tractor (${String.format(Locale.US, "%.0f°", currentTractorHeading)})", color = Color.White, fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Navigation Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (step > 1) {
                        OutlinedButton(
                            onClick = { step-- },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("BACK", color = Color.White)
                        }
                    } else {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("CANCEL", color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    if (step < 3) {
                        Button(
                            onClick = { step++ },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                            modifier = Modifier.weight(1.5f)
                        ) {
                            Text("NEXT STEP", color = Color.Black, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.Black)
                        }
                    } else {
                        Button(
                            onClick = {
                                val customCfg = if (implementMode == WizardImplementMode.CUSTOM_BUILDER) {
                                    val widthFt = customToolWidthFeet.toDoubleOrNull() ?: 22.0
                                    val widthM = widthFt * 0.3048
                                    val hitchFt = customHitchFeet.toDoubleOrNull() ?: 3.0
                                    val hitchM = hitchFt * 0.3048
                                    val overlapIn = customOverlapInches.toDoubleOrNull() ?: 3.0
                                    val overlapM = overlapIn * 0.0254
                                    val lateralIn = customLateralOffsetInches.toDoubleOrNull() ?: 0.0
                                    val lateralM = lateralIn * 0.0254

                                    ImplementConfig(
                                        implementType = customImplementType,
                                        toolWidth = widthM,
                                        offsetBehindTractor = hitchM,
                                        numSections = customSections.coerceIn(1, 48),
                                        overlap = overlapM,
                                        lateralOffset = lateralM,
                                        isMasterActive = true,
                                        isAutoSectionControl = customAutoSectionControl
                                    )
                                } else null

                                val preset = if (implementMode == WizardImplementMode.PRESETS) {
                                    implementPresets.find { it.id == selectedPresetId }
                                } else null

                                onLaunchFieldJob(
                                    fieldName.ifBlank { "Home Field" },
                                    farmName.ifBlank { "Lac qui Parle Farm" },
                                    cropType,
                                    preset,
                                    customCfg,
                                    selectedBoundaryMode,
                                    selectedGuidanceMode,
                                    aPlusHeadingDeg
                                )
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                            modifier = Modifier.weight(1.8f)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("START FIELD RUN NOW", color = Color.Black, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

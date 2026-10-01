package com.example.agopengps.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.agopengps.data.FieldDataImporter
import com.example.agopengps.data.FieldEntity
import com.example.agopengps.navigation.ABLine
import com.example.agopengps.navigation.CurveLine
import com.example.agopengps.navigation.Vec2
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldManagerDialog(
    currentField: FieldEntity?,
    savedFieldsList: List<FieldEntity>,
    boundaryVertices: List<Vec2>,
    isRecordingBoundary: Boolean,
    currentABLine: ABLine?,
    currentCurveLine: CurveLine?,
    isRecordingCurve: Boolean,
    currentTractorHeading: Double,
    implementWidthMeters: Double,
    appliedSegmentsCount: Int,
    onSaveCurrentFieldAndPasses: () -> Unit,
    onOpenSavedField: (FieldEntity) -> Unit,
    onDeleteSavedField: (Long) -> Unit,
    onStartBoundaryRecording: () -> Unit,
    onAddBoundaryPoint: () -> Unit,
    onFinishBoundaryRecording: () -> Unit,
    onClearBoundary: () -> Unit,
    onResetCoverage: () -> Unit,
    onCreateNewField: (name: String) -> Unit,
    onCreateAPlusLine: (headingDeg: Double) -> Unit,
    onStartCurveRecording: () -> Unit,
    onFinishCurveRecording: () -> Unit,
    onImportField: (content: String, name: String) -> Unit = { _, _ -> },
    onLoadHomeFarm: () -> Unit = {},
    onOpenFormFieldFromAb: () -> Unit = {},
    onOpenOuterBoundaryCorners: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Saved Fields", "Current Field", "AB & Contour", "Import KML / GeoJSON", "ISOBUS / Export")

    var newFieldName by remember { mutableStateOf("") }
    var aPlusHeadingText by remember { mutableStateOf(String.format("%.1f", currentTractorHeading)) }
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141C18)),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f)
                .padding(8.dp)
                .testTag("field_manager_dialog")
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Terrain, contentDescription = null, tint = Color(0xFF00E676))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "AgSteer Field & Pattern Storage",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Save, store, and repeat exact swath patterns across seasons",
                                color = Color(0xFF81C784),
                                fontSize = 11.sp
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Navigation Tabs
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF1A2420),
                    contentColor = Color(0xFF00E676)
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tab Content
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> {
                            // Saved Fields Tab
                            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                // Preloaded Home Farm Quick Action Card
                                Surface(
                                    color = Color(0xFF1E2E24),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF00E676)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .background(Color(0xFF00E676), androidx.compose.foundation.shape.CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Home, contentDescription = null, tint = Color.Black)
                                            }
                                            Column {
                                                Text(
                                                    text = "4367 176th St, Lac qui Parle County, MN",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.5.sp
                                                )
                                                Text(
                                                    text = "160 Ac Quarter Section • 176th St Baseline (090°) • USGS Topo",
                                                    color = Color(0xFF81C784),
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                onLoadHomeFarm()
                                                onDismiss()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = "LOAD FARM",
                                                color = Color.Black,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }

                                // Create New Field Section
                                Surface(
                                    color = Color(0xFF1B2621),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = newFieldName,
                                            onValueChange = { newFieldName = it },
                                            placeholder = { Text("New Field Name (e.g. West 80 Corn)") },
                                            modifier = Modifier.weight(1f),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White
                                            )
                                        )
                                        Button(
                                            onClick = {
                                                if (newFieldName.isNotBlank()) {
                                                    onCreateNewField(newFieldName.trim())
                                                    newFieldName = ""
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                            modifier = Modifier.height(56.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("CREATE")
                                        }
                                    }
                                }

                                Text(
                                    text = "SAVED FIELDS IN DATABASE (${savedFieldsList.size})",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )

                                if (savedFieldsList.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "No saved fields found. Create a field or save the active field above.",
                                            color = Color(0xFF78909C),
                                            fontSize = 13.sp
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(savedFieldsList) { field ->
                                            val isCurrent = currentField?.id == field.id
                                            Card(
                                                shape = RoundedCornerShape(8.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (isCurrent) Color(0xFF1E3326) else Color(0xFF1A231F)
                                                ),
                                                border = if (isCurrent) borderStroke(1.5.dp, Color(0xFF00E676)) else null,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                text = field.name,
                                                                color = Color.White,
                                                                fontWeight = FontWeight.Black,
                                                                fontSize = 15.sp
                                                            )
                                                            if (isCurrent) {
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                Surface(
                                                                    color = Color(0xFF00E676),
                                                                    shape = RoundedCornerShape(4.dp)
                                                                ) {
                                                                    Text(
                                                                        text = "ACTIVE",
                                                                        color = Color.Black,
                                                                        fontWeight = FontWeight.Black,
                                                                        fontSize = 9.sp,
                                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(
                                                            text = "Area: ${String.format("%.2f", field.areaAcres)} Ac | Worked: ${String.format("%.2f", field.workedAcres)} Ac | ${if (field.hasActiveAbLine) "AB: ${field.activeAbLineHeading.toInt()}°" else "No AB"}",
                                                            color = Color(0xFFB0BEC5),
                                                            fontSize = 12.sp,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                        Text(
                                                            text = "Saved: ${dateFormat.format(Date(field.lastModifiedAt))}",
                                                            color = Color(0xFF78909C),
                                                            fontSize = 10.sp
                                                        )
                                                    }

                                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Button(
                                                            onClick = {
                                                                onOpenSavedField(field)
                                                                onDismiss()
                                                            },
                                                            colors = ButtonDefaults.buttonColors(
                                                                containerColor = if (isCurrent) Color(0xFF388E3C) else Color(0xFF1976D2)
                                                            )
                                                        ) {
                                                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                text = if (isCurrent) "RE-SYNC" else "REOPEN PASSES",
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 11.sp
                                                            )
                                                        }

                                                        IconButton(
                                                            onClick = { onDeleteSavedField(field.id) }
                                                        ) {
                                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF5252))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            // Current Field & Passes Tab
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item {
                                    Surface(
                                        color = Color(0xFF1B2621),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Text("ACTIVE WORKING FIELD", color = Color(0xFF81C784), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            Text(
                                                text = currentField?.name ?: "No Field Selected",
                                                color = Color.White,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 20.sp
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "Recorded Passes: $appliedSegmentsCount segments",
                                                    color = Color(0xFF00E676),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                                Text(
                                                    text = "Boundary Vertices: ${boundaryVertices.size}",
                                                    color = Color(0xFFFFD54F),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                // Quick Save Passes Button
                                item {
                                    Button(
                                        onClick = {
                                            onSaveCurrentFieldAndPasses()
                                            onDismiss()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF00E676),
                                            contentColor = Color.Black
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                    ) {
                                        Icon(Icons.Default.Save, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "SAVE CURRENT FIELD & ALL PASSES TO DISK",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 13.sp
                                        )
                                    }
                                }

                                item {
                                    OutlinedButton(
                                        onClick = onResetCoverage,
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFAB40)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("RESET / CLEAR WORKED COVERAGE PASSES")
                                    }
                                }

                                // Boundary Recording Box
                                item {
                                    Text("Field Perimeter & Boundary", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(4.dp))

                                    if (!isRecordingBoundary) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = onStartBoundaryRecording,
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("DRIVE PERIMETER", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Button(
                                                onClick = onOpenFormFieldFromAb,
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B)),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.AspectRatio, contentDescription = null)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("FROM AB LINE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Button(
                                                onClick = onOpenOuterBoundaryCorners,
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF455A64)),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.PinDrop, contentDescription = null)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("4 CORNERS", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        if (boundaryVertices.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            OutlinedButton(
                                                onClick = onClearBoundary,
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("CLEAR BOUNDARY (${boundaryVertices.size} PTS)", fontSize = 12.sp)
                                            }
                                        }
                                    } else {
                                        Surface(
                                            color = Color(0xFF2E2414),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    text = "RECORDING BOUNDARY... Drive perimeter of field.",
                                                    color = Color(0xFFFFB74D),
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 13.sp
                                                )
                                                Text(
                                                    text = "Recorded points: ${boundaryVertices.size}",
                                                    color = Color.White,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 12.sp
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Button(
                                                        onClick = onAddBoundaryPoint,
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text("ADD VERTEX")
                                                    }
                                                    Button(
                                                        onClick = onFinishBoundaryRecording,
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676), contentColor = Color.Black),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text("CLOSE & SAVE", fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        2 -> {
                            // AB & Contour Guidance Lines Tab
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                item {
                                    Text("Active Guidance Swaths", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Surface(
                                        color = Color(0xFF1B2621),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "Current AB Line: ${currentABLine?.name ?: "None"}",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                            val widthFt = implementWidthMeters * 3.28084
                                            Text(
                                                text = "Heading: ${String.format("%.1f", currentABLine?.headingDeg ?: 0.0)}° | Swath Width: ${String.format("%.1f", widthFt)} ft (${String.format("%.2f", implementWidthMeters)}m)",
                                                color = Color(0xFF81C784),
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }

                                item {
                                    Text("A+ Line Generator (From Position + Heading)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = aPlusHeadingText,
                                            onValueChange = { aPlusHeadingText = it },
                                            label = { Text("Heading (°)") },
                                            modifier = Modifier.width(130.dp),
                                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                                        )
                                        Button(
                                            onClick = { aPlusHeadingText = String.format("%.1f", currentTractorHeading) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F))
                                        ) {
                                            Text("TRACTOR (${currentTractorHeading.toInt()}°)", fontSize = 11.sp)
                                        }
                                        Button(
                                            onClick = {
                                                val hdg = aPlusHeadingText.toDoubleOrNull() ?: currentTractorHeading
                                                onCreateAPlusLine(hdg)
                                                onDismiss()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                                        ) {
                                            Text("CREATE A+", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                item {
                                    Text("Curve & Contour Guidance Mode", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    if (currentCurveLine != null) {
                                        Surface(
                                            color = Color(0xFF261933),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text(
                                                    text = "Active Contour Curve: ${currentCurveLine.name}",
                                                    color = Color(0xFFE1BEE7),
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Vertices: ${currentCurveLine.points.size} pts | Swath: ${String.format("%.2f", implementWidthMeters)}m",
                                                    color = Color(0xFFCE93D8),
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }

                                    if (!isRecordingCurve) {
                                        Button(
                                            onClick = onStartCurveRecording,
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.Timeline, contentDescription = null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("RECORD CURVED PASS (CONTOUR)", fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Surface(
                                            color = Color(0xFF2E1736),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text("Recording Contour Curve Points...", color = Color(0xFFE1BEE7), fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Button(
                                                    onClick = {
                                                        onFinishCurveRecording()
                                                        onDismiss()
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBA68C8), contentColor = Color.Black),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text("SAVE & ACTIVATE CURVE", fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        3 -> {
                            // Import KML / GeoJSON Field Data Tab
                            val context = LocalContext.current
                            var importText by remember { mutableStateOf("") }
                            var importName by remember { mutableStateOf("Imported Field") }
                            var importStatus by remember { mutableStateOf<String?>(null) }
                            var parsedPreview by remember { mutableStateOf<com.example.agopengps.data.ImportedFieldResult?>(null) }

                            val filePickerLauncher = rememberLauncherForActivityResult(
                                contract = ActivityResultContracts.OpenDocument()
                            ) { uri ->
                                if (uri != null) {
                                    try {
                                        context.contentResolver.openInputStream(uri)?.use { stream ->
                                            val text = stream.bufferedReader().use { it.readText() }
                                            val inferredName = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')
                                                ?: "Imported Field"
                                            importText = text
                                            importName = inferredName
                                            val res = FieldDataImporter.parseFieldData(text, inferredName)
                                            if (res != null) {
                                                parsedPreview = res
                                                importStatus = "Loaded '${res.fieldName}' (${String.format(Locale.US, "%.1f", res.areaAcres)} ac, ${res.boundaryLocal.size} pts)"
                                            } else {
                                                importStatus = "Failed to parse KML/GeoJSON from file."
                                            }
                                        }
                                    } catch (e: Exception) {
                                        importStatus = "Error opening file: ${e.message}"
                                    }
                                }
                            }

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                item {
                                    Surface(
                                        color = Color(0xFF1B2621),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.FileOpen, contentDescription = null, tint = Color(0xFF00E676))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    "Import External Field Boundaries",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                "Import field boundary polygons from Google Earth (.kml), QGIS / GeoJSON (.geojson), Climate FieldView, or John Deere Operations Center. AgOpenGPS will automatically project coordinates, generate a 12m headland boundary, and calculate an optimal baseline AB line.",
                                                color = Color(0xFFB0BEC5),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }

                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                filePickerLauncher.launch(arrayOf("*/*"))
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                            modifier = Modifier.weight(1f).testTag("btn_browse_kml")
                                        ) {
                                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("OPEN KML / GEOJSON FILE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                val sampleKml = """<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <name>Sample 40-Acre Organic Field</name>
    <Placemark>
      <name>North 40 Boundary</name>
      <Polygon>
        <outerBoundaryIs>
          <LinearRing>
            <coordinates>
              -95.7170,44.9125,0
              -95.7130,44.9125,0
              -95.7130,44.9160,0
              -95.7170,44.9160,0
              -95.7170,44.9125,0
            </coordinates>
          </LinearRing>
        </outerBoundaryIs>
      </Polygon>
    </Placemark>
  </Document>
</kml>"""
                                                importText = sampleKml
                                                importName = "North 40 Organic Field"
                                                val res = FieldDataImporter.parseFieldData(sampleKml, importName)
                                                parsedPreview = res
                                                importStatus = "Loaded Sample 40-Acre KML Field"
                                            },
                                            modifier = Modifier.weight(1f).testTag("btn_load_sample_kml")
                                        ) {
                                            Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("LOAD SAMPLE KML", fontSize = 11.sp)
                                        }
                                    }
                                }

                                if (parsedPreview != null) {
                                    item {
                                        Surface(
                                            color = Color(0xFF142419),
                                            shape = RoundedCornerShape(8.dp),
                                            border = borderStroke(1.dp, Color(0xFF00E676)),
                                            modifier = Modifier.fillMaxWidth().testTag("import_preview_card")
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("PARSED GEOMETRY PREVIEW", color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text("Field Name: ${parsedPreview!!.fieldName}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text("Area: ${String.format(Locale.US, "%.1f", parsedPreview!!.areaAcres)} Acres (${String.format(Locale.US, "%.1f", parsedPreview!!.areaAcres * 0.404686)} Ha)", color = Color(0xFF81C784), fontSize = 12.sp)
                                                Text("Boundary Points: ${parsedPreview!!.boundaryLocal.size} coordinates", color = Color(0xFFB0BEC5), fontSize = 12.sp)
                                                Text("Headland: 12.0m inner boundary calculated", color = Color(0xFFB0BEC5), fontSize = 12.sp)
                                                Text("Guidance AB Line: ${parsedPreview!!.abLine?.name ?: "Generated along baseline"}", color = Color(0xFFB0BEC5), fontSize = 12.sp)
                                                Spacer(modifier = Modifier.height(10.dp))
                                                Button(
                                                    onClick = {
                                                        onImportField(importText, parsedPreview!!.fieldName)
                                                        onDismiss()
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                                    modifier = Modifier.fillMaxWidth().testTag("btn_apply_import")
                                                ) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("APPLY & SET AS ACTIVE GUIDANCE FIELD", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }

                                if (importStatus != null) {
                                    item {
                                        Text(importStatus!!, color = Color(0xFFFFD54F), fontSize = 12.sp)
                                    }
                                }

                                item {
                                    OutlinedTextField(
                                        value = importText,
                                        onValueChange = {
                                            importText = it
                                            val res = FieldDataImporter.parseFieldData(it, importName)
                                            parsedPreview = res
                                            if (res != null) {
                                                importStatus = "Parsed: ${res.fieldName} (${String.format(Locale.US, "%.1f", res.areaAcres)} ac)"
                                            }
                                        },
                                        label = { Text("Or Paste Raw KML / GeoJSON Text", color = Color(0xFF90A4AE)) },
                                        placeholder = { Text("Paste <?xml... or {\"type\": \"FeatureCollection\"... here", color = Color(0xFF546E7A)) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF00E676),
                                            unfocusedBorderColor = Color(0xFF37474F),
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color(0xFFB0BEC5)
                                        ),
                                        modifier = Modifier.fillMaxWidth().height(160.dp).testTag("input_kml_raw_text")
                                    )
                                }
                            }
                        }

                        4 -> {
                            // ISOBUS XML / Field Data Management & Export Tab
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item {
                                    Surface(
                                        color = Color(0xFF1B2621),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF00E676))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    "ISOBUS Task Controller (ISO-XML / ISO 11783)",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                "Manage and export field boundaries, guidance AB lines, and coverage logs compatible with John Deere Operations Center, Trimble Ag Software, and Climate FieldView.",
                                                color = Color(0xFFB0BEC5),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }

                                item {
                                    Text("FIELD EXPORT & EXCHANGE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                onSaveCurrentFieldAndPasses()
                                                onDismiss()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("EXPORT ISO-XML", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Button(
                                            onClick = {
                                                onSaveCurrentFieldAndPasses()
                                                onDismiss()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("EXPORT KML / SHP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                item {
                                    Surface(
                                        color = Color(0xFF16201B),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text("TASK DATA TELEMETRY", color = Color(0xFF81C784), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Field Name:", color = Color(0xFF90A4AE), fontSize = 12.sp)
                                                Text(currentField?.name ?: "Unnamed Field", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Boundary Vertices:", color = Color(0xFF90A4AE), fontSize = 12.sp)
                                                Text("${boundaryVertices.size} Points", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                            }
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Active Swath:", color = Color(0xFF90A4AE), fontSize = 12.sp)
                                                Text(currentABLine?.name ?: (currentCurveLine?.name ?: "None"), color = Color.White, fontSize = 12.sp)
                                            }
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Applied Swath Polygons:", color = Color(0xFF90A4AE), fontSize = 12.sp)
                                                Text("$appliedSegmentsCount Segments", color = Color(0xFF00E676), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun borderStroke(width: androidx.compose.ui.unit.Dp, color: Color) =
    androidx.compose.foundation.BorderStroke(width, color)

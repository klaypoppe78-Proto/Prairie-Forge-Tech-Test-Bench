package com.example.agopengps.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.agopengps.io.*
import com.example.agopengps.navigation.FixQuality
import com.example.agopengps.navigation.VehicleState
import java.util.*

enum class NmeaTab {
    LIVE_STREAM,
    HEALTH_AND_HZ,
    TROUBLESHOOTING_GUIDE,
    COMMAND_TRANSMIT
}

/**
 * Dedicated NMEA Diagnostics & Field Troubleshooting Tool.
 * Provides live sentence monitoring, syntax color-coding, per-sentence frequency (Hz) analysis,
 * checksum validation, automated health checks, interactive troubleshooting guides,
 * and 1-tap clipboard diagnostic reporting for field service technicians.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NmeaDiagnosticsDialog(
    diagnosticsManager: NmeaDiagnosticsManager,
    vehicleState: VehicleState,
    onSendCommand: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val records by diagnosticsManager.records.collectAsState()
    val summary by diagnosticsManager.summary.collectAsState()

    var selectedTab by remember { mutableStateOf(NmeaTab.LIVE_STREAM) }
    var filterType by remember { mutableStateOf("ALL") }
    var searchText by remember { mutableStateOf("") }
    var isPaused by remember { mutableStateOf(diagnosticsManager.isPaused) }
    var selectedRecord by remember { mutableStateOf<NmeaSentenceRecord?>(null) }
    var customCommandText by remember { mutableStateOf("") }
    var lastSentFeedback by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()

    // Filtered sentences
    val filteredRecords = remember(records, filterType, searchText) {
        records.filter { rec ->
            val matchesType = when (filterType) {
                "ALL" -> true
                "ERRORS" -> !rec.isValidChecksum || rec.sentenceType == "UNKNOWN"
                else -> rec.sentenceType.equals(filterType, ignoreCase = true)
            }
            val matchesSearch = if (searchText.isBlank()) true else rec.rawSentence.contains(searchText, ignoreCase = true)
            matchesType && matchesSearch
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1714)),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.94f)
                .padding(6.dp)
                .testTag("nmea_diagnostics_dialog")
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {

                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Troubleshoot, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "NMEA Diagnostics & Field Troubleshooting",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 17.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = if (summary.overallHz > 0) Color(0xFF003816) else Color(0xFF381200),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "${summary.overallHz} Hz",
                                        color = if (summary.overallHz > 0) Color(0xFF00E676) else Color(0xFFFF5252),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Receiver: ${summary.detectedReceiver} | Total: ${summary.totalReceived} sentences",
                                color = Color(0xFF90A4AE),
                                fontSize = 11.sp
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Copy Full Diagnostic Report Button
                        Button(
                            onClick = {
                                val report = diagnosticsManager.generateFullDiagnosticsReport(vehicleState)
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("AgSteer NMEA Diagnostics", report)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Full NMEA Diagnostic Report Copied!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0277BD)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("COPY REPORT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Navigation Tabs
                TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = Color(0xFF16231E),
                    contentColor = Color(0xFF00E676),
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                ) {
                    Tab(
                        selected = selectedTab == NmeaTab.LIVE_STREAM,
                        onClick = { selectedTab = NmeaTab.LIVE_STREAM },
                        text = { Text("LIVE NMEA STREAM", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                        icon = { Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == NmeaTab.HEALTH_AND_HZ,
                        onClick = { selectedTab = NmeaTab.HEALTH_AND_HZ },
                        text = { Text("HZ & HEALTH CHECK", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                        icon = { Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == NmeaTab.TROUBLESHOOTING_GUIDE,
                        onClick = { selectedTab = NmeaTab.TROUBLESHOOTING_GUIDE },
                        text = { Text("FIELD GUIDE & FIXES", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                        icon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == NmeaTab.COMMAND_TRANSMIT,
                        onClick = { selectedTab = NmeaTab.COMMAND_TRANSMIT },
                        text = { Text("COMMAND SENDER", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                        icon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tab Content
                when (selectedTab) {
                    NmeaTab.LIVE_STREAM -> {
                        LiveStreamTab(
                            records = filteredRecords,
                            listState = listState,
                            filterType = filterType,
                            searchText = searchText,
                            isPaused = isPaused,
                            selectedRecord = selectedRecord,
                            onFilterChange = { filterType = it },
                            onSearchChange = { searchText = it },
                            onTogglePause = {
                                isPaused = !isPaused
                                diagnosticsManager.isPaused = isPaused
                            },
                            onClear = { diagnosticsManager.clearLog() },
                            onSelectRecord = { selectedRecord = it },
                            onDismissDetails = { selectedRecord = null }
                        )
                    }
                    NmeaTab.HEALTH_AND_HZ -> {
                        HealthAndHzTab(
                            summary = summary,
                            vehicleState = vehicleState
                        )
                    }
                    NmeaTab.TROUBLESHOOTING_GUIDE -> {
                        TroubleshootingGuideTab(
                            onSendCommand = onSendCommand
                        )
                    }
                    NmeaTab.COMMAND_TRANSMIT -> {
                        CommandSenderTab(
                            customCommandText = customCommandText,
                            lastSentFeedback = lastSentFeedback,
                            onCommandTextChange = { customCommandText = it },
                            onSendCommand = { cmd ->
                                onSendCommand(cmd)
                                lastSentFeedback = cmd
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tab 1: Live Sentence Stream & Inspector
 */
@Composable
private fun LiveStreamTab(
    records: List<NmeaSentenceRecord>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    filterType: String,
    searchText: String,
    isPaused: Boolean,
    selectedRecord: NmeaSentenceRecord?,
    onFilterChange: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onTogglePause: () -> Unit,
    onClear: () -> Unit,
    onSelectRecord: (NmeaSentenceRecord) -> Unit,
    onDismissDetails: () -> Unit
) {
    val context = LocalContext.current
    val filterOptions = listOf("ALL", "GGA", "KSXT", "PAOGI", "VTG", "RMC", "HPR", "ERRORS")

    Column(modifier = Modifier.fillMaxSize()) {
        // Controls Row: Filters, Search, Pause, Clear
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Filter Chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                filterOptions.forEach { opt ->
                    FilterChip(
                        selected = filterType == opt,
                        onClick = { onFilterChange(opt) },
                        label = { Text(opt, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Pause / Resume Toggle
                Button(
                    onClick = onTogglePause,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPaused) Color(0xFFFF9100) else Color(0xFF263238)
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(
                        if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isPaused) "RESUME" else "PAUSE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                // Clear
                OutlinedButton(
                    onClick = onClear,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("CLEAR", fontSize = 10.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Search Bar
        OutlinedTextField(
            value = searchText,
            onValueChange = onSearchChange,
            placeholder = { Text("Search sentence text (e.g. \$KSXT, 44.9, RTK)...", fontSize = 11.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
            trailingIcon = {
                if (searchText.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Split Layout: List of Sentences and optional Inspector Details
        Row(modifier = Modifier.fillMaxSize()) {
            // Left: Sentence Stream List
            Surface(
                color = Color(0xFF0A100E),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2824)),
                modifier = Modifier
                    .weight(if (selectedRecord != null) 1.2f else 1f)
                    .fillMaxHeight()
            ) {
                if (records.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (isPaused) "Stream paused. Tap 'RESUME' to view live sentences." else "Listening for incoming NMEA sentences...",
                            color = Color(0xFF607D8B),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(records, key = { it.id }) { rec ->
                            SentenceItemRow(
                                record = rec,
                                isSelected = selectedRecord?.id == rec.id,
                                onClick = { onSelectRecord(rec) },
                                onCopy = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("NMEA Sentence", rec.rawSentence))
                                    Toast.makeText(context, "Sentence copied", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }

            // Right: Inspector Panel when a sentence is selected
            if (selectedRecord != null) {
                Spacer(modifier = Modifier.width(8.dp))
                SentenceInspectorPanel(
                    record = selectedRecord,
                    onDismiss = onDismissDetails,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun SentenceItemRow(
    record: NmeaSentenceRecord,
    isSelected: Boolean,
    onClick: () -> Unit,
    onCopy: () -> Unit
) {
    val tagColor = when (record.sentenceType) {
        "KSXT" -> Color(0xFF00E676)
        "GGA" -> Color(0xFF00E5FF)
        "PAOGI" -> Color(0xFFFFD600)
        "VTG" -> Color(0xFFFF9100)
        "RMC" -> Color(0xFFE040FB)
        "HPR", "HDT" -> Color(0xFF76FF03)
        else -> Color(0xFF90A4AE)
    }

    Surface(
        color = if (isSelected) Color(0xFF1E352B) else Color(0xFF141F1A),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) Color(0xFF00E676) else if (!record.isValidChecksum) Color(0xFFFF5252) else Color(0xFF263930)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                // Type Badge
                Surface(
                    color = tagColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.width(52.dp)
                ) {
                    Text(
                        text = record.sentenceType,
                        color = tagColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Time
                Text(
                    text = record.formattedTime,
                    color = Color(0xFF78909C),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Raw Sentence Snippet
                Text(
                    text = record.rawSentence,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }

            // Checksum Indicator & Copy Button
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (record.isValidChecksum) {
                    Icon(Icons.Default.Check, contentDescription = "Valid", tint = Color(0xFF00E676), modifier = Modifier.size(14.dp))
                } else {
                    Icon(Icons.Default.Error, contentDescription = "Invalid Checksum", tint = Color(0xFFFF5252), modifier = Modifier.size(14.dp))
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = onCopy, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFF90A4AE), modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

/**
 * Detailed Sentence Breakdown Panel
 */
@Composable
private fun SentenceInspectorPanel(
    record: NmeaSentenceRecord,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Surface(
        color = Color(0xFF14201B),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E4639)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            // Inspector Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SENTENCE FIELD DECODER",
                    color = Color(0xFF00E676),
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Raw Full String Card
            Surface(
                color = Color(0xFF09100D),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(6.dp)) {
                    Text(
                        text = record.rawSentence,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Parsed Values Summary
            val report = record.parsedReport
            if (report != null) {
                Text("DECODED TELEMETRY:", color = Color(0xFF81D4FA), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        InspectorFieldRow("Talker & Sentence", "${record.talkerId} | ${record.sentenceType}")
                    }
                    if (report.geoPoint.latitude != 0.0 || report.geoPoint.longitude != 0.0) {
                        item {
                            InspectorFieldRow("Latitude", String.format(Locale.US, "%.7f°", report.geoPoint.latitude))
                        }
                        item {
                            InspectorFieldRow("Longitude", String.format(Locale.US, "%.7f°", report.geoPoint.longitude))
                        }
                        item {
                            InspectorFieldRow("Altitude MSL", "${String.format(Locale.US, "%.1fm", report.geoPoint.altitude)} (${String.format(Locale.US, "%.1f ft", report.geoPoint.altitude * 3.28084)})")
                        }
                    }
                    if (report.headingDeg != null) {
                        item {
                            InspectorFieldRow("True Heading", String.format(Locale.US, "%.2f°", report.headingDeg))
                        }
                    }
                    if (report.rollDeg != 0.0 || report.pitchDeg != 0.0) {
                        item {
                            InspectorFieldRow("Roll & Pitch", String.format(Locale.US, "Roll %+.1f°, Pitch %+.1f°", report.rollDeg, report.pitchDeg))
                        }
                    }
                    if (report.baselineLengthMeters > 0.0) {
                        item {
                            InspectorFieldRow("Antenna Baseline", String.format(Locale.US, "%.3f m (%.1f in)", report.baselineLengthMeters, report.baselineLengthMeters * 39.3701))
                        }
                    }
                    item {
                        InspectorFieldRow("Fix Quality", report.fixQuality.name)
                    }
                    if (report.satellites > 0) {
                        item {
                            InspectorFieldRow("Satellites Used", "${report.satellites}")
                        }
                    }
                    if (report.hdop < 90.0) {
                        item {
                            InspectorFieldRow("HDOP", String.format(Locale.US, "%.2f", report.hdop))
                        }
                    }
                    if (report.ageSec > 0.0) {
                        item {
                            InspectorFieldRow("Correction Age", String.format(Locale.US, "%.1f sec", report.ageSec))
                        }
                    }
                    if (report.speedKmh != null) {
                        item {
                            InspectorFieldRow("Speed", String.format(Locale.US, "%.1f km/h (%.1f mph)", report.speedKmh, report.speedKmh * 0.621371))
                        }
                    }
                    item {
                        InspectorFieldRow("Checksum Check", if (record.isValidChecksum) "VALID (PASSED)" else "CHECKSUM MISMATCH / ERROR")
                    }

                    // Raw Token index breakdown
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("TOKEN INDEX BREAKDOWN:", color = Color(0xFFB0BEC5), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    items(record.fieldTokens.size) { idx ->
                        InspectorFieldRow("Token [$idx]", record.fieldTokens[idx])
                    }
                }
            } else {
                Text(
                    text = "Sentence could not be fully parsed into standard GNSS fields.",
                    color = Color(0xFFFF8A80),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun InspectorFieldRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F1814), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color(0xFF90A4AE), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Text(text = value, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

/**
 * Tab 2: Frequency & Health Check Monitor
 */
@Composable
private fun HealthAndHzTab(
    summary: NmeaDiagnosticsSummary,
    vehicleState: VehicleState
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Frequency Stream Rates Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF14201B)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ACTIVE NMEA SENTENCE RATES (Hz):", color = Color(0xFF00E676), fontWeight = FontWeight.Black, fontSize = 12.sp)
                        Text("Total: ${summary.overallHz} Hz", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (summary.sentenceFrequenciesHz.isEmpty()) {
                        Text("Waiting for frequency statistics...", color = Color(0xFF90A4AE), fontSize = 11.sp)
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            summary.sentenceFrequenciesHz.entries.take(4).forEach { (type, hz) ->
                                HzPill(type = type, hz = hz, modifier = Modifier.weight(1f))
                            }
                        }
                        if (summary.sentenceFrequenciesHz.size > 4) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                summary.sentenceFrequenciesHz.entries.drop(4).forEach { (type, hz) ->
                                    HzPill(type = type, hz = hz, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Automated Field Diagnostics Checklist
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF14201B)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("AUTOMATED FIELD HEALTH VERIFICATION:", color = Color(0xFF81D4FA), fontWeight = FontWeight.Black, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (summary.checklist.isEmpty()) {
                        Text("Analyzing stream health...", color = Color(0xFF90A4AE), fontSize = 11.sp)
                    } else {
                        summary.checklist.forEach { item ->
                            HealthCheckItemRow(item)
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }
        }

        // Checksum & Packet Statistics
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF14201B)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("COMMUNICATION INTEGRITY METRICS:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricBox("TOTAL SENTENCES", "${summary.totalReceived}", Color.White, Modifier.weight(1f))
                        MetricBox("VALID CHECKSUMS", "${summary.validChecksumCount}", Color(0xFF00E676), Modifier.weight(1f))
                        MetricBox("CHECKSUM ERRORS", "${summary.checksumErrorCount}", if (summary.checksumErrorCount > 0) Color(0xFFFF5252) else Color(0xFF00E676), Modifier.weight(1f))
                        MetricBox("MALFORMED PACKETS", "${summary.malformedCount}", if (summary.malformedCount > 0) Color(0xFFFFB300) else Color(0xFF00E676), Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun HzPill(type: String, hz: Double, modifier: Modifier = Modifier) {
    Surface(
        color = Color(0xFF0D1713),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF263930)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(type, color = Color(0xFF81D4FA), fontSize = 10.sp, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${hz} Hz",
                color = if (hz >= 9.0) Color(0xFF00E676) else if (hz >= 4.0) Color(0xFFFFD600) else Color(0xFFFF5252),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun HealthCheckItemRow(item: HealthCheckItem) {
    val (statusColor, statusIcon, badgeText) = when (item.status) {
        CheckStatus.PASS -> Triple(Color(0xFF00E676), Icons.Default.CheckCircle, "PASS")
        CheckStatus.WARNING -> Triple(Color(0xFFFFD600), Icons.Default.Warning, "WARN")
        CheckStatus.FAIL -> Triple(Color(0xFFFF5252), Icons.Default.Cancel, "FAIL")
    }

    Surface(
        color = Color(0xFF0D1713),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text(item.detail, color = Color(0xFFB0BEC5), fontSize = 10.sp)
                }
            }

            Surface(
                color = statusColor.copy(alpha = 0.2f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = badgeText,
                    color = statusColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun MetricBox(title: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Surface(
        color = Color(0xFF0D1713),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Color(0xFF78909C), fontSize = 8.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        }
    }
}

/**
 * Tab 3: Interactive Field Troubleshooting Guides & 1-Tap Fixes
 */
@Composable
private fun TroubleshootingGuideTab(
    onSendCommand: (String) -> Unit
) {
    val guides = listOf(
        TroubleshootingIssue(
            title = "Issue: RTK Float Only / No RTK Fix",
            symptoms = "Tractor cross-track error drifts 4-8 inches; Fix status shows 'RTK_FLOAT' or 'DGPS'.",
            causes = listOf(
                "NTRIP Caster connection interrupted or Wi-Fi hotspot in cab disconnected.",
                "Radio modem antenna disconnected or off-frequency from RTK base station.",
                "Base station antenna is shadowed or sending stale RTCM3 correction packets (>5s age)."
            ),
            remedies = listOf(
                "Check that NTRIP Client or Radio Modem is actively forwarding RTCM3 packets.",
                "Inspect the RTK correction latency counter on the top lightbar (should be < 2.0s).",
                "Ensure local RTK Base Station is powered on and locked with at least 16 satellites."
            )
        ),
        TroubleshootingIssue(
            title = "Issue: Dual-Antenna Heading Missing or 0.0°",
            symptoms = "Autosteer wanders; Heading indicator shows 0.0° or jumps wildly; No KSXT/HPR sentence.",
            causes = listOf(
                "Secondary antenna SMA coaxial cable loose or pinched in tractor cab door.",
                "Unicore UM982 / F9P receiver secondary RF input not receiving satellite lock.",
                "Receiver output mode set to single-antenna (KSXT output disabled)."
            ),
            remedies = listOf(
                "Verify both Primary and Secondary antenna cables are securely screwed into SMA ports.",
                "Check antenna roof spacing matches configured baseline (0.85m to 1.25m).",
                "Send preset command: 'UNILOG COM1 KSXT ONTIME 0.05' to enable 20Hz KSXT."
            )
        ),
        TroubleshootingIssue(
            title = "Issue: Low Update Rate (< 5Hz) / Steer Hesitation",
            symptoms = "Guidance map lags; Steer valve reacts slowly; Total Hz reads 1Hz or 5Hz.",
            causes = listOf(
                "Receiver baud rate set too low (e.g. 9600 or 38400 baud saturating bandwidth).",
                "Too many redundant NMEA sentences enabled simultaneously (\$GSV, \$GSA, \$ZDA, \$GLL)."
            ),
            remedies = listOf(
                "Ensure USB / Serial COM port baud rate is set to 115200 or 460800 baud.",
                "Disable unnecessary sentences: keep only \$KSXT (or \$GGA + \$VTG + \$HPR)."
            )
        ),
        TroubleshootingIssue(
            title = "Issue: Checksum Errors / Corrupted NMEA Characters",
            symptoms = "Checksum error count increasing rapidly; random symbols appear in sentence feed.",
            causes = listOf(
                "Baud rate mismatch between Teensy / tablet and GNSS receiver.",
                "Electrical noise interference from alternator, high-power radio, or unshielded motor."
            ),
            remedies = listOf(
                "Route GNSS serial cables away from heavy hydraulic solenoids and alternator wires.",
                "Verify common ground between Teensy 4.1 AIO board and tractor chassis."
            )
        )
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(guides) { issue ->
            TroubleshootingCard(issue)
        }
    }
}

data class TroubleshootingIssue(
    val title: String,
    val symptoms: String,
    val causes: List<String>,
    val remedies: List<String>
)

@Composable
private fun TroubleshootingCard(issue: TroubleshootingIssue) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14201B)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(issue.title, color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Symptoms: ${issue.symptoms}", color = Color(0xFFFFD600), fontSize = 11.sp)
            Spacer(modifier = Modifier.height(6.dp))

            Text("Likely Causes:", color = Color(0xFF90A4AE), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            issue.causes.forEach { cause ->
                Text("• $cause", color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, modifier = Modifier.padding(start = 6.dp))
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text("Field Fix Steps:", color = Color(0xFF81D4FA), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            issue.remedies.forEach { remedy ->
                Text("✓ $remedy", color = Color(0xFF80CBC4), fontSize = 10.sp, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

/**
 * Tab 4: Command Transmitter & Presets
 */
@Composable
private fun CommandSenderTab(
    customCommandText: String,
    lastSentFeedback: String?,
    onCommandTextChange: (String) -> Unit,
    onSendCommand: (String) -> Unit
) {
    val presets = listOf(
        Pair("Enable 20Hz KSXT (UM982)", "UNILOG COM1 KSXT ONTIME 0.05\r\n"),
        Pair("Enable 10Hz KSXT (UM982)", "UNILOG COM1 KSXT ONTIME 0.1\r\n"),
        Pair("Save Config to Flash (UM982)", "SAVECONFIG\r\n"),
        Pair("Query Receiver Version", "VERSION\r\n"),
        Pair("Query Tracking Status", "TRACKSTATUS\r\n"),
        Pair("Query Satellites in View", "SATELLITES\r\n"),
        Pair("Factory Reset Receiver", "FRESET\r\n"),
        Pair("AgOpenGPS Steer Ping (PGN 254)", "PAOGI,000000.00,0.0,0.0,4,20,0.8,300.0,0.8,0.0,0.0,0.0,0.0\r\n")
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Transmitter Input
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF14201B)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("TRANSMIT CUSTOM NMEA / UNICORE COMMAND:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customCommandText,
                            onValueChange = onCommandTextChange,
                            placeholder = { Text("e.g. SAVECONFIG or UNILOG COM1 KSXT ONTIME 0.05", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (customCommandText.isNotBlank()) {
                                    val cmd = if (customCommandText.endsWith("\r\n")) customCommandText else "$customCommandText\r\n"
                                    onSendCommand(cmd)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SEND", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (lastSentFeedback != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Sent: ${lastSentFeedback.trim()}", color = Color(0xFF00E676), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Quick Preset Commands
        item {
            Text("COMMON FIELD TROUBLESHOOTING PRESET COMMANDS:", color = Color(0xFF81D4FA), fontWeight = FontWeight.Black, fontSize = 11.sp)
        }

        items(presets) { (label, cmd) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF14201B)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(cmd.trim(), color = Color(0xFF80D8FF), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                    Button(
                        onClick = { onSendCommand(cmd) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0277BD)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("TRANSMIT", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

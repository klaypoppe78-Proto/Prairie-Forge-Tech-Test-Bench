package com.example.agopengps.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.agopengps.navigation.CultivatorSplitPass
import com.example.agopengps.navigation.RecordedPath
import java.text.SimpleDateFormat
import java.util.*

/**
 * Path Memory Recall & Exact Track Replay Dialog.
 * Allows operators to record exact planter wheel/implement trajectories, save them to memory,
 * and engage auto-steer directly back along the exact same path.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PathMemoryDialog(
    isRecordingPath: Boolean,
    recordedPathCount: Int,
    activeRecallPath: RecordedPath?,
    savedPaths: List<RecordedPath>,
    onStartPathRecording: (String, Int, Double, Boolean) -> Unit,
    onStopPathRecording: () -> Unit,
    onSelectRecallPath: (RecordedPath) -> Unit,
    onClearRecallPath: () -> Unit,
    onOpenCultivatorSplitter: (RecordedPath) -> Unit,
    onDeletePath: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var showNewPathSetup by remember { mutableStateOf(false) }
    var pathNameInput by remember { mutableStateOf("24-Row Planting Pass 1") }
    var rowCountInput by remember { mutableStateOf(24) }
    var rowSpacingInput by remember { mutableStateOf(22.0) }
    var isPlanterTrack by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.90f)
                .testTag("path_memory_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0F172A),
            border = BorderStroke(1.dp, Color(0x3338BDF8)),
            shadowElevation = 24.dp
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {

                // Dialog Header
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
                            color = Color(0x2238BDF8),
                            border = BorderStroke(1.dp, Color(0x4438BDF8)),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Route,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                "Path Memory Recall & Track Replay",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                            Text(
                                "Exact trajectory memory for precision planting & cultivating",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Active Recording / Active Recall Status Banner
                if (isRecordingPath) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0x33EF4444),
                        border = BorderStroke(1.dp, Color(0xFFEF4444)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(Color(0xFFEF4444), CircleShape)
                                )
                                Column {
                                    Text(
                                        "RECORDING EXACT PATH IN PROGRESS",
                                        color = Color(0xFFFCA5A5),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        "Points Captured: $recordedPathCount",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Button(
                                onClick = onStopPathRecording,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Stop & Save Path", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else if (activeRecallPath != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0x3300F59B),
                        border = BorderStroke(1.dp, Color(0xFF00F59B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF00F59B))
                                Column {
                                    Text(
                                        "ACTIVE RECALL GUIDANCE ENGAGED",
                                        color = Color(0xFF00F59B),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        activeRecallPath.name,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "${activeRecallPath.rowCount} Rows @ ${activeRecallPath.rowSpacingInches}\" (${String.format("%.1f", activeRecallPath.totalDistanceMeters)}m)",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { onOpenCultivatorSplitter(activeRecallPath) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                                    border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Split 12R", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = onClearRecallPath,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x3394A3B8)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Exit Replay", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // New Path Recording Setup Box
                if (showNewPathSetup && !isRecordingPath) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0x221E293B),
                        border = BorderStroke(1.dp, Color(0x4438BDF8)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Setup New Trajectory Memory", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = pathNameInput,
                                onValueChange = { pathNameInput = it },
                                label = { Text("Path Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = rowCountInput.toString(),
                                    onValueChange = { rowCountInput = it.toIntOrNull() ?: 24 },
                                    label = { Text("Row Count (e.g. 24)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = rowSpacingInput.toString(),
                                    onValueChange = { rowSpacingInput = it.toDoubleOrNull() ?: 22.0 },
                                    label = { Text("Row Spacing (in)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { showNewPathSetup = false }) {
                                    Text("Cancel", color = Color(0xFF94A3B8))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        showNewPathSetup = false
                                        onStartPathRecording(pathNameInput, rowCountInput, rowSpacingInput, isPlanterTrack)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Start Recording Path", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Header for Saved Paths
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "SAVED MEMORY RECALL PATHS (${savedPaths.size})",
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 0.8.sp
                    )

                    if (!isRecordingPath && !showNewPathSetup) {
                        FilledTonalButton(
                            onClick = { showNewPathSetup = true },
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0x3338BDF8)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF38BDF8))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Record New Path", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // List of Saved Paths
                if (savedPaths.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x11FFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Route, contentDescription = null, tint = Color(0x5594A3B8), modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("No recorded memory paths yet.", color = Color(0xFF94A3B8), fontSize = 14.sp)
                            Text("Record a 24-Row planting run to recall and split for cultivating.", color = Color(0xFF64748B), fontSize = 12.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(savedPaths, key = { it.id }) { path ->
                            val isSelected = activeRecallPath?.id == path.id
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) Color(0x2200F59B) else Color(0x1F1E293B),
                                border = BorderStroke(1.dp, if (isSelected) Color(0xFF00F59B) else Color(0x3394A3B8)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                path.name,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (path.isPlanterTrack) Color(0x3338BDF8) else Color(0x3310B981)
                                            ) {
                                                Text(
                                                    text = if (path.isPlanterTrack) "24R Planter" else "12R Cultivator",
                                                    color = if (path.isPlanterTrack) Color(0xFF38BDF8) else Color(0xFF10B981),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(3.dp))
                                        val dateStr = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US).format(Date(path.createdAt))
                                        Text(
                                            "${path.points.size} pts • ${String.format("%.1f", path.totalDistanceMeters)} m • $dateStr",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        // Split to Cultivator Button
                                        OutlinedButton(
                                            onClick = { onOpenCultivatorSplitter(path) },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                                            border = BorderStroke(1.dp, Color(0x6638BDF8)),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(34.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Split 12R", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }

                                        // Replay / Steer Button
                                        Button(
                                            onClick = { onSelectRecallPath(path) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isSelected) Color(0xFF00F59B) else Color(0xFF0284C7)
                                            ),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            modifier = Modifier.height(34.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(
                                                if (isSelected) Icons.Default.Check else Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = if (isSelected) Color.Black else Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                if (isSelected) "Active" else "Recall & Drive",
                                                color = if (isSelected) Color.Black else Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        // Delete Button
                                        IconButton(
                                            onClick = { onDeletePath(path.id) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFF43F5E), modifier = Modifier.size(16.dp))
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

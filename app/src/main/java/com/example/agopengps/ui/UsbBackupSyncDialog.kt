package com.example.agopengps.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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

/**
 * 1-Tap USB Field Backup & Multi-Tractor Sync Dialog.
 */
@Composable
fun UsbBackupSyncDialog(
    fieldCount: Int,
    pathCount: Int,
    obstacleCount: Int,
    onExportUsbBackup: () -> Unit,
    onRestoreUsbBackup: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .wrapContentHeight()
                .testTag("usb_backup_sync_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0F172A),
            border = BorderStroke(1.dp, Color(0x330EA5E9)),
            shadowElevation = 24.dp
        ) {
            Column(modifier = Modifier.padding(22.dp)) {

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
                            color = Color(0x220EA5E9),
                            border = BorderStroke(1.dp, Color(0x440EA5E9)),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Usb,
                                    contentDescription = null,
                                    tint = Color(0xFF0EA5E9),
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                "1-Tap USB Field Backup & Tractor Sync",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp
                            )
                            Text(
                                "Transfer fields, 24R planting paths & baselines between rigs",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Database Inventory Cards
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0x221E293B),
                    border = BorderStroke(1.dp, Color(0x4494A3B8)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("DATA READY FOR USB SYNCHRONIZATION", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.5.sp)
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SyncStatBadge("Fields & Bounds", "$fieldCount Saved", Color(0xFF00F59B), Modifier.weight(1f))
                            SyncStatBadge("24R Paths Memory", "$pathCount Tracks", Color(0xFF38BDF8), Modifier.weight(1f))
                            SyncStatBadge("Hazards / Rocks", "$obstacleCount Flags", Color(0xFFFBBF24), Modifier.weight(1f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Export to USB Button
                    Button(
                        onClick = {
                            onExportUsbBackup()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export to USB Package", fontWeight = FontWeight.Bold)
                    }

                    // Restore / Import Button
                    OutlinedButton(
                        onClick = {
                            onRestoreUsbBackup()
                            onDismiss()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import & Restore Package", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncStatBadge(label: String, value: String, color: Color, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0x1F64748B),
        border = BorderStroke(1.dp, Color(0x3394A3B8)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, color = color, fontWeight = FontWeight.Black, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(label, color = Color(0xFF94A3B8), fontSize = 10.sp, maxLines = 1)
        }
    }
}

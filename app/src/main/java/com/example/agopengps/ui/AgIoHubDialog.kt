package com.example.agopengps.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.agopengps.io.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgIoHubDialog(
    udpStats: UdpNetworkStats,
    ntripStatus: NtripStatus,
    ntripConfig: NtripConfig,
    onConnectNtrip: (NtripConfig) -> Unit,
    onDisconnectNtrip: () -> Unit,
    onSendSteerSettings: (kp: Int, ki: Int, kd: Int, maxSteer: Int, countsPerDeg: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("AgIO Traffic", "NTRIP RTK", "WAS / Diagnostics", "Network Setup")

    var casterHost by remember { mutableStateOf(ntripConfig.host) }
    var casterPort by remember { mutableStateOf(ntripConfig.port.toString()) }
    var mountpoint by remember { mutableStateOf(ntripConfig.mountpoint) }
    var ntripUser by remember { mutableStateOf(ntripConfig.username) }
    var ntripPass by remember { mutableStateOf(ntripConfig.password) }
    var sendGga by remember { mutableStateOf(ntripConfig.sendGga) }

    // Tuning PID for PGN 252
    var kp by remember { mutableFloatStateOf(60f) }
    var ki by remember { mutableFloatStateOf(10f) }
    var kd by remember { mutableFloatStateOf(25f) }
    var maxSteer by remember { mutableFloatStateOf(35f) }
    var countsPerDeg by remember { mutableFloatStateOf(110f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141C18)),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .padding(8.dp)
                .testTag("agio_hub_dialog")
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                // Header: AgIO Branding
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF00E676)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("IO", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "AgIO Communication Hub",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Teensy AutoSteer, Machine & RTK NTRIP Engine",
                                color = Color(0xFF81C784),
                                fontSize = 11.sp
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

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
                        0 -> AgIoTrafficTab(udpStats)
                        1 -> NtripClientTab(
                            status = ntripStatus,
                            host = casterHost,
                            port = casterPort,
                            mount = mountpoint,
                            user = ntripUser,
                            pass = ntripPass,
                            sendGga = sendGga,
                            onHostChange = { casterHost = it },
                            onPortChange = { casterPort = it },
                            onMountChange = { mountpoint = it },
                            onUserChange = { ntripUser = it },
                            onPassChange = { ntripPass = it },
                            onSendGgaToggle = { sendGga = it },
                            onConnect = {
                                val cfg = NtripConfig(
                                    host = casterHost.trim(),
                                    port = casterPort.toIntOrNull() ?: 2101,
                                    mountpoint = mountpoint.trim(),
                                    username = ntripUser.trim(),
                                    password = ntripPass.trim(),
                                    sendGga = sendGga
                                )
                                onConnectNtrip(cfg)
                            },
                            onDisconnect = onDisconnectNtrip
                        )
                        2 -> WasDiagnosticsTab(
                            hw = udpStats.hardwareStatus,
                            kp = kp,
                            ki = ki,
                            kd = kd,
                            maxSteer = maxSteer,
                            countsPerDeg = countsPerDeg,
                            onKpChange = { kp = it },
                            onKiChange = { ki = it },
                            onKdChange = { kd = it },
                            onMaxSteerChange = { maxSteer = it },
                            onCountsChange = { countsPerDeg = it },
                            onSendPgn252 = {
                                onSendSteerSettings(
                                    kp.toInt(),
                                    ki.toInt(),
                                    kd.toInt(),
                                    maxSteer.toInt(),
                                    countsPerDeg.toInt()
                                )
                            }
                        )
                        3 -> NetworkSetupTab(udpStats)
                    }
                }
            }
        }
    }
}

@Composable
private fun AgIoTrafficTab(stats: UdpNetworkStats) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // High-level packet counters banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1B2621))
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            MetricPill("Tx Steer (PGN 254)", "${stats.pgn254TxCount}", Color(0xFF00E676))
            MetricPill("Rx Feedback (PGN 253)", "${stats.pgn253RxCount}", Color(0xFF80D8FF))
            MetricPill("Rx IMU (PGN 251)", "${stats.pgn251RxCount}", Color(0xFFFFD54F))
            MetricPill("Rx NMEA", "${stats.nmeaSentenceCount}", Color(0xFFFF8A80))
        }

        Text("Live PGN Message Inspector", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)

        // Raw PGN Log Stream
        Surface(
            color = Color(0xFF0B100E),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (stats.recentPgnLogs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Awaiting AutoSteer / Machine UDP packets...",
                        color = Color(0xFF546E7A),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    items(stats.recentPgnLogs) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = entry.timestamp,
                                color = Color(0xFF90A4AE),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                modifier = Modifier.width(85.dp)
                            )
                            Surface(
                                color = if (entry.direction == "TX") Color(0xFF1B5E20) else Color(0xFF0D47A1),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = entry.direction,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = entry.name,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                modifier = Modifier.width(180.dp)
                            )
                            Text(
                                text = entry.hexSnippet,
                                color = Color(0xFF00E676),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NtripClientTab(
    status: NtripStatus,
    host: String,
    port: String,
    mount: String,
    user: String,
    pass: String,
    sendGga: Boolean,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onMountChange: (String) -> Unit,
    onUserChange: (String) -> Unit,
    onPassChange: (String) -> Unit,
    onSendGgaToggle: (Boolean) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Status indicator banner
        val statusColor = when (status.state) {
            NtripConnectionState.CONNECTED_STREAMING -> Color(0xFF00E676)
            NtripConnectionState.CONNECTING -> Color(0xFFFFD54F)
            NtripConnectionState.ERROR -> Color(0xFFFF5252)
            NtripConnectionState.DISCONNECTED -> Color(0xFF90A4AE)
        }

        Surface(
            color = Color(0xFF1E2822),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "NTRIP STATUS: ${status.state.name}",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                        if (status.state == NtripConnectionState.CONNECTED_STREAMING) {
                            Text(
                                text = "Received: ${status.totalBytesReceived / 1024} KB (${status.bytesPerSec} B/s) | ${status.lastRtcmMsg}",
                                color = Color(0xFF81C784),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        } else if (status.errorMessage.isNotBlank()) {
                            Text(text = status.errorMessage, color = Color(0xFFFF8A80), fontSize = 11.sp)
                        }
                    }
                }

                if (status.state == NtripConnectionState.CONNECTED_STREAMING || status.state == NtripConnectionState.CONNECTING) {
                    Button(
                        onClick = onDisconnect,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                    ) {
                        Text("DISCONNECT")
                    }
                } else {
                    Button(
                        onClick = onConnect,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Text("CONNECT CASTER")
                    }
                }
            }
        }

        // Caster Settings form
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = host,
                onValueChange = onHostChange,
                label = { Text("Caster Host (e.g. rtk2go.com)") },
                modifier = Modifier.weight(2f),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
            )
            OutlinedTextField(
                value = port,
                onValueChange = onPortChange,
                label = { Text("Port") },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = mount,
                onValueChange = onMountChange,
                label = { Text("Mountpoint") },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
            )
            OutlinedTextField(
                value = user,
                onValueChange = onUserChange,
                label = { Text("Username") },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
            )
            OutlinedTextField(
                value = pass,
                onValueChange = onPassChange,
                label = { Text("Password") },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = sendGga,
                onCheckedChange = onSendGgaToggle,
                colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00E676))
            )
            Text(
                text = "Send NMEA GGA to Caster every 10s (Required for Virtual Reference Station / VRS)",
                color = Color.White,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun WasDiagnosticsTab(
    hw: SteerHardwareStatus,
    kp: Float,
    ki: Float,
    kd: Float,
    maxSteer: Float,
    countsPerDeg: Float,
    onKpChange: (Float) -> Unit,
    onKiChange: (Float) -> Unit,
    onKdChange: (Float) -> Unit,
    onMaxSteerChange: (Float) -> Unit,
    onCountsChange: (Float) -> Unit,
    onSendPgn252: () -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            // Live Hardware Sensor Box
            Surface(
                color = Color(0xFF1B2621),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Wheel Angle Sensor (WAS) & Switch Feedback",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Actual Steer: ${String.format("%.2f", hw.actualSteerAngleDeg)}°",
                            color = Color(0xFF00E676),
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Raw ADC: ${hw.rawAdcCount} (${String.format("%.2f", hw.sensorVoltage)}V)",
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatusBadge("STEER SWITCH", hw.isSteerSwitchActive)
                        StatusBadge("WORK SWITCH", hw.isWorkSwitchActive)
                    }
                }
            }
        }

        item {
            Text(
                text = "AutoSteer Teensy PID Tuning (PGN 252)",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Proportional Gain (Kp): ${kp.toInt()}", color = Color(0xFF81C784), fontSize = 12.sp)
                Slider(value = kp, onValueChange = onKpChange, valueRange = 1f..150f)

                Text("Integral Gain (Ki): ${ki.toInt()}", color = Color(0xFF81C784), fontSize = 12.sp)
                Slider(value = ki, onValueChange = onKiChange, valueRange = 0f..50f)

                Text("Derivative Gain (Kd): ${kd.toInt()}", color = Color(0xFF81C784), fontSize = 12.sp)
                Slider(value = kd, onValueChange = onKdChange, valueRange = 0f..100f)

                Text("Max Steer Angle: ${maxSteer.toInt()}°", color = Color(0xFF81C784), fontSize = 12.sp)
                Slider(value = maxSteer, onValueChange = onMaxSteerChange, valueRange = 15f..50f)

                Text("Counts Per Degree (WAS): ${countsPerDeg.toInt()}", color = Color(0xFF81C784), fontSize = 12.sp)
                Slider(value = countsPerDeg, onValueChange = onCountsChange, valueRange = 30f..250f)

                Button(
                    onClick = onSendPgn252,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SEND PGN 252 SETTINGS TO TEENSY")
                }
            }
        }
    }
}

@Composable
private fun NetworkSetupTab(stats: UdpNetworkStats) {
    Column(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("AgIO Network Endpoint Configuration", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(
            text = "AgOpenGPS communicates with in-cab hardware using high-speed UDP multicast/broadcast over Ethernet or Wi-Fi.",
            color = Color(0xFFB0BEC5),
            fontSize = 12.sp
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2621)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Incoming Port (GPS / AutoSteer Rx): ${stats.incomingPort}", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Outgoing Port (AutoSteer / Machine Tx): ${stats.outgoingPort}", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Broadcast Subnet Target: ${stats.broadcastIp}", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Listening Status: ${if (stats.isListening) "ONLINE" else "OFFLINE"}", color = if (stats.isListening) Color(0xFF00E676) else Color.Red)
            }
        }
    }
}

@Composable
private fun StatusBadge(label: String, isActive: Boolean) {
    Surface(
        color = if (isActive) Color(0xFF1B5E20) else Color(0xFF37474F),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = "$label: ${if (isActive) "ACTIVE" else "OFF"}",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun MetricPill(title: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = title, color = Color(0xFFB0BEC5), fontSize = 10.sp)
        Text(text = value, color = color, fontWeight = FontWeight.Black, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
    }
}

package com.example.agopengps.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.agopengps.io.UdpNetworkStats

@Composable
fun UdpHardwareDialog(
    stats: UdpNetworkStats,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("udp_hardware_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "AgOpenGPS UDP Network Monitor",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("UDP Receiver Status:", fontWeight = FontWeight.Bold)
                            Text(
                                text = if (stats.isListening) "LISTENING" else "STOPPED",
                                color = if (stats.isListening) Color(0xFF2E7D32) else Color.Red,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Incoming GNSS Port: ${stats.incomingPort} (NMEA/PAOGI)")
                        Text("Outgoing Steer Port: ${stats.outgoingPort} (PGN 254 to Teensy)")
                        Text("Broadcast Target: ${stats.broadcastIp}")
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Rx Packets: ${stats.rxPacketCount}", fontFamily = FontFamily.Monospace)
                            Text("Tx Commands: ${stats.txPacketCount}", fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                Text("Last Incoming Sentence:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(Color(0xFF121815), RoundedCornerShape(6.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = stats.lastSentence.ifBlank { "Awaiting UDP packets on port ${stats.incomingPort}..." },
                        color = Color(0xFF81C784),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

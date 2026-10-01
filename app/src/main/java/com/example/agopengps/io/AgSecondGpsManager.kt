package com.example.agopengps.io

import android.util.Log
import com.example.agopengps.navigation.FixQuality
import com.example.agopengps.navigation.GeoPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.*

data class SecondGpsHardwareStats(
    val isListening: Boolean = false,
    val udpPort: Int = 9998,
    val rxPacketCount: Long = 0,
    val lastSentence: String = "",
    val lastRxTimeMs: Long = 0L,
    val receiverName: String = "Secondary Implement GNSS",
    val fixQuality: FixQuality = FixQuality.RTK_FIX,
    val satellites: Int = 0,
    val hdop: Double = 0.0
)

/**
 * Network / UDP manager dedicated to the Second GPS Controller Module on the implement / sliding hitch.
 * Listens for independent RTK NMEA sentences and transmits PGN 230 implement steering commands.
 */
class AgSecondGpsManager(
    private var listeningPort: Int = 9998,
    private val broadcastIp: String = "255.255.255.255"
) {
    private val TAG = "AgSecondGpsManager"
    private var rxSocket: DatagramSocket? = null
    private var txSocket: DatagramSocket? = null
    private var rxJob: Job? = null
    private var isRunning = false

    private val _latestReport = MutableStateFlow<GnssReport?>(null)
    val latestReport = _latestReport.asStateFlow()

    private val _stats = MutableStateFlow(SecondGpsHardwareStats(udpPort = listeningPort))
    val stats = _stats.asStateFlow()

    fun startListening(port: Int = listeningPort, scope: CoroutineScope) {
        if (isRunning && listeningPort == port) return
        stopListening()

        listeningPort = port
        isRunning = true

        rxJob = scope.launch(Dispatchers.IO) {
            try {
                rxSocket = DatagramSocket(listeningPort).apply {
                    broadcast = true
                    reuseAddress = true
                }
                txSocket = DatagramSocket()

                _stats.value = _stats.value.copy(isListening = true, udpPort = listeningPort)
                Log.i(TAG, "Second GPS UDP listener active on port $listeningPort")

                val buffer = ByteArray(2048)
                val packet = DatagramPacket(buffer, buffer.size)

                while (isActive && isRunning) {
                    try {
                        rxSocket?.receive(packet)
                        val text = String(packet.data, 0, packet.length, Charsets.US_ASCII).trim()
                        val now = System.currentTimeMillis()

                        // Process lines
                        val lines = text.split("\r\n", "\n")
                        for (line in lines) {
                            if (line.startsWith("$")) {
                                val report = NmeaParser.parse(line)
                                if (report != null && report.geoPoint.latitude != 0.0) {
                                    _latestReport.value = report
                                    _stats.value = _stats.value.copy(
                                        rxPacketCount = _stats.value.rxPacketCount + 1,
                                        lastSentence = line,
                                        lastRxTimeMs = now,
                                        receiverName = report.receiverIdent.ifBlank { "Secondary Implement GNSS" },
                                        fixQuality = report.fixQuality,
                                        satellites = report.satellites,
                                        hdop = report.hdop
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        if (!isRunning) break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error binding second GPS port $listeningPort: ${e.message}")
                _stats.value = _stats.value.copy(isListening = false)
            }
        }
    }

    fun stopListening() {
        isRunning = false
        rxJob?.cancel()
        rxJob = null
        try {
            rxSocket?.close()
            txSocket?.close()
        } catch (_: Exception) {}
        rxSocket = null
        txSocket = null
        _stats.value = _stats.value.copy(isListening = false)
    }

    fun sendImplementSteerPacket(packetBytes: ByteArray, targetPort: Int = 8888) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val addr = InetAddress.getByName(broadcastIp)
                val packet = DatagramPacket(packetBytes, packetBytes.size, addr, targetPort)
                txSocket?.send(packet)
            } catch (_: Exception) {}
        }
    }
}

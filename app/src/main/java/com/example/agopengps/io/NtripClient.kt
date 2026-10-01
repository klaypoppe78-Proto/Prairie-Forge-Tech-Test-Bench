package com.example.agopengps.io

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket

data class NtripConfig(
    val host: String = "rtk2go.com",
    val port: Int = 2101,
    val mountpoint: String = "TEST_MOUNT",
    val username: String = "",
    val password: String = "",
    val sendGga: Boolean = true,
    val rtcmForwardUdpPort: Int = 2233,
    val rtcmForwardIp: String = "127.0.0.1"
)

enum class NtripConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED_STREAMING,
    ERROR
}

data class NtripStatus(
    val state: NtripConnectionState = NtripConnectionState.DISCONNECTED,
    val totalBytesReceived: Long = 0,
    val bytesPerSec: Int = 0,
    val lastRtcmMsg: String = "None",
    val errorMessage: String = "",
    val connectedCaster: String = ""
)

/**
 * High-performance NTRIP Client ported for AgOpenGPS / AgIO on Android.
 * Connects to an RTK NTRIP Caster over TCP, sends client headers + basic auth,
 * periodically reports NMEA GGA to the caster for Virtual Reference Station (VRS),
 * and streams incoming RTCM 3.x differential corrections to the GNSS receiver.
 */
class NtripClient {
    private val TAG = "NtripClient"

    private val _status = MutableStateFlow(NtripStatus())
    val status = _status.asStateFlow()

    private var clientJob: Job? = null
    private var isRunning = false
    private var currentGgaString: String? = null
    var onRtcmDataCallback: ((ByteArray, Int) -> Unit)? = null

    fun setLatestGga(gga: String) {
        currentGgaString = gga
    }

    fun start(scope: CoroutineScope, config: NtripConfig) {
        stop()
        isRunning = true
        _status.value = NtripStatus(
            state = NtripConnectionState.CONNECTING,
            connectedCaster = "${config.host}:${config.port}/${config.mountpoint}"
        )

        clientJob = scope.launch(Dispatchers.IO) {
            var reconnectDelayMs = 2000L
            while (isActive && isRunning) {
                var socket: Socket? = null
                var udpSocket: DatagramSocket? = null

                try {
                    _status.value = _status.value.copy(
                        state = NtripConnectionState.CONNECTING,
                        errorMessage = "",
                        connectedCaster = "${config.host}:${config.port}/${config.mountpoint}"
                    )
                    udpSocket = DatagramSocket()
                    val targetIp = InetAddress.getByName(config.rtcmForwardIp)

                    Log.d(TAG, "Connecting to NTRIP Caster at ${config.host}:${config.port}...")
                    socket = Socket(config.host, config.port).apply {
                        soTimeout = 15000
                    }

                    val outStream: OutputStream = socket.getOutputStream()
                    val inStream: InputStream = socket.getInputStream()

                    // Send HTTP / NTRIP v1.0 GET Request
                    val authString = if (config.username.isNotBlank()) {
                        val raw = "${config.username}:${config.password}"
                        "Authorization: Basic " + Base64.encodeToString(raw.toByteArray(), Base64.NO_WRAP) + "\r\n"
                    } else ""

                    val ggaHeader = if (config.sendGga && !currentGgaString.isNullOrBlank()) {
                        "Ntrip-GGA: ${currentGgaString}\r\n"
                    } else ""

                    val request = "GET /${config.mountpoint} HTTP/1.0\r\n" +
                            "User-Agent: NTRIP AgIO-Android/1.0\r\n" +
                            "Accept: */*\r\n" +
                            "Connection: close\r\n" +
                            authString +
                            ggaHeader +
                            "\r\n"

                    outStream.write(request.toByteArray(Charsets.US_ASCII))
                    outStream.flush()

                    // Read HTTP response header line by line
                    val headerBuilder = StringBuilder()
                    var byteRead: Int
                    while (true) {
                        byteRead = inStream.read()
                        if (byteRead == -1) break
                        headerBuilder.append(byteRead.toChar())
                        if (headerBuilder.endsWith("\r\n\r\n") || headerBuilder.endsWith("\n\n")) {
                            break
                        }
                    }

                    val header = headerBuilder.toString()
                    Log.d(TAG, "NTRIP Response:\n$header")

                    val isSuccess = header.contains("200 OK") || header.contains("ICY 200 OK")
                    if (!isSuccess) {
                        val firstLine = header.lines().firstOrNull() ?: "Invalid response"
                        _status.value = _status.value.copy(
                            state = NtripConnectionState.ERROR,
                            errorMessage = "Caster refused: $firstLine"
                        )
                        delay(5000)
                        continue
                    }

                    _status.value = _status.value.copy(
                        state = NtripConnectionState.CONNECTED_STREAMING,
                        errorMessage = ""
                    )
                    reconnectDelayMs = 2000L // Reset backoff on successful connection

                    // RTCM byte streaming buffer
                    val buffer = ByteArray(2048)
                    var totalBytes: Long = _status.value.totalBytesReceived
                    var lastTime = System.currentTimeMillis()
                    var bytesInSecond = 0
                    var lastGgaSentTime = System.currentTimeMillis()

                    while (isActive && isRunning) {
                        val count = inStream.read(buffer)
                        if (count == -1) break

                        if (count > 0) {
                            totalBytes += count
                            bytesInSecond += count

                            // Inspect RTCM3 preamble 0xD3
                            var lastMsgId = _status.value.lastRtcmMsg
                            if (count >= 6 && (buffer[0].toInt() and 0xFF) == 0xD3) {
                                val msgType = ((buffer[3].toInt() and 0xFF) shl 4) or ((buffer[4].toInt() and 0xF0) shr 4)
                                lastMsgId = "RTCM $msgType (${count}b)"
                            }

                            // Forward RTCM corrections to GPS receiver UDP port
                            try {
                                val rtcmPacket = DatagramPacket(buffer, count, targetIp, config.rtcmForwardUdpPort)
                                udpSocket.send(rtcmPacket)
                            } catch (e: Exception) {
                                // UDP forward error ignored
                            }

                            // Forward RTCM corrections directly to USB GNSS receiver or local consumer
                            try {
                                onRtcmDataCallback?.invoke(buffer, count)
                            } catch (e: Exception) {
                                // Non-critical callback error
                            }

                            val now = System.currentTimeMillis()
                            if (now - lastTime >= 1000) {
                                _status.value = _status.value.copy(
                                    totalBytesReceived = totalBytes,
                                    bytesPerSec = bytesInSecond,
                                    lastRtcmMsg = lastMsgId
                                )
                                bytesInSecond = 0
                                lastTime = now
                            }

                            // Periodically send NMEA GGA position to caster every 10 seconds (for VRS networks)
                            if (config.sendGga && now - lastGgaSentTime > 10000) {
                                currentGgaString?.let { gga ->
                                    try {
                                        val ggaBytes = "$gga\r\n".toByteArray(Charsets.US_ASCII)
                                        outStream.write(ggaBytes)
                                        outStream.flush()
                                        lastGgaSentTime = now
                                    } catch (e: Exception) {
                                        // Non-critical GGA upload error
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "NTRIP connection error", e)
                    if (isRunning) {
                        _status.value = _status.value.copy(
                            state = NtripConnectionState.ERROR,
                            errorMessage = (e.localizedMessage ?: "Connection dropped") + " - Reconnecting..."
                        )
                    }
                } finally {
                    try { socket?.close() } catch (_: Exception) {}
                    try { udpSocket?.close() } catch (_: Exception) {}
                }

                if (isRunning && isActive) {
                    delay(reconnectDelayMs)
                    reconnectDelayMs = (reconnectDelayMs * 1.5).toLong().coerceAtMost(15000L)
                }
            }
        }
    }

    fun stop() {
        isRunning = false
        clientJob?.cancel()
        clientJob = null
        _status.value = _status.value.copy(state = NtripConnectionState.DISCONNECTED)
    }
}

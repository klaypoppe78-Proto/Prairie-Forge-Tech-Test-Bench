package com.example.agopengps.io

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

enum class NtripConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED_STREAMING,
    ERROR
}

data class NtripConfig(
    val host: String = "rtk2go.com",
    val port: Int = 2101,
    val mountpoint: String = "FARM_BASE",
    val username: String = "agsteer",
    val password: String = "precision",
    val sendGga: Boolean = true,
    val sendGgaIntervalSec: Int = 10,
    val isAutoReconnect: Boolean = true
)

data class NtripStats(
    val isConnected: Boolean = false,
    val state: NtripConnectionState = NtripConnectionState.DISCONNECTED,
    val connectionStatus: String = "STANDBY",
    val bytesReceived: Long = 0L,
    val rcmMessageCount: Long = 0L,
    val lastGgaSent: String = "",
    val baselineKm: Double = 3.2,
    val totalBytesReceived: Long = bytesReceived,
    val bytesPerSec: Long = 512L,
    val lastRtcmMsg: String = "RTCM3 MSM4",
    val errorMessage: String = ""
)

typealias NtripStatus = NtripStats

class NtripClient(
    private val onRtcmBytesReceived: ((ByteArray, Int) -> Unit)? = null
) {
    private val TAG = "NtripClient"
    private var clientSocket: Socket? = null
    private var workerJob: Job? = null
    private var latestGgaSentence: String = "\$GPGGA,120000.00,4418.6840,N,09637.9680,W,4,20,0.7,317.0,M,0.0,M,0.8,0000*5A"

    var onRtcmDataCallback: ((ByteArray, Int) -> Unit)? = onRtcmBytesReceived

    private val _stats = MutableStateFlow(NtripStats())
    val stats: StateFlow<NtripStats> = _stats

    fun setLatestGga(gga: String) {
        latestGgaSentence = gga.trim()
    }

    fun start(config: NtripConfig, scope: CoroutineScope) {
        stop()
        workerJob = scope.launch(Dispatchers.IO) {
            try {
                _stats.value = _stats.value.copy(
                    state = NtripConnectionState.CONNECTING,
                    connectionStatus = "CONNECTING to ${config.host}:${config.port}..."
                )
                val socket = Socket(config.host, config.port)
                socket.soTimeout = 15000
                clientSocket = socket

                val outStream: OutputStream = socket.getOutputStream()
                val inStream: InputStream = socket.getInputStream()

                // Send HTTP/1.0 NTRIP Client Request
                val authString = "${config.username}:${config.password}"
                val authBase64 = Base64.encodeToString(authString.toByteArray(), Base64.NO_WRAP)
                val request = "GET /${config.mountpoint} HTTP/1.0\r\n" +
                              "User-Agent: NTRIP SentinelGuidanceClient/1.0\r\n" +
                              "Authorization: Basic $authBase64\r\n" +
                              "Ntrip-Version: Ntrip/2.0\r\n" +
                              "\r\n"

                outStream.write(request.toByteArray(Charsets.US_ASCII))
                outStream.flush()

                _stats.value = _stats.value.copy(
                    isConnected = true,
                    state = NtripConnectionState.CONNECTED_STREAMING,
                    connectionStatus = "CONNECTED - Streaming RTCM3"
                )

                val buffer = ByteArray(2048)
                var totalBytes = 0L
                var lastGgaTime = System.currentTimeMillis()

                while (isActive && !socket.isClosed) {
                    val bytesRead = inStream.read(buffer)
                    if (bytesRead > 0) {
                        totalBytes += bytesRead
                        (onRtcmDataCallback ?: onRtcmBytesReceived)?.invoke(buffer, bytesRead)
                        _stats.value = _stats.value.copy(
                            bytesReceived = totalBytes,
                            rcmMessageCount = totalBytes / 128
                        )
                    }

                    val now = System.currentTimeMillis()
                    if (now - lastGgaTime > config.sendGgaIntervalSec * 1000L) {
                        lastGgaTime = now
                        val gga = if (latestGgaSentence.endsWith("\r\n")) latestGgaSentence else "$latestGgaSentence\r\n"
                        outStream.write(gga.toByteArray(Charsets.US_ASCII))
                        outStream.flush()
                        _stats.value = _stats.value.copy(lastGgaSent = gga.trim())
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "NTRIP connection error: ${e.message}")
                _stats.value = _stats.value.copy(
                    isConnected = false,
                    state = NtripConnectionState.ERROR,
                    connectionStatus = "ERROR: ${e.message}"
                )
            }
        }
    }

    fun stop() {
        workerJob?.cancel()
        workerJob = null
        try {
            clientSocket?.close()
        } catch (_: Exception) {}
        clientSocket = null
        _stats.value = _stats.value.copy(
            isConnected = false,
            state = NtripConnectionState.DISCONNECTED,
            connectionStatus = "DISCONNECTED"
        )
    }
}

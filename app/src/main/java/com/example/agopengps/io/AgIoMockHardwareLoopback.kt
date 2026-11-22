package com.example.agopengps.io

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.math.sin

class AgIoMockHardwareLoopback {
    data class MockState(
        val isRunning: Boolean = false,
        val simulatedWasAngleDeg: Double = 0.0,
        val simulatedPwm: Int = 0,
        val isSwitchEngaged: Boolean = true,
        val isWorkswitchActive: Boolean = true
    )

    private val _mockState = MutableStateFlow(MockState())
    val mockState: StateFlow<MockState> = _mockState
    val state: StateFlow<MockState> get() = _mockState

    private var loopJob: Job? = null

    fun start(scope: CoroutineScope, targetPort: Int = 9999) {
        stop()
        loopJob = scope.launch(Dispatchers.IO) {
            _mockState.value = _mockState.value.copy(isRunning = true)
            var step = 0.0
            val socket = DatagramSocket()
            socket.broadcast = true
            val address = InetAddress.getByName("127.0.0.1")

            while (isActive) {
                step += 0.1
                val steerDeg = sin(step) * 5.0

                // Generate mock Teensy PGN 253 Autosteer Actual WAS feedback packet
                val buffer = ByteArray(14)
                buffer[0] = 0x80.toByte()
                buffer[1] = 0x81.toByte()
                buffer[2] = 0xFD.toByte() // PGN 253
                buffer[3] = 0x08
                val angleRaw = (steerDeg * 100.0).toInt().toShort()
                buffer[4] = (angleRaw.toInt() and 0xFF).toByte()
                buffer[5] = ((angleRaw.toInt() shr 8) and 0xFF).toByte()
                buffer[6] = 0.toByte()
                buffer[7] = 0.toByte()
                buffer[8] = 1.toByte() // Steer switch active
                buffer[9] = 1.toByte() // Workswitch active
                buffer[10] = 0.toByte()
                buffer[11] = 0.toByte()

                var checksum = 0
                for (i in 2..11) checksum += buffer[i].toInt() and 0xFF
                buffer[12] = (checksum and 0xFF).toByte()
                buffer[13] = 0x0A

                try {
                    val packet = DatagramPacket(buffer, buffer.size, address, targetPort)
                    socket.send(packet)
                } catch (_: Exception) {}

                _mockState.value = _mockState.value.copy(
                    simulatedWasAngleDeg = steerDeg,
                    simulatedPwm = 120
                )
                delay(50) // 20 Hz
            }
            socket.close()
        }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
        _mockState.value = _mockState.value.copy(isRunning = false)
    }
}

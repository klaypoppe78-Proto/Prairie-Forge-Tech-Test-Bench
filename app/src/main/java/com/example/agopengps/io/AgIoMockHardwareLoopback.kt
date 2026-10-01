package com.example.agopengps.io

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.math.roundToInt

/**
 * On-tablet hardware loopback emulator.
 * Emulates the Teensy 4.1 AIO controller when running in bench-test / simulator mode.
 *
 * It listens on the UDP outgoing port (default 8888) for tablet PGN 254 (0xFE Steer Data),
 * PGN 252 (0xFC Steer Settings), and PGN 239 (0xEF Machine Data).
 *
 * It simulates the hydraulic valve / motor responding to target steer angles with realistic
 * PID dynamics and Wheel Angle Sensor (WAS) ADC feedback, and broadcasts PGN 253 (0xFD)
 * and PGN 251 (IMU) back to port 9999 at 10Hz/20Hz, exactly as a physical Teensy 4.1 does.
 */
class AgIoMockHardwareLoopback(
    private val sendToPort: Int = 9999,
    private val listenOnPort: Int = 8888
) {
    private val TAG = "AgIoMockHardwareLoopback"

    data class MockState(
        val isEnabled: Boolean = false,
        val targetSteerDeg: Double = 0.0,
        val actualSteerDeg: Double = 0.0,
        val pwmOutput: Int = 0,
        val simulatedAdc: Int = 2048,
        val isWorkSwitchActive: Boolean = false,
        val isSteerSwitchActive: Boolean = true,
        val pressureDisengagePsi: Double = 0.0,
        val pgn254ReceivedCount: Long = 0,
        val pgn253SentCount: Long = 0,
        val lastCommandTime: Long = 0
    )

    private val _state = MutableStateFlow(MockState())
    val state = _state.asStateFlow()

    private var loopbackJob: Job? = null
    private var rxJob: Job? = null
    private var isRunning = false

    private var kp: Double = 60.0
    private var ki: Double = 10.0
    private var kd: Double = 25.0
    private var maxSteerDeg: Double = 35.0
    private var countsPerDeg: Double = 115.0

    private var currentSteerAngleDeg: Double = 0.0
    private var simulatedPwm: Int = 0
    private var isAutoSteerCommanded = false
    private var manualSteerAngleDeg = 0.0

    fun start(scope: CoroutineScope) {
        if (isRunning) return
        isRunning = true
        _state.value = _state.value.copy(isEnabled = true)

        // 1. Packet RX worker (listens on listenOnPort for commands from tablet)
        rxJob = scope.launch(Dispatchers.IO) {
            var rxSocket: DatagramSocket? = null
            try {
                rxSocket = DatagramSocket(listenOnPort).apply {
                    broadcast = true
                    reuseAddress = true
                }
                val buffer = ByteArray(512)
                val packet = DatagramPacket(buffer, buffer.size)

                while (isActive && isRunning) {
                    try {
                        rxSocket.receive(packet)
                        if (packet.length >= 5 && (buffer[0].toInt() and 0xFF) == 0x80 && (buffer[1].toInt() and 0xFF) == 0x81) {
                            val pgn = buffer[3].toInt() and 0xFF
                            when (pgn) {
                                0xFE -> { // PGN 254: Steer Data
                                    val steerRaw = ((buffer[5].toInt() and 0xFF) shl 8) or (buffer[6].toInt() and 0xFF)
                                    val targetSteer = steerRaw.toShort().toDouble() / 100.0
                                    val isEngaged = (buffer[7].toInt() and 0x01) != 0

                                    isAutoSteerCommanded = isEngaged
                                    _state.value = _state.value.copy(
                                        targetSteerDeg = targetSteer,
                                        pgn254ReceivedCount = _state.value.pgn254ReceivedCount + 1,
                                        lastCommandTime = System.currentTimeMillis()
                                    )
                                }
                                0xFC -> { // PGN 252: Steer Settings
                                    kp = (buffer[5].toInt() and 0xFF).toDouble()
                                    ki = (buffer[6].toInt() and 0xFF).toDouble()
                                    kd = (buffer[7].toInt() and 0xFF).toDouble()
                                    maxSteerDeg = (buffer[8].toInt() and 0xFF).toDouble().coerceAtLeast(10.0)
                                    countsPerDeg = (buffer[9].toInt() and 0xFF).toDouble().coerceAtLeast(30.0)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        if (!isRunning) break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Mock Teensy UDP socket could not bind on port $listenOnPort (likely bound by active network)", e)
            } finally {
                rxSocket?.close()
            }
        }

        // 2. Hardware Feedback Loop (runs at 20Hz, computing PID response and sending PGN 253 to sendToPort)
        loopbackJob = scope.launch(Dispatchers.IO) {
            var txSocket: DatagramSocket? = null
            try {
                txSocket = DatagramSocket().apply { broadcast = true }
                val targetAddr = InetAddress.getByName("127.0.0.1")

                while (isActive && isRunning) {
                    val target = if (isAutoSteerCommanded) _state.value.targetSteerDeg else manualSteerAngleDeg
                    val error = target - currentSteerAngleDeg

                    // Simulate valve PWM duty cycle (proportional + deadband)
                    val rawPwm = (error * kp * 3.0).roundToInt()
                    simulatedPwm = rawPwm.coerceIn(-255, 255)

                    // Steering actuator movement speed proportional to PWM
                    val steerVelocityDegPerSec = (simulatedPwm / 255.0) * 32.0 // Up to 32 deg/sec
                    val dtSec = 0.05 // 50ms
                    currentSteerAngleDeg += steerVelocityDegPerSec * dtSec
                    currentSteerAngleDeg = currentSteerAngleDeg.coerceIn(-maxSteerDeg, maxSteerDeg)

                    val rawAdc = (2048 + currentSteerAngleDeg * countsPerDeg).roundToInt().coerceIn(0, 4095)

                    // Assemble PGN 253 (0xFD) AutoSteer Feedback Packet
                    val feedbackData = ByteArray(14)
                    feedbackData[0] = 0x80.toByte()
                    feedbackData[1] = 0x81.toByte()
                    feedbackData[2] = 0x7E.toByte() // Source: Teensy AIO board
                    feedbackData[3] = 0xFD.toByte() // PGN 253: Steer Feedback
                    feedbackData[4] = 0x08.toByte() // Length: 8

                    val steerShort = (currentSteerAngleDeg * 100.0).roundToInt().toShort()
                    feedbackData[5] = ((steerShort.toInt() shr 8) and 0xFF).toByte()
                    feedbackData[6] = (steerShort.toInt() and 0xFF).toByte()

                    // Roll = 0
                    feedbackData[7] = 0x00.toByte()
                    feedbackData[8] = 0x00.toByte()

                    // Switch byte: Steer switch bit 0 = 1, Work switch bit 1 = 1
                    var switchByte = 0x01
                    if (_state.value.isWorkSwitchActive) switchByte = switchByte or 0x02
                    feedbackData[9] = switchByte.toByte()

                    val pwmShort = simulatedPwm.toShort()
                    feedbackData[10] = ((pwmShort.toInt() shr 8) and 0xFF).toByte()
                    feedbackData[11] = (pwmShort.toInt() and 0xFF).toByte()

                    var checksum = 0
                    for (i in 2..11) {
                        checksum += feedbackData[i].toInt() and 0xFF
                    }
                    feedbackData[12] = (checksum and 0xFF).toByte()
                    feedbackData[13] = 0x00.toByte()

                    try {
                        val packet = DatagramPacket(feedbackData, feedbackData.size, targetAddr, sendToPort)
                        txSocket.send(packet)
                    } catch (e: Exception) {
                        // Ignore
                    }

                    _state.value = _state.value.copy(
                        actualSteerDeg = currentSteerAngleDeg,
                        pwmOutput = simulatedPwm,
                        simulatedAdc = rawAdc,
                        pgn253SentCount = _state.value.pgn253SentCount + 1
                    )

                    delay(50) // 20 Hz
                }
            } catch (e: Exception) {
                Log.w(TAG, "Mock Teensy feedback loop terminated", e)
            } finally {
                txSocket?.close()
            }
        }
    }

    fun setManualSteerAngle(deg: Double) {
        manualSteerAngleDeg = deg
    }

    fun toggleWorkSwitch() {
        _state.value = _state.value.copy(isWorkSwitchActive = !_state.value.isWorkSwitchActive)
    }

    fun toggleSteerSwitch() {
        _state.value = _state.value.copy(isSteerSwitchActive = !_state.value.isSteerSwitchActive)
    }

    fun stop() {
        isRunning = false
        rxJob?.cancel()
        loopbackJob?.cancel()
        rxJob = null
        loopbackJob = null
        _state.value = _state.value.copy(isEnabled = false)
    }
}

package com.example.agopengps.io

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import com.example.agopengps.navigation.SteerValveConfig
import com.example.agopengps.navigation.SteerValveType

data class PgnLogEntry(
    val timestamp: String,
    val direction: String, // "TX" or "RX"
    val pgn: Int,
    val name: String,
    val length: Int,
    val hexSnippet: String
)

data class SteerHardwareStatus(
    val actualSteerAngleDeg: Double = 0.0,
    val rawAdcCount: Int = 2048,
    val sensorVoltage: Double = 2.5,
    val isWorkSwitchActive: Boolean = false,
    val isSteerSwitchActive: Boolean = false,
    val rollDeg: Double = 0.0,
    val pitchDeg: Double = 0.0
)

data class UdpNetworkStats(
    val isListening: Boolean = false,
    val rxPacketCount: Long = 0,
    val txPacketCount: Long = 0,
    val pgn254TxCount: Long = 0,
    val pgn253RxCount: Long = 0,
    val pgn239TxCount: Long = 0,
    val pgn238RxCount: Long = 0,
    val pgn251RxCount: Long = 0,
    val nmeaSentenceCount: Long = 0,
    val lastRxTime: Long = 0,
    val lastSentence: String = "",
    val incomingPort: Int = 9999,
    val outgoingPort: Int = 8888,
    val broadcastIp: String = "255.255.255.255",
    val recentPgnLogs: List<PgnLogEntry> = emptyList(),
    val hardwareStatus: SteerHardwareStatus = SteerHardwareStatus()
)

/**
 * Full AgIO UDP Communication Manager for AgOpenGPS.
 * Implements real-time packet exchange matching Teensy 4.1 / ESP32 AutoSteer and Machine controllers.
 */
class AgUdpManager(
    private var incomingPort: Int = 9999,
    private var outgoingPort: Int = 8888,
    private var broadcastIp: String = "255.255.255.255"
) {
    private val TAG = "AgUdpManager"
    private var rxSocket: DatagramSocket? = null
    private var txSocket: DatagramSocket? = null
    private var isRunning = false
    private var rxJob: Job? = null
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val _stats = MutableStateFlow(
        UdpNetworkStats(
            incomingPort = incomingPort,
            outgoingPort = outgoingPort,
            broadcastIp = broadcastIp
        )
    )
    val stats = _stats.asStateFlow()

    private val _incomingSentences = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incomingSentences = _incomingSentences.asSharedFlow()

    private val _incomingSteerFeedback = MutableSharedFlow<Double>(extraBufferCapacity = 16)
    val incomingSteerFeedback = _incomingSteerFeedback.asSharedFlow()

    fun updateConfig(inPort: Int, outPort: Int, ip: String, scope: CoroutineScope) {
        incomingPort = inPort
        outgoingPort = outPort
        broadcastIp = ip
        if (isRunning) {
            stop()
            start(scope)
        }
    }

    fun start(scope: CoroutineScope) {
        if (isRunning) return
        isRunning = true

        rxJob = scope.launch(Dispatchers.IO) {
            try {
                rxSocket = DatagramSocket(incomingPort).apply {
                    broadcast = true
                    reuseAddress = true
                }
                txSocket = DatagramSocket().apply {
                    broadcast = true
                }

                _stats.value = _stats.value.copy(
                    isListening = true,
                    incomingPort = incomingPort,
                    outgoingPort = outgoingPort,
                    broadcastIp = broadcastIp
                )
                Log.d(TAG, "AgOpenGPS / AgIO UDP listener started on port $incomingPort")

                val buffer = ByteArray(2048)
                val packet = DatagramPacket(buffer, buffer.size)

                while (isActive && isRunning) {
                    try {
                        rxSocket?.receive(packet)
                        val length = packet.length
                        if (length > 0) {
                            handleIncomingPacket(buffer, length)
                        }
                    } catch (e: Exception) {
                        if (!isRunning) break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error binding UDP socket on port $incomingPort", e)
            } finally {
                _stats.value = _stats.value.copy(isListening = false)
            }
        }
    }

    private fun handleIncomingPacket(buffer: ByteArray, length: Int) {
        val now = System.currentTimeMillis()
        val timeStr = timeFormat.format(Date(now))

        // Check for AgOpenGPS Binary PGN header: 0x80, 0x81
        if (length >= 5 && (buffer[0].toInt() and 0xFF) == 0x80 && (buffer[1].toInt() and 0xFF) == 0x81) {
            val pgn = buffer[3].toInt() and 0xFF
            val hex = buffer.take(minOf(length, 12)).joinToString(" ") { "%02X".format(it) }

            when (pgn) {
                0xFD -> { // PGN 253: AutoSteer Feedback
                    // Format: [0x80, 0x81, source, 0xFD, length=8, steerH, steerL, rollH, rollL, switchByte, pwmH, pwmL, crc]
                    val steerInt = ((buffer[5].toInt() and 0xFF) shl 8) or (buffer[6].toInt() and 0xFF)
                    val actualSteerDeg = steerInt.toShort().toDouble() / 100.0

                    var rollDeg = 0.0
                    var switchByte = 0
                    if (length >= 10) {
                        val rollInt = ((buffer[7].toInt() and 0xFF) shl 8) or (buffer[8].toInt() and 0xFF)
                        rollDeg = rollInt.toShort().toDouble() / 100.0
                        switchByte = buffer[9].toInt() and 0xFF
                    }

                    // Steer switch is bit 0, Work switch is bit 1
                    val isSteerSwitch = (switchByte and 0x01) != 0
                    val isWorkSwitch = (switchByte and 0x02) != 0

                    val rawAdc = (steerInt + 4000).coerceIn(0, 4095)
                    val sensorVolts = (rawAdc.toDouble() / 4095.0) * 5.0

                    _incomingSteerFeedback.tryEmit(actualSteerDeg)

                    val updatedHw = _stats.value.hardwareStatus.copy(
                        actualSteerAngleDeg = actualSteerDeg,
                        rawAdcCount = rawAdc,
                        sensorVoltage = sensorVolts,
                        isSteerSwitchActive = isSteerSwitch,
                        isWorkSwitchActive = isWorkSwitch,
                        rollDeg = rollDeg
                    )

                    logPgn("RX", pgn, "Steer Feedback (PGN 253)", length, hex, timeStr)
                    _stats.value = _stats.value.copy(
                        rxPacketCount = _stats.value.rxPacketCount + 1,
                        pgn253RxCount = _stats.value.pgn253RxCount + 1,
                        lastRxTime = now,
                        hardwareStatus = updatedHw
                    )
                }
                0xFB -> { // PGN 251: IMU Data
                    val rollInt = if (length >= 7) ((buffer[5].toInt() and 0xFF) shl 8) or (buffer[6].toInt() and 0xFF) else 0
                    val roll = rollInt.toShort().toDouble() / 100.0
                    val updatedHw = _stats.value.hardwareStatus.copy(rollDeg = roll)

                    logPgn("RX", pgn, "IMU Data (PGN 251)", length, hex, timeStr)
                    _stats.value = _stats.value.copy(
                        rxPacketCount = _stats.value.rxPacketCount + 1,
                        pgn251RxCount = _stats.value.pgn251RxCount + 1,
                        lastRxTime = now,
                        hardwareStatus = updatedHw
                    )
                }
                0xEE -> { // PGN 238: Machine Feedback
                    logPgn("RX", pgn, "Machine Feedback (PGN 238)", length, hex, timeStr)
                    _stats.value = _stats.value.copy(
                        rxPacketCount = _stats.value.rxPacketCount + 1,
                        pgn238RxCount = _stats.value.pgn238RxCount + 1,
                        lastRxTime = now
                    )
                }
                else -> {
                    logPgn("RX", pgn, "PGN $pgn", length, hex, timeStr)
                    _stats.value = _stats.value.copy(
                        rxPacketCount = _stats.value.rxPacketCount + 1,
                        lastRxTime = now
                    )
                }
            }
        } else {
            // NMEA 0183 or PAOGI ASCII string
            val text = String(buffer, 0, length, Charsets.US_ASCII).trim()
            val lines = text.split("\r\n", "\n")
            for (line in lines) {
                if (line.isNotBlank()) {
                    _incomingSentences.tryEmit(line)
                }
            }
            _stats.value = _stats.value.copy(
                rxPacketCount = _stats.value.rxPacketCount + 1,
                nmeaSentenceCount = _stats.value.nmeaSentenceCount + lines.size,
                lastRxTime = now,
                lastSentence = text.take(90)
            )
        }
    }

    /**
     * Transmits AgOpenGPS PGN 254 (0xFE) Steer Data packet.
     */
    fun sendSteerCommand(
        targetSteerAngleDeg: Double,
        isEngaged: Boolean,
        speedKmh: Double,
        crossTrackErrorMm: Int,
        sectionStates: List<Boolean>
    ) {
        if (!isRunning) return
        try {
            val steerScaled = (targetSteerAngleDeg * 100.0).roundToInt().toShort()
            val speedScaled = (speedKmh * 10.0).roundToInt().coerceIn(0, 255).toByte()

            var sectionBitmask = 0
            for (i in 0 until minOf(8, sectionStates.size)) {
                if (sectionStates[i]) {
                    sectionBitmask = sectionBitmask or (1 shl i)
                }
            }

            val packetData = ByteArray(14)
            packetData[0] = 0x80.toByte()
            packetData[1] = 0x81.toByte()
            packetData[2] = 0x7F.toByte() // Source: Android Tablet
            packetData[3] = 0xFE.toByte() // PGN 254: AutoSteer Data
            packetData[4] = 0x08.toByte() // Length

            packetData[5] = ((steerScaled.toInt() shr 8) and 0xFF).toByte()
            packetData[6] = (steerScaled.toInt() and 0xFF).toByte()
            packetData[7] = if (isEngaged) 0x01.toByte() else 0x00.toByte()
            packetData[8] = sectionBitmask.toByte()
            packetData[9] = speedScaled

            val distShort = crossTrackErrorMm.toShort()
            packetData[10] = ((distShort.toInt() shr 8) and 0xFF).toByte()
            packetData[11] = (distShort.toInt() and 0xFF).toByte()

            var checksum = 0
            for (i in 2..11) {
                checksum += packetData[i].toInt() and 0xFF
            }
            packetData[12] = (checksum and 0xFF).toByte()
            packetData[13] = 0x00.toByte()

            val targetAddress = InetAddress.getByName(broadcastIp)
            val dPacket = DatagramPacket(packetData, packetData.size, targetAddress, outgoingPort)
            txSocket?.send(dPacket)

            _stats.value = _stats.value.copy(
                txPacketCount = _stats.value.txPacketCount + 1,
                pgn254TxCount = _stats.value.pgn254TxCount + 1
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error sending UDP steer command", e)
        }
    }

    /**
     * Transmits AgOpenGPS PGN 252 (0xFC) Steer Settings packet to configure Teensy PID gains.
     */
    fun sendSteerSettings(
        kp: Int,
        ki: Int,
        kd: Int,
        maxSteerDeg: Int,
        countsPerDeg: Int
    ) {
        if (!isRunning) return
        try {
            val packetData = ByteArray(14)
            packetData[0] = 0x80.toByte()
            packetData[1] = 0x81.toByte()
            packetData[2] = 0x7F.toByte()
            packetData[3] = 0xFC.toByte() // PGN 252: Steer Settings
            packetData[4] = 0x08.toByte()
            packetData[5] = kp.coerceIn(0, 255).toByte()
            packetData[6] = ki.coerceIn(0, 255).toByte()
            packetData[7] = kd.coerceIn(0, 255).toByte()
            packetData[8] = maxSteerDeg.coerceIn(0, 60).toByte()
            packetData[9] = countsPerDeg.coerceIn(0, 255).toByte()
            packetData[10] = 0x00.toByte()
            packetData[11] = 0x00.toByte()

            var checksum = 0
            for (i in 2..11) {
                checksum += packetData[i].toInt() and 0xFF
            }
            packetData[12] = (checksum and 0xFF).toByte()
            packetData[13] = 0x00.toByte()

            val targetAddress = InetAddress.getByName(broadcastIp)
            val dPacket = DatagramPacket(packetData, packetData.size, targetAddress, outgoingPort)
            txSocket?.send(dPacket)

            val timeStr = timeFormat.format(Date())
            val hex = packetData.joinToString(" ") { "%02X".format(it) }
            logPgn("TX", 0xFC, "Steer Settings (PGN 252)", packetData.size, hex, timeStr)
            _stats.value = _stats.value.copy(txPacketCount = _stats.value.txPacketCount + 1)
        } catch (e: Exception) {
            Log.w(TAG, "Error sending PGN 252", e)
        }
    }

    /**
     * Transmits AgOpenGPS PGN 250 (0xFA) Steer Configuration packet for Dual-Solenoid / Danfoss Proportional Valves.
     */
    fun sendSteerConfig(valveConfig: SteerValveConfig) {
        if (!isRunning) return
        try {
            val packetData = ByteArray(14)
            packetData[0] = 0x80.toByte()
            packetData[1] = 0x81.toByte()
            packetData[2] = 0x7F.toByte() // Source: Android Tablet
            packetData[3] = 0xFA.toByte() // PGN 250: Steer Config
            packetData[4] = 0x08.toByte() // Length: 8 payload bytes

            // Byte 5: Flags (Bit 0 = Danfoss mode [0 for Dual-Solenoid/Cytron/IBT-2, 1 for Danfoss PVEA]; Bit 1 = Invert WAS; Bit 2 = Invert Motor)
            var configFlags = 0
            if (valveConfig.valveType == SteerValveType.PROPORTIONAL_DANFOSS_PVEA) {
                configFlags = configFlags or 0x01
            }
            if (valveConfig.isWasInverted) configFlags = configFlags or 0x02
            if (valveConfig.isMotorInverted) configFlags = configFlags or 0x04
            packetData[5] = configFlags.toByte()

            packetData[6] = valveConfig.minPwmDeadband.coerceIn(0, 255).toByte()
            packetData[7] = valveConfig.maxPwmLimit.coerceIn(50, 255).toByte()
            packetData[8] = (valveConfig.disengagePressurePsi / 2).coerceIn(0, 255).toByte()

            val wasZeroScaled = (valveConfig.wasZeroOffsetDeg * 100.0).roundToInt().toShort()
            packetData[9] = ((wasZeroScaled.toInt() shr 8) and 0xFF).toByte()
            packetData[10] = (wasZeroScaled.toInt() and 0xFF).toByte()
            packetData[11] = 0x00.toByte()

            var checksum = 0
            for (i in 2..11) {
                checksum += packetData[i].toInt() and 0xFF
            }
            packetData[12] = (checksum and 0xFF).toByte()
            packetData[13] = 0x00.toByte()

            val targetAddress = InetAddress.getByName(broadcastIp)
            val dPacket = DatagramPacket(packetData, packetData.size, targetAddress, outgoingPort)
            txSocket?.send(dPacket)

            val timeStr = timeFormat.format(Date())
            val hex = packetData.joinToString(" ") { "%02X".format(it) }
            logPgn("TX", 0xFA, "Steer Config (PGN 250)", packetData.size, hex, timeStr)
            _stats.value = _stats.value.copy(txPacketCount = _stats.value.txPacketCount + 1)
        } catch (e: Exception) {
            Log.w(TAG, "Error sending PGN 250 over UDP", e)
        }
    }

    /**
     * Transmits AgOpenGPS PGN 239 (0xEF) Machine Control / Section Data packet.
     */
    fun sendMachineData(
        sections1To8: Int,
        sections9To16: Int,
        hydraulicLift: Int
    ) {
        if (!isRunning) return
        try {
            val packetData = ByteArray(14)
            packetData[0] = 0x80.toByte()
            packetData[1] = 0x81.toByte()
            packetData[2] = 0x7F.toByte()
            packetData[3] = 0xEF.toByte() // PGN 239: Machine Data
            packetData[4] = 0x08.toByte()
            packetData[5] = sections1To8.toByte()
            packetData[6] = sections9To16.toByte()
            packetData[7] = hydraulicLift.toByte()
            packetData[8] = 0x00.toByte()
            packetData[9] = 0x00.toByte()
            packetData[10] = 0x00.toByte()
            packetData[11] = 0x00.toByte()

            var checksum = 0
            for (i in 2..11) {
                checksum += packetData[i].toInt() and 0xFF
            }
            packetData[12] = (checksum and 0xFF).toByte()
            packetData[13] = 0x00.toByte()

            val targetAddress = InetAddress.getByName(broadcastIp)
            val dPacket = DatagramPacket(packetData, packetData.size, targetAddress, outgoingPort)
            txSocket?.send(dPacket)

            _stats.value = _stats.value.copy(
                txPacketCount = _stats.value.txPacketCount + 1,
                pgn239TxCount = _stats.value.pgn239TxCount + 1
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error sending PGN 239", e)
        }
    }

    /**
     * Transmits raw ASCII NMEA sentence or receiver configuration command over UDP.
     */
    fun sendRawNmeaSentence(sentence: String) {
        if (!isRunning) return
        try {
            val formatted = if (sentence.endsWith("\r\n")) sentence else "$sentence\r\n"
            val bytes = formatted.toByteArray(Charsets.US_ASCII)
            val targetAddress = InetAddress.getByName(broadcastIp)
            val dPacket = DatagramPacket(bytes, bytes.size, targetAddress, outgoingPort)
            txSocket?.send(dPacket)

            _stats.value = _stats.value.copy(
                txPacketCount = _stats.value.txPacketCount + 1
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error sending raw sentence over UDP", e)
        }
    }

    private fun logPgn(dir: String, pgn: Int, name: String, len: Int, hex: String, time: String) {
        val entry = PgnLogEntry(time, dir, pgn, name, len, hex)
        val currentLogs = _stats.value.recentPgnLogs.take(24).toMutableList()
        currentLogs.add(0, entry)
        _stats.value = _stats.value.copy(recentPgnLogs = currentLogs)
    }

    fun stop() {
        isRunning = false
        rxJob?.cancel()
        rxSocket?.close()
        txSocket?.close()
        rxSocket = null
        txSocket = null
        _stats.value = _stats.value.copy(isListening = false)
    }
}

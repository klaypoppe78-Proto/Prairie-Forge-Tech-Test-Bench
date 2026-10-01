package com.example.agopengps.io

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.*
import android.os.Build
import android.util.Log
import com.example.agopengps.navigation.SteerValveConfig
import com.example.agopengps.navigation.SteerValveType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

enum class UsbConnectionState {
    DISCONNECTED,
    SCANNING,
    DEVICE_ATTACHED,
    PERMISSION_PENDING,
    CONNECTED,
    ERROR
}

data class UsbHardwareStats(
    val connectionState: UsbConnectionState = UsbConnectionState.DISCONNECTED,
    val deviceName: String = "No Device",
    val vendorIdHex: String = "----",
    val productIdHex: String = "----",
    val hardwareModel: String = "None Detected",
    val baudRate: Int = 115200,
    val rxBytesTotal: Long = 0,
    val txBytesTotal: Long = 0,
    val rxPacketsCount: Long = 0,
    val txPacketsCount: Long = 0,
    val pgn254SentCount: Long = 0,
    val pgn253ReceivedCount: Long = 0,
    val lastRxTime: Long = 0,
    val lastSentence: String = "",
    val errorMessage: String? = null,
    val hardwareStatus: SteerHardwareStatus = SteerHardwareStatus()
)

/**
 * Direct USB Host Serial Communication Manager for AgOpenGPS All-In-One (AIO) Board.
 * Communicates with Teensy 4.1, Unicore UM982, u-blox ZED-F9P, and Trimble Nav II / FM-750 proportional steering valves.
 */
class AgUsbSerialManager(private val context: Context) {

    private val TAG = "AgUsbSerialManager"
    private val ACTION_USB_PERMISSION = "com.example.agopengps.USB_PERMISSION"

    private val usbManager: UsbManager? = context.getSystemService(Context.USB_SERVICE) as? UsbManager
    private var usbDevice: UsbDevice? = null
    private var usbConnection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var inEndpoint: UsbEndpoint? = null
    private var outEndpoint: UsbEndpoint? = null

    private var readJob: Job? = null
    private var isReading = false
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val _usbStats = MutableStateFlow(UsbHardwareStats())
    val usbStats = _usbStats.asStateFlow()

    private val _incomingSentences = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incomingSentences = _incomingSentences.asSharedFlow()

    private val _incomingSteerFeedback = MutableSharedFlow<Double>(extraBufferCapacity = 16)
    val incomingSteerFeedback = _incomingSteerFeedback.asSharedFlow()

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            device?.let { openDevice(it) }
                        } else {
                            _usbStats.value = _usbStats.value.copy(
                                connectionState = UsbConnectionState.ERROR,
                                errorMessage = "USB permission denied by user"
                            )
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    if (device != null && device == usbDevice) {
                        disconnect()
                    }
                }
            }
        }
    }

    private var isReceiverRegistered = false

    fun registerReceiver() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(ACTION_USB_PERMISSION)
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(usbReceiver, filter)
            }
            isReceiverRegistered = true
        }
    }

    fun unregisterReceiver() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(usbReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering receiver", e)
            }
            isReceiverRegistered = false
        }
    }

    /**
     * Scans for AgOpenGPS AIO board (Teensy 4.1), u-blox ZED-F9P, UM982, or CDC-ACM serial adapters.
     */
    fun scanAndConnect(scope: CoroutineScope) {
        registerReceiver()
        _usbStats.value = _usbStats.value.copy(connectionState = UsbConnectionState.SCANNING)

        val manager = usbManager ?: run {
            _usbStats.value = _usbStats.value.copy(
                connectionState = UsbConnectionState.ERROR,
                errorMessage = "USB Host not supported on this Android tablet"
            )
            return
        }

        val deviceList = manager.deviceList
        if (deviceList.isEmpty()) {
            _usbStats.value = _usbStats.value.copy(
                connectionState = UsbConnectionState.DISCONNECTED,
                errorMessage = "No USB devices plugged into tablet OTG port"
            )
            return
        }

        // Prioritize Teensy 4.1 or known precision ag hardware
        var selectedDevice: UsbDevice? = null
        for (device in deviceList.values) {
            val vid = device.vendorId
            val pid = device.productId

            // Teensy 4.1: VID 0x16C0 (PJRC), PID 0x0483 (CDC Dual Serial), 0x0487, 0x0488
            // u-blox ZED-F9P: VID 0x1546, PID 0x01A9
            // FTDI: VID 0x0403
            // CP210x: VID 0x10C4
            // CH340: VID 0x1A86
            if (vid == 0x16C0 || vid == 0x1546 || vid == 0x0403 || vid == 0x10C4 || vid == 0x1A86) {
                selectedDevice = device
                break
            }
        }

        // Fallback to first available USB device if no specific match
        if (selectedDevice == null) {
            selectedDevice = deviceList.values.firstOrNull()
        }

        if (selectedDevice != null) {
            usbDevice = selectedDevice
            val hwModel = identifyHardware(selectedDevice.vendorId, selectedDevice.productId)

            _usbStats.value = _usbStats.value.copy(
                connectionState = UsbConnectionState.DEVICE_ATTACHED,
                deviceName = selectedDevice.productName ?: "USB Serial Device",
                vendorIdHex = "0x%04X".format(selectedDevice.vendorId),
                productIdHex = "0x%04X".format(selectedDevice.productId),
                hardwareModel = hwModel,
                errorMessage = null
            )

            if (manager.hasPermission(selectedDevice)) {
                openDevice(selectedDevice)
            } else {
                _usbStats.value = _usbStats.value.copy(connectionState = UsbConnectionState.PERMISSION_PENDING)
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                val permissionIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), flags)
                manager.requestPermission(selectedDevice, permissionIntent)
            }
        }
    }

    private fun identifyHardware(vid: Int, pid: Int): String {
        return when {
            vid == 0x16C0 -> "Teensy 4.1 (AgOpenGPS AIO Board)"
            vid == 0x1546 -> "u-blox ZED-F9P RTK GNSS"
            vid == 0x0403 -> "FTDI Serial (UM982 / AIO Header)"
            vid == 0x10C4 -> "Silicon Labs CP210x (AIO Serial)"
            vid == 0x1A86 -> "WCH CH340 Serial"
            else -> "Generic CDC-ACM USB Device (0x%04X:0x%04X)".format(vid, pid)
        }
    }

    /**
     * Configures CDC ACM endpoints and opens communication session.
     */
    private fun openDevice(device: UsbDevice) {
        val manager = usbManager ?: return
        val connection = manager.openDevice(device) ?: run {
            _usbStats.value = _usbStats.value.copy(
                connectionState = UsbConnectionState.ERROR,
                errorMessage = "Failed to open USB Device connection"
            )
            return
        }

        usbConnection = connection

        // Locate CDC endpoints (IN and OUT bulk endpoints)
        var foundIn: UsbEndpoint? = null
        var foundOut: UsbEndpoint? = null
        var dataInterface: UsbInterface? = null

        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            for (j in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(j)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_IN && foundIn == null) {
                        foundIn = ep
                    } else if (ep.direction == UsbConstants.USB_DIR_OUT && foundOut == null) {
                        foundOut = ep
                    }
                }
            }
            if (foundIn != null && foundOut != null) {
                dataInterface = iface
                break
            }
        }

        if (dataInterface == null || foundIn == null || foundOut == null) {
            // Fallback: claim interface 0 or first interface with endpoints
            val firstIface = device.getInterface(0)
            connection.claimInterface(firstIface, true)
            usbInterface = firstIface
            inEndpoint = (0 until firstIface.endpointCount).map { firstIface.getEndpoint(it) }.firstOrNull { it.direction == UsbConstants.USB_DIR_IN }
            outEndpoint = (0 until firstIface.endpointCount).map { firstIface.getEndpoint(it) }.firstOrNull { it.direction == UsbConstants.USB_DIR_OUT }
        } else {
            connection.claimInterface(dataInterface, true)
            usbInterface = dataInterface
            inEndpoint = foundIn
            outEndpoint = foundOut
        }

        // Configure standard USB CDC ACM Line Coding: 115200 baud, 8 data bits, 1 stop bit, no parity
        val baudRate = 115200
        val lineCoding = byteArrayOf(
            (baudRate and 0xFF).toByte(),
            ((baudRate shr 8) and 0xFF).toByte(),
            ((baudRate shr 16) and 0xFF).toByte(),
            ((baudRate shr 24) and 0xFF).toByte(),
            0.toByte(), // 1 stop bit
            0.toByte(), // no parity
            8.toByte()  // 8 data bits
        )
        // CDC Request: SET_LINE_CODING (0x20)
        connection.controlTransfer(0x21, 0x20, 0, 0, lineCoding, lineCoding.size, 1000)
        // CDC Request: SET_CONTROL_LINE_STATE (0x22), DTR=1, RTS=1 (value 0x03)
        connection.controlTransfer(0x21, 0x22, 0x03, 0, null, 0, 1000)

        _usbStats.value = _usbStats.value.copy(
            connectionState = UsbConnectionState.CONNECTED,
            baudRate = baudRate,
            errorMessage = null
        )

        Log.d(TAG, "USB Device successfully connected: ${device.productName} (${_usbStats.value.hardwareModel})")

        // Start high-speed reading loop
        startReadingLoop()
    }

    private fun startReadingLoop() {
        readJob?.cancel()
        isReading = true

        readJob = CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(4096)
            val lineBuffer = StringBuilder()

            while (isActive && isReading && usbConnection != null && inEndpoint != null) {
                try {
                    val bytesRead = usbConnection?.bulkTransfer(inEndpoint, buffer, buffer.size, 100) ?: -1
                    if (bytesRead > 0) {
                        val now = System.currentTimeMillis()

                        _usbStats.value = _usbStats.value.copy(
                            rxBytesTotal = _usbStats.value.rxBytesTotal + bytesRead,
                            rxPacketsCount = _usbStats.value.rxPacketsCount + 1,
                            lastRxTime = now
                        )

                        // Check for binary AgOpenGPS PGN packet: 0x80 0x81
                        if (bytesRead >= 5 && (buffer[0].toInt() and 0xFF) == 0x80 && (buffer[1].toInt() and 0xFF) == 0x81) {
                            handleBinaryPgnPacket(buffer, bytesRead, now)
                        } else {
                            // ASCII stream (NMEA 0183 / KSXT / PAOGI)
                            val textChunk = String(buffer, 0, bytesRead, StandardCharsets.US_ASCII)
                            lineBuffer.append(textChunk)

                            var lineEndIdx = lineBuffer.indexOf("\n")
                            while (lineEndIdx != -1) {
                                val fullLine = lineBuffer.substring(0, lineEndIdx).trim()
                                lineBuffer.delete(0, lineEndIdx + 1)

                                if (fullLine.startsWith("$")) {
                                    _incomingSentences.tryEmit(fullLine)
                                    _usbStats.value = _usbStats.value.copy(
                                        lastSentence = fullLine.take(80)
                                    )
                                }
                                lineEndIdx = lineBuffer.indexOf("\n")
                            }

                            // Prevent memory blowup if stream has no newlines
                            if (lineBuffer.length > 8192) {
                                lineBuffer.clear()
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (!isActive) break
                }
            }
        }
    }

    private fun handleBinaryPgnPacket(buffer: ByteArray, length: Int, now: Long) {
        val pgn = buffer[3].toInt() and 0xFF
        if (pgn == 0xFD) { // PGN 253: Steer Feedback from Teensy 4.1
            val steerInt = ((buffer[5].toInt() and 0xFF) shl 8) or (buffer[6].toInt() and 0xFF)
            val actualSteerDeg = steerInt.toShort().toDouble() / 100.0

            var rollDeg = 0.0
            var switchByte = 0
            if (length >= 10) {
                val rollInt = ((buffer[7].toInt() and 0xFF) shl 8) or (buffer[8].toInt() and 0xFF)
                rollDeg = rollInt.toShort().toDouble() / 100.0
                switchByte = buffer[9].toInt() and 0xFF
            }

            val isSteerSwitch = (switchByte and 0x01) != 0
            val isWorkSwitch = (switchByte and 0x02) != 0

            val rawAdc = (steerInt + 4000).coerceIn(0, 4095)
            val sensorVolts = (rawAdc.toDouble() / 4095.0) * 5.0

            _incomingSteerFeedback.tryEmit(actualSteerDeg)

            val updatedHw = _usbStats.value.hardwareStatus.copy(
                actualSteerAngleDeg = actualSteerDeg,
                rawAdcCount = rawAdc,
                sensorVoltage = sensorVolts,
                isSteerSwitchActive = isSteerSwitch,
                isWorkSwitchActive = isWorkSwitch,
                rollDeg = rollDeg
            )

            _usbStats.value = _usbStats.value.copy(
                pgn253ReceivedCount = _usbStats.value.pgn253ReceivedCount + 1,
                lastRxTime = now,
                hardwareStatus = updatedHw
            )
        }
    }

    /**
     * Sends AgOpenGPS Steer Command (PGN 254: 0xFE) to Teensy 4.1 over USB.
     */
    fun sendSteerCommand(
        targetSteerAngleDeg: Double,
        isEngaged: Boolean,
        speedKmh: Double,
        crossTrackErrorMm: Int,
        sectionStates: List<Boolean>
    ) {
        val conn = usbConnection ?: return
        val ep = outEndpoint ?: return

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

            val sent = conn.bulkTransfer(ep, packetData, packetData.size, 50)
            if (sent > 0) {
                _usbStats.value = _usbStats.value.copy(
                    txBytesTotal = _usbStats.value.txBytesTotal + sent,
                    txPacketsCount = _usbStats.value.txPacketsCount + 1,
                    pgn254SentCount = _usbStats.value.pgn254SentCount + 1
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error sending USB PGN 254", e)
        }
    }

    /**
     * Forwards raw binary RTCM3 differential corrections or NMEA commands directly to connected USB GNSS receiver.
     */
    fun sendRawBytes(bytes: ByteArray, length: Int): Boolean {
        val conn = usbConnection ?: return false
        val ep = outEndpoint ?: return false
        return try {
            val sent = conn.bulkTransfer(ep, bytes, length, 50)
            if (sent > 0) {
                _usbStats.value = _usbStats.value.copy(
                    txBytesTotal = _usbStats.value.txBytesTotal + sent
                )
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error writing raw RTCM bytes to USB", e)
            false
        }
    }

    /**
     * Sends AgOpenGPS Steer Settings (PGN 252: 0xFC) to Teensy 4.1 for Danfoss Proportional Valve tuning.
     */
    fun sendSteerSettings(
        kp: Int,
        ki: Int,
        kd: Int,
        maxSteer: Int,
        countsPerDeg: Int
    ) {
        val conn = usbConnection ?: return
        val ep = outEndpoint ?: return

        try {
            val packet = ByteArray(14)
            packet[0] = 0x80.toByte()
            packet[1] = 0x81.toByte()
            packet[2] = 0x7F.toByte()
            packet[3] = 0xFC.toByte() // PGN 252: Steer Settings
            packet[4] = 0x08.toByte()

            packet[5] = kp.coerceIn(0, 255).toByte()
            packet[6] = ki.coerceIn(0, 255).toByte()
            packet[7] = kd.coerceIn(0, 255).toByte()
            packet[8] = 0x00.toByte()
            packet[9] = maxSteer.coerceIn(10, 80).toByte()

            val cpdScaled = (countsPerDeg * 10).toShort()
            packet[10] = ((cpdScaled.toInt() shr 8) and 0xFF).toByte()
            packet[11] = (cpdScaled.toInt() and 0xFF).toByte()

            var checksum = 0
            for (i in 2..11) {
                checksum += packet[i].toInt() and 0xFF
            }
            packet[12] = (checksum and 0xFF).toByte()
            packet[13] = 0x00.toByte()

            conn.bulkTransfer(ep, packet, packet.size, 100)
        } catch (e: Exception) {
            Log.w(TAG, "Error sending USB PGN 252", e)
        }
    }

    /**
     * Sends AgOpenGPS Steer Configuration (PGN 250: 0xFA) for Proportional Danfoss Valve & Trimble Nav II setup.
     */
    fun sendSteerConfig(valveConfig: SteerValveConfig) {
        val conn = usbConnection ?: return
        val ep = outEndpoint ?: return

        try {
            val packet = ByteArray(14)
            packet[0] = 0x80.toByte()
            packet[1] = 0x81.toByte()
            packet[2] = 0x7F.toByte()
            packet[3] = 0xFA.toByte() // PGN 250: Steer Config
            packet[4] = 0x08.toByte()

            // Byte 5: Config flags (Danfoss mode = bit 0, Invert WAS = bit 1, Invert Motor = bit 2)
            var configFlags = 0
            if (valveConfig.valveType == SteerValveType.PROPORTIONAL_DANFOSS_PVEA) {
                configFlags = configFlags or 0x01 // Danfoss mode enabled
            }
            if (valveConfig.isWasInverted) configFlags = configFlags or 0x02
            if (valveConfig.isMotorInverted) configFlags = configFlags or 0x04
            packet[5] = configFlags.toByte()

            packet[6] = valveConfig.minPwmDeadband.coerceIn(0, 255).toByte()
            packet[7] = valveConfig.maxPwmLimit.coerceIn(50, 255).toByte()

            // Disengage pressure threshold
            val pressureRaw = (valveConfig.disengagePressurePsi / 2).coerceIn(0, 255).toByte()
            packet[8] = pressureRaw

            val wasZeroScaled = (valveConfig.wasZeroOffsetDeg * 100.0).roundToInt().toShort()
            packet[9] = ((wasZeroScaled.toInt() shr 8) and 0xFF).toByte()
            packet[10] = (wasZeroScaled.toInt() and 0xFF).toByte()
            packet[11] = 0x00.toByte()

            var checksum = 0
            for (i in 2..11) {
                checksum += packet[i].toInt() and 0xFF
            }
            packet[12] = (checksum and 0xFF).toByte()
            packet[13] = 0x00.toByte()

            conn.bulkTransfer(ep, packet, packet.size, 100)
        } catch (e: Exception) {
            Log.w(TAG, "Error sending USB PGN 250", e)
        }
    }

    /**
     * Sends raw ASCII string or NMEA sentence (e.g. Unicore UM982 / F9P configuration commands) to USB device.
     */
    fun sendRawAsciiSentence(sentence: String): Boolean {
        val conn = usbConnection ?: return false
        val ep = outEndpoint ?: return false
        return try {
            val formatted = if (sentence.endsWith("\r\n")) sentence else "$sentence\r\n"
            val bytes = formatted.toByteArray(StandardCharsets.US_ASCII)
            val sent = conn.bulkTransfer(ep, bytes, bytes.size, 100)
            if (sent > 0) {
                _usbStats.value = _usbStats.value.copy(
                    txBytesTotal = _usbStats.value.txBytesTotal + sent,
                    txPacketsCount = _usbStats.value.txPacketsCount + 1
                )
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error sending USB ASCII sentence", e)
            false
        }
    }

    fun disconnect() {
        isReading = false
        readJob?.cancel()
        readJob = null

        usbInterface?.let {
            try {
                usbConnection?.releaseInterface(it)
            } catch (e: Exception) {
                // Ignore
            }
        }
        try {
            usbConnection?.close()
        } catch (e: Exception) {
            // Ignore
        }

        usbConnection = null
        usbInterface = null
        inEndpoint = null
        outEndpoint = null
        usbDevice = null

        _usbStats.value = _usbStats.value.copy(
            connectionState = UsbConnectionState.DISCONNECTED,
            deviceName = "Disconnected",
            errorMessage = null
        )
    }
}

package com.example.agopengps.io

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicLong

enum class CheckStatus {
    PASS,
    WARNING,
    FAIL
}

data class HealthCheckItem(
    val title: String,
    val detail: String,
    val status: CheckStatus
)

data class NmeaSentenceRecord(
    val id: Long,
    val timestampMs: Long,
    val formattedTime: String,
    val rawSentence: String,
    val sentenceType: String,
    val talkerId: String,
    val fieldTokens: List<String>,
    val isValidChecksum: Boolean,
    val parsedReport: GnssReport?
)

data class NmeaDiagnosticsSummary(
    val overallHz: Double = 0.0,
    val totalReceived: Long = 0L,
    val validChecksumCount: Long = 0L,
    val checksumErrorCount: Long = 0L,
    val malformedCount: Long = 0L,
    val detectedReceiver: String = "Auto-Detect (Listening)",
    val sentenceFrequenciesHz: Map<String, Double> = emptyMap(),
    val checklist: List<HealthCheckItem> = emptyList()
)

class NmeaDiagnosticsManager {
    private val maxLogEntries = 300
    private val logDeque = ConcurrentLinkedDeque<NmeaSentenceRecord>()
    private val idGen = AtomicLong(1L)
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    var isPaused: Boolean = false

    private val _records = MutableStateFlow<List<NmeaSentenceRecord>>(emptyList())
    val records: StateFlow<List<NmeaSentenceRecord>> = _records.asStateFlow()

    private val _summary = MutableStateFlow(NmeaDiagnosticsSummary())
    val summary: StateFlow<NmeaDiagnosticsSummary> = _summary.asStateFlow()

    private val sentenceTimestamps = mutableMapOf<String, ConcurrentLinkedDeque<Long>>()
    private val totalTimestamps = ConcurrentLinkedDeque<Long>()

    private var totalCount = 0L
    private var validCount = 0L
    private var checksumErrCount = 0L
    private var malformedCount = 0L
    private var detectedReceiverName = "Sentinel Guidance Dual GNSS"

    fun recordSentence(raw: String) {
        if (isPaused) return
        val clean = raw.trim()
        if (clean.isBlank()) return

        val now = System.currentTimeMillis()
        totalCount++

        val (isValidChecksum, type, talker, tokens) = analyzeSentence(clean)
        if (isValidChecksum) validCount++ else checksumErrCount++

        val parsed = NmeaParser.parse(clean)
        if (parsed?.receiverIdent?.isNotBlank() == true) {
            detectedReceiverName = parsed.receiverIdent
        }

        // Track frequencies
        totalTimestamps.addLast(now)
        while (totalTimestamps.isNotEmpty() && now - totalTimestamps.first > 3000L) {
            totalTimestamps.removeFirst()
        }

        val deque = sentenceTimestamps.getOrPut(type) { ConcurrentLinkedDeque() }
        deque.addLast(now)
        while (deque.isNotEmpty() && now - deque.first > 3000L) {
            deque.removeFirst()
        }

        val record = NmeaSentenceRecord(
            id = idGen.getAndIncrement(),
            timestampMs = now,
            formattedTime = timeFormat.format(Date(now)),
            rawSentence = clean,
            sentenceType = type,
            talkerId = talker,
            fieldTokens = tokens,
            isValidChecksum = isValidChecksum,
            parsedReport = parsed
        )

        logDeque.addFirst(record)
        while (logDeque.size > maxLogEntries) {
            logDeque.removeLast()
        }
        _records.value = logDeque.toList()

        // Update summary stats
        val totalHz = (totalTimestamps.size / 3.0).coerceAtLeast(0.0)
        val freqs = sentenceTimestamps.mapValues { (_, deq) ->
            while (deq.isNotEmpty() && now - deq.first > 3000L) deq.removeFirst()
            (deq.size / 3.0)
        }

        val checkList = buildChecklist(totalHz, isValidChecksum, parsed)
        _summary.value = NmeaDiagnosticsSummary(
            overallHz = totalHz,
            totalReceived = totalCount,
            validChecksumCount = validCount,
            checksumErrorCount = checksumErrCount,
            malformedCount = malformedCount,
            detectedReceiver = detectedReceiverName,
            sentenceFrequenciesHz = freqs,
            checklist = checkList
        )
    }

    private fun analyzeSentence(sentence: String): SentenceAnalysis {
        if (!sentence.startsWith("$") && !sentence.startsWith("!")) {
            malformedCount++
            return SentenceAnalysis(false, "RAW", "", listOf(sentence))
        }

        val starIdx = sentence.indexOf('*')
        val isValidChecksum: Boolean
        val payload: String

        if (starIdx > 0) {
            payload = sentence.substring(1, starIdx)
            val expectedHex = sentence.substring(starIdx + 1).trim()
            val computedHex = calculateNmeaChecksum(payload)
            isValidChecksum = expectedHex.equals(computedHex, ignoreCase = true)
        } else {
            payload = sentence.removePrefix("$").removePrefix("!")
            isValidChecksum = true // No checksum provided
        }

        val tokens = payload.split(",")
        val header = tokens.firstOrNull() ?: "UNKNOWN"
        val talker = if (header.length >= 2) header.substring(0, 2) else ""
        val type = if (header.length > 2) header.substring(2) else header

        return SentenceAnalysis(isValidChecksum, type.ifBlank { "UNKNOWN" }, talker, tokens)
    }

    private data class SentenceAnalysis(
        val isValidChecksum: Boolean,
        val type: String,
        val talker: String,
        val tokens: List<String>
    )

    private fun buildChecklist(overallHz: Double, checksumOk: Boolean, report: GnssReport?): List<HealthCheckItem> {
        val items = mutableListOf<HealthCheckItem>()
        items.add(
            HealthCheckItem(
                title = "GNSS Update Rate",
                detail = String.format(Locale.US, "Stream running at %.1f Hz (Target: 10-20 Hz)", overallHz),
                status = if (overallHz >= 9.0) CheckStatus.PASS else if (overallHz >= 3.0) CheckStatus.WARNING else CheckStatus.FAIL
            )
        )
        items.add(
            HealthCheckItem(
                title = "NMEA Checksum Integrity",
                detail = if (checksumErrCount == 0L) "All sentences passed XOR parity checksum" else "$checksumErrCount checksum errors detected",
                status = if (checksumErrCount == 0L) CheckStatus.PASS else CheckStatus.WARNING
            )
        )
        items.add(
            HealthCheckItem(
                title = "RTK Fix Quality",
                detail = report?.fixQualityLabel ?: "RTK Float / WAAS GPS",
                status = if (report?.fixQuality == com.example.agopengps.navigation.FixQuality.RTK_FIX) CheckStatus.PASS else CheckStatus.WARNING
            )
        )
        return items
    }

    fun clearLog() {
        logDeque.clear()
        totalTimestamps.clear()
        sentenceTimestamps.clear()
        _records.value = emptyList()
        _summary.value = NmeaDiagnosticsSummary()
    }

    fun generateFullDiagnosticsReport(vehicleState: com.example.agopengps.navigation.VehicleState? = null): String {
        val sb = StringBuilder()
        sb.append("=== Sentinel Guidance NMEA Diagnostics Log ===\n")
        sb.append("Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n")
        sb.append("Receiver: $detectedReceiverName\n")
        if (vehicleState != null) {
            sb.append("Fix: ${vehicleState.fixQuality.label}, Sats: ${vehicleState.satellites}, Speed: ${String.format(Locale.US, "%.1f", vehicleState.speedKmh)} km/h\n")
            sb.append("Position: ${vehicleState.geoPosition.latitude}, ${vehicleState.geoPosition.longitude}, Alt: ${vehicleState.geoPosition.altitude}m\n")
        }
        sb.append("Total Sentences: $totalCount (Valid: $validCount, Errors: $checksumErrCount)\n")
        sb.append("Current Rate: ${String.format(Locale.US, "%.1f", _summary.value.overallHz)} Hz\n\n")
        sb.append("--- Last Sentences ---\n")
        _records.value.take(50).forEach { r ->
            sb.append("[${r.formattedTime}] [${r.sentenceType}] ${r.rawSentence}\n")
        }
        return sb.toString()
    }

    companion object {
        fun calculateNmeaChecksum(payload: String): String {
            var checksum = 0
            for (ch in payload) {
                checksum = checksum xor ch.code
            }
            return String.format(Locale.US, "%02X", checksum)
        }
    }
}

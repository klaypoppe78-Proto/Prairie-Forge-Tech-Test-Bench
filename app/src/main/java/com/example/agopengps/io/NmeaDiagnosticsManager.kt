package com.example.agopengps.io

import com.example.agopengps.navigation.FixQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

data class NmeaSentenceRecord(
    val id: Long,
    val timestampMs: Long,
    val formattedTime: String,
    val rawSentence: String,
    val sentenceType: String,
    val talkerId: String,
    val isValidChecksum: Boolean,
    val parsedReport: GnssReport?,
    val fieldTokens: List<String>,
    val errorMessage: String? = null
)

data class HealthCheckItem(
    val title: String,
    val detail: String,
    val status: CheckStatus // PASS, WARNING, FAIL
)

enum class CheckStatus {
    PASS, WARNING, FAIL
}

data class NmeaDiagnosticsSummary(
    val totalReceived: Long = 0L,
    val validChecksumCount: Long = 0L,
    val checksumErrorCount: Long = 0L,
    val malformedCount: Long = 0L,
    val sentenceFrequenciesHz: Map<String, Double> = emptyMap(),
    val overallHz: Double = 0.0,
    val lastReceivedTimeMs: Long = 0L,
    val lastSentenceType: String = "",
    val detectedReceiver: String = "None Detected",
    val checklist: List<HealthCheckItem> = emptyList()
)

/**
 * Real-time NMEA 0183 / AgOpenGPS / Unicore diagnostic inspection and troubleshooting engine.
 * Maintains a live rolling buffer of incoming sentences, calculates per-sentence Hz rates,
 * validates checksums, and performs automated field health checks.
 */
class NmeaDiagnosticsManager {

    private val idCounter = AtomicLong(1L)
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val maxLogCapacity = 200

    private val _records = MutableStateFlow<List<NmeaSentenceRecord>>(emptyList())
    val records: StateFlow<List<NmeaSentenceRecord>> = _records.asStateFlow()

    private val _summary = MutableStateFlow(NmeaDiagnosticsSummary())
    val summary: StateFlow<NmeaDiagnosticsSummary> = _summary.asStateFlow()

    // Sliding window of timestamps for rate calculation (per sentence type and overall)
    private val recentTimestampsByType = mutableMapOf<String, ConcurrentLinkedDeque<Long>>()
    private val recentOverallTimestamps = ConcurrentLinkedDeque<Long>()

    private var totalCount = 0L
    private var validCount = 0L
    private var checksumErrCount = 0L
    private var malformedCount = 0L
    private var detectedReceiverName = "GNSS Receiver"

    var isPaused: Boolean = false

    @Synchronized
    fun recordSentence(raw: String) {
        val now = System.currentTimeMillis()
        totalCount++

        val trimmed = raw.trim()
        val isWellFormed = trimmed.startsWith("$")
        if (!isWellFormed) {
            malformedCount++
        }

        // Validate Checksum
        val isValidChecksum = verifyNmeaChecksum(trimmed)
        if (isValidChecksum) {
            validCount++
        } else {
            checksumErrCount++
        }

        // Extract Talker and Type
        val tokens = if (isWellFormed) {
            val clean = if (trimmed.contains('*')) trimmed.substring(0, trimmed.indexOf('*')) else trimmed
            clean.split(',')
        } else emptyList()

        val fullTag = tokens.firstOrNull()?.removePrefix("$")?.uppercase() ?: "UNKNOWN"
        val (talker, sType) = parseTag(fullTag)

        // Parse with NmeaParser
        val parsed = if (isWellFormed) NmeaParser.parse(trimmed) else null
        if (parsed?.receiverIdent?.isNotBlank() == true) {
            detectedReceiverName = parsed.receiverIdent
        }

        // Track frequency
        trackTimestamp(sType, now)
        recentOverallTimestamps.addLast(now)
        pruneWindow(recentOverallTimestamps, now)

        val newRecord = NmeaSentenceRecord(
            id = idCounter.getAndIncrement(),
            timestampMs = now,
            formattedTime = timeFormat.format(Date(now)),
            rawSentence = trimmed,
            sentenceType = sType,
            talkerId = talker,
            isValidChecksum = isValidChecksum,
            parsedReport = parsed,
            fieldTokens = tokens
        )

        if (!isPaused) {
            _records.update { current ->
                val updated = ArrayList<NmeaSentenceRecord>(current.size + 1)
                updated.add(newRecord)
                if (current.size >= maxLogCapacity) {
                    updated.addAll(current.take(maxLogCapacity - 1))
                } else {
                    updated.addAll(current)
                }
                updated
            }
        }

        // Update Summary periodically or every sentence
        updateSummary(now, sType, parsed)
    }

    private fun parseTag(tag: String): Pair<String, String> {
        if (tag.length <= 2) return Pair("", tag)
        return when {
            tag.startsWith("PAOGI") -> Pair("PA", "PAOGI")
            tag.startsWith("KSXT") -> Pair("KS", "KSXT")
            tag.length >= 5 -> Pair(tag.substring(0, 2), tag.substring(2))
            else -> Pair("", tag)
        }
    }

    private fun trackTimestamp(type: String, now: Long) {
        val deque = recentTimestampsByType.getOrPut(type) { ConcurrentLinkedDeque() }
        deque.addLast(now)
        pruneWindow(deque, now)
    }

    private fun pruneWindow(deque: ConcurrentLinkedDeque<Long>, now: Long) {
        val windowMs = 2500L
        while (!deque.isEmpty() && now - (deque.peekFirst() ?: now) > windowMs) {
            deque.pollFirst()
        }
    }

    private fun updateSummary(now: Long, lastType: String, latestParsed: GnssReport?) {
        val freqMap = mutableMapOf<String, Double>()
        for ((type, deque) in recentTimestampsByType) {
            pruneWindow(deque, now)
            val count = deque.size
            if (count >= 2) {
                val spanSec = 2.5
                val hz = count / spanSec
                freqMap[type] = (hz * 10.0).roundToInt() / 10.0
            } else if (count == 1) {
                freqMap[type] = 0.5
            }
        }

        val overallHz = if (recentOverallTimestamps.size >= 2) {
            (recentOverallTimestamps.size / 2.5 * 10.0).roundToInt() / 10.0
        } else 0.0

        val checklist = buildHealthChecklist(overallHz, freqMap, latestParsed)

        _summary.update {
            it.copy(
                totalReceived = totalCount,
                validChecksumCount = validCount,
                checksumErrorCount = checksumErrCount,
                malformedCount = malformedCount,
                sentenceFrequenciesHz = freqMap,
                overallHz = overallHz,
                lastReceivedTimeMs = now,
                lastSentenceType = lastType,
                detectedReceiver = detectedReceiverName,
                checklist = checklist
            )
        }
    }

    private fun buildHealthChecklist(
        overallHz: Double,
        freqMap: Map<String, Double>,
        report: GnssReport?
    ): List<HealthCheckItem> {
        val list = mutableListOf<HealthCheckItem>()

        // 1. Data Link Flow Check
        if (overallHz >= 8.0) {
            list.add(HealthCheckItem("Stream Rate", "${overallHz} Hz (High Speed Stream Active)", CheckStatus.PASS))
        } else if (overallHz > 0.0) {
            list.add(HealthCheckItem("Stream Rate", "${overallHz} Hz (Low Rate - recommended 10Hz to 20Hz)", CheckStatus.WARNING))
        } else {
            list.add(HealthCheckItem("Stream Rate", "0.0 Hz (No incoming data detected)", CheckStatus.FAIL))
        }

        // 2. Checksum Health
        val errPct = if (totalCount > 0) (checksumErrCount.toDouble() / totalCount * 100.0) else 0.0
        if (checksumErrCount == 0L) {
            list.add(HealthCheckItem("Checksum Integrity", "100% Valid ($validCount sentences verified)", CheckStatus.PASS))
        } else if (errPct < 5.0) {
            list.add(HealthCheckItem("Checksum Integrity", "${String.format("%.1f", errPct)}% errors ($checksumErrCount dropped)", CheckStatus.WARNING))
        } else {
            list.add(HealthCheckItem("Checksum Integrity", "${String.format("%.1f", errPct)}% errors (Check Baud Rate or cable noise)", CheckStatus.FAIL))
        }

        // 3. GNSS Fix Status
        val fix = report?.fixQuality ?: FixQuality.INVALID
        when (fix) {
            FixQuality.RTK_FIX -> {
                list.add(HealthCheckItem("RTK Fix Quality", "RTK FIX (Sub-inch accuracy active)", CheckStatus.PASS))
            }
            FixQuality.RTK_FLOAT -> {
                list.add(HealthCheckItem("RTK Fix Quality", "RTK FLOAT (Carrier-phase resolving - wait 30s)", CheckStatus.WARNING))
            }
            FixQuality.GPS, FixQuality.DGPS, FixQuality.TABLET_INTERNAL -> {
                list.add(HealthCheckItem("RTK Fix Quality", "${fix.label} (Autonomous only - RTCM corrections missing)", CheckStatus.WARNING))
            }
            FixQuality.SIMULATOR -> {
                list.add(HealthCheckItem("RTK Fix Quality", "SIMULATOR LOCK (Indoor bench mode)", CheckStatus.PASS))
            }
            FixQuality.INVALID -> {
                list.add(HealthCheckItem("RTK Fix Quality", "NO FIX / NO SATELLITES", CheckStatus.FAIL))
            }
        }

        // 4. Dual-Antenna Heading / Baseline Check
        val hasHeadingSentence = freqMap.containsKey("KSXT") || freqMap.containsKey("HPR") || freqMap.containsKey("HDT")
        if (hasHeadingSentence) {
            val baseline = report?.baselineLengthMeters ?: 0.0
            if (baseline in 0.6..2.5) {
                list.add(HealthCheckItem("Dual Antenna Heading", "LOCKED (${String.format("%.2f", baseline)}m baseline)", CheckStatus.PASS))
            } else {
                list.add(HealthCheckItem("Dual Antenna Heading", "SENTENCE OK (Baseline ${String.format("%.2f", baseline)}m)", CheckStatus.PASS))
            }
        } else {
            list.add(HealthCheckItem("Dual Antenna Heading", "No KSXT/HPR sentence detected", CheckStatus.WARNING))
        }

        // 5. Satellites and HDOP
        val sats = report?.satellites ?: 0
        val hdop = report?.hdop ?: 99.0
        if (sats >= 14 && hdop <= 1.2) {
            list.add(HealthCheckItem("Constellation Quality", "$sats Sats tracked | HDOP: ${String.format("%.2f", hdop)}", CheckStatus.PASS))
        } else if (sats > 0) {
            list.add(HealthCheckItem("Constellation Quality", "$sats Sats | HDOP: ${String.format("%.2f", hdop)}", CheckStatus.WARNING))
        } else {
            list.add(HealthCheckItem("Constellation Quality", "No satellite metadata", CheckStatus.WARNING))
        }

        return list
    }

    fun clearLog() {
        _records.update { emptyList() }
        totalCount = 0L
        validCount = 0L
        checksumErrCount = 0L
        malformedCount = 0L
    }

    fun generateFullDiagnosticsReport(currentVehicleState: com.example.agopengps.navigation.VehicleState? = null): String {
        val sb = StringBuilder()
        val s = _summary.value
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        sb.appendLine("==================================================")
        sb.appendLine("AGSTEER NMEA & GNSS FIELD DIAGNOSTIC REPORT")
        sb.appendLine("Timestamp: $now")
        sb.appendLine("Receiver Detected: ${s.detectedReceiver}")
        sb.appendLine("==================================================")
        sb.appendLine()
        sb.appendLine("STREAM TELEMETRY SUMMARY:")
        sb.appendLine("  Overall Stream Rate: ${s.overallHz} Hz")
        sb.appendLine("  Total Sentences:     ${s.totalReceived}")
        sb.appendLine("  Valid Checksums:     ${s.validChecksumCount}")
        sb.appendLine("  Checksum Errors:     ${s.checksumErrorCount}")
        sb.appendLine("  Malformed Packets:   ${s.malformedCount}")
        sb.appendLine()
        sb.appendLine("PER-SENTENCE FREQUENCIES:")
        if (s.sentenceFrequenciesHz.isEmpty()) {
            sb.appendLine("  (No active frequency telemetry)")
        } else {
            for ((type, hz) in s.sentenceFrequenciesHz) {
                sb.appendLine("  $$type: ${hz} Hz")
            }
        }
        sb.appendLine()
        sb.appendLine("HEALTH CHECKLIST:")
        for (item in s.checklist) {
            val mark = when (item.status) {
                CheckStatus.PASS -> "[PASS]"
                CheckStatus.WARNING -> "[WARN]"
                CheckStatus.FAIL -> "[FAIL]"
            }
            sb.appendLine("  $mark ${item.title}: ${item.detail}")
        }
        sb.appendLine()
        if (currentVehicleState != null) {
            sb.appendLine("LIVE VEHICLE ATTITUDE & GUIDANCE:")
            sb.appendLine("  Position:       ${String.format(Locale.US, "%.6f, %.6f (Alt: %.1fm)", currentVehicleState.geoPosition.latitude, currentVehicleState.geoPosition.longitude, currentVehicleState.geoPosition.altitude)}")
            sb.appendLine("  True Heading:   ${String.format(Locale.US, "%.1f°", currentVehicleState.headingDeg)}")
            sb.appendLine("  Roll / Pitch:   ${String.format(Locale.US, "Roll %+.1f°, Pitch %+.1f°", currentVehicleState.rollDeg, currentVehicleState.pitchDeg)}")
            sb.appendLine("  Speed:          ${String.format(Locale.US, "%.1f km/h (%.1f mph)", currentVehicleState.speedKmh, currentVehicleState.speedKmh * 0.621371)}")
            sb.appendLine("  Actual Steer:   ${String.format(Locale.US, "%+.1f° (Target: %+.1f°)", currentVehicleState.actualSteerAngleDeg, currentVehicleState.targetSteerAngleDeg)}")
            sb.appendLine("  Cross-Track:    ${String.format(Locale.US, "%+.1f cm (%+.1f in)", currentVehicleState.crossTrackErrorCm, currentVehicleState.crossTrackErrorMeters * 39.3701)}")
            sb.appendLine("  Heading Error:  ${String.format(Locale.US, "%+.1f°", currentVehicleState.headingErrorDeg)}")
            sb.appendLine("  Fix Quality:    ${currentVehicleState.fixQuality.name}")
            sb.appendLine("  Satellites:     ${currentVehicleState.satellites}")
            sb.appendLine("  RTK Age:        ${String.format(Locale.US, "%.1fs", currentVehicleState.ageOfCorrectionSec)}")
            sb.appendLine()
        }
        sb.appendLine("RECENT RAW NMEA SAMPLE (LATEST 15):")
        val sample = _records.value.take(15)
        if (sample.isEmpty()) {
            sb.appendLine("  (No sentences recorded)")
        } else {
            for (rec in sample) {
                val validMark = if (rec.isValidChecksum) "✓" else "✗ ERR"
                sb.appendLine("  [${rec.formattedTime}] [$validMark] ${rec.rawSentence}")
            }
        }
        sb.appendLine("==================================================")
        return sb.toString()
    }

    companion object {
        fun calculateNmeaChecksum(sentence: String): String {
            val startIndex = if (sentence.startsWith("$")) 1 else 0
            val endIndex = sentence.indexOf('*').let { if (it != -1) it else sentence.length }
            var sum = 0
            for (i in startIndex until endIndex) {
                sum = sum xor sentence[i].code
            }
            return String.format(Locale.US, "%02X", sum)
        }

        fun verifyNmeaChecksum(sentence: String): Boolean {
            val asteriskIdx = sentence.lastIndexOf('*')
            if (asteriskIdx == -1 || asteriskIdx + 3 > sentence.length) return true // No checksum provided
            val expected = sentence.substring(asteriskIdx + 1, asteriskIdx + 3).trim().uppercase()
            val calculated = calculateNmeaChecksum(sentence)
            return expected.equals(calculated, ignoreCase = true)
        }
    }
}

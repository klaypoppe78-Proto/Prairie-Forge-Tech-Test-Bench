package com.example.agopengps.io

import com.example.agopengps.navigation.FixQuality
import com.example.agopengps.navigation.GeoPoint
import kotlin.math.*

data class GnssReport(
    val geoPoint: GeoPoint = GeoPoint(),
    val headingDeg: Double? = null,
    val speedKmh: Double? = null,
    val rollDeg: Double = 0.0,
    val pitchDeg: Double = 0.0,
    val fixQuality: FixQuality = FixQuality.RTK_FIX,
    val fixQualityLabel: String = "RTK FIX (4)",
    val satellites: Int = 31,
    val hdop: Double = 0.7,
    val ageSec: Double = 0.8,
    val baselineLengthMeters: Double = 2.45,
    val receiverIdent: String = "RTK GNSS"
)

typealias NmeaGnssReport = GnssReport

object NmeaParser {

    fun parse(sentence: String): GnssReport? {
        val clean = sentence.trim()
        if (!clean.startsWith("$") && !clean.startsWith("!")) return null

        val parts = clean.substring(1).split("*")[0].split(",")
        val header = parts[0].uppercase()

        return when {
            header.endsWith("GGA") -> parseGga(parts)
            header.endsWith("VTG") -> parseVtg(parts)
            header.endsWith("RMC") -> parseRmc(parts)
            header == "KSXT" -> parseKsxt(parts) // Unicore UM982 Dual-Antenna Heading & Pitch
            header.endsWith("HDT") -> parseHdt(parts)
            header == "PAOGI" -> parsePaogi(parts)
            else -> null
        }
    }

    private fun parseGga(parts: List<String>): GnssReport? {
        if (parts.size < 10) return null
        val latStr = parts[2]
        val latHem = parts[3]
        val lonStr = parts[4]
        val lonHem = parts[5]
        val qual = parts[6].toIntOrNull() ?: 1
        val sats = parts[7].toIntOrNull() ?: 0
        val hdop = parts[8].toDoubleOrNull() ?: 1.0
        val alt = parts[9].toDoubleOrNull() ?: 0.0

        val lat = parseCoord(latStr, latHem == "S")
        val lon = parseCoord(lonStr, lonHem == "W")

        val fixEnum = when (qual) {
            4 -> FixQuality.RTK_FIX
            5 -> FixQuality.RTK_FLOAT
            2 -> FixQuality.DGPS
            1 -> FixQuality.GPS
            else -> FixQuality.INVALID
        }
        val fixLabel = when (qual) {
            4 -> "RTK FIX (4)"
            5 -> "RTK FLOAT (5)"
            2 -> "DGPS (2)"
            1 -> "GPS FIX (1)"
            else -> "NO FIX ($qual)"
        }

        return GnssReport(
            geoPoint = GeoPoint(lat, lon, alt),
            fixQuality = fixEnum,
            fixQualityLabel = fixLabel,
            satellites = sats,
            hdop = hdop
        )
    }

    private fun parseVtg(parts: List<String>): GnssReport? {
        if (parts.size < 8) return null
        val heading = parts[1].toDoubleOrNull()
        val speedKmh = parts[7].toDoubleOrNull()
        return GnssReport(headingDeg = heading, speedKmh = speedKmh)
    }

    private fun parseRmc(parts: List<String>): GnssReport? {
        if (parts.size < 9) return null
        val lat = parseCoord(parts[3], parts[4] == "S")
        val lon = parseCoord(parts[5], parts[6] == "W")
        val speedKnots = parts[7].toDoubleOrNull() ?: 0.0
        val heading = parts[8].toDoubleOrNull()
        return GnssReport(
            geoPoint = GeoPoint(lat, lon, 0.0),
            headingDeg = heading,
            speedKmh = speedKnots * 1.852
        )
    }

    private fun parseKsxt(parts: List<String>): GnssReport? {
        // $KSXT,20261002140900.00,lon,lat,alt,heading,pitch,roll,qual,status
        if (parts.size < 9) return null
        val lon = parts[2].toDoubleOrNull() ?: return null
        val lat = parts[3].toDoubleOrNull() ?: return null
        val alt = parts[4].toDoubleOrNull() ?: 0.0
        val hdg = parts[5].toDoubleOrNull()
        val pitch = parts[6].toDoubleOrNull() ?: 0.0
        val roll = parts[7].toDoubleOrNull() ?: 0.0
        val qual = parts[8].toIntOrNull() ?: 4

        val fixEnum = if (qual == 4) FixQuality.RTK_FIX else FixQuality.RTK_FLOAT
        return GnssReport(
            geoPoint = GeoPoint(lat, lon, alt),
            headingDeg = hdg,
            pitchDeg = pitch,
            rollDeg = roll,
            fixQuality = fixEnum,
            fixQualityLabel = if (qual == 4) "UM982 RTK DUAL FIX" else "UM982 FIX ($qual)",
            receiverIdent = "Unicore UM982 Dual"
        )
    }

    private fun parseHdt(parts: List<String>): GnssReport? {
        if (parts.size < 2) return null
        val hdg = parts[1].toDoubleOrNull()
        return GnssReport(headingDeg = hdg)
    }

    private fun parsePaogi(parts: List<String>): GnssReport? {
        // $PAOGI,time,lat,lon,alt,heading,roll,pitch,speed,sats,fix
        if (parts.size < 10) return null
        val lat = parts[2].toDoubleOrNull() ?: 0.0
        val lon = parts[3].toDoubleOrNull() ?: 0.0
        val alt = parts[4].toDoubleOrNull() ?: 0.0
        val hdg = parts[5].toDoubleOrNull()
        val roll = parts[6].toDoubleOrNull() ?: 0.0
        val pitch = parts[7].toDoubleOrNull() ?: 0.0
        val spd = parts[8].toDoubleOrNull() ?: 0.0
        val sats = parts[9].toIntOrNull() ?: 28
        return GnssReport(
            geoPoint = GeoPoint(lat, lon, alt),
            headingDeg = hdg,
            rollDeg = roll,
            pitchDeg = pitch,
            speedKmh = spd,
            satellites = sats,
            receiverIdent = "Sentinel Guidance PAOGI"
        )
    }

    private fun parseCoord(nmeaVal: String, isNegative: Boolean): Double {
        if (nmeaVal.length < 4) return 0.0
        val dotIdx = nmeaVal.indexOf('.')
        val degLength = if (dotIdx >= 0) dotIdx - 2 else nmeaVal.length - 2
        if (degLength <= 0) return 0.0
        val deg = nmeaVal.substring(0, degLength).toDoubleOrNull() ?: 0.0
        val min = nmeaVal.substring(degLength).toDoubleOrNull() ?: 0.0
        val dec = deg + (min / 60.0)
        return if (isNegative) -dec else dec
    }
}

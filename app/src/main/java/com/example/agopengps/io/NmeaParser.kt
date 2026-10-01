package com.example.agopengps.io

import com.example.agopengps.navigation.FixQuality
import com.example.agopengps.navigation.GeoPoint

/**
 * Parsed GNSS & IMU navigation record from NMEA 0183 or AgOpenGPS PAOGI strings.
 */
data class GnssReport(
    val geoPoint: GeoPoint,
    val headingDeg: Double? = null,
    val speedKmh: Double? = null,
    val rollDeg: Double = 0.0,
    val pitchDeg: Double = 0.0,
    val baselineLengthMeters: Double = 0.0,
    val fixQuality: FixQuality = FixQuality.INVALID,
    val satellites: Int = 0,
    val hdop: Double = 1.0,
    val ageSec: Double = 0.0,
    val rawSentence: String = "",
    val receiverIdent: String = ""
)

/**
 * Parser for standard NMEA 0183 ($GPGGA, $GNGGA, $GPVTG, $GPRMC),
 * Unicore UM982 dual-antenna ($KSXT), u-blox ZED-F9P ($GNHPR, $GNHDT),
 * and AgOpenGPS proprietary high-precision IMU sentence ($PAOGI).
 */
object NmeaParser {

    fun parse(sentence: String): GnssReport? {
        val trimmed = sentence.trim()
        if (!trimmed.startsWith("$")) return null

        // Strip optional checksum *xx
        val asteriskIdx = trimmed.indexOf('*')
        val clean = if (asteriskIdx != -1) trimmed.substring(0, asteriskIdx) else trimmed
        val tokens = clean.split(',')
        if (tokens.isEmpty()) return null

        val tag = tokens[0].uppercase()

        return try {
            when {
                tag.endsWith("KSXT") -> parseKsxt(tokens, trimmed)
                tag.endsWith("PAOGI") -> parsePaogi(tokens, trimmed)
                tag.endsWith("HPR") -> parseHpr(tokens, trimmed)
                tag.endsWith("HDT") -> parseHdt(tokens, trimmed)
                tag.endsWith("GGA") -> parseGga(tokens, trimmed)
                tag.endsWith("RMC") -> parseRmc(tokens, trimmed)
                tag.endsWith("VTG") -> parseVtg(tokens, trimmed)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Unicore UM982 Dual-Antenna Sentence:
     * Standard: $KSXT,yyyymmddhhmmss.ss,lon,lat,alt,yaw,pitch,roll_or_dist,quality,status*hh
     * Extended: $KSXT,time,lon,lat,alt,yaw,pitch,heading_std,pitch_std,roll,baseline_len,quality*hh
     */
     private fun parseKsxt(tokens: List<String>, raw: String): GnssReport? {
        if (tokens.size < 7) return null
        val lon = tokens.getOrNull(2)?.toDoubleOrNull() ?: return null
        val lat = tokens.getOrNull(3)?.toDoubleOrNull() ?: return null
        val alt = tokens.getOrNull(4)?.toDoubleOrNull() ?: 0.0
        val yaw = tokens.getOrNull(5)?.toDoubleOrNull() ?: 0.0
        val pitch = tokens.getOrNull(6)?.toDoubleOrNull() ?: 0.0

        // Parse roll and baseline depending on token count
        val token7 = tokens.getOrNull(7)?.toDoubleOrNull() ?: 0.0
        val baselineLen = if (tokens.size >= 11) {
            tokens.getOrNull(10)?.toDoubleOrNull() ?: 1.25
        } else {
            1.25 // Default typical dual antenna baseline distance in meters
        }
        val rollDeg = if (tokens.size >= 10) {
            tokens.getOrNull(9)?.toDoubleOrNull() ?: token7
        } else {
            token7
        }

        val qualCode = tokens.lastOrNull()?.toIntOrNull()
            ?: (tokens.getOrNull(8)?.toIntOrNull() ?: 4)

        val fix = when (qualCode) {
            3, 4 -> FixQuality.RTK_FIX
            2, 5 -> FixQuality.RTK_FLOAT
            1 -> FixQuality.GPS
            else -> FixQuality.RTK_FIX
        }

        return GnssReport(
            geoPoint = GeoPoint(lat, lon, alt),
            headingDeg = (yaw + 360.0) % 360.0,
            pitchDeg = pitch,
            rollDeg = rollDeg,
            baselineLengthMeters = baselineLen,
            fixQuality = fix,
            satellites = 28,
            hdop = 0.5,
            rawSentence = raw,
            receiverIdent = "Unicore UM982 Dual-Antenna RTK"
        )
    }

    /**
     * Dual-antenna Heading, Pitch, Roll sentence ($GNHPR):
     * $GNHPR,hhmmss.ss,heading,pitch,roll*hh
     */
    private fun parseHpr(tokens: List<String>, raw: String): GnssReport? {
        if (tokens.size < 5) return null
        val heading = tokens.getOrNull(2)?.toDoubleOrNull() ?: return null
        val pitch = tokens.getOrNull(3)?.toDoubleOrNull() ?: 0.0
        val roll = tokens.getOrNull(4)?.toDoubleOrNull() ?: 0.0

        return GnssReport(
            geoPoint = GeoPoint(0.0, 0.0, 0.0),
            headingDeg = (heading + 360.0) % 360.0,
            pitchDeg = pitch,
            rollDeg = roll,
            fixQuality = FixQuality.RTK_FIX,
            rawSentence = raw,
            receiverIdent = "u-blox F9P / Dual Antenna Heading"
        )
    }

    /**
     * True Heading sentence ($GNHDT):
     * $GNHDT,heading,T*hh
     */
    private fun parseHdt(tokens: List<String>, raw: String): GnssReport? {
        if (tokens.size < 3) return null
        val heading = tokens.getOrNull(1)?.toDoubleOrNull() ?: return null

        return GnssReport(
            geoPoint = GeoPoint(0.0, 0.0, 0.0),
            headingDeg = (heading + 360.0) % 360.0,
            rawSentence = raw,
            receiverIdent = "Dual Antenna HDT Heading"
        )
    }

    /**
     * AgOpenGPS $PAOGI:
     * $PAOGI,time,lat,lon,fix,sats,hdop,alt,age,roll,pitch,yaw,angSpeed*checksum
     */
    private fun parsePaogi(tokens: List<String>, raw: String): GnssReport? {
        if (tokens.size < 12) return null
        val lat = tokens.getOrNull(2)?.toDoubleOrNull() ?: return null
        val lon = tokens.getOrNull(3)?.toDoubleOrNull() ?: return null
        val fixCode = tokens.getOrNull(4)?.toIntOrNull() ?: 1
        val sats = tokens.getOrNull(5)?.toIntOrNull() ?: 0
        val hdop = tokens.getOrNull(6)?.toDoubleOrNull() ?: 1.0
        val alt = tokens.getOrNull(7)?.toDoubleOrNull() ?: 0.0
        val age = tokens.getOrNull(8)?.toDoubleOrNull() ?: 0.0
        val roll = tokens.getOrNull(9)?.toDoubleOrNull() ?: 0.0
        val pitch = tokens.getOrNull(10)?.toDoubleOrNull() ?: 0.0
        val yaw = tokens.getOrNull(11)?.toDoubleOrNull() ?: 0.0

        return GnssReport(
            geoPoint = GeoPoint(lat, lon, alt),
            headingDeg = (yaw + 360.0) % 360.0,
            rollDeg = roll,
            pitchDeg = pitch,
            fixQuality = FixQuality.fromCode(fixCode),
            satellites = sats,
            hdop = hdop,
            ageSec = age,
            rawSentence = raw
        )
    }

    /**
     * Standard $GPGGA / $GNGGA:
     * $GPGGA,hhmmss.ss,llll.ll,a,yyyyy.yy,a,x,xx,x.x,x.x,M,x.x,M,x.x,xxxx*hh
     */
    private fun parseGga(tokens: List<String>, raw: String): GnssReport? {
        if (tokens.size < 10) return null
        val rawLat = tokens.getOrNull(2) ?: return null
        val latHem = tokens.getOrNull(3) ?: "N"
        val rawLon = tokens.getOrNull(4) ?: return null
        val lonHem = tokens.getOrNull(5) ?: "W"
        val fixCode = tokens.getOrNull(6)?.toIntOrNull() ?: 0
        val sats = tokens.getOrNull(7)?.toIntOrNull() ?: 0
        val hdop = tokens.getOrNull(8)?.toDoubleOrNull() ?: 1.0
        val alt = tokens.getOrNull(9)?.toDoubleOrNull() ?: 0.0

        val lat = parseNmeaCoordinate(rawLat, latHem) ?: return null
        val lon = parseNmeaCoordinate(rawLon, lonHem) ?: return null

        return GnssReport(
            geoPoint = GeoPoint(lat, lon, alt),
            fixQuality = FixQuality.fromCode(fixCode),
            satellites = sats,
            hdop = hdop,
            rawSentence = raw
        )
    }

    /**
     * Standard $GPRMC / $GNRMC:
     * $GPRMC,hhmmss.ss,A,llll.ll,a,yyyyy.yy,a,x.x,x.x,ddmmyy,,,a*hh
     */
    private fun parseRmc(tokens: List<String>, raw: String): GnssReport? {
        if (tokens.size < 9) return null
        val status = tokens.getOrNull(2) ?: "V"
        if (status != "A") return null

        val rawLat = tokens.getOrNull(3) ?: return null
        val latHem = tokens.getOrNull(4) ?: "N"
        val rawLon = tokens.getOrNull(5) ?: return null
        val lonHem = tokens.getOrNull(6) ?: "W"
        val speedKnots = tokens.getOrNull(7)?.toDoubleOrNull() ?: 0.0
        val trackDeg = tokens.getOrNull(8)?.toDoubleOrNull()

        val lat = parseNmeaCoordinate(rawLat, latHem) ?: return null
        val lon = parseNmeaCoordinate(rawLon, lonHem) ?: return null

        return GnssReport(
            geoPoint = GeoPoint(lat, lon, 0.0),
            speedKmh = speedKnots * 1.852,
            headingDeg = trackDeg?.let { (it + 360.0) % 360.0 },
            fixQuality = FixQuality.GPS,
            rawSentence = raw
        )
    }

    /**
     * Standard $GPVTG / $GNVTG:
     * $GPVTG,x.x,T,x.x,M,x.x,N,x.x,K,m*hh
     */
    private fun parseVtg(tokens: List<String>, raw: String): GnssReport? {
        val heading = tokens.getOrNull(1)?.toDoubleOrNull()
        val speedKmh = tokens.getOrNull(7)?.toDoubleOrNull()
            ?: (tokens.getOrNull(5)?.toDoubleOrNull()?.let { it * 1.852 })

        return GnssReport(
            geoPoint = GeoPoint(0.0, 0.0, 0.0),
            headingDeg = heading?.let { (it + 360.0) % 360.0 },
            speedKmh = speedKmh,
            rawSentence = raw
        )
    }

    /**
     * Converts NMEA DDMM.MMMMM to Decimal Degrees.
     */
    private fun parseNmeaCoordinate(rawCoord: String, hemisphere: String): Double? {
        if (rawCoord.length < 4) return null
        val dotIdx = rawCoord.indexOf('.')
        val degDigits = if (dotIdx != -1) dotIdx - 2 else rawCoord.length - 2
        if (degDigits <= 0) return null

        val degrees = rawCoord.substring(0, degDigits).toDoubleOrNull() ?: return null
        val minutes = rawCoord.substring(degDigits).toDoubleOrNull() ?: return null

        var decimal = degrees + (minutes / 60.0)
        if (hemisphere.equals("S", ignoreCase = true) || hemisphere.equals("W", ignoreCase = true)) {
            decimal = -decimal
        }
        return decimal
    }
}

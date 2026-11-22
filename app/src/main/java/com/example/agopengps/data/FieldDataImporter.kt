package com.example.agopengps.data

import com.example.agopengps.navigation.ABLine
import com.example.agopengps.navigation.GeoPoint
import com.example.agopengps.navigation.GeoUtils
import com.example.agopengps.navigation.GuidanceAlgorithms
import com.example.agopengps.navigation.Vec2
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

data class ImportedFieldResult(
    val fieldName: String,
    val originGeo: GeoPoint,
    val boundaryLocal: List<Vec2>,
    val headlandBoundary: List<Vec2>,
    val abLine: ABLine?,
    val areaAcres: Double
)

object FieldDataImporter {

    fun parseFieldData(content: String, defaultName: String = "Imported Field"): ImportedFieldResult? {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return null

        return try {
            when {
                trimmed.startsWith("<") && (trimmed.contains("<kml", ignoreCase = true) || trimmed.contains("<coordinates", ignoreCase = true)) -> {
                    parseKml(trimmed, defaultName)
                }
                trimmed.startsWith("{") -> {
                    if (isGeoJsonText(trimmed)) {
                        parseGeoJsonText(trimmed, defaultName)
                    } else {
                        val json = JSONObject(trimmed)
                        parseJson(json, defaultName)
                    }
                }
                else -> parseCsvOrTxt(trimmed, defaultName)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun isGeoJsonText(text: String): Boolean {
        return Regex("\"type\"\\s*:\\s*\"(FeatureCollection|Feature|Polygon|MultiPolygon)\"", RegexOption.IGNORE_CASE)
            .containsMatchIn(text)
    }

    private fun parseKml(kmlText: String, defaultName: String): ImportedFieldResult? {
        val nameMatch = Regex("<name>(.*?)</name>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(kmlText)
        val fieldName = nameMatch?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() } ?: defaultName

        val coordsMatch = Regex("<coordinates>(.*?)</coordinates>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(kmlText) ?: return null
        val rawCoords = coordsMatch.groupValues[1].trim()
        val geoPoints = mutableListOf<GeoPoint>()
        for (token in rawCoords.split(Regex("\\s+"))) {
            val parts = token.trim().split(",")
            if (parts.size >= 2) {
                val lon = parts[0].toDoubleOrNull()
                val lat = parts[1].toDoubleOrNull()
                val alt = parts.getOrNull(2)?.toDoubleOrNull() ?: 317.0
                if (lat != null && lon != null) {
                    geoPoints.add(GeoPoint(lat, lon, alt))
                }
            }
        }
        return buildResultFromGeoRing(fieldName, geoPoints)
    }

    private fun parseGeoJsonText(text: String, defaultName: String): ImportedFieldResult? {
        // Extract first "name" string (top-level FeatureCollection name comes before feature properties)
        val nameMatch = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").find(text)
        val fieldName = nameMatch?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() } ?: defaultName

        // Locate "coordinates" array and extract the first outer ring of [lon, lat(, alt)] pairs
        val coordsKey = Regex("\"coordinates\"\\s*:\\s*").find(text) ?: return null
        var idx = coordsKey.range.last + 1
        while (idx < text.length && text[idx].isWhitespace()) idx++
        if (idx >= text.length || text[idx] != '[') return null

        // Descend nested '[' until we reach the array of coordinate pairs: [ [lon, lat], [lon, lat], ... ]
        var depth = 0
        var ringStart = -1
        var ringEnd = -1
        // Determine nesting level: Polygon -> [[[lon,lat],...]], MultiPolygon -> [[[[lon,lat],...]]]
        // Find the first coordinate pair `[ <num>, <num> ]` and take its enclosing `[ ... ]` ring.
        val firstPairMatch = Regex("\\[\\s*(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)")
            .find(text, startIndex = idx) ?: return null
        // Walk backwards from firstPairMatch.range.first to find the '[' opening the ring
        var back = firstPairMatch.range.first - 1
        while (back >= idx && text[back] != '[') back--
        if (back < idx) return null
        ringStart = back

        // Walk forward from ringStart to find matching ']' for that ring
        depth = 0
        for (i in ringStart until text.length) {
            val ch = text[i]
            if (ch == '[') depth++
            else if (ch == ']') {
                depth--
                if (depth == 0) {
                    ringEnd = i
                    break
                }
            }
        }
        if (ringEnd <= ringStart) return null

        val ringText = text.substring(ringStart, ringEnd + 1)
        val pairRegex = Regex("\\[\\s*(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)(?:\\s*,\\s*(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?))?\\s*\\]")
        val geoPoints = mutableListOf<GeoPoint>()
        for (m in pairRegex.findAll(ringText)) {
            val lon = m.groupValues[1].toDoubleOrNull()
            val lat = m.groupValues[2].toDoubleOrNull()
            val alt = m.groupValues.getOrNull(3)?.toDoubleOrNull() ?: 317.0
            if (lat != null && lon != null) {
                geoPoints.add(GeoPoint(lat, lon, alt))
            }
        }
        return buildResultFromGeoRing(fieldName, geoPoints)
    }

    private fun buildResultFromGeoRing(fieldName: String, rawGeoPoints: List<GeoPoint>): ImportedFieldResult? {
        if (rawGeoPoints.size < 3) return null
        // Drop duplicate closing vertex for clean polygon math if present
        val geoPoints = if (rawGeoPoints.size > 3 &&
            abs(rawGeoPoints.first().latitude - rawGeoPoints.last().latitude) < 1e-8 &&
            abs(rawGeoPoints.first().longitude - rawGeoPoints.last().longitude) < 1e-8
        ) {
            rawGeoPoints.dropLast(1)
        } else {
            rawGeoPoints
        }
        if (geoPoints.size < 3) return null

        val avgLat = geoPoints.map { it.latitude }.average()
        val avgLon = geoPoints.map { it.longitude }.average()
        val avgAlt = geoPoints.map { it.altitude }.average()
        val originGeo = GeoPoint(avgLat, avgLon, avgAlt)

        val boundaryLocal = geoPoints.map { GeoUtils.geoToLocalMeters(it, originGeo) }
        val areaAcres = GeoUtils.calculatePolygonAreaAcres(boundaryLocal)
        val headland = GuidanceAlgorithms.generateHeadlandPolygon(boundaryLocal, 12.0)

        // Generate optimal baseline AB line along the longest boundary edge
        var bestA = boundaryLocal[0]
        var bestB = boundaryLocal[1]
        var maxDist = 0.0
        for (i in boundaryLocal.indices) {
            val p1 = boundaryLocal[i]
            val p2 = boundaryLocal[(i + 1) % boundaryLocal.size]
            val d = p1.distanceTo(p2)
            if (d > maxDist) {
                maxDist = d
                bestA = p1
                bestB = p2
            }
        }
        val abLine = if (maxDist > 1.0) {
            ABLine.fromPoints(
                aLocal = bestA,
                bLocal = bestB,
                aGeo = GeoUtils.localMetersToGeo(bestA, originGeo),
                bGeo = GeoUtils.localMetersToGeo(bestB, originGeo),
                name = "$fieldName Baseline"
            )
        } else null

        return ImportedFieldResult(
            fieldName = fieldName,
            originGeo = originGeo,
            boundaryLocal = boundaryLocal,
            headlandBoundary = headland,
            abLine = abLine,
            areaAcres = areaAcres
        )
    }

    private fun parseJson(json: JSONObject, defaultName: String): ImportedFieldResult {
        val name = json.optString("fieldName", json.optString("name", defaultName))
        val lat = json.optDouble("originLat", 44.9542)
        val lon = json.optDouble("originLon", -96.0825)
        val alt = json.optDouble("originAlt", 317.0)
        val originGeo = GeoPoint(lat, lon, alt)

        val boundaryPts = mutableListOf<Vec2>()
        val bJson = json.optString("boundaryPointsJson", "")
        if (bJson.isNotEmpty()) {
            bJson.split(";").forEach { pair ->
                val coords = pair.split(",")
                if (coords.size >= 2) {
                    val x = coords[0].toDoubleOrNull()
                    val y = coords[1].toDoubleOrNull()
                    if (x != null && y != null) boundaryPts.add(Vec2(x, y))
                }
            }
        }

        val headlandPts = mutableListOf<Vec2>()
        val hJson = json.optString("headlandPointsJson", "")
        if (hJson.isNotEmpty()) {
            hJson.split(";").forEach { pair ->
                val coords = pair.split(",")
                if (coords.size >= 2) {
                    val x = coords[0].toDoubleOrNull()
                    val y = coords[1].toDoubleOrNull()
                    if (x != null && y != null) headlandPts.add(Vec2(x, y))
                }
            }
        }

        var abLine: ABLine? = null
        if (json.optBoolean("hasActiveAbLine", false)) {
            val ax = json.optDouble("activeAbLineAX", 0.0)
            val ay = json.optDouble("activeAbLineAY", 0.0)
            val bx = json.optDouble("activeAbLineBX", 0.0)
            val by = json.optDouble("activeAbLineBY", 0.0)
            val lineName = json.optString("activeAbLineName", "Imported AB")
            val aLoc = Vec2(ax, ay)
            val bLoc = Vec2(bx, by)
            val aGeo = GeoUtils.localMetersToGeo(aLoc, originGeo)
            val bGeo = GeoUtils.localMetersToGeo(bLoc, originGeo)
            abLine = ABLine.fromPoints(aLoc, bLoc, aGeo, bGeo, lineName)
        }

        val area = if (boundaryPts.size >= 3) GeoUtils.calculatePolygonAreaAcres(boundaryPts) else 0.0

        return ImportedFieldResult(
            fieldName = name,
            originGeo = originGeo,
            boundaryLocal = boundaryPts,
            headlandBoundary = headlandPts,
            abLine = abLine,
            areaAcres = area
        )
    }

    private fun parseCsvOrTxt(text: String, defaultName: String): ImportedFieldResult {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val originGeo = GeoPoint(44.9542, -96.0825, 317.0)
        val boundaryPts = mutableListOf<Vec2>()

        for (line in lines) {
            if (line.startsWith("#") || line.startsWith("//")) continue
            val parts = line.split(",", ";", "\t")
            if (parts.size >= 2) {
                val p1 = parts[0].trim().toDoubleOrNull()
                val p2 = parts[1].trim().toDoubleOrNull()
                if (p1 != null && p2 != null) {
                    if (abs(p1) <= 90.0 && abs(p2) <= 180.0) {
                        val ptLocal = GeoUtils.geoToLocalMeters(GeoPoint(p1, p2, 317.0), originGeo)
                        boundaryPts.add(ptLocal)
                    } else {
                        boundaryPts.add(Vec2(p1, p2))
                    }
                }
            }
        }

        val area = if (boundaryPts.size >= 3) GeoUtils.calculatePolygonAreaAcres(boundaryPts) else 0.0

        return ImportedFieldResult(
            fieldName = defaultName,
            originGeo = originGeo,
            boundaryLocal = boundaryPts,
            headlandBoundary = emptyList(),
            abLine = null,
            areaAcres = area
        )
    }
}

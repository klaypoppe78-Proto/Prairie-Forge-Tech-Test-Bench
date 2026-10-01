package com.example.agopengps.data

import com.example.agopengps.navigation.*
import org.json.JSONObject
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.abs

data class ImportedFieldResult(
    val fieldName: String,
    val originGeo: GeoPoint,
    val boundaryLocal: List<Vec2>,
    val abLine: ABLine?,
    val headlandBoundary: List<Vec2>,
    val areaAcres: Double
)

object FieldDataImporter {

    /**
     * Attempts to parse raw text which may be KML XML or GeoJSON JSON.
     */
    fun parseFieldData(content: String, defaultName: String = "Imported Field"): ImportedFieldResult? {
        val trimmed = content.trim()
        return if (trimmed.startsWith("{") || trimmed.contains("\"type\"")) {
            parseGeoJson(trimmed, defaultName)
        } else {
            parseKml(trimmed, defaultName)
        }
    }

    /**
     * Parses standard Google Earth / QGIS / Ops Center KML XML.
     */
    fun parseKml(kmlContent: String, defaultName: String): ImportedFieldResult? {
        val geoPoints = mutableListOf<GeoPoint>()
        var extractedName = defaultName

        // Extract field name if present in <name>
        val nameMatcher = Pattern.compile("<name>(.*?)</name>", Pattern.CASE_INSENSITIVE).matcher(kmlContent)
        if (nameMatcher.find()) {
            val candidate = nameMatcher.group(1)?.trim() ?: ""
            if (candidate.isNotBlank() && !candidate.contains("Export", ignoreCase = true)) {
                extractedName = candidate
            }
        }

        // Look for <coordinates>...</coordinates>
        val coordMatcher = Pattern.compile("<coordinates>([\\s\\S]*?)</coordinates>", Pattern.CASE_INSENSITIVE).matcher(kmlContent)
        while (coordMatcher.find()) {
            val coordBlock = coordMatcher.group(1)?.trim() ?: continue
            val tokens = coordBlock.split("\\s+".toRegex())
            for (token in tokens) {
                val parts = token.split(",")
                if (parts.size >= 2) {
                    val lon = parts[0].toDoubleOrNull() ?: continue
                    val lat = parts[1].toDoubleOrNull() ?: continue
                    val alt = if (parts.size >= 3) parts[2].toDoubleOrNull() ?: 300.0 else 300.0
                    geoPoints.add(GeoPoint(lat, lon, alt))
                }
            }
            if (geoPoints.size >= 3) break
        }

        if (geoPoints.size < 3) return null
        return buildImportedField(extractedName, geoPoints)
    }

    /**
     * Parses standard GeoJSON geometry (FeatureCollection, Feature, or Polygon).
     */
    fun parseGeoJson(jsonContent: String, defaultName: String): ImportedFieldResult? {
        val geoPoints = mutableListOf<GeoPoint>()
        var extractedName = defaultName

        try {
            val root = JSONObject(jsonContent)
            if (root.has("name")) {
                extractedName = root.getString("name")
            }

            fun extractCoordsFromPolygon(polyArr: org.json.JSONArray) {
                if (polyArr.length() == 0) return
                val ring = polyArr.getJSONArray(0)
                for (i in 0 until ring.length()) {
                    val pt = ring.getJSONArray(i)
                    val lon = pt.getDouble(0)
                    val lat = pt.getDouble(1)
                    val alt = if (pt.length() > 2) pt.getDouble(2) else 300.0
                    geoPoints.add(GeoPoint(lat, lon, alt))
                }
            }

            val type = root.optString("type", "")
            when (type) {
                "FeatureCollection" -> {
                    val features = root.getJSONArray("features")
                    for (i in 0 until features.length()) {
                        val feature = features.getJSONObject(i)
                        val props = feature.optJSONObject("properties")
                        if (props != null && props.has("name")) {
                            extractedName = props.getString("name")
                        }
                        val geom = feature.getJSONObject("geometry")
                        if (geom.getString("type").equals("Polygon", ignoreCase = true)) {
                            extractCoordsFromPolygon(geom.getJSONArray("coordinates"))
                            if (geoPoints.size >= 3) break
                        }
                    }
                }
                "Feature" -> {
                    val geom = root.getJSONObject("geometry")
                    if (geom.getString("type").equals("Polygon", ignoreCase = true)) {
                        extractCoordsFromPolygon(geom.getJSONArray("coordinates"))
                    }
                }
                "Polygon" -> {
                    extractCoordsFromPolygon(root.getJSONArray("coordinates"))
                }
            }
        } catch (_: Throwable) {
            // Fall through to regex coordinate extraction
        }

        if (geoPoints.size < 3) {
            // Robust regex fallback for GeoJSON coordinates: [lon, lat] or [lon, lat, alt]
            val coordPattern = Pattern.compile("\\[\\s*(-?\\d+\\.\\d+)\\s*,\\s*(-?\\d+\\.\\d+)(?:\\s*,\\s*(-?\\d+\\.\\d+))?\\s*\\]")
            val matcher = coordPattern.matcher(jsonContent)
            while (matcher.find()) {
                val lon = matcher.group(1)?.toDoubleOrNull() ?: continue
                val lat = matcher.group(2)?.toDoubleOrNull() ?: continue
                val alt = matcher.group(3)?.toDoubleOrNull() ?: 300.0
                geoPoints.add(GeoPoint(lat, lon, alt))
            }
            val namePattern = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"")
            val nameMatcher = namePattern.matcher(jsonContent)
            if (nameMatcher.find()) {
                extractedName = nameMatcher.group(1) ?: defaultName
            }
        }

        if (geoPoints.size < 3) return null
        return buildImportedField(extractedName, geoPoints)
    }

    private fun buildImportedField(name: String, rawPoints: List<GeoPoint>): ImportedFieldResult {
        // Remove trailing identical point if closed polygon
        val points = if (rawPoints.size > 3 && rawPoints.first().latitude == rawPoints.last().latitude &&
            rawPoints.first().longitude == rawPoints.last().longitude) {
            rawPoints.dropLast(1)
        } else {
            rawPoints
        }

        // Calculate geographic centroid for local projection origin
        val avgLat = points.map { it.latitude }.average()
        val avgLon = points.map { it.longitude }.average()
        val avgAlt = points.map { it.altitude }.average()
        val originGeo = GeoPoint(avgLat, avgLon, avgAlt)

        // Convert to local Cartesian meters
        val localBoundary = points.map { GeoUtils.geoToLocalMeters(it, originGeo) }
        val areaM2 = GuidanceAlgorithms.calculatePolygonAreaM2(localBoundary)
        val areaAcres = areaM2 / 4046.86

        // Compute Headland Boundary (12m offset)
        val headland = GuidanceAlgorithms.generateHeadlandPolygon(localBoundary, 12.0)

        // Find the longest edge in the polygon to align the baseline AB line
        var maxEdgeLen = 0.0
        var bestA = localBoundary.first()
        var bestB = localBoundary[1]
        for (i in localBoundary.indices) {
            val p1 = localBoundary[i]
            val p2 = localBoundary[(i + 1) % localBoundary.size]
            val len = p1.distanceTo(p2)
            if (len > maxEdgeLen) {
                maxEdgeLen = len
                bestA = p1
                bestB = p2
            }
        }

        val aGeo = GeoUtils.localMetersToGeo(bestA, originGeo)
        val bGeo = GeoUtils.localMetersToGeo(bestB, originGeo)
        val abLine = ABLine.fromPoints(bestA, bestB, aGeo, bGeo, name = "$name Baseline")

        return ImportedFieldResult(
            fieldName = name,
            originGeo = originGeo,
            boundaryLocal = localBoundary,
            abLine = abLine,
            headlandBoundary = headland,
            areaAcres = areaAcres
        )
    }
}

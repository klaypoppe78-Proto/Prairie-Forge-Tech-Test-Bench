package com.example.agopengps.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.agopengps.navigation.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object FieldDataExporter {

    fun generateKml(
        fieldName: String,
        originGeo: GeoPoint,
        fieldBoundary: List<Vec2>,
        headlandBoundary: List<Vec2>,
        abLine: ABLine?,
        obstacles: List<FieldObstacle>,
        appliedSegments: List<AppliedSwathSegment> = emptyList(),
        workedAcres: Double = 0.0
    ): String = exportToKml(fieldName, originGeo, fieldBoundary, headlandBoundary, abLine, obstacles)

    fun generateCsv(
        fieldName: String,
        originGeo: GeoPoint,
        workedAcres: Double,
        fieldBoundary: List<Vec2>,
        abLine: ABLine?,
        obstacles: List<FieldObstacle>,
        appliedSegmentsCount: Int = 0
    ): String {
        val sb = StringBuilder()
        sb.append("FIELD NAME,ORIGIN LAT,ORIGIN LON,WORKED ACRES,BOUNDARY POINTS,APPLIED PASSES,OBSTACLES COUNT\n")
        sb.append("\"$fieldName\",${originGeo.latitude},${originGeo.longitude},$workedAcres,${fieldBoundary.size},$appliedSegmentsCount,${obstacles.size}\n\n")
        sb.append("OBSTACLES LOG\n")
        sb.append("NAME,TYPE,LATITUDE,LONGITUDE,RADIUS (M),NOTES\n")
        for (obs in obstacles) {
            sb.append("\"${obs.name}\",\"${obs.type.label}\",${obs.geoPos.latitude},${obs.geoPos.longitude},${obs.radiusMeters},\"${obs.notes}\"\n")
        }
        return sb.toString()
    }

    fun generateGeoJson(
        fieldName: String,
        originGeo: GeoPoint,
        fieldBoundary: List<Vec2>,
        abLine: ABLine?,
        obstacles: List<FieldObstacle>
    ): String {
        val root = JSONObject()
        root.put("type", "FeatureCollection")
        val features = JSONArray()

        if (fieldBoundary.size >= 3) {
            val coords = JSONArray()
            val ring = JSONArray()
            for (pt in fieldBoundary) {
                val geo = GeoUtils.localMetersToGeo(pt, originGeo)
                val coord = JSONArray().apply { put(geo.longitude); put(geo.latitude) }
                ring.put(coord)
            }
            val first = GeoUtils.localMetersToGeo(fieldBoundary.first(), originGeo)
            ring.put(JSONArray().apply { put(first.longitude); put(first.latitude) })
            coords.put(ring)

            val polyFeature = JSONObject().apply {
                put("type", "Feature")
                put("geometry", JSONObject().apply {
                    put("type", "Polygon")
                    put("coordinates", coords)
                })
                put("properties", JSONObject().apply {
                    put("name", "Boundary: $fieldName")
                })
            }
            features.put(polyFeature)
        }

        root.put("features", features)
        return root.toString(2)
    }

    fun shareFile(context: Context, filename: String, content: String, mimeType: String) =
        shareContent(context, filename, content, mimeType)

    fun exportToKml(
        fieldName: String,
        originGeo: GeoPoint,
        fieldBoundary: List<Vec2>,
        headlandBoundary: List<Vec2>,
        abLine: ABLine?,
        obstacles: List<FieldObstacle>
    ): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n")
        sb.append("  <Document>\n")
        sb.append("    <name>$fieldName</name>\n")

        // Field Boundary Polygon
        if (fieldBoundary.size >= 3) {
            sb.append("    <Placemark>\n")
            sb.append("      <name>Field Boundary</name>\n")
            sb.append("      <Polygon>\n")
            sb.append("        <outerBoundaryIs>\n")
            sb.append("          <LinearRing>\n")
            sb.append("            <coordinates>\n")
            for (pt in fieldBoundary) {
                val geo = GeoUtils.localMetersToGeo(pt, originGeo)
                sb.append("              ${geo.longitude},${geo.latitude},${geo.altitude}\n")
            }
            // Close the loop
            val firstGeo = GeoUtils.localMetersToGeo(fieldBoundary.first(), originGeo)
            sb.append("              ${firstGeo.longitude},${firstGeo.latitude},${firstGeo.altitude}\n")
            sb.append("            </coordinates>\n")
            sb.append("          </LinearRing>\n")
            sb.append("        </outerBoundaryIs>\n")
            sb.append("      </Polygon>\n")
            sb.append("    </Placemark>\n")
        }

        // AB Line
        if (abLine != null) {
            sb.append("    <Placemark>\n")
            sb.append("      <name>${abLine.name}</name>\n")
            sb.append("      <LineString>\n")
            sb.append("        <coordinates>\n")
            sb.append("          ${abLine.aGeo.longitude},${abLine.aGeo.latitude},${abLine.aGeo.altitude}\n")
            sb.append("          ${abLine.bGeo.longitude},${abLine.bGeo.latitude},${abLine.bGeo.altitude}\n")
            sb.append("        </coordinates>\n")
            sb.append("      </LineString>\n")
            sb.append("    </Placemark>\n")
        }

        // Obstacles
        for (obs in obstacles) {
            sb.append("    <Placemark>\n")
            sb.append("      <name>${obs.name} (${obs.type.label})</name>\n")
            sb.append("      <Point>\n")
            sb.append("        <coordinates>${obs.geoPos.longitude},${obs.geoPos.latitude},${obs.geoPos.altitude}</coordinates>\n")
            sb.append("      </Point>\n")
            sb.append("    </Placemark>\n")
        }

        sb.append("  </Document>\n")
        sb.append("</kml>\n")
        return sb.toString()
    }

    fun exportToJson(
        fieldName: String,
        originGeo: GeoPoint,
        fieldBoundary: List<Vec2>,
        headlandBoundary: List<Vec2>,
        abLine: ABLine?,
        obstacles: List<FieldObstacle>,
        appliedSegments: List<AppliedSwathSegment>,
        workedAcres: Double
    ): String {
        val root = JSONObject()
        root.put("version", "1.0")
        root.put("appName", "Sentinel Guidance")
        root.put("fieldName", fieldName)
        root.put("originLat", originGeo.latitude)
        root.put("originLon", originGeo.longitude)
        root.put("originAlt", originGeo.altitude)
        root.put("workedAcres", workedAcres)

        val boundaryArr = JSONArray()
        fieldBoundary.forEach { pt ->
            boundaryArr.put(JSONObject().apply { put("x", pt.x); put("y", pt.y) })
        }
        root.put("boundary", boundaryArr)

        val headlandArr = JSONArray()
        headlandBoundary.forEach { pt ->
            headlandArr.put(JSONObject().apply { put("x", pt.x); put("y", pt.y) })
        }
        root.put("headland", headlandArr)

        if (abLine != null) {
            val abObj = JSONObject().apply {
                put("name", abLine.name)
                put("headingDeg", abLine.headingDeg)
                put("ax", abLine.aLocal.x)
                put("ay", abLine.aLocal.y)
                put("bx", abLine.bLocal.x)
                put("by", abLine.bLocal.y)
            }
            root.put("abLine", abObj)
        }

        val obsArr = JSONArray()
        obstacles.forEach { obs ->
            obsArr.put(JSONObject().apply {
                put("id", obs.id)
                put("name", obs.name)
                put("type", obs.type.name)
                put("localX", obs.localPos.x)
                put("localY", obs.localPos.y)
                put("radiusMeters", obs.radiusMeters)
                put("notes", obs.notes)
            })
        }
        root.put("obstacles", obsArr)

        return root.toString(2)
    }

    fun exportToAgOpenGpsTxt(
        fieldName: String,
        originGeo: GeoPoint,
        fieldBoundary: List<Vec2>,
        abLine: ABLine?
    ): String {
        val sb = StringBuilder()
        sb.append("# Sentinel Guidance Field Definition\n")
        sb.append("# Field: $fieldName\n")
        sb.append("# Origin: ${originGeo.latitude}, ${originGeo.longitude}\n\n")

        sb.append("[Boundary]\n")
        for (pt in fieldBoundary) {
            sb.append("${pt.x}, ${pt.y}\n")
        }

        if (abLine != null) {
            sb.append("\n[ABLine]\n")
            sb.append("Heading: ${abLine.headingDeg}\n")
            sb.append("A: ${abLine.aLocal.x}, ${abLine.aLocal.y}\n")
            sb.append("B: ${abLine.bLocal.x}, ${abLine.bLocal.y}\n")
        }

        return sb.toString()
    }

    fun shareContent(context: Context, filename: String, content: String, mimeType: String) {
        try {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(exportDir, filename)
            file.writeText(content)

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, filename)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Export Field via USB / Share"))
        } catch (e: Exception) {
            // Fallback plain text share if FileProvider fails
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, content)
                putExtra(Intent.EXTRA_SUBJECT, filename)
            }
            context.startActivity(Intent.createChooser(intent, "Export Field"))
        }
    }
}

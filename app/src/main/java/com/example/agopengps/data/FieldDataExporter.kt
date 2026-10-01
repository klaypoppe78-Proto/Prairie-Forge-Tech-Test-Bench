package com.example.agopengps.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.agopengps.navigation.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Generates and shares field data files:
 * 1. Google Earth KML (.kml) containing boundary polygon, AB line vector, obstacles, and coverage polygons.
 * 2. Field Summary & Acreage CSV Report (.csv).
 * 3. Standard GIS GeoJSON (.geojson).
 */
object FieldDataExporter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US)
    private val displayFormat = SimpleDateFormat("MMMM dd, yyyy HH:mm", Locale.US)

    /**
     * Generates a Google Earth KML string.
     */
    fun generateKml(
        fieldName: String,
        originGeo: GeoPoint,
        fieldBoundary: List<Vec2>,
        headlandBoundary: List<Vec2>,
        abLine: ABLine?,
        obstacles: List<FieldObstacle>,
        appliedSegments: List<AppliedSwathSegment>,
        workedAcres: Double
    ): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n")
        sb.append("  <Document>\n")
        sb.append("    <name>${escapeXml(fieldName)} - AgOpenGPS Export</name>\n")
        sb.append("    <description>Exported from AgOpenGPS Android on ${displayFormat.format(Date())}. Total Worked: ${String.format(Locale.US, "%.2f", workedAcres)} acres.</description>\n")

        // Styles
        sb.append("    <Style id=\"boundaryStyle\">\n")
        sb.append("      <LineStyle><color>ff0055ff</color><width>3.5</width></LineStyle>\n")
        sb.append("      <PolyStyle><color>330055ff</color><fill>1</fill><outline>1</outline></PolyStyle>\n")
        sb.append("    </Style>\n")

        sb.append("    <Style id=\"headlandStyle\">\n")
        sb.append("      <LineStyle><color>ff00e5ff</color><width>2.0</width></LineStyle>\n")
        sb.append("    </Style>\n")

        sb.append("    <Style id=\"abLineStyle\">\n")
        sb.append("      <LineStyle><color>ff00e676</color><width>4.0</width></LineStyle>\n")
        sb.append("    </Style>\n")

        sb.append("    <Style id=\"coverageStyle\">\n")
        sb.append("      <LineStyle><color>6600c853</color><width>1.0</width></LineStyle>\n")
        sb.append("      <PolyStyle><color>6600e676</color><fill>1</fill><outline>1</outline></PolyStyle>\n")
        sb.append("    </Style>\n")

        sb.append("    <Style id=\"obstacleStyle\">\n")
        sb.append("      <IconStyle>\n")
        sb.append("        <color>ff00aaff</color>\n")
        sb.append("        <scale>1.2</scale>\n")
        sb.append("        <Icon><href>http://maps.google.com/mapfiles/kml/shapes/caution.png</href></Icon>\n")
        sb.append("      </IconStyle>\n")
        sb.append("    </Style>\n")

        // 1. Field Boundary Polygon
        if (fieldBoundary.size >= 3) {
            sb.append("    <Placemark>\n")
            sb.append("      <name>Field Boundary</name>\n")
            sb.append("      <styleUrl>#boundaryStyle</styleUrl>\n")
            sb.append("      <Polygon>\n")
            sb.append("        <outerBoundaryIs>\n")
            sb.append("          <LinearRing>\n")
            sb.append("            <coordinates>\n")
            for (pt in fieldBoundary) {
                val geo = GeoUtils.localMetersToGeo(pt, originGeo)
                sb.append("              ${geo.longitude},${geo.latitude},0\n")
            }
            // Close loop
            val firstGeo = GeoUtils.localMetersToGeo(fieldBoundary.first(), originGeo)
            sb.append("              ${firstGeo.longitude},${firstGeo.latitude},0\n")
            sb.append("            </coordinates>\n")
            sb.append("          </LinearRing>\n")
            sb.append("        </outerBoundaryIs>\n")
            sb.append("      </Polygon>\n")
            sb.append("    </Placemark>\n")
        }

        // 2. Headland Boundary
        if (headlandBoundary.size >= 3) {
            sb.append("    <Placemark>\n")
            sb.append("      <name>Headland Line</name>\n")
            sb.append("      <styleUrl>#headlandStyle</styleUrl>\n")
            sb.append("      <LineString>\n")
            sb.append("        <tessellate>1</tessellate>\n")
            sb.append("        <coordinates>\n")
            for (pt in headlandBoundary) {
                val geo = GeoUtils.localMetersToGeo(pt, originGeo)
                sb.append("          ${geo.longitude},${geo.latitude},0\n")
            }
            val firstGeo = GeoUtils.localMetersToGeo(headlandBoundary.first(), originGeo)
            sb.append("          ${firstGeo.longitude},${firstGeo.latitude},0\n")
            sb.append("        </coordinates>\n")
            sb.append("      </LineString>\n")
            sb.append("    </Placemark>\n")
        }

        // 3. AB Line
        if (abLine != null) {
            val aGeo = GeoUtils.localMetersToGeo(abLine.aLocal, originGeo)
            val bGeo = GeoUtils.localMetersToGeo(abLine.bLocal, originGeo)
            sb.append("    <Placemark>\n")
            sb.append("      <name>${escapeXml(abLine.name)} (Heading: ${String.format(Locale.US, "%.1f°", abLine.headingDeg)})</name>\n")
            sb.append("      <styleUrl>#abLineStyle</styleUrl>\n")
            sb.append("      <LineString>\n")
            sb.append("        <coordinates>\n")
            sb.append("          ${aGeo.longitude},${aGeo.latitude},0\n")
            sb.append("          ${bGeo.longitude},${bGeo.latitude},0\n")
            sb.append("        </coordinates>\n")
            sb.append("      </LineString>\n")
            sb.append("    </Placemark>\n")
        }

        // 4. Obstacles
        if (obstacles.isNotEmpty()) {
            sb.append("    <Folder><name>Field Obstacles &amp; Hazards</name>\n")
            for (obs in obstacles) {
                sb.append("      <Placemark>\n")
                sb.append("        <name>${escapeXml(obs.name)} [${obs.type.label}]</name>\n")
                sb.append("        <description>Safety Radius: ${obs.radiusMeters}m. Notes: ${escapeXml(obs.notes)}</description>\n")
                sb.append("        <styleUrl>#obstacleStyle</styleUrl>\n")
                sb.append("        <Point>\n")
                sb.append("          <coordinates>${obs.geoPos.longitude},${obs.geoPos.latitude},0</coordinates>\n")
                sb.append("        </Point>\n")
                sb.append("      </Placemark>\n")
            }
            sb.append("    </Folder>\n")
        }

        // 5. Applied Coverage Swaths
        if (appliedSegments.isNotEmpty()) {
            sb.append("    <Folder><name>Applied Coverage Passes</name>\n")
            val maxSample = appliedSegments.size.coerceAtMost(300) // Sample to avoid excessive KML size
            val step = (appliedSegments.size / maxSample).coerceAtLeast(1)
            for (i in appliedSegments.indices step step) {
                val seg = appliedSegments[i]
                val g1 = GeoUtils.localMetersToGeo(seg.leftStart, originGeo)
                val g2 = GeoUtils.localMetersToGeo(seg.rightStart, originGeo)
                val g3 = GeoUtils.localMetersToGeo(seg.rightEnd, originGeo)
                val g4 = GeoUtils.localMetersToGeo(seg.leftEnd, originGeo)
                sb.append("      <Placemark>\n")
                sb.append("        <styleUrl>#coverageStyle</styleUrl>\n")
                sb.append("        <Polygon><outerBoundaryIs><LinearRing><coordinates>\n")
                sb.append("          ${g1.longitude},${g1.latitude},0\n")
                sb.append("          ${g2.longitude},${g2.latitude},0\n")
                sb.append("          ${g3.longitude},${g3.latitude},0\n")
                sb.append("          ${g4.longitude},${g4.latitude},0\n")
                sb.append("          ${g1.longitude},${g1.latitude},0\n")
                sb.append("        </coordinates></LinearRing></outerBoundaryIs></Polygon>\n")
                sb.append("      </Placemark>\n")
            }
            sb.append("    </Folder>\n")
        }

        sb.append("  </Document>\n")
        sb.append("</kml>\n")
        return sb.toString()
    }

    /**
     * Generates a field summary CSV report.
     */
    fun generateCsv(
        fieldName: String,
        originGeo: GeoPoint,
        workedAcres: Double,
        fieldBoundary: List<Vec2>,
        abLine: ABLine?,
        obstacles: List<FieldObstacle>,
        appliedSegmentsCount: Int
    ): String {
        val sb = StringBuilder()
        sb.append("Field Name,${fieldName}\n")
        sb.append("Export Date,${displayFormat.format(Date())}\n")
        sb.append("Origin Latitude,${originGeo.latitude}\n")
        sb.append("Origin Longitude,${originGeo.longitude}\n")
        sb.append("Total Worked Acres,${String.format(Locale.US, "%.3f", workedAcres)}\n")
        sb.append("Applied Passes Count,${appliedSegmentsCount}\n")
        sb.append("Boundary Vertices,${fieldBoundary.size}\n")
        if (abLine != null) {
            sb.append("AB Line Name,${abLine.name}\n")
            sb.append("AB Line Heading,${String.format(Locale.US, "%.2f", abLine.headingDeg)}\n")
        }
        sb.append("\n--- FIELD OBSTACLES & HAZARDS ---\n")
        sb.append("ID,Name,Type,Latitude,Longitude,Safety Radius (m),Notes\n")
        for (obs in obstacles) {
            sb.append("${obs.id},\"${obs.name}\",\"${obs.type.label}\",${obs.geoPos.latitude},${obs.geoPos.longitude},${obs.radiusMeters},\"${obs.notes}\"\n")
        }
        return sb.toString()
    }

    /**
     * Generates GeoJSON string.
     */
    fun generateGeoJson(
        fieldName: String,
        originGeo: GeoPoint,
        fieldBoundary: List<Vec2>,
        abLine: ABLine?,
        obstacles: List<FieldObstacle>
    ): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"type\": \"FeatureCollection\",\n")
        sb.append("  \"name\": \"${escapeJson(fieldName)}\",\n")
        sb.append("  \"features\": [\n")

        val features = mutableListOf<String>()

        // Boundary
        if (fieldBoundary.size >= 3) {
            val coords = mutableListOf<String>()
            for (pt in fieldBoundary) {
                val g = GeoUtils.localMetersToGeo(pt, originGeo)
                coords.add("[${g.longitude}, ${g.latitude}]")
            }
            val firstG = GeoUtils.localMetersToGeo(fieldBoundary.first(), originGeo)
            coords.add("[${firstG.longitude}, ${firstG.latitude}]")
            features.add("""
                {
                  "type": "Feature",
                  "properties": { "name": "Field Boundary", "type": "boundary" },
                  "geometry": {
                    "type": "Polygon",
                    "coordinates": [ [ ${coords.joinToString(", ")} ] ]
                  }
                }
            """.trimIndent())
        }

        // Obstacles
        for (obs in obstacles) {
            features.add("""
                {
                  "type": "Feature",
                  "properties": {
                    "name": "${escapeJson(obs.name)}",
                    "hazardType": "${obs.type.label}",
                    "radiusMeters": ${obs.radiusMeters},
                    "notes": "${escapeJson(obs.notes)}"
                  },
                  "geometry": {
                    "type": "Point",
                    "coordinates": [ ${obs.geoPos.longitude}, ${obs.geoPos.latitude} ]
                  }
                }
            """.trimIndent())
        }

        sb.append(features.joinToString(",\n"))
        sb.append("\n  ]\n}")
        return sb.toString()
    }

    /**
     * Saves content to a file in app cache and triggers the Android share sheet.
     */
    fun shareFile(context: Context, filename: String, content: String, mimeType: String) {
        try {
            val exportDir = File(context.cacheDir, "exports")
            if (!exportDir.exists()) exportDir.mkdirs()

            val file = File(exportDir, filename)
            FileOutputStream(file).use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "AgOpenGPS Export: $filename")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share $filename"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun escapeXml(s: String): String {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    }

    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
    }
}

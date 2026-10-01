package com.example

import com.example.agopengps.io.AgIoMockHardwareLoopback
import com.example.agopengps.navigation.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class AgSteerNavigationAndBenchTest {

    @Test
    fun testShoelaceAreaCalculation() {
        // 100m x 100m square = 10,000 m2
        val square = listOf(
            Vec2(0.0, 0.0),
            Vec2(100.0, 0.0),
            Vec2(100.0, 100.0),
            Vec2(0.0, 100.0)
        )
        val area = GuidanceAlgorithms.calculatePolygonAreaM2(square)
        assertEquals(10000.0, area, 0.01)
    }

    @Test
    fun testHeadlandPolygonGeneration() {
        val square = listOf(
            Vec2(0.0, 0.0),
            Vec2(100.0, 0.0),
            Vec2(100.0, 100.0),
            Vec2(0.0, 100.0)
        )
        val headland = GuidanceAlgorithms.generateHeadlandPolygon(square, 10.0)
        assertEquals(4, headland.size)
        val headlandArea = GuidanceAlgorithms.calculatePolygonAreaM2(headland)
        assertTrue("Headland area should be smaller than boundary", headlandArea < 10000.0)
    }

    @Test
    fun testUTurnPathGeneration() {
        val start = Vec2(0.0, 100.0)
        val nextStart = Vec2(12.0, 100.0)
        val path = GuidanceAlgorithms.generateUTurnPath(
            currentSwathEnd = start,
            currentHeadingDeg = 0.0,
            nextSwathStart = nextStart,
            turnRadiusMeters = 6.0
        )
        assertTrue("UTurn path should contain points connecting swaths", path.size >= 10)
    }

    @Test
    fun testKsxtDualAntennaParsing() {
        // Sample UM982 KSXT sentence
        // $KSXT,20231012143000.00,116.1234567,39.1234567,52.3,184.50,-1.25,12.3,0.02,2,3,45,42,0.85*5F
        val sentence = "\$KSXT,20231012143000.00,116.1234567,39.1234567,52.3,184.50,-1.25,12.3,0.02,2,3,45,42,0.85*5F"
        val clean = sentence.trim().removePrefix("\$").split("*")[0]
        val tokens = clean.split(",")
        assertEquals("KSXT", tokens[0])
        val lon = tokens[2].toDouble()
        val lat = tokens[3].toDouble()
        val heading = tokens[5].toDouble()
        val roll = tokens[6].toDouble()
        val baseline = tokens[13].toDouble()

        assertEquals(116.1234567, lon, 0.0001)
        assertEquals(39.1234567, lat, 0.0001)
        assertEquals(184.50, heading, 0.01)
        assertEquals(-1.25, roll, 0.01)
        assertEquals(0.85, baseline, 0.01)
    }

    @Test
    fun testDistanceToBoundaryEdge() {
        val boundary = listOf(
            Vec2(0.0, 0.0),
            Vec2(100.0, 0.0),
            Vec2(100.0, 100.0),
            Vec2(0.0, 100.0)
        )
        // Vehicle at (50, 95) is 5m from the top boundary edge (y=100)
        val pos = Vec2(50.0, 95.0)
        val dist = GuidanceAlgorithms.distanceToBoundaryEdge(pos, boundary)
        assertEquals(5.0, dist, 0.01)
    }

    @Test
    fun testKmlFieldImport() {
        val sampleKml = """<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <name>North 40 Organic Field</name>
    <Placemark>
      <name>Boundary</name>
      <Polygon>
        <outerBoundaryIs>
          <LinearRing>
            <coordinates>
              -95.7170,44.9125,0
              -95.7130,44.9125,0
              -95.7130,44.9160,0
              -95.7170,44.9160,0
              -95.7170,44.9125,0
            </coordinates>
          </LinearRing>
        </outerBoundaryIs>
      </Polygon>
    </Placemark>
  </Document>
</kml>"""
        val result = com.example.agopengps.data.FieldDataImporter.parseFieldData(sampleKml, "Test Field")
        assertNotNull(result)
        assertEquals("North 40 Organic Field", result!!.fieldName)
        assertTrue("Boundary should have 4 or 5 vertices", result.boundaryLocal.size >= 4)
        assertTrue("Area should be approximately 30-40 acres", result.areaAcres > 20.0 && result.areaAcres < 60.0)
        assertNotNull("Should auto-generate AB line", result.abLine)
    }

    @Test
    fun testGeoJsonFieldImport() {
        val geoJson = """{
  "type": "FeatureCollection",
  "name": "South 80 Parcel",
  "features": [
    {
      "type": "Feature",
      "properties": { "name": "Parcel Boundary" },
      "geometry": {
        "type": "Polygon",
        "coordinates": [
          [
            [-95.7170, 44.9125],
            [-95.7090, 44.9125],
            [-95.7090, 44.9160],
            [-95.7170, 44.9160],
            [-95.7170, 44.9125]
          ]
        ]
      }
    }
  ]
}"""
        val result = com.example.agopengps.data.FieldDataImporter.parseFieldData(geoJson, "South 80")
        assertNotNull(result)
        assertEquals("South 80 Parcel", result!!.fieldName)
        assertTrue("Area should be non-zero", result.areaAcres > 0.0)
        assertTrue("Should have calculated headlands", result.headlandBoundary.isNotEmpty())
    }
}

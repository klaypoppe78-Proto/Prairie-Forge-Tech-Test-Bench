package com.example.agopengps.navigation

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

/**
 * High-resolution breadcrumb node along a recorded vehicle path.
 */
data class PathPoint(
    val localX: Double,
    val localY: Double,
    val headingDeg: Double = 0.0,
    val speedKmh: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toVec2(): Vec2 = Vec2(localX, localY)
}

/**
 * Complete recorded trajectory representing an exact fieldwork pass (e.g. 24-Row Planting Pass).
 */
data class RecordedPath(
    val id: Long = 0L,
    val name: String,
    val fieldId: Long = 0L,
    val points: List<PathPoint> = emptyList(),
    val totalDistanceMeters: Double = 0.0,
    val rowCount: Int = 24,
    val rowSpacingInches: Double = 22.0,
    val isPlanterTrack: Boolean = true,
    val activeCultivatorSplitPass: CultivatorSplitPass = CultivatorSplitPass.PASS_A_LEFT,
    val createdAt: Long = System.currentTimeMillis()
) {
    val totalWidthMeters: Double
        get() = (rowCount * rowSpacingInches * 0.0254)

    val totalWidthFeet: Double
        get() = (rowCount * rowSpacingInches) / 12.0

    /**
     * Serializes points to JSON for SQLite storage.
     */
    fun toJson(): String {
        val array = JSONArray()
        for (p in points) {
            val obj = JSONObject().apply {
                put("x", p.localX)
                put("y", p.localY)
                put("h", p.headingDeg)
                put("s", p.speedKmh)
                put("t", p.timestamp)
            }
            array.put(obj)
        }
        return array.toString()
    }

    companion object {
        fun fromJson(
            id: Long,
            name: String,
            fieldId: Long,
            json: String,
            totalDist: Double,
            rowCount: Int,
            rowSpacingInches: Double,
            isPlanter: Boolean,
            createdAt: Long
        ): RecordedPath {
            val list = mutableListOf<PathPoint>()
            try {
                if (json.isNotBlank()) {
                    val array = JSONArray(json)
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        list.add(
                            PathPoint(
                                localX = obj.optDouble("x", 0.0),
                                localY = obj.optDouble("y", 0.0),
                                headingDeg = obj.optDouble("h", 0.0),
                                speedKmh = obj.optDouble("s", 0.0),
                                timestamp = obj.optLong("t", 0L)
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // Ignore parsing errors on corrupted data
            }
            return RecordedPath(
                id = id,
                name = name,
                fieldId = fieldId,
                points = list,
                totalDistanceMeters = totalDist,
                rowCount = rowCount,
                rowSpacingInches = rowSpacingInches,
                isPlanterTrack = isPlanter,
                createdAt = createdAt
            )
        }
    }
}

/**
 * Cultivator pass choice when splitting a 24-Row Planter track into two 12-Row Cultivator tracks.
 */
enum class CultivatorSplitPass(val label: String, val shortTag: String) {
    FULL_PLANTER("24-Row Planter Centerline", "24R CENTER"),
    PASS_A_LEFT("Pass A (Left 12 Rows)", "12R LEFT"),
    PASS_B_RIGHT("Pass B (Right 12 Rows)", "12R RIGHT")
}

/**
 * Organic Cultivator Pass Splitting Engine.
 * Takes a 24-Row 22" (or custom) planter baseline / curve and derives the two exact 12-Row paths.
 */
object CultivatorSplittingEngine {

    /**
     * Splits a 24-Row Planter ABLine into two 12-Row Cultivator ABLines:
     * - Left 12 Rows: Offset left by 6 rows = 6 * 22" = 132 inches = 11.0 ft = 3.3528 m
     * - Right 12 Rows: Offset right by 6 rows = 6 * 22" = 132 inches = 11.0 ft = 3.3528 m
     * - Plus optional knife inter-row centering offset in inches.
     */
    fun splitPlanterAbLine(
        planterAb: ABLine,
        planterRows: Int = 24,
        cultivatorRows: Int = 12,
        rowSpacingInches: Double = 22.0,
        interRowKnifeOffsetInches: Double = 0.0,
        originGeo: GeoPoint
    ): Pair<ABLine, ABLine> {
        val rowSpacingMeters = rowSpacingInches * 0.0254
        val halfPlanterWidthMeters = (planterRows * rowSpacingMeters) / 2.0
        val halfCultivatorWidthMeters = (cultivatorRows * rowSpacingMeters) / 2.0

        // Distance from planter centerline to center of the 12-row pass = 6 rows = 3.3528m
        val offsetDistanceMeters = halfPlanterWidthMeters - halfCultivatorWidthMeters
        val knifeOffsetMeters = interRowKnifeOffsetInches * 0.0254

        // Normal vector pointing to the right of AB line
        val rightNormal = planterAb.normalVector
        val leftNormal = Vec2(-rightNormal.x, -rightNormal.y)

        // Pass A: Left 12 Rows (Offset Left)
        val leftShiftTotal = offsetDistanceMeters - knifeOffsetMeters
        val aLeftLocal = planterAb.aLocal + (leftNormal * leftShiftTotal)
        val bLeftLocal = planterAb.bLocal + (leftNormal * leftShiftTotal)
        val aLeftGeo = GeoUtils.localMetersToGeo(aLeftLocal, originGeo)
        val bLeftGeo = GeoUtils.localMetersToGeo(bLeftLocal, originGeo)
        val passALeft = ABLine.fromPoints(
            aLocal = aLeftLocal,
            bLocal = bLeftLocal,
            aGeo = aLeftGeo,
            bGeo = bLeftGeo,
            name = "${planterAb.name} [Pass A: Left 12R]"
        )

        // Pass B: Right 12 Rows (Offset Right)
        val rightShiftTotal = offsetDistanceMeters + knifeOffsetMeters
        val aRightLocal = planterAb.aLocal + (rightNormal * rightShiftTotal)
        val bRightLocal = planterAb.bLocal + (rightNormal * rightShiftTotal)
        val aRightGeo = GeoUtils.localMetersToGeo(aRightLocal, originGeo)
        val bRightGeo = GeoUtils.localMetersToGeo(bRightLocal, originGeo)
        val passBRight = ABLine.fromPoints(
            aLocal = aRightLocal,
            bLocal = bRightLocal,
            aGeo = aRightGeo,
            bGeo = bRightGeo,
            name = "${planterAb.name} [Pass B: Right 12R]"
        )

        return Pair(passALeft, passBRight)
    }

    /**
     * Splits a recorded curvilinear 24-Row planting path into two 12-Row cultivator paths.
     */
    fun splitRecordedPlanterPath(
        planterPath: RecordedPath,
        planterRows: Int = 24,
        cultivatorRows: Int = 12,
        rowSpacingInches: Double = 22.0,
        interRowKnifeOffsetInches: Double = 0.0
    ): Pair<RecordedPath, RecordedPath> {
        val pts = planterPath.points
        if (pts.size < 2) return Pair(planterPath, planterPath)

        val rowSpacingMeters = rowSpacingInches * 0.0254
        val halfPlanterWidthMeters = (planterRows * rowSpacingMeters) / 2.0
        val halfCultivatorWidthMeters = (cultivatorRows * rowSpacingMeters) / 2.0
        val offsetDistanceMeters = halfPlanterWidthMeters - halfCultivatorWidthMeters
        val knifeOffsetMeters = interRowKnifeOffsetInches * 0.0254

        val leftShift = offsetDistanceMeters - knifeOffsetMeters
        val rightShift = offsetDistanceMeters + knifeOffsetMeters

        val leftPts = mutableListOf<PathPoint>()
        val rightPts = mutableListOf<PathPoint>()

        for (i in pts.indices) {
            val curr = pts[i]
            val headingRad = Math.toRadians(curr.headingDeg)
            // Forward unit vector in local coordinates
            val forward = Vec2(sin(headingRad), cos(headingRad))
            // Right normal
            val rightNorm = Vec2(forward.y, -forward.x)
            val leftNorm = Vec2(-rightNorm.x, -rightNorm.y)

            val pLeft = curr.toVec2() + (leftNorm * leftShift)
            val pRight = curr.toVec2() + (rightNorm * rightShift)

            leftPts.add(PathPoint(pLeft.x, pLeft.y, curr.headingDeg, curr.speedKmh, curr.timestamp))
            rightPts.add(PathPoint(pRight.x, pRight.y, curr.headingDeg, curr.speedKmh, curr.timestamp))
        }

        val passALeft = RecordedPath(
            id = 0L,
            name = "${planterPath.name} - Left 12R Cultivator",
            fieldId = planterPath.fieldId,
            points = leftPts,
            totalDistanceMeters = planterPath.totalDistanceMeters,
            rowCount = cultivatorRows,
            rowSpacingInches = rowSpacingInches,
            isPlanterTrack = false,
            activeCultivatorSplitPass = CultivatorSplitPass.PASS_A_LEFT,
            createdAt = System.currentTimeMillis()
        )

        val passBRight = RecordedPath(
            id = 0L,
            name = "${planterPath.name} - Right 12R Cultivator",
            fieldId = planterPath.fieldId,
            points = rightPts,
            totalDistanceMeters = planterPath.totalDistanceMeters,
            rowCount = cultivatorRows,
            rowSpacingInches = rowSpacingInches,
            isPlanterTrack = false,
            activeCultivatorSplitPass = CultivatorSplitPass.PASS_B_RIGHT,
            createdAt = System.currentTimeMillis()
        )

        return Pair(passALeft, passBRight)
    }

    /**
     * Calculates tracking cross-track error and target steer heading for following an exact recorded path.
     */
    fun calculatePathTracking(
        pivotPosition: Vec2,
        tractorHeadingDeg: Double,
        pathPoints: List<PathPoint>,
        lookaheadDistanceMeters: Double = 3.5
    ): PathTrackingResult {
        if (pathPoints.size < 2) {
            return PathTrackingResult(0.0, tractorHeadingDeg, 0, 0.0)
        }

        var minDistance = Double.MAX_VALUE
        var nearestSegmentIndex = 0
        var nearestProjection = pathPoints[0].toVec2()
        var pathHeadingAtProjection = pathPoints[0].headingDeg

        // Find nearest line segment on polyline
        for (i in 0 until pathPoints.size - 1) {
            val p1 = pathPoints[i].toVec2()
            val p2 = pathPoints[i + 1].toVec2()
            val seg = p2 - p1
            val segLenSq = seg.x * seg.x + seg.y * seg.y
            if (segLenSq < 1e-6) continue

            val toPivot = pivotPosition - p1
            val t = ((toPivot.x * seg.x + toPivot.y * seg.y) / segLenSq).coerceIn(0.0, 1.0)
            val projection = p1 + (seg * t)
            val dist = pivotPosition.distanceTo(projection)

            if (dist < minDistance) {
                minDistance = dist
                nearestSegmentIndex = i
                nearestProjection = projection
                val segBearing = Math.toDegrees(atan2(seg.x, seg.y))
                pathHeadingAtProjection = (segBearing + 360.0) % 360.0
            }
        }

        // Determine cross-track signed error (positive = vehicle is to the right of path)
        val p1 = pathPoints[nearestSegmentIndex].toVec2()
        val p2 = pathPoints[nearestSegmentIndex + 1].toVec2()
        val seg = p2 - p1
        val toVeh = pivotPosition - p1
        val crossProduct = seg.x * toVeh.y - seg.y * toVeh.x
        val crossTrackErrorMeters = if (crossProduct > 0) minDistance else -minDistance

        // Find lookahead target point along the path
        var accumulatedDist = 0.0
        var targetPoint = pathPoints.last().toVec2()
        var targetHeading = pathHeadingAtProjection

        for (i in nearestSegmentIndex until pathPoints.size - 1) {
            val pa = if (i == nearestSegmentIndex) nearestProjection else pathPoints[i].toVec2()
            val pb = pathPoints[i + 1].toVec2()
            val segDist = pa.distanceTo(pb)
            if (accumulatedDist + segDist >= lookaheadDistanceMeters) {
                val rem = lookaheadDistanceMeters - accumulatedDist
                val ratio = (rem / segDist).coerceIn(0.0, 1.0)
                targetPoint = pa + ((pb - pa) * ratio)
                val targetSeg = pb - pa
                targetHeading = (Math.toDegrees(atan2(targetSeg.x, targetSeg.y)) + 360.0) % 360.0
                break
            }
            accumulatedDist += segDist
        }

        val toTarget = targetPoint - pivotPosition
        val targetBearingDeg = (Math.toDegrees(atan2(toTarget.x, toTarget.y)) + 360.0) % 360.0

        return PathTrackingResult(
            crossTrackErrorMeters = crossTrackErrorMeters,
            targetSteerHeadingDeg = targetBearingDeg,
            nearestPointIndex = nearestSegmentIndex,
            distanceRemainingMeters = (pathPoints.size - nearestSegmentIndex).toDouble()
        )
    }
}

data class PathTrackingResult(
    val crossTrackErrorMeters: Double,
    val targetSteerHeadingDeg: Double,
    val nearestPointIndex: Int,
    val distanceRemainingMeters: Double
)

package com.example.agopengps.navigation

import kotlin.math.*

enum class SectionOverride {
    AUTO,
    FORCED_ON,
    FORCED_OFF
}

/**
 * Representation of an AB Guidance Line.
 */
data class ABLine(
    val id: Long = 0,
    val name: String = "AB Line 1",
    val aLocal: Vec2,
    val bLocal: Vec2,
    val headingDeg: Double,
    val aGeo: GeoPoint,
    val bGeo: GeoPoint,
    val isAPlusLine: Boolean = false
) {
    val dirVector: Vec2
    val normalVector: Vec2

    init {
        val headingRad = Math.toRadians(headingDeg)
        dirVector = Vec2(sin(headingRad), cos(headingRad))
        normalVector = dirVector.perpClockwise()
    }

    companion object {
        fun fromPoints(aLocal: Vec2, bLocal: Vec2, aGeo: GeoPoint, bGeo: GeoPoint, name: String = "AB Line"): ABLine {
            val dx = bLocal.x - aLocal.x
            val dy = bLocal.y - aLocal.y
            val headingRad = atan2(dx, dy)
            val headingDeg = (Math.toDegrees(headingRad) + 360.0) % 360.0
            return ABLine(
                name = name,
                aLocal = aLocal,
                bLocal = bLocal,
                headingDeg = headingDeg,
                aGeo = aGeo,
                bGeo = bGeo,
                isAPlusLine = false
            )
        }

        fun fromAPlus(aLocal: Vec2, headingDeg: Double, aGeo: GeoPoint, name: String = "A+ Line"): ABLine {
            val headingRad = Math.toRadians(headingDeg)
            val dir = Vec2(sin(headingRad), cos(headingRad))
            val bLocal = aLocal + (dir * 100.0)
            val bGeo = GeoUtils.localMetersToGeo(bLocal, aGeo)
            return ABLine(
                name = name,
                aLocal = aLocal,
                bLocal = bLocal,
                headingDeg = (headingDeg + 360.0) % 360.0,
                aGeo = aGeo,
                bGeo = bGeo,
                isAPlusLine = true
            )
        }
    }
}

/**
 * Representation of an AgOpenGPS Curved / Contour Guidance line.
 */
data class CurveLine(
    val id: Long = 0,
    val name: String = "Curve Line 1",
    val points: List<Vec2> = emptyList()
)

/**
 * Core navigation, terrain compensation, and steering algorithms ported from AgOpenGPS.
 */
object GuidanceAlgorithms {

    /**
     * Terrain Roll and Antenna Offset Compensation.
     */
    fun compensateAntennaPosition(
        antennaLocal: Vec2,
        headingDeg: Double,
        rollDeg: Double,
        vehicle: VehicleConfig,
        rollZeroOffset: Double = 0.0
    ): Vec2 {
        val effectiveRoll = rollDeg - rollZeroOffset
        val rollRad = Math.toRadians(effectiveRoll)
        val headingRad = Math.toRadians(headingDeg)

        // Lateral antenna shift due to cab roll (antenna height * sin(roll))
        val rollShiftDistance = vehicle.antennaHeight * sin(rollRad)

        // Perpendicular vector to heading pointing left (counter-clockwise)
        val headingDir = Vec2(sin(headingRad), cos(headingRad))
        val leftPerp = headingDir.perpCounterClockwise()

        val rollCompensated = antennaLocal + (leftPerp * rollShiftDistance)
        return rollCompensated - (headingDir * vehicle.antennaPivotOffset)
    }

    /**
     * Compensates internal tablet accelerometer roll for lateral centrifugal acceleration in sharp turns.
     * a_lat = v * omega (m/s * rad/s).
     * Gravity g = 9.80665 m/s^2.
     * Corrected lateral tilt = accel_lat - (v * omega).
     */
    fun calculateCentrifugalCompensatedRoll(
        rawAccelRollDeg: Double,
        speedKmh: Double,
        yawRateRadPerSec: Double
    ): Double {
        val speedMps = speedKmh / 3.6
        val g = 9.80665
        val centrifugalAccel = speedMps * yawRateRadPerSec
        // Centrifugal tilt error in radians ~ centrifugalAccel / g
        val centrifugalAngleRad = atan2(centrifugalAccel, g)
        val centrifugalAngleDeg = Math.toDegrees(centrifugalAngleRad)
        return rawAccelRollDeg - centrifugalAngleDeg
    }

    /**
     * Translates internal Android device/tablet GPS antenna position to the tractor rear axle pivot
     * using the user-calibrated 3D mounting offset (X lateral, Y forward, Z height) and terrain roll.
     */
    fun compensateTabletPosition(
        tabletLocal: Vec2,
        headingDeg: Double,
        rollDeg: Double,
        vehicle: VehicleConfig,
        rollZeroOffset: Double = 0.0
    ): Vec2 {
        val effectiveRoll = rollDeg - rollZeroOffset
        val rollRad = Math.toRadians(effectiveRoll)
        val headingRad = Math.toRadians(headingDeg)

        // Lateral antenna shift due to cab roll (tablet height * sin(roll))
        val rollShiftDistance = vehicle.tabletHeightAboveAxle * sin(rollRad)

        val headingDir = Vec2(sin(headingRad), cos(headingRad))
        val rightPerp = headingDir.perpClockwise()
        val leftPerp = headingDir.perpCounterClockwise()

        // 1. Compensate cab roll tilt
        val rollCompensated = tabletLocal + (leftPerp * rollShiftDistance)
        // 2. Subtract tablet mount offsets (forward of axle, right of centerline)
        return rollCompensated - (headingDir * vehicle.tabletForwardOffset) - (rightPerp * vehicle.tabletRightOffset)
    }

    /**
     * Finds the nearest parallel swath pass and calculates cross-track and heading errors for an AB line.
     */
    fun calculateSwathTracking(
        pivotPosition: Vec2,
        tractorHeadingDeg: Double,
        abLine: ABLine,
        swathWidth: Double
    ): SwathResult {
        val toPivot = pivotPosition - abLine.aLocal
        val perpDist = toPivot.dot(abLine.normalVector)

        // Calculate swath index N (integer pass number)
        val swathIndex = round(perpDist / swathWidth).toInt()
        val swathOrigin = abLine.aLocal + (abLine.normalVector * (swathIndex * swathWidth))

        val deltaHeading = GeoUtils.normalizeAngleDeg(tractorHeadingDeg - abLine.headingDeg)
        val isReversePass = abs(deltaHeading) > 90.0

        val targetHeadingDeg: Double
        val activeNormal: Vec2
        val activeDir: Vec2

        if (isReversePass) {
            targetHeadingDeg = (abLine.headingDeg + 180.0) % 360.0
            activeDir = abLine.dirVector * -1.0
            activeNormal = activeDir.perpClockwise()
        } else {
            targetHeadingDeg = abLine.headingDeg
            activeDir = abLine.dirVector
            activeNormal = abLine.normalVector
        }

        val toPivotActive = pivotPosition - swathOrigin
        val crossTrackError = toPivotActive.dot(activeNormal)
        val headingErrorDeg = GeoUtils.normalizeAngleDeg(tractorHeadingDeg - targetHeadingDeg)
        val projectedOnSwath = pivotPosition.projectOntoLine(swathOrigin, activeDir)

        return SwathResult(
            activeSwathIndex = swathIndex,
            swathOrigin = swathOrigin,
            swathDir = activeDir,
            crossTrackErrorMeters = crossTrackError,
            headingErrorDeg = headingErrorDeg,
            targetHeadingDeg = targetHeadingDeg,
            projectedPoint = projectedOnSwath,
            isReversePass = isReversePass
        )
    }

    /**
     * Curved / Contour Swath Tracking Algorithm (AgOpenGPS Curve Mode).
     * Finds closest segment along a polyline and computes offset curve guidance.
     */
    fun calculateCurveTracking(
        pivotPosition: Vec2,
        tractorHeadingDeg: Double,
        curveLine: CurveLine,
        swathWidth: Double
    ): SwathResult? {
        val pts = curveLine.points
        if (pts.size < 2) return null

        var bestDistSq = Double.MAX_VALUE
        var bestSegIdx = 0
        var bestProj = pts[0]

        for (i in 0 until pts.size - 1) {
            val p0 = pts[i]
            val p1 = pts[i + 1]
            val segVec = p1 - p0
            val segLenSq = segVec.lengthSquared()
            if (segLenSq < 1e-4) continue

            val t = ((pivotPosition - p0).dot(segVec) / segLenSq).coerceIn(0.0, 1.0)
            val proj = p0 + (segVec * t)
            val distSq = pivotPosition.distanceTo(proj).let { it * it }

            if (distSq < bestDistSq) {
                bestDistSq = distSq
                bestSegIdx = i
                bestProj = proj
            }
        }

        val p0 = pts[bestSegIdx]
        val p1 = pts[bestSegIdx + 1]
        val segDir = (p1 - p0).normalized()
        val segNormal = segDir.perpClockwise()

        val toPivot = pivotPosition - bestProj
        val perpDist = toPivot.dot(segNormal)
        val passIndex = round(perpDist / swathWidth).toInt()
        val offsetProj = bestProj + (segNormal * (passIndex * swathWidth))

        val segHeadingRad = atan2(segDir.x, segDir.y)
        val segHeadingDeg = (Math.toDegrees(segHeadingRad) + 360.0) % 360.0

        val deltaHeading = GeoUtils.normalizeAngleDeg(tractorHeadingDeg - segHeadingDeg)
        val isReverse = abs(deltaHeading) > 90.0

        val targetHeading = if (isReverse) (segHeadingDeg + 180.0) % 360.0 else segHeadingDeg
        val activeDir = if (isReverse) segDir * -1.0 else segDir
        val activeNormal = activeDir.perpClockwise()

        val cte = (pivotPosition - offsetProj).dot(activeNormal)
        val headingError = GeoUtils.normalizeAngleDeg(tractorHeadingDeg - targetHeading)

        return SwathResult(
            activeSwathIndex = passIndex,
            swathOrigin = offsetProj,
            swathDir = activeDir,
            crossTrackErrorMeters = cte,
            headingErrorDeg = headingError,
            targetHeadingDeg = targetHeading,
            projectedPoint = offsetProj,
            isReversePass = isReverse
        )
    }

    /**
     * Stanley Steering Control Law (AgOpenGPS core steering engine).
     */
    fun calculateStanleySteering(
        crossTrackErrorMeters: Double,
        headingErrorDeg: Double,
        speedKmh: Double,
        settings: GuidanceSettings,
        vehicle: VehicleConfig,
        integralAccumulator: Double = 0.0
    ): Double {
        val speedMps = max(speedKmh / 3.6, 0.1)
        val headingErrorRad = Math.toRadians(headingErrorDeg)

        val deadbandMeters = settings.deadbandCm / 100.0
        val effectiveError = when {
            abs(crossTrackErrorMeters) <= deadbandMeters -> 0.0
            crossTrackErrorMeters > 0 -> crossTrackErrorMeters - deadbandMeters
            else -> crossTrackErrorMeters + deadbandMeters
        }

        val crossTrackCorrectionRad = -atan2(settings.stanleyGain * effectiveError, speedMps + 0.5)
        val headingCorrectionRad = -headingErrorRad
        val integralCorrectionRad = -settings.stanleyIntegralGain * integralAccumulator

        val targetSteerRad = headingCorrectionRad + crossTrackCorrectionRad + integralCorrectionRad
        val targetSteerDeg = Math.toDegrees(targetSteerRad)

        return targetSteerDeg.coerceIn(-vehicle.maxSteerAngleDeg, vehicle.maxSteerAngleDeg)
    }

    /**
     * Pure Pursuit Steering Controller.
     */
    fun calculatePurePursuitSteering(
        pivotPosition: Vec2,
        tractorHeadingDeg: Double,
        swathOrigin: Vec2,
        swathDir: Vec2,
        speedKmh: Double,
        settings: GuidanceSettings,
        vehicle: VehicleConfig,
        lookaheadTimeOverrideSec: Double? = null
    ): Double {
        val speedMps = max(speedKmh / 3.6, 0.2)
        val lookaheadTime = lookaheadTimeOverrideSec ?: settings.purePursuitLookaheadGain

        val lookaheadDist = (speedMps * lookaheadTime)
            .coerceIn(settings.purePursuitMinLookahead, settings.purePursuitMaxLookahead)

        val proj = pivotPosition.projectOntoLine(swathOrigin, swathDir)
        val lookaheadTarget = proj + (swathDir * lookaheadDist)

        val toTarget = lookaheadTarget - pivotPosition
        val targetAngleRad = atan2(toTarget.x, toTarget.y)
        val targetAngleDeg = (Math.toDegrees(targetAngleRad) + 360.0) % 360.0

        val alphaDeg = GeoUtils.normalizeAngleDeg(targetAngleDeg - tractorHeadingDeg)
        val alphaRad = Math.toRadians(alphaDeg)

        val actualLookahead = toTarget.length().coerceAtLeast(1.0)
        val curvature = (2.0 * sin(alphaRad)) / actualLookahead

        val steerAngleRad = atan(curvature * vehicle.wheelbase)
        val steerAngleDeg = Math.toDegrees(steerAngleRad)

        return steerAngleDeg.coerceIn(-vehicle.maxSteerAngleDeg, vehicle.maxSteerAngleDeg)
    }

    /**
     * Calculates implement boom coordinates and section status with manual override support.
     */
    fun calculateImplementSections(
        pivotPosition: Vec2,
        tractorHeadingDeg: Double,
        vehicle: VehicleConfig,
        implement: ImplementConfig,
        fieldBoundary: List<Vec2>?,
        appliedPolygons: List<AppliedSwathSegment>,
        sectionOverrides: List<SectionOverride> = emptyList(),
        spatialGrid: CoverageSpatialGrid? = null
    ): ImplementStatus {
        val headingRad = Math.toRadians(tractorHeadingDeg)
        val forwardDir = Vec2(sin(headingRad), cos(headingRad))
        val rightDir = forwardDir.perpClockwise()

        // 3-point hitch toolbar vs trailing toolbar hitch kinematics
        val toolbarDistanceBehindPivot = vehicle.hitchLength - implement.offsetBehindTractor
        val toolBarCenter = pivotPosition + (forwardDir * toolbarDistanceBehindPivot)
        val hitchPinPos = pivotPosition + (forwardDir * vehicle.hitchLength)

        val sectionSpans = implement.getSectionSpans()
        val sectionPoints = mutableListOf<SectionBoomPosition>()
        val sectionActive = mutableListOf<Boolean>()

        for (i in sectionSpans.indices) {
            val (leftOffset, rightOffset) = sectionSpans[i]
            val leftPos = toolBarCenter + (rightDir * leftOffset)
            val rightPos = toolBarCenter + (rightDir * rightOffset)
            val centerPos = (leftPos + rightPos) * 0.5

            sectionPoints.add(SectionBoomPosition(i, leftPos, rightPos, centerPos))

            val override = sectionOverrides.getOrNull(i) ?: SectionOverride.AUTO

            when (override) {
                SectionOverride.FORCED_ON -> sectionActive.add(true)
                SectionOverride.FORCED_OFF -> sectionActive.add(false)
                SectionOverride.AUTO -> {
                    if (!implement.isMasterActive) {
                        sectionActive.add(false)
                    } else if (!implement.isAutoSectionControl) {
                        sectionActive.add(true)
                    } else {
                        val insideBoundary = fieldBoundary == null || isPointInPolygon(centerPos, fieldBoundary)
                        val alreadyCovered = isPointCovered(centerPos, appliedPolygons, spatialGrid)
                        sectionActive.add(insideBoundary && !alreadyCovered)
                    }
                }
            }
        }

        return ImplementStatus(
            hitchPosition = hitchPinPos,
            sections = sectionPoints,
            sectionStates = sectionActive
        )
    }

    /**
     * Calculates exact polygon area using Shoelace formula. Returns square meters.
     */
    fun calculatePolygonAreaM2(polygon: List<Vec2>): Double {
        if (polygon.size < 3) return 0.0
        var area = 0.0
        var j = polygon.size - 1
        for (i in polygon.indices) {
            area += (polygon[j].x + polygon[i].x) * (polygon[j].y - polygon[i].y)
            j = i
        }
        return abs(area * 0.5)
    }

    /**
     * Calculates inner headland polygon offset by distance (meters).
     */
    fun generateHeadlandPolygon(boundary: List<Vec2>, offsetDistanceMeters: Double): List<Vec2> {
        if (boundary.size < 3 || offsetDistanceMeters <= 0.0) return emptyList()
        val headland = mutableListOf<Vec2>()
        val n = boundary.size
        for (i in 0 until n) {
            val prev = boundary[(i - 1 + n) % n]
            val curr = boundary[i]
            val next = boundary[(i + 1) % n]

            val v1 = (curr - prev).normalized()
            val v2 = (next - curr).normalized()

            // Inward normal
            val n1 = v1.perpCounterClockwise()
            val n2 = v2.perpCounterClockwise()

            val bisector = (n1 + n2).normalized()
            val cosHalf = bisector.dot(n1)
            val d = if (abs(cosHalf) > 0.1) offsetDistanceMeters / cosHalf else offsetDistanceMeters

            headland.add(curr + (bisector * d))
        }
        return headland
    }

    /**
     * Calculates turn arc (Dubins turn path) from current swath to next swath at field boundary.
     */
    fun generateUTurnPath(
        currentSwathEnd: Vec2,
        currentHeadingDeg: Double,
        nextSwathStart: Vec2,
        turnRadiusMeters: Double
    ): List<Vec2> {
        val path = mutableListOf<Vec2>()
        val headRad = Math.toRadians(currentHeadingDeg)
        val forwardDir = Vec2(sin(headRad), cos(headRad))

        // Determine if next swath is to the right or left
        val toNext = nextSwathStart - currentSwathEnd
        val normal = forwardDir.perpClockwise()
        val isRightTurn = toNext.dot(normal) >= 0

        val turnSign = if (isRightTurn) 1.0 else -1.0
        val center = currentSwathEnd + (normal * (turnSign * turnRadiusMeters))

        val steps = 18
        for (i in 0..steps) {
            val angle = headRad - (turnSign * (PI * (i.toDouble() / steps.toDouble())))
            val pt = center + Vec2(-sin(angle) * turnRadiusMeters * turnSign, cos(angle) * turnRadiusMeters)
            path.add(pt)
        }
        path.add(nextSwathStart)
        return path
    }

    fun isPointInPolygon(point: Vec2, polygon: List<Vec2>): Boolean {
        if (polygon.size < 3) return true
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val pi = polygon[i]
            val pj = polygon[j]
            if ((pi.y > point.y) != (pj.y > point.y) &&
                point.x < (pj.x - pi.x) * (point.y - pi.y) / (pj.y - pi.y) + pi.x
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    /**
     * Calculates speed-adaptive lookahead distance to provide snappy low-speed line acquisition
     * without high-speed oscillation or line hunting.
     */
    fun calculateSpeedAdaptiveLookahead(
        speedKmh: Double,
        baseLookaheadSec: Double = 0.85,
        minLookaheadMeters: Double = 2.0,
        maxLookaheadMeters: Double = 14.0
    ): Double {
        val speedMps = max(speedKmh / 3.6, 0.1)
        val dynamicLookaheadSec = when {
            speedKmh < 4.0 -> 1.6 // Low-speed tight turn-in responsiveness
            speedKmh < 9.0 -> baseLookaheadSec.coerceAtLeast(1.0) // Nominal field working speed (4-9 km/h)
            speedKmh < 16.0 -> baseLookaheadSec * 1.25 // Fast spraying / transport damping
            else -> baseLookaheadSec * 1.5
        }
        return (speedMps * dynamicLookaheadSec).coerceIn(minLookaheadMeters, maxLookaheadMeters)
    }

    /**
     * Checks if the tractor or implement is approaching any flagged field obstacle/hazard.
     * Returns the closest obstacle within the warning threshold (if any).
     */
    fun checkObstacleProximity(
        pivotPosition: Vec2,
        obstacles: List<FieldObstacle>,
        warningThresholdMeters: Double = 12.0
    ): FieldObstacle? {
        if (obstacles.isEmpty()) return null
        var closestObs: FieldObstacle? = null
        var minDistance = Double.MAX_VALUE

        for (obs in obstacles) {
            val dist = pivotPosition.distanceTo(obs.localPos)
            val effectiveWarningDistance = obs.radiusMeters + warningThresholdMeters
            if (dist <= effectiveWarningDistance && dist < minDistance) {
                minDistance = dist
                closestObs = obs
            }
        }
        return closestObs
    }

    /**
     * Checks if a 2D local point is inside a polygon boundary using ray casting algorithm.
     */
    fun isPointInsidePolygon(point: Vec2, polygon: List<Vec2>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val pi = polygon[i]
            val pj = polygon[j]
            if ((pi.y > point.y) != (pj.y > point.y) &&
                point.x < (pj.x - pi.x) * (point.y - pi.y) / (pj.y - pi.y) + pi.x
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    /**
     * Calculates headland turn guidance and directional turn arrow when approaching or crossing headlands.
     */
    fun calculateHeadlandTurnGuidance(
        pivotPosition: Vec2,
        tractorHeadingDeg: Double,
        activeSwathIndex: Int,
        headlandBoundary: List<Vec2>?,
        swathDir: Vec2
    ): HeadlandTurnInfo {
        if (headlandBoundary == null || headlandBoundary.size < 3) {
            return HeadlandTurnInfo()
        }

        val distToHeadland = distanceToBoundaryEdge(pivotPosition, headlandBoundary)
        val isInsideHeadland = isPointInsidePolygon(pivotPosition, headlandBoundary)

        // Approaching headland line within 18 meters or outside
        val isApproaching = distToHeadland < 18.0 || !isInsideHeadland

        if (!isApproaching) {
            return HeadlandTurnInfo(
                direction = TurnDirection.NONE,
                distanceToTurnMeters = distToHeadland,
                targetSwathIndex = activeSwathIndex,
                isApproachingHeadland = false
            )
        }

        // Swath perpendicular direction relative to forward motion
        val headingRad = Math.toRadians(tractorHeadingDeg)
        val forwardDir = Vec2(sin(headingRad), cos(headingRad))
        val rightNormal = forwardDir.perpClockwise()

        val dotRight = swathDir.perpClockwise().dot(rightNormal)
        val turnDir = if (dotRight > 0.0) TurnDirection.TURN_RIGHT else TurnDirection.TURN_LEFT

        return HeadlandTurnInfo(
            direction = turnDir,
            distanceToTurnMeters = distToHeadland,
            targetSwathIndex = activeSwathIndex + 1,
            isApproachingHeadland = true
        )
    }

    /**
     * Calculates minimum perpendicular distance from a point to any boundary perimeter edge.
     */
    fun distanceToBoundaryEdge(point: Vec2, boundary: List<Vec2>): Double {
        if (boundary.size < 2) return Double.MAX_VALUE
        var minDist = Double.MAX_VALUE
        val n = boundary.size
        for (i in 0 until n) {
            val a = boundary[i]
            val b = boundary[(i + 1) % n]
            val dist = distanceToSegment(point, a, b)
            if (dist < minDist) {
                minDist = dist
            }
        }
        return minDist
    }

    private fun distanceToSegment(p: Vec2, a: Vec2, b: Vec2): Double {
        val ab = b - a
        val lenSq = ab.x * ab.x + ab.y * ab.y
        if (lenSq < 1e-6) return p.distanceTo(a)
        val t = ((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / lenSq
        val clampedT = t.coerceIn(0.0, 1.0)
        val proj = a + (ab * clampedT)
        return p.distanceTo(proj)
    }

    /**
     * Exact 2D point-in-convex-polygon test for an applied swath quad segment.
     * Vertices ordered: leftStart -> rightStart -> rightEnd -> leftEnd.
     */
    fun isPointInSegmentQuad(pt: Vec2, seg: AppliedSwathSegment): Boolean {
        // Fast Axis-Aligned Bounding Box (AABB) rejection with 5cm tolerance
        val minX = min(min(seg.leftStart.x, seg.rightStart.x), min(seg.leftEnd.x, seg.rightEnd.x)) - 0.05
        val maxX = max(max(seg.leftStart.x, seg.rightStart.x), max(seg.leftEnd.x, seg.rightEnd.x)) + 0.05
        val minY = min(min(seg.leftStart.y, seg.rightStart.y), min(seg.leftEnd.y, seg.rightEnd.y)) - 0.05
        val maxY = max(max(seg.leftStart.y, seg.rightStart.y), max(seg.leftEnd.y, seg.rightEnd.y)) + 0.05
        if (pt.x < minX || pt.x > maxX || pt.y < minY || pt.y > maxY) return false

        // 2D cross-product point-in-convex-polygon orientation test
        val c1 = (seg.rightStart.x - seg.leftStart.x) * (pt.y - seg.leftStart.y) - (seg.rightStart.y - seg.leftStart.y) * (pt.x - seg.leftStart.x)
        val c2 = (seg.rightEnd.x - seg.rightStart.x) * (pt.y - seg.rightStart.y) - (seg.rightEnd.y - seg.rightStart.y) * (pt.x - seg.rightStart.x)
        val c3 = (seg.leftEnd.x - seg.rightEnd.x) * (pt.y - seg.rightEnd.y) - (seg.leftEnd.y - seg.rightEnd.y) * (pt.x - seg.rightEnd.x)
        val c4 = (seg.leftStart.x - seg.leftEnd.x) * (pt.y - seg.leftEnd.y) - (seg.leftStart.y - seg.leftEnd.y) * (pt.x - seg.leftEnd.x)

        val hasNeg = (c1 < -1e-5) || (c2 < -1e-5) || (c3 < -1e-5) || (c4 < -1e-5)
        val hasPos = (c1 > 1e-5) || (c2 > 1e-5) || (c3 > 1e-5) || (c4 > 1e-5)
        return !(hasNeg && hasPos)
    }

    /**
     * Checks if a point is already covered by applied swath passes.
     * Uses O(1) 2D spatial hash grid if provided, or fallback list iteration.
     * Excludes recent segments (~80 segments = ~12m travel) so active boom never triggers self-shutoff.
     */
    fun isPointCovered(
        point: Vec2,
        applied: List<AppliedSwathSegment>,
        spatialGrid: CoverageSpatialGrid? = null
    ): Boolean {
        if (spatialGrid != null) {
            return spatialGrid.isPointCovered(point)
        }
        if (applied.isEmpty()) return false
        val cutoffIndex = max(0, applied.size - 80)
        for (k in 0 until cutoffIndex) {
            val seg = applied[k]
            if (isPointInSegmentQuad(point, seg)) {
                return true
            }
        }
        return false
    }

    fun isPointCovered(point: Vec2, spatialGrid: CoverageSpatialGrid): Boolean {
        return spatialGrid.isPointCovered(point)
    }

    /**
     * Pure Pursuit algorithm targeting a sequential waypoint path (such as Dubins U-Turn arc or curved swath).
     */
    fun calculatePurePursuitWaypointsSteering(
        pivotPosition: Vec2,
        tractorHeadingDeg: Double,
        waypoints: List<Vec2>,
        speedKmh: Double,
        vehicle: VehicleConfig,
        lookaheadMeters: Double = 4.0
    ): Double {
        if (waypoints.isEmpty()) return 0.0
        var bestTarget = waypoints.last()
        for (pt in waypoints) {
            val dist = pivotPosition.distanceTo(pt)
            if (dist >= lookaheadMeters) {
                bestTarget = pt
                break
            }
        }
        val toTarget = bestTarget - pivotPosition
        val targetAngleRad = atan2(toTarget.x, toTarget.y)
        val targetAngleDeg = (Math.toDegrees(targetAngleRad) + 360.0) % 360.0
        val alphaDeg = GeoUtils.normalizeAngleDeg(targetAngleDeg - tractorHeadingDeg)
        val alphaRad = Math.toRadians(alphaDeg)
        val actualDist = toTarget.length().coerceAtLeast(1.0)
        val curvature = (2.0 * sin(alphaRad)) / actualDist
        val steerAngleRad = atan(curvature * vehicle.wheelbase)
        val steerAngleDeg = Math.toDegrees(steerAngleRad)
        return steerAngleDeg.coerceIn(-vehicle.maxSteerAngleDeg, vehicle.maxSteerAngleDeg)
    }
}

data class SwathResult(
    val activeSwathIndex: Int,
    val swathOrigin: Vec2,
    val swathDir: Vec2,
    val crossTrackErrorMeters: Double,
    val headingErrorDeg: Double,
    val targetHeadingDeg: Double,
    val projectedPoint: Vec2,
    val isReversePass: Boolean
)

data class SectionBoomPosition(
    val index: Int,
    val left: Vec2,
    val right: Vec2,
    val center: Vec2
)

data class ImplementStatus(
    val hitchPosition: Vec2,
    val sections: List<SectionBoomPosition>,
    val sectionStates: List<Boolean>
)

data class AppliedSwathSegment(
    val leftStart: Vec2,
    val rightStart: Vec2,
    val leftEnd: Vec2,
    val rightEnd: Vec2,
    val center: Vec2,
    val radius: Double
)

/**
 * 2D Spatial Hash Grid for O(1) field coverage indexing.
 * Maps 15.0-meter grid cells to lists of applied swath segments.
 * When querying isPointCovered(point), only checks segments stored in the active cell and its 8 neighboring cells.
 */
class CoverageSpatialGrid(val cellSizeMeters: Double = 15.0) {
    private data class IndexedSegment(val segment: AppliedSwathSegment, val id: Int)
    private val grid = HashMap<Pair<Int, Int>, MutableList<IndexedSegment>>()
    private var currentId = 0

    fun clear() {
        grid.clear()
        currentId = 0
    }

    fun addSegment(segment: AppliedSwathSegment) {
        val id = currentId++
        val indexed = IndexedSegment(segment, id)

        val minX = min(min(segment.leftStart.x, segment.rightStart.x), min(segment.leftEnd.x, segment.rightEnd.x))
        val maxX = max(max(segment.leftStart.x, segment.rightStart.x), max(segment.leftEnd.x, segment.rightEnd.x))
        val minY = min(min(segment.leftStart.y, segment.rightStart.y), min(segment.leftEnd.y, segment.rightEnd.y))
        val maxY = max(max(segment.leftStart.y, segment.rightStart.y), max(segment.leftEnd.y, segment.rightEnd.y))

        val minGx = floor(minX / cellSizeMeters).toInt()
        val maxGx = floor(maxX / cellSizeMeters).toInt()
        val minGy = floor(minY / cellSizeMeters).toInt()
        val maxGy = floor(maxY / cellSizeMeters).toInt()

        for (gx in minGx..maxGx) {
            for (gy in minGy..maxGy) {
                grid.getOrPut(Pair(gx, gy)) { mutableListOf() }.add(indexed)
            }
        }
    }

    fun addAll(segments: List<AppliedSwathSegment>) {
        for (seg in segments) {
            addSegment(seg)
        }
    }

    fun isPointCovered(point: Vec2): Boolean {
        if (currentId == 0) return false
        val gx = floor(point.x / cellSizeMeters).toInt()
        val gy = floor(point.y / cellSizeMeters).toInt()

        // Exclude recent 80 segments (~12m of travel) to prevent self-overlap shutoff under active boom
        val cutoffId = currentId - 80

        for (dx in -1..1) {
            for (dy in -1..1) {
                val cellList = grid[Pair(gx + dx, gy + dy)] ?: continue
                for (indexed in cellList) {
                    if (indexed.id < cutoffId) {
                        if (GuidanceAlgorithms.isPointInSegmentQuad(point, indexed.segment)) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    val size: Int get() = currentId
}


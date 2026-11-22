package com.example.agopengps.navigation

import kotlin.math.*

data class ABLine(
    val aLocal: Vec2,
    val bLocal: Vec2,
    val aGeo: GeoPoint,
    val bGeo: GeoPoint,
    val headingDeg: Double,
    val dirVector: Vec2,
    val normalVector: Vec2,
    val name: String = "AB Line"
) {
    val directionVector: Vec2 get() = dirVector

    companion object {
        fun fromPoints(aLocal: Vec2, bLocal: Vec2, aGeo: GeoPoint, bGeo: GeoPoint, name: String = "AB Line"): ABLine {
            val dir = (bLocal - aLocal).normalized()
            val headingRad = atan2(dir.x, dir.y)
            val headingDeg = (Math.toDegrees(headingRad) + 360.0) % 360.0
            val normal = dir.perpClockwise()
            return ABLine(aLocal, bLocal, aGeo, bGeo, headingDeg, dir, normal, name)
        }

        fun fromAPlus(aLocal: Vec2, headingDeg: Double, aGeo: GeoPoint, name: String = "A+ Heading Line"): ABLine {
            val headingRad = Math.toRadians(headingDeg)
            val dir = Vec2(sin(headingRad), cos(headingRad))
            val bLocal = aLocal + (dir * 1000.0)
            val bGeo = GeoUtils.localMetersToGeo(dir * 1000.0, aGeo)
            val normal = dir.perpClockwise()
            return ABLine(aLocal, bLocal, aGeo, bGeo, headingDeg, dir, normal, name)
        }

        fun fromPointAndHeading(aLocal: Vec2, aGeo: GeoPoint, headingDeg: Double, name: String = "A+ Heading Line"): ABLine {
            return fromAPlus(aLocal, headingDeg, aGeo, name)
        }
    }
}

data class CurveLine(
    val name: String,
    val points: List<Vec2>
)

data class AppliedSwathSegment(
    val leftStart: Vec2,
    val rightStart: Vec2,
    val leftEnd: Vec2,
    val rightEnd: Vec2,
    val center: Vec2,
    val radius: Double
)

enum class SectionOverride {
    AUTO,
    FORCED_ON,
    FORCED_OFF
}

data class SwathResult(
    val activeSwathIndex: Int = 0,
    val swathOrigin: Vec2 = Vec2(),
    val swathDir: Vec2 = Vec2(0.0, 1.0),
    val crossTrackErrorMeters: Double = 0.0,
    val headingErrorDeg: Double = 0.0,
    val targetHeadingDeg: Double = 0.0,
    val projectedPoint: Vec2 = Vec2(),
    val isReversePass: Boolean = false
)

data class ImplementStatus(
    val isMasterActive: Boolean,
    val sectionStates: List<Boolean>,
    val sections: List<SectionSpan> = emptyList()
)

data class SectionSpan(
    val left: Vec2,
    val right: Vec2
)

class CoverageSpatialGrid(val cellSizeMeters: Double = 15.0) {
    private val segments = mutableListOf<AppliedSwathSegment>()

    fun addSegment(seg: AppliedSwathSegment) {
        segments.add(seg)
    }

    fun clear() {
        segments.clear()
    }

    fun getAll(): List<AppliedSwathSegment> = segments
}

object GuidanceAlgorithms {

    fun normalizeAngleDeg(angle: Double): Double {
        var a = angle % 360.0
        if (a > 180.0) a -= 360.0
        if (a < -180.0) a += 360.0
        return a
    }

    fun compensateAntennaPosition(
        rawAntennaPos: Vec2,
        headingDeg: Double,
        rollDeg: Double,
        antennaHeight: Double,
        antennaPivotOffset: Double
    ): Vec2 {
        val headingRad = Math.toRadians(headingDeg)
        val rollRad = Math.toRadians(rollDeg)
        val lateralOffset = antennaHeight * sin(rollRad)
        val rightVec = Vec2(cos(headingRad), -sin(headingRad))
        val fwdVec = Vec2(sin(headingRad), cos(headingRad))
        return rawAntennaPos - (rightVec * lateralOffset) - (fwdVec * antennaPivotOffset)
    }

    fun compensateAntennaPosition(
        antennaLocal: Vec2,
        headingDeg: Double,
        rollDeg: Double,
        vehicle: VehicleConfig,
        rollZeroOffset: Double = 0.0
    ): Vec2 {
        return compensateAntennaPosition(
            rawAntennaPos = antennaLocal,
            headingDeg = headingDeg,
            rollDeg = rollDeg - rollZeroOffset,
            antennaHeight = vehicle.antennaHeight,
            antennaPivotOffset = vehicle.antennaPivotOffset
        )
    }

    fun compensateTabletPosition(
        tabletPos: Vec2,
        headingDeg: Double,
        rollDeg: Double,
        tabletHeight: Double,
        tabletForwardOffset: Double,
        tabletRightOffset: Double
    ): Vec2 {
        val headingRad = Math.toRadians(headingDeg)
        val rollRad = Math.toRadians(rollDeg)
        val lateralRollOffset = tabletHeight * sin(rollRad)
        val rightVec = Vec2(cos(headingRad), -sin(headingRad))
        val fwdVec = Vec2(sin(headingRad), cos(headingRad))
        return tabletPos - (rightVec * (tabletRightOffset + lateralRollOffset)) - (fwdVec * tabletForwardOffset)
    }

    fun compensateTabletPosition(
        tabletLocal: Vec2,
        headingDeg: Double,
        rollDeg: Double,
        vehicle: VehicleConfig,
        rollZeroOffset: Double = 0.0
    ): Vec2 {
        return compensateTabletPosition(
            tabletPos = tabletLocal,
            headingDeg = headingDeg,
            rollDeg = rollDeg - rollZeroOffset,
            tabletHeight = vehicle.tabletHeightAboveAxle,
            tabletForwardOffset = vehicle.tabletForwardOffset,
            tabletRightOffset = vehicle.tabletRightOffset
        )
    }

    fun calculateCentrifugalCompensatedRoll(
        rawRollDeg: Double,
        speedKmh: Double,
        steerAngleDeg: Double,
        wheelbase: Double
    ): Double {
        val speedMps = speedKmh / 3.6
        val steerRad = Math.toRadians(steerAngleDeg)
        val yawRate = if (wheelbase > 0.1) (speedMps * tan(steerRad)) / wheelbase else 0.0
        val lateralAccel = speedMps * yawRate
        val dynamicRollCompDeg = Math.toDegrees(atan2(lateralAccel, 9.81)) * 0.15
        return rawRollDeg - dynamicRollCompDeg
    }

    fun calculateCentrifugalCompensatedRoll(
        rawAccelRollDeg: Double,
        speedKmh: Double,
        yawRateRadPerSec: Double
    ): Double {
        val speedMps = speedKmh / 3.6
        val lateralAccel = speedMps * yawRateRadPerSec
        val dynamicRollCompDeg = Math.toDegrees(atan2(lateralAccel, 9.81)) * 0.15
        return rawAccelRollDeg - dynamicRollCompDeg
    }

    fun calculateSwathTracking(
        pivotPosition: Vec2,
        tractorHeadingDeg: Double,
        abLine: ABLine,
        swathWidth: Double,
        forcedSwathIndex: Int? = null
    ): NavigationOutput {
        val dir = abLine.dirVector
        val normal = abLine.normalVector
        val toPivot = pivotPosition - abLine.aLocal
        val distAlong = toPivot.dot(dir)
        val distAcross = toPivot.dot(normal)

        val activeIndex = forcedSwathIndex ?: round(distAcross / swathWidth).toInt()
        val swathCenterAcross = activeIndex * swathWidth
        val crossTrack = distAcross - swathCenterAcross

        var headingError = normalizeAngleDeg(tractorHeadingDeg - abLine.headingDeg)
        val isReverse = abs(headingError) > 90.0
        val swathDir = if (isReverse) -dir else dir
        val effectiveCrossTrack = if (isReverse) -crossTrack else crossTrack
        if (isReverse) {
            headingError = normalizeAngleDeg(headingError - 180.0)
        }

        val swathOrigin = abLine.aLocal + (normal * swathCenterAcross)
        val projectedPoint = swathOrigin + (dir * distAlong)

        return NavigationOutput(
            targetSteerAngleDeg = 0.0,
            crossTrackErrorMeters = effectiveCrossTrack,
            crossTrackErrorInches = effectiveCrossTrack * 39.3701,
            headingErrorDeg = headingError,
            activeSwathIndex = activeIndex,
            swathOrigin = swathOrigin,
            swathDirection = swathDir,
            projectedPoint = projectedPoint,
            isReversePass = isReverse,
            algorithmUsed = SteeringAlgorithm.STANLEY
        )
    }

    fun calculateStanleySteering(
        crossTrackMeters: Double,
        headingErrorDeg: Double,
        speedKmh: Double,
        stanleyGain: Double,
        integralGain: Double,
        deadbandCm: Double = 2.0,
        maxSteerDeg: Double = 40.0
    ): Double {
        val speedMps = max(0.5, speedKmh / 3.6)
        val effectiveCrossTrack = if (abs(crossTrackMeters * 100.0) < deadbandCm) 0.0 else crossTrackMeters
        val crossTrackSteerRad = atan2(-stanleyGain * effectiveCrossTrack, speedMps)
        val headingSteerRad = Math.toRadians(-headingErrorDeg)
        val totalSteerDeg = Math.toDegrees(headingSteerRad + crossTrackSteerRad)
        return totalSteerDeg.coerceIn(-maxSteerDeg, maxSteerDeg)
    }

    fun calculateStanleySteering(
        crossTrackErrorMeters: Double,
        headingErrorDeg: Double,
        speedKmh: Double,
        settings: GuidanceSettings,
        vehicle: VehicleConfig
    ): Double {
        val aggressionScale = (settings.steeringAggressionPercent / 100.0).coerceIn(0.4, 2.5)
        return calculateStanleySteering(
            crossTrackMeters = crossTrackErrorMeters,
            headingErrorDeg = headingErrorDeg,
            speedKmh = speedKmh,
            stanleyGain = settings.stanleyGain * aggressionScale,
            integralGain = settings.stanleyIntegralGain * aggressionScale,
            deadbandCm = (settings.deadbandCm / aggressionScale).coerceAtLeast(0.5),
            maxSteerDeg = vehicle.maxSteerAngleDeg
        )
    }

    fun calculateCurveTracking(
        pivotPosition: Vec2,
        headingDeg: Double,
        curvePoints: List<Vec2>,
        swathWidth: Double,
        activeSwathIndex: Int = 0
    ): NavigationOutput {
        if (curvePoints.size < 2) {
            return NavigationOutput(
                targetSteerAngleDeg = 0.0,
                crossTrackErrorMeters = 0.0,
                crossTrackErrorInches = 0.0,
                headingErrorDeg = 0.0,
                activeSwathIndex = 0,
                swathOrigin = pivotPosition,
                swathDirection = Vec2(0.0, 1.0),
                projectedPoint = pivotPosition,
                isReversePass = false,
                algorithmUsed = SteeringAlgorithm.STANLEY
            )
        }

        var closestDistSq = Double.MAX_VALUE
        var closestIdx = 0
        for (i in curvePoints.indices) {
            val dSq = (curvePoints[i] - pivotPosition).lengthSquared()
            if (dSq < closestDistSq) {
                closestDistSq = dSq
                closestIdx = i
            }
        }

        val nextIdx = min(curvePoints.size - 1, closestIdx + 1)
        val p1 = curvePoints[closestIdx]
        val p2 = curvePoints[nextIdx]
        val segDir = (p2 - p1).normalized()
        val segNormal = segDir.perpClockwise()
        val toPivot = pivotPosition - p1
        val crossTrack = toPivot.dot(segNormal) - (activeSwathIndex * swathWidth)
        val segHeading = (Math.toDegrees(atan2(segDir.x, segDir.y)) + 360.0) % 360.0
        val headingError = normalizeAngleDeg(headingDeg - segHeading)

        return NavigationOutput(
            targetSteerAngleDeg = 0.0,
            crossTrackErrorMeters = crossTrack,
            crossTrackErrorInches = crossTrack * 39.3701,
            headingErrorDeg = headingError,
            activeSwathIndex = activeSwathIndex,
            swathOrigin = p1,
            swathDirection = segDir,
            projectedPoint = p1 + (segDir * toPivot.dot(segDir)),
            isReversePass = false,
            algorithmUsed = SteeringAlgorithm.STANLEY
        )
    }

    fun calculateCurveTracking(
        pivotPosition: Vec2,
        tractorHeadingDeg: Double,
        curveLine: CurveLine,
        swathWidth: Double,
        activeSwathIndex: Int = 0
    ): NavigationOutput {
        return calculateCurveTracking(
            pivotPosition = pivotPosition,
            headingDeg = tractorHeadingDeg,
            curvePoints = curveLine.points,
            swathWidth = swathWidth,
            activeSwathIndex = activeSwathIndex
        )
    }

    fun calculatePathTracking(
        pivotPosition: Vec2,
        headingDeg: Double,
        pathPoints: List<Vec2>,
        lookaheadGain: Double = 1.0,
        speedKmh: Double = 0.0
    ): NavigationOutput {
        return calculateCurveTracking(pivotPosition, headingDeg, pathPoints, 0.0, 0)
    }

    fun calculatePurePursuitWaypointsSteering(
        pivotPosition: Vec2,
        headingDeg: Double,
        waypoints: List<Vec2>,
        lookaheadDistance: Double,
        wheelbase: Double,
        maxSteerDeg: Double = 40.0
    ): Double {
        if (waypoints.isEmpty()) return 0.0
        val headingRad = Math.toRadians(headingDeg)
        val fwd = Vec2(sin(headingRad), cos(headingRad))
        val right = Vec2(cos(headingRad), -sin(headingRad))

        var targetPt = waypoints.last()
        for (pt in waypoints) {
            if ((pt - pivotPosition).length() >= lookaheadDistance) {
                targetPt = pt
                break
            }
        }

        val rel = targetPt - pivotPosition
        val localX = rel.dot(right)
        val localY = rel.dot(fwd)
        val distSq = rel.lengthSquared()

        if (distSq < 0.1 || localY <= 0.0) return 0.0
        val curvature = (2.0 * localX) / distSq
        val steerRad = atan(curvature * wheelbase)
        return Math.toDegrees(steerRad).coerceIn(-maxSteerDeg, maxSteerDeg)
    }

    fun calculateSpeedAdaptiveLookahead(
        speedKmh: Double,
        baseLookaheadSec: Double,
        minLookaheadMeters: Double,
        maxLookaheadMeters: Double
    ): Double {
        val speedMps = speedKmh / 3.6
        return (speedMps * baseLookaheadSec).coerceIn(minLookaheadMeters, maxLookaheadMeters)
    }

    fun calculateHeadlandTurnGuidance(
        pivotPosition: Vec2,
        headingDeg: Double,
        headlandBoundary: List<Vec2>,
        abLine: ABLine?,
        isAutoUTurnEnabled: Boolean
    ): HeadlandTurnInfo {
        if (headlandBoundary.size < 3) return HeadlandTurnInfo()
        val distToEdge = distanceToBoundaryEdge(pivotPosition, headlandBoundary)
        val isInside = distToEdge < 0.0
        val distToTurn = max(0.0, distToEdge)
        val triggerUTurn = isAutoUTurnEnabled && distToEdge <= 3.0

        return HeadlandTurnInfo(
            distanceToTurnMeters = distToTurn,
            isApproachingHeadland = distToEdge <= 15.0
        )
    }

    fun calculateHeadlandTurnGuidance(
        pivotPosition: Vec2,
        tractorHeadingDeg: Double,
        headlandBoundary: List<Vec2>,
        abLine: ABLine?,
        isAutoUTurnEnabled: Boolean,
        activeSwathIndex: Int = 0,
        swathDir: Vec2 = Vec2(0.0, 1.0)
    ): HeadlandTurnInfo {
        return calculateHeadlandTurnGuidance(
            pivotPosition = pivotPosition,
            headingDeg = tractorHeadingDeg,
            headlandBoundary = headlandBoundary,
            abLine = abLine,
            isAutoUTurnEnabled = isAutoUTurnEnabled
        )
    }

    fun generateUTurnPath(
        startPivot: Vec2,
        headingDeg: Double,
        turnToRight: Boolean,
        turnRadiusMeters: Double,
        swathWidthMeters: Double
    ): List<Vec2> {
        val headingRad = Math.toRadians(headingDeg)
        val fwd = Vec2(sin(headingRad), cos(headingRad))
        val right = Vec2(cos(headingRad), -sin(headingRad))
        val turnSign = if (turnToRight) 1.0 else -1.0

        val path = mutableListOf<Vec2>()
        path.add(startPivot)
        val arcCenter = startPivot + (right * (turnRadiusMeters * turnSign))
        val numSteps = 16
        for (i in 1..numSteps) {
            val angleStep = (PI * i) / numSteps
            val currentAngle = headingRad - (turnSign * PI / 2.0) + (turnSign * angleStep)
            val pt = arcCenter + Vec2(cos(currentAngle) * turnRadiusMeters * turnSign, sin(currentAngle) * turnRadiusMeters)
            path.add(pt)
        }
        return path
    }

    fun generateUTurnPath(
        currentSwathEnd: Vec2,
        currentHeadingDeg: Double,
        nextSwathStart: Vec2,
        turnRadiusMeters: Double = 6.0,
        turnToRight: Boolean = true
    ): List<Vec2> {
        return generateUTurnPath(
            startPivot = currentSwathEnd,
            headingDeg = currentHeadingDeg,
            turnToRight = turnToRight,
            turnRadiusMeters = turnRadiusMeters,
            swathWidthMeters = 6.0
        )
    }

    fun checkObstacleProximity(
        pivotPosition: Vec2,
        obstacles: List<FieldObstacle>,
        warningThresholdMeters: Double = 12.0
    ): FieldObstacle? {
        return obstacles.firstOrNull { obs ->
            (obs.localPos - pivotPosition).length() <= (obs.radiusMeters + warningThresholdMeters)
        }
    }

    fun distanceToBoundaryEdge(point: Vec2, boundary: List<Vec2>): Double {
        if (boundary.size < 3) return Double.MAX_VALUE
        var minDist = Double.MAX_VALUE
        for (i in boundary.indices) {
            val p1 = boundary[i]
            val p2 = boundary[(i + 1) % boundary.size]
            val seg = p2 - p1
            val lenSq = seg.lengthSquared()
            val t = if (lenSq > 0.0001) ((point - p1).dot(seg) / lenSq).coerceIn(0.0, 1.0) else 0.0
            val proj = p1 + (seg * t)
            val dist = (point - proj).length()
            if (dist < minDist) minDist = dist
        }
        return minDist
    }

    fun calculatePolygonAreaM2(points: List<Vec2>): Double {
        if (points.size < 3) return 0.0
        var area = 0.0
        for (i in points.indices) {
            val j = (i + 1) % points.size
            area += points[i].x * points[j].y
            area -= points[j].x * points[i].y
        }
        return abs(area) / 2.0
    }

    fun generateHeadlandPolygon(boundary: List<Vec2>, offsetMeters: Double): List<Vec2> {
        if (boundary.size < 3) return emptyList()
        val cx = boundary.sumOf { it.x } / boundary.size
        val cy = boundary.sumOf { it.y } / boundary.size
        val center = Vec2(cx, cy)
        return boundary.map { pt ->
            val dir = center - pt
            val len = dir.length()
            if (len > offsetMeters) pt + (dir.normalized() * offsetMeters) else pt
        }
    }

    fun calculateImplementSections(
        pivotPosition: Vec2,
        tractorHeadingDeg: Double,
        vehicle: VehicleConfig,
        implement: ImplementConfig,
        fieldBoundary: List<Vec2>?,
        appliedPolygons: List<AppliedSwathSegment>,
        sectionOverrides: List<SectionOverride>,
        spatialGrid: CoverageSpatialGrid
    ): ImplementStatus {
        val headingRad = Math.toRadians(tractorHeadingDeg)
        val fwd = Vec2(sin(headingRad), cos(headingRad))
        val right = Vec2(cos(headingRad), -sin(headingRad))
        val toolbarDist = vehicle.hitchLength - implement.offsetBehindTractor
        val toolbarCenter = pivotPosition + (fwd * toolbarDist)

        val numSections = implement.numSections.coerceIn(1, 48)
        val sectionWidth = implement.toolWidth / numSections
        val halfTotal = implement.toolWidth * 0.5

        val sections = mutableListOf<SectionSpan>()
        val states = mutableListOf<Boolean>()

        for (i in 0 until numSections) {
            val leftOffset = -halfTotal + (i * sectionWidth)
            val rightOffset = leftOffset + sectionWidth
            val leftPt = toolbarCenter + (right * leftOffset)
            val rightPt = toolbarCenter + (right * rightOffset)
            sections.add(SectionSpan(leftPt, rightPt))

            val override = sectionOverrides.getOrElse(i) { SectionOverride.AUTO }
            val state = when (override) {
                SectionOverride.FORCED_ON -> true
                SectionOverride.FORCED_OFF -> false
                SectionOverride.AUTO -> implement.isMasterActive
            }
            states.add(state)
        }

        return ImplementStatus(
            isMasterActive = implement.isMasterActive,
            sectionStates = states,
            sections = sections
        )
    }
}

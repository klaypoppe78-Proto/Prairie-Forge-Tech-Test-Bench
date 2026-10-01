package com.example.agopengps.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agopengps.CameraViewMode
import com.example.agopengps.GuidanceMode
import com.example.agopengps.map.*
import com.example.agopengps.navigation.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.*

@Composable
fun GuidanceMapCanvas(
    vehicleState: VehicleState,
    vehicleConfig: VehicleConfig,
    implementConfig: ImplementConfig,
    currentABLine: ABLine?,
    currentCurveLine: CurveLine?,
    guidanceMode: GuidanceMode,
    cameraViewMode: CameraViewMode,
    fieldBoundary: List<Vec2>,
    headlandBoundary: List<Vec2>,
    uTurnPath: List<Vec2>,
    recordingCurvePoints: List<Vec2>,
    isRecordingBoundary: Boolean,
    appliedSegments: List<AppliedSwathSegment>,
    isNightMode: Boolean,
    themeMode: DisplayThemeMode = DisplayThemeMode.STANDARD_AG,
    obstacles: List<FieldObstacle> = emptyList(),
    headlandTurnInfo: HeadlandTurnInfo = HeadlandTurnInfo(),
    activeObstacleAlert: FieldObstacle? = null,
    showSatelliteOverlay: Boolean = false,
    satelliteMapProvider: MapTileProvider = MapTileProvider.GOOGLE_SATELLITE,
    rtkBaseConfig: RtkBaseStationConfig = RtkBaseStationConfig(),
    slidingHitchConfig: SlidingHitchConfig = SlidingHitchConfig(),
    slidingHitchState: SlidingHitchState = SlidingHitchState(),
    zoomFactor: Float,
    onZoomChange: (Float) -> Unit = {},
    terrainSquare: TerrainSquare? = null,
    terrainAttitude: TerrainAttitude = TerrainAttitude(),
    isTerrainCompensationEnabled: Boolean = false,
    showTopographyMap: Boolean = false,
    show3DTerrainMesh: Boolean = false,
    navigationOutput: NavigationOutput? = null,
    onToggleCameraMode: () -> Unit,
    onCycleBasemap: () -> Unit = {},
    onOpenRtkBaseDialog: () -> Unit = {},
    onDropHazardPin: () -> Unit = {},
    onOpenObstacleDialog: () -> Unit = {},
    onToggleTerrainCompensation: () -> Unit = {},
    onToggleTopographyMap: () -> Unit = {},
    onToggle3DTerrainMesh: () -> Unit = {},
    onResetToBasicGps: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Refined Modern Theme palette: Sleek Obsidian Cab, Modern Slate Day, or Sunlight High-Contrast
    val fieldBgColor = when (themeMode) {
        DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST -> Color(0xFFF8FAFC)
        DisplayThemeMode.CAB_OLED_NIGHT -> Color(0xFF030712)
        DisplayThemeMode.STANDARD_AG -> if (isNightMode) Color(0xFF090E17) else Color(0xFFF1F5F9)
    }
    val gridLineColor = when (themeMode) {
        DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST -> Color(0xFFCBD5E1)
        DisplayThemeMode.CAB_OLED_NIGHT -> Color(0x2294A3B8)
        DisplayThemeMode.STANDARD_AG -> if (isNightMode) Color(0x2894A3B8) else Color(0x3394A3B8)
    }
    val boundaryColor = when (themeMode) {
        DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST -> Color(0xFFE11D48)
        DisplayThemeMode.CAB_OLED_NIGHT -> Color(0xFFFBBF24)
        DisplayThemeMode.STANDARD_AG -> if (isNightMode) Color(0xFFFBBF24) else Color(0xFFE11D48)
    }
    val headlandColor = when (themeMode) {
        DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST -> Color(0xFFF97316)
        DisplayThemeMode.CAB_OLED_NIGHT -> Color(0xFFF97316)
        DisplayThemeMode.STANDARD_AG -> if (isNightMode) Color(0xFFFB923C) else Color(0xFFEA580C)
    }
    val activeSwathColor = when (themeMode) {
        DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST -> Color(0xFF059669)
        DisplayThemeMode.CAB_OLED_NIGHT -> Color(0xFF00F59B)
        DisplayThemeMode.STANDARD_AG -> if (isNightMode) Color(0xFF00F59B) else Color(0xFF059669)
    }
    val parallelSwathColor = when (themeMode) {
        DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST -> Color(0xFF0284C7)
        DisplayThemeMode.CAB_OLED_NIGHT -> Color(0x4438BDF8)
        DisplayThemeMode.STANDARD_AG -> if (isNightMode) Color(0x5538BDF8) else Color(0x770284C7)
    }
    val baseCoverageColor = when (themeMode) {
        DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST -> Color(0x9910B981)
        DisplayThemeMode.CAB_OLED_NIGHT -> Color(0x77059669)
        DisplayThemeMode.STANDARD_AG -> if (isNightMode) Color(0x8810B981) else Color(0x99059669)
    }
    val overlapHighlightColor = Color(0x99F59E0B) // Luminous warm amber overlap highlight

    var panOffset by remember { mutableStateOf(Offset.Zero) }
    val tileRepo = remember { SatelliteTileRepository() }
    var tileRefreshTrigger by remember { mutableStateOf(0) }

    // Reusable Path instances to prevent frame-rate jitter and GC allocation spikes at 60-120fps
    val reusableSegPath = remember { Path() }
    val reusableBoundaryPath = remember { Path() }
    val reusableHeadlandPath = remember { Path() }
    val reusableTowerPath = remember { Path() }
    val reusableArrowPath = remember { Path() }

    // Proactively fetch tiles around current position when basemap overlay is on
    // Key ONLY on discrete center tile index to avoid continuous network thrashing
    val discreteCenterTile = remember(
        showSatelliteOverlay,
        satelliteMapProvider,
        vehicleState.geoPosition.latitude,
        vehicleState.geoPosition.longitude,
        (zoomFactor * 2f).toInt()
    ) {
        if (!showSatelliteOverlay || vehicleState.geoPosition.latitude == 0.0) {
            null
        } else {
            val pixelsPerMeter = 4.8f * zoomFactor
            val z = tileRepo.getZoomForPixelsPerMeter(pixelsPerMeter, vehicleState.geoPosition.latitude, satelliteMapProvider)
            tileRepo.geoToTile(vehicleState.geoPosition.latitude, vehicleState.geoPosition.longitude, z)
        }
    }

    LaunchedEffect(showSatelliteOverlay, satelliteMapProvider, discreteCenterTile) {
        tileRepo.currentProvider = satelliteMapProvider
        if (showSatelliteOverlay && discreteCenterTile != null) {
            val centerTile = discreteCenterTile
            val z = centerTile.z
            // Fetch 5x5 surrounding tiles asynchronously for seamless coverage ahead of travel
            coroutineScope {
                for (dx in -2..2) {
                    for (dy in -2..2) {
                        launch(Dispatchers.IO) {
                            val tileCoord = TileCoord(z, centerTile.x + dx, centerTile.y + dy)
                            if (tileRepo.getCachedTile(tileCoord, satelliteMapProvider) == null) {
                                val bitmap = tileRepo.fetchTile(tileCoord, satelliteMapProvider)
                                if (bitmap != null) {
                                    tileRefreshTrigger++
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val currentOnZoomChange by rememberUpdatedState(onZoomChange)

    LaunchedEffect(cameraViewMode) {
        if (cameraViewMode != CameraViewMode.FREE_PAN) {
            panOffset = Offset.Zero
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(fieldBgColor)
            .testTag("guidance_canvas")
            .pointerInput(cameraViewMode) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (zoom != 1.0f && abs(zoom - 1.0f) > 0.0005f) {
                        currentOnZoomChange(zoom)
                    }
                    if (cameraViewMode == CameraViewMode.FREE_PAN || cameraViewMode == CameraViewMode.NORTH_UP) {
                        panOffset += pan
                    }
                }
            }
    ) {
        val currentOriginGeo = remember(vehicleState.geoPosition) {
            if (vehicleState.geoPosition.latitude != 0.0 || vehicleState.geoPosition.longitude != 0.0) {
                // Anchor map origin directly to the active vehicle's real GNSS coordinate datum
                GeoUtils.localMetersToGeo(Vec2(-vehicleState.localPivotPosition.x, -vehicleState.localPivotPosition.y), vehicleState.geoPosition)
            } else {
                GeoPoint(44.9141, -95.7153, 310.0)
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val _trigger = tileRefreshTrigger // Observe recomposition on new tile load
            val canvasWidth = size.width
            val canvasHeight = size.height

            val tractorPivot = vehicleState.localPivotPosition
            val headingDeg = vehicleState.headingDeg
            val headingRad = Math.toRadians(headingDeg)
            val sinH = sin(headingRad)
            val cosH = cos(headingRad)

            val pixelsPerMeter = 4.8f * zoomFactor
            val is3D = cameraViewMode == CameraViewMode.CAB_3D

            if (is3D) {
                // --- ELEVATED HIGH-ANGLE TOP-DOWN 3D PERSPECTIVE (AG-CAB CHASE VIEW) ---
                // Higher top-down pitch (~60° look-down) stabilizes tractor model, swath lines, and avoids pinch distortion
                val horizonY = canvasHeight * 0.04f
                val tractorScreenPos = Offset(canvasWidth / 2f, canvasHeight * 0.76f)
                val fwdVec = Vec2(sinH, cosH)
                val rightVec = Vec2(cosH, -sinH)

                // Sky & Atmospheric Horizon Rendering
                val skyColors = if (isNightMode) {
                    listOf(Color(0xFF030D14), Color(0xFF0C1F28), Color(0xFF16323B))
                } else {
                    listOf(Color(0xFF81D4FA), Color(0xFFB3E5FC), Color(0xFFE1F5FE))
                }
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = skyColors,
                        startY = 0f,
                        endY = horizonY
                    ),
                    topLeft = Offset.Zero,
                    size = Size(canvasWidth, horizonY)
                )

                // Horizon guidance glow line
                val horizonGlowColor = if (isNightMode) Color(0xFF00E5FF).copy(alpha = 0.55f) else Color(0xFF0277BD).copy(alpha = 0.65f)
                drawLine(
                    color = horizonGlowColor,
                    start = Offset(0f, horizonY),
                    end = Offset(canvasWidth, horizonY),
                    strokeWidth = 2.0f
                )

                val tractorElevM = if (show3DTerrainMesh && terrainSquare != null) {
                    terrainSquare.getElevationAt(tractorPivot)
                } else 0.0

                // Precise 3D Projection for World Points with Topographical DEM Elevation
                fun project3D(worldPoint: Vec2, customElevMeters: Double? = null): Offset? {
                    val dx = worldPoint.x - tractorPivot.x
                    val dy = worldPoint.y - tractorPivot.y
                    // Forward & Lateral relative to tractor track
                    val forwardMeters = dx * sinH + dy * cosH
                    val lateralMeters = dx * cosH - dy * sinH

                    val eyeOffsetBehind = 16.0
                    val eyeDist = forwardMeters + eyeOffsetBehind
                    if (eyeDist <= 1.0) return null // Behind camera near plane

                    // Perspective scaling with higher camera elevation & flatter depth
                    val focalDist = 42.0
                    val scale = (focalDist / (eyeDist + focalDist * 0.65)).toFloat()

                    val dz = if (show3DTerrainMesh && terrainSquare != null) {
                        (customElevMeters ?: terrainSquare.getElevationAt(worldPoint)) - tractorElevM
                    } else 0.0
                    val vertOffset = (dz * pixelsPerMeter * scale * 1.5).toFloat()

                    val sx = tractorScreenPos.x + (lateralMeters * pixelsPerMeter * scale).toFloat()
                    val sy = (tractorScreenPos.y - (forwardMeters * pixelsPerMeter * scale * 0.88f).toFloat() - vertOffset).coerceAtLeast(horizonY)

                    return if (sy < horizonY - 4f) null else Offset(sx, sy)
                }

                // Robust 3D Line Drawing with Near-Plane and Horizon Segment Clipping
                val targetDrop = (tractorScreenPos.y - horizonY).toDouble()
                val C = pixelsPerMeter * 42.0 * 0.88
                val horizonFwd = if (C > targetDrop + 1.0) {
                    (43.3 * targetDrop / (C - targetDrop)).coerceIn(350.0, 3500.0)
                } else {
                    2800.0
                }

                fun projectLocal(fwd: Double, lat: Double, elevOffsetM: Double = 0.0): Offset? {
                    val eyeDist = fwd + 16.0
                    if (eyeDist <= 1.0) return null
                    val scale = (42.0 / (eyeDist + 27.3)).toFloat()
                    val vertOffset = (elevOffsetM * pixelsPerMeter * scale * 1.5).toFloat()
                    val sx = tractorScreenPos.x + (lat * pixelsPerMeter * scale).toFloat()
                    val sy = (tractorScreenPos.y - (fwd * pixelsPerMeter * scale * 0.88f).toFloat() - vertOffset).coerceAtLeast(horizonY)
                    return Offset(sx, sy)
                }

                fun draw3DLine(
                    p1: Vec2,
                    p2: Vec2,
                    color: Color,
                    strokeWidth: Float,
                    pathEffect: PathEffect? = null,
                    cap: StrokeCap = StrokeCap.Butt,
                    elev1: Double? = null,
                    elev2: Double? = null
                ) {
                    val dx1 = p1.x - tractorPivot.x
                    val dy1 = p1.y - tractorPivot.y
                    val fwd1 = dx1 * sinH + dy1 * cosH
                    val lat1 = dx1 * cosH - dy1 * sinH

                    val dx2 = p2.x - tractorPivot.x
                    val dy2 = p2.y - tractorPivot.y
                    val fwd2 = dx2 * sinH + dy2 * cosH
                    val lat2 = dx2 * cosH - dy2 * sinH

                    val dz1 = if (show3DTerrainMesh && terrainSquare != null) {
                        (elev1 ?: terrainSquare.getElevationAt(p1)) - tractorElevM
                    } else 0.0
                    val dz2 = if (show3DTerrainMesh && terrainSquare != null) {
                        (elev2 ?: terrainSquare.getElevationAt(p2)) - tractorElevM
                    } else 0.0

                    val nearLimit = -12.0 // Behind implement toolbar
                    if (fwd1 < nearLimit && fwd2 < nearLimit) return // Completely behind camera
                    if (fwd1 > horizonFwd && fwd2 > horizonFwd) return // Completely past horizon

                    var startFwd = fwd1
                    var startLat = lat1
                    var startDz = dz1
                    var endFwd = fwd2
                    var endLat = lat2
                    var endDz = dz2

                    // Clip against near plane
                    if (startFwd < nearLimit) {
                        val t = ((nearLimit - startFwd) / (endFwd - startFwd)).coerceIn(0.0, 1.0)
                        startLat += (endLat - startLat) * t
                        startDz += (endDz - startDz) * t
                        startFwd = nearLimit
                    } else if (startFwd > horizonFwd) {
                        val t = ((horizonFwd - startFwd) / (endFwd - startFwd)).coerceIn(0.0, 1.0)
                        startLat += (endLat - startLat) * t
                        startDz += (endDz - startDz) * t
                        startFwd = horizonFwd
                    }

                    if (endFwd < nearLimit) {
                        val t = ((nearLimit - endFwd) / (startFwd - endFwd)).coerceIn(0.0, 1.0)
                        endLat += (startLat - endLat) * t
                        endDz += (startDz - endDz) * t
                        endFwd = nearLimit
                    } else if (endFwd > horizonFwd) {
                        val t = ((horizonFwd - endFwd) / (startFwd - endFwd)).coerceIn(0.0, 1.0)
                        endLat += (startLat - endLat) * t
                        endDz += (startDz - endDz) * t
                        endFwd = horizonFwd
                    }

                    val sp1 = projectLocal(startFwd, startLat, startDz)
                    val sp2 = projectLocal(endFwd, endLat, endDz)
                    if (sp1 != null && sp2 != null) {
                        drawLine(
                            color = color,
                            start = sp1,
                            end = sp2,
                            strokeWidth = strokeWidth,
                            pathEffect = pathEffect,
                            cap = cap
                        )
                    }
                }

                // =========================================================================
                // 3D TOPOGRAPHIC TERRAIN MESH (0.5 MILE × 0.5 MILE REAL-TIME ELEVATION DEM)
                // =========================================================================
                if (show3DTerrainMesh && terrainSquare != null) {
                    // 1. Draw 3D 0.5-Mile Quarter Section Perimeter Wireframe (Glowing Cyan)
                    val nwCorner = Vec2(terrainSquare.minX, terrainSquare.maxY)
                    val neCorner = Vec2(terrainSquare.maxX, terrainSquare.maxY)
                    val seCorner = Vec2(terrainSquare.maxX, terrainSquare.minY)
                    val swCorner = Vec2(terrainSquare.minX, terrainSquare.minY)

                    val topoBoundColor = Color(0xFF00E5FF).copy(alpha = 0.85f)
                    val boundStroke = 2.5f
                    val boundDash = PathEffect.dashPathEffect(floatArrayOf(14f, 8f), 0f)

                    draw3DLine(nwCorner, neCorner, topoBoundColor, boundStroke, boundDash)
                    draw3DLine(neCorner, seCorner, topoBoundColor, boundStroke, boundDash)
                    draw3DLine(seCorner, swCorner, topoBoundColor, boundStroke, boundDash)
                    draw3DLine(swCorner, nwCorner, topoBoundColor, boundStroke, boundDash)

                    // 2. Draw 3D Elevation Topo Contours across the 0.5-mile terrain
                    for (contour in terrainSquare.contours) {
                        if (contour.points.size < 2) continue
                        val isIndex = contour.isIndexContour
                        val contourColor = if (isIndex) {
                            Color(0xFFFFD54F).copy(alpha = 0.95f) // Index contour (gold)
                        } else {
                            Color(0xFF81C784).copy(alpha = 0.65f) // Intermediate contour (emerald)
                        }
                        val strokeW = if (isIndex) 2.2f else 1.2f
                        val pathEffect = if (isIndex) null else PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)

                        for (cIdx in 0 until contour.points.size - 1) {
                            draw3DLine(
                                p1 = contour.points[cIdx],
                                p2 = contour.points[cIdx + 1],
                                color = contourColor,
                                strokeWidth = strokeW,
                                pathEffect = pathEffect,
                                elev1 = contour.elevationMetersMsl,
                                elev2 = contour.elevationMetersMsl
                            )
                        }
                    }
                }

                // --- 3D FIELD GRID (CLEAN, OPTIMIZED FOR 60-120 FPS REFRESH RATE) ---
                val forwardSpacingM = 10.0 // 10m grid spacing for optimal 120fps performance
                val lateralSpacingM = 10.0
                val maxForwardDist = horizonFwd
                val maxLateralDist = 180.0

                // Forward-running track grid lines
                var latOffset = -maxLateralDist
                while (latOffset <= maxLateralDist) {
                    val pStartWorld = tractorPivot + (rightVec * latOffset) - (fwdVec * 12.0)
                    val pEndWorld = tractorPivot + (rightVec * latOffset) + (fwdVec * maxForwardDist)
                    val isMajor = abs(latOffset % (lateralSpacingM * 3)) < 0.5
                    val gridAlpha = if (isMajor) 0.5f else 0.2f
                    draw3DLine(
                        p1 = pStartWorld,
                        p2 = pEndWorld,
                        color = gridLineColor.copy(alpha = gridAlpha),
                        strokeWidth = if (isMajor) 1.5f else 0.8f
                    )
                    latOffset += lateralSpacingM
                }

                // Lateral cross grid lines
                val forwardProgress = (tractorPivot.x * sinH + tractorPivot.y * cosH)
                val rawMod = ((forwardProgress % forwardSpacingM) + forwardSpacingM) % forwardSpacingM
                val scrollOffset = (forwardSpacingM - rawMod) % forwardSpacingM
                var fwdDist = -12.0 + scrollOffset
                while (fwdDist <= maxForwardDist) {
                    val pLeftWorld = tractorPivot + (fwdVec * fwdDist) - (rightVec * maxLateralDist)
                    val pRightWorld = tractorPivot + (fwdVec * fwdDist) + (rightVec * maxLateralDist)
                    val isMajor = abs((fwdDist + (forwardProgress - scrollOffset)) % (forwardSpacingM * 3)) < 1.5
                    val distFraction = ((fwdDist + 12.0) / (maxForwardDist + 12.0)).toFloat().coerceIn(0f, 1f)
                    val fadeFactor = (1.0f - distFraction * 0.65f).coerceIn(0.2f, 1.0f)
                    val baseAlpha = if (isMajor) 0.5f else 0.2f
                    draw3DLine(
                        p1 = pLeftWorld,
                        p2 = pRightWorld,
                        color = gridLineColor.copy(alpha = baseAlpha * fadeFactor),
                        strokeWidth = if (isMajor) 1.5f else 0.8f
                    )
                    fwdDist += if (fwdDist > 80.0) forwardSpacingM * 2 else forwardSpacingM
                }

                // 1. Infield Coverage Rendering (Applied Segments in 3D with Viewport Distance Culling)
                val maxRenderDistSq = 240.0 * 240.0
                for (i in appliedSegments.indices) {
                    val seg = appliedSegments[i]
                    val dx = seg.center.x - tractorPivot.x
                    val dy = seg.center.y - tractorPivot.y
                    if (dx * dx + dy * dy > maxRenderDistSq) continue

                    val p1 = project3D(seg.leftStart)
                    val p2 = project3D(seg.rightStart)
                    val p3 = project3D(seg.rightEnd)
                    val p4 = project3D(seg.leftEnd)

                    if (p1 != null && p2 != null && p3 != null && p4 != null) {
                        reusableSegPath.reset()
                        reusableSegPath.moveTo(p1.x, p1.y)
                        reusableSegPath.lineTo(p2.x, p2.y)
                        reusableSegPath.lineTo(p3.x, p3.y)
                        reusableSegPath.lineTo(p4.x, p4.y)
                        reusableSegPath.close()
                        drawPath(
                            path = reusableSegPath,
                            color = baseCoverageColor,
                            style = Fill
                        )
                    }
                }

                // Smooth Live Brush Head in 3D (Connects last segment directly to live toolbar in real time)
                val isImplementWorking3D = vehicleState.isAutoSteerEngaged || implementConfig.isMasterActive
                if (isImplementWorking3D && abs(vehicleState.speedKmh) > 0.3) {
                    val toolbarDistM = vehicleConfig.hitchLength - implementConfig.offsetBehindTractor
                    val liveBoomCenter = tractorPivot + (fwdVec * toolbarDistM)
                    val halfBoomM = implementConfig.toolWidth * 0.5
                    val liveBoomLeft = liveBoomCenter - (rightVec * halfBoomM)
                    val liveBoomRight = liveBoomCenter + (rightVec * halfBoomM)

                    val lastSeg = appliedSegments.lastOrNull()
                    val startLeft = lastSeg?.leftEnd ?: liveBoomLeft
                    val startRight = lastSeg?.rightEnd ?: liveBoomRight

                    val p1 = project3D(startLeft)
                    val p2 = project3D(startRight)
                    val p3 = project3D(liveBoomRight)
                    val p4 = project3D(liveBoomLeft)

                    if (p1 != null && p2 != null && p3 != null && p4 != null) {
                        reusableSegPath.reset()
                        reusableSegPath.moveTo(p1.x, p1.y)
                        reusableSegPath.lineTo(p2.x, p2.y)
                        reusableSegPath.lineTo(p3.x, p3.y)
                        reusableSegPath.lineTo(p4.x, p4.y)
                        reusableSegPath.close()
                        drawPath(
                            path = reusableSegPath,
                            color = baseCoverageColor,
                            style = Fill
                        )
                    }
                }

                // 2. Field Boundary in 3D
                if (fieldBoundary.size >= 2) {
                    for (i in fieldBoundary.indices) {
                        val nextIdx = (i + 1) % fieldBoundary.size
                        draw3DLine(
                            p1 = fieldBoundary[i],
                            p2 = fieldBoundary[nextIdx],
                            color = if (isRecordingBoundary) Color(0xFFFF5252) else boundaryColor,
                            strokeWidth = 2.8f
                        )
                    }
                }

                // 3. Headland Boundary in 3D
                if (headlandBoundary.size >= 2) {
                    for (i in headlandBoundary.indices) {
                        val nextIdx = (i + 1) % headlandBoundary.size
                        draw3DLine(
                            p1 = headlandBoundary[i],
                            p2 = headlandBoundary[nextIdx],
                            color = headlandColor,
                            strokeWidth = 2.0f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                        )
                    }
                }

                // 4. RTK Base Station Tower & Baseline in 3D
                if (rtkBaseConfig.isEnabled) {
                    val rtkLocal = Vec2(rtkBaseConfig.relativeX, rtkBaseConfig.relativeY)
                    val spRtk = project3D(rtkLocal)
                    if (spRtk != null) {
                        reusableTowerPath.reset()
                        reusableTowerPath.moveTo(spRtk.x, spRtk.y - 14.dp.toPx())
                        reusableTowerPath.lineTo(spRtk.x - 8.dp.toPx(), spRtk.y + 6.dp.toPx())
                        reusableTowerPath.lineTo(spRtk.x + 8.dp.toPx(), spRtk.y + 6.dp.toPx())
                        reusableTowerPath.close()
                        drawPath(reusableTowerPath, Color(0xFF00E5FF), style = Fill)
                        drawCircle(Color(0xFFFFD54F), radius = 3.5.dp.toPx(), center = Offset(spRtk.x, spRtk.y - 14.dp.toPx()))

                        draw3DLine(
                            p1 = rtkLocal,
                            p2 = tractorPivot,
                            color = Color(0x6600E5FF),
                            strokeWidth = 1.4f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                        )
                    }
                }

                // 5. Guidance Swath Lines in 3D (AB lines or Contour Curve)
                if (guidanceMode == GuidanceMode.CONTOUR_CURVE && currentCurveLine != null && currentCurveLine.points.size >= 2) {
                    val curvePts = currentCurveLine.points
                    val swathW = implementConfig.swathWidth
                    val activeIdx = vehicleState.activeSwathIndex
                    for (offset in -3..3) {
                        val passIdx = activeIdx + offset
                        val isTarget = (offset == 0)
                        val passColor = if (isTarget) activeSwathColor else parallelSwathColor
                        val strokeW = if (isTarget) 4.5f else 1.8f

                        for (i in 0 until curvePts.size - 1) {
                            val p0 = curvePts[i]
                            val p1 = curvePts[i + 1]
                            val dir = (p1 - p0).normalized()
                            val norm = dir.perpClockwise()
                            val shift = norm * (passIdx * swathW)

                            draw3DLine(
                                p1 = p0 + shift,
                                p2 = p1 + shift,
                                color = passColor,
                                strokeWidth = strokeW,
                                pathEffect = if (isTarget) null else PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                } else if (currentABLine != null) {
                    val swathW = implementConfig.swathWidth
                    val activeIndex = vehicleState.activeSwathIndex
                    val lineDir = currentABLine.dirVector
                    val lineNormal = currentABLine.normalVector

                    for (passOffset in -5..5) {
                        val passIndex = activeIndex + passOffset
                        val swathOrigin = currentABLine.aLocal + (lineNormal * (passIndex * swathW))

                        // Project tractor position onto the swath line to center the line dynamically
                        val toTractor = tractorPivot - swathOrigin
                        val distAlongLine = toTractor.x * lineDir.x + toTractor.y * lineDir.y
                        val currentSwathCenter = swathOrigin + (lineDir * distAlongLine)

                        // Ensure effective line vector points forward in direction of vehicle travel
                        val lineDirDotTractorFwd = lineDir.x * sinH + lineDir.y * cosH
                        val effectiveDir = if (lineDirDotTractorFwd >= 0.0) lineDir else (lineDir * -1.0)

                        val pStart = currentSwathCenter - (effectiveDir * 15.0)
                        val pEnd = currentSwathCenter + (effectiveDir * maxForwardDist.coerceAtMost(1600.0))

                        val isTarget = (passOffset == 0)
                        if (isTarget) {
                            draw3DLine(
                                p1 = pStart,
                                p2 = pEnd,
                                color = activeSwathColor.copy(alpha = 0.22f),
                                strokeWidth = 9.0f,
                                cap = StrokeCap.Round
                            )
                        }
                        draw3DLine(
                            p1 = pStart,
                            p2 = pEnd,
                            color = if (isTarget) activeSwathColor else parallelSwathColor,
                            strokeWidth = if (isTarget) 4.0f else 1.8f,
                            pathEffect = if (isTarget) null else PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f),
                            cap = StrokeCap.Round
                        )
                    }
                }

                // 6. 3D U-Turn Dubins Arc Preview
                if (uTurnPath.size >= 2) {
                    for (i in 0 until uTurnPath.size - 1) {
                        draw3DLine(
                            p1 = uTurnPath[i],
                            p2 = uTurnPath[i + 1],
                            color = Color(0xFF00E5FF),
                            strokeWidth = 3.0f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                        )
                    }
                }

                // 7. 3D Field Obstacles & Hazards
                for (obs in obstacles) {
                    val sp = project3D(obs.localPos)
                    if (sp != null) {
                        val radiusPx = (obs.radiusMeters * pixelsPerMeter * 0.75f).toFloat().coerceIn(8f, 70f)
                        drawCircle(
                            color = obs.type.displayColor.copy(alpha = 0.30f),
                            radius = radiusPx,
                            center = sp
                        )
                        drawCircle(
                            color = obs.type.displayColor,
                            radius = 8.dp.toPx(),
                            center = sp
                        )
                        drawCircle(
                            color = Color.Black,
                            radius = 3.5.dp.toPx(),
                            center = sp
                        )
                    }
                }

                // 8. Draw Tractor Vehicle Model in Cab View
                drawTractorVehicle(
                    pixelsPerMeter = pixelsPerMeter * 1.15f,
                    center = tractorScreenPos,
                    vehicleConfig = vehicleConfig,
                    implementConfig = implementConfig,
                    actualSteerDeg = vehicleState.actualSteerAngleDeg,
                    isAutoSteerEngaged = vehicleState.isAutoSteerEngaged,
                    sectionStates = vehicleState.sectionStates,
                    slidingHitchConfig = slidingHitchConfig,
                    slidingHitchState = slidingHitchState
                )

            } else {
                // --- 2D OVERHEAD MODES (BIRD EYE 2D, NORTH UP, FREE PAN) ---
                val isHeadUp = (cameraViewMode == CameraViewMode.BIRD_EYE_2D)
                val centerScreen = Offset(canvasWidth / 2f + panOffset.x, canvasHeight / 2f + panOffset.y)
                val canvasRotation = if (isHeadUp) -headingDeg.toFloat() else 0f

                withTransform({
                    rotate(degrees = canvasRotation, pivot = centerScreen)
                }) {
                    fun worldToScreen(worldPoint: Vec2): Offset {
                        val dx = (worldPoint.x - tractorPivot.x).toFloat() * pixelsPerMeter
                        val dy = (worldPoint.y - tractorPivot.y).toFloat() * pixelsPerMeter
                        return Offset(centerScreen.x + dx, centerScreen.y - dy)
                    }

                    // --- TOPOGRAPHICAL & SATELLITE BASEMAP TILES (ESRI TOPO / USGS TOPO / OPENTOPO) ---
                    if (showSatelliteOverlay && vehicleState.geoPosition.latitude != 0.0) {
                        val z = tileRepo.getZoomForPixelsPerMeter(pixelsPerMeter, vehicleState.geoPosition.latitude, satelliteMapProvider)
                        val centerTile = tileRepo.geoToTile(vehicleState.geoPosition.latitude, vehicleState.geoPosition.longitude, z)
                        for (dx in -2..2) {
                            for (dy in -2..2) {
                                val tile = TileCoord(z, centerTile.x + dx, centerTile.y + dy)
                                val cachedImage = tileRepo.getCachedTile(tile, satelliteMapProvider)
                                val nwGeo = tileRepo.tileToGeoNW(tile)
                                val seGeo = tileRepo.tileToGeoSE(tile)
                                val nwLocal = GeoUtils.geoToLocalMeters(nwGeo, currentOriginGeo)
                                val seLocal = GeoUtils.geoToLocalMeters(seGeo, currentOriginGeo)

                                val nwScreen = worldToScreen(nwLocal)
                                val seScreen = worldToScreen(seLocal)

                                val tileLeft = min(nwScreen.x, seScreen.x)
                                val tileTop = min(nwScreen.y, seScreen.y)
                                val tileWidth = abs(seScreen.x - nwScreen.x)
                                val tileHeight = abs(seScreen.y - nwScreen.y)

                                if (tileWidth > 1f && tileHeight > 1f) {
                                    if (cachedImage != null) {
                                        drawImage(
                                            image = cachedImage,
                                            dstOffset = androidx.compose.ui.unit.IntOffset(tileLeft.roundToInt(), tileTop.roundToInt()),
                                            dstSize = androidx.compose.ui.unit.IntSize(tileWidth.roundToInt(), tileHeight.roundToInt()),
                                            filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium,
                                            alpha = 0.95f
                                        )
                                    } else {
                                        // Parent tile fallback at z-1: prevents blank screen while high-res tile downloads
                                        val parentCoord = TileCoord(z - 1, tile.x shr 1, tile.y shr 1)
                                        val parentImg = tileRepo.getCachedTile(parentCoord, satelliteMapProvider)
                                        if (parentImg != null) {
                                            val qx = (tile.x and 1) * 128
                                            val qy = (tile.y and 1) * 128
                                            drawImage(
                                                image = parentImg,
                                                srcOffset = androidx.compose.ui.unit.IntOffset(qx, qy),
                                                srcSize = androidx.compose.ui.unit.IntSize(128, 128),
                                                dstOffset = androidx.compose.ui.unit.IntOffset(tileLeft.roundToInt(), tileTop.roundToInt()),
                                                dstSize = androidx.compose.ui.unit.IntSize(tileWidth.roundToInt(), tileHeight.roundToInt()),
                                                filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium,
                                                alpha = 0.85f
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // --- 2D FIELD GRID & PLSS TOWNSHIP SECTION LINES (CLEAN, 120 FPS OPTIMIZED) ---
                    val screenDiagPx = sqrt((canvasWidth * canvasWidth + canvasHeight * canvasHeight).toDouble())
                    val viewExtentM = ((screenDiagPx / 2.0 + max(abs(panOffset.x.toDouble()), abs(panOffset.y.toDouble()))) / pixelsPerMeter.toDouble()) + 40.0

                    // Clean, well-spaced grid intervals (max 20-30 lines across screen)
                    val (minorStepM, majorStepM) = when {
                        pixelsPerMeter >= 6.0f -> Pair(10.0, 50.0)
                        pixelsPerMeter >= 2.5f -> Pair(20.0, 100.0)
                        pixelsPerMeter >= 0.8f -> Pair(50.0, 250.0)
                        else -> Pair(100.0, 500.0)
                    }

                    val startX = floor((tractorPivot.x - viewExtentM) / minorStepM) * minorStepM
                    val endX = ceil((tractorPivot.x + viewExtentM) / minorStepM) * minorStepM
                    val startY = floor((tractorPivot.y - viewExtentM) / minorStepM) * minorStepM
                    val endY = ceil((tractorPivot.y + viewExtentM) / minorStepM) * minorStepM

                    var currX = startX
                    while (currX <= endX) {
                        val p1 = worldToScreen(Vec2(currX, startY))
                        val p2 = worldToScreen(Vec2(currX, endY))
                        val isMajor = abs(currX % majorStepM) < (minorStepM * 0.4)
                        val lineAlpha = if (isMajor) 0.55f else 0.18f
                        drawLine(
                            color = gridLineColor.copy(alpha = lineAlpha),
                            start = p1,
                            end = p2,
                            strokeWidth = if (isMajor) 1.5f else 0.8f
                        )
                        currX += minorStepM
                    }

                    var currY = startY
                    while (currY <= endY) {
                        val p1 = worldToScreen(Vec2(startX, currY))
                        val p2 = worldToScreen(Vec2(endX, currY))
                        val isMajor = abs(currY % majorStepM) < (minorStepM * 0.4)
                        val lineAlpha = if (isMajor) 0.55f else 0.18f
                        drawLine(
                            color = gridLineColor.copy(alpha = lineAlpha),
                            start = p1,
                            end = p2,
                            strokeWidth = if (isMajor) 1.5f else 0.8f
                        )
                        currY += minorStepM
                    }

                    // --- 0.5 MILE × 0.5 MILE (160 ACRE) TOPOGRAPHIC DEM MAP LAYER (REAL-TIME CONTOURS & DEM) ---
                    if (showTopographyMap && terrainSquare != null) {
                        // 1. Draw 0.5 mi × 0.5 mi Quarter Section Boundary Box
                        val nwScreen = worldToScreen(Vec2(terrainSquare.minX, terrainSquare.maxY))
                        val neScreen = worldToScreen(Vec2(terrainSquare.maxX, terrainSquare.maxY))
                        val seScreen = worldToScreen(Vec2(terrainSquare.maxX, terrainSquare.minY))
                        val swScreen = worldToScreen(Vec2(terrainSquare.minX, terrainSquare.minY))

                        val topoBoxPath = Path().apply {
                            moveTo(nwScreen.x, nwScreen.y)
                            lineTo(neScreen.x, neScreen.y)
                            lineTo(seScreen.x, seScreen.y)
                            lineTo(swScreen.x, swScreen.y)
                            close()
                        }
                        // Quarter section perimeter outline (cyan dashed line)
                        drawPath(
                            path = topoBoxPath,
                            color = Color(0xFF00E5FF).copy(alpha = 0.75f),
                            style = Stroke(
                                width = 2.2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f), 0f)
                            )
                        )

                        // 2. Draw all real-time Topographic Contours (5' intervals generated from DEM)
                        for (contour in terrainSquare.contours) {
                            if (contour.points.size < 2) continue
                            val isIndex = contour.isIndexContour
                            val strokeW = if (isIndex) 2.2f else 1.1f
                            val contourColor = if (isIndex) {
                                if (isNightMode) Color(0xFFFFD54F).copy(alpha = 0.85f) else Color(0xFFD84315).copy(alpha = 0.85f)
                            } else {
                                if (isNightMode) Color(0xFFA5D6A7).copy(alpha = 0.5f) else Color(0xFF8D6E63).copy(alpha = 0.45f)
                            }

                            for (i in 0 until contour.points.size - 1) {
                                val sp1 = worldToScreen(contour.points[i])
                                val sp2 = worldToScreen(contour.points[i + 1])
                                drawLine(
                                    color = contourColor,
                                    start = sp1,
                                    end = sp2,
                                    strokeWidth = strokeW,
                                    pathEffect = if (isIndex) null else PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                                )
                            }
                        }
                    }

                    // --- RTK BASE STATION (CONFIGURABLE LOCATION & SURVEY POINT) ---
                    if (rtkBaseConfig.isEnabled) {
                        val rtkBasePos = Vec2(rtkBaseConfig.relativeX, rtkBaseConfig.relativeY)
                        val rtkScreenPos = worldToScreen(rtkBasePos)

                        // RTK Radio Broadcast Waves (Concentric pulsing circles)
                        drawCircle(
                            color = Color(0x2200E5FF),
                            radius = 28.dp.toPx(),
                            center = rtkScreenPos
                        )
                        drawCircle(
                            color = Color(0x4400E5FF),
                            radius = 18.dp.toPx(),
                            center = rtkScreenPos,
                            style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f))
                        )
                        drawCircle(
                            color = Color(0x8800E5FF),
                            radius = 9.dp.toPx(),
                            center = rtkScreenPos,
                            style = Stroke(width = 2f)
                        )

                        // RTK Base Station Tower Icon (Truss Triangle + Mast)
                        reusableTowerPath.reset()
                        reusableTowerPath.moveTo(rtkScreenPos.x, rtkScreenPos.y - 12.dp.toPx()) // Top Antenna
                        reusableTowerPath.lineTo(rtkScreenPos.x - 7.dp.toPx(), rtkScreenPos.y + 6.dp.toPx()) // Left Leg
                        reusableTowerPath.lineTo(rtkScreenPos.x + 7.dp.toPx(), rtkScreenPos.y + 6.dp.toPx()) // Right Leg
                        reusableTowerPath.close()
                        drawPath(
                            path = reusableTowerPath,
                            color = Color(0xFF00E5FF),
                            style = Fill
                        )
                        drawCircle(
                            color = Color(0xFFFFD54F),
                            radius = 3.dp.toPx(),
                            center = Offset(rtkScreenPos.x, rtkScreenPos.y - 12.dp.toPx())
                        )

                        // Baseline Vector connecting Tractor to RTK Base Station
                        drawLine(
                            color = Color(0x6600E5FF),
                            start = rtkScreenPos,
                            end = worldToScreen(tractorPivot),
                            strokeWidth = 1.2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                        )
                    }

                    // 1. Field Boundary Polygon
                    if (fieldBoundary.size >= 3) {
                        reusableBoundaryPath.reset()
                        val first = worldToScreen(fieldBoundary[0])
                        reusableBoundaryPath.moveTo(first.x, first.y)
                        for (i in 1 until fieldBoundary.size) {
                            val pt = worldToScreen(fieldBoundary[i])
                            reusableBoundaryPath.lineTo(pt.x, pt.y)
                        }
                        reusableBoundaryPath.close()
                        drawPath(
                            path = reusableBoundaryPath,
                            color = Color(0x1800E676),
                            style = Fill
                        )
                        drawPath(
                            path = reusableBoundaryPath,
                            color = if (isRecordingBoundary) Color(0xFFFF5252) else boundaryColor,
                            style = Stroke(
                                width = 2.5f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 8f), 0f)
                            )
                        )
                    } else if (fieldBoundary.size >= 2) {
                        for (i in 0 until fieldBoundary.size - 1) {
                            drawLine(
                                color = Color(0xFFFF5252),
                                start = worldToScreen(fieldBoundary[i]),
                                end = worldToScreen(fieldBoundary[i + 1]),
                                strokeWidth = 3f
                            )
                        }
                    }

                    // 2. Inner Headland Boundary
                    if (headlandBoundary.size >= 3) {
                        reusableHeadlandPath.reset()
                        val first = worldToScreen(headlandBoundary[0])
                        reusableHeadlandPath.moveTo(first.x, first.y)
                        for (i in 1 until headlandBoundary.size) {
                            val pt = worldToScreen(headlandBoundary[i])
                            reusableHeadlandPath.lineTo(pt.x, pt.y)
                        }
                        reusableHeadlandPath.close()
                        drawPath(
                            path = reusableHeadlandPath,
                            color = headlandColor,
                            style = Stroke(
                                width = 2.0f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                            )
                        )
                    }

                    // 3. Infield Coverage Rendering (Smooth continuous paint ribbon)
                    for (i in appliedSegments.indices) {
                        val seg = appliedSegments[i]
                        val dx = abs(seg.center.x - tractorPivot.x)
                        val dy = abs(seg.center.y - tractorPivot.y)
                        if (dx > viewExtentM || dy > viewExtentM) {
                            continue
                        }

                        val p1 = worldToScreen(seg.leftStart)
                        val p2 = worldToScreen(seg.rightStart)
                        val p3 = worldToScreen(seg.rightEnd)
                        val p4 = worldToScreen(seg.leftEnd)

                        reusableSegPath.reset()
                        reusableSegPath.moveTo(p1.x, p1.y)
                        reusableSegPath.lineTo(p2.x, p2.y)
                        reusableSegPath.lineTo(p3.x, p3.y)
                        reusableSegPath.lineTo(p4.x, p4.y)
                        reusableSegPath.close()

                        drawPath(
                            path = reusableSegPath,
                            color = baseCoverageColor,
                            style = Fill
                        )
                    }

                    // Smooth Live Brush Head in 2D (Connects last segment directly to live toolbar in real time)
                    val isWorking2D = vehicleState.isAutoSteerEngaged || implementConfig.isMasterActive
                    if (isWorking2D && abs(vehicleState.speedKmh) > 0.3) {
                        val headingRad = Math.toRadians(headingDeg)
                        val fwd2D = Vec2(sin(headingRad), cos(headingRad))
                        val right2D = fwd2D.perpClockwise()
                        val toolbarDistM = vehicleConfig.hitchLength - implementConfig.offsetBehindTractor
                        val liveCenter2D = tractorPivot + (fwd2D * toolbarDistM)
                        val halfBoom2D = implementConfig.toolWidth * 0.5
                        val liveBoomLeft2D = liveCenter2D - (right2D * halfBoom2D)
                        val liveBoomRight2D = liveCenter2D + (right2D * halfBoom2D)

                        val lastSeg2D = appliedSegments.lastOrNull()
                        val startLeft2D = lastSeg2D?.leftEnd ?: liveBoomLeft2D
                        val startRight2D = lastSeg2D?.rightEnd ?: liveBoomRight2D

                        val p1 = worldToScreen(startLeft2D)
                        val p2 = worldToScreen(startRight2D)
                        val p3 = worldToScreen(liveBoomRight2D)
                        val p4 = worldToScreen(liveBoomLeft2D)

                        reusableSegPath.reset()
                        reusableSegPath.moveTo(p1.x, p1.y)
                        reusableSegPath.lineTo(p2.x, p2.y)
                        reusableSegPath.lineTo(p3.x, p3.y)
                        reusableSegPath.lineTo(p4.x, p4.y)
                        reusableSegPath.close()

                        drawPath(
                            path = reusableSegPath,
                            color = baseCoverageColor,
                            style = Fill
                        )
                    }

                    // 4. Guidance Swath Lines
                    if (guidanceMode == GuidanceMode.CONTOUR_CURVE && currentCurveLine != null && currentCurveLine.points.size >= 2) {
                        val curvePts = currentCurveLine.points
                        val swathW = implementConfig.swathWidth
                        val activeIdx = vehicleState.activeSwathIndex

                        // Render active and adjacent curved passes
                        for (offset in -3..3) {
                            val passIdx = activeIdx + offset
                            val isTarget = (offset == 0)
                            val passColor = if (isTarget) activeSwathColor else parallelSwathColor
                            val strokeW = if (isTarget) 4.0f else 1.8f

                            for (i in 0 until curvePts.size - 1) {
                                val p0 = curvePts[i]
                                val p1 = curvePts[i + 1]
                                val dir = (p1 - p0).normalized()
                                val norm = dir.perpClockwise()

                                val shift = norm * (passIdx * swathW)
                                val sp0 = worldToScreen(p0 + shift)
                                val sp1 = worldToScreen(p1 + shift)

                                drawLine(
                                    color = passColor,
                                    start = sp0,
                                    end = sp1,
                                    strokeWidth = strokeW,
                                    cap = StrokeCap.Round,
                                    pathEffect = if (isTarget) null else PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                                )
                            }
                        }
                    } else if (currentABLine != null) {
                        val swathW = implementConfig.swathWidth
                        val activeIndex = vehicleState.activeSwathIndex
                        val lineDir = currentABLine.dirVector
                        val lineNormal = currentABLine.normalVector
                        val lineLengthMeters = 500.0

                        for (passOffset in -6..6) {
                            val passIndex = activeIndex + passOffset
                            val swathOrigin = currentABLine.aLocal + (lineNormal * (passIndex * swathW))
                            val pStart = swathOrigin - (lineDir * lineLengthMeters)
                            val pEnd = swathOrigin + (lineDir * lineLengthMeters)

                            val screenStart = worldToScreen(pStart)
                            val screenEnd = worldToScreen(pEnd)

                            if (passOffset == 0) {
                                // Active target pass: Glowing Neon Green Solid line
                                drawLine(
                                    color = activeSwathColor.copy(alpha = 0.25f),
                                    start = screenStart,
                                    end = screenEnd,
                                    strokeWidth = 9.0f,
                                    cap = StrokeCap.Round
                                )
                                drawLine(
                                    color = activeSwathColor,
                                    start = screenStart,
                                    end = screenEnd,
                                    strokeWidth = 3.8f,
                                    cap = StrokeCap.Round
                                )
                            } else {
                                // Parallel passes: Cyan dashed lines
                                drawLine(
                                    color = parallelSwathColor,
                                    start = screenStart,
                                    end = screenEnd,
                                    strokeWidth = 1.8f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                                )
                            }
                        }
                    }

                    // Recording curve pass indicator
                    if (recordingCurvePoints.size >= 2) {
                        for (i in 0 until recordingCurvePoints.size - 1) {
                            drawLine(
                                color = Color(0xFFE040FB),
                                start = worldToScreen(recordingCurvePoints[i]),
                                end = worldToScreen(recordingCurvePoints[i + 1]),
                                strokeWidth = 3f
                            )
                        }
                    }

                    // U-Turn Dubins Path Preview
                    if (uTurnPath.size >= 2) {
                        for (i in 0 until uTurnPath.size - 1) {
                            drawLine(
                                color = Color(0xFF00E5FF),
                                start = worldToScreen(uTurnPath[i]),
                                end = worldToScreen(uTurnPath[i + 1]),
                                strokeWidth = 2.5f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                            )
                        }
                    }

                    // 4.5. Field Obstacles & Hazards (2D Top-Down View)
                    for (obs in obstacles) {
                        val screenPos = worldToScreen(obs.localPos)
                        val radiusPx = (obs.radiusMeters * pixelsPerMeter).toFloat().coerceAtLeast(14f)

                        // Translucent safety buffer halo
                        drawCircle(
                            color = obs.type.displayColor.copy(alpha = 0.22f),
                            radius = radiusPx,
                            center = screenPos
                        )
                        // Outer warning circle outline
                        drawCircle(
                            color = obs.type.displayColor.copy(alpha = 0.8f),
                            radius = radiusPx,
                            center = screenPos,
                            style = Stroke(
                                width = 2.0f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                            )
                        )
                        // Center Solid Hazard Marker Pin
                        drawCircle(
                            color = obs.type.displayColor,
                            radius = 6.dp.toPx(),
                            center = screenPos
                        )
                        drawCircle(
                            color = Color.Black,
                            radius = 3.dp.toPx(),
                            center = screenPos
                        )
                    }

                    // 5. Draw Tractor and Implement Model
                    // In Head-Up mode (canvas rotated by -headingDeg), vehicleAngle (+headingDeg) aligns the tractor straight forward/up.
                    // In North-Up mode (canvas unrotated 0°), vehicleAngle (+headingDeg) points the tractor in the exact direction of motion.
                    val vehicleAngle = headingDeg.toFloat()
                    withTransform({
                        rotate(degrees = vehicleAngle, pivot = centerScreen)
                    }) {
                        drawTractorVehicle(
                            pixelsPerMeter = pixelsPerMeter,
                            center = centerScreen,
                            vehicleConfig = vehicleConfig,
                            implementConfig = implementConfig,
                            actualSteerDeg = vehicleState.actualSteerAngleDeg,
                            isAutoSteerEngaged = vehicleState.isAutoSteerEngaged,
                            sectionStates = vehicleState.sectionStates,
                            slidingHitchConfig = slidingHitchConfig,
                            slidingHitchState = slidingHitchState
                        )
                    }
                }
            }

            // Compass Rose Overlay (Top-Right)
            drawCompass(
                center = Offset(size.width - 45.dp.toPx(), 45.dp.toPx()),
                headingDeg = headingDeg,
                isHeadUpMode = (cameraViewMode == CameraViewMode.CAB_3D || cameraViewMode == CameraViewMode.BIRD_EYE_2D)
            )
        }

        // Headland Turn Guidance Banner (Top-Center)
        if (headlandTurnInfo.isApproachingHeadland && headlandTurnInfo.direction != TurnDirection.NONE) {
            Surface(
                color = Color(0xEEFF9800),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .padding(top = 10.dp)
                    .align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (headlandTurnInfo.direction == TurnDirection.TURN_LEFT) "⮜ TURN LEFT" else "TURN RIGHT ⮞",
                        color = Color.Black,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "• Target Pass #${headlandTurnInfo.targetSwathIndex} in ${String.format(java.util.Locale.US, "%.0f", headlandTurnInfo.distanceToTurnMeters)}m",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Obstacle Proximity Warning Banner (Below Top Status)
        activeObstacleAlert?.let { obs ->
            Surface(
                color = Color(0xFFD50000),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .padding(top = if (headlandTurnInfo.isApproachingHeadland) 48.dp else 10.dp)
                    .align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "⚠ HAZARD NEARBY: ${obs.name.uppercase()}",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "(${String.format(java.util.Locale.US, "%.1f", obs.radiusMeters)}m zone)",
                        color = Color(0xFFFFD54F),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawTractorVehicle(
    pixelsPerMeter: Float,
    center: Offset,
    vehicleConfig: VehicleConfig,
    implementConfig: ImplementConfig,
    actualSteerDeg: Double,
    isAutoSteerEngaged: Boolean,
    sectionStates: List<Boolean>,
    slidingHitchConfig: SlidingHitchConfig = SlidingHitchConfig(),
    slidingHitchState: SlidingHitchState = SlidingHitchState()
) {
    val wheelbasePx = (vehicleConfig.wheelbase * pixelsPerMeter).toFloat()
    val halfTrackPx = (vehicleConfig.trackWidth * 0.5 * pixelsPerMeter).toFloat()
    val tireWidthPx = (0.52 * pixelsPerMeter).toFloat().coerceAtLeast(5f)
    val tireLengthPx = (1.25 * pixelsPerMeter).toFloat().coerceAtLeast(10f)
    val dualSpacingPx = (0.65 * pixelsPerMeter).toFloat().coerceAtLeast(7f)

    val rearTireColor = Color(0xFF212121)
    val rimColor = Color(0xFFECEFF1)

    // Left Dual Rear Wheels (Inner & Outer)
    val rearLeftInner = Offset(center.x - halfTrackPx, center.y)
    val rearLeftOuter = Offset(center.x - halfTrackPx - dualSpacingPx, center.y)
    drawRoundRect(
        color = rearTireColor,
        topLeft = Offset(rearLeftInner.x - tireWidthPx / 2f, rearLeftInner.y - tireLengthPx / 2f),
        size = Size(tireWidthPx, tireLengthPx),
        cornerRadius = CornerRadius(2f, 2f)
    )
    drawRoundRect(
        color = rearTireColor,
        topLeft = Offset(rearLeftOuter.x - tireWidthPx / 2f, rearLeftOuter.y - tireLengthPx / 2f),
        size = Size(tireWidthPx, tireLengthPx),
        cornerRadius = CornerRadius(2f, 2f)
    )
    // Dual wheel axle hub spacer
    drawLine(
        color = Color(0xFF37474F),
        start = rearLeftOuter,
        end = rearLeftInner,
        strokeWidth = 3f
    )

    // Right Dual Rear Wheels (Inner & Outer)
    val rearRightInner = Offset(center.x + halfTrackPx, center.y)
    val rearRightOuter = Offset(center.x + halfTrackPx + dualSpacingPx, center.y)
    drawRoundRect(
        color = rearTireColor,
        topLeft = Offset(rearRightInner.x - tireWidthPx / 2f, rearRightInner.y - tireLengthPx / 2f),
        size = Size(tireWidthPx, tireLengthPx),
        cornerRadius = CornerRadius(2f, 2f)
    )
    drawRoundRect(
        color = rearTireColor,
        topLeft = Offset(rearRightOuter.x - tireWidthPx / 2f, rearRightOuter.y - tireLengthPx / 2f),
        size = Size(tireWidthPx, tireLengthPx),
        cornerRadius = CornerRadius(2f, 2f)
    )
    drawLine(
        color = Color(0xFF37474F),
        start = rearRightInner,
        end = rearRightOuter,
        strokeWidth = 3f
    )

    // Rear Heavy Cast Axle
    drawLine(
        color = Color(0xFF1B2428),
        start = rearLeftInner,
        end = rearRightInner,
        strokeWidth = 4.5f
    )

    // Front Axle & Steered Wheels
    val frontAxleCenter = Offset(center.x, center.y - wheelbasePx)
    val frontLeft = Offset(frontAxleCenter.x - halfTrackPx * 0.9f, frontAxleCenter.y)
    val frontRight = Offset(frontAxleCenter.x + halfTrackPx * 0.9f, frontAxleCenter.y)
    val frontTireWidth = tireWidthPx * 0.85f
    val frontTireLength = tireLengthPx * 0.82f

    drawLine(
        color = Color(0xFF263238),
        start = frontLeft,
        end = frontRight,
        strokeWidth = 3.5f
    )

    // Steered Front Left Tire
    withTransform({
        rotate(degrees = actualSteerDeg.toFloat(), pivot = frontLeft)
    }) {
        drawRoundRect(
            color = if (isAutoSteerEngaged) Color(0xFF2E7D32) else rearTireColor,
            topLeft = Offset(frontLeft.x - frontTireWidth / 2f, frontLeft.y - frontTireLength / 2f),
            size = Size(frontTireWidth, frontTireLength),
            cornerRadius = CornerRadius(2f, 2f)
        )
    }

    // Steered Front Right Tire
    withTransform({
        rotate(degrees = actualSteerDeg.toFloat(), pivot = frontRight)
    }) {
        drawRoundRect(
            color = if (isAutoSteerEngaged) Color(0xFF2E7D32) else rearTireColor,
            topLeft = Offset(frontRight.x - frontTireWidth / 2f, frontRight.y - frontTireLength / 2f),
            size = Size(frontTireWidth, frontTireLength),
            cornerRadius = CornerRadius(2f, 2f)
        )
    }

    // Trajectory Motion Vector Lookahead (Front Axle Heading & Steer Vector)
    val vectorLookaheadPx = (wheelbasePx * 1.5f).coerceAtLeast(35f)
    withTransform({
        rotate(degrees = actualSteerDeg.toFloat() * 0.75f, pivot = frontAxleCenter)
    }) {
        val vectorEnd = Offset(frontAxleCenter.x, frontAxleCenter.y - vectorLookaheadPx)
        drawLine(
            color = if (isAutoSteerEngaged) Color(0xFF00E676) else Color(0xFFFFD54F),
            start = frontAxleCenter,
            end = vectorEnd,
            strokeWidth = 2.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
        )
        val arrowPath = Path().apply {
            moveTo(vectorEnd.x, vectorEnd.y - 6f)
            lineTo(vectorEnd.x - 5f, vectorEnd.y + 4f)
            lineTo(vectorEnd.x + 5f, vectorEnd.y + 4f)
            close()
        }
        drawPath(
            path = arrowPath,
            color = if (isAutoSteerEngaged) Color(0xFF00E676) else Color(0xFFFFD54F),
            style = Fill
        )
    }

    // New Holland 8870 Genesis Long Hood & Cab
    val hoodWidth = halfTrackPx * 0.76f
    val hoodLength = wheelbasePx * 0.94f
    // New Holland signature Vibrant Ford Blue (High contrast): Color(0xFF0066B3)
    val nhBlue = Color(0xFF0066B3)
    val nhYellow = Color(0xFFFFD100)
    val hoodColor = if (isAutoSteerEngaged) Color(0xFF00C853) else nhBlue

    // Main Engine Hood
    drawRoundRect(
        color = hoodColor,
        topLeft = Offset(center.x - hoodWidth / 2f, center.y - hoodLength),
        size = Size(hoodWidth, hoodLength),
        cornerRadius = CornerRadius(4f, 4f)
    )

    // New Holland White & Yellow Accent Striping along hood
    drawLine(
        color = Color.White,
        start = Offset(center.x - (hoodWidth * 0.42f), center.y - (hoodLength * 0.85f)),
        end = Offset(center.x - (hoodWidth * 0.42f), center.y - (hoodLength * 0.15f)),
        strokeWidth = 2.0f
    )
    drawLine(
        color = Color.White,
        start = Offset(center.x + (hoodWidth * 0.42f), center.y - (hoodLength * 0.85f)),
        end = Offset(center.x + (hoodWidth * 0.42f), center.y - (hoodLength * 0.15f)),
        strokeWidth = 2.0f
    )

    // Genesis Black Front Radiator Grille & Dual Headlights
    drawRoundRect(
        color = Color(0xFF1E2124),
        topLeft = Offset(center.x - (hoodWidth * 0.45f), center.y - hoodLength - 3f),
        size = Size(hoodWidth * 0.9f, 6f),
        cornerRadius = CornerRadius(2f, 2f)
    )
    // Headlights
    drawCircle(color = Color(0xFFFFF9C4), radius = 2.5f, center = Offset(center.x - (hoodWidth * 0.35f), center.y - hoodLength))
    drawCircle(color = Color(0xFFFFF9C4), radius = 2.5f, center = Offset(center.x + (hoodWidth * 0.35f), center.y - hoodLength))

    // Genesis Deluxe Cab Glass & Roof
    val cabWidth = hoodWidth * 1.25f
    val cabLength = hoodLength * 0.44f
    val cabY = center.y - cabLength - (wheelbasePx * 0.08f)

    // Tinted Glass
    drawRoundRect(
        color = Color(0xFF81D4FA).copy(alpha = 0.85f),
        topLeft = Offset(center.x - cabWidth / 2f, cabY),
        size = Size(cabWidth, cabLength),
        cornerRadius = CornerRadius(3f, 3f)
    )

    // White Genesis Roof Cap with Blue Sunshade Trim
    drawRoundRect(
        color = Color(0xFFFAFAFA),
        topLeft = Offset(center.x - (cabWidth * 0.48f), cabY + 2f),
        size = Size(cabWidth * 0.96f, cabLength * 0.52f),
        cornerRadius = CornerRadius(3f, 3f)
    )
    drawRoundRect(
        color = nhBlue,
        topLeft = Offset(center.x - (cabWidth * 0.45f), cabY + (cabLength * 0.45f)),
        size = Size(cabWidth * 0.9f, 2.5f),
        cornerRadius = CornerRadius(1f, 1f)
    )

    // Yellow Wheel Hub Centers (New Holland / Ag spec)
    drawCircle(color = nhYellow, radius = 3.5f, center = rearLeftInner)
    drawCircle(color = nhYellow, radius = 3.5f, center = rearRightInner)

    // GPS/GNSS Dual Antenna Target Dot
    val antennaOffsetPx = (vehicleConfig.antennaPivotOffset * pixelsPerMeter).toFloat()
    val antennaPos = Offset(center.x, center.y - antennaOffsetPx)
    drawCircle(
        color = Color(0xFFFFEA00),
        radius = 5.0f,
        center = antennaPos
    )
    drawCircle(
        color = Color(0xFF212121),
        radius = 5.0f,
        center = antennaPos,
        style = Stroke(width = 1.2f)
    )

    // --- IMPLEMENT HITCH & TOOLBAR GEOMETRY ---
    val hitchLengthPx = (-vehicleConfig.hitchLength * pixelsPerMeter).toFloat()
    val hitchY = center.y + hitchLengthPx
    val offsetPx = (implementConfig.offsetBehindTractor * pixelsPerMeter).toFloat()
    val boomY = hitchY + offsetPx

    // Lateral hitch shift from active sliding hitch controller
    val isHitchEnabled = slidingHitchConfig.isEnabled
    val hitchShiftPx = if (isHitchEnabled) {
        ((slidingHitchState.currentHitchShiftCm / 100.0) * pixelsPerMeter).toFloat()
    } else {
        0f
    }
    val toolbarCenterX = center.x + hitchShiftPx

    if (implementConfig.implementType == ImplementType.THREE_POINT_MOUNTED) {
        // --- 3-POINT HITCH MOUNTED TOOLBAR ---
        // 1. Lower Draft Links
        val leftLiftArmStart = Offset(center.x - halfTrackPx * 0.35f, center.y)
        val rightLiftArmStart = Offset(center.x + halfTrackPx * 0.35f, center.y)
        val leftQuickHitchPoint = Offset(center.x - halfTrackPx * 0.45f, hitchY)
        val rightQuickHitchPoint = Offset(center.x + halfTrackPx * 0.45f, hitchY)

        drawLine(color = Color(0xFF37474F), start = leftLiftArmStart, end = leftQuickHitchPoint, strokeWidth = 4f)
        drawLine(color = Color(0xFF37474F), start = rightLiftArmStart, end = rightQuickHitchPoint, strokeWidth = 4f)

        // 2. Top Link (Center cylinder)
        drawLine(
            color = Color(0xFF455A64),
            start = Offset(center.x, center.y - (wheelbasePx * 0.12f)),
            end = Offset(center.x, hitchY),
            strokeWidth = 3f
        )

        // 3. Category 3 Quick-Hitch Frame Crossbar
        drawLine(
            color = Color(0xFF263238),
            start = leftQuickHitchPoint,
            end = rightQuickHitchPoint,
            strokeWidth = 6f,
            cap = StrokeCap.Square
        )

        // 4. Sliding Hitch Hydraulic Carriage & Slide Rails
        if (isHitchEnabled) {
            val slideHalfPx = (halfTrackPx * 0.4f).coerceAtLeast(16f)
            // Heavy Dual Slide Rails
            drawLine(
                color = Color(0xFF1E282C),
                start = Offset(center.x - slideHalfPx, hitchY + 2f),
                end = Offset(center.x + slideHalfPx, hitchY + 2f),
                strokeWidth = 5f
            )
            drawLine(
                color = Color(0xFF1E282C),
                start = Offset(center.x - slideHalfPx, hitchY + 6f),
                end = Offset(center.x + slideHalfPx, hitchY + 6f),
                strokeWidth = 5f
            )

            // Hydraulic Cylinder Body (Dark Teal) & Chrome Rod (Silver)
            val cylBodyStart = Offset(center.x - slideHalfPx * 0.8f, hitchY + 4f)
            val cylBodyEnd = Offset(center.x, hitchY + 4f)
            val cylRodEnd = Offset(toolbarCenterX, hitchY + 4f)

            drawLine(color = Color(0xFF006064), start = cylBodyStart, end = cylBodyEnd, strokeWidth = 4f)
            drawLine(color = Color(0xFFE0F7FA), start = cylBodyEnd, end = cylRodEnd, strokeWidth = 2.5f)

            // Sliding Carriage Box
            drawRect(
                color = Color(0xFF00E5FF),
                topLeft = Offset(toolbarCenterX - 4f, hitchY),
                size = Size(8f, 8f)
            )

            // Connecting stanchions from sliding carriage to Toolbar
            drawLine(color = Color(0xFF00ACC1), start = Offset(toolbarCenterX - 6f, hitchY + 8f), end = Offset(toolbarCenterX - 6f, boomY), strokeWidth = 3.5f)
            drawLine(color = Color(0xFF00ACC1), start = Offset(toolbarCenterX + 6f, hitchY + 8f), end = Offset(toolbarCenterX + 6f, boomY), strokeWidth = 3.5f)
        } else if (offsetPx > 1f) {
            drawLine(color = Color(0xFF37474F), start = leftQuickHitchPoint, end = Offset(leftQuickHitchPoint.x, boomY), strokeWidth = 4f)
            drawLine(color = Color(0xFF37474F), start = rightQuickHitchPoint, end = Offset(rightQuickHitchPoint.x, boomY), strokeWidth = 4f)
            drawLine(color = Color(0xFF455A64), start = Offset(center.x, hitchY), end = Offset(center.x, boomY), strokeWidth = 3f)
        }

        // 5. Heavy Square-Tube Toolbar (Shifted laterally by sliding hitch)
        val halfToolWidthPx = (implementConfig.toolWidth * 0.5 * pixelsPerMeter).toFloat()
        val toolbarLeft = Offset(toolbarCenterX - halfToolWidthPx, boomY)
        val toolbarRight = Offset(toolbarCenterX + halfToolWidthPx, boomY)

        drawLine(
            color = Color(0xFF263238),
            start = toolbarLeft,
            end = toolbarRight,
            strokeWidth = 7f,
            cap = StrokeCap.Square
        )
    } else {
        // --- TRAILING TOOLBAR WITH DRAWBAR TONGUE ---
        val hitchPin = Offset(center.x, hitchY)
        drawLine(color = Color(0xFF37474F), start = center, end = hitchPin, strokeWidth = 3.5f)

        if (isHitchEnabled) {
            // Drawbar Slide Cylinder
            val slideHalfPx = 20f
            drawLine(color = Color(0xFF1E282C), start = Offset(center.x - slideHalfPx, hitchY), end = Offset(center.x + slideHalfPx, hitchY), strokeWidth = 6f)
            drawLine(color = Color(0xFF006064), start = Offset(center.x - slideHalfPx * 0.8f, hitchY), end = Offset(center.x, hitchY), strokeWidth = 3.5f)
            drawLine(color = Color(0xFFE0F7FA), start = Offset(center.x, hitchY), end = Offset(toolbarCenterX, hitchY), strokeWidth = 2.5f)
            drawCircle(color = Color(0xFF00E5FF), radius = 3.5f, center = Offset(toolbarCenterX, hitchY))
        }

        // Tongue to toolbar
        val halfTongueSpread = (implementConfig.toolWidth * 0.2 * pixelsPerMeter).toFloat().coerceAtLeast(15f)
        val tongueOrigin = if (isHitchEnabled) Offset(toolbarCenterX, hitchY) else hitchPin
        drawLine(color = Color(0xFF455A64), start = tongueOrigin, end = Offset(toolbarCenterX - halfTongueSpread, boomY), strokeWidth = 3f)
        drawLine(color = Color(0xFF455A64), start = tongueOrigin, end = Offset(toolbarCenterX + halfTongueSpread, boomY), strokeWidth = 3f)

        // Trailing toolbar bar
        val halfToolWidthPx = (implementConfig.toolWidth * 0.5 * pixelsPerMeter).toFloat()
        drawLine(
            color = Color(0xFF37474F),
            start = Offset(toolbarCenterX - halfToolWidthPx, boomY),
            end = Offset(toolbarCenterX + halfToolWidthPx, boomY),
            strokeWidth = 5f,
            cap = StrokeCap.Round
        )

        // Trailing gauge tires
        val wheelLeft = Offset(toolbarCenterX - halfToolWidthPx * 0.6f, boomY + 5f)
        val wheelRight = Offset(toolbarCenterX + halfToolWidthPx * 0.6f, boomY + 5f)
        drawCircle(color = Color(0xFF212121), radius = 4f, center = wheelLeft)
        drawCircle(color = Color(0xFF212121), radius = 4f, center = wheelRight)
    }

    // Boom Sections with active application spray nozzles (Shifted with toolbar)
    val spans = implementConfig.getSectionSpans()
    for (i in spans.indices) {
        val (leftOffset, rightOffset) = spans[i]
        val leftX = toolbarCenterX + (leftOffset * pixelsPerMeter).toFloat()
        val rightX = toolbarCenterX + (rightOffset * pixelsPerMeter).toFloat()
        val active = sectionStates.getOrElse(i) { false }
        val sectionColor = if (active) Color(0xFF00E676) else Color(0xFFFF5252)

        drawLine(
            color = sectionColor,
            start = Offset(leftX, boomY),
            end = Offset(rightX, boomY),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )

        // Spray Nozzles and Droplet Mist
        if (active) {
            val numNozzles = 4
            for (n in 0..numNozzles) {
                val nozzleX = leftX + (rightX - leftX) * (n.toFloat() / numNozzles)
                drawCircle(
                    color = Color(0xFF80D8FF),
                    radius = 2.5f,
                    center = Offset(nozzleX, boomY + 3f)
                )
                drawLine(
                    color = Color(0x6640C4FF),
                    start = Offset(nozzleX, boomY + 3f),
                    end = Offset(nozzleX + (n % 3 - 1) * 2f, boomY + 10f),
                    strokeWidth = 1.5f
                )
            }
        }
    }

    // Secondary GPS Antenna Mounted on Implement Toolbar
    if (isHitchEnabled) {
        val secAntennaAheadPx = (slidingHitchConfig.antennaOffsetAheadMeters * pixelsPerMeter).toFloat()
        val secAntennaLateralPx = (slidingHitchConfig.antennaLateralOffsetMeters * pixelsPerMeter).toFloat()
        val secAntennaPos = Offset(toolbarCenterX + secAntennaLateralPx, boomY - secAntennaAheadPx)

        // Outer beacon halo ring
        drawCircle(
            color = Color(0x8800E5FF),
            radius = 6.5f,
            center = secAntennaPos,
            style = Stroke(width = 1.5f)
        )
        // Yellow GNSS Receiver puck
        drawCircle(
            color = Color(0xFFFFD600),
            radius = 4.0f,
            center = secAntennaPos
        )
        drawCircle(
            color = Color(0xFF004D40),
            radius = 1.8f,
            center = secAntennaPos
        )

        // Implement Forward Guidance Laser Reference (Shows true implement cutting path)
        if (slidingHitchConfig.isAutoHitchEngaged) {
            drawLine(
                color = Color(0xAA00E5FF),
                start = secAntennaPos,
                end = Offset(secAntennaPos.x, secAntennaPos.y - 35f),
                strokeWidth = 1.5f
            )
        }
    }
}

private fun DrawScope.drawCompass(
    center: Offset,
    headingDeg: Double,
    isHeadUpMode: Boolean
) {
    val radius = 22.dp.toPx()
    drawCircle(
        color = Color(0xDD1E2822),
        radius = radius,
        center = center
    )
    drawCircle(
        color = Color(0xFF37474F),
        radius = radius,
        center = center,
        style = Stroke(width = 1.5f)
    )

    val needleRotation = if (isHeadUpMode) -headingDeg.toFloat() else 0f
    withTransform({
        rotate(degrees = needleRotation, pivot = center)
    }) {
        val northPath = Path().apply {
            moveTo(center.x, center.y - radius * 0.8f)
            lineTo(center.x - radius * 0.35f, center.y)
            lineTo(center.x + radius * 0.35f, center.y)
            close()
        }
        drawPath(path = northPath, color = Color(0xFFFF5252), style = Fill)

        val southPath = Path().apply {
            moveTo(center.x, center.y + radius * 0.8f)
            lineTo(center.x - radius * 0.35f, center.y)
            lineTo(center.x + radius * 0.35f, center.y)
            close()
        }
        drawPath(path = southPath, color = Color(0xFFB0BEC5), style = Fill)
    }
}

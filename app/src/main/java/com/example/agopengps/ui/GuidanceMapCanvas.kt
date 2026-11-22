package com.example.agopengps.ui

import android.graphics.Typeface
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agopengps.CameraViewMode
import com.example.agopengps.GuidanceMode
import com.example.agopengps.LiveTelemetryState
import com.example.agopengps.io.ThreePointState
import com.example.agopengps.map.MapTileProvider
import com.example.agopengps.map.SatelliteTileRepository
import com.example.agopengps.map.TerrainAttitude
import com.example.agopengps.map.TerrainSquare
import com.example.agopengps.navigation.*
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import kotlin.math.*

@Composable
fun GuidanceMapCanvas(
    vehicleState: VehicleState = VehicleState(),
    vehicleConfig: VehicleConfig,
    implementConfig: ImplementConfig,
    currentABLine: ABLine?,
    currentCurveLine: CurveLine?,
    guidanceMode: GuidanceMode = GuidanceMode.AB_LINE,
    cameraViewMode: CameraViewMode = CameraViewMode.CAB_3D,
    fieldBoundary: List<Vec2> = emptyList(),
    headlandBoundary: List<Vec2> = emptyList(),
    uTurnPath: List<Vec2> = emptyList(),
    recordingCurvePoints: List<Vec2> = emptyList(),
    isRecordingBoundary: Boolean = false,
    activeRecallPath: RecordedPath? = null,
    recordingPathPoints: List<PathPoint> = emptyList(),
    isRecordingPath: Boolean = false,
    appliedSegments: List<AppliedSwathSegment> = emptyList(),
    coverageChunkStore: CoverageChunkStore? = null,
    threePointState: ThreePointState = ThreePointState.LOWERED,
    isNightMode: Boolean = true,
    themeMode: DisplayThemeMode = DisplayThemeMode.STANDARD_AG,
    obstacles: List<FieldObstacle> = emptyList(),
    headlandTurnInfo: HeadlandTurnInfo = HeadlandTurnInfo(),
    activeObstacleAlert: FieldObstacle? = null,
    showSatelliteOverlay: Boolean = false,
    satelliteMapProvider: MapTileProvider = MapTileProvider.ESRI_WORLD_TOPO,
    rtkBaseConfig: RtkBaseStationConfig = RtkBaseStationConfig(),
    slidingHitchConfig: SlidingHitchConfig = SlidingHitchConfig(),
    slidingHitchState: SlidingHitchState = SlidingHitchState(),
    zoomFactor: Float = 1.0f,
    onZoomChange: (Float) -> Unit = {},
    terrainSquare: TerrainSquare? = null,
    terrainAttitude: TerrainAttitude = TerrainAttitude(),
    isTerrainCompensationEnabled: Boolean = true,
    showTopographyMap: Boolean = true,
    show3DTerrainMesh: Boolean = true,
    navigationOutput: NavigationOutput? = null,
    tempPointA: Vec2? = null,
    abTrackTrail: List<Vec2> = emptyList(),
    isSettingAB: Boolean = false,
    telemetryFlow: StateFlow<LiveTelemetryState>? = null,
    onToggleCameraMode: () -> Unit = {},
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
    val isDaylight = themeMode == DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST
    val isOledNight = themeMode == DisplayThemeMode.CAB_OLED_NIGHT

    // High-Sun Daylight matte glare-resistant palette + Emerald default + OLED Night
    val fieldBgColor = when (themeMode) {
        DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST -> Color(0xFFE9EFE9) // Matte glare-resistant field canvas
        DisplayThemeMode.CAB_OLED_NIGHT -> Color(0xFF000000)
        DisplayThemeMode.STANDARD_AG -> Color(0xFF090E17)
    }
    val gridLineColor = when (themeMode) {
        DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST -> Color(0xFF94A3B8)
        DisplayThemeMode.CAB_OLED_NIGHT -> Color(0x1CE53935)
        DisplayThemeMode.STANDARD_AG -> Color(0x2E94A3B8)
    }
    val boundaryColor = when (themeMode) {
        DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST -> Color(0xFFBE123C)
        DisplayThemeMode.CAB_OLED_NIGHT -> Color(0xFFFFB300)
        DisplayThemeMode.STANDARD_AG -> Color(0xFFFBBF24)
    }
    val headlandColor = when (themeMode) {
        DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST -> Color(0xFFC2410C)
        DisplayThemeMode.CAB_OLED_NIGHT -> Color(0xFFFF5722)
        DisplayThemeMode.STANDARD_AG -> Color(0xFFFB923C)
    }
    val activeSwathColor = when (themeMode) {
        DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST -> Color(0xFFD90429) // High-contrast crimson over matte light field & green swaths
        DisplayThemeMode.CAB_OLED_NIGHT -> Color(0xFFFF9100)
        DisplayThemeMode.STANDARD_AG -> Color(0xFF00FF9D)
    }
    val activeSwathHaloColor = when (themeMode) {
        DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST -> Color(0xF0FFFFFF)
        DisplayThemeMode.CAB_OLED_NIGHT -> Color(0x55FF6D00)
        DisplayThemeMode.STANDARD_AG -> Color(0xB8020617)
    }
    val parallelSwathColor = when (themeMode) {
        DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST -> Color(0xFF0369A1)
        DisplayThemeMode.CAB_OLED_NIGHT -> Color(0x55FFB300)
        DisplayThemeMode.STANDARD_AG -> Color(0x7038BDF8)
    }
    val baseCoverageColor = when (themeMode) {
        DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST -> Color(0xCC10B981)
        DisplayThemeMode.CAB_OLED_NIGHT -> Color(0xCCFF6D00)
        DisplayThemeMode.STANDARD_AG -> Color(0xC800E676)
    }
    val coverageBorderColor = when (themeMode) {
        DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST -> Color(0xFF047857)
        DisplayThemeMode.CAB_OLED_NIGHT -> Color(0xFFFF9100)
        DisplayThemeMode.STANDARD_AG -> Color(0xFF00B248)
    }
    val indexContourColor = when (themeMode) {
        DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST -> Color(0xDD9A3412) // Rich terracotta on matte light field
        DisplayThemeMode.CAB_OLED_NIGHT -> Color(0xBBFFB300)
        DisplayThemeMode.STANDARD_AG -> Color(0xDDFCD34D)
    }
    val minorContourColor = when (themeMode) {
        DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST -> Color(0x99854D0E)
        DisplayThemeMode.CAB_OLED_NIGHT -> Color(0x66FF9100)
        DisplayThemeMode.STANDARD_AG -> Color(0x8086EFAC)
    }
    val uTurnArcColor = when (themeMode) {
        DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST -> Color(0xFFEA580C)
        DisplayThemeMode.CAB_OLED_NIGHT -> Color(0xFFFFB300)
        DisplayThemeMode.STANDARD_AG -> Color(0xFFFFB703)
    }
    val uTurnHaloColor = when (themeMode) {
        DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST -> Color(0xE6FFFFFF)
        DisplayThemeMode.CAB_OLED_NIGHT -> Color(0x66000000)
        DisplayThemeMode.STANDARD_AG -> Color(0xC8090E17)
    }

    var panOffset by remember { mutableStateOf(Offset.Zero) }
    val currentOnZoomChange by rememberUpdatedState(onZoomChange)

    // Pre-allocated zero-allocation scratch buffers reused across every draw frame
    val scratch = remember { CanvasScratchBuffers() }

    // Collect live 20 Hz telemetry as State<T> and read ONLY inside Canvas draw lambda (zero composition recompositions!)
    val telemetryState: State<LiveTelemetryState>? = telemetryFlow?.collectAsStateWithLifecycle()

    // Only run pulse animation while actively setting A-B points to save GPU/CPU cycles during steady driving
    val pulseScaleState: State<Float>
    val pulseAlphaState: State<Float>
    if (isSettingAB || tempPointA != null) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        pulseScaleState = infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.45f,
            animationSpec = infiniteRepeatable(animation = tween(1100, easing = LinearEasing), repeatMode = RepeatMode.Restart),
            label = "pulse_scale"
        )
        pulseAlphaState = infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 0.0f,
            animationSpec = infiniteRepeatable(animation = tween(1100, easing = LinearEasing), repeatMode = RepeatMode.Restart),
            label = "pulse_alpha"
        )
    } else {
        pulseScaleState = remember { mutableFloatStateOf(1.1f) }
        pulseAlphaState = remember { mutableFloatStateOf(0.45f) }
    }

    LaunchedEffect(cameraViewMode) {
        if (cameraViewMode != CameraViewMode.FREE_PAN) {
            panOffset = Offset.Zero
        }
    }

    val tileRepo = remember { SatelliteTileRepository() }
    var tileVersion by remember { mutableIntStateOf(0) }

    // Lifecycle cleanup for background satellite/topo tile downloads
    DisposableEffect(tileRepo) {
        onDispose { tileRepo.shutdown() }
    }

    val currentPixelsPerMeter = (14.0f * zoomFactor).coerceIn(2.0f, 150.0f)

    // Coarsely bucketed tile trigger (~220m buckets) using derivedStateOf so tile fetches don't trigger 20 Hz recomposition
    val tileBucketKey by remember(telemetryState, vehicleState.geoPosition) {
        derivedStateOf {
            val geo = telemetryState?.value?.vehicleState?.geoPosition ?: vehicleState.geoPosition
            ((geo.latitude * 500.0).roundToInt().toLong() shl 32) or ((geo.longitude * 500.0).roundToInt().toLong() and 0xFFFFFFFFL)
        }
    }
    LaunchedEffect(showSatelliteOverlay, satelliteMapProvider, zoomFactor, tileBucketKey) {
        if (showSatelliteOverlay) {
            val centerGeo = telemetryState?.value?.vehicleState?.geoPosition ?: vehicleState.geoPosition
            tileRepo.ensureTilesLoaded(
                centerGeo = centerGeo,
                pixelsPerMeter = currentPixelsPerMeter,
                provider = satelliteMapProvider,
                onTileLoaded = { tileVersion++ }
            )
        } else {
            tileRepo.cancelPendingDownloads()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
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
            .testTag("guidance_canvas")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw-phase reads ONLY (isolates 20 Hz pose updates from Compose composition!)
            val _tileVersionTrigger = tileVersion
            val live = telemetryState?.value
            val liveVehicle = live?.vehicleState ?: vehicleState
            val liveTerrainSquare = live?.currentTerrainSquare ?: terrainSquare
            val liveUTurnPath = live?.uTurnPath ?: uTurnPath
            val liveAbTrail = live?.abTrackTrail ?: abTrackTrail
            val pulseScale = pulseScaleState.value
            val pulseAlpha = pulseAlphaState.value

            val canvasWidth = size.width
            val canvasHeight = size.height
            val tractorPivot = liveVehicle.localPivotPosition
            val headingDeg = liveVehicle.headingDeg
            val headingRad = Math.toRadians(headingDeg)
            val sinH = sin(headingRad)
            val cosH = cos(headingRad)

            val pixelsPerMeter = (14.0f * zoomFactor).coerceIn(2.0f, 150.0f)
            val is3D = (cameraViewMode == CameraViewMode.CAB_3D)
            val textPaint = scratch.textPaint

            // Shared Satellite & Topographic Tile Layer Renderer
            fun drawSatelliteTileLayer(anchorScreen: Offset, tileAlpha: Float = 1.0f) {
                val centerGeo = liveVehicle.geoPosition
                val renderZoom = tileRepo.getRenderZoomLevel(pixelsPerMeter, satelliteMapProvider, centerGeo.latitude)
                val visibleCoords = tileRepo.getVisibleTileCoords(centerGeo, renderZoom, radiusTiles = 3)

                for (tileCoord in visibleCoords) {
                    val cached = tileRepo.getCachedTileWithFallback(tileCoord, satelliteMapProvider) ?: continue
                    val (bitmap, actualCoord) = cached
                    val (tileNwLocal, tileSeLocal) = tileRepo.tileToLocalBoundsMeters(tileCoord, centerGeo)
                    val nwX = anchorScreen.x + tileNwLocal.x.toFloat() * pixelsPerMeter
                    val nwY = anchorScreen.y - tileNwLocal.y.toFloat() * pixelsPerMeter
                    val seX = anchorScreen.x + tileSeLocal.x.toFloat() * pixelsPerMeter
                    val seY = anchorScreen.y - tileSeLocal.y.toFloat() * pixelsPerMeter

                    val leftPx = min(nwX, seX).roundToInt()
                    val topPx = min(nwY, seY).roundToInt()
                    val rightPx = max(nwX, seX).roundToInt()
                    val bottomPx = max(nwY, seY).roundToInt()
                    val dstWidth = (rightPx - leftPx).coerceAtLeast(1)
                    val dstHeight = (bottomPx - topPx).coerceAtLeast(1)

                    if (rightPx < -canvasWidth || leftPx > canvasWidth * 2 || bottomPx < -canvasHeight || topPx > canvasHeight * 2) {
                        continue
                    }

                    val srcOffset: IntOffset
                    val srcSize: IntSize
                    if (actualCoord.z < tileCoord.z) {
                        val dz = tileCoord.z - actualCoord.z
                        val factor = 1 shl dz
                        val ix = tileCoord.x and (factor - 1)
                        val iy = tileCoord.y and (factor - 1)
                        val x0 = ((ix.toDouble() / factor) * bitmap.width).roundToInt().coerceIn(0, (bitmap.width - 1).coerceAtLeast(0))
                        val y0 = ((iy.toDouble() / factor) * bitmap.height).roundToInt().coerceIn(0, (bitmap.height - 1).coerceAtLeast(0))
                        val x1 = (((ix + 1).toDouble() / factor) * bitmap.width).roundToInt().coerceIn(x0 + 1, bitmap.width.coerceAtLeast(1))
                        val y1 = (((iy + 1).toDouble() / factor) * bitmap.height).roundToInt().coerceIn(y0 + 1, bitmap.height.coerceAtLeast(1))
                        srcOffset = IntOffset(x0, y0)
                        srcSize = IntSize((x1 - x0).coerceAtLeast(1), (y1 - y0).coerceAtLeast(1))
                    } else {
                        srcOffset = IntOffset.Zero
                        srcSize = IntSize(bitmap.width, bitmap.height)
                    }

                    drawImage(
                        image = bitmap,
                        srcOffset = srcOffset,
                        srcSize = srcSize,
                        dstOffset = IntOffset(leftPx, topPx),
                        dstSize = IntSize(dstWidth, dstHeight),
                        alpha = tileAlpha,
                        filterQuality = FilterQuality.Medium
                    )
                }
            }

            if (is3D) {
                // =========================================================================
                // --- 3D IN-CAB PERSPECTIVE VIEW ---
                // =========================================================================
                val horizonY = canvasHeight * 0.08f
                val tractorScreenPos = Offset(canvasWidth * 0.5f, canvasHeight * 0.62f)
                val fwdVec = Vec2(sinH, cosH)
                val rightVec = Vec2(cosH, -sinH)

                val skyColors = when {
                    isOledNight -> listOf(Color(0xFF000000), Color(0xFF080300), Color(0xFF140700))
                    isDaylight -> listOf(Color(0xFF93C5FD), Color(0xFFBFDBFE), Color(0xFFDBEAFE))
                    else -> listOf(Color(0xFF030D14), Color(0xFF0C1F28), Color(0xFF16323B))
                }
                drawRect(
                    brush = Brush.verticalGradient(colors = skyColors, startY = 0f, endY = horizonY),
                    topLeft = Offset.Zero,
                    size = Size(canvasWidth, horizonY)
                )
                drawRect(
                    color = fieldBgColor,
                    topLeft = Offset(0f, horizonY),
                    size = Size(canvasWidth, canvasHeight - horizonY)
                )

                if (showSatelliteOverlay) {
                    withTransform({
                        clipRect(left = 0f, top = horizonY, right = canvasWidth, bottom = canvasHeight)
                        rotate(degrees = -headingDeg.toFloat(), pivot = tractorScreenPos)
                    }) {
                        drawSatelliteTileLayer(anchorScreen = tractorScreenPos, tileAlpha = 0.88f)
                    }
                }

                val horizonGlowColor = when {
                    isOledNight -> Color(0x73FF8C00)
                    isDaylight -> Color(0xFF334155)
                    else -> Color(0x8C00E5FF)
                }
                drawLine(color = horizonGlowColor, start = Offset(0f, horizonY), end = Offset(canvasWidth, horizonY), strokeWidth = 2.0f)

                val eyeOffsetBehind = 32.0
                val focalDist = 54.0
                val nearLimit = -30.0
                val horizonLimit = 1500.0
                val coverageHorizonLimit = 460.0 // Frustum far limit for coverage swath batching
                val coverageLatHalf = 220.0

                fun project3D(worldX: Double, worldY: Double): Offset? {
                    val dx = worldX - tractorPivot.x
                    val dy = worldY - tractorPivot.y
                    val forwardM = dx * sinH + dy * cosH
                    val lateralM = dx * cosH - dy * sinH
                    val eyeDist = forwardM + eyeOffsetBehind
                    if (eyeDist <= 1.0) return null
                    val scale = (focalDist / (eyeDist + focalDist * 0.60)).toFloat()
                    val sx = tractorScreenPos.x + (lateralM * pixelsPerMeter * scale).toFloat()
                    val sy = (tractorScreenPos.y - (forwardM * pixelsPerMeter * scale * 0.90f).toFloat()).coerceAtLeast(horizonY)
                    return if (sy < horizonY - 4f) null else Offset(sx, sy)
                }

                fun project3D(worldPoint: Vec2): Offset? = project3D(worldPoint.x, worldPoint.y)

                fun append3DSegmentToPath(targetPath: Path, x1: Double, y1: Double, x2: Double, y2: Double, maxRange: Double = horizonLimit) {
                    val dx1 = x1 - tractorPivot.x
                    val dy1 = y1 - tractorPivot.y
                    var sFwd = dx1 * sinH + dy1 * cosH
                    var sLat = dx1 * cosH - dy1 * sinH

                    val dx2 = x2 - tractorPivot.x
                    val dy2 = y2 - tractorPivot.y
                    var eFwd = dx2 * sinH + dy2 * cosH
                    var eLat = dx2 * cosH - dy2 * sinH

                    if (sFwd < nearLimit && eFwd < nearLimit) return
                    if (sFwd > maxRange && eFwd > maxRange) return

                    if (sFwd < nearLimit) {
                        val denom = eFwd - sFwd
                        if (abs(denom) < 1e-6) return
                        val t = ((nearLimit - sFwd) / denom).coerceIn(0.0, 1.0)
                        sLat += (eLat - sLat) * t
                        sFwd = nearLimit
                    } else if (sFwd > maxRange) {
                        val denom = eFwd - sFwd
                        if (abs(denom) < 1e-6) return
                        val t = ((maxRange - sFwd) / denom).coerceIn(0.0, 1.0)
                        sLat += (eLat - sLat) * t
                        sFwd = maxRange
                    }

                    if (eFwd < nearLimit) {
                        val denom = sFwd - eFwd
                        if (abs(denom) < 1e-6) return
                        val t = ((nearLimit - eFwd) / denom).coerceIn(0.0, 1.0)
                        eLat += (sLat - eLat) * t
                        eFwd = nearLimit
                    } else if (eFwd > maxRange) {
                        val denom = sFwd - eFwd
                        if (abs(denom) < 1e-6) return
                        val t = ((maxRange - eFwd) / denom).coerceIn(0.0, 1.0)
                        eLat += (sLat - eLat) * t
                        eFwd = maxRange
                    }

                    val eye1 = sFwd + eyeOffsetBehind
                    val eye2 = eFwd + eyeOffsetBehind
                    if (eye1 <= 1.0 || eye2 <= 1.0) return
                    val sc1 = (focalDist / (eye1 + focalDist * 0.60)).toFloat()
                    val sc2 = (focalDist / (eye2 + focalDist * 0.60)).toFloat()
                    val sx1 = tractorScreenPos.x + (sLat * pixelsPerMeter * sc1).toFloat()
                    val sy1 = (tractorScreenPos.y - (sFwd * pixelsPerMeter * sc1 * 0.90f).toFloat()).coerceAtLeast(horizonY)
                    val sx2 = tractorScreenPos.x + (eLat * pixelsPerMeter * sc2).toFloat()
                    val sy2 = (tractorScreenPos.y - (eFwd * pixelsPerMeter * sc2 * 0.90f).toFloat()).coerceAtLeast(horizonY)
                    targetPath.moveTo(sx1, sy1)
                    targetPath.lineTo(sx2, sy2)
                }

                fun draw3DLine(
                    p1: Vec2,
                    p2: Vec2,
                    color: Color,
                    strokeWidth: Float,
                    pathEffect: PathEffect? = null,
                    cap: StrokeCap = StrokeCap.Butt
                ) {
                    val dx1 = p1.x - tractorPivot.x
                    val dy1 = p1.y - tractorPivot.y
                    var sFwd = dx1 * sinH + dy1 * cosH
                    var sLat = dx1 * cosH - dy1 * sinH

                    val dx2 = p2.x - tractorPivot.x
                    val dy2 = p2.y - tractorPivot.y
                    var eFwd = dx2 * sinH + dy2 * cosH
                    var eLat = dx2 * cosH - dy2 * sinH

                    if (sFwd < nearLimit && eFwd < nearLimit) return
                    if (sFwd > horizonLimit && eFwd > horizonLimit) return

                    if (sFwd < nearLimit) {
                        val t = ((nearLimit - sFwd) / (eFwd - sFwd)).coerceIn(0.0, 1.0)
                        sLat += (eLat - sLat) * t; sFwd = nearLimit
                    } else if (sFwd > horizonLimit) {
                        val t = ((horizonLimit - sFwd) / (eFwd - sFwd)).coerceIn(0.0, 1.0)
                        sLat += (eLat - sLat) * t; sFwd = horizonLimit
                    }
                    if (eFwd < nearLimit) {
                        val t = ((nearLimit - eFwd) / (sFwd - eFwd)).coerceIn(0.0, 1.0)
                        eLat += (sLat - eLat) * t; eFwd = nearLimit
                    } else if (eFwd > horizonLimit) {
                        val t = ((horizonLimit - eFwd) / (sFwd - eFwd)).coerceIn(0.0, 1.0)
                        eLat += (sLat - eLat) * t; eFwd = horizonLimit
                    }

                    val eye1 = sFwd + eyeOffsetBehind
                    val eye2 = eFwd + eyeOffsetBehind
                    if (eye1 <= 1.0 || eye2 <= 1.0) return
                    val sc1 = (focalDist / (eye1 + focalDist * 0.60)).toFloat()
                    val sc2 = (focalDist / (eye2 + focalDist * 0.60)).toFloat()
                    val sx1 = tractorScreenPos.x + (sLat * pixelsPerMeter * sc1).toFloat()
                    val sy1 = (tractorScreenPos.y - (sFwd * pixelsPerMeter * sc1 * 0.90f).toFloat()).coerceAtLeast(horizonY)
                    val sx2 = tractorScreenPos.x + (eLat * pixelsPerMeter * sc2).toFloat()
                    val sy2 = (tractorScreenPos.y - (eFwd * pixelsPerMeter * sc2 * 0.90f).toFloat()).coerceAtLeast(horizonY)
                    drawLine(color = color, start = Offset(sx1, sy1), end = Offset(sx2, sy2), strokeWidth = strokeWidth, pathEffect = pathEffect, cap = cap)
                }

                // Zero-allocation Sutherland-Hodgman 3D quad clipper that appends directly into a batched Path!
                fun append3DQuadToPath(
                    targetPath: Path,
                    p1x: Double, p1y: Double,
                    p2x: Double, p2y: Double,
                    p3x: Double, p3y: Double,
                    p4x: Double, p4y: Double
                ) {
                    val inX = scratch.quadInX
                    val inY = scratch.quadInY
                    inX[0] = p1x; inY[0] = p1y
                    inX[1] = p2x; inY[1] = p2y
                    inX[2] = p3x; inY[2] = p3y
                    inX[3] = p4x; inY[3] = p4y

                    var allBehind = true
                    var allBeyond = true
                    for (i in 0 until 4) {
                        val fwd = (inX[i] - tractorPivot.x) * sinH + (inY[i] - tractorPivot.y) * cosH
                        if (fwd >= nearLimit) allBehind = false
                        if (fwd <= coverageHorizonLimit) allBeyond = false
                    }
                    if (allBehind || allBeyond) return

                    val outX = scratch.clipX
                    val outY = scratch.clipY
                    var outCount = 0

                    for (i in 0 until 4) {
                        val nx = if (i == 3) 0 else i + 1
                        val cx = inX[i]; val cy = inY[i]
                        val ex = inX[nx]; val ey = inY[nx]
                        val curFwd = (cx - tractorPivot.x) * sinH + (cy - tractorPivot.y) * cosH
                        val nextFwd = (ex - tractorPivot.x) * sinH + (ey - tractorPivot.y) * cosH
                        val curIn = curFwd >= nearLimit && curFwd <= coverageHorizonLimit
                        val nextIn = nextFwd >= nearLimit && nextFwd <= coverageHorizonLimit

                        if (curIn && nextIn) {
                            outX[outCount] = ex; outY[outCount] = ey; outCount++
                        } else if (curIn && !nextIn) {
                            val limit = if (nextFwd < nearLimit) nearLimit else coverageHorizonLimit
                            val denom = nextFwd - curFwd
                            if (abs(denom) > 1e-6) {
                                val t = ((limit - curFwd) / denom).coerceIn(0.0, 1.0)
                                outX[outCount] = cx + (ex - cx) * t
                                outY[outCount] = cy + (ey - cy) * t
                                outCount++
                            }
                        } else if (!curIn && nextIn) {
                            val limit = if (curFwd < nearLimit) nearLimit else coverageHorizonLimit
                            val denom = nextFwd - curFwd
                            if (abs(denom) > 1e-6) {
                                val t = ((limit - curFwd) / denom).coerceIn(0.0, 1.0)
                                outX[outCount] = cx + (ex - cx) * t
                                outY[outCount] = cy + (ey - cy) * t
                                outCount++
                            }
                            outX[outCount] = ex; outY[outCount] = ey; outCount++
                        }
                    }

                    if (outCount < 3) return

                    val px = scratch.projX
                    val py = scratch.projY
                    var pCount = 0
                    for (i in 0 until outCount) {
                        val dx = outX[i] - tractorPivot.x
                        val dy = outY[i] - tractorPivot.y
                        val forwardM = dx * sinH + dy * cosH
                        val lateralM = dx * cosH - dy * sinH
                        val eyeDist = forwardM + eyeOffsetBehind
                        if (eyeDist <= 1.0) continue
                        val sc = (focalDist / (eyeDist + focalDist * 0.60)).toFloat()
                        val sx = tractorScreenPos.x + (lateralM * pixelsPerMeter * sc).toFloat()
                        val sy = (tractorScreenPos.y - (forwardM * pixelsPerMeter * sc * 0.90f).toFloat()).coerceAtLeast(horizonY)
                        if (sy >= horizonY - 4f) {
                            px[pCount] = sx; py[pCount] = sy; pCount++
                        }
                    }

                    if (pCount >= 3) {
                        targetPath.moveTo(px[0], py[0])
                        for (j in 1 until pCount) {
                            targetPath.lineTo(px[j], py[j])
                        }
                        targetPath.close()
                    }
                }

                // --- 2. 3D FIELD PERSPECTIVE GRID ---
                val gridStepM = 15.0
                val maxFwdM = 540.0
                val maxLatM = 180.0
                val majorGridAlpha = if (isDaylight) 0.55f else 0.45f
                val minorGridAlpha = if (isDaylight) 0.28f else 0.20f

                var lat = -maxLatM
                while (lat <= maxLatM) {
                    val isMajor = abs(lat % (gridStepM * 3)) < 0.5
                    draw3DLine(
                        tractorPivot + (rightVec * lat) - (fwdVec * 20.0),
                        tractorPivot + (rightVec * lat) + (fwdVec * maxFwdM),
                        gridLineColor.copy(alpha = if (isMajor) majorGridAlpha else minorGridAlpha),
                        if (isMajor) 1.5f else 0.85f
                    )
                    lat += gridStepM
                }
                var fwd = -20.0
                while (fwd <= maxFwdM) {
                    val isMajor = abs(fwd % (gridStepM * 3)) < 0.5
                    draw3DLine(
                        tractorPivot - (rightVec * maxLatM) + (fwdVec * fwd),
                        tractorPivot + (rightVec * maxLatM) + (fwdVec * fwd),
                        gridLineColor.copy(alpha = if (isMajor) majorGridAlpha else minorGridAlpha),
                        if (isMajor) 1.5f else 0.85f
                    )
                    fwd += gridStepM
                }

                // --- 3. 3D TOPOGRAPHIC CONTOURS (Batched into 2 reusable Paths — zero per-line draw overhead) ---
                if (showTopographyMap && liveTerrainSquare != null) {
                    val nw = Vec2(liveTerrainSquare.minX, liveTerrainSquare.maxY)
                    val ne = Vec2(liveTerrainSquare.maxX, liveTerrainSquare.maxY)
                    val se = Vec2(liveTerrainSquare.maxX, liveTerrainSquare.minY)
                    val sw = Vec2(liveTerrainSquare.minX, liveTerrainSquare.minY)
                    val boxColor = if (isDaylight) Color(0xAA0369A1) else Color(0xB300E5FF)
                    draw3DLine(nw, ne, boxColor, 2f, scratch.dashTopoBox)
                    draw3DLine(ne, se, boxColor, 2f, scratch.dashTopoBox)
                    draw3DLine(se, sw, boxColor, 2f, scratch.dashTopoBox)
                    draw3DLine(sw, nw, boxColor, 2f, scratch.dashTopoBox)

                    scratch.indexContourPath.reset()
                    scratch.minorContourPath.reset()
                    val contours = liveTerrainSquare.contours
                    for (ci in contours.indices) {
                        val c = contours[ci]
                        val pts = c.points
                        if (pts.size < 2) continue
                        val target = if (c.isIndexContour) scratch.indexContourPath else scratch.minorContourPath
                        for (i in 0 until pts.size - 1) {
                            append3DSegmentToPath(target, pts[i].x, pts[i].y, pts[i + 1].x, pts[i + 1].y, maxRange = 650.0)
                        }
                    }
                    drawPath(path = scratch.minorContourPath, color = minorContourColor, style = scratch.strokeMinorContour)
                    drawPath(path = scratch.indexContourPath, color = indexContourColor, style = scratch.strokeIndexContour)
                }

                // --- 4. 3D APPLIED COVERAGE (Chunked Frustum Culling + Single Batched GPU DrawCall) ---
                scratch.coverageBatchPath.reset()
                var lastRenderedSeg: AppliedSwathSegment? = null
                if (coverageChunkStore != null) {
                    coverageChunkStore.forEachVisibleIn3DFrustum(
                        pivotX = tractorPivot.x,
                        pivotY = tractorPivot.y,
                        sinH = sinH,
                        cosH = cosH,
                        nearLimit = nearLimit,
                        farLimit = coverageHorizonLimit,
                        maxLateral = coverageLatHalf
                    ) { seg ->
                        append3DQuadToPath(
                            scratch.coverageBatchPath,
                            seg.leftStart.x, seg.leftStart.y,
                            seg.rightStart.x, seg.rightStart.y,
                            seg.rightEnd.x, seg.rightEnd.y,
                            seg.leftEnd.x, seg.leftEnd.y
                        )
                    }
                    lastRenderedSeg = coverageChunkStore.lastSegment
                } else {
                    for (i in appliedSegments.indices) {
                        val seg = appliedSegments[i]
                        append3DQuadToPath(
                            scratch.coverageBatchPath,
                            seg.leftStart.x, seg.leftStart.y,
                            seg.rightStart.x, seg.rightStart.y,
                            seg.rightEnd.x, seg.rightEnd.y,
                            seg.leftEnd.x, seg.leftEnd.y
                        )
                    }
                    lastRenderedSeg = appliedSegments.lastOrNull()
                }

                // Smooth live brush bridge in 3D
                val isHitchLowered3D = threePointState == ThreePointState.LOWERED || threePointState == ThreePointState.FLOAT
                val isWorking3D = isHitchLowered3D && (liveVehicle.isAutoSteerEngaged || implementConfig.isMasterActive) && tempPointA == null
                if (isWorking3D && abs(liveVehicle.speedKmh) > 0.15 && lastRenderedSeg != null) {
                    val toolbarDistM = vehicleConfig.hitchLength - implementConfig.offsetBehindTractor
                    val liveCenter3D = tractorPivot + (fwdVec * toolbarDistM)
                    val halfBoom3D = implementConfig.toolWidth * 0.5
                    val liveBoomLeft3D = liveCenter3D - (rightVec * halfBoom3D)
                    val liveBoomRight3D = liveCenter3D + (rightVec * halfBoom3D)
                    append3DQuadToPath(
                        scratch.coverageBatchPath,
                        lastRenderedSeg.leftEnd.x, lastRenderedSeg.leftEnd.y,
                        lastRenderedSeg.rightEnd.x, lastRenderedSeg.rightEnd.y,
                        liveBoomRight3D.x, liveBoomRight3D.y,
                        liveBoomLeft3D.x, liveBoomLeft3D.y
                    )
                }

                if (!scratch.coverageBatchPath.isEmpty) {
                    drawPath(path = scratch.coverageBatchPath, color = baseCoverageColor)
                    drawPath(path = scratch.coverageBatchPath, color = coverageBorderColor, style = scratch.strokeCoverageEdge)
                }

                // Helper for 3D Surveyor Flagpole Marker (zero-alloc reusable scratchPath)
                fun draw3DSurveyorFlag(worldPos: Vec2, label: String, flagColor: Color) {
                    val sp = project3D(worldPos)
                    if (sp != null) {
                        val basePulseRad = 18.dp.toPx() * pulseScale
                        drawCircle(color = flagColor.copy(alpha = pulseAlpha * 0.5f), radius = basePulseRad, center = sp)
                        drawCircle(color = flagColor, radius = 9.dp.toPx(), center = sp)
                        drawCircle(color = Color(0xFF0F172A), radius = 6.dp.toPx(), center = sp)

                        val arm = 15.dp.toPx()
                        drawLine(color = flagColor, start = Offset(sp.x - arm, sp.y), end = Offset(sp.x + arm, sp.y), strokeWidth = 2.0f)
                        drawLine(color = flagColor, start = Offset(sp.x, sp.y - arm), end = Offset(sp.x, sp.y + arm), strokeWidth = 2.0f)

                        val poleHeight = 52.dp.toPx()
                        val poleTop = Offset(sp.x, sp.y - poleHeight)
                        drawLine(color = Color.White, start = sp, end = poleTop, strokeWidth = 3.5f, cap = StrokeCap.Round)
                        drawLine(color = Color(0xFF0F172A), start = sp, end = poleTop, strokeWidth = 1.5f, cap = StrokeCap.Round)

                        val flagW = 40.dp.toPx()
                        val flagH = 25.dp.toPx()
                        scratch.scratchPath.reset()
                        scratch.scratchPath.moveTo(poleTop.x, poleTop.y)
                        scratch.scratchPath.lineTo(poleTop.x + flagW, poleTop.y + flagH * 0.5f)
                        scratch.scratchPath.lineTo(poleTop.x, poleTop.y + flagH)
                        scratch.scratchPath.close()
                        drawPath(path = scratch.scratchPath, color = flagColor)
                        drawPath(path = scratch.scratchPath, color = Color.White, style = scratch.strokeMedium)

                        textPaint.color = android.graphics.Color.WHITE
                        textPaint.textSize = 22f
                        drawContext.canvas.nativeCanvas.drawText(label, poleTop.x + 14.dp.toPx(), poleTop.y + 18.dp.toPx(), textPaint)
                    } else if (tempPointA != null) {
                        val distToTractor = worldPos.distanceTo(tractorPivot)
                        val distFt = distToTractor * 3.28084
                        val deltaX = worldPos.x - tractorPivot.x
                        val deltaY = worldPos.y - tractorPivot.y
                        val bearingDeg = (Math.toDegrees(atan2(deltaX, deltaY)) + 360.0) % 360.0
                        val hudText = String.format(Locale.US, "POINT %s [LOCKED]: %.1fm (%.0fft) | %03.0f° BEHIND", label, distToTractor, distFt, bearingDeg)
                        textPaint.textSize = 20f
                        val textW = textPaint.measureText(hudText)
                        val beaconY = (canvasHeight * 0.72f).coerceAtMost(canvasHeight - 115.dp.toPx())
                        drawRoundRect(
                            color = Color(0xEE0F172A),
                            topLeft = Offset(canvasWidth * 0.5f - textW * 0.5f - 16f, beaconY - 32f),
                            size = Size(textW + 32f, 44f),
                            cornerRadius = CornerRadius(12f, 12f)
                        )
                        drawRoundRect(
                            color = flagColor,
                            topLeft = Offset(canvasWidth * 0.5f - textW * 0.5f - 16f, beaconY - 32f),
                            size = Size(textW + 32f, 44f),
                            cornerRadius = CornerRadius(12f, 12f),
                            style = scratch.strokeMedium
                        )
                        textPaint.color = if (label == "A") android.graphics.Color.CYAN else android.graphics.Color.YELLOW
                        drawContext.canvas.nativeCanvas.drawText(hudText, canvasWidth * 0.5f, beaconY - 3f, textPaint)
                    }
                }

                // --- 5. 3D AB GUIDANCE LINES & PARALLEL SWATHS (High-Contrast Halo + Vivid Core) ---
                if (currentABLine != null) {
                    val swathW = implementConfig.swathWidth
                    val activeIdx = liveVehicle.activeSwathIndex
                    val lineDir = currentABLine.dirVector
                    val lineNormal = currentABLine.normalVector
                    val lineLengthMeters = 2000.0

                    for (passOffset in -10..10) {
                        val passIndex = activeIdx + passOffset
                        val swathOrigin = currentABLine.aLocal + (lineNormal * (passIndex * swathW))
                        val pStart = swathOrigin - (lineDir * lineLengthMeters)
                        val pEnd = swathOrigin + (lineDir * lineLengthMeters)

                        if (passOffset == 0) {
                            // Crisp high-contrast halo + bold active A-B line
                            draw3DLine(pStart, pEnd, activeSwathHaloColor, 10.5f, cap = StrokeCap.Round)
                            draw3DLine(pStart, pEnd, activeSwathColor, 5.2f, cap = StrokeCap.Round)
                        } else {
                            draw3DLine(pStart, pEnd, parallelSwathColor, 2.2f, scratch.dashParallel)
                        }
                    }

                    draw3DSurveyorFlag(currentABLine.aLocal, "A", Color(0xFF00E5FF))
                    draw3DSurveyorFlag(currentABLine.bLocal, "B", Color(0xFFF59E0B))
                }

                // --- 5.5 3D DASHED HEADLAND U-TURN PREVIEW ARC ---
                if (liveUTurnPath.size >= 2) {
                    scratch.uTurnPath.reset()
                    for (i in 0 until liveUTurnPath.size - 1) {
                        append3DSegmentToPath(
                            scratch.uTurnPath,
                            liveUTurnPath[i].x, liveUTurnPath[i].y,
                            liveUTurnPath[i + 1].x, liveUTurnPath[i + 1].y,
                            maxRange = 450.0
                        )
                    }
                    if (!scratch.uTurnPath.isEmpty) {
                        drawPath(path = scratch.uTurnPath, color = uTurnHaloColor, style = scratch.strokeTurnHalo)
                        drawPath(path = scratch.uTurnPath, color = uTurnArcColor, style = scratch.strokeTurnDash)
                        val endPt = project3D(liveUTurnPath.last())
                        if (endPt != null) {
                            drawCircle(color = uTurnHaloColor, radius = 8f, center = endPt)
                            drawCircle(color = uTurnArcColor, radius = 5f, center = endPt)
                        }
                    }
                }

                // --- 6. Field & Headland Boundaries in 3D ---
                if (fieldBoundary.size >= 2) {
                    for (i in fieldBoundary.indices) {
                        val nextIdx = (i + 1) % fieldBoundary.size
                        draw3DLine(fieldBoundary[i], fieldBoundary[nextIdx], boundaryColor, 3.2f)
                    }
                }
                if (headlandBoundary.size >= 2) {
                    for (i in headlandBoundary.indices) {
                        val nextIdx = (i + 1) % headlandBoundary.size
                        draw3DLine(headlandBoundary[i], headlandBoundary[nextIdx], headlandColor, 2.2f, scratch.dashHeadland)
                    }
                }

                // 3D Obstacles
                for (i in obstacles.indices) {
                    val obs = obstacles[i]
                    val sp = project3D(obs.localPos) ?: continue
                    drawCircle(color = obs.type.displayColor.copy(alpha = 0.28f), radius = 14.dp.toPx(), center = sp)
                    drawCircle(color = obs.type.displayColor, radius = 6.dp.toPx(), center = sp)
                    drawCircle(color = Color.Black, radius = 3.dp.toPx(), center = sp)
                }

                // --- 7. 3D Tractor & Implement Model (Steered Front Wheels + 3-Point Hitch State) ---
                drawTractorIn3D(
                    center = tractorScreenPos,
                    pixelsPerMeter = pixelsPerMeter,
                    vehicleConfig = vehicleConfig,
                    implementConfig = implementConfig,
                    actualSteerDeg = liveVehicle.actualSteerAngleDeg,
                    isAutoSteerEngaged = liveVehicle.isAutoSteerEngaged,
                    isMasterActive = implementConfig.isMasterActive,
                    threePointState = threePointState,
                    sectionStates = liveVehicle.sectionStates,
                    themeMode = themeMode,
                    scratch = scratch
                )

                // --- 8. POINT A LIVE TRACKING IN 3D ---
                if (tempPointA != null) {
                    val distAtoTractor = tempPointA.distanceTo(tractorPivot)
                    val distFt = distAtoTractor * 3.28084
                    val deltaX = tractorPivot.x - tempPointA.x
                    val deltaY = tractorPivot.y - tempPointA.y
                    val bearingDeg = if (distAtoTractor >= 0.3) {
                        (Math.toDegrees(atan2(deltaX, deltaY)) + 360.0) % 360.0
                    } else {
                        (headingDeg + 360.0) % 360.0
                    }
                    val swathDir = if (distAtoTractor >= 0.8) (tractorPivot - tempPointA).normalized() else Vec2(sinH, cosH)
                    val swathNorm = Vec2(swathDir.y, -swathDir.x)
                    val safeBottomY = (canvasHeight - 140.dp.toPx()).coerceAtLeast(tractorScreenPos.y + 45.dp.toPx())
                    val fwdHorizon = tractorPivot + (swathDir * 1200.0)
                    val halfSwath = implementConfig.toolWidth * 0.5

                    draw3DLine(tempPointA + (swathNorm * halfSwath), fwdHorizon + (swathNorm * halfSwath), Color(0xA638BDF8), 2.4f, scratch.dashCorridor)
                    draw3DLine(tempPointA - (swathNorm * halfSwath), fwdHorizon - (swathNorm * halfSwath), Color(0xA638BDF8), 2.4f, scratch.dashCorridor)
                    for (passOffset in -4..4) {
                        if (passOffset == 0) continue
                        val pOffset = swathNorm * (passOffset * implementConfig.swathWidth)
                        draw3DLine(tempPointA + pOffset, fwdHorizon + pOffset, Color(0x730284C7), 1.8f, scratch.dashParallel)
                    }

                    if (liveAbTrail.size >= 2) {
                        for (i in 0 until liveAbTrail.size - 1) {
                            draw3DLine(liveAbTrail[i], liveAbTrail[i + 1], Color(0xE600E5FF), 5.0f)
                        }
                    }

                    draw3DLine(tempPointA, fwdHorizon, Color(0x6100E5FF), 13.0f)
                    draw3DLine(tempPointA, fwdHorizon, Color(0xFF00E5FF), 5.5f)

                    val spHorizon = project3D(fwdHorizon) ?: Offset(canvasWidth * 0.5f, horizonY)
                    drawLine(Color(0x6600E5FF), tractorScreenPos, spHorizon, strokeWidth = 10.0f, cap = StrokeCap.Round)
                    drawLine(Color(0xFF00E5FF), tractorScreenPos, spHorizon, strokeWidth = 4.2f, cap = StrokeCap.Round)

                    val spA = project3D(tempPointA)
                    if (spA != null && spA.y in horizonY..safeBottomY) {
                        drawLine(Color(0x7300E5FF), spA, tractorScreenPos, strokeWidth = 11.0f, cap = StrokeCap.Round)
                        drawLine(Color(0xFF00E5FF), spA, tractorScreenPos, strokeWidth = 4.8f, cap = StrokeCap.Round)
                        draw3DSurveyorFlag(tempPointA, "A", Color(0xFF00E5FF))
                    } else {
                        val latA = (tempPointA.x - tractorPivot.x) * cosH - (tempPointA.y - tractorPivot.y) * sinH
                        val indX = (canvasWidth * 0.5f + (latA * pixelsPerMeter * 0.4f).toFloat()).coerceIn(140f, canvasWidth - 140f)
                        val anchorPos = Offset(indX, safeBottomY)
                        drawLine(Color(0x7300E5FF), anchorPos, tractorScreenPos, strokeWidth = 11.0f, cap = StrokeCap.Round)
                        drawLine(Color(0xFF00E5FF), anchorPos, tractorScreenPos, strokeWidth = 4.8f, cap = StrokeCap.Round)
                        drawCircle(Color(0xFF00E5FF).copy(alpha = pulseAlpha * 0.45f), 18.dp.toPx() * pulseScale, anchorPos)
                        drawCircle(Color(0xFF00E5FF), 8.dp.toPx(), anchorPos)
                    }

                    val tractorIconCenter = Offset(tractorScreenPos.x, tractorScreenPos.y - 68.dp.toPx())
                    drawLine(Color(0xFF00E5FF), tractorIconCenter, tractorScreenPos, strokeWidth = 3.0f, cap = StrokeCap.Round)
                    drawCircle(Color(0xFF00E5FF).copy(alpha = pulseAlpha * 0.40f), 24.dp.toPx() * pulseScale, tractorIconCenter)
                    drawCircle(Color(0xFF00E5FF), 17.dp.toPx(), tractorIconCenter)
                    drawCircle(Color(0xFF0F172A), 13.dp.toPx(), tractorIconCenter)
                    textPaint.color = android.graphics.Color.CYAN
                    textPaint.textSize = 32f
                    drawContext.canvas.nativeCanvas.drawText("A", tractorIconCenter.x, tractorIconCenter.y + 6.dp.toPx(), textPaint)

                    val badgeText = String.format(Locale.US, "A-B TRACKING • %.1f m (%.0f ft) | %03.0f°", distAtoTractor, distFt, bearingDeg)
                    textPaint.textSize = 18f
                    val textW = textPaint.measureText(badgeText)
                    val badgeTop = tractorIconCenter.y - 34.dp.toPx()
                    drawRoundRect(Color(0xF50F172A), Offset(tractorIconCenter.x - textW * 0.5f - 14f, badgeTop - 16f), Size(textW + 28f, 34f), CornerRadius(12f, 12f))
                    drawRoundRect(Color(0xFF00E5FF), Offset(tractorIconCenter.x - textW * 0.5f - 14f, badgeTop - 16f), Size(textW + 28f, 34f), CornerRadius(12f, 12f), style = scratch.strokeMedium)
                    textPaint.color = android.graphics.Color.WHITE
                    drawContext.canvas.nativeCanvas.drawText(badgeText, tractorIconCenter.x, badgeTop + 7f, textPaint)
                }

            } else {
                // =========================================================================
                // --- 2D OVERHEAD TOP-DOWN MODES (BIRD EYE 2D, NORTH UP, FREE PAN) ---
                // =========================================================================
                val isHeadUp = (cameraViewMode == CameraViewMode.BIRD_EYE_2D)
                val tractorOffsetY = if (isHeadUp) canvasHeight * 0.68f else (canvasHeight * 0.5f)
                val centerScreen = Offset(canvasWidth * 0.5f + panOffset.x, tractorOffsetY + panOffset.y)
                val canvasRotation = if (isHeadUp) -headingDeg.toFloat() else 0f

                drawRect(color = fieldBgColor)

                withTransform({
                    rotate(degrees = canvasRotation, pivot = centerScreen)
                }) {
                    fun worldToScreenX(wx: Double): Float = centerScreen.x + ((wx - tractorPivot.x).toFloat() * pixelsPerMeter)
                    fun worldToScreenY(wy: Double): Float = centerScreen.y - ((wy - tractorPivot.y).toFloat() * pixelsPerMeter)
                    fun worldToScreen(worldPoint: Vec2): Offset = Offset(worldToScreenX(worldPoint.x), worldToScreenY(worldPoint.y))

                    if (showSatelliteOverlay) {
                        drawSatelliteTileLayer(anchorScreen = centerScreen, tileAlpha = 1.0f)
                    }

                    // 1. 2D Field Grid & Visible World Bounds (for 100+ acre viewport culling)
                    val screenDiagPx = sqrt((canvasWidth * canvasWidth + canvasHeight * canvasHeight).toDouble())
                    val viewExtentM = ((screenDiagPx * 0.5 + max(abs(panOffset.x.toDouble()), abs(panOffset.y.toDouble()))) / pixelsPerMeter.toDouble()) + 35.0
                    val visMinX = tractorPivot.x - viewExtentM
                    val visMaxX = tractorPivot.x + viewExtentM
                    val visMinY = tractorPivot.y - viewExtentM
                    val visMaxY = tractorPivot.y + viewExtentM

                    val gridStepM = 20.0
                    val startX = floor(visMinX / gridStepM) * gridStepM
                    val endX = ceil(visMaxX / gridStepM) * gridStepM
                    val startY = floor(visMinY / gridStepM) * gridStepM
                    val endY = ceil(visMaxY / gridStepM) * gridStepM
                    val gridAlpha = if (isDaylight) 0.42f else 0.32f

                    var currX = startX
                    while (currX <= endX) {
                        val sx = worldToScreenX(currX)
                        drawLine(gridLineColor.copy(alpha = gridAlpha), Offset(sx, worldToScreenY(startY)), Offset(sx, worldToScreenY(endY)), strokeWidth = 1f)
                        currX += gridStepM
                    }
                    var currY = startY
                    while (currY <= endY) {
                        val sy = worldToScreenY(currY)
                        drawLine(gridLineColor.copy(alpha = gridAlpha), Offset(worldToScreenX(startX), sy), Offset(worldToScreenX(endX), sy), strokeWidth = 1f)
                        currY += gridStepM
                    }

                    // 2. 2D Topographic Contours (Batched into 2 reusable Paths with viewport culling)
                    if (showTopographyMap && liveTerrainSquare != null) {
                        scratch.scratchPath.reset()
                        scratch.scratchPath.moveTo(worldToScreenX(liveTerrainSquare.minX), worldToScreenY(liveTerrainSquare.maxY))
                        scratch.scratchPath.lineTo(worldToScreenX(liveTerrainSquare.maxX), worldToScreenY(liveTerrainSquare.maxY))
                        scratch.scratchPath.lineTo(worldToScreenX(liveTerrainSquare.maxX), worldToScreenY(liveTerrainSquare.minY))
                        scratch.scratchPath.lineTo(worldToScreenX(liveTerrainSquare.minX), worldToScreenY(liveTerrainSquare.minY))
                        scratch.scratchPath.close()
                        drawPath(path = scratch.scratchPath, color = if (isDaylight) Color(0xAA0369A1) else Color(0xB300E5FF), style = scratch.strokeTopoBox)

                        scratch.indexContourPath.reset()
                        scratch.minorContourPath.reset()
                        val contours = liveTerrainSquare.contours
                        for (ci in contours.indices) {
                            val c = contours[ci]
                            val pts = c.points
                            if (pts.size < 2) continue
                            val target = if (c.isIndexContour) scratch.indexContourPath else scratch.minorContourPath
                            for (i in 0 until pts.size - 1) {
                                val a = pts[i]; val b = pts[i + 1]
                                if ((a.x < visMinX && b.x < visMinX) || (a.x > visMaxX && b.x > visMaxX) ||
                                    (a.y < visMinY && b.y < visMinY) || (a.y > visMaxY && b.y > visMaxY)) continue
                                target.moveTo(worldToScreenX(a.x), worldToScreenY(a.y))
                                target.lineTo(worldToScreenX(b.x), worldToScreenY(b.y))
                            }
                        }
                        drawPath(path = scratch.minorContourPath, color = minorContourColor, style = scratch.strokeMinorContour)
                        drawPath(path = scratch.indexContourPath, color = indexContourColor, style = scratch.strokeIndexContour)
                    }

                    // 3. 2D Applied Coverage Swaths (Chunked Viewport Culling + Single Batched Path)
                    scratch.coverageBatchPath.reset()
                    var lastSeg2D: AppliedSwathSegment? = null
                    if (coverageChunkStore != null) {
                        coverageChunkStore.forEachVisibleIn2D(visMinX, visMaxX, visMinY, visMaxY) { seg ->
                            scratch.coverageBatchPath.moveTo(worldToScreenX(seg.leftStart.x), worldToScreenY(seg.leftStart.y))
                            scratch.coverageBatchPath.lineTo(worldToScreenX(seg.rightStart.x), worldToScreenY(seg.rightStart.y))
                            scratch.coverageBatchPath.lineTo(worldToScreenX(seg.rightEnd.x), worldToScreenY(seg.rightEnd.y))
                            scratch.coverageBatchPath.lineTo(worldToScreenX(seg.leftEnd.x), worldToScreenY(seg.leftEnd.y))
                            scratch.coverageBatchPath.close()
                        }
                        lastSeg2D = coverageChunkStore.lastSegment
                    } else {
                        for (i in appliedSegments.indices) {
                            val seg = appliedSegments[i]
                            if (seg.center.x + seg.radius < visMinX || seg.center.x - seg.radius > visMaxX ||
                                seg.center.y + seg.radius < visMinY || seg.center.y - seg.radius > visMaxY) continue
                            scratch.coverageBatchPath.moveTo(worldToScreenX(seg.leftStart.x), worldToScreenY(seg.leftStart.y))
                            scratch.coverageBatchPath.lineTo(worldToScreenX(seg.rightStart.x), worldToScreenY(seg.rightStart.y))
                            scratch.coverageBatchPath.lineTo(worldToScreenX(seg.rightEnd.x), worldToScreenY(seg.rightEnd.y))
                            scratch.coverageBatchPath.lineTo(worldToScreenX(seg.leftEnd.x), worldToScreenY(seg.leftEnd.y))
                            scratch.coverageBatchPath.close()
                        }
                        lastSeg2D = appliedSegments.lastOrNull()
                    }

                    // Live brush bridge in 2D
                    val isHitchLowered2D = threePointState == ThreePointState.LOWERED || threePointState == ThreePointState.FLOAT
                    val isWorking2D = isHitchLowered2D && (liveVehicle.isAutoSteerEngaged || implementConfig.isMasterActive) && tempPointA == null
                    if (isWorking2D && abs(liveVehicle.speedKmh) > 0.15 && lastSeg2D != null) {
                        val fwd2D = Vec2(sinH, cosH)
                        val right2D = fwd2D.perpClockwise()
                        val toolbarDistM = vehicleConfig.hitchLength - implementConfig.offsetBehindTractor
                        val liveCenter2D = tractorPivot + (fwd2D * toolbarDistM)
                        val halfBoom2D = implementConfig.toolWidth * 0.5
                        val liveBoomLeft2D = liveCenter2D - (right2D * halfBoom2D)
                        val liveBoomRight2D = liveCenter2D + (right2D * halfBoom2D)
                        scratch.coverageBatchPath.moveTo(worldToScreenX(lastSeg2D.leftEnd.x), worldToScreenY(lastSeg2D.leftEnd.y))
                        scratch.coverageBatchPath.lineTo(worldToScreenX(lastSeg2D.rightEnd.x), worldToScreenY(lastSeg2D.rightEnd.y))
                        scratch.coverageBatchPath.lineTo(worldToScreenX(liveBoomRight2D.x), worldToScreenY(liveBoomRight2D.y))
                        scratch.coverageBatchPath.lineTo(worldToScreenX(liveBoomLeft2D.x), worldToScreenY(liveBoomLeft2D.y))
                        scratch.coverageBatchPath.close()
                    }

                    if (!scratch.coverageBatchPath.isEmpty) {
                        drawPath(path = scratch.coverageBatchPath, color = baseCoverageColor)
                        drawPath(path = scratch.coverageBatchPath, color = coverageBorderColor, style = scratch.strokeCoverageEdge)
                    }

                    // Helper for 2D Surveyor Flagpole Marker (zero-alloc reusable scratchPath)
                    fun draw2DSurveyorFlag(worldPos: Vec2, label: String, flagColor: Color) {
                        val sp = worldToScreen(worldPos)
                        drawCircle(color = flagColor.copy(alpha = pulseAlpha * 0.5f), radius = 16.dp.toPx() * pulseScale, center = sp)
                        drawCircle(color = flagColor, radius = 8.dp.toPx(), center = sp)
                        drawCircle(color = Color(0xFF0F172A), radius = 5.dp.toPx(), center = sp)

                        val arm = 14.dp.toPx()
                        drawLine(flagColor, Offset(sp.x - arm, sp.y), Offset(sp.x + arm, sp.y), strokeWidth = 2.0f)
                        drawLine(flagColor, Offset(sp.x, sp.y - arm), Offset(sp.x, sp.y + arm), strokeWidth = 2.0f)

                        val poleHeight = 44.dp.toPx()
                        val poleTop = Offset(sp.x, sp.y - poleHeight)
                        drawLine(Color.White, sp, poleTop, strokeWidth = 3.0f, cap = StrokeCap.Round)
                        drawLine(Color(0xFF0F172A), sp, poleTop, strokeWidth = 1.2f, cap = StrokeCap.Round)

                        val flagW = 36.dp.toPx()
                        val flagH = 23.dp.toPx()
                        scratch.scratchPath.reset()
                        scratch.scratchPath.moveTo(poleTop.x, poleTop.y)
                        scratch.scratchPath.lineTo(poleTop.x + flagW, poleTop.y + flagH * 0.5f)
                        scratch.scratchPath.lineTo(poleTop.x, poleTop.y + flagH)
                        scratch.scratchPath.close()
                        drawPath(path = scratch.scratchPath, color = flagColor)
                        drawPath(path = scratch.scratchPath, color = Color.White, style = scratch.strokeMedium)

                        val textOffsetXPx = 13.dp.toPx()
                        val textOffsetYPx = 16.dp.toPx()
                        withTransform({
                            rotate(degrees = -canvasRotation, pivot = Offset(poleTop.x + textOffsetXPx, poleTop.y + flagH * 0.5f))
                        }) {
                            textPaint.color = android.graphics.Color.WHITE
                            textPaint.textSize = 21f
                            drawContext.canvas.nativeCanvas.drawText(label, poleTop.x + textOffsetXPx, poleTop.y + textOffsetYPx, textPaint)
                        }
                    }

                    // 4. Point A Live Tracking in 2D
                    if (tempPointA != null) {
                        draw2DSurveyorFlag(tempPointA, "A", Color(0xFF00E5FF))
                        val distA = tempPointA.distanceTo(tractorPivot)
                        val distFt = distA * 3.28084
                        val bearingDeg = (Math.toDegrees(atan2(tractorPivot.x - tempPointA.x, tractorPivot.y - tempPointA.y)) + 360.0) % 360.0
                        val swathDir2D = if (distA >= 0.8) (tractorPivot - tempPointA).normalized() else Vec2(sinH, cosH)
                        val swathNorm2D = Vec2(swathDir2D.y, -swathDir2D.x)
                        val lineStart2D = tempPointA - (swathDir2D * 100.0)
                        val lineEnd2D = tractorPivot + (swathDir2D * 1000.0)

                        val spStart = worldToScreen(lineStart2D)
                        val spEnd = worldToScreen(lineEnd2D)
                        val spTractor = worldToScreen(tractorPivot)
                        drawLine(Color(0x5900E5FF), spStart, spEnd, strokeWidth = 10.0f)
                        drawLine(Color(0xFF00E5FF), spStart, spEnd, strokeWidth = 4.5f)

                        if (liveAbTrail.size >= 2) {
                            for (i in 0 until liveAbTrail.size - 1) {
                                drawLine(Color(0xD900E5FF), worldToScreen(liveAbTrail[i]), worldToScreen(liveAbTrail[i + 1]), strokeWidth = 4.0f)
                            }
                        }

                        val halfW = implementConfig.toolWidth * 0.5
                        drawLine(Color(0x8C38BDF8), worldToScreen(lineStart2D + (swathNorm2D * halfW)), worldToScreen(lineEnd2D + (swathNorm2D * halfW)), strokeWidth = 2.0f, pathEffect = scratch.dashCorridor)
                        drawLine(Color(0x8C38BDF8), worldToScreen(lineStart2D - (swathNorm2D * halfW)), worldToScreen(lineEnd2D - (swathNorm2D * halfW)), strokeWidth = 2.0f, pathEffect = scratch.dashCorridor)

                        for (passOffset in -4..4) {
                            if (passOffset == 0) continue
                            val pOffset = swathNorm2D * (passOffset * implementConfig.swathWidth)
                            drawLine(Color(0x660284C7), worldToScreen(lineStart2D + pOffset), worldToScreen(lineEnd2D + pOffset), strokeWidth = 1.8f, pathEffect = scratch.dashParallel)
                        }

                        val tractorIconCenter2D = Offset(spTractor.x, spTractor.y - 46.dp.toPx())
                        drawLine(Color(0xFF00E5FF), Offset(spTractor.x, spTractor.y - 30.dp.toPx()), spTractor, strokeWidth = 2.5f, cap = StrokeCap.Round)
                        drawCircle(Color(0xFF00E5FF).copy(alpha = pulseAlpha * 0.35f), 22.dp.toPx() * pulseScale, tractorIconCenter2D)
                        drawCircle(Color(0xFF00E5FF), 15.dp.toPx(), tractorIconCenter2D)
                        drawCircle(Color(0xFF0F172A), 11.dp.toPx(), tractorIconCenter2D)

                        withTransform({
                            rotate(degrees = -canvasRotation, pivot = tractorIconCenter2D)
                        }) {
                            textPaint.color = android.graphics.Color.CYAN
                            textPaint.textSize = 28f
                            drawContext.canvas.nativeCanvas.drawText("A", tractorIconCenter2D.x, tractorIconCenter2D.y + 6.dp.toPx(), textPaint)

                            val badgeText = String.format(Locale.US, "POINT A ACTIVE • %.1f m (%.0f ft) | %03.0f°", distA, distFt, bearingDeg)
                            textPaint.textSize = 16f
                            val textW = textPaint.measureText(badgeText)
                            val badgeTop = tractorIconCenter2D.y - 28.dp.toPx()
                            drawRoundRect(Color(0xEE0F172A), Offset(tractorIconCenter2D.x - textW * 0.5f - 12f, badgeTop - 12f), Size(textW + 24f, 30f), CornerRadius(8f, 8f))
                            drawRoundRect(Color(0xFF00E5FF), Offset(tractorIconCenter2D.x - textW * 0.5f - 12f, badgeTop - 12f), Size(textW + 24f, 30f), CornerRadius(8f, 8f), style = scratch.strokeMedium)
                            textPaint.color = android.graphics.Color.WHITE
                            drawContext.canvas.nativeCanvas.drawText(badgeText, tractorIconCenter2D.x, badgeTop + 7f, textPaint)
                        }
                    }

                    // 5. 2D AB Line Guidance & Parallel Passes (Sharpened Contrast Halo + Vivid Core)
                    if (currentABLine != null) {
                        val swathW = implementConfig.swathWidth
                        val activeIdx = liveVehicle.activeSwathIndex
                        val lineDir = currentABLine.dirVector
                        val lineNormal = currentABLine.normalVector
                        val lineLengthMeters = 2000.0

                        for (passOffset in -10..10) {
                            val passIndex = activeIdx + passOffset
                            val swathOrigin = currentABLine.aLocal + (lineNormal * (passIndex * swathW))
                            val sp1 = worldToScreen(swathOrigin - (lineDir * lineLengthMeters))
                            val sp2 = worldToScreen(swathOrigin + (lineDir * lineLengthMeters))
                            if (passOffset == 0) {
                                drawLine(color = activeSwathHaloColor, start = sp1, end = sp2, strokeWidth = 10.5f, cap = StrokeCap.Round)
                                drawLine(color = activeSwathColor, start = sp1, end = sp2, strokeWidth = 5.0f, cap = StrokeCap.Round)
                            } else {
                                drawLine(color = parallelSwathColor, start = sp1, end = sp2, strokeWidth = 2.2f, pathEffect = scratch.dashParallel)
                            }
                        }
                        draw2DSurveyorFlag(currentABLine.aLocal, "A", Color(0xFF00E5FF))
                        draw2DSurveyorFlag(currentABLine.bLocal, "B", Color(0xFFF59E0B))
                    }

                    // 5.5 2D Dashed Headland U-Turn Preview Arc
                    if (liveUTurnPath.size >= 2) {
                        scratch.uTurnPath.reset()
                        scratch.uTurnPath.moveTo(worldToScreenX(liveUTurnPath[0].x), worldToScreenY(liveUTurnPath[0].y))
                        for (i in 1 until liveUTurnPath.size) {
                            scratch.uTurnPath.lineTo(worldToScreenX(liveUTurnPath[i].x), worldToScreenY(liveUTurnPath[i].y))
                        }
                        drawPath(path = scratch.uTurnPath, color = uTurnHaloColor, style = scratch.strokeTurnHalo)
                        drawPath(path = scratch.uTurnPath, color = uTurnArcColor, style = scratch.strokeTurnDash)
                        val endPt = worldToScreen(liveUTurnPath.last())
                        drawCircle(color = uTurnHaloColor, radius = 8f, center = endPt)
                        drawCircle(color = uTurnArcColor, radius = 5f, center = endPt)
                    }

                    // 6. Field & Headland Boundaries (Single Batched Paths)
                    if (fieldBoundary.size >= 2) {
                        scratch.boundaryPath.reset()
                        scratch.boundaryPath.moveTo(worldToScreenX(fieldBoundary[0].x), worldToScreenY(fieldBoundary[0].y))
                        for (i in 1 until fieldBoundary.size) {
                            scratch.boundaryPath.lineTo(worldToScreenX(fieldBoundary[i].x), worldToScreenY(fieldBoundary[i].y))
                        }
                        scratch.boundaryPath.close()
                        drawPath(path = scratch.boundaryPath, color = boundaryColor, style = scratch.strokeBoundary)
                    }
                    if (headlandBoundary.size >= 2) {
                        scratch.headlandPath.reset()
                        scratch.headlandPath.moveTo(worldToScreenX(headlandBoundary[0].x), worldToScreenY(headlandBoundary[0].y))
                        for (i in 1 until headlandBoundary.size) {
                            scratch.headlandPath.lineTo(worldToScreenX(headlandBoundary[i].x), worldToScreenY(headlandBoundary[i].y))
                        }
                        scratch.headlandPath.close()
                        drawPath(path = scratch.headlandPath, color = headlandColor, style = scratch.strokeHeadland)
                    }

                    // 7. Field Obstacles
                    for (i in obstacles.indices) {
                        val obs = obstacles[i]
                        val sPos = worldToScreen(obs.localPos)
                        val radPx = (obs.radiusMeters.toFloat() * pixelsPerMeter).coerceAtLeast(10f)
                        drawCircle(color = obs.type.displayColor.copy(alpha = 0.25f), radius = radPx, center = sPos)
                        drawCircle(color = obs.type.displayColor, radius = radPx, center = sPos, style = scratch.strokeMedium)
                        drawCircle(color = obs.type.displayColor, radius = 6.dp.toPx(), center = sPos)
                        drawCircle(color = Color.Black, radius = 3.dp.toPx(), center = sPos)
                    }

                    // 8. 2D Tractor & Implement Model (Steered Front Wheels + 3-Point Hitch State)
                    val vehicleAngle = if (isHeadUp) 0f else headingDeg.toFloat()
                    withTransform({
                        rotate(degrees = vehicleAngle, pivot = centerScreen)
                    }) {
                        drawTractorIn2D(
                            center = centerScreen,
                            pixelsPerMeter = pixelsPerMeter,
                            vehicleConfig = vehicleConfig,
                            implementConfig = implementConfig,
                            actualSteerDeg = liveVehicle.actualSteerAngleDeg,
                            isAutoSteerEngaged = liveVehicle.isAutoSteerEngaged,
                            isMasterActive = implementConfig.isMasterActive,
                            threePointState = threePointState,
                            sectionStates = liveVehicle.sectionStates,
                            themeMode = themeMode,
                            scratch = scratch
                        )
                    }
                }
            }

            // Compass Rose Overlay (Top-Right)
            drawCompass(
                center = Offset(size.width - 44.dp.toPx(), 44.dp.toPx()),
                headingDeg = headingDeg,
                isHeadUpMode = (cameraViewMode == CameraViewMode.CAB_3D || cameraViewMode == CameraViewMode.BIRD_EYE_2D),
                themeMode = themeMode,
                scratch = scratch
            )
        }
    }
}

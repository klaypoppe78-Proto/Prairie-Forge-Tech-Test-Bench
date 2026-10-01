package com.example.agopengps

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import java.util.Locale
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.agopengps.data.*
import com.example.agopengps.io.*
import com.example.agopengps.map.*
import com.example.agopengps.navigation.*
import com.example.agopengps.ui.WizardBoundaryMode
import com.example.agopengps.ui.WizardGuidanceMode
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.*

enum class GnssSourceMode {
    SIMULATOR,
    USB_AIO_TEENSY,
    UDP_NETWORK,
    ANDROID_GPS
}

enum class GuidanceMode {
    AB_LINE,
    A_PLUS,
    CONTOUR_CURVE
}

enum class CameraViewMode {
    CAB_3D,       // Authentic 3D in-cab perspective angled forward over hood
    BIRD_EYE_2D,  // 2D overhead top-down follow view (tractor heading up)
    NORTH_UP,     // 2D top-down strategic field overview (North up)
    FREE_PAN      // Free pan & zoom
}

data class AgUiState(
    val vehicleState: VehicleState = VehicleState(),
    val vehicleConfig: VehicleConfig = VehicleConfig(),
    val implementConfig: ImplementConfig = ImplementConfig(),
    val steerValveConfig: SteerValveConfig = SteerValveConfig(),
    val gnssReceiverType: GnssReceiverType = GnssReceiverType.AUTO_DETECT,
    val usbStats: UsbHardwareStats = UsbHardwareStats(),
    val guidanceSettings: GuidanceSettings = GuidanceSettings(),
    val currentField: FieldEntity? = null,
    val savedFieldsList: List<FieldEntity> = emptyList(),
    val currentABLine: ABLine? = null,
    val currentCurveLine: CurveLine? = null,
    val guidanceMode: GuidanceMode = GuidanceMode.AB_LINE,
    val cameraViewMode: CameraViewMode = CameraViewMode.CAB_3D,
    val fieldBoundary: List<Vec2> = emptyList(),
    val headlandBoundary: List<Vec2> = emptyList(),
    val uTurnPath: List<Vec2> = emptyList(),
    val isRecordingBoundary: Boolean = false,
    val isRecordingCurve: Boolean = false,
    val recordingCurvePoints: List<Vec2> = emptyList(),
    val appliedSegments: List<AppliedSwathSegment> = emptyList(),
    val sectionOverrides: List<SectionOverride> = List(8) { SectionOverride.AUTO },
    val workedAcres: Double = 0.0,
    val isNightMode: Boolean = true,
    val themeMode: DisplayThemeMode = DisplayThemeMode.STANDARD_AG,
    val showSatelliteOverlay: Boolean = true,
    val satelliteMapProvider: MapTileProvider = MapTileProvider.ESRI_WORLD_TOPO,
    val isHeadUpMode: Boolean = true,
    val mapZoom: Float = 1.0f,
    val gnssSource: GnssSourceMode = GnssSourceMode.SIMULATOR,
    val tempPointA: Vec2? = null,
    val isSettingAB: Boolean = false,
    val obstacles: List<FieldObstacle> = emptyList(),
    val activeObstacleAlert: FieldObstacle? = null,
    val headlandTurnInfo: HeadlandTurnInfo = HeadlandTurnInfo(),
    val showSettingsDialog: Boolean = false,
    val showFieldDialog: Boolean = false,
    val showAgIoDialog: Boolean = false,
    val showUdpDialog: Boolean = false,
    val showVirtualDrive: Boolean = false,
    val showWasCalibrationDialog: Boolean = false,
    val showUm982Dialog: Boolean = false,
    val showObstacleDialog: Boolean = false,
    val showImplementPresetsDialog: Boolean = false,
    val showExportDialog: Boolean = false,
    val isMockLoopbackActive: Boolean = false,
    val mockLoopbackState: com.example.agopengps.io.AgIoMockHardwareLoopback.MockState = com.example.agopengps.io.AgIoMockHardwareLoopback.MockState(),
    val isAutoUTurnEnabled: Boolean = true,
    val autoUTurnActive: Boolean = false,
    val uTurnPrompt: String? = null,
    val rtkBaseStationConfig: RtkBaseStationConfig = RtkBaseStationConfig(),
    val showRtkBaseDialog: Boolean = false,
    val showFieldWizardDialog: Boolean = false,
    val showFormFieldFromAbDialog: Boolean = false,
    val showOuterBoundaryCornersDialog: Boolean = false,
    val capturedCorners: List<Vec2> = emptyList(),
    val slidingHitchConfig: SlidingHitchConfig = SlidingHitchConfig(),
    val slidingHitchState: SlidingHitchState = SlidingHitchState(),
    val secondGpsStats: SecondGpsHardwareStats = SecondGpsHardwareStats(),
    val showSlidingHitchDialog: Boolean = false,
    val showOperatorDashboard: Boolean = false,
    val showNmeaDiagnosticsDialog: Boolean = false,
    val isFieldJobActive: Boolean = false,
    val activeJobName: String = "4367 176th St Home Farm",
    val activeJobStartTime: Long = System.currentTimeMillis(),
    // 0.5-Mile Topography, 3D Terrain Mesh & Hillside Compensation
    val isTerrainCompensationEnabled: Boolean = true,
    val showTopographyMap: Boolean = true,
    val show3DTerrainMesh: Boolean = true,
    val currentTerrainSquare: TerrainSquare? = null,
    val currentTerrainAttitude: TerrainAttitude = TerrainAttitude(),
    val lastNavigationOutput: NavigationOutput? = null,
    val statusMessage: String = "AgSteer Ready"
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AgOpenGpsDatabase.getDatabase(application)
    val repository = AgRepository(db.agDao())

    private val _uiState = MutableStateFlow(AgUiState())
    val uiState = _uiState.asStateFlow()

    private val simulator = SimulatorEngine()
    val udpManager = AgUdpManager()
    val usbManager = AgUsbSerialManager(application)
    val secondGpsManager = AgSecondGpsManager()
    private val slidingHitchController = SlidingHitchController()
    val navigationEngine = NavigationEngine()
    val topographyRepo = TopographyRepository()
    val mockLoopback = com.example.agopengps.io.AgIoMockHardwareLoopback()
    val ntripClient = NtripClient()
    val soundManager = SoundManager()
    val nmeaDiagnosticsManager = NmeaDiagnosticsManager()
    private var lastSimNmeaTimeMs: Long = 0L

    var ntripConfig = NtripConfig()
        private set

    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var fusedLocationCallback: LocationCallback? = null
    private var sensorManager: SensorManager? = null
    private var sensorEventListener: SensorEventListener? = null
    private var tabletRollDeg: Double = 0.0
    private var tabletAzimuthDeg: Double = 0.0
    private var filteredLat: Double = 0.0
    private var filteredLon: Double = 0.0
    private var isGpsFilterInitialized: Boolean = false
    private var lastFixTimeMs: Long = 0L

    // Dead-Nuts Precision GNSS State Tracking
    private var rawLocalAntennaPos: Vec2 = Vec2(0.0, 0.0)
    private var stationaryAnchorGeo: GeoPoint? = null
    private var gnssStatusCallback: GnssStatus.Callback? = null
    private var visibleSatellitesCount: Int = 14
    private var usedInFixSatellitesCount: Int = 12
    private var currentHdop: Double = 0.85

    // Reference field datum origin for local meters calculation (Lac qui Parle County, MN Farm: 4367 176th St, 44.954200, -96.082500, 317m / 1040ft)
    var originGeo = GeoPoint(44.954200, -96.082500, 317.0)
        private set
    private var hasInitializedTabletOrigin = false
    private var lastRawGeoPoint: GeoPoint? = null
    private var lastValidMovedGeoPoint: GeoPoint? = null
    private var lastMovedHeadingDeg: Double = 0.0

    private var previousPivotPos: Vec2? = null
    private var lastBoomLeft: Vec2? = null
    private var lastBoomRight: Vec2? = null
    private var lastCoveragePivot: Vec2? = null
    private var lastHeadingDeg: Double = 0.0
    private var integralErrorAccumulator = 0.0
    private var simJob: Job? = null
    private var lastEndOfPassChimeTime: Long = 0L
    private var lastHardwareGpsFixTimeMs: Long = 0L
    private var latestHardwareSteerAngle: Double = 0.0
    val coverageSpatialGrid = CoverageSpatialGrid(cellSizeMeters = 15.0)

    init {
        // Initialize default sample field & baseline AB line
        initDefaultField()

        // Observe saved fields from local database
        viewModelScope.launch {
            repository.allFields.collect { fields ->
                _uiState.update { it.copy(savedFieldsList = fields) }
            }
        }

        // Start UDP manager
        udpManager.start(viewModelScope)

        // Observe USB hardware stats
        viewModelScope.launch {
            usbManager.usbStats.collect { stats ->
                _uiState.update { it.copy(usbStats = stats) }
            }
        }

        // Connect NTRIP client to forward RTCM corrections directly over USB OTG to F9P/UM982
        ntripClient.onRtcmDataCallback = { buffer, count ->
            if (usbManager.usbStats.value.connectionState == com.example.agopengps.io.UsbConnectionState.CONNECTED) {
                usbManager.sendRawBytes(buffer, count)
            }
        }

        // Observe incoming NMEA / KSXT sentences from USB (Teensy 4.1 / UM982 / F9P)
        viewModelScope.launch {
            usbManager.incomingSentences.collect { sentence ->
                nmeaDiagnosticsManager.recordSentence(sentence)
                if (_uiState.value.gnssSource == GnssSourceMode.USB_AIO_TEENSY) {
                    processIncomingSentence(sentence)
                }
            }
        }

        // Observe incoming steer angle feedback from USB
        viewModelScope.launch {
            usbManager.incomingSteerFeedback.collect { actualAngle ->
                latestHardwareSteerAngle = actualAngle
            }
        }

        // Observe incoming NMEA sentences from UDP
        viewModelScope.launch {
            udpManager.incomingSentences.collect { sentence ->
                nmeaDiagnosticsManager.recordSentence(sentence)
                if (_uiState.value.gnssSource == GnssSourceMode.UDP_NETWORK) {
                    processIncomingSentence(sentence)
                }
            }
        }

        // Observe incoming steer angle feedback from UDP
        viewModelScope.launch {
            udpManager.incomingSteerFeedback.collect { actualAngle ->
                if (_uiState.value.gnssSource == GnssSourceMode.UDP_NETWORK || _uiState.value.isMockLoopbackActive) {
                    latestHardwareSteerAngle = actualAngle
                }
            }
        }

        // Observe Mock Hardware Loopback state
        viewModelScope.launch {
            mockLoopback.state.collect { mockState ->
                _uiState.update { it.copy(mockLoopbackState = mockState) }
            }
        }

        // Start Second GPS UDP Listener for Implement Steering / Sliding Hitch
        secondGpsManager.startListening(port = _uiState.value.slidingHitchConfig.udpPort, scope = viewModelScope)
        viewModelScope.launch {
            secondGpsManager.stats.collect { stats ->
                _uiState.update { it.copy(secondGpsStats = stats) }
            }
        }

        // Start high-frequency 20Hz (50ms) simulation & guidance loop
        startNavigationLoop()
    }

    private fun initDefaultField() {
        loadLacQuiParleHomeFarm()
    }

    fun loadLacQuiParleHomeFarm() {
        originGeo = GeoPoint(44.954200, -96.082500, 317.0) // 4367 176th St, Lac qui Parle County, MN (~1,040 ft MSL)

        // 160-Acre Minnesota Quarter-Section (800m x 800m square: 0.5 mile x 0.5 mile)
        val boundary = listOf(
            Vec2(-400.0, -400.0),
            Vec2(400.0, -400.0),
            Vec2(400.0, 400.0),
            Vec2(-400.0, 400.0)
        )

        // Baseline AB Line along 176th Street East-West (090.0° / 270.0°)
        val aLocal = Vec2(-360.0, -200.0)
        val bLocal = Vec2(360.0, -200.0)
        val aGeo = GeoUtils.localMetersToGeo(aLocal, originGeo)
        val bGeo = GeoUtils.localMetersToGeo(bLocal, originGeo)
        val defaultAB = ABLine.fromPoints(aLocal, bLocal, aGeo, bGeo, name = "176th St East-West Baseline")

        simulator.resetTo(Vec2(-320.0, -200.0), 90.0)

        val headland = GuidanceAlgorithms.generateHeadlandPolygon(boundary, 12.0)

        // Preload New Holland 8870 Machine Profile
        val nh8870Config = VehicleConfig(
            wheelbase = 2.8448,          // 112.0 inches
            trackWidth = 2.0828,         // 82.0 inches
            antennaHeight = 3.2004,      // 10.5 feet
            antennaPivotOffset = 0.2032, // 8.0 inches
            hitchLength = 1.2192         // 4.0 feet
        )

        val nh8870Valve = SteerValveConfig(
            valveType = SteerValveType.PROPORTIONAL_DANFOSS_PVEA,
            proportionalGainKp = 65,
            integralGainKi = 10,
            derivativeGainKd = 20,
            minPwmDeadband = 30,
            maxPwmLimit = 225,
            isNewHolland8870Preset = true
        )

        _uiState.update {
            it.copy(
                fieldBoundary = boundary,
                headlandBoundary = headland,
                currentABLine = defaultAB,
                vehicleConfig = nh8870Config,
                steerValveConfig = nh8870Valve,
                showSatelliteOverlay = true,
                satelliteMapProvider = MapTileProvider.ESRI_WORLD_TOPO,
                showTopographyMap = true,
                show3DTerrainMesh = true,
                isTerrainCompensationEnabled = true,
                currentField = FieldEntity(
                    id = 1L,
                    name = "Poppe Farm - 4367 176th St (160 Ac)",
                    areaAcres = 160.0,
                    workedAcres = 0.0,
                    originLat = originGeo.latitude,
                    originLon = originGeo.longitude,
                    activeAbLineName = "176th St East-West Baseline",
                    hasActiveAbLine = true,
                    activeAbLineAX = aLocal.x,
                    activeAbLineAY = aLocal.y,
                    activeAbLineBX = bLocal.x,
                    activeAbLineBY = bLocal.y,
                    activeAbLineHeading = defaultAB.headingDeg
                ),
                vehicleState = it.vehicleState.copy(
                    geoPosition = originGeo,
                    localPivotPosition = Vec2(-320.0, -200.0),
                    headingDeg = 90.0,
                    speedKmh = 0.0
                ),
                statusMessage = "Loaded Home Farm: 4367 176th St, Lac qui Parle Co, MN (160 Ac, USGS Topo Active)"
            )
        }
    }

    private fun startNavigationLoop() {
        simJob?.cancel()
        simJob = viewModelScope.launch(Dispatchers.Default) {
            var lastTimeNanos = System.nanoTime()
            while (isActive) {
                val currentTimeNanos = System.nanoTime()
                val elapsedNanos = currentTimeNanos - lastTimeNanos
                lastTimeNanos = currentTimeNanos

                // Dynamic delta time in seconds, clamped between 5ms and 100ms to prevent simulation jumps
                val dtSec = (elapsedNanos / 1_000_000_000.0).coerceIn(0.005, 0.10)

                updateGuidanceAndKinematics(dtSec)

                val workTimeMs = (System.nanoTime() - currentTimeNanos) / 1_000_000L
                // Standard AgIO / AgOpenGPS 20 Hz update frequency (50 ms) to prevent Compose main-thread starvation
                val targetIntervalMs = 50L
                val sleepTime = max(1L, targetIntervalMs - workTimeMs)
                delay(sleepTime)
            }
        }
    }

    private fun updateGuidanceAndKinematics(dtSec: Double) {
        val state = _uiState.value
        val vehicle = state.vehicleConfig
        val settings = state.guidanceSettings
        val implement = state.implementConfig
        val abLine = state.currentABLine
        val curveLine = state.currentCurveLine
        val mode = state.guidanceMode

        var heading = state.vehicleState.headingDeg
        var speed = state.vehicleState.speedKmh
        var roll = state.vehicleState.rollDeg

        if (state.gnssSource == GnssSourceMode.SIMULATOR) {
            simulator.update(
                dtSec = dtSec,
                targetSteerAngleDeg = state.vehicleState.targetSteerAngleDeg,
                isAutoSteerEngaged = state.vehicleState.isAutoSteerEngaged,
                vehicle = vehicle
            )
            rawLocalAntennaPos = simulator.localPivot
            heading = simulator.headingDeg
            speed = if (simulator.isReverse) -simulator.speedKmh else simulator.speedKmh
            roll = simulator.simulatedRollDeg
        } else if (state.gnssSource == GnssSourceMode.ANDROID_GPS) {
            // Inter-sample dead reckoning between Android GNSS fixes at 20Hz loop
            // Dynamic dead reckoning at 20Hz for ultra-smooth fluid motion across screen
            val speedMps = speed / 3.6
            if (abs(speedMps) > 0.05) {
                val headingRad = Math.toRadians(heading)
                val deltaDist = speedMps * dtSec
                val deltaX = sin(headingRad) * deltaDist
                val deltaY = cos(headingRad) * deltaDist
                rawLocalAntennaPos = Vec2(rawLocalAntennaPos.x + deltaX, rawLocalAntennaPos.y + deltaY)
            }

            // Sensor Prioritization: Hardware IMU (Teensy) > Tablet Accelerometer with Centrifugal Compensation
            if (state.usbStats.connectionState != com.example.agopengps.io.UsbConnectionState.CONNECTED) {
                val dHeading = GeoUtils.normalizeAngleDeg(heading - lastHeadingDeg)
                val yawRateDegPerSec = if (dtSec > 0.0) dHeading / dtSec else 0.0
                lastHeadingDeg = heading
                val yawRateRad = Math.toRadians(yawRateDegPerSec)
                roll = GuidanceAlgorithms.calculateCentrifugalCompensatedRoll(
                    rawAccelRollDeg = tabletRollDeg,
                    speedKmh = speed,
                    yawRateRadPerSec = yawRateRad
                )
            }
        }

        // Derive true ground rear-axle pivot from un-compounded raw antenna position
        val compensatedPivot = if (state.gnssSource == GnssSourceMode.SIMULATOR) {
            simulator.localPivot
        } else if (state.gnssSource == GnssSourceMode.ANDROID_GPS) {
            GuidanceAlgorithms.compensateTabletPosition(
                tabletLocal = rawLocalAntennaPos,
                headingDeg = heading,
                rollDeg = roll,
                vehicle = vehicle,
                rollZeroOffset = settings.rollZeroOffsetDeg
            )
        } else {
            GuidanceAlgorithms.compensateAntennaPosition(
                antennaLocal = rawLocalAntennaPos,
                headingDeg = heading,
                rollDeg = roll,
                vehicle = vehicle,
                rollZeroOffset = settings.rollZeroOffsetDeg
            )
        }

        // Record boundary vertex if recording boundary
        if (state.isRecordingBoundary && abs(speed) > 0.5) {
            val lastBoundaryPt = state.fieldBoundary.lastOrNull()
            if (lastBoundaryPt == null || compensatedPivot.distanceTo(lastBoundaryPt) > 3.0) {
                val newBoundary = state.fieldBoundary + compensatedPivot
                _uiState.update { it.copy(fieldBoundary = newBoundary) }
            }
        }

        // Record curve vertex if recording contour pass
        if (state.isRecordingCurve && abs(speed) > 0.5) {
            val lastCurvePt = state.recordingCurvePoints.lastOrNull()
            if (lastCurvePt == null || compensatedPivot.distanceTo(lastCurvePt) > 2.0) {
                val newCurvePts = state.recordingCurvePoints + compensatedPivot
                _uiState.update { it.copy(recordingCurvePoints = newCurvePts) }
            }
        }

        // Topographical Digital Elevation Model (0.5 mile by 0.5 mile area)
        val currentGeo = GeoUtils.localMetersToGeo(compensatedPivot, originGeo)
        val terrainSquare = topographyRepo.loadOrUpdateSquare(compensatedPivot, currentGeo)
        val terrainAttitude = terrainSquare.getTerrainAttitude(
            pos = compensatedPivot,
            headingDeg = heading,
            antennaHeightMeters = vehicle.antennaHeight,
            sideDraftGain = settings.terrainSideDraftGain
        )

        // Hillside Topography Compensation for Roll & Antenna Offset
        if (state.isTerrainCompensationEnabled) {
            if (state.gnssSource == GnssSourceMode.ANDROID_GPS && state.usbStats.connectionState != com.example.agopengps.io.UsbConnectionState.CONNECTED) {
                roll = terrainAttitude.rollDeg
            }
        }

        var crossTrackError = 0.0
        var headingError = 0.0
        var activeSwathIndex = 0
        var targetSteerAngle = 0.0
        var latestNavOutput: NavigationOutput? = null
        var uTurnPath: List<Vec2> = emptyList()
        var headlandTurnInfo = HeadlandTurnInfo()

        // Guidance Tracking calculation (AB / A+ Line or Curve Line)
        if (mode == GuidanceMode.CONTOUR_CURVE && curveLine != null && curveLine.points.size >= 2) {
            val curveResult = GuidanceAlgorithms.calculateCurveTracking(
                pivotPosition = compensatedPivot,
                tractorHeadingDeg = heading,
                curveLine = curveLine,
                swathWidth = implement.swathWidth
            )
            if (curveResult != null) {
                crossTrackError = curveResult.crossTrackErrorMeters
                headingError = curveResult.headingErrorDeg
                activeSwathIndex = curveResult.activeSwathIndex

                targetSteerAngle = GuidanceAlgorithms.calculateStanleySteering(
                    crossTrackErrorMeters = crossTrackError,
                    headingErrorDeg = headingError,
                    speedKmh = speed,
                    settings = settings,
                    vehicle = vehicle
                )
            }
        } else if (abLine != null) {
            val swathResult = GuidanceAlgorithms.calculateSwathTracking(
                pivotPosition = compensatedPivot,
                tractorHeadingDeg = heading,
                abLine = abLine,
                swathWidth = implement.swathWidth
            )

            crossTrackError = swathResult.crossTrackErrorMeters
            headingError = swathResult.headingErrorDeg
            activeSwathIndex = swathResult.activeSwathIndex

            if (state.vehicleState.isAutoSteerEngaged && abs(crossTrackError) < 1.0) {
                integralErrorAccumulator += crossTrackError * dtSec
                integralErrorAccumulator = integralErrorAccumulator.coerceIn(-2.0, 2.0)
            } else {
                integralErrorAccumulator = 0.0
            }

            // Dynamic Speed-Adaptive Lookahead: snappy low-speed turn-in without high-speed hunting
            val adaptiveLookaheadSec = GuidanceAlgorithms.calculateSpeedAdaptiveLookahead(
                speedKmh = speed,
                baseLookaheadSec = settings.purePursuitLookaheadGain,
                minLookaheadMeters = settings.purePursuitMinLookahead,
                maxLookaheadMeters = settings.purePursuitMaxLookahead
            ) / max(speed / 3.6, 0.5)

            // Configure Navigation Engine parameters matching active tractor and tuning settings
            navigationEngine.updateConfig(
                NavigationEngineConfig(
                    algorithm = if (settings.controllerType == ControllerType.STANLEY) SteeringAlgorithm.STANLEY else SteeringAlgorithm.PURE_PURSUIT,
                    wheelbaseMeters = vehicle.wheelbase,
                    maxSteerAngleDeg = vehicle.maxSteerAngleDeg,
                    deadbandMeters = settings.deadbandCm / 100.0,
                    stanleyGainK = settings.stanleyGain,
                    stanleyIntegralGainKi = settings.stanleyIntegralGain,
                    purePursuitLookaheadGainSec = settings.purePursuitLookaheadGain,
                    purePursuitMinLookaheadMeters = settings.purePursuitMinLookahead,
                    purePursuitMaxLookaheadMeters = settings.purePursuitMaxLookahead
                )
            )

            val navInput = NavigationInput(
                currentPosition = compensatedPivot,
                headingDeg = heading,
                speedKmh = speed,
                targetABLine = abLine,
                swathWidthMeters = implement.swathWidth,
                forcedSwathIndex = activeSwathIndex,
                dtSec = dtSec,
                terrainRollDeg = terrainAttitude.rollDeg,
                isTerrainCompensationEnabled = state.isTerrainCompensationEnabled,
                terrainSideDraftGain = settings.terrainSideDraftGain,
                terrainSquare = terrainSquare,
                antennaHeightMeters = vehicle.antennaHeight
            )

            val navOutput = if (state.gnssSource == GnssSourceMode.ANDROID_GPS) {
                // When Tablet GPS is active, use Pure Pursuit with dynamic speed adaptive lookahead
                navigationEngine.calculatePurePursuit(navInput)
            } else {
                navigationEngine.calculate(navInput)
            }
            targetSteerAngle = navOutput.targetSteerAngleDeg
            latestNavOutput = navOutput

            // Calculate U-Turn Dubins arc preview approaching headland
            val nextSwathOrigin = abLine.aLocal + (abLine.normalVector * ((activeSwathIndex + 1) * implement.swathWidth))
            uTurnPath = GuidanceAlgorithms.generateUTurnPath(
                currentSwathEnd = swathResult.projectedPoint + (swathResult.swathDir * 15.0),
                currentHeadingDeg = swathResult.targetHeadingDeg,
                nextSwathStart = nextSwathOrigin,
                turnRadiusMeters = (implement.swathWidth * 0.5).coerceAtLeast(3.5)
            )

            // Calculate Smart Headland Turn Guidance
            headlandTurnInfo = GuidanceAlgorithms.calculateHeadlandTurnGuidance(
                pivotPosition = compensatedPivot,
                tractorHeadingDeg = heading,
                activeSwathIndex = activeSwathIndex,
                headlandBoundary = state.headlandBoundary.ifEmpty { state.fieldBoundary }.ifEmpty { null },
                swathDir = swathResult.swathDir
            )

            // Auto U-Turn Engine for New Holland 8870 (Dubins arc auto-steer + operator lift/lower guidance)
            if (state.isAutoUTurnEnabled && state.vehicleState.isAutoSteerEngaged && abs(speed) > 0.4) {
                if (!state.autoUTurnActive) {
                    val distToBoundary = if (state.fieldBoundary.size >= 3) {
                        GuidanceAlgorithms.distanceToBoundaryEdge(compensatedPivot, state.fieldBoundary)
                    } else {
                        Double.MAX_VALUE
                    }
                    val isNearTurn = (headlandTurnInfo.distanceToTurnMeters in 0.1..4.5) ||
                            (distToBoundary <= (implement.toolWidth * 1.5).coerceAtLeast(7.0) && distToBoundary > 0.0)

                    if (isNearTurn) {
                        _uiState.update {
                            it.copy(
                                autoUTurnActive = true,
                                uTurnPrompt = "NH 8870: LIFT IMPLEMENT NOW (TURNING)",
                                statusMessage = "Auto U-Turn: LIFT IMPLEMENT NOW"
                            )
                        }
                        soundManager.playEndOfPassChime(viewModelScope)
                        vibrateHaptic(120)
                    }
                } else {
                    // While auto U-turn is actively executing, steer tractor along the Dubins arc
                    if (uTurnPath.isNotEmpty()) {
                        val turnSteer = GuidanceAlgorithms.calculatePurePursuitWaypointsSteering(
                            pivotPosition = compensatedPivot,
                            tractorHeadingDeg = heading,
                            waypoints = uTurnPath,
                            speedKmh = speed,
                            vehicle = vehicle,
                            lookaheadMeters = 3.5
                        )
                        targetSteerAngle = turnSteer
                    }

                    // Check if tractor has acquired the new adjacent swath pass
                    val toTargetLine = compensatedPivot - nextSwathOrigin
                    val distToNewPass = abs(toTargetLine.dot(abLine.normalVector))
                    val diffHeading = abs(GeoUtils.normalizeAngleDeg(heading - swathResult.targetHeadingDeg))

                    // If heading has rotated around (> 120 deg from start) and close to new swath line (< 2.0m)
                    if (diffHeading > 120.0 && distToNewPass < 2.0) {
                        activeSwathIndex += 1
                        _uiState.update {
                            it.copy(
                                autoUTurnActive = false,
                                uTurnPrompt = "ON LINE: LOWER IMPLEMENT (NH 8870)",
                                statusMessage = "On Pass: LOWER IMPLEMENT NOW"
                            )
                        }
                        soundManager.playEndOfPassChime(viewModelScope)
                        vibrateHaptic(80)
                    }
                }
            } else if (!state.autoUTurnActive && state.uTurnPrompt != null && headlandTurnInfo.distanceToTurnMeters > 15.0) {
                _uiState.update { it.copy(uTurnPrompt = null) }
            }

            // Trigger end-of-pass audible alert chime when vehicle approaches headland
            if (state.fieldBoundary.size >= 3 && abs(speed) > 1.0) {
                val distToBoundary = GuidanceAlgorithms.distanceToBoundaryEdge(compensatedPivot, state.fieldBoundary)
                val lookaheadDist = (speed / 3.6) * 5.0 // 5 seconds lookahead to turn
                val headlandOffset = (implement.toolWidth * 2.0).coerceAtLeast(12.0)
                val now = System.currentTimeMillis()

                if (distToBoundary <= (headlandOffset + lookaheadDist) && (now - lastEndOfPassChimeTime) > 12000L) {
                    lastEndOfPassChimeTime = now
                    soundManager.playEndOfPassChime(viewModelScope)
                    vibrateHaptic(80)
                }
            }
        }

        // Check Obstacle Proximity Alerts (e.g. within 12m of rock / tile drain / power pole)
        val closestObstacle = GuidanceAlgorithms.checkObstacleProximity(
            pivotPosition = compensatedPivot,
            obstacles = state.obstacles,
            warningThresholdMeters = 12.0
        )
        if (closestObstacle != null && state.activeObstacleAlert?.id != closestObstacle.id) {
            soundManager.playEndOfPassChime(viewModelScope)
            vibrateHaptic(120)
        }

        // Section status calculation: automatically assumes implement is active whenever auto-steer is engaged
        val isImplementActive = state.vehicleState.isAutoSteerEngaged || implement.isMasterActive
        val effectiveImplement = implement.copy(isMasterActive = isImplementActive)
        val implementStatus = GuidanceAlgorithms.calculateImplementSections(
            pivotPosition = compensatedPivot,
            tractorHeadingDeg = heading,
            vehicle = vehicle,
            implement = effectiveImplement,
            fieldBoundary = state.fieldBoundary.ifEmpty { null },
            appliedPolygons = state.appliedSegments,
            sectionOverrides = state.sectionOverrides,
            spatialGrid = coverageSpatialGrid
        )

        // Record applied coverage map when implement is working (Smooth continuous brush)
        var updatedApplied: List<AppliedSwathSegment>? = null
        var addedAreaAcres = 0.0

        if (isImplementActive && abs(speed) > 0.3) {
            val activeSections = implementStatus.sections.filterIndexed { idx, _ ->
                implementStatus.sectionStates.getOrElse(idx) { false }
            }
            if (activeSections.isNotEmpty()) {
                val currentLeft = activeSections.first().left
                val currentRight = activeSections.last().right
                val lastLeft = lastBoomLeft
                val lastRight = lastBoomRight
                val lastPiv = lastCoveragePivot

                if (lastLeft != null && lastRight != null && lastPiv != null) {
                    val travelDist = compensatedPivot.distanceTo(lastPiv)
                    // High-resolution smooth brush recording: record fine seamless quads at 0.15m (6 in)
                    if (travelDist >= 0.15) {
                        val center = (currentLeft + currentRight + lastLeft + lastRight) * 0.25
                        val seg = AppliedSwathSegment(
                            leftStart = lastLeft,
                            rightStart = lastRight,
                            leftEnd = currentLeft,
                            rightEnd = currentRight,
                            center = center,
                            radius = implement.toolWidth * 0.5
                        )
                        coverageSpatialGrid.addSegment(seg)
                        updatedApplied = state.appliedSegments + seg
                        val activeWidth = (implement.toolWidth / implement.numSections) * activeSections.size
                        addedAreaAcres = (activeWidth * travelDist) / 4046.86

                        lastBoomLeft = currentLeft
                        lastBoomRight = currentRight
                        lastCoveragePivot = compensatedPivot
                        previousPivotPos = compensatedPivot
                    }
                } else {
                    lastBoomLeft = currentLeft
                    lastBoomRight = currentRight
                    lastCoveragePivot = compensatedPivot
                    previousPivotPos = compensatedPivot
                }
            } else {
                lastBoomLeft = null
                lastBoomRight = null
                lastCoveragePivot = null
            }
        } else {
            lastBoomLeft = null
            lastBoomRight = null
            lastCoveragePivot = null
            previousPivotPos = compensatedPivot
        }

        val actualSteer = if (state.gnssSource == GnssSourceMode.SIMULATOR) {
            simulator.actualSteerAngleDeg
        } else if (state.gnssSource == GnssSourceMode.USB_AIO_TEENSY || state.gnssSource == GnssSourceMode.UDP_NETWORK || state.isMockLoopbackActive) {
            latestHardwareSteerAngle
        } else {
            // Standalone / Tablet GPS mode: smoothly animate the front wheels to reflect commanded steer angle
            if (state.vehicleState.isAutoSteerEngaged || abs(speed) > 0.4) {
                val currentSteer = state.vehicleState.actualSteerAngleDeg
                val diff = targetSteerAngle - currentSteer
                val maxStep = 35.0 * dtSec
                if (abs(diff) <= maxStep) targetSteerAngle else currentSteer + sign(diff) * maxStep
            } else {
                0.0
            }
        }

        // Send PGN 254 (0xFE) to Steer Controller over UDP
        udpManager.sendSteerCommand(
            targetSteerAngleDeg = targetSteerAngle,
            isEngaged = state.vehicleState.isAutoSteerEngaged,
            speedKmh = speed,
            crossTrackErrorMm = (crossTrackError * 1000.0).roundToInt(),
            sectionStates = implementStatus.sectionStates
        )

        // Send PGN 254 (0xFE) to Teensy 4.1 over USB Host Serial
        usbManager.sendSteerCommand(
            targetSteerAngleDeg = targetSteerAngle,
            isEngaged = state.vehicleState.isAutoSteerEngaged,
            speedKmh = speed,
            crossTrackErrorMm = (crossTrackError * 1000.0).roundToInt(),
            sectionStates = implementStatus.sectionStates
        )

        // Send PGN 239 (0xEF) Machine control relays
        var secBits = 0
        for (i in implementStatus.sectionStates.indices) {
            if (implementStatus.sectionStates[i]) {
                secBits = secBits or (1 shl i)
            }
        }
        udpManager.sendMachineData(
            sections1To8 = secBits and 0xFF,
            sections9To16 = (secBits shr 8) and 0xFF,
            hydraulicLift = if (implement.isMasterActive) 1 else 0
        )

        // --- ACTIVE IMPLEMENT GUIDANCE & SLIDING HITCH UPDATE ---
        val externalSecondGps = secondGpsManager.latestReport.value
        val updatedSlidingHitchState = slidingHitchController.update(
            dtSec = dtSec,
            tractorPivot = compensatedPivot,
            tractorHeadingDeg = heading,
            tractorSpeedKmh = speed,
            hitchLengthBehindAxle = vehicle.hitchLength,
            implementOffsetBehindHitch = implement.offsetBehindTractor,
            currentABLine = abLine,
            activeSwathIndex = activeSwathIndex,
            swathWidth = implement.swathWidth,
            config = state.slidingHitchConfig,
            currentState = state.slidingHitchState,
            externalSecondGpsReport = externalSecondGps,
            originGeo = originGeo
        )

        // Broadcast PGN 230 Implement Steering / Sliding Hitch command
        if (state.slidingHitchConfig.isEnabled) {
            val steerPacket = slidingHitchController.buildImplementSteerPacket(
                shiftCm = updatedSlidingHitchState.currentHitchShiftCm,
                pwm = updatedSlidingHitchState.hydraulicPwmOutput,
                isAutoActive = state.slidingHitchConfig.isAutoHitchEngaged
            )
            secondGpsManager.sendImplementSteerPacket(steerPacket, targetPort = state.slidingHitchConfig.udpPort)
        }

        // Update UI state
        _uiState.update { current ->
            val updatedGeoPos = GeoUtils.localMetersToGeo(compensatedPivot, originGeo)
            current.copy(
                vehicleState = current.vehicleState.copy(
                    geoPosition = updatedGeoPos,
                    localPivotPosition = compensatedPivot,
                    headingDeg = heading,
                    speedKmh = speed,
                    rollDeg = roll,
                    actualSteerAngleDeg = actualSteer,
                    targetSteerAngleDeg = targetSteerAngle,
                    crossTrackErrorMeters = crossTrackError,
                    headingErrorDeg = headingError,
                    activeSwathIndex = activeSwathIndex,
                    sectionStates = implementStatus.sectionStates
                ),
                slidingHitchState = updatedSlidingHitchState,
                appliedSegments = updatedApplied ?: current.appliedSegments,
                workedAcres = current.workedAcres + addedAreaAcres,
                uTurnPath = uTurnPath,
                activeObstacleAlert = closestObstacle,
                headlandTurnInfo = headlandTurnInfo,
                currentTerrainSquare = terrainSquare,
                currentTerrainAttitude = terrainAttitude,
                lastNavigationOutput = latestNavOutput
            )
        }

        // Emit simulated NMEA / KSXT sentences for real-time field diagnostics inspection in simulator mode
        if (state.gnssSource == GnssSourceMode.SIMULATOR) {
            val now = System.currentTimeMillis()
            if (now - lastSimNmeaTimeMs >= 50L) { // 20Hz update
                lastSimNmeaTimeMs = now
                val geo = _uiState.value.vehicleState.geoPosition
                val hdg = heading
                val spdKmh = abs(speed)
                val spdKnots = spdKmh / 1.852
                val rollVal = roll
                val pitchVal = state.vehicleState.pitchDeg

                val timeUtc = java.text.SimpleDateFormat("HHmmss.ss", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(now))
                val dateUtc = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(now))

                // 1. $KSXT (Unicore UM982 Dual-Antenna Heading & Position)
                val ksxtBody = "KSXT,$dateUtc$timeUtc,${String.format(java.util.Locale.US, "%.7f", geo.longitude)},${String.format(java.util.Locale.US, "%.7f", geo.latitude)},${String.format(java.util.Locale.US, "%.2f", geo.altitude)},${String.format(java.util.Locale.US, "%.2f", hdg)},${String.format(java.util.Locale.US, "%.2f", pitchVal)},${String.format(java.util.Locale.US, "%.2f", rollVal)},4,0"
                val ksxtChecksum = com.example.agopengps.io.NmeaDiagnosticsManager.calculateNmeaChecksum("$$ksxtBody")
                nmeaDiagnosticsManager.recordSentence("$$ksxtBody*$ksxtChecksum")

                // 2. $GNGGA (Standard Global Navigation Positioning Fix)
                val latDeg = geo.latitude.toInt()
                val latMin = (abs(geo.latitude) - abs(latDeg)) * 60.0
                val latNmea = String.format(java.util.Locale.US, "%02d%07.4f", abs(latDeg), latMin)
                val latHem = if (geo.latitude >= 0) "N" else "S"

                val lonDeg = geo.longitude.toInt()
                val lonMin = (abs(geo.longitude) - abs(lonDeg)) * 60.0
                val lonNmea = String.format(java.util.Locale.US, "%03d%07.4f", abs(lonDeg), lonMin)
                val lonHem = if (geo.longitude >= 0) "E" else "W"

                val ggaBody = "GNGGA,$timeUtc,$latNmea,$latHem,$lonNmea,$lonHem,4,20,0.7,${String.format(java.util.Locale.US, "%.1f", geo.altitude)},M,0.0,M,0.8,0000"
                val ggaChecksum = com.example.agopengps.io.NmeaDiagnosticsManager.calculateNmeaChecksum("$$ggaBody")
                nmeaDiagnosticsManager.recordSentence("$$ggaBody*$ggaChecksum")

                // 3. $GNVTG (Course & Ground Speed)
                val vtgBody = "GNVTG,${String.format(java.util.Locale.US, "%.1f", hdg)},T,${String.format(java.util.Locale.US, "%.1f", hdg)},M,${String.format(java.util.Locale.US, "%.1f", spdKnots)},N,${String.format(java.util.Locale.US, "%.1f", spdKmh)},K,D"
                val vtgChecksum = com.example.agopengps.io.NmeaDiagnosticsManager.calculateNmeaChecksum("$$vtgBody")
                nmeaDiagnosticsManager.recordSentence("$$vtgBody*$vtgChecksum")
            }
        }
    }

    private fun processIncomingSentence(sentence: String) {
        val report = NmeaParser.parse(sentence) ?: return

        // Forward GGA to NTRIP client if sentence is GGA
        if (sentence.startsWith("\$GPGGA") || sentence.startsWith("\$GNGGA")) {
            ntripClient.setLatestGga(sentence)
        }

        val hasValidGeo = (report.geoPoint.latitude != 0.0 || report.geoPoint.longitude != 0.0)
        val localPos = if (hasValidGeo) GeoUtils.geoToLocalMeters(report.geoPoint, originGeo) else null
        if (localPos != null) {
            rawLocalAntennaPos = localPos
        }

        val state = _uiState.value
        val effectiveHeading = report.headingDeg ?: state.vehicleState.headingDeg
        val effectiveRoll = if (report.rollDeg != 0.0) report.rollDeg else state.vehicleState.rollDeg

        val compensatedPivot = if (localPos != null) {
            GuidanceAlgorithms.compensateAntennaPosition(
                antennaLocal = localPos,
                headingDeg = effectiveHeading,
                rollDeg = effectiveRoll,
                vehicle = state.vehicleConfig,
                rollZeroOffset = state.guidanceSettings.rollZeroOffsetDeg
            )
        } else null

        _uiState.update { current ->
            val updatedVeh = current.vehicleState.copy(
                geoPosition = if (hasValidGeo) report.geoPoint else current.vehicleState.geoPosition,
                localPivotPosition = compensatedPivot ?: current.vehicleState.localPivotPosition,
                headingDeg = report.headingDeg ?: current.vehicleState.headingDeg,
                speedKmh = report.speedKmh ?: current.vehicleState.speedKmh,
                rollDeg = if (report.rollDeg != 0.0) report.rollDeg else current.vehicleState.rollDeg,
                pitchDeg = if (report.pitchDeg != 0.0) report.pitchDeg else current.vehicleState.pitchDeg,
                baselineLengthMeters = if (report.baselineLengthMeters > 0.0) report.baselineLengthMeters else current.vehicleState.baselineLengthMeters,
                fixQuality = if (report.fixQuality != FixQuality.INVALID) report.fixQuality else current.vehicleState.fixQuality,
                satellites = if (report.satellites > 0) report.satellites else current.vehicleState.satellites,
                ageOfCorrectionSec = if (report.ageSec > 0.0) report.ageSec else current.vehicleState.ageOfCorrectionSec
            )
            val receiverStatus = if (report.receiverIdent.isNotEmpty()) "GNSS: ${report.receiverIdent}" else current.statusMessage
            current.copy(
                vehicleState = updatedVeh,
                statusMessage = receiverStatus
            )
        }
    }

    fun toggleAutoSteer() {
        val currentEngaged = _uiState.value.vehicleState.isAutoSteerEngaged
        val newEngaged = !currentEngaged
        vibrateHaptic(if (newEngaged) 100 else 50)

        if (newEngaged) {
            soundManager.playAutoSteerEngage(viewModelScope)
        } else {
            soundManager.playAutoSteerDisengage(viewModelScope)
        }

        _uiState.update {
            it.copy(
                vehicleState = it.vehicleState.copy(isAutoSteerEngaged = newEngaged),
                implementConfig = it.implementConfig.copy(isMasterActive = newEngaged),
                statusMessage = if (newEngaged) "Auto-Steer & Implement ENGAGED" else "Auto-Steer & Implement Disengaged"
            )
        }
    }

    fun setPointA() {
        val currentPivot = _uiState.value.vehicleState.localPivotPosition
        _uiState.update {
            it.copy(
                tempPointA = currentPivot,
                isSettingAB = true,
                statusMessage = "Point A Set. Drive forward and press Set B."
            )
        }
        vibrateHaptic(50)
    }

    fun setPointB() {
        val pointA = _uiState.value.tempPointA
        if (pointA == null) {
            setPointA()
            return
        }
        val pointB = _uiState.value.vehicleState.localPivotPosition
        if (pointA.distanceTo(pointB) < 3.0) {
            _uiState.update { it.copy(statusMessage = "Points too close. Drive at least 3m from A.") }
            return
        }
        val aGeo = GeoUtils.localMetersToGeo(pointA, originGeo)
        val bGeo = GeoUtils.localMetersToGeo(pointB, originGeo)
        val newAB = ABLine.fromPoints(pointA, pointB, aGeo, bGeo, "AB Line ${_uiState.value.currentField?.name ?: ""}")
        _uiState.update {
            it.copy(
                currentABLine = newAB,
                tempPointA = null,
                isSettingAB = false,
                guidanceMode = GuidanceMode.AB_LINE,
                statusMessage = "AB Line Configured (${newAB.headingDeg.roundToInt()}°)"
            )
        }
        vibrateHaptic(100)
    }

    fun createAPlusLine(headingDeg: Double) {
        val currentPivot = _uiState.value.vehicleState.localPivotPosition
        val aGeo = GeoUtils.localMetersToGeo(currentPivot, originGeo)
        val newAB = ABLine.fromAPlus(currentPivot, headingDeg, aGeo, "A+ (${headingDeg.roundToInt()}°)")
        _uiState.update {
            it.copy(
                currentABLine = newAB,
                guidanceMode = GuidanceMode.A_PLUS,
                statusMessage = "A+ Line Generated at ${headingDeg.roundToInt()}°"
            )
        }
        vibrateHaptic(80)
    }

    fun reverseABLineDirection() {
        val state = _uiState.value
        if (state.guidanceMode == GuidanceMode.CONTOUR_CURVE && state.currentCurveLine != null) {
            val curve = state.currentCurveLine
            val reversedPts = curve.points.reversed()
            _uiState.update {
                it.copy(
                    currentCurveLine = curve.copy(points = reversedPts),
                    statusMessage = "Contour Curve Direction Reversed 180°"
                )
            }
            return
        }

        val ab = state.currentABLine ?: return
        val reversed = ABLine.fromPoints(
            aLocal = ab.bLocal,
            bLocal = ab.aLocal,
            aGeo = ab.bGeo,
            bGeo = ab.aGeo,
            name = "${ab.name} (180° Swapped)"
        )
        _uiState.update {
            it.copy(
                currentABLine = reversed,
                statusMessage = "AB Direction Swapped 180°"
            )
        }
    }

    fun startCurveRecording() {
        _uiState.update {
            it.copy(
                isRecordingCurve = true,
                recordingCurvePoints = listOf(it.vehicleState.localPivotPosition),
                statusMessage = "Recording Contour Curve... Drive baseline pass."
            )
        }
    }

    fun finishCurveRecording() {
        val pts = _uiState.value.recordingCurvePoints
        if (pts.size >= 3) {
            val curve = CurveLine(name = "Contour Curve 1", points = pts)
            _uiState.update {
                it.copy(
                    isRecordingCurve = false,
                    currentCurveLine = curve,
                    guidanceMode = GuidanceMode.CONTOUR_CURVE,
                    statusMessage = "Contour Curve Activated (${pts.size} points)"
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    isRecordingCurve = false,
                    statusMessage = "Not enough points for Curve."
                )
            }
        }
    }

    fun snapABLineToVehicle() {
        val state = _uiState.value
        val pivot = state.vehicleState.localPivotPosition
        val swathWidth = state.implementConfig.swathWidth

        if (state.guidanceMode == GuidanceMode.CONTOUR_CURVE && state.currentCurveLine != null) {
            val curve = state.currentCurveLine
            val curveResult = GuidanceAlgorithms.calculateCurveTracking(
                pivotPosition = pivot,
                tractorHeadingDeg = state.vehicleState.headingDeg,
                curveLine = curve,
                swathWidth = swathWidth
            )
            if (curveResult != null) {
                val shift = curveResult.crossTrackErrorMeters
                val shiftedPts = curve.points.map { pt ->
                    pt + (curveResult.swathDir.perpClockwise() * shift)
                }
                _uiState.update {
                    it.copy(
                        currentCurveLine = curve.copy(points = shiftedPts),
                        statusMessage = "Snapped Contour Curve to Current Track"
                    )
                }
                vibrateHaptic(40)
            }
            return
        }

        val abLine = state.currentABLine ?: return
        val swathResult = GuidanceAlgorithms.calculateSwathTracking(
            pivotPosition = pivot,
            tractorHeadingDeg = state.vehicleState.headingDeg,
            abLine = abLine,
            swathWidth = swathWidth
        )
        val shiftMeters = swathResult.crossTrackErrorMeters
        val shiftedA = abLine.aLocal + (abLine.normalVector * shiftMeters)
        val shiftedB = abLine.bLocal + (abLine.normalVector * shiftMeters)
        val aGeo = GeoUtils.localMetersToGeo(shiftedA, originGeo)
        val bGeo = GeoUtils.localMetersToGeo(shiftedB, originGeo)
        val updated = ABLine.fromPoints(shiftedA, shiftedB, aGeo, bGeo, abLine.name)
        _uiState.update {
            it.copy(
                currentABLine = updated,
                statusMessage = "Snapped Swath to Current Tractor Track"
            )
        }
        vibrateHaptic(40)
    }

    fun cycleSwath(direction: Int) {
        val state = _uiState.value
        val swathWidth = state.implementConfig.swathWidth

        if (state.guidanceMode == GuidanceMode.CONTOUR_CURVE && state.currentCurveLine != null) {
            val curve = state.currentCurveLine
            val shiftedPts = curve.points.mapIndexed { idx, pt ->
                val nextPt = curve.points.getOrNull(idx + 1) ?: pt
                val dir = (nextPt - pt).normalized()
                val norm = dir.perpClockwise()
                pt + (norm * (direction * swathWidth))
            }
            _uiState.update {
                it.copy(
                    currentCurveLine = curve.copy(points = shiftedPts),
                    statusMessage = if (direction > 0) "Shifted Contour Curve Right (+${swathWidth}m)" else "Shifted Contour Curve Left (-${swathWidth}m)"
                )
            }
            return
        }

        val abLine = state.currentABLine ?: return
        val shift = abLine.normalVector * (direction * swathWidth)
        val newA = abLine.aLocal + shift
        val newB = abLine.bLocal + shift
        val aGeo = GeoUtils.localMetersToGeo(newA, originGeo)
        val bGeo = GeoUtils.localMetersToGeo(newB, originGeo)
        val updated = ABLine.fromPoints(newA, newB, aGeo, bGeo, abLine.name)
        _uiState.update {
            it.copy(
                currentABLine = updated,
                statusMessage = if (direction > 0) "Shifted Swath Right (+${swathWidth}m)" else "Shifted Swath Left (-${swathWidth}m)"
            )
        }
    }

    fun nudgeSwath(centimeters: Double) {
        val state = _uiState.value
        val shiftMeters = centimeters / 100.0

        if (state.guidanceMode == GuidanceMode.CONTOUR_CURVE && state.currentCurveLine != null) {
            val curve = state.currentCurveLine
            val shiftedPts = curve.points.mapIndexed { idx, pt ->
                val nextPt = curve.points.getOrNull(idx + 1) ?: pt
                val dir = (nextPt - pt).normalized()
                val norm = dir.perpClockwise()
                pt + (norm * shiftMeters)
            }
            _uiState.update {
                it.copy(
                    currentCurveLine = curve.copy(points = shiftedPts),
                    statusMessage = "Nudged Contour Curve ${if (centimeters > 0) "+$centimeters" else "$centimeters"} cm"
                )
            }
            return
        }

        val abLine = state.currentABLine ?: return
        val shift = abLine.normalVector * shiftMeters
        val newA = abLine.aLocal + shift
        val newB = abLine.bLocal + shift
        val aGeo = GeoUtils.localMetersToGeo(newA, originGeo)
        val bGeo = GeoUtils.localMetersToGeo(newB, originGeo)
        val updated = ABLine.fromPoints(newA, newB, aGeo, bGeo, abLine.name)
        _uiState.update {
            it.copy(
                currentABLine = updated,
                statusMessage = "Nudged Swath ${if (centimeters > 0) "+$centimeters" else "$centimeters"} cm"
            )
        }
    }

    /**
     * Shifts active swath by 11.0 inches (27.94 cm) for 22-inch organic row-crop cultivating / inter-row weeding.
     */
    fun nudgeHalfRow22Inch(isRight: Boolean) {
        val cm = if (isRight) 27.94 else -27.94
        nudgeSwath(cm)
    }

    /**
     * Shifts active swath by 22.0 inches (55.88 cm) for 22-inch organic row-crop pass shifting.
     */
    fun nudgeFullRow22Inch(isRight: Boolean) {
        val cm = if (isRight) 55.88 else -55.88
        nudgeSwath(cm)
    }

    /**
     * Shifts active swath by arbitrary micro-inches (e.g. 0.5 in = 1.27 cm).
     */
    fun microNudgeInches(inches: Double) {
        nudgeSwath(inches * 2.54)
    }

    /**
     * 1-Tap WAS Auto-Zero calibration:
     * Reads current actual steer angle, adds it to the valve zero offset, and transmits updated PGN 250 to both USB & UDP.
     */
    fun zeroWasAtCurrentAngle() {
        val currentSteer = _uiState.value.vehicleState.actualSteerAngleDeg
        val currentConfig = _uiState.value.steerValveConfig
        val newOffset = currentConfig.wasZeroOffsetDeg + currentSteer
        val updatedConfig = currentConfig.copy(wasZeroOffsetDeg = newOffset)
        _uiState.update {
            it.copy(
                steerValveConfig = updatedConfig,
                statusMessage = "WAS Auto-Zeroed at ${String.format(java.util.Locale.US, "%+.2f°", currentSteer)} (New Offset: ${String.format(java.util.Locale.US, "%+.2f°", newOffset)})"
            )
        }
        usbManager.sendSteerConfig(updatedConfig)
        udpManager.sendSteerConfig(updatedConfig)
        vibrateHaptic(80)
    }

    fun toggleSectionOverride(sectionIndex: Int) {
        val currentOverrides = _uiState.value.sectionOverrides.toMutableList()
        while (currentOverrides.size <= sectionIndex) {
            currentOverrides.add(SectionOverride.AUTO)
        }
        val current = currentOverrides[sectionIndex]
        val next = when (current) {
            SectionOverride.AUTO -> SectionOverride.FORCED_ON
            SectionOverride.FORCED_ON -> SectionOverride.FORCED_OFF
            SectionOverride.FORCED_OFF -> SectionOverride.AUTO
        }
        currentOverrides[sectionIndex] = next
        _uiState.update {
            it.copy(
                sectionOverrides = currentOverrides,
                statusMessage = "Section ${sectionIndex + 1}: ${next.name}"
            )
        }
    }

    fun startBoundaryRecording() {
        _uiState.update {
            it.copy(
                isRecordingBoundary = true,
                fieldBoundary = listOf(it.vehicleState.localPivotPosition),
                statusMessage = "Recording Boundary... Drive perimeter of field."
            )
        }
    }

    fun addBoundaryPoint() {
        val currentPivot = _uiState.value.vehicleState.localPivotPosition
        _uiState.update {
            it.copy(
                fieldBoundary = it.fieldBoundary + currentPivot,
                statusMessage = "Added Boundary Vertex (${it.fieldBoundary.size + 1})"
            )
        }
    }

    fun finishBoundaryRecording() {
        val boundary = _uiState.value.fieldBoundary
        if (boundary.size >= 3) {
            val areaM2 = GuidanceAlgorithms.calculatePolygonAreaM2(boundary)
            val acres = areaM2 / 4046.86
            val headland = GuidanceAlgorithms.generateHeadlandPolygon(boundary, 12.0)
            _uiState.update {
                it.copy(
                    isRecordingBoundary = false,
                    headlandBoundary = headland,
                    currentField = it.currentField?.copy(areaAcres = acres) ?: FieldEntity(
                        name = "Field ${System.currentTimeMillis() % 1000}",
                        areaAcres = acres,
                        originLat = originGeo.latitude,
                        originLon = originGeo.longitude
                    ),
                    statusMessage = "Boundary Saved: ${String.format("%.2f", acres)} Acres"
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    isRecordingBoundary = false,
                    statusMessage = "Need at least 3 vertices for boundary."
                )
            }
        }
    }

    fun clearBoundary() {
        _uiState.update {
            it.copy(
                fieldBoundary = emptyList(),
                headlandBoundary = emptyList(),
                statusMessage = "Field Boundary Cleared"
            )
        }
    }

    fun createNewField(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val newField = FieldEntity(
                name = name,
                areaAcres = 0.0,
                workedAcres = 0.0,
                originLat = originGeo.latitude,
                originLon = originGeo.longitude
            )
            val id = repository.saveField(newField)
            withContext(Dispatchers.Main) {
                coverageSpatialGrid.clear()
                _uiState.update {
                    it.copy(
                        currentField = newField.copy(id = id),
                        fieldBoundary = emptyList(),
                        headlandBoundary = emptyList(),
                        currentABLine = null,
                        appliedSegments = emptyList(),
                        workedAcres = 0.0,
                        statusMessage = "Field '$name' Created & Opened"
                    )
                }
            }
        }
    }

    /**
     * Stores current field, its exact boundary, AB line coordinates, and all covered passes in Room DB.
     */
    fun saveCurrentFieldAndPasses() {
        viewModelScope.launch(Dispatchers.IO) {
            val state = _uiState.value
            val boundaryStr = state.fieldBoundary.joinToString(";") { "${it.x},${it.y}" }
            val headlandStr = state.headlandBoundary.joinToString(";") { "${it.x},${it.y}" }
            val ab = state.currentABLine

            val currentField = state.currentField ?: FieldEntity(
                name = "Field ${System.currentTimeMillis() % 10000}",
                originLat = originGeo.latitude,
                originLon = originGeo.longitude
            )

            val updatedField = currentField.copy(
                workedAcres = state.workedAcres,
                boundaryPointsJson = boundaryStr,
                headlandPointsJson = headlandStr,
                activeAbLineName = ab?.name ?: "",
                activeAbLineHeading = ab?.headingDeg ?: 0.0,
                activeAbLineAX = ab?.aLocal?.x ?: 0.0,
                activeAbLineAY = ab?.aLocal?.y ?: 0.0,
                activeAbLineBX = ab?.bLocal?.x ?: 0.0,
                activeAbLineBY = ab?.bLocal?.y ?: 0.0,
                hasActiveAbLine = ab != null,
                lastModifiedAt = System.currentTimeMillis()
            )

            val savedFieldId = repository.saveField(updatedField)
            val fieldId = if (currentField.id != 0L) currentField.id else savedFieldId

            val passEntities = state.appliedSegments.map { seg ->
                CoveredPassEntity(
                    fieldId = fieldId,
                    leftStartX = seg.leftStart.x,
                    leftStartY = seg.leftStart.y,
                    rightStartX = seg.rightStart.x,
                    rightStartY = seg.rightStart.y,
                    leftEndX = seg.leftEnd.x,
                    leftEndY = seg.leftEnd.y,
                    rightEndX = seg.rightEnd.x,
                    rightEndY = seg.rightEnd.y,
                    centerX = seg.center.x,
                    centerY = seg.center.y,
                    radius = seg.radius
                )
            }

            repository.savePassesForField(fieldId, passEntities)

            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        currentField = updatedField.copy(id = fieldId),
                        statusMessage = "Field '${updatedField.name}' & ${passEntities.size} passes saved"
                    )
                }
            }
        }
    }

    /**
     * Reopens a saved field, restoring its boundary, AB line, and exact coverage pass history
     * so the tractor can follow the exact same pattern right on top of previous swaths.
     */
    fun openSavedField(field: FieldEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val boundary = if (field.boundaryPointsJson.isNotBlank()) {
                field.boundaryPointsJson.split(";").mapNotNull { pair ->
                    val parts = pair.split(",")
                    if (parts.size == 2) {
                        val x = parts[0].toDoubleOrNull()
                        val y = parts[1].toDoubleOrNull()
                        if (x != null && y != null) Vec2(x, y) else null
                    } else null
                }
            } else emptyList()

            val headland = if (field.headlandPointsJson.isNotBlank()) {
                field.headlandPointsJson.split(";").mapNotNull { pair ->
                    val parts = pair.split(",")
                    if (parts.size == 2) {
                        val x = parts[0].toDoubleOrNull()
                        val y = parts[1].toDoubleOrNull()
                        if (x != null && y != null) Vec2(x, y) else null
                    } else null
                }
            } else emptyList()

            val abLine = if (field.hasActiveAbLine) {
                val aLocal = Vec2(field.activeAbLineAX, field.activeAbLineAY)
                val bLocal = Vec2(field.activeAbLineBX, field.activeAbLineBY)
                val aGeo = GeoUtils.localMetersToGeo(aLocal, originGeo)
                val bGeo = GeoUtils.localMetersToGeo(bLocal, originGeo)
                ABLine.fromPoints(
                    aLocal = aLocal,
                    bLocal = bLocal,
                    aGeo = aGeo,
                    bGeo = bGeo,
                    name = field.activeAbLineName.ifBlank { "Restored AB" }
                )
            } else null

            val passEntities = repository.getPassesForField(field.id)
            val restoredSegments = passEntities.map { p ->
                AppliedSwathSegment(
                    leftStart = Vec2(p.leftStartX, p.leftStartY),
                    rightStart = Vec2(p.rightStartX, p.rightStartY),
                    leftEnd = Vec2(p.leftEndX, p.leftEndY),
                    rightEnd = Vec2(p.rightEndX, p.rightEndY),
                    center = Vec2(p.centerX, p.centerY),
                    radius = p.radius
                )
            }

            // Re-anchor origin to field's registered origin if valid
            if (field.originLat != 0.0 && field.originLon != 0.0) {
                originGeo = GeoPoint(field.originLat, field.originLon, 317.0)
            }

            val currentGeo = _uiState.value.vehicleState.geoPosition
            val newLocalPivot = if (currentGeo.latitude != 0.0 && currentGeo.longitude != 0.0) {
                GeoUtils.geoToLocalMeters(currentGeo, originGeo)
            } else if (abLine != null) {
                abLine.aLocal
            } else if (boundary.isNotEmpty()) {
                boundary.first()
            } else {
                Vec2(0.0, 0.0)
            }

            withContext(Dispatchers.Main) {
                coverageSpatialGrid.clear()
                coverageSpatialGrid.addAll(restoredSegments)
                _uiState.update {
                    it.copy(
                        currentField = field,
                        activeJobName = field.name,
                        fieldBoundary = boundary,
                        headlandBoundary = headland,
                        currentABLine = abLine,
                        appliedSegments = restoredSegments,
                        workedAcres = field.workedAcres,
                        isFieldJobActive = true,
                        vehicleState = it.vehicleState.copy(localPivotPosition = newLocalPivot),
                        showFieldDialog = false,
                        statusMessage = "Loaded '${field.name}': ${boundary.size} boundary pts, AB line & ${restoredSegments.size} passes restored"
                    )
                }
            }
        }
    }

    fun deleteSavedField(fieldId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteField(fieldId)
            if (_uiState.value.currentField?.id == fieldId) {
                withContext(Dispatchers.Main) {
                    initDefaultField()
                }
            }
        }
    }

    fun cycleCameraViewMode() {
        val nextMode = when (_uiState.value.cameraViewMode) {
            CameraViewMode.CAB_3D -> CameraViewMode.BIRD_EYE_2D
            CameraViewMode.BIRD_EYE_2D -> CameraViewMode.NORTH_UP
            CameraViewMode.NORTH_UP -> CameraViewMode.FREE_PAN
            CameraViewMode.FREE_PAN -> CameraViewMode.CAB_3D
        }
        val isHeadUp = nextMode == CameraViewMode.CAB_3D || nextMode == CameraViewMode.BIRD_EYE_2D
        _uiState.update {
            it.copy(
                cameraViewMode = nextMode,
                isHeadUpMode = isHeadUp,
                statusMessage = "Camera: ${nextMode.name.replace("_", " ")}"
            )
        }
        vibrateHaptic(40)
    }

    fun setCameraViewMode(mode: CameraViewMode) {
        _uiState.update {
            it.copy(
                cameraViewMode = mode,
                isHeadUpMode = (mode == CameraViewMode.CAB_3D || mode == CameraViewMode.BIRD_EYE_2D)
            )
        }
    }

    fun connectNtrip(config: NtripConfig) {
        ntripConfig = config
        ntripClient.start(viewModelScope, config)
    }

    fun disconnectNtrip() {
        ntripClient.stop()
    }

    fun sendSteerSettings(kp: Int, ki: Int, kd: Int, maxSteer: Int, countsPerDeg: Int) {
        udpManager.sendSteerSettings(kp, ki, kd, maxSteer, countsPerDeg)
    }

    fun toggleMasterSection() {
        _uiState.update {
            val newMaster = !it.implementConfig.isMasterActive
            it.copy(
                implementConfig = it.implementConfig.copy(isMasterActive = newMaster),
                statusMessage = if (newMaster) "Section Master ON" else "Section Master OFF"
            )
        }
    }

    fun toggleAutoSectionControl() {
        _uiState.update {
            val newAuto = !it.implementConfig.isAutoSectionControl
            it.copy(
                implementConfig = it.implementConfig.copy(isAutoSectionControl = newAuto),
                statusMessage = if (newAuto) "Auto Section Control ON" else "Manual Section Control"
            )
        }
    }

    fun toggleNightMode() {
        _uiState.update { it.copy(isNightMode = !it.isNightMode) }
    }

    fun toggleSatelliteOverlay() {
        _uiState.update {
            val next = !it.showSatelliteOverlay
            it.copy(
                showSatelliteOverlay = next,
                statusMessage = if (next) "Basemap ON: ${it.satelliteMapProvider.label}" else "Basemap OFF (Field Grid Only - Ultra Fast)"
            )
        }
    }

    fun setSatelliteOverlay(enabled: Boolean) {
        _uiState.update {
            it.copy(
                showSatelliteOverlay = enabled,
                statusMessage = if (enabled) "Basemap ON: ${it.satelliteMapProvider.label}" else "Basemap OFF (Field Grid Only - Ultra Fast)"
            )
        }
    }

    fun cycleBasemapMode() {
        val currentShow = _uiState.value.showSatelliteOverlay
        val currentProvider = _uiState.value.satelliteMapProvider

        if (!currentShow) {
            // First click: Enable Esri World Topographic (High-Res Contours & Relief)
            _uiState.update {
                it.copy(
                    showSatelliteOverlay = true,
                    satelliteMapProvider = MapTileProvider.ESRI_WORLD_TOPO,
                    showTopographyMap = true,
                    statusMessage = "Basemap: Esri World Topographic (Crisp)"
                )
            }
        } else {
            val providers = MapTileProvider.values()
            val currentIndex = providers.indexOf(currentProvider)
            if (currentIndex < providers.size - 1) {
                val nextProvider = providers[currentIndex + 1]
                _uiState.update {
                    it.copy(
                        showSatelliteOverlay = true,
                        satelliteMapProvider = nextProvider,
                        statusMessage = "Basemap: ${nextProvider.label}"
                    )
                }
            } else {
                // Cycled through all -> Grid + Topo Contours Only
                _uiState.update {
                    it.copy(
                        showSatelliteOverlay = false,
                        statusMessage = "Basemap: OFF (Precision Field Grid & Elevation Contours)"
                    )
                }
            }
        }
    }

    fun cycleSatelliteProvider() {
        val providers = MapTileProvider.values()
        val currentIndex = providers.indexOf(_uiState.value.satelliteMapProvider)
        val next = providers[(currentIndex + 1) % providers.size]
        _uiState.update {
            it.copy(
                satelliteMapProvider = next,
                showSatelliteOverlay = true,
                statusMessage = "Basemap: ${next.label}"
            )
        }
    }

    fun setBasemapProvider(provider: MapTileProvider) {
        _uiState.update {
            it.copy(
                satelliteMapProvider = provider,
                showSatelliteOverlay = true,
                statusMessage = "Basemap: ${provider.label}"
            )
        }
    }

    fun toggleHeadUp() {
        _uiState.update { it.copy(isHeadUpMode = !it.isHeadUpMode) }
    }

    fun zoomIn() {
        _uiState.update { it.copy(mapZoom = (it.mapZoom * 1.35f).coerceAtMost(12.0f)) }
    }

    fun zoomOut() {
        _uiState.update { it.copy(mapZoom = (it.mapZoom / 1.35f).coerceAtLeast(0.2f)) }
    }

    /**
     * Smooth continuous pinch-to-zoom on the tractor viewfinder
     */
    fun onPinchZoom(zoomChangeFactor: Float) {
        if (zoomChangeFactor.isNaN() || zoomChangeFactor <= 0f) return
        _uiState.update { current ->
            val newZoom = (current.mapZoom * zoomChangeFactor).coerceIn(0.2f, 15.0f)
            current.copy(mapZoom = newZoom)
        }
    }

    /**
     * Toggles hillside terrain compensation on/off. When off, steering runs in standard flat GPS autosteer mode.
     */
    fun toggleTerrainCompensation(enabled: Boolean? = null) {
        val next = enabled ?: !_uiState.value.isTerrainCompensationEnabled
        topographyRepo.isEnabled = next
        _uiState.update {
            it.copy(
                isTerrainCompensationEnabled = next,
                statusMessage = if (next) "Hillside Topography Autosteer ENABLED" else "Hillside Autosteer DISABLED (Basic GPS Mode)"
            )
        }
        vibrateHaptic(50)
    }

    /**
     * Toggles the 0.5 mile by 0.5 mile topographic contour map layer on/off.
     */
    fun toggleTopographyMap(enabled: Boolean? = null) {
        val next = enabled ?: !_uiState.value.showTopographyMap
        _uiState.update {
            it.copy(
                showTopographyMap = next,
                statusMessage = if (next) "0.5-Mile Topography Map & Contours ON" else "Topography Map Layer OFF"
            )
        }
        vibrateHaptic(40)
    }

    /**
     * Toggles 3D terrain elevation mesh and draping in Cab View mode on/off.
     */
    fun toggle3DTerrainMesh(enabled: Boolean? = null) {
        val next = enabled ?: !_uiState.value.show3DTerrainMesh
        _uiState.update {
            it.copy(
                show3DTerrainMesh = next,
                statusMessage = if (next) "3D Terrain Elevation Mesh ON (Cab 3D View)" else "3D Terrain Flat Ground Grid (Basic GPS)"
            )
        }
        vibrateHaptic(40)
    }

    /**
     * Emergency / Quick-turn-off master switch: Turns off all hillside compensation, topography mapping,
     * and 3D terrain mesh to instantly revert to basic, clean GPS autosteer.
     */
    fun resetToBasicGpsOnly() {
        topographyRepo.isEnabled = false
        _uiState.update {
            it.copy(
                isTerrainCompensationEnabled = false,
                showTopographyMap = false,
                show3DTerrainMesh = false,
                statusMessage = "Bypassed: Basic GPS Autosteer Active (All Terrain Off)"
            )
        }
        vibrateHaptic(80)
    }

    /**
     * One-tap master switch to activate all topography, 3D terrain, and hillside autosteer features.
     */
    fun activateAllTerrainFeatures() {
        topographyRepo.isEnabled = true
        _uiState.update {
            it.copy(
                isTerrainCompensationEnabled = true,
                showTopographyMap = true,
                show3DTerrainMesh = true,
                statusMessage = "0.5-Mile Topography & 3D Hillside Autosteer Active"
            )
        }
        vibrateHaptic(80)
    }

    fun toggleSimulationMode() {
        val current = _uiState.value.gnssSource
        val newSource = when (current) {
            GnssSourceMode.ANDROID_GPS -> GnssSourceMode.SIMULATOR
            GnssSourceMode.SIMULATOR -> {
                if (_uiState.value.usbStats.connectionState == com.example.agopengps.io.UsbConnectionState.CONNECTED) {
                    GnssSourceMode.USB_AIO_TEENSY
                } else {
                    GnssSourceMode.ANDROID_GPS
                }
            }
            else -> GnssSourceMode.ANDROID_GPS
        }
        setGnssSource(newSource)
        val msg = when (newSource) {
            GnssSourceMode.SIMULATOR -> "Simulation Mode ON - Drive with Slider Controls"
            GnssSourceMode.ANDROID_GPS -> "Internal Tablet/Phone GPS Active (Fused 1-5Hz)"
            else -> "Hardware Mode ACTIVE - Listening to ${newSource.name}"
        }
        _uiState.update { it.copy(statusMessage = msg) }
    }

    fun toggleAutoUTurn() {
        _uiState.update {
            val enabled = !it.isAutoUTurnEnabled
            val msg = if (enabled) "Auto U-Turn Enabled (Dubins Arc + NH 8870 Prompts)" else "Auto U-Turn Disabled"
            it.copy(isAutoUTurnEnabled = enabled, statusMessage = msg)
        }
    }

    fun triggerManualUTurn() {
        if (!_uiState.value.autoUTurnActive) {
            soundManager.playEndOfPassChime(viewModelScope)
            vibrateHaptic(100)
            _uiState.update {
                it.copy(
                    autoUTurnActive = true,
                    uTurnPrompt = "NH 8870: LIFT IMPLEMENT NOW (TURNING)",
                    statusMessage = "Manual U-Turn: LIFT IMPLEMENT NOW"
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    autoUTurnActive = false,
                    uTurnPrompt = null,
                    statusMessage = "U-Turn Disengaged: Resuming Swath Tracking"
                )
            }
        }
    }

    fun importFieldFromText(content: String, name: String = "Imported Field"): Boolean {
        val result = com.example.agopengps.data.FieldDataImporter.parseFieldData(content, name) ?: return false
        val entity = FieldEntity(
            id = 0L,
            name = result.fieldName,
            areaAcres = result.areaAcres,
            workedAcres = 0.0,
            originLat = result.originGeo.latitude,
            originLon = result.originGeo.longitude,
            boundaryPointsJson = result.boundaryLocal.joinToString(";") { "${it.x},${it.y}" },
            headlandPointsJson = result.headlandBoundary.joinToString(";") { "${it.x},${it.y}" },
            activeAbLineName = result.abLine?.name ?: "",
            activeAbLineHeading = result.abLine?.headingDeg ?: 0.0,
            activeAbLineAX = result.abLine?.aLocal?.x ?: 0.0,
            activeAbLineAY = result.abLine?.aLocal?.y ?: 0.0,
            activeAbLineBX = result.abLine?.bLocal?.x ?: 0.0,
            activeAbLineBY = result.abLine?.bLocal?.y ?: 0.0,
            hasActiveAbLine = result.abLine != null,
            createdAt = System.currentTimeMillis(),
            lastModifiedAt = System.currentTimeMillis()
        )
        viewModelScope.launch(Dispatchers.IO) {
            val fieldId = repository.saveField(entity)
            withContext(Dispatchers.Main) {
                openSavedField(entity.copy(id = fieldId))
            }
        }
        _uiState.update {
            it.copy(
                statusMessage = "Imported '${result.fieldName}' (${String.format(java.util.Locale.US, "%.1f", result.areaAcres)} ac) with Boundary & AB Line"
            )
        }
        return true
    }

    fun resetWorkedArea() {
        coverageSpatialGrid.clear()
        _uiState.update {
            it.copy(appliedSegments = emptyList(), workedAcres = 0.0, statusMessage = "Coverage Map Cleared")
        }
    }

    fun setSimSpeed(speedKmh: Double) {
        simulator.speedKmh = speedKmh.coerceIn(0.0, 25.0)
    }

    fun setManualSteerAngle(steerAngleDeg: Double) {
        simulator.manualSteerInputDeg = steerAngleDeg
    }

    fun releaseManualSteer() {
        simulator.manualSteerInputDeg = 0.0
    }

    fun isSimReverse(): Boolean = simulator.isReverse

    fun toggleSimReverse() {
        simulator.isReverse = !simulator.isReverse
        _uiState.update { it.copy(statusMessage = if (simulator.isReverse) "Simulator: REVERSE Gear" else "Simulator: FORWARD Gear") }
    }

    fun setGnssSource(source: GnssSourceMode) {
        _uiState.update { 
            it.copy(
                gnssSource = source,
                vehicleState = it.vehicleState.copy(
                    speedKmh = if (source == GnssSourceMode.ANDROID_GPS) 0.0 else it.vehicleState.speedKmh,
                    targetSteerAngleDeg = 0.0
                ),
                statusMessage = if (source == GnssSourceMode.ANDROID_GPS) "Tablet Internal GNSS Mode Active" else "Switched GNSS Mode: ${source.name}"
            ) 
        }
        if (source == GnssSourceMode.ANDROID_GPS) {
            startAndroidLocationUpdates()
        } else {
            stopAndroidLocationUpdates()
        }
    }

    fun setManualFarmLocation(lat: Double, lon: Double, alt: Double = 310.0, name: String = "Farm Field Location") {
        val targetGeo = GeoPoint(lat, lon, alt)
        originGeo = targetGeo
        hasInitializedTabletOrigin = true
        filteredLat = targetGeo.latitude
        filteredLon = targetGeo.longitude
        isGpsFilterInitialized = true
        lastValidMovedGeoPoint = targetGeo
        lastRawGeoPoint = targetGeo
        stationaryAnchorGeo = targetGeo
        rawLocalAntennaPos = Vec2(0.0, 0.0)

        // Clear DEM cache and reload fresh square for the new coordinates
        topographyRepo.clear()

        val boundary = listOf(
            Vec2(-160.0, -160.0),
            Vec2(160.0, -160.0),
            Vec2(160.0, 160.0),
            Vec2(-160.0, 160.0)
        )
        val headland = GuidanceAlgorithms.generateHeadlandPolygon(boundary, 12.0)
        val aLocal = Vec2(0.0, -130.0)
        val bLocal = Vec2(0.0, 130.0)
        val aGeo = GeoUtils.localMetersToGeo(aLocal, originGeo)
        val bGeo = GeoUtils.localMetersToGeo(bLocal, originGeo)
        val defaultAB = ABLine.fromPoints(aLocal, bLocal, aGeo, bGeo, name = "North-South Baseline")

        simulator.resetTo(Vec2(0.0, -120.0), 0.0)

        _uiState.update {
            it.copy(
                fieldBoundary = boundary,
                headlandBoundary = headland,
                currentABLine = defaultAB,
                currentField = it.currentField?.copy(
                    name = name,
                    originLat = targetGeo.latitude,
                    originLon = targetGeo.longitude
                ),
                vehicleState = it.vehicleState.copy(
                    geoPosition = targetGeo,
                    localPivotPosition = Vec2(0.0, 0.0),
                    speedKmh = 0.0
                ),
                statusMessage = "Field Located at $name (${String.format(Locale.US, "%.5f", lat)}, ${String.format(Locale.US, "%.5f", lon)})"
            )
        }
        vibrateHaptic(80)
    }

    fun recenterOriginToCurrentGps() {
        val currentGeo = _uiState.value.vehicleState.geoPosition
        val ctx = getApplication<Application>()
        val locManager = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        var bestLoc: Location? = null
        val providers = listOf(LocationManager.GPS_PROVIDER, "fused", LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        for (p in providers) {
            try {
                val l = locManager?.getLastKnownLocation(p)
                if (l != null && (bestLoc == null || l.time > bestLoc.time)) {
                    bestLoc = l
                }
            } catch (_: Exception) {}
        }

        if (bestLoc != null && bestLoc.latitude != 0.0) {
            setManualFarmLocation(bestLoc.latitude, bestLoc.longitude, bestLoc.altitude, "Tablet GPS Location")
        } else if (currentGeo.latitude != 0.0 || currentGeo.longitude != 0.0) {
            setManualFarmLocation(currentGeo.latitude, currentGeo.longitude, currentGeo.altitude, "Field Center")
        }
    }

    private fun processAndroidLocation(loc: Location, isInitialFix: Boolean = false) {
        if (_uiState.value.gnssSource != GnssSourceMode.ANDROID_GPS) return
        val rawGeo = GeoPoint(loc.latitude, loc.longitude, loc.altitude)

        // Automatically anchor field datum to tablet GPS upon receiving first fix only if no field is loaded
        if (!hasInitializedTabletOrigin && _uiState.value.currentField == null) {
            hasInitializedTabletOrigin = true
            setManualFarmLocation(rawGeo.latitude, rawGeo.longitude, rawGeo.altitude, "Tablet GPS Field")
        }

        val nowMs = System.currentTimeMillis()

        // Filter out inaccurate or delayed cellular tower fixes if we have active GPS satellite fixes
        val isNetworkProvider = loc.provider == LocationManager.NETWORK_PROVIDER
        val isHardwareGps = loc.provider == LocationManager.GPS_PROVIDER || loc.provider == "fused"
        if (isHardwareGps) {
            lastHardwareGpsFixTimeMs = nowMs
        } else if (isNetworkProvider) {
            if ((nowMs - lastHardwareGpsFixTimeMs) < 3000L || (loc.hasAccuracy() && loc.accuracy > 25.0f)) {
                // Reject stale cellular cell-tower triangulation that causes 9-10s hitching
                return
            }
        }

        val dtSec = if (lastFixTimeMs > 0) ((nowMs - lastFixTimeMs) / 1000.0).coerceIn(0.05, 2.5) else 0.5
        lastFixTimeMs = nowMs

        val accuracy = if (loc.hasAccuracy()) loc.accuracy.coerceIn(0.5f, 25.0f) else 3.0f

        // Calculate displacement from previous raw fix
        val prevMoved = lastValidMovedGeoPoint
        val rawDistFromPrev = if (prevMoved != null) GeoUtils.distanceBetweenMeters(rawGeo, prevMoved) else 0.0

        val sensorSpeedKmh = if (loc.hasSpeed() && loc.speed > 0.05f) {
            (loc.speed * 3.6).toDouble()
        } else if (dtSec > 0.0 && rawDistFromPrev >= 0.08) {
            (rawDistFromPrev / dtSec) * 3.6
        } else {
            0.0
        }

        // Fast zero-drift threshold: clamp if vehicle is essentially stationary (<0.10m and <0.2 km/h)
        val isStationary = sensorSpeedKmh < 0.2 && rawDistFromPrev < 0.10

        val effectiveGeo = rawGeo
        val effectiveSpeedKmh = if (isStationary) 0.0 else sensorSpeedKmh.coerceAtMost(120.0)
        if (effectiveSpeedKmh >= 0.2 || prevMoved == null) {
            lastValidMovedGeoPoint = effectiveGeo
        }

        // Vector Heading calculation:
        // 1) When moving (> 0.2 km/h), prioritize true course-over-ground vector bearing
        // 2) When stationary (< 0.2 km/h), hold the last valid moved heading or device azimuth (0-drift)
        val heading = if (!isStationary && effectiveSpeedKmh >= 0.2) {
            val hasValidBearing = loc.hasBearing() && loc.bearing != 0.0f
            if (hasValidBearing) {
                val gpsBearing = (loc.bearing.toDouble() + 360.0) % 360.0
                lastMovedHeadingDeg = gpsBearing
                gpsBearing
            } else if (prevMoved != null && rawDistFromPrev >= 0.15) {
                val computedBearing = GeoUtils.bearingDegrees(prevMoved, effectiveGeo)
                val diff = GeoUtils.normalizeAngleDeg(computedBearing - lastMovedHeadingDeg)
                lastMovedHeadingDeg = (lastMovedHeadingDeg + diff * 0.85 + 360.0) % 360.0
                lastMovedHeadingDeg
            } else {
                lastMovedHeadingDeg.takeIf { it != 0.0 } ?: _uiState.value.vehicleState.headingDeg
            }
        } else {
            if (lastMovedHeadingDeg != 0.0) {
                lastMovedHeadingDeg
            } else if (tabletAzimuthDeg > 0.0) {
                tabletAzimuthDeg
            } else {
                _uiState.value.vehicleState.headingDeg
            }
        }

        // Update raw local antenna coordinates
        val rawLocal = GeoUtils.geoToLocalMeters(effectiveGeo, originGeo)
        rawLocalAntennaPos = rawLocal

        // Derive un-compounded rear axle ground pivot
        val state = _uiState.value
        val roll = if (tabletRollDeg != 0.0) tabletRollDeg else state.vehicleState.rollDeg
        val compensatedPivot = GuidanceAlgorithms.compensateTabletPosition(
            tabletLocal = rawLocal,
            headingDeg = heading,
            rollDeg = roll,
            vehicle = state.vehicleConfig,
            rollZeroOffset = state.guidanceSettings.rollZeroOffsetDeg
        )

        // Tablet GPS Fix Quality rating
        val fixQuality = if (loc.hasAccuracy() && loc.accuracy < 2.5f) {
            FixQuality.DGPS
        } else {
            FixQuality.TABLET_INTERNAL
        }

        val sats = usedInFixSatellitesCount.coerceAtLeast(visibleSatellitesCount).coerceAtLeast(12)

        _uiState.update { current ->
            current.copy(
                vehicleState = current.vehicleState.copy(
                    geoPosition = effectiveGeo,
                    localPivotPosition = compensatedPivot,
                    headingDeg = heading,
                    speedKmh = effectiveSpeedKmh,
                    rollDeg = roll,
                    fixQuality = fixQuality,
                    satellites = sats
                )
            )
        }
    }

    private fun startAndroidLocationUpdates() {
        try {
            val ctx = getApplication<Application>()
            locationManager = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            // Check for immediate cached / last-known location across all providers (GPS, Fused, Network, Passive)
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                "fused",
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )

            var bestCachedLoc: Location? = null
            for (p in providers) {
                try {
                    val loc = locationManager?.getLastKnownLocation(p)
                    if (loc != null) {
                        if (bestCachedLoc == null || loc.time > bestCachedLoc.time || (loc.hasAccuracy() && loc.accuracy < (bestCachedLoc.accuracy))) {
                            bestCachedLoc = loc
                        }
                    }
                } catch (_: SecurityException) {}
                catch (_: Exception) {}
            }

            if (bestCachedLoc != null) {
                processAndroidLocation(bestCachedLoc, isInitialFix = true)
            }

            locationListener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    processAndroidLocation(loc)
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            // Google Play Services FusedLocationProviderClient for high-speed sub-second fixes (500ms / 2Hz)
            try {
                fusedLocationClient = LocationServices.getFusedLocationProviderClient(ctx)
                val gmsRequest = com.google.android.gms.location.LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500L)
                    .setMinUpdateIntervalMillis(200L)
                    .setMinUpdateDistanceMeters(0.0f)
                    .setMaxUpdateDelayMillis(1000L)
                    .build()

                fusedLocationCallback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        for (loc in result.locations) {
                            processAndroidLocation(loc)
                        }
                    }
                }
                fusedLocationClient?.requestLocationUpdates(gmsRequest, fusedLocationCallback!!, Looper.getMainLooper())
            } catch (_: SecurityException) {}
            catch (_: Exception) {}

            // Primary High-Accuracy Satellite Providers (GPS, Fused)
            val gpsProviders = listOf(
                LocationManager.GPS_PROVIDER,
                "fused"
            )

            for (p in gpsProviders) {
                try {
                    if (locationManager?.allProviders?.contains(p) == true || locationManager?.isProviderEnabled(p) == true) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val request = android.location.LocationRequest.Builder(50L) // 50ms interval (20Hz)
                                .setQuality(android.location.LocationRequest.QUALITY_HIGH_ACCURACY)
                                .setMinUpdateIntervalMillis(20L)
                                .setMinUpdateDistanceMeters(0.0f)
                                .build()
                            locationManager?.requestLocationUpdates(
                                p,
                                request,
                                ctx.mainExecutor,
                                locationListener!!
                            )
                        } else {
                            locationManager?.requestLocationUpdates(
                                p,
                                0L,
                                0.0f,
                                locationListener!!,
                                Looper.getMainLooper()
                            )
                        }
                    }
                } catch (_: SecurityException) {}
                catch (_: Exception) {}
            }

            // Passive / Network provider as fallback only if GPS is not enabled
            val fallbackProviders = listOf(
                LocationManager.PASSIVE_PROVIDER
            )
            for (p in fallbackProviders) {
                try {
                    if (locationManager?.allProviders?.contains(p) == true || locationManager?.isProviderEnabled(p) == true) {
                        locationManager?.requestLocationUpdates(
                            p,
                            500L,
                            0.0f,
                            locationListener!!,
                            Looper.getMainLooper()
                        )
                    }
                } catch (_: SecurityException) {}
                catch (_: Exception) {}
            }

            // Register GnssStatus callback for satellite constellations (GPS, GLONASS, Galileo, BeiDou)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                gnssStatusCallback = object : GnssStatus.Callback() {
                    override fun onSatelliteStatusChanged(status: GnssStatus) {
                        val totalSats = status.satelliteCount
                        var usedInFix = 0
                        for (i in 0 until totalSats) {
                            if (status.usedInFix(i)) {
                                usedInFix++
                            }
                        }
                        visibleSatellitesCount = totalSats
                        usedInFixSatellitesCount = usedInFix
                        currentHdop = when {
                            usedInFix >= 16 -> 0.7
                            usedInFix >= 12 -> 0.85
                            usedInFix >= 8 -> 1.1
                            usedInFix >= 5 -> 1.6
                            else -> 2.4
                        }
                    }
                }
                try {
                    locationManager?.registerGnssStatusCallback(gnssStatusCallback!!, Handler(Looper.getMainLooper()))
                } catch (_: SecurityException) {}
                catch (_: Exception) {}
            }

            // Register tablet IMU rotation vector sensor & accelerometer
            sensorManager = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val rotSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
            val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

            sensorEventListener = object : SensorEventListener {
                private val rotationMatrix = FloatArray(9)
                private val orientationValues = FloatArray(3)

                override fun onSensorChanged(event: SensorEvent) {
                    when (event.sensor.type) {
                        Sensor.TYPE_ROTATION_VECTOR -> {
                            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                            SensorManager.getOrientation(rotationMatrix, orientationValues)
                            val azimuthRad = orientationValues[0].toDouble()
                            tabletAzimuthDeg = (Math.toDegrees(azimuthRad) + 360.0) % 360.0
                            val rollRad = orientationValues[2].toDouble()
                            tabletRollDeg = Math.toDegrees(rollRad)
                        }
                        Sensor.TYPE_ACCELEROMETER -> {
                            if (rotSensor == null) {
                                val ax = event.values[0]
                                val ay = event.values[1]
                                val az = event.values[2]
                                val rawRollRad = atan2(ax.toDouble(), sqrt(ay.toDouble() * ay + az.toDouble() * az))
                                tabletRollDeg = Math.toDegrees(rawRollRad)
                            }
                        }
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }

            if (rotSensor != null) {
                sensorManager?.registerListener(sensorEventListener, rotSensor, SensorManager.SENSOR_DELAY_UI)
            }
            if (accel != null) {
                sensorManager?.registerListener(sensorEventListener, accel, SensorManager.SENSOR_DELAY_UI)
            }

            _uiState.update { it.copy(statusMessage = "Tablet Internal GNSS Active - Re-centered on Location") }
        } catch (e: SecurityException) {
            _uiState.update { it.copy(statusMessage = "Location permission needed for internal GPS") }
        }
    }

    private fun stopAndroidLocationUpdates() {
        fusedLocationCallback?.let { fusedLocationClient?.removeLocationUpdates(it) }
        fusedLocationCallback = null
        fusedLocationClient = null
        locationListener?.let { locationManager?.removeUpdates(it) }
        locationListener = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && gnssStatusCallback != null) {
            locationManager?.unregisterGnssStatusCallback(gnssStatusCallback!!)
            gnssStatusCallback = null
        }
        sensorEventListener?.let { sensorManager?.unregisterListener(it) }
        sensorEventListener = null
    }

    fun updateGuidanceSettings(settings: GuidanceSettings) {
        _uiState.update { it.copy(guidanceSettings = settings, statusMessage = "Guidance Settings Updated") }
    }

    fun updateVehicleConfig(config: VehicleConfig) {
        _uiState.update { it.copy(vehicleConfig = config, statusMessage = "Vehicle Dimensions Updated") }
    }

    fun updateImplementConfig(config: ImplementConfig) {
        _uiState.update { it.copy(implementConfig = config, statusMessage = "Implement Specs Updated") }
    }

    fun updateSteerValveConfig(config: SteerValveConfig) {
        _uiState.update { it.copy(steerValveConfig = config, statusMessage = "Steering Valve Parameters Updated") }
        usbManager.sendSteerConfig(config)
        usbManager.sendSteerSettings(
            kp = config.proportionalGainKp,
            ki = config.integralGainKi,
            kd = config.derivativeGainKd,
            maxSteer = _uiState.value.vehicleConfig.maxSteerAngleDeg.roundToInt(),
            countsPerDeg = config.wasCountsPerDeg
        )
        udpManager.sendSteerConfig(config)
        udpManager.sendSteerSettings(
            kp = config.proportionalGainKp,
            ki = config.integralGainKi,
            kd = config.derivativeGainKd,
            maxSteerDeg = _uiState.value.vehicleConfig.maxSteerAngleDeg.roundToInt(),
            countsPerDeg = config.wasCountsPerDeg
        )
    }

    fun setGnssReceiverType(type: GnssReceiverType) {
        _uiState.update { it.copy(gnssReceiverType = type, statusMessage = "Receiver profile set to ${type.name}") }
    }

    fun connectUsb() {
        connectUsbDevice()
    }

    fun disconnectUsb() {
        disconnectUsbDevice()
    }

    fun connectUsbDevice() {
        usbManager.scanAndConnect(viewModelScope)
        _uiState.update { it.copy(gnssSource = GnssSourceMode.USB_AIO_TEENSY, statusMessage = "Scanning for Teensy 4.1 AIO Board...") }
    }

    fun disconnectUsbDevice() {
        usbManager.disconnect()
        _uiState.update { it.copy(statusMessage = "USB Disconnected") }
    }

    fun loadNewHolland8870Preset() {
        val nhConfig = VehicleConfig(
            wheelbase = 2.845,       // 112 inches (standard Genesis NH 8870 wheelbase)
            trackWidth = 2.083,      // 82 inches (row-crop 30" center-to-center)
            antennaHeight = 3.20,    // 10.5 feet (cab roof mount)
            antennaPivotOffset = 0.20, // 8 inches ahead of rear axle
            hitchLength = -1.22,     // 48 inches behind rear axle
            maxSteerAngleDeg = 42.0  // SuperSteer / standard FWD axle lock
        )
        val nhImplement = _uiState.value.implementConfig.copy(
            implementType = ImplementType.THREE_POINT_MOUNTED,
            toolWidth = 9.144,       // 30 feet (12-row 30-inch toolbar)
            offsetBehindTractor = 0.914, // 3.0 feet behind 3-point lift arms
            numSections = 8
        )
        val nhValve = SteerValveConfig(
            valveType = SteerValveType.DUAL_SOLENOID_PWM,
            wasCountsPerDeg = 125,
            wasZeroOffsetDeg = 0.0,
            proportionalGainKp = 35,
            integralGainKi = 10,
            derivativeGainKd = 18,
            minPwmDeadband = 22,
            maxPwmLimit = 245,
            isWasInverted = false,
            isMotorInverted = false,
            disengagePressurePsi = 180
        )
        _uiState.update {
            it.copy(
                vehicleConfig = nhConfig,
                implementConfig = nhImplement,
                steerValveConfig = nhValve,
                statusMessage = "Loaded New Holland 8870 + 3-Pt Toolbar + Dual-Solenoid PWM Valve Preset"
            )
        }
        usbManager.sendSteerConfig(nhValve)
        usbManager.sendSteerSettings(
            kp = nhValve.proportionalGainKp,
            ki = nhValve.integralGainKi,
            kd = nhValve.derivativeGainKd,
            maxSteer = 42,
            countsPerDeg = nhValve.wasCountsPerDeg
        )
        udpManager.sendSteerConfig(nhValve)
        udpManager.sendSteerSettings(
            kp = nhValve.proportionalGainKp,
            ki = nhValve.integralGainKi,
            kd = nhValve.derivativeGainKd,
            maxSteerDeg = 42,
            countsPerDeg = nhValve.wasCountsPerDeg
        )
    }

    fun openSettingsDialog(show: Boolean) {
        _uiState.update { it.copy(showSettingsDialog = show) }
    }

    fun openFieldDialog(show: Boolean) {
        _uiState.update { it.copy(showFieldDialog = show) }
    }

    fun openAgIoDialog(show: Boolean) {
        _uiState.update { it.copy(showAgIoDialog = show) }
    }

    fun openUdpDialog(show: Boolean) {
        _uiState.update { it.copy(showUdpDialog = show) }
    }

    fun openVirtualDrive(show: Boolean) {
        _uiState.update { it.copy(showVirtualDrive = show) }
    }

    fun openWasCalibrationDialog(show: Boolean) {
        _uiState.update { it.copy(showWasCalibrationDialog = show) }
    }

    fun openUm982Dialog(show: Boolean) {
        _uiState.update { it.copy(showUm982Dialog = show) }
    }

    fun openNmeaDiagnosticsDialog(show: Boolean) {
        _uiState.update { it.copy(showNmeaDiagnosticsDialog = show) }
    }

    fun toggleOperatorDashboard() {
        _uiState.update { it.copy(showOperatorDashboard = !it.showOperatorDashboard) }
    }

    fun openOperatorDashboard(show: Boolean) {
        _uiState.update { it.copy(showOperatorDashboard = show) }
    }

    fun sendNmeaCommand(command: String) {
        val clean = if (command.endsWith("\r\n")) command else "$command\r\n"
        if (_uiState.value.usbStats.connectionState == com.example.agopengps.io.UsbConnectionState.CONNECTED) {
            val bytes = clean.toByteArray(Charsets.US_ASCII)
            usbManager.sendRawBytes(bytes, bytes.size)
        } else {
            udpManager.sendRawNmeaSentence(clean)
        }
        val cleanTrim = clean.trim().replace(",", ";")
        val ackSentence = "\$RESPONSE,ACK,SENT,$cleanTrim*00"
        nmeaDiagnosticsManager.recordSentence(ackSentence)
    }

    fun toggleMockHardwareLoopback() {
        val next = !_uiState.value.isMockLoopbackActive
        if (next) {
            mockLoopback.start(viewModelScope)
            _uiState.update {
                it.copy(
                    isMockLoopbackActive = true,
                    statusMessage = "Mock AgIO Hardware Loopback ENABLED (Teensy Emulated)"
                )
            }
        } else {
            mockLoopback.stop()
            _uiState.update {
                it.copy(
                    isMockLoopbackActive = false,
                    statusMessage = "Mock AgIO Hardware Loopback Stopped"
                )
            }
        }
    }

    fun sendUm982Command(command: String) {
        // Send via USB OTG if connected, else via UDP
        val sentUsb = usbManager.sendRawAsciiSentence(command)
        if (!sentUsb) {
            udpManager.sendRawNmeaSentence(command)
        }
        _uiState.update { it.copy(statusMessage = "Command sent: ${command.trim()}") }
    }

    fun openObstacleDialog(show: Boolean) {
        _uiState.update { it.copy(showObstacleDialog = show) }
    }

    fun openImplementPresetsDialog(show: Boolean) {
        _uiState.update { it.copy(showImplementPresetsDialog = show) }
    }

    fun openExportDialog(show: Boolean) {
        _uiState.update { it.copy(showExportDialog = show) }
    }

    fun cycleThemeMode() {
        val current = _uiState.value.themeMode
        val next = when (current) {
            DisplayThemeMode.STANDARD_AG -> DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST
            DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST -> DisplayThemeMode.CAB_OLED_NIGHT
            DisplayThemeMode.CAB_OLED_NIGHT -> DisplayThemeMode.STANDARD_AG
        }
        _uiState.update {
            it.copy(
                themeMode = next,
                isNightMode = (next == DisplayThemeMode.CAB_OLED_NIGHT),
                statusMessage = "Theme: ${next.label}"
            )
        }
    }

    fun setThemeMode(mode: DisplayThemeMode) {
        _uiState.update {
            it.copy(
                themeMode = mode,
                isNightMode = (mode == DisplayThemeMode.CAB_OLED_NIGHT),
                statusMessage = "Theme: ${mode.label}"
            )
        }
    }

    fun addObstacleAtVehicle(
        type: ObstacleType,
        name: String,
        radiusMeters: Double = 3.0,
        notes: String = ""
    ) {
        val current = _uiState.value
        val pivotPos = current.vehicleState.localPivotPosition
        val geoPos = current.vehicleState.geoPosition
        val fieldId = current.currentField?.id ?: 0L

        val newObs = FieldObstacle(
            id = System.currentTimeMillis(),
            fieldId = fieldId,
            name = name.ifBlank { type.label },
            type = type,
            localPos = pivotPos,
            geoPos = geoPos,
            radiusMeters = radiusMeters,
            notes = notes
        )

        _uiState.update {
            it.copy(
                obstacles = it.obstacles + newObs,
                statusMessage = "Flagged ${type.label}: ${newObs.name}"
            )
        }
        vibrateHaptic(100)
    }

    fun dropQuickObstacle(type: ObstacleType = ObstacleType.ROCK) {
        addObstacleAtVehicle(type, type.label, type.defaultRadiusMeters, "Quick Flag")
    }

    fun deleteObstacle(id: Long) {
        _uiState.update {
            it.copy(
                obstacles = it.obstacles.filter { obs -> obs.id != id },
                statusMessage = "Hazard deleted"
            )
        }
    }

    fun clearAllObstacles() {
        _uiState.update {
            it.copy(
                obstacles = emptyList(),
                activeObstacleAlert = null,
                statusMessage = "All field hazards cleared"
            )
        }
    }

    fun selectImplementPreset(preset: ImplementPreset) {
        val config = preset.toImplementConfig()
        _uiState.update {
            it.copy(
                implementConfig = config,
                statusMessage = "Switched Tool: ${preset.name}"
            )
        }
    }

    fun applyImplementPreset(preset: ImplementPreset) {
        selectImplementPreset(preset)
    }

    fun skipSwathPass(delta: Int) {
        val current = _uiState.value.vehicleState.activeSwathIndex
        val target = current + delta
        _uiState.update {
            it.copy(
                vehicleState = it.vehicleState.copy(activeSwathIndex = target),
                statusMessage = "Swath Skipped to Pass #$target (${if (delta > 0) "+$delta" else "$delta"})"
            )
        }
        vibrateHaptic(60)
    }

    fun openRtkBaseDialog(show: Boolean = true) {
        _uiState.update { it.copy(showRtkBaseDialog = show) }
    }

    fun updateRtkBaseConfig(newConfig: RtkBaseStationConfig) {
        _uiState.update {
            it.copy(
                rtkBaseStationConfig = newConfig,
                statusMessage = "RTK Base '${newConfig.name}' updated (${String.format("%.5f", newConfig.latitude)}, ${String.format("%.5f", newConfig.longitude)})"
            )
        }
        vibrateHaptic(60)
    }

    fun openFieldWizard(show: Boolean = true) {
        _uiState.update { it.copy(showFieldWizardDialog = show) }
    }

    fun launchFieldFromWizard(
        fieldName: String,
        farmName: String,
        cropType: String,
        selectedPreset: ImplementPreset?,
        customImplementConfig: ImplementConfig? = null,
        boundaryMode: WizardBoundaryMode,
        guidanceMode: WizardGuidanceMode,
        aPlusHeadingDeg: Double
    ) {
        if (customImplementConfig != null) {
            updateImplementConfig(customImplementConfig)
        } else if (selectedPreset != null) {
            selectImplementPreset(selectedPreset)
        }

        val pivot = _uiState.value.vehicleState.localPivotPosition

        val tractorHeading = if (guidanceMode == WizardGuidanceMode.A_PLUS_HEADING) aPlusHeadingDeg else _uiState.value.vehicleState.headingDeg
        val headingRad = Math.toRadians(tractorHeading)
        val fwd = Vec2(sin(headingRad), cos(headingRad))
        val right = Vec2(cos(headingRad), -sin(headingRad))

        if (boundaryMode == WizardBoundaryMode.SET_4_CORNERS) {
            _uiState.update {
                it.copy(
                    showOuterBoundaryCornersDialog = true,
                    capturedCorners = listOf(pivot),
                    showFieldWizardDialog = false,
                    statusMessage = "Corner 1 Captured at vehicle. Drive to Corner 2."
                )
            }
            return
        }

        // Boundary generation: starts from vehicle current position as baseline corner (edge), NOT centering tractor
        var newBoundary: List<Vec2> = emptyList()
        var isRecBoundary = false
        when (boundaryMode) {
            WizardBoundaryMode.FORM_FROM_AB_LINE -> {
                // 80-acre standard swath block spanning rightward from baseline
                newBoundary = listOf(
                    pivot,
                    pivot + (fwd * 804.672),
                    pivot + (fwd * 804.672) + (right * 402.336),
                    pivot + (right * 402.336)
                )
            }
            WizardBoundaryMode.DRIVE_PERIMETER -> {
                newBoundary = listOf(pivot)
                isRecBoundary = true
            }
            WizardBoundaryMode.QUARTER_SECTION_160 -> {
                // 804.67m x 804.67m (1/2 mile square) extending from vehicle entrance corner
                newBoundary = listOf(
                    pivot,
                    pivot + (fwd * 804.672),
                    pivot + (fwd * 804.672) + (right * 804.672),
                    pivot + (right * 804.672)
                )
            }
            WizardBoundaryMode.EIGHTY_ACRES -> {
                // 804.67m x 402.335m extending from vehicle entrance corner
                newBoundary = listOf(
                    pivot,
                    pivot + (fwd * 804.672),
                    pivot + (fwd * 804.672) + (right * 402.336),
                    pivot + (right * 402.336)
                )
            }
            WizardBoundaryMode.SET_4_CORNERS -> {
                newBoundary = emptyList()
            }
            WizardBoundaryMode.NO_BOUNDARY -> {
                newBoundary = emptyList()
            }
        }

        val headland = if (newBoundary.size >= 3) {
            GuidanceAlgorithms.generateHeadlandPolygon(newBoundary, _uiState.value.implementConfig.swathWidth)
        } else emptyList()

        // Swath Guidance setup
        when (guidanceMode) {
            WizardGuidanceMode.SET_AB_DRIVE -> {
                setPointA()
            }
            WizardGuidanceMode.A_PLUS_HEADING -> {
                createAPlusLine(aPlusHeadingDeg)
            }
            WizardGuidanceMode.COPY_EXISTING -> {
                createAPlusLine(90.0) // East-West road baseline
            }
            WizardGuidanceMode.ADAPTIVE_CURVE -> {
                startCurveRecording()
            }
        }

        val fieldEntity = FieldEntity(
            id = 0L,
            name = "$farmName - $fieldName ($cropType)",
            areaAcres = if (newBoundary.size >= 3) GeoUtils.calculatePolygonAreaAcres(newBoundary) else 0.0,
            workedAcres = 0.0,
            originLat = originGeo.latitude,
            originLon = originGeo.longitude,
            boundaryPointsJson = newBoundary.joinToString(";") { "${it.x},${it.y}" },
            headlandPointsJson = headland.joinToString(";") { "${it.x},${it.y}" },
            createdAt = System.currentTimeMillis(),
            lastModifiedAt = System.currentTimeMillis()
        )

        viewModelScope.launch(Dispatchers.IO) {
            val fieldId = repository.saveField(fieldEntity)
            val savedEntity = fieldEntity.copy(id = fieldId)
            withContext(Dispatchers.Main) {
                coverageSpatialGrid.clear()
                _uiState.update {
                    it.copy(
                        currentField = savedEntity,
                        fieldBoundary = newBoundary,
                        headlandBoundary = headland,
                        appliedSegments = emptyList(),
                        workedAcres = 0.0,
                        isFieldJobActive = true,
                        activeJobName = "$farmName: $fieldName",
                        isRecordingBoundary = isRecBoundary,
                        showFieldWizardDialog = false,
                        statusMessage = "Active Run: '$fieldName' Launched. " + if (isRecBoundary) "Drive perimeter..." else "Ready for guidance."
                    )
                }
            }
        }
        vibrateHaptic(100)
    }

    fun saveAndFinishFieldJob() {
        val state = _uiState.value
        val currentBoundary = state.fieldBoundary
        val headland = state.headlandBoundary
        val abLine = state.currentABLine
        val workedAcres = state.workedAcres

        val field = state.currentField ?: FieldEntity(
            id = 0L,
            name = state.activeJobName.ifBlank { "Field ${System.currentTimeMillis() % 10000}" },
            originLat = originGeo.latitude,
            originLon = originGeo.longitude
        )

        viewModelScope.launch(Dispatchers.IO) {
            val updated = field.copy(
                areaAcres = if (currentBoundary.size >= 3) GeoUtils.calculatePolygonAreaAcres(currentBoundary) else field.areaAcres,
                workedAcres = workedAcres,
                boundaryPointsJson = currentBoundary.joinToString(";") { "${it.x},${it.y}" },
                headlandPointsJson = headland.joinToString(";") { "${it.x},${it.y}" },
                activeAbLineName = abLine?.name ?: "",
                activeAbLineHeading = abLine?.headingDeg ?: 0.0,
                activeAbLineAX = abLine?.aLocal?.x ?: 0.0,
                activeAbLineAY = abLine?.aLocal?.y ?: 0.0,
                activeAbLineBX = abLine?.bLocal?.x ?: 0.0,
                activeAbLineBY = abLine?.bLocal?.y ?: 0.0,
                hasActiveAbLine = abLine != null,
                lastModifiedAt = System.currentTimeMillis()
            )
            val fieldId = if (updated.id == 0L) repository.saveField(updated) else {
                repository.updateField(updated)
                updated.id
            }

            // Save all worked passes into Room database!
            val passEntities = state.appliedSegments.map { seg ->
                CoveredPassEntity(
                    fieldId = fieldId,
                    leftStartX = seg.leftStart.x,
                    leftStartY = seg.leftStart.y,
                    rightStartX = seg.rightStart.x,
                    rightStartY = seg.rightStart.y,
                    leftEndX = seg.leftEnd.x,
                    leftEndY = seg.leftEnd.y,
                    rightEndX = seg.rightEnd.x,
                    rightEndY = seg.rightEnd.y,
                    centerX = seg.center.x,
                    centerY = seg.center.y,
                    radius = seg.radius
                )
            }
            repository.savePassesForField(fieldId, passEntities)

            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        currentField = updated.copy(id = fieldId),
                        isFieldJobActive = false,
                        isRecordingBoundary = false,
                        statusMessage = "Field '${updated.name}' & ${passEntities.size} passes Saved (${String.format("%.1f", workedAcres)} ac worked)"
                    )
                }
            }
        }
        vibrateHaptic(120)
    }

    fun openFormFieldFromAbDialog(open: Boolean = true) {
        if (open && _uiState.value.currentABLine == null) {
            _uiState.update { it.copy(statusMessage = "Set an AB Line first to form boundary") }
            return
        }
        _uiState.update { it.copy(showFormFieldFromAbDialog = open) }
    }

    fun dismissFormFieldFromAbDialog() {
        _uiState.update { it.copy(showFormFieldFromAbDialog = false) }
    }

    fun formFieldFromCurrentABLine(
        fieldName: String,
        farmName: String = "Farm",
        cropType: String = "Corn",
        sideChoice: com.example.agopengps.ui.FieldSideChoice = com.example.agopengps.ui.FieldSideChoice.RIGHT,
        extentAcres: Double = 80.0,
        headlandPasses: Int = 2,
        useAbLineLength: Boolean = true
    ) {
        val state = _uiState.value
        val ab = state.currentABLine ?: return
        val aLocal = ab.aLocal
        val bLocal = ab.bLocal
        val diff = bLocal - aLocal
        val abDist = sqrt(diff.x * diff.x + diff.y * diff.y)
        val abDir = if (abDist > 0.01) diff / abDist else Vec2(0.0, 1.0)
        val rightNormal = Vec2(abDir.y, -abDir.x)

        val fieldLengthM = if (useAbLineLength && abDist >= 60.0) abDist else 804.672
        val targetAreaSqM = extentAcres * 4046.85642
        val fieldWidthM = (targetAreaSqM / fieldLengthM).coerceIn(20.0, 5000.0)

        val corners = when (sideChoice) {
            com.example.agopengps.ui.FieldSideChoice.RIGHT -> listOf(
                aLocal,
                aLocal + (abDir * fieldLengthM),
                aLocal + (abDir * fieldLengthM) + (rightNormal * fieldWidthM),
                aLocal + (rightNormal * fieldWidthM)
            )
            com.example.agopengps.ui.FieldSideChoice.LEFT -> listOf(
                aLocal,
                aLocal - (rightNormal * fieldWidthM),
                aLocal + (abDir * fieldLengthM) - (rightNormal * fieldWidthM),
                aLocal + (abDir * fieldLengthM)
            )
            com.example.agopengps.ui.FieldSideChoice.BOTH_SIDES -> {
                val halfW = fieldWidthM / 2.0
                listOf(
                    aLocal - (rightNormal * halfW),
                    aLocal + (abDir * fieldLengthM) - (rightNormal * halfW),
                    aLocal + (abDir * fieldLengthM) + (rightNormal * halfW),
                    aLocal + (rightNormal * halfW)
                )
            }
        }

        val actualAreaAcres = GeoUtils.calculatePolygonAreaAcres(corners)
        val swathWidth = state.implementConfig.swathWidth
        val headland = if (headlandPasses > 0) {
            GuidanceAlgorithms.generateHeadlandPolygon(corners, swathWidth * headlandPasses)
        } else emptyList()

        val fieldEntity = FieldEntity(
            id = 0L,
            name = "$farmName - $fieldName ($cropType)",
            areaAcres = actualAreaAcres,
            workedAcres = 0.0,
            originLat = originGeo.latitude,
            originLon = originGeo.longitude,
            boundaryPointsJson = corners.joinToString(";") { "${it.x},${it.y}" },
            headlandPointsJson = headland.joinToString(";") { "${it.x},${it.y}" },
            activeAbLineName = ab.name,
            activeAbLineHeading = ab.headingDeg,
            activeAbLineAX = ab.aLocal.x,
            activeAbLineAY = ab.aLocal.y,
            activeAbLineBX = ab.bLocal.x,
            activeAbLineBY = ab.bLocal.y,
            hasActiveAbLine = true,
            createdAt = System.currentTimeMillis(),
            lastModifiedAt = System.currentTimeMillis()
        )

        viewModelScope.launch(Dispatchers.IO) {
            val fieldId = repository.saveField(fieldEntity)
            val saved = fieldEntity.copy(id = fieldId)
            withContext(Dispatchers.Main) {
                coverageSpatialGrid.clear()
                _uiState.update {
                    it.copy(
                        currentField = saved,
                        fieldBoundary = corners,
                        headlandBoundary = headland,
                        appliedSegments = emptyList(),
                        workedAcres = 0.0,
                        isFieldJobActive = true,
                        activeJobName = saved.name,
                        showFormFieldFromAbDialog = false,
                        showFieldWizardDialog = false,
                        statusMessage = "Field '${saved.name}' Created from AB Line (${String.format("%.1f", actualAreaAcres)} Ac). Ready for passes!"
                    )
                }
            }
        }
        vibrateHaptic(100)
    }

    fun openOuterBoundaryDialog(open: Boolean = true) {
        _uiState.update {
            it.copy(
                showOuterBoundaryCornersDialog = open,
                capturedCorners = if (open && it.capturedCorners.isEmpty()) listOf(it.vehicleState.localPivotPosition) else it.capturedCorners
            )
        }
    }

    fun dismissOuterBoundaryDialog() {
        _uiState.update { it.copy(showOuterBoundaryCornersDialog = false) }
    }

    fun captureCurrentCorner() {
        val pivot = _uiState.value.vehicleState.localPivotPosition
        val currentList = _uiState.value.capturedCorners
        val updated = currentList + pivot
        _uiState.update {
            it.copy(
                capturedCorners = updated,
                statusMessage = "Captured Corner #${updated.size} at (${String.format("%.1f", pivot.x)}, ${String.format("%.1f", pivot.y)})"
            )
        }
        vibrateHaptic(80)
    }

    fun clearCapturedCorners() {
        _uiState.update {
            it.copy(
                capturedCorners = emptyList(),
                statusMessage = "Cleared captured corners"
            )
        }
    }

    fun finishOuterCorners(fieldName: String, farmName: String) {
        val corners = _uiState.value.capturedCorners
        if (corners.size < 3) {
            _uiState.update { it.copy(statusMessage = "Need at least 3 corners to close boundary") }
            return
        }
        val area = GeoUtils.calculatePolygonAreaAcres(corners)
        val headland = GuidanceAlgorithms.generateHeadlandPolygon(corners, _uiState.value.implementConfig.swathWidth * 2)

        val fieldEntity = FieldEntity(
            id = 0L,
            name = "$farmName - $fieldName",
            areaAcres = area,
            workedAcres = 0.0,
            originLat = originGeo.latitude,
            originLon = originGeo.longitude,
            boundaryPointsJson = corners.joinToString(";") { "${it.x},${it.y}" },
            headlandPointsJson = headland.joinToString(";") { "${it.x},${it.y}" },
            activeAbLineName = _uiState.value.currentABLine?.name ?: "",
            hasActiveAbLine = _uiState.value.currentABLine != null,
            activeAbLineHeading = _uiState.value.currentABLine?.headingDeg ?: 0.0,
            activeAbLineAX = _uiState.value.currentABLine?.aLocal?.x ?: 0.0,
            activeAbLineAY = _uiState.value.currentABLine?.aLocal?.y ?: 0.0,
            activeAbLineBX = _uiState.value.currentABLine?.bLocal?.x ?: 0.0,
            activeAbLineBY = _uiState.value.currentABLine?.bLocal?.y ?: 0.0,
            createdAt = System.currentTimeMillis(),
            lastModifiedAt = System.currentTimeMillis()
        )

        viewModelScope.launch(Dispatchers.IO) {
            val fieldId = repository.saveField(fieldEntity)
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        currentField = fieldEntity.copy(id = fieldId),
                        fieldBoundary = corners,
                        headlandBoundary = headland,
                        isFieldJobActive = true,
                        activeJobName = fieldEntity.name,
                        showOuterBoundaryCornersDialog = false,
                        capturedCorners = emptyList(),
                        statusMessage = "Outer Boundary Set (${String.format("%.1f", area)} Ac, ${corners.size} corners). Saved."
                    )
                }
            }
        }
        vibrateHaptic(100)
    }

    fun skipPass(delta: Int) {
        skipSwathPass(delta)
    }

    private fun vibrateHaptic(durationMs: Long) {
        try {
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vm = getApplication<Application>().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getApplication<Application>().getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.let {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(durationMs)
                }
            }
        } catch (e: Exception) {
            // Non-critical
        }
    }

    fun openSlidingHitchDialog(open: Boolean = true) {
        _uiState.update { it.copy(showSlidingHitchDialog = open) }
    }

    fun updateSlidingHitchConfig(config: SlidingHitchConfig) {
        _uiState.update { it.copy(slidingHitchConfig = config) }
        secondGpsManager.startListening(port = config.udpPort, scope = viewModelScope)
    }

    fun toggleAutoHitch(enabled: Boolean) {
        _uiState.update { current ->
            current.copy(slidingHitchConfig = current.slidingHitchConfig.copy(isAutoHitchEngaged = enabled))
        }
    }

    fun centerSlidingHitch() {
        slidingHitchController.resetIntegral()
        _uiState.update { current ->
            current.copy(
                slidingHitchState = current.slidingHitchState.copy(
                    currentHitchShiftCm = 0.0,
                    targetHitchShiftCm = 0.0,
                    hydraulicPwmOutput = 0
                )
            )
        }
    }

    fun nudgeSlidingHitch(deltaCm: Double) {
        _uiState.update { current ->
            val maxStroke = current.slidingHitchConfig.maxStrokeCm
            val newShift = (current.slidingHitchState.currentHitchShiftCm + deltaCm).coerceIn(-maxStroke, maxStroke)
            current.copy(
                slidingHitchState = current.slidingHitchState.copy(
                    currentHitchShiftCm = newShift,
                    targetHitchShiftCm = newShift
                )
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        simJob?.cancel()
        usbManager.unregisterReceiver()
        usbManager.disconnect()
        udpManager.stop()
        secondGpsManager.stopListening()
        ntripClient.stop()
        soundManager.release()
        stopAndroidLocationUpdates()
    }
}

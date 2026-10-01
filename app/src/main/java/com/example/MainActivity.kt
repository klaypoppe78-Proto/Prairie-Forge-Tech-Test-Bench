package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agopengps.GnssSourceMode
import com.example.agopengps.MainViewModel
import com.example.agopengps.ui.*
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep tractor cab screen awake while operating auto-steer
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val udpStats by viewModel.udpManager.stats.collectAsStateWithLifecycle()
            val usbStats by viewModel.usbManager.usbStats.collectAsStateWithLifecycle()
            val ntripStatus by viewModel.ntripClient.status.collectAsStateWithLifecycle()

            // Location permission launcher for Android GPS fallback
            val locationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                if (granted) {
                    viewModel.setGnssSource(GnssSourceMode.ANDROID_GPS)
                }
            }

            LaunchedEffect(Unit) {
                val fineLocation = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION)
                val coarseLocation = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION)
                if (fineLocation == PackageManager.PERMISSION_GRANTED || coarseLocation == PackageManager.PERMISSION_GRANTED) {
                    viewModel.setGnssSource(GnssSourceMode.ANDROID_GPS)
                } else {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            }

            MyApplicationTheme(darkTheme = uiState.isNightMode) {
                Scaffold(
                    modifier = Modifier.fillMaxSize().testTag("main_scaffold"),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // 1. Interactive Guidance Canvas (with 3D Cab, 2D Top, North-Up views & Infield Coverage)
                        GuidanceMapCanvas(
                            vehicleState = uiState.vehicleState,
                            vehicleConfig = uiState.vehicleConfig,
                            implementConfig = uiState.implementConfig,
                            currentABLine = uiState.currentABLine,
                            currentCurveLine = uiState.currentCurveLine,
                            guidanceMode = uiState.guidanceMode,
                            cameraViewMode = uiState.cameraViewMode,
                            fieldBoundary = uiState.fieldBoundary,
                            headlandBoundary = uiState.headlandBoundary,
                            uTurnPath = uiState.uTurnPath,
                            recordingCurvePoints = uiState.recordingCurvePoints,
                            isRecordingBoundary = uiState.isRecordingBoundary,
                            appliedSegments = uiState.appliedSegments,
                            isNightMode = uiState.isNightMode,
                            themeMode = uiState.themeMode,
                            obstacles = uiState.obstacles,
                            headlandTurnInfo = uiState.headlandTurnInfo,
                            activeObstacleAlert = uiState.activeObstacleAlert,
                            showSatelliteOverlay = uiState.showSatelliteOverlay,
                            satelliteMapProvider = uiState.satelliteMapProvider,
                            rtkBaseConfig = uiState.rtkBaseStationConfig,
                            slidingHitchConfig = uiState.slidingHitchConfig,
                            slidingHitchState = uiState.slidingHitchState,
                            zoomFactor = uiState.mapZoom,
                            onZoomChange = { zoomChange -> viewModel.onPinchZoom(zoomChange) },
                            terrainSquare = uiState.currentTerrainSquare,
                            terrainAttitude = uiState.currentTerrainAttitude,
                            isTerrainCompensationEnabled = uiState.isTerrainCompensationEnabled,
                            showTopographyMap = uiState.showTopographyMap,
                            show3DTerrainMesh = uiState.show3DTerrainMesh,
                            navigationOutput = uiState.lastNavigationOutput,
                            onToggleCameraMode = { viewModel.cycleCameraViewMode() },
                            onCycleBasemap = { viewModel.cycleBasemapMode() },
                            onOpenRtkBaseDialog = { viewModel.openRtkBaseDialog(true) },
                            onDropHazardPin = { viewModel.openObstacleDialog(true) },
                            onOpenObstacleDialog = { viewModel.openObstacleDialog(true) },
                            onToggleTerrainCompensation = { viewModel.toggleTerrainCompensation() },
                            onToggleTopographyMap = { viewModel.toggleTopographyMap() },
                            onToggle3DTerrainMesh = { viewModel.toggle3DTerrainMesh() },
                            onResetToBasicGps = { viewModel.resetToBasicGpsOnly() },
                            modifier = Modifier.fillMaxSize()
                        )

                        // 2. Top Precision LED Lightbar HUD & Telemetry Bar
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                        ) {
                            LightbarHud(
                                vehicleState = uiState.vehicleState,
                                workedAcres = uiState.workedAcres,
                                isNightMode = uiState.isNightMode,
                                gnssSource = uiState.gnssSource,
                                slidingHitchState = uiState.slidingHitchState,
                                slidingHitchConfig = uiState.slidingHitchConfig,
                                uTurnPrompt = uiState.uTurnPrompt,
                                onToggleSimulation = { viewModel.toggleSimulationMode() },
                                onOpenSettings = { viewModel.openSettingsDialog(true) },
                                onOpenSlidingHitch = { viewModel.openSlidingHitchDialog(true) },
                                onToggleDashboard = { viewModel.toggleOperatorDashboard() },
                                onOpenNmeaDiagnostics = { viewModel.openNmeaDiagnosticsDialog(true) },
                                onRecenterGps = { viewModel.recenterOriginToCurrentGps() }
                            )

                            // Modern Floating Glass Status Banner Alert
                            AnimatedVisibility(
                                visible = uiState.statusMessage.isNotBlank(),
                                enter = fadeIn() + slideInVertically(),
                                exit = fadeOut() + slideOutVertically()
                            ) {
                                Surface(
                                    color = Color(0xEB0F172A),
                                    shape = RoundedCornerShape(16.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3300F59B)),
                                    shadowElevation = 8.dp,
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .padding(top = 4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(androidx.compose.foundation.shape.CircleShape)
                                                .background(Color(0xFF00F59B))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = uiState.statusMessage,
                                            color = Color(0xFFF1F5F9),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        // Dedicated High-Contrast Operator Dashboard Overlay (Steer Angle, Cross-Track Error, Heading Error)
                        AnimatedVisibility(
                            visible = uiState.showOperatorDashboard,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .padding(top = 96.dp, start = 8.dp, end = 74.dp)
                        ) {
                            OperatorDashboardOverlay(
                                vehicleState = uiState.vehicleState,
                                vehicleConfig = uiState.vehicleConfig,
                                currentABLine = uiState.currentABLine,
                                activeSwathIndex = uiState.vehicleState.activeSwathIndex,
                                gnssSource = uiState.gnssSource,
                                isNightMode = uiState.isNightMode,
                                cameraViewMode = uiState.cameraViewMode,
                                slidingHitchConfig = uiState.slidingHitchConfig,
                                slidingHitchState = uiState.slidingHitchState,
                                onToggleAutoSteer = { viewModel.toggleAutoSteer() },
                                onSnapABLine = { viewModel.snapABLineToVehicle() },
                                onNudgeSwath = { cm -> viewModel.nudgeSwath(cm) },
                                onNudgeHalfRow22 = { isRight -> viewModel.nudgeHalfRow22Inch(isRight) },
                                onNudgeFullRow22 = { isRight -> viewModel.nudgeFullRow22Inch(isRight) },
                                onZeroWas = { viewModel.zeroWasAtCurrentAngle() },
                                onCycleSwath = { dir -> viewModel.cycleSwath(dir) },
                                onToggleCameraMode = { viewModel.cycleCameraViewMode() },
                                onOpenSettings = { viewModel.openSettingsDialog(true) },
                                onOpenNmeaDiagnostics = { viewModel.openNmeaDiagnosticsDialog(true) },
                                onOpenUm982Dialog = { viewModel.openUm982Dialog(true) },
                                onOpenSlidingHitchDialog = { viewModel.openSlidingHitchDialog(true) },
                                onOpenWasCalibrationDialog = { viewModel.openWasCalibrationDialog(true) },
                                onOpenAgIoDialog = { viewModel.openAgIoDialog(true) },
                                onOpenFieldWizard = { viewModel.openFieldWizard(true) },
                                onOpenVirtualDrive = { viewModel.openVirtualDrive(true) }
                            )
                        }

                        // 3. Iconic AgOpenGPS In-Cab Right Dock and Bottom Section Bar
                        CabControls(
                            isAutoSteerEngaged = uiState.vehicleState.isAutoSteerEngaged,
                            isSectionMasterActive = uiState.implementConfig.isMasterActive,
                            isAutoSectionControl = uiState.implementConfig.isAutoSectionControl,
                            sectionStates = uiState.vehicleState.sectionStates,
                            sectionOverrides = uiState.sectionOverrides,
                            isSettingAB = uiState.isSettingAB,
                            guidanceMode = uiState.guidanceMode,
                            cameraViewMode = uiState.cameraViewMode,
                            gnssSource = uiState.gnssSource,
                            simSpeedKmh = uiState.vehicleState.speedKmh,
                            isNightMode = uiState.isNightMode,
                            themeMode = uiState.themeMode,
                            showSatelliteOverlay = uiState.showSatelliteOverlay,
                            satelliteMapProvider = uiState.satelliteMapProvider,
                            isAutoUTurnEnabled = uiState.isAutoUTurnEnabled,
                            autoUTurnActive = uiState.autoUTurnActive,
                            isFieldJobActive = uiState.isFieldJobActive,
                            activeJobName = uiState.activeJobName,
                            workedAcres = uiState.workedAcres,
                            isRecordingBoundary = uiState.isRecordingBoundary,
                            onToggleAutoSteer = { viewModel.toggleAutoSteer() },
                            onToggleAutoUTurn = { viewModel.toggleAutoUTurn() },
                            onTriggerManualUTurn = { viewModel.triggerManualUTurn() },
                            onToggleCameraMode = { viewModel.cycleCameraViewMode() },
                            onSetPointA = { viewModel.setPointA() },
                            onSetPointB = { viewModel.setPointB() },
                            onSnapABLine = { viewModel.snapABLineToVehicle() },
                            onReverseDirection = { viewModel.reverseABLineDirection() },
                            onCycleSwath = { dir -> viewModel.cycleSwath(dir) },
                            onSkipPass = { count -> viewModel.skipPass(count) },
                            onNudgeSwath = { cm -> viewModel.nudgeSwath(cm) },
                            onNudgeHalfRow22 = { isRight -> viewModel.nudgeHalfRow22Inch(isRight) },
                            onNudgeFullRow22 = { isRight -> viewModel.nudgeFullRow22Inch(isRight) },
                            onZeroWas = { viewModel.zeroWasAtCurrentAngle() },
                            onToggleSectionOverride = { idx -> viewModel.toggleSectionOverride(idx) },
                            onToggleMasterSection = { viewModel.toggleMasterSection() },
                            onToggleAutoSection = { viewModel.toggleAutoSectionControl() },
                            onToggleNightMode = { viewModel.toggleNightMode() },
                            onCycleThemeMode = { viewModel.cycleThemeMode() },
                            onOpenObstacleDialog = { viewModel.openObstacleDialog(true) },
                            onOpenImplementPresetsDialog = { viewModel.openImplementPresetsDialog(true) },
                            onOpenExportDialog = { viewModel.openExportDialog(true) },
                            onOpenFieldWizard = { viewModel.openFieldWizard(true) },
                            onOpenFormFieldFromAb = { viewModel.openFormFieldFromAbDialog(true) },
                            onOpenOuterBoundaryCorners = { viewModel.openOuterBoundaryDialog(true) },
                            onOpenRtkBaseDialog = { viewModel.openRtkBaseDialog(true) },
                            onSaveAndFinishField = { viewModel.saveAndFinishFieldJob() },
                            onToggleBoundaryRecording = {
                                if (uiState.isRecordingBoundary) viewModel.finishBoundaryRecording() else viewModel.startBoundaryRecording()
                            },
                            onToggleSatelliteOverlay = { viewModel.toggleSatelliteOverlay() },
                            onCycleSatelliteProvider = { viewModel.cycleSatelliteProvider() },
                            onZoomIn = { viewModel.zoomIn() },
                            onZoomOut = { viewModel.zoomOut() },
                            onOpenSettings = { viewModel.openSettingsDialog(true) },
                            onOpenFieldDialog = { viewModel.openFieldDialog(true) },
                            onOpenAgIoDialog = { viewModel.openAgIoDialog(true) },
                            onOpenVirtualDrive = { viewModel.openVirtualDrive(true) },
                            onOpenWasDialog = { viewModel.openWasCalibrationDialog(true) },
                            onOpenUm982Dialog = { viewModel.openUm982Dialog(true) },
                            onToggleDashboard = { viewModel.toggleOperatorDashboard() },
                            onOpenNmeaDiagnostics = { viewModel.openNmeaDiagnosticsDialog(true) },
                            slidingHitchConfig = uiState.slidingHitchConfig,
                            slidingHitchState = uiState.slidingHitchState,
                            onOpenSlidingHitch = { viewModel.openSlidingHitchDialog(true) },
                            onToggleSimulationMode = { viewModel.toggleSimulationMode() },
                            onSetSimSpeed = { speed -> viewModel.setSimSpeed(speed) },
                            onToggleSimReverse = { viewModel.toggleSimReverse() },
                            onRecenterGps = { viewModel.recenterOriginToCurrentGps() },
                            modifier = Modifier.fillMaxSize()
                        )

                        // 4. Configuration & Tuning Dialog
                        if (uiState.showSettingsDialog) {
                            SettingsDialog(
                                guidanceSettings = uiState.guidanceSettings,
                                vehicleConfig = uiState.vehicleConfig,
                                implementConfig = uiState.implementConfig,
                                steerValveConfig = uiState.steerValveConfig,
                                gnssReceiverType = uiState.gnssReceiverType,
                                gnssSource = uiState.gnssSource,
                                usbStats = usbStats,
                                currentRoll = uiState.vehicleState.rollDeg,
                                onUpdateGuidanceSettings = { s -> viewModel.updateGuidanceSettings(s) },
                                onUpdateVehicleConfig = { v -> viewModel.updateVehicleConfig(v) },
                                onUpdateImplementConfig = { i -> viewModel.updateImplementConfig(i) },
                                onUpdateSteerValveConfig = { v -> viewModel.updateSteerValveConfig(v) },
                                onSetGnssReceiverType = { t -> viewModel.setGnssReceiverType(t) },
                                onSelectGnssSource = { source ->
                                    if (source == GnssSourceMode.ANDROID_GPS) {
                                        val hasFine = ContextCompat.checkSelfPermission(
                                            this@MainActivity,
                                            Manifest.permission.ACCESS_FINE_LOCATION
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (hasFine) {
                                            viewModel.setGnssSource(GnssSourceMode.ANDROID_GPS)
                                        } else {
                                            locationPermissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                                )
                                            )
                                        }
                                    } else {
                                        viewModel.setGnssSource(source)
                                    }
                                },
                                onConnectUsb = { viewModel.connectUsb() },
                                onDisconnectUsb = { viewModel.disconnectUsb() },
                                isMockLoopbackActive = uiState.isMockLoopbackActive,
                                onToggleMockLoopback = { viewModel.toggleMockHardwareLoopback() },
                                onOpenUm982Dialog = { viewModel.openUm982Dialog(true) },
                                isTerrainCompensationEnabled = uiState.isTerrainCompensationEnabled,
                                showTopographyMap = uiState.showTopographyMap,
                                show3DTerrainMesh = uiState.show3DTerrainMesh,
                                onToggleTerrainCompensation = { viewModel.toggleTerrainCompensation(it) },
                                onToggleTopographyMap = { viewModel.toggleTopographyMap(it) },
                                onToggle3DTerrainMesh = { viewModel.toggle3DTerrainMesh(it) },
                                onResetToBasicGps = { viewModel.resetToBasicGpsOnly() },
                                onActivateAllTerrain = { viewModel.activateAllTerrainFeatures() },
                                onLoadNewHolland8870Preset = { viewModel.loadNewHolland8870Preset() },
                                onResetCoverage = { viewModel.resetWorkedArea() },
                                currentGeoPosition = uiState.vehicleState.geoPosition,
                                onSetManualLocation = { lat, lon, alt, name ->
                                    viewModel.setManualFarmLocation(lat, lon, alt, name)
                                },
                                onRecenterToTabletGps = { viewModel.recenterOriginToCurrentGps() },
                                onDismiss = { viewModel.openSettingsDialog(false) }
                            )
                        }

                        // 5. AgIO Communication Hub & Diagnostics Dialog
                        if (uiState.showAgIoDialog) {
                            AgIoHubDialog(
                                udpStats = udpStats,
                                ntripStatus = ntripStatus,
                                ntripConfig = viewModel.ntripConfig,
                                onConnectNtrip = { cfg -> viewModel.connectNtrip(cfg) },
                                onDisconnectNtrip = { viewModel.disconnectNtrip() },
                                onSendSteerSettings = { kp, ki, kd, maxSteer, countsPerDeg ->
                                    viewModel.sendSteerSettings(kp, ki, kd, maxSteer, countsPerDeg)
                                },
                                onDismiss = { viewModel.openAgIoDialog(false) }
                            )
                        }

                        // 6. Field & Pattern Storage Management Dialog (Save, Open, Pattern Repeat)
                        if (uiState.showFieldDialog) {
                            FieldManagerDialog(
                                currentField = uiState.currentField,
                                savedFieldsList = uiState.savedFieldsList,
                                boundaryVertices = uiState.fieldBoundary,
                                isRecordingBoundary = uiState.isRecordingBoundary,
                                currentABLine = uiState.currentABLine,
                                currentCurveLine = uiState.currentCurveLine,
                                isRecordingCurve = uiState.isRecordingCurve,
                                currentTractorHeading = uiState.vehicleState.headingDeg,
                                implementWidthMeters = uiState.implementConfig.toolWidth,
                                appliedSegmentsCount = uiState.appliedSegments.size,
                                onSaveCurrentFieldAndPasses = { viewModel.saveCurrentFieldAndPasses() },
                                onOpenSavedField = { field -> viewModel.openSavedField(field) },
                                onDeleteSavedField = { id -> viewModel.deleteSavedField(id) },
                                onStartBoundaryRecording = { viewModel.startBoundaryRecording() },
                                onAddBoundaryPoint = { viewModel.addBoundaryPoint() },
                                onFinishBoundaryRecording = { viewModel.finishBoundaryRecording() },
                                onClearBoundary = { viewModel.clearBoundary() },
                                onResetCoverage = { viewModel.resetWorkedArea() },
                                onCreateNewField = { name -> viewModel.createNewField(name) },
                                onCreateAPlusLine = { heading -> viewModel.createAPlusLine(heading) },
                                onStartCurveRecording = { viewModel.startCurveRecording() },
                                onFinishCurveRecording = { viewModel.finishCurveRecording() },
                                onImportField = { content, name -> viewModel.importFieldFromText(content, name) },
                                onLoadHomeFarm = { viewModel.loadLacQuiParleHomeFarm() },
                                onOpenFormFieldFromAb = {
                                    viewModel.openFieldDialog(false)
                                    viewModel.openFormFieldFromAbDialog(true)
                                },
                                onOpenOuterBoundaryCorners = {
                                    viewModel.openFieldDialog(false)
                                    viewModel.openOuterBoundaryDialog(true)
                                },
                                onDismiss = { viewModel.openFieldDialog(false) }
                            )
                        }

                        // 7. Virtual In-Cab Manual Steering Wheel & Throttle Overlay
                        if (uiState.showVirtualDrive) {
                            VirtualCabDriveOverlay(
                                isAutoSteerEngaged = uiState.vehicleState.isAutoSteerEngaged,
                                simSpeedKmh = uiState.vehicleState.speedKmh,
                                actualSteerAngleDeg = uiState.vehicleState.actualSteerAngleDeg,
                                targetSteerAngleDeg = uiState.vehicleState.targetSteerAngleDeg,
                                isReverse = viewModel.isSimReverse(),
                                maxSteerDeg = uiState.vehicleConfig.maxSteerAngleDeg,
                                onManualSteerChange = { angle -> viewModel.setManualSteerAngle(angle) },
                                onManualSteerRelease = { viewModel.releaseManualSteer() },
                                onSetSimSpeed = { speed -> viewModel.setSimSpeed(speed) },
                                onToggleReverse = { viewModel.toggleSimReverse() },
                                onToggleAutoSteer = { viewModel.toggleAutoSteer() },
                                onDismiss = { viewModel.openVirtualDrive(false) }
                            )
                        }

                        // 8. WAS Zeroing & Live PID/PWM Oscilloscope Bench Calibration Dialog
                        if (uiState.showWasCalibrationDialog) {
                            WasCalibrationDialog(
                                hardwareStatus = udpStats.hardwareStatus,
                                targetSteerAngleDeg = uiState.vehicleState.targetSteerAngleDeg,
                                steerValveConfig = uiState.steerValveConfig,
                                onUpdateSteerValveConfig = { cfg -> viewModel.updateSteerValveConfig(cfg) },
                                onDismiss = { viewModel.openWasCalibrationDialog(false) }
                            )
                        }

                        // 9. Unicore UM982 Dual-Antenna RTK Diagnostics & Config Wizard
                        if (uiState.showUm982Dialog) {
                            Um982DiagnosticsDialog(
                                vehicleState = uiState.vehicleState,
                                lastSentence = usbStats.lastSentence.ifEmpty { udpStats.lastSentence },
                                onSendCommand = { cmd -> viewModel.sendUm982Command(cmd) },
                                onDismiss = { viewModel.openUm982Dialog(false) }
                            )
                        }

                        // 9.5. Dedicated NMEA Diagnostics & Field Troubleshooting Dialog
                        if (uiState.showNmeaDiagnosticsDialog) {
                            NmeaDiagnosticsDialog(
                                diagnosticsManager = viewModel.nmeaDiagnosticsManager,
                                vehicleState = uiState.vehicleState,
                                onSendCommand = { cmd -> viewModel.sendNmeaCommand(cmd) },
                                onDismiss = { viewModel.openNmeaDiagnosticsDialog(false) }
                            )
                        }

                        // 10. Field Obstacles & Hazard Markers Dialog
                        if (uiState.showObstacleDialog) {
                            ObstacleDialog(
                                obstacles = uiState.obstacles,
                                currentLat = uiState.vehicleState.geoPosition.latitude,
                                currentLon = uiState.vehicleState.geoPosition.longitude,
                                onAddObstacleAtVehicle = { type, name, radius, notes ->
                                    viewModel.addObstacleAtVehicle(type, name, radius, notes)
                                },
                                onDeleteObstacle = { id -> viewModel.deleteObstacle(id) },
                                onClearAllObstacles = { viewModel.clearAllObstacles() },
                                onDismiss = { viewModel.openObstacleDialog(false) }
                            )
                        }

                        // 11. Implement & Machine Presets Dialog (1-Tap Setup)
                        if (uiState.showImplementPresetsDialog) {
                            ImplementPresetsDialog(
                                currentConfig = uiState.implementConfig,
                                onSelectPreset = { preset -> viewModel.applyImplementPreset(preset) },
                                onCustomConfigSave = { cfg -> viewModel.updateImplementConfig(cfg) },
                                onDismiss = { viewModel.openImplementPresetsDialog(false) }
                            )
                        }

                        // 12. Export Field Data Dialog (KML, CSV, GeoJSON)
                        if (uiState.showExportDialog) {
                            ExportFieldDialog(
                                fieldName = uiState.currentField?.name ?: "Montevideo_Farm_Field",
                                originGeo = viewModel.originGeo,
                                fieldBoundary = uiState.fieldBoundary,
                                headlandBoundary = uiState.headlandBoundary,
                                abLine = uiState.currentABLine,
                                obstacles = uiState.obstacles,
                                appliedSegments = uiState.appliedSegments,
                                workedAcres = uiState.workedAcres,
                                onDismiss = { viewModel.openExportDialog(false) }
                            )
                        }

                        // 13. RTK Base Station Survey & Reference Location Dialog
                        if (uiState.showRtkBaseDialog) {
                            RtkBaseStationDialog(
                                config = uiState.rtkBaseStationConfig,
                                vehicleState = uiState.vehicleState,
                                originGeo = viewModel.originGeo,
                                onSaveConfig = { cfg -> viewModel.updateRtkBaseConfig(cfg) },
                                onDismiss = { viewModel.openRtkBaseDialog(false) }
                            )
                        }

                        // 14. Trimble / John Deere Style Field Setup & Guidance Wizard
                        if (uiState.showFieldWizardDialog) {
                            FieldSetupWizardDialog(
                                currentTractorHeading = uiState.vehicleState.headingDeg,
                                currentTractorPos = uiState.vehicleState.localPivotPosition,
                                implementPresets = com.example.agopengps.navigation.ImplementPreset.getAllPresets(),
                                currentImplementConfig = uiState.implementConfig,
                                onLaunchFieldJob = { fieldName, farmName, cropType, selectedPreset, customConfig, boundaryMode, guidanceMode, aPlusHeadingDeg ->
                                    viewModel.launchFieldFromWizard(
                                        fieldName = fieldName,
                                        farmName = farmName,
                                        cropType = cropType,
                                        selectedPreset = selectedPreset,
                                        customImplementConfig = customConfig,
                                        boundaryMode = boundaryMode,
                                        guidanceMode = guidanceMode,
                                        aPlusHeadingDeg = aPlusHeadingDeg
                                    )
                                },
                                onDismiss = { viewModel.openFieldWizard(false) }
                            )
                        }

                        // 15. Form Outer Boundary from AB Line Dialog
                        if (uiState.showFormFieldFromAbDialog) {
                            com.example.agopengps.ui.FormFieldFromAbDialog(
                                abLine = uiState.currentABLine,
                                currentSwathWidthMeters = uiState.implementConfig.toolWidth,
                                onConfirmFormField = { name, farm, crop, side, acres, headlands, useAbLen ->
                                    viewModel.formFieldFromCurrentABLine(
                                        fieldName = name,
                                        farmName = farm,
                                        cropType = crop,
                                        sideChoice = side,
                                        extentAcres = acres,
                                        headlandPasses = headlands,
                                        useAbLineLength = useAbLen
                                    )
                                },
                                onDismiss = { viewModel.dismissFormFieldFromAbDialog() }
                            )
                        }

                        // 16. Set Outer Boundary (4 Corners) Dialog
                        if (uiState.showOuterBoundaryCornersDialog) {
                            com.example.agopengps.ui.OuterBoundaryCornersDialog(
                                capturedCorners = uiState.capturedCorners,
                                currentTractorPos = uiState.vehicleState.localPivotPosition,
                                swathWidthMeters = uiState.implementConfig.toolWidth,
                                onCaptureCorner = { viewModel.captureCurrentCorner() },
                                onClearCorners = { viewModel.clearCapturedCorners() },
                                onConfirmCorners = { name, farm -> viewModel.finishOuterCorners(name, farm) },
                                onDismiss = { viewModel.dismissOuterBoundaryDialog() }
                            )
                        }

                        // 17. Second GPS Controller & Sliding Hitch Implement Steering Dialog
                        if (uiState.showSlidingHitchDialog) {
                            SlidingHitchDialog(
                                config = uiState.slidingHitchConfig,
                                state = uiState.slidingHitchState,
                                tractorXteCm = uiState.vehicleState.crossTrackErrorCm,
                                secondGpsStats = uiState.secondGpsStats,
                                onSaveConfig = { cfg -> viewModel.updateSlidingHitchConfig(cfg) },
                                onCenterHitch = { viewModel.centerSlidingHitch() },
                                onNudgeHitch = { deltaCm -> viewModel.nudgeSlidingHitch(deltaCm) },
                                onToggleAutoHitch = { enabled -> viewModel.toggleAutoHitch(enabled) },
                                onDismiss = { viewModel.openSlidingHitchDialog(false) }
                            )
                        }
                    }
                }
            }
        }
    }
}

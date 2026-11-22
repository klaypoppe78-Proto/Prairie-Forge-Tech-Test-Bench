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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agopengps.GnssSourceMode
import com.example.agopengps.LiveTelemetryState
import com.example.agopengps.MainViewModel
import com.example.agopengps.navigation.*
import com.example.agopengps.ui.*
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep tractor cab screen awake while operating auto-steer
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            // Discrete / low-frequency UI state (NEVER updates at 20 Hz!)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            // Lifecycle-safe pause/resume for 20 Hz navigation loop & background I/O
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> viewModel.onAppPause()
                        Lifecycle.Event.ON_RESUME -> viewModel.onAppResume()
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            // Location permission launcher for Android GPS fallback
            val locationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { _ ->
                // Location permissions granted for GPS / GNSS access when user enables Android GPS mode
            }

            LaunchedEffect(Unit) {
                val fineLocation = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION)
                val coarseLocation = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION)
                if (fineLocation != PackageManager.PERMISSION_GRANTED && coarseLocation != PackageManager.PERMISSION_GRANTED) {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            }

            MyApplicationTheme(darkTheme = uiState.isNightMode) {
                val isOledNight = uiState.themeMode == DisplayThemeMode.CAB_OLED_NIGHT
                val isDaylight = uiState.themeMode == DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST
                val view = LocalView.current
                SideEffect {
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = isDaylight || (!uiState.isNightMode && !isOledNight)
                    controller.isAppearanceLightNavigationBars = isDaylight || (!uiState.isNightMode && !isOledNight)
                }
                Scaffold(
                    modifier = Modifier.fillMaxSize().testTag("main_scaffold"),
                    containerColor = when {
                        isOledNight -> Color.Black
                        isDaylight -> Color(0xFFE9EFE9)
                        else -> Color(0xFF090E17)
                    },
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { _ ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                compositingStrategy = if (isOledNight) CompositingStrategy.Offscreen else CompositingStrategy.Auto
                            }
                            .drawWithContent {
                                drawContent()
                                if (isOledNight) {
                                    // Full Edge-to-Edge Warm Amber / OLED Night Dimming Scrim
                                    drawRect(Color(0x2EFF8C00))
                                }
                            }
                    ) {
                        // 1. Interactive Guidance Canvas (Direct draw-phase 20 Hz telemetry read; zero 20 Hz recomposition)
                        GuidanceMapCanvas(
                            vehicleConfig = uiState.vehicleConfig,
                            implementConfig = uiState.implementConfig,
                            currentABLine = uiState.currentABLine,
                            currentCurveLine = uiState.currentCurveLine,
                            guidanceMode = uiState.guidanceMode,
                            cameraViewMode = uiState.cameraViewMode,
                            fieldBoundary = uiState.fieldBoundary,
                            headlandBoundary = uiState.headlandBoundary,
                            recordingCurvePoints = uiState.recordingCurvePoints,
                            isRecordingBoundary = uiState.isRecordingBoundary,
                            activeRecallPath = uiState.activeRecallPath,
                            recordingPathPoints = uiState.currentRecordingPathPoints,
                            isRecordingPath = uiState.isRecordingPath,
                            coverageChunkStore = viewModel.coverageChunkStore,
                            threePointState = uiState.threePointConfig.state,
                            isNightMode = uiState.isNightMode,
                            themeMode = uiState.themeMode,
                            obstacles = uiState.obstacles,
                            showSatelliteOverlay = uiState.showSatelliteOverlay,
                            satelliteMapProvider = uiState.satelliteMapProvider,
                            rtkBaseConfig = uiState.rtkBaseStationConfig,
                            slidingHitchConfig = uiState.slidingHitchConfig,
                            zoomFactor = uiState.mapZoom,
                            onZoomChange = { zoomChange -> viewModel.onPinchZoom(zoomChange) },
                            isTerrainCompensationEnabled = uiState.isTerrainCompensationEnabled,
                            showTopographyMap = uiState.showTopographyMap,
                            show3DTerrainMesh = uiState.show3DTerrainMesh,
                            tempPointA = uiState.tempPointA,
                            isSettingAB = uiState.isSettingAB,
                            telemetryFlow = viewModel.liveTelemetry,
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

                        // 2. Top Precision Segmented LED Lightbar HUD & Telemetry Bar (Scoped 20 Hz collector)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.statusBars)
                                .align(Alignment.TopCenter)
                        ) {
                            ScopedLightbarHud(
                                telemetryFlow = viewModel.liveTelemetry,
                                isNightMode = uiState.isNightMode,
                                themeMode = uiState.themeMode,
                                gnssSource = uiState.gnssSource,
                                slidingHitchConfig = uiState.slidingHitchConfig,
                                uTurnPrompt = uiState.uTurnPrompt,
                                onToggleSimulation = { viewModel.toggleSimulationMode() },
                                onOpenSettings = { viewModel.openSettingsDialog(true) },
                                onOpenSlidingHitch = { viewModel.openSlidingHitchDialog(true) },
                                onToggleDashboard = { viewModel.toggleOperatorDashboard() },
                                onOpenNmeaDiagnostics = { viewModel.openNmeaDiagnosticsDialog(true) },
                                onRecenterGps = { viewModel.recenterOriginToCurrentGps() }
                            )

                            // Dedicated A-B Swath Active Setting Banner (Scoped live distance read)
                            AnimatedVisibility(
                                visible = uiState.isSettingAB && uiState.tempPointA != null,
                                enter = fadeIn() + slideInVertically(),
                                exit = fadeOut() + slideOutVertically()
                            ) {
                                ScopedAbSettingBanner(
                                    tempPointA = uiState.tempPointA,
                                    telemetryFlow = viewModel.liveTelemetry,
                                    onSetPointB = { viewModel.setPointB() },
                                    onCancelSetAB = { viewModel.cancelSetAB() }
                                )
                            }

                            // Operator Steering Wheel Override Urgent Alert Banner
                            AnimatedVisibility(
                                visible = uiState.isOperatorOverrideAlertVisible,
                                enter = fadeIn() + slideInVertically(),
                                exit = fadeOut() + slideOutVertically()
                            ) {
                                Surface(
                                    color = Color(0xF0991B1B),
                                    shape = RoundedCornerShape(18.dp),
                                    border = BorderStroke(2.dp, Color(0xFFF87171)),
                                    shadowElevation = 16.dp,
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .padding(top = 4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "OPERATOR OVERRIDE: AUTOSTEER CANCELLED",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                            Text(
                                                text = uiState.operatorOverrideReason.ifEmpty { "Manual steering wheel intervention detected" },
                                                color = Color(0xFFFEE2E2),
                                                fontSize = 11.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        IconButton(
                                            onClick = { viewModel.dismissOperatorOverrideAlert() },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.White, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }

                            // Floating Status Banner Alert (Daylight, Emerald & OLED aware)
                            AnimatedVisibility(
                                visible = uiState.statusMessage.isNotBlank() && !(uiState.isSettingAB && uiState.tempPointA != null) && !uiState.isOperatorOverrideAlertVisible,
                                enter = fadeIn() + slideInVertically(),
                                exit = fadeOut() + slideOutVertically()
                            ) {
                                Surface(
                                    color = when {
                                        isOledNight -> Color(0xF50A0400)
                                        isDaylight -> Color(0xF7F8FAFC)
                                        else -> Color(0xEB0F172A)
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(
                                        if (isDaylight) 1.5.dp else 1.dp,
                                        when {
                                            isOledNight -> Color(0x66FF9100)
                                            isDaylight -> Color(0xFF334155)
                                            else -> Color(0x3300F59B)
                                        }
                                    ),
                                    shadowElevation = if (isOledNight) 0.dp else 8.dp,
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
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    when {
                                                        isOledNight -> Color(0xFFFF9100)
                                                        isDaylight -> Color(0xFF047857)
                                                        else -> Color(0xFF00F59B)
                                                    }
                                                )
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = uiState.statusMessage,
                                            color = when {
                                                isOledNight -> Color(0xFFFFB300)
                                                isDaylight -> Color(0xFF0F172A)
                                                else -> Color(0xFFF1F5F9)
                                            },
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Dedicated High-Contrast Operator Dashboard Overlay (Scoped telemetry collector)
                        AnimatedVisibility(
                            visible = uiState.showOperatorDashboard,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .padding(top = 104.dp, start = 8.dp, end = 78.dp)
                        ) {
                            val liveTelemetry by viewModel.liveTelemetry.collectAsStateWithLifecycle()
                            OperatorDashboardOverlay(
                                vehicleState = liveTelemetry.vehicleState,
                                vehicleConfig = uiState.vehicleConfig,
                                currentABLine = uiState.currentABLine,
                                activeSwathIndex = liveTelemetry.vehicleState.activeSwathIndex,
                                gnssSource = uiState.gnssSource,
                                isNightMode = uiState.isNightMode,
                                cameraViewMode = uiState.cameraViewMode,
                                slidingHitchConfig = uiState.slidingHitchConfig,
                                slidingHitchState = liveTelemetry.slidingHitchState,
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

                        // 3. AgSteer In-Cab Right Dock and Bottom Swath Guidance Bar (Isolated from 20 Hz pose updates!)
                        CabControls(
                            isAutoSteerEngaged = uiState.vehicleState.isAutoSteerEngaged,
                            isSectionMasterActive = uiState.implementConfig.isMasterActive,
                            cameraViewMode = uiState.cameraViewMode,
                            isNightMode = uiState.isNightMode,
                            themeMode = uiState.themeMode,
                            showSatelliteOverlay = uiState.showSatelliteOverlay,
                            satelliteMapProvider = uiState.satelliteMapProvider,
                            gnssSource = uiState.gnssSource,
                            onToggleAutoSteer = { viewModel.toggleAutoSteer() },
                            onToggleSectionMaster = { viewModel.toggleMasterSection() },
                            onToggleCameraMode = { viewModel.cycleCameraViewMode() },
                            onSetPointA = { viewModel.setPointA() },
                            onSetPointB = { viewModel.setPointB() },
                            onCancelSetAB = { viewModel.cancelSetAB() },
                            currentABLine = uiState.currentABLine,
                            onPromptDeleteGuidancePath = { viewModel.openDeletePathConfirmDialog(true) },
                            onSnapABLine = { viewModel.snapABLineToVehicle() },
                            onReverseDirection = { viewModel.reverseABLineDirection() },
                            onCycleSwath = { dir -> viewModel.cycleSwath(dir) },
                            onSkipPass = { count -> viewModel.skipPass(count) },
                            onNudgeSwath = { cm -> viewModel.nudgeSwath(cm) },
                            onNudgeHalfRow22 = { isRight -> viewModel.nudgeHalfRow22Inch(isRight) },
                            onNudgeFullRow22 = { isRight -> viewModel.nudgeFullRow22Inch(isRight) },
                            onZeroWas = { viewModel.zeroWasAtCurrentAngle() },
                            onToggleNightMode = { viewModel.toggleNightMode() },
                            onCycleThemeMode = { viewModel.cycleThemeMode() },
                            onSelectThemeMode = { mode -> viewModel.setThemeMode(mode) },
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
                            onSelectBasemapProvider = { provider ->
                                if (provider == null) {
                                    viewModel.setSatelliteOverlay(false)
                                } else {
                                    viewModel.setBasemapProvider(provider)
                                }
                            },
                            showTopographyMap = uiState.showTopographyMap,
                            isTerrainCompensationEnabled = uiState.isTerrainCompensationEnabled,
                            onToggleTopographyMap = { viewModel.toggleTopographyMap() },
                            onToggleTerrainCompensation = { viewModel.toggleTerrainCompensation() },
                            onZoomIn = { viewModel.zoomIn() },
                            onZoomOut = { viewModel.zoomOut() },
                            onOpenSettings = { viewModel.openSettingsDialog(true) },
                            onOpenFieldDialog = { viewModel.openFieldDialog(true) },
                            onOpenAgIoDialog = { viewModel.openAgIoDialog(true) },
                            onOpenVirtualDrive = { viewModel.openVirtualDrive(true) },
                            onOpenSteerAggressionDialog = { viewModel.openSteerAggressionDialog(true) },
                            onOpenWasDialog = { viewModel.openWasCalibrationDialog(true) },
                            onOpenUm982Dialog = { viewModel.openUm982Dialog(true) },
                            onToggleDashboard = { viewModel.toggleOperatorDashboard() },
                            onOpenNmeaDiagnostics = { viewModel.openNmeaDiagnosticsDialog(true) },
                            onOpenPathMemory = { viewModel.openPathMemoryDialog(true) },
                            onOpenCultivatorSplitter = { viewModel.openCultivatorSplitDialog(true) },
                            onOpenGamepadWizard = { viewModel.openGamepadWizard(true) },
                            onOpenScvHitchWizard = { viewModel.openScvHitchWizard(true) },
                            onOpenUsbBackup = { viewModel.openUsbBackupSyncDialog(true) },
                            onToggleCultivatorPass = { viewModel.toggleCultivatorSplitPass() },
                            activeCultivatorPass = uiState.activeCultivatorSplitPass,
                            hasCultivatorSplit = (uiState.splitCultivatorLineA != null || uiState.activeRecallPath != null),
                            isRecordingPath = uiState.isRecordingPath,
                            activeRecallPathName = uiState.activeRecallPath?.name,
                            threePointConfig = uiState.threePointConfig,
                            onToggleThreePoint = { viewModel.toggleThreePointHitch() },
                            onTriggerSoftKey = { idx -> viewModel.triggerSoftKey(idx) },
                            slidingHitchConfig = uiState.slidingHitchConfig,
                            onOpenSlidingHitch = { viewModel.openSlidingHitchDialog(true) },
                            onToggleSimulationMode = { viewModel.toggleSimulationMode() },
                            onSetSimSpeed = { speed -> viewModel.setSimSpeed(speed) },
                            onToggleSimReverse = { viewModel.toggleSimReverse() },
                            onRecenterGps = { viewModel.recenterOriginToCurrentGps() },
                            tempPointA = uiState.tempPointA,
                            isSettingAB = uiState.isSettingAB,
                            telemetryFlow = viewModel.liveTelemetry,
                            modifier = Modifier.fillMaxSize()
                        )

                        // 4. Configuration & Tuning Dialog (Scoped hardware/telemetry collectors)
                        if (uiState.showSettingsDialog) {
                            val usbStats by viewModel.usbManager.usbStats.collectAsStateWithLifecycle()
                            val liveTelemetry by viewModel.liveTelemetry.collectAsStateWithLifecycle()
                            SettingsDialog(
                                guidanceSettings = uiState.guidanceSettings,
                                vehicleConfig = uiState.vehicleConfig,
                                implementConfig = uiState.implementConfig,
                                steerValveConfig = uiState.steerValveConfig,
                                gnssReceiverType = uiState.gnssReceiverType,
                                gnssSource = uiState.gnssSource,
                                usbStats = usbStats,
                                currentRoll = liveTelemetry.vehicleState.rollDeg,
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
                                currentGeoPosition = liveTelemetry.vehicleState.geoPosition,
                                onSetManualLocation = { lat, lon, alt, name ->
                                    viewModel.setManualFarmLocation(lat, lon, alt, name)
                                },
                                onRecenterToTabletGps = { viewModel.recenterOriginToCurrentGps() },
                                onDismiss = { viewModel.openSettingsDialog(false) }
                            )
                        }

                        // 5. AgIO Communication Hub & Diagnostics Dialog
                        if (uiState.showAgIoDialog) {
                            val udpStats by viewModel.udpManager.stats.collectAsStateWithLifecycle()
                            val ntripStats by viewModel.ntripClient.stats.collectAsStateWithLifecycle()
                            AgIoHubDialog(
                                udpStats = udpStats,
                                ntripStatus = ntripStats,
                                ntripConfig = viewModel.ntripConfig,
                                onConnectNtrip = { cfg -> viewModel.connectNtrip(cfg) },
                                onDisconnectNtrip = { viewModel.disconnectNtrip() },
                                onSendSteerSettings = { kp, ki, kd, maxSteer, countsPerDeg ->
                                    viewModel.sendSteerSettings(kp, ki, kd, maxSteer, countsPerDeg)
                                },
                                onDismiss = { viewModel.openAgIoDialog(false) }
                            )
                        }

                        // 6. Field & Pattern Storage Management Dialog
                        if (uiState.showFieldDialog) {
                            val liveTelemetry by viewModel.liveTelemetry.collectAsStateWithLifecycle()
                            FieldManagerDialog(
                                currentField = uiState.currentField,
                                savedFieldsList = uiState.savedFieldsList,
                                boundaryVertices = uiState.fieldBoundary,
                                isRecordingBoundary = uiState.isRecordingBoundary,
                                currentABLine = uiState.currentABLine,
                                currentCurveLine = uiState.currentCurveLine,
                                isRecordingCurve = uiState.isRecordingCurve,
                                currentTractorHeading = liveTelemetry.vehicleState.headingDeg,
                                implementWidthMeters = uiState.implementConfig.toolWidth,
                                appliedSegmentsCount = liveTelemetry.appliedSegmentsCount,
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
                            val liveTelemetry by viewModel.liveTelemetry.collectAsStateWithLifecycle()
                            VirtualCabDriveOverlay(
                                isAutoSteerEngaged = uiState.vehicleState.isAutoSteerEngaged,
                                simSpeedKmh = liveTelemetry.vehicleState.speedKmh,
                                actualSteerAngleDeg = liveTelemetry.vehicleState.actualSteerAngleDeg,
                                targetSteerAngleDeg = liveTelemetry.vehicleState.targetSteerAngleDeg,
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

                        // 7.5. Precision Steering Aggression & Operator Safety Override Dialog
                        if (uiState.showSteerAggressionDialog) {
                            SteerAggressionDialog(
                                guidanceSettings = uiState.guidanceSettings,
                                isAutoSteerEngaged = uiState.vehicleState.isAutoSteerEngaged,
                                onSetSteeringAggression = { pct -> viewModel.setSteeringAggression(pct) },
                                onSetOperatorOverrideSensitivity = { deg -> viewModel.setOperatorOverrideSensitivity(deg) },
                                onToggleOperatorOverride = { enabled -> viewModel.toggleOperatorOverride(enabled) },
                                onUpdateGuidanceSettings = { cfg -> viewModel.updateGuidanceSettings(cfg) },
                                onDismiss = { viewModel.openSteerAggressionDialog(false) }
                            )
                        }

                        // 8. WAS Zeroing & Live PID/PWM Oscilloscope Bench Calibration Dialog
                        if (uiState.showWasCalibrationDialog) {
                            val udpStats by viewModel.udpManager.stats.collectAsStateWithLifecycle()
                            val liveTelemetry by viewModel.liveTelemetry.collectAsStateWithLifecycle()
                            WasCalibrationDialog(
                                hardwareStatus = udpStats.hardwareStatus,
                                targetSteerAngleDeg = liveTelemetry.vehicleState.targetSteerAngleDeg,
                                steerValveConfig = uiState.steerValveConfig,
                                onUpdateSteerValveConfig = { cfg -> viewModel.updateSteerValveConfig(cfg) },
                                onDismiss = { viewModel.openWasCalibrationDialog(false) }
                            )
                        }

                        // 9. Unicore UM982 Dual-Antenna RTK Diagnostics & Config Wizard
                        if (uiState.showUm982Dialog) {
                            val usbStats by viewModel.usbManager.usbStats.collectAsStateWithLifecycle()
                            val udpStats by viewModel.udpManager.stats.collectAsStateWithLifecycle()
                            val liveTelemetry by viewModel.liveTelemetry.collectAsStateWithLifecycle()
                            Um982DiagnosticsDialog(
                                vehicleState = liveTelemetry.vehicleState,
                                lastSentence = usbStats.lastSentence.ifEmpty { udpStats.lastSentence },
                                onSendCommand = { cmd -> viewModel.sendUm982Command(cmd) },
                                onDismiss = { viewModel.openUm982Dialog(false) }
                            )
                        }

                        // 9.5. Dedicated NMEA Diagnostics & Field Troubleshooting Dialog
                        if (uiState.showNmeaDiagnosticsDialog) {
                            val liveTelemetry by viewModel.liveTelemetry.collectAsStateWithLifecycle()
                            NmeaDiagnosticsDialog(
                                diagnosticsManager = viewModel.nmeaDiagnosticsManager,
                                vehicleState = liveTelemetry.vehicleState,
                                onSendCommand = { cmd -> viewModel.sendNmeaCommand(cmd) },
                                onDismiss = { viewModel.openNmeaDiagnosticsDialog(false) }
                            )
                        }

                        // 10. Field Obstacles & Hazard Markers Dialog
                        if (uiState.showObstacleDialog) {
                            val liveTelemetry by viewModel.liveTelemetry.collectAsStateWithLifecycle()
                            ObstacleDialog(
                                obstacles = uiState.obstacles,
                                currentLat = liveTelemetry.vehicleState.geoPosition.latitude,
                                currentLon = liveTelemetry.vehicleState.geoPosition.longitude,
                                onAddObstacleAtVehicle = { type, name, radius, notes ->
                                    viewModel.addObstacleAtVehicle(type, name, radius, notes)
                                },
                                onDeleteObstacle = { id -> viewModel.deleteObstacle(id) },
                                onClearAllObstacles = { viewModel.clearAllObstacles() },
                                onDismiss = { viewModel.openObstacleDialog(false) }
                            )
                        }

                        // 11. Implement & Machine Presets Dialog
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
                            val liveTelemetry by viewModel.liveTelemetry.collectAsStateWithLifecycle()
                            val segmentsSnapshot = remember(liveTelemetry.coverageVersion) {
                                viewModel.getAllAppliedSegmentsSnapshot()
                            }
                            ExportFieldDialog(
                                fieldName = uiState.currentField?.name ?: "Montevideo_Farm_Field",
                                originGeo = viewModel.originGeo,
                                fieldBoundary = uiState.fieldBoundary,
                                headlandBoundary = uiState.headlandBoundary,
                                abLine = uiState.currentABLine,
                                obstacles = uiState.obstacles,
                                appliedSegments = segmentsSnapshot,
                                workedAcres = liveTelemetry.workedAcres,
                                onDismiss = { viewModel.openExportDialog(false) }
                            )
                        }

                        // 13. RTK Base Station Survey & Reference Location Dialog
                        if (uiState.showRtkBaseDialog) {
                            val liveTelemetry by viewModel.liveTelemetry.collectAsStateWithLifecycle()
                            RtkBaseStationDialog(
                                config = uiState.rtkBaseStationConfig,
                                vehicleState = liveTelemetry.vehicleState,
                                originGeo = viewModel.originGeo,
                                onSaveConfig = { cfg -> viewModel.updateRtkBaseConfig(cfg) },
                                onDismiss = { viewModel.openRtkBaseDialog(false) }
                            )
                        }

                        // 14. Field Setup & Guidance Wizard
                        if (uiState.showFieldWizardDialog) {
                            val liveTelemetry by viewModel.liveTelemetry.collectAsStateWithLifecycle()
                            FieldSetupWizardDialog(
                                currentTractorHeading = liveTelemetry.vehicleState.headingDeg,
                                currentTractorPos = liveTelemetry.vehicleState.localPivotPosition,
                                implementPresets = ImplementPreset.getAllPresets(),
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
                            FormFieldFromAbDialog(
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
                            val liveTelemetry by viewModel.liveTelemetry.collectAsStateWithLifecycle()
                            OuterBoundaryCornersDialog(
                                capturedCorners = uiState.capturedCorners,
                                currentTractorPos = liveTelemetry.vehicleState.localPivotPosition,
                                swathWidthMeters = uiState.implementConfig.toolWidth,
                                onCaptureCorner = { viewModel.captureCurrentCorner() },
                                onClearCorners = { viewModel.clearCapturedCorners() },
                                onConfirmCorners = { name, farm -> viewModel.finishOuterCorners(name, farm) },
                                onDismiss = { viewModel.dismissOuterBoundaryDialog() }
                            )
                        }

                        // 17. Second GPS Controller & Sliding Hitch Implement Steering Dialog
                        if (uiState.showSlidingHitchDialog) {
                            val liveTelemetry by viewModel.liveTelemetry.collectAsStateWithLifecycle()
                            SlidingHitchDialog(
                                config = uiState.slidingHitchConfig,
                                state = liveTelemetry.slidingHitchState,
                                tractorXteCm = liveTelemetry.vehicleState.crossTrackErrorCm,
                                secondGpsStats = uiState.secondGpsStats,
                                onSaveConfig = { cfg -> viewModel.updateSlidingHitchConfig(cfg) },
                                onCenterHitch = { viewModel.centerSlidingHitch() },
                                onNudgeHitch = { deltaCm -> viewModel.nudgeSlidingHitch(deltaCm) },
                                onToggleAutoHitch = { enabled -> viewModel.toggleAutoHitch(enabled) },
                                onDismiss = { viewModel.openSlidingHitchDialog(false) }
                            )
                        }

                        // 18. Path Memory Recall & Exact Replay Dialog
                        if (uiState.showPathMemoryDialog) {
                            PathMemoryDialog(
                                isRecordingPath = uiState.isRecordingPath,
                                recordedPathCount = uiState.currentRecordingPathPoints.size,
                                activeRecallPath = uiState.activeRecallPath,
                                savedPaths = uiState.savedRecordedPaths,
                                onStartPathRecording = { name, rows, spacing, isPlanter ->
                                    viewModel.startPathRecording(name, rows, spacing, isPlanter)
                                },
                                onStopPathRecording = { viewModel.stopAndSavePathRecording() },
                                onSelectRecallPath = { path -> viewModel.selectRecallPath(path) },
                                onClearRecallPath = { viewModel.clearRecallPath() },
                                onOpenCultivatorSplitter = { _ ->
                                    viewModel.openPathMemoryDialog(false)
                                    viewModel.openCultivatorSplitDialog(true)
                                },
                                onDeletePath = { id -> viewModel.deleteRecordedPath(id) },
                                onDismiss = { viewModel.openPathMemoryDialog(false) }
                            )
                        }

                        // 19. Organic 24R -> 12R Cultivator Pass Splitting Dialog
                        if (uiState.showCultivatorSplitDialog) {
                            OrganicCultivatorSplitDialog(
                                planterAbLine = uiState.currentABLine,
                                planterRecordedPath = uiState.activeRecallPath,
                                activeSplitPass = uiState.activeCultivatorSplitPass,
                                onApplySplitAbLine = { ab, pass, offset ->
                                    viewModel.applySplitCultivatorAbLine(ab, pass, offset)
                                },
                                onApplySplitRecordedPath = { path, pass, offset ->
                                    viewModel.applySplitCultivatorPath(path, pass, offset)
                                },
                                onDismiss = { viewModel.openCultivatorSplitDialog(false) }
                            )
                        }

                        // 20. PS4 & Gamepad Control Setup Wizard Dialog
                        if (uiState.showGamepadWizardDialog) {
                            GamepadWizardDialog(
                                bindings = uiState.gamepadBindings,
                                lastPressedButton = uiState.lastPressedGamepadButton,
                                onSaveBinding = { b -> viewModel.saveGamepadBinding(b) },
                                onDeleteBinding = { id -> viewModel.deleteGamepadBinding(id) },
                                onResetDefaultBindings = { viewModel.resetDefaultGamepadBindings() },
                                onDismiss = { viewModel.openGamepadWizard(false) }
                            )
                        }

                        // 21. 1-Tap USB Field Backup & Multi-Tractor Sync Dialog
                        if (uiState.showUsbBackupSyncDialog) {
                            UsbBackupSyncDialog(
                                fieldCount = uiState.savedFieldsList.size.coerceAtLeast(1),
                                pathCount = uiState.savedRecordedPaths.size,
                                obstacleCount = uiState.obstacles.size,
                                onExportUsbBackup = { viewModel.exportUsbBackupPackage(this@MainActivity) },
                                onRestoreUsbBackup = { viewModel.openExportDialog(true) },
                                onDismiss = { viewModel.openUsbBackupSyncDialog(false) }
                            )
                        }

                        // 22. SCV Hydraulics & 3-Point Hitch Trigger Mapping Setup Wizard
                        if (uiState.showScvHitchWizard) {
                            ScvHitchMappingWizardDialog(
                                threePointConfig = uiState.threePointConfig,
                                scvValves = uiState.scvValves,
                                triggerBindings = uiState.triggerBindings,
                                onUpdateThreePointConfig = { cfg -> viewModel.updateThreePointConfig(cfg) },
                                onSetScvState = { id, state -> viewModel.setScvState(id, state) },
                                onUpdateScvValve = { v -> viewModel.updateScvValve(v) },
                                onSaveTriggerBinding = { b -> viewModel.saveTriggerBinding(b) },
                                onDeleteTriggerBinding = { id -> viewModel.deleteTriggerBinding(id) },
                                onResetDefaultBindings = { viewModel.resetDefaultTriggerBindings() },
                                onExecuteAction = { act -> viewModel.executeCabAction(act) },
                                onDismiss = { viewModel.openScvHitchWizard(false) }
                            )
                        }

                        // 23. Delete Guidance Path Confirmation Dialog
                        if (uiState.showDeletePathConfirmDialog) {
                            AlertDialog(
                                onDismissRequest = { viewModel.openDeletePathConfirmDialog(false) },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.DeleteForever,
                                        contentDescription = null,
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(32.dp)
                                    )
                                },
                                title = {
                                    Text(
                                        text = "Delete Current Guidance Path?",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = Color.White
                                    )
                                },
                                text = {
                                    Text(
                                        text = "This will delete and clear the active AB guidance path and disengage auto-steer. All parallel field passes will be removed.\n\nAre you sure you want to continue?",
                                        fontSize = 14.sp,
                                        color = Color(0xFFCBD5E1)
                                    )
                                },
                                confirmButton = {
                                    Button(
                                        onClick = { viewModel.confirmDeleteCurrentGuidancePath() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFDC2626),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Delete Path", fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = {
                                    OutlinedButton(
                                        onClick = { viewModel.openDeletePathConfirmDialog(false) },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Cancel / Keep Path")
                                    }
                                },
                                containerColor = Color(0xFF0F172A),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.testTag("delete_path_confirm_dialog")
                            )
                        }

                        // Explicit top-most edge-to-edge Amber Night Filter overlay
                        if (isOledNight) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0x18FF8C00))
                                    .testTag("oled_night_amber_scrim")
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (event != null && (event.source and android.view.InputDevice.SOURCE_GAMEPAD == android.view.InputDevice.SOURCE_GAMEPAD ||
                    event.source and android.view.InputDevice.SOURCE_JOYSTICK == android.view.InputDevice.SOURCE_JOYSTICK ||
                    event.source and android.view.InputDevice.SOURCE_KEYBOARD == android.view.InputDevice.SOURCE_KEYBOARD)) {
            val handled = viewModel.onGamepadKeyDown(keyCode)
            if (handled) return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
private fun ColumnScope.ScopedAbSettingBanner(
    tempPointA: Vec2?,
    telemetryFlow: StateFlow<LiveTelemetryState>,
    onSetPointB: () -> Unit,
    onCancelSetAB: () -> Unit
) {
    val live by telemetryFlow.collectAsStateWithLifecycle()
    val distFromA = tempPointA?.distanceTo(live.vehicleState.localPivotPosition) ?: 0.0
    val distFt = distFromA * 3.28084
    Surface(
        color = Color(0xF00284C7),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.5.dp, Color(0xFF38BDF8)),
        shadowElevation = 12.dp,
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(top = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Timeline,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Point A Set | Distance: ${String.format(Locale.US, "%.1f", distFromA)}m (${String.format(Locale.US, "%.0f", distFt)}ft)",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(10.dp))
            Button(
                onClick = onSetPointB,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.heightIn(min = 36.dp)
            ) {
                Text("SET B NOW", fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onCancelSetAB,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

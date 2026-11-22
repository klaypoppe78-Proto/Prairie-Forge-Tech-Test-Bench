package com.example.agopengps.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agopengps.CameraViewMode
import com.example.agopengps.GnssSourceMode
import com.example.agopengps.LiveTelemetryState
import com.example.agopengps.io.ThreePointHitchConfig
import com.example.agopengps.io.ThreePointState
import com.example.agopengps.map.MapTileProvider
import com.example.agopengps.navigation.*
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

@Composable
fun CabControls(
    isAutoSteerEngaged: Boolean,
    isSectionMasterActive: Boolean,
    cameraViewMode: CameraViewMode,
    isNightMode: Boolean,
    themeMode: DisplayThemeMode,
    showSatelliteOverlay: Boolean,
    satelliteMapProvider: MapTileProvider,
    gnssSource: GnssSourceMode,
    simSpeedKmh: Double = 0.0,
    onToggleAutoSteer: () -> Unit,
    onToggleSectionMaster: () -> Unit,
    onToggleCameraMode: () -> Unit,
    onSetPointA: () -> Unit,
    onSetPointB: () -> Unit,
    onCancelSetAB: () -> Unit = {},
    currentABLine: ABLine? = null,
    onPromptDeleteGuidancePath: () -> Unit = {},
    onSnapABLine: () -> Unit,
    onReverseDirection: () -> Unit = {},
    onCycleSwath: (Int) -> Unit,
    onSkipPass: (Int) -> Unit = {},
    onNudgeSwath: (Double) -> Unit,
    onNudgeHalfRow22: (Boolean) -> Unit = {},
    onNudgeFullRow22: (Boolean) -> Unit = {},
    onZeroWas: () -> Unit = {},
    onToggleNightMode: () -> Unit = {},
    onCycleThemeMode: () -> Unit = {},
    onSelectThemeMode: (DisplayThemeMode) -> Unit = {},
    onOpenObstacleDialog: () -> Unit = {},
    onOpenImplementPresetsDialog: () -> Unit = {},
    onOpenExportDialog: () -> Unit = {},
    onOpenFieldWizard: () -> Unit = {},
    onOpenFormFieldFromAb: () -> Unit = {},
    onOpenOuterBoundaryCorners: () -> Unit = {},
    onOpenRtkBaseDialog: () -> Unit = {},
    onSaveAndFinishField: () -> Unit = {},
    onToggleBoundaryRecording: () -> Unit = {},
    onToggleSatelliteOverlay: () -> Unit = {},
    onCycleSatelliteProvider: () -> Unit = {},
    onSelectBasemapProvider: (MapTileProvider?) -> Unit = {},
    showTopographyMap: Boolean = false,
    isTerrainCompensationEnabled: Boolean = false,
    onToggleTopographyMap: () -> Unit = {},
    onToggleTerrainCompensation: () -> Unit = {},
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFieldDialog: () -> Unit,
    onOpenAgIoDialog: () -> Unit,
    onOpenVirtualDrive: () -> Unit = {},
    onOpenSteerAggressionDialog: () -> Unit = {},
    onOpenWasDialog: () -> Unit = {},
    onOpenUm982Dialog: () -> Unit = {},
    onToggleDashboard: () -> Unit = {},
    onOpenNmeaDiagnostics: () -> Unit = {},
    onOpenPathMemory: () -> Unit = {},
    onOpenCultivatorSplitter: () -> Unit = {},
    onOpenGamepadWizard: () -> Unit = {},
    onOpenScvHitchWizard: () -> Unit = {},
    onOpenUsbBackup: () -> Unit = {},
    onToggleCultivatorPass: () -> Unit = {},
    activeCultivatorPass: CultivatorSplitPass = CultivatorSplitPass.PASS_A_LEFT,
    hasCultivatorSplit: Boolean = false,
    isRecordingPath: Boolean = false,
    activeRecallPathName: String? = null,
    threePointConfig: ThreePointHitchConfig = ThreePointHitchConfig(),
    onToggleThreePoint: () -> Unit = {},
    onTriggerSoftKey: (Int) -> Unit = {},
    slidingHitchConfig: SlidingHitchConfig = SlidingHitchConfig(),
    slidingHitchState: SlidingHitchState = SlidingHitchState(),
    onOpenSlidingHitch: () -> Unit = {},
    onToggleSimulationMode: () -> Unit,
    onSetSimSpeed: (Double) -> Unit,
    onToggleSimReverse: () -> Unit,
    onRecenterGps: () -> Unit = {},
    tempPointA: Vec2? = null,
    isSettingAB: Boolean = false,
    vehiclePivot: Vec2 = Vec2(0.0, 0.0),
    telemetryFlow: StateFlow<LiveTelemetryState>? = null,
    modifier: Modifier = Modifier
) {
    val isDaylight = themeMode == DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST
    val isOledNight = themeMode == DisplayThemeMode.CAB_OLED_NIGHT

    val panelBg = when (themeMode) {
        DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST -> Color(0xF7F8FAFC)
        DisplayThemeMode.CAB_OLED_NIGHT -> Color(0xF5000000)
        DisplayThemeMode.STANDARD_AG -> Color(0xEE0F172A)
    }
    val glassBorder = when (themeMode) {
        DisplayThemeMode.CAB_OLED_NIGHT -> Color(0x55FFB300)
        DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST -> Color(0xFF334155)
        DisplayThemeMode.STANDARD_AG -> Color(0x4494A3B8)
    }
    val groupSurfaceBg = when (themeMode) {
        DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST -> Color(0xFFE2E8F0)
        DisplayThemeMode.CAB_OLED_NIGHT -> Color(0xFF140A00)
        DisplayThemeMode.STANDARD_AG -> Color(0xFF172236)
    }
    val groupBorderColor = when (themeMode) {
        DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST -> Color(0xFF94A3B8)
        DisplayThemeMode.CAB_OLED_NIGHT -> Color(0x44FF9100)
        DisplayThemeMode.STANDARD_AG -> Color(0x3864748B)
    }
    val groupHeaderColor = when (themeMode) {
        DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST -> Color(0xFF334155)
        DisplayThemeMode.CAB_OLED_NIGHT -> Color(0xFFFFB300)
        DisplayThemeMode.STANDARD_AG -> Color(0xFF94A3B8)
    }
    val primaryTextColor = when {
        isDaylight -> Color(0xFF0F172A)
        isOledNight -> Color(0xFFFFE0B2)
        else -> Color(0xFFF8FAFC)
    }

    var isDockExpanded by remember { mutableStateOf(true) }
    var showToolsSheet by remember { mutableStateOf(false) }

    val cameraLabel = when (cameraViewMode) {
        CameraViewMode.CAB_3D -> "3D Cab"
        CameraViewMode.BIRD_EYE_2D -> "2D Top"
        CameraViewMode.NORTH_UP -> "North"
        CameraViewMode.FREE_PAN -> "Pan"
    }
    val themeIcon = when (themeMode) {
        DisplayThemeMode.STANDARD_AG -> Icons.Default.Brightness6
        DisplayThemeMode.CAB_OLED_NIGHT -> Icons.Default.NightlightRound
        DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST -> Icons.Default.WbSunny
    }
    val themeLabel = when (themeMode) {
        DisplayThemeMode.STANDARD_AG -> "Emerald"
        DisplayThemeMode.CAB_OLED_NIGHT -> "Night"
        DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST -> "Daylight"
    }

    Box(modifier = modifier.fillMaxSize()) {

        // RIGHT CONTROL RAIL
        AnimatedVisibility(
            visible = isDockExpanded,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.End))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 10.dp)
            ) {
                // Dock Collapse Tab (>=48dp height touch target)
                Surface(
                    color = panelBg,
                    shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
                    border = BorderStroke(if (isDaylight) 1.5.dp else 1.dp, glassBorder),
                    modifier = Modifier
                        .heightIn(min = 52.dp)
                        .widthIn(min = 28.dp)
                        .clickable { isDockExpanded = false }
                        .testTag("collapse_right_dock_btn")
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Hide Menu Dock",
                            tint = if (isDaylight) Color(0xFF0F172A) else Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Dock Menu Column
                Surface(
                    color = panelBg,
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(if (isDaylight) 1.5.dp else 1.dp, glassBorder),
                    shadowElevation = if (isOledNight) 0.dp else 12.dp,
                    modifier = Modifier.pointerInput(Unit) {
                        detectHorizontalDragGestures { _, dragAmount ->
                            if (dragAmount > 20f) isDockExpanded = false
                        }
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. AutoSteer Master (60dp primary target)
                        val steerBrush = if (isAutoSteerEngaged) {
                            Brush.radialGradient(listOf(Color(0xFF00F59B), Color(0xFF047857)))
                        } else {
                            Brush.radialGradient(listOf(Color(0xFFF43F5E), Color(0xFF9F1239)))
                        }
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(steerBrush)
                                .clickable { onToggleAutoSteer() }
                                .testTag("autosteer_master_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = if (isAutoSteerEngaged) Icons.Default.Navigation else Icons.Default.NearMeDisabled,
                                    contentDescription = if (isAutoSteerEngaged) "Disengage Auto-Steer" else "Engage Auto-Steer",
                                    tint = if (isAutoSteerEngaged) Color(0xFF003822) else Color.White,
                                    modifier = Modifier.size(23.dp)
                                )
                                Text(
                                    text = if (isAutoSteerEngaged) "AUTO" else "MANUAL",
                                    color = if (isAutoSteerEngaged) Color(0xFF003822) else Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 9.sp
                                )
                            }
                        }

                        // 2. Field
                        ModernDockButton(Icons.Default.FolderOpen, "Field", if (isDaylight) Color(0xFF047857) else Color(0xFF10B981), themeMode, onOpenFieldDialog, "field_button")

                        // 3. Wizard
                        ModernDockButton(Icons.Default.AutoFixHigh, "Wizard", if (isDaylight) Color(0xFF0F766E) else Color(0xFF2DD4BF), themeMode, onOpenFieldWizard, "wizard_button")

                        // 4. Camera Mode
                        ModernDockButton(Icons.Default.ViewInAr, cameraLabel, if (isDaylight) Color(0xFF0369A1) else Color(0xFF38BDF8), themeMode, onToggleCameraMode, "camera_button")

                        // 5. Center GPS
                        ModernDockButton(Icons.Default.MyLocation, "Center", if (isDaylight) Color(0xFFB45309) else Color(0xFFFBBF24), themeMode, onRecenterGps, "center_button")

                        // 6. Theme Toggle (Emerald -> OLED Night -> High-Sun Daylight)
                        ModernDockButton(
                            icon = themeIcon,
                            label = themeLabel,
                            accentColor = when (themeMode) {
                                DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST -> Color(0xFFD97706)
                                DisplayThemeMode.CAB_OLED_NIGHT -> Color(0xFFFF9100)
                                DisplayThemeMode.STANDARD_AG -> Color(0xFF00F59B)
                            },
                            themeMode = themeMode,
                            onClick = onCycleThemeMode,
                            tag = "theme_button"
                        )

                        // 7. Tools Drawer
                        ModernDockButton(Icons.Default.Widgets, "Tools", if (isDaylight) Color(0xFF4338CA) else Color(0xFF818CF8), themeMode, { showToolsSheet = true }, "tools_button")

                        // 8. Settings
                        ModernDockButton(Icons.Default.Settings, "Setup", if (isDaylight) Color(0xFF334155) else Color(0xFFCBD5E1), themeMode, onOpenSettings, "settings_button")
                    }
                }
            }
        }

        // Pull-out tab when collapsed (>=48dp target)
        AnimatedVisibility(
            visible = !isDockExpanded,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Surface(
                color = panelBg,
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                border = BorderStroke(if (isDaylight) 1.5.dp else 1.dp, glassBorder),
                modifier = Modifier
                    .widthIn(min = 48.dp)
                    .heightIn(min = 64.dp)
                    .clickable { isDockExpanded = true }
                    .testTag("expand_right_dock_btn")
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.ChevronLeft,
                        contentDescription = "Show Menu Dock",
                        tint = if (isDaylight) Color(0xFF047857) else Color(0xFF00F59B),
                        modifier = Modifier.size(22.dp)
                    )
                    Text("MENU", color = primaryTextColor, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp)
                }
            }
        }

        // BOTTOM CONTROL DOCK (Proper navigation-bar / taskbar inset padding & 3 clear visual groups)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.navigationBars.union(WindowInsets.displayCutout.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                )
                .padding(start = 12.dp, end = if (isDockExpanded) 90.dp else 44.dp, bottom = 14.dp)
                .testTag("bottom_control_dock")
        ) {
            Surface(
                color = panelBg,
                border = BorderStroke(if (isDaylight) 1.5.dp else 1.dp, glassBorder),
                shape = RoundedCornerShape(22.dp),
                shadowElevation = if (isOledNight) 0.dp else 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // ======================================================
                    // GROUP 1: A-B SWATH CONTROLS
                    // ======================================================
                    DockControlGroup(
                        title = "A-B SWATH",
                        backgroundColor = groupSurfaceBg,
                        borderColor = groupBorderColor,
                        titleColor = groupHeaderColor
                    ) {
                        Button(
                            onClick = onSetPointA,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSettingAB && tempPointA != null) Color(0xFF0284C7) else Color(0xFF0369A1),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 56.dp).testTag("set_a_button")
                        ) {
                            Text(if (isSettingAB && tempPointA != null) "A ✓" else "SET A", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        }

                        // Isolated Set-B Button (only subscribes to telemetry while setting A-B!)
                        ScopedSetPointBButton(
                            isSettingAB = isSettingAB,
                            tempPointA = tempPointA,
                            fallbackPivot = vehiclePivot,
                            telemetryFlow = telemetryFlow,
                            onSetPointB = onSetPointB
                        )

                        if (isSettingAB && tempPointA != null) {
                            FilledTonalIconButton(
                                onClick = onCancelSetAB,
                                modifier = Modifier.size(48.dp).testTag("cancel_ab_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = if (isDaylight) Color(0xFFFFE4E6) else Color(0x33EF4444),
                                    contentColor = if (isDaylight) Color(0xFFBE123C) else Color(0xFFF87171)
                                )
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel Point A", modifier = Modifier.size(20.dp))
                            }
                        }

                        Button(
                            onClick = onSnapABLine,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDaylight) Color(0xFF1E293B) else Color(0xFF283548),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 56.dp).testTag("snap_ab_button")
                        ) {
                            Text("SNAP", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        }

                        Button(
                            onClick = onReverseDirection,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDaylight) Color(0xFF334155) else Color(0xFF1E293B),
                                contentColor = if (isDaylight) Color.White else Color(0xFFE2E8F0)
                            ),
                            border = BorderStroke(1.dp, if (isDaylight) Color(0xFF0F172A) else Color(0x5594A3B8)),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 52.dp).testTag("reverse_ab_button")
                        ) {
                            Text("180°", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        }

                        if (currentABLine != null) {
                            Button(
                                onClick = onPromptDeleteGuidancePath,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDaylight) Color(0xFFFFE4E6) else Color(0x38EF4444),
                                    contentColor = if (isDaylight) Color(0xFFBE123C) else Color(0xFFFCA5A5)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, if (isDaylight) Color(0xFFBE123C) else Color(0x88EF4444)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).testTag("delete_ab_path_btn")
                            ) {
                                Icon(Icons.Default.LayersClear, contentDescription = "Delete Active Path", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("CLR", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                            }
                        }
                    }

                    // ======================================================
                    // GROUP 2: PASS & HITCH STATUS
                    // ======================================================
                    DockControlGroup(
                        title = "PASS / HITCH",
                        backgroundColor = groupSurfaceBg,
                        borderColor = groupBorderColor,
                        titleColor = groupHeaderColor
                    ) {
                        // 12R Cultivator Split Toggle
                        val isLeftPass = activeCultivatorPass == CultivatorSplitPass.PASS_A_LEFT
                        val passChipBg = when {
                            isDaylight -> if (isLeftPass) Color(0xFFD1FAE5) else Color(0xFFE0F2FE)
                            else -> if (isLeftPass) Color(0x3810B981) else Color(0x3838BDF8)
                        }
                        val passChipFg = when {
                            isDaylight -> if (isLeftPass) Color(0xFF065F46) else Color(0xFF075985)
                            else -> if (isLeftPass) Color(0xFF6EE7B7) else Color(0xFF7DD3FC)
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = passChipBg,
                            border = BorderStroke(1.5.dp, passChipFg.copy(alpha = if (isDaylight) 0.75f else 0.7f)),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clickable { onToggleCultivatorPass() }
                                .testTag("cultivator_pass_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = "Cultivator Pass", tint = passChipFg, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isLeftPass) "12R LEFT" else "12R RIGHT",
                                    color = passChipFg,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // 3-Point Hitch Status & Toggle
                        val isHitchLowered = threePointConfig.state == ThreePointState.LOWERED || threePointConfig.state == ThreePointState.FLOAT
                        val hitchBg = when {
                            isDaylight -> if (isHitchLowered) Color(0xFFD1FAE5) else Color(0xFFFEF3C7)
                            else -> if (isHitchLowered) Color(0x38059669) else Color(0x38D97706)
                        }
                        val hitchFg = when {
                            isDaylight -> if (isHitchLowered) Color(0xFF065F46) else Color(0xFF92400E)
                            else -> if (isHitchLowered) Color(0xFF34D399) else Color(0xFFFBBF24)
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = hitchBg,
                            border = BorderStroke(1.5.dp, hitchFg.copy(alpha = if (isDaylight) 0.8f else 0.7f)),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .widthIn(min = 66.dp)
                                .clickable { onToggleThreePoint() }
                                .testTag("three_point_hitch_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (isHitchLowered) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                    contentDescription = "3-Point Hitch State",
                                    tint = hitchFg,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (isHitchLowered) "3-PT DN" else "3-PT UP",
                                    color = hitchFg,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Pass Stepper
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isDaylight) Color(0xFFFFFFFF) else Color(0xFF1E293B),
                            border = BorderStroke(1.2.dp, if (isDaylight) Color(0xFF475569) else Color(0x5594A3B8))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .widthIn(min = 58.dp)
                                        .clickable { onCycleSwath(-1) }
                                        .padding(horizontal = 10.dp)
                                        .testTag("pass_prev_button")
                                ) {
                                    Text("‹ PASS", color = primaryTextColor, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                }
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(26.dp)
                                        .background(if (isDaylight) Color(0xFFCBD5E1) else Color(0x4494A3B8))
                                )
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .widthIn(min = 58.dp)
                                        .clickable { onCycleSwath(1) }
                                        .padding(horizontal = 10.dp)
                                        .testTag("pass_next_button")
                                ) {
                                    Text("PASS ›", color = primaryTextColor, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }

                    // ======================================================
                    // GROUP 3: HIGH-CONTRAST ROW NUDGES (1", 11", 22") & ZERO WAS
                    // ======================================================
                    DockControlGroup(
                        title = "ROW NUDGE",
                        backgroundColor = groupSurfaceBg,
                        borderColor = groupBorderColor,
                        titleColor = groupHeaderColor
                    ) {
                        // 1" Micro Nudge Pair (High Contrast Amber)
                        NudgePairButton(
                            leftLabel = "‹ 1\"",
                            rightLabel = "1\" ›",
                            containerColor = if (isDaylight) Color(0xFFFEF3C7) else Color(0xFF2B210E),
                            borderColor = if (isDaylight) Color(0xFFB45309) else Color(0xFFFBBF24),
                            textColor = if (isDaylight) Color(0xFF78350F) else Color(0xFFFDE68A),
                            onLeft = { onNudgeSwath(-2.54) },
                            onRight = { onNudgeSwath(2.54) },
                            leftTag = "nudge_1in_left",
                            rightTag = "nudge_1in_right"
                        )

                        // 11" Half-Row Nudge Pair (High Contrast Cyan)
                        NudgePairButton(
                            leftLabel = "‹ 11\"",
                            rightLabel = "11\" ›",
                            containerColor = if (isDaylight) Color(0xFFE0F2FE) else Color(0xFF0D283E),
                            borderColor = if (isDaylight) Color(0xFF0369A1) else Color(0xFF38BDF8),
                            textColor = if (isDaylight) Color(0xFF0C4A6E) else Color(0xFFBAE6FD),
                            onLeft = { onNudgeHalfRow22(false) },
                            onRight = { onNudgeHalfRow22(true) },
                            leftTag = "nudge_11in_left",
                            rightTag = "nudge_11in_right"
                        )

                        // 22" Full-Row Nudge Pair (High Contrast Emerald)
                        NudgePairButton(
                            leftLabel = "‹ 22\"",
                            rightLabel = "22\" ›",
                            containerColor = if (isDaylight) Color(0xFFD1FAE5) else Color(0xFF0A2E23),
                            borderColor = if (isDaylight) Color(0xFF047857) else Color(0xFF34D399),
                            textColor = if (isDaylight) Color(0xFF064E3B) else Color(0xFFA7F3D0),
                            onLeft = { onNudgeFullRow22(false) },
                            onRight = { onNudgeFullRow22(true) },
                            leftTag = "nudge_22in_left",
                            rightTag = "nudge_22in_right"
                        )

                        // Zero WAS Button
                        Button(
                            onClick = onZeroWas,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDaylight) Color(0xFFD97706) else Color(0x44D97706),
                                contentColor = if (isDaylight) Color.White else Color(0xFFFDE68A)
                            ),
                            border = BorderStroke(1.2.dp, if (isDaylight) Color(0xFF92400E) else Color(0xFFFBBF24)),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 64.dp).testTag("zero_was_button")
                        ) {
                            Text("ZERO WAS", fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }

    // TOOLS DRAWER DIALOG
    if (showToolsSheet) {
        val dialogBg = if (isDaylight) Color(0xFFF8FAFC) else Color(0xFA0F172A)
        val dialogText = if (isDaylight) Color(0xFF0F172A) else Color.White
        val dialogSubText = if (isDaylight) Color(0xFF475569) else Color(0xFF94A3B8)

        Dialog(onDismissRequest = { showToolsSheet = false }) {
            Surface(
                color = dialogBg,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.5.dp, glassBorder),
                shadowElevation = 16.dp,
                modifier = Modifier.fillMaxWidth(0.94f).padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Image(
                                painter = painterResource(id = com.example.R.drawable.ic_sentinel_logo),
                                contentDescription = "Sentinel Guidance Logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                            )
                            Column {
                                Text("Sentinel Guidance Tools", color = dialogText, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                Text("Diagnostics, Telemetry & Setup", color = dialogSubText, fontSize = 11.sp)
                            }
                        }
                        IconButton(onClick = { showToolsSheet = false }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = dialogSubText)
                        }
                    }

                    // 1. NAVIGATION & SENSORS
                    Text("NAVIGATION & SENSORS", color = if (isDaylight) Color(0xFF0369A1) else Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.8.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModernToolCard(Icons.Default.Troubleshoot, "NMEA Diagnostics", "GNSS sentences & fix", Color(0xFF0284C7), isDaylight, Modifier.weight(1f)) {
                            showToolsSheet = false; onOpenNmeaDiagnostics()
                        }
                        ModernToolCard(Icons.Default.CellTower, "RTK Base Station", "Survey & baseline", Color(0xFF0D9488), isDaylight, Modifier.weight(1f)) {
                            showToolsSheet = false; onOpenRtkBaseDialog()
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModernToolCard(Icons.Default.Router, "AgIO Network Hub", "UDP / USB telemetry", Color(0xFF6366F1), isDaylight, Modifier.weight(1f)) {
                            showToolsSheet = false; onOpenAgIoDialog()
                        }
                        ModernToolCard(Icons.Default.Speed, "Steer Sensitivity & Safety", "Aggression & wheel override", Color(0xFF0284C7), isDaylight, Modifier.weight(1f)) {
                            showToolsSheet = false; onOpenSteerAggressionDialog()
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModernToolCard(Icons.Default.Tune, "WAS Calibration", "Steer sensor zero & span", Color(0xFF10B981), isDaylight, Modifier.weight(1f)) {
                            showToolsSheet = false; onOpenWasDialog()
                        }
                        ModernToolCard(Icons.Default.GpsFixed, "UM982 Dual Antenna", "True Heading & Pitch", Color(0xFF3B82F6), isDaylight, Modifier.weight(1f)) {
                            showToolsSheet = false; onOpenUm982Dialog()
                        }
                    }

                    // 2. HARDWARE & IMPLEMENTS
                    Text("HARDWARE & IMPLEMENTS", color = if (isDaylight) Color(0xFF047857) else Color(0xFF34D399), fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.8.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModernToolCard(Icons.Default.Build, "Implement Presets", "22\" Beet, 30\" Corn, Custom", Color(0xFF14B8A6), isDaylight, Modifier.weight(1f)) {
                            showToolsSheet = false; onOpenImplementPresetsDialog()
                        }
                        ModernToolCard(Icons.AutoMirrored.Filled.CompareArrows, "Sliding Hitch", "Implement steering", Color(0xFF06B6D4), isDaylight, Modifier.weight(1f)) {
                            showToolsSheet = false; onOpenSlidingHitch()
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModernToolCard(Icons.Default.Dashboard, "Operator Dashboard", "Full-cab live telemetry", Color(0xFF3B82F6), isDaylight, Modifier.weight(1f)) {
                            showToolsSheet = false; onToggleDashboard()
                        }
                        ModernToolCard(Icons.Default.Warning, "Hazard Pins", "Rocks & obstacles", Color(0xFFF59E0B), isDaylight, Modifier.weight(1f)) {
                            showToolsSheet = false; onOpenObstacleDialog()
                        }
                    }

                    // 3. ORGANIC ROW-CROP & ADVANCED CONTROLS
                    Text("ORGANIC ROW-CROP & HARDWARE CONTROLS", color = if (isDaylight) Color(0xFF047857) else Color(0xFF34D399), fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.8.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModernToolCard(Icons.Default.Route, "Path Memory Recall", if (isRecordingPath) "Recording Live Pass..." else if (activeRecallPathName != null) "Active: $activeRecallPathName" else "Exact trajectory memory", if (isRecordingPath) Color(0xFFEF4444) else Color(0xFF38BDF8), isDaylight, Modifier.weight(1f)) {
                            showToolsSheet = false; onOpenPathMemory()
                        }
                        ModernToolCard(Icons.AutoMirrored.Filled.CompareArrows, "24R → 12R Cultivator", "Split 24R Planter to 12R Pass", Color(0xFF10B981), isDaylight, Modifier.weight(1f)) {
                            showToolsSheet = false; onOpenCultivatorSplitter()
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModernToolCard(Icons.Default.Tune, "SCV & 3-Point Setup", "Pick & map hydraulic triggers", Color(0xFF0284C7), isDaylight, Modifier.weight(1f)) {
                            showToolsSheet = false; onOpenScvHitchWizard()
                        }
                        ModernToolCard(Icons.Default.Usb, "1-Tap USB Backup", "Sync fields & paths between tractors", Color(0xFF0EA5E9), isDaylight, Modifier.weight(1f)) {
                            showToolsSheet = false; onOpenUsbBackup()
                        }
                    }

                    // 4. MAPPING & SIMULATION
                    Text("MAPPING & SIMULATION", color = if (isDaylight) Color(0xFFB45309) else Color(0xFFFBBF24), fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.8.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModernToolCard(Icons.Default.Share, "Export Field", "KML, CSV, GeoJSON", Color(0xFF0EA5E9), isDaylight, Modifier.weight(1f)) {
                            showToolsSheet = false; onOpenExportDialog()
                        }
                        ModernToolCard(Icons.Default.SportsMotorsports, "Virtual Drive", "Steering & speed test", Color(0xFF10B981), isDaylight, Modifier.weight(1f)) {
                            showToolsSheet = false; onOpenVirtualDrive()
                        }
                    }

                    // Simulator Speed Control (Scoped live speed read only while Tools dialog is open)
                    if (gnssSource == GnssSourceMode.SIMULATOR) {
                        val liveSimSpeed = if (telemetryFlow != null) {
                            telemetryFlow.collectAsStateWithLifecycle().value.vehicleState.speedKmh
                        } else {
                            simSpeedKmh
                        }
                        Surface(
                            color = if (isDaylight) Color(0xFFFEF3C7) else Color(0x22F59E0B),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, if (isDaylight) Color(0xFFB45309) else Color(0x44F59E0B)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(onClick = onToggleSimReverse, shape = RoundedCornerShape(10.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                                    Text("REV / FWD", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                val simSpeedMph = liveSimSpeed * 0.621371
                                Text("SIM: ${String.format(Locale.US, "%.1f", simSpeedMph)} mph", color = dialogText, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                Slider(
                                    value = kotlin.math.abs(liveSimSpeed).toFloat().coerceIn(0f, 25f),
                                    onValueChange = { onSetSimSpeed(it.toDouble()) },
                                    valueRange = 0f..25f,
                                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                                )
                            }
                        }
                    }

                    // 5. IN-CAB DISPLAY THEMES
                    Text("IN-CAB DISPLAY THEME & BACKLIGHT", color = if (isDaylight) Color(0xFFB45309) else Color(0xFFFFB300), fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.8.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeChoiceTile(
                            label = "Ag Emerald",
                            icon = Icons.Default.Brightness6,
                            accent = Color(0xFF00F59B),
                            isSelected = themeMode == DisplayThemeMode.STANDARD_AG,
                            isDaylight = isDaylight,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectThemeMode(DisplayThemeMode.STANDARD_AG) }
                        )
                        ThemeChoiceTile(
                            label = "OLED Night",
                            icon = Icons.Default.NightlightRound,
                            accent = Color(0xFFFF9100),
                            isSelected = themeMode == DisplayThemeMode.CAB_OLED_NIGHT,
                            isDaylight = isDaylight,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectThemeMode(DisplayThemeMode.CAB_OLED_NIGHT) }
                        )
                        ThemeChoiceTile(
                            label = "Sunlight Day",
                            icon = Icons.Default.WbSunny,
                            accent = Color(0xFFD97706),
                            isSelected = themeMode == DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST,
                            isDaylight = isDaylight,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectThemeMode(DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST) }
                        )
                    }

                    // 6. TOPOGRAPHIC ELEVATION CONTOURS (VECTOR)
                    Text("TOPOGRAPHIC ELEVATION CONTOURS (VECTOR)", color = if (isDaylight) Color(0xFFB45309) else Color(0xFFFBBF24), fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.8.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (showTopographyMap) (if (isDaylight) Color(0xFFFEF3C7) else Color(0x33FBBF24)) else (if (isDaylight) Color(0xFFE2E8F0) else Color(0x1F94A3B8)),
                            border = BorderStroke(1.5.dp, if (showTopographyMap) (if (isDaylight) Color(0xFFB45309) else Color(0xFFFBBF24)) else Color(0x3394A3B8)),
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp).clickable { onToggleTopographyMap() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Terrain, contentDescription = null, tint = if (showTopographyMap) (if (isDaylight) Color(0xFFB45309) else Color(0xFFFBBF24)) else dialogSubText, modifier = Modifier.size(20.dp))
                                    Column {
                                        Text("5-ft Elevation Contours", color = dialogText, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        Text(if (showTopographyMap) "Contours & Quarter Box ON" else "Contours OFF (Clean Field)", color = dialogSubText, fontSize = 9.sp)
                                    }
                                }
                                Text(if (showTopographyMap) "ON" else "OFF", color = if (showTopographyMap) (if (isDaylight) Color(0xFFB45309) else Color(0xFFFBBF24)) else dialogSubText, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isTerrainCompensationEnabled) (if (isDaylight) Color(0xFFD1FAE5) else Color(0x3300F59B)) else (if (isDaylight) Color(0xFFE2E8F0) else Color(0x1F94A3B8)),
                            border = BorderStroke(1.5.dp, if (isTerrainCompensationEnabled) (if (isDaylight) Color(0xFF047857) else Color(0xFF00F59B)) else Color(0x3394A3B8)),
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp).clickable { onToggleTerrainCompensation() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Tune, contentDescription = null, tint = if (isTerrainCompensationEnabled) (if (isDaylight) Color(0xFF047857) else Color(0xFF00F59B)) else dialogSubText, modifier = Modifier.size(20.dp))
                                    Column {
                                        Text("Hillside AutoSteer", color = dialogText, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        Text(if (isTerrainCompensationEnabled) "Gravity Draft Bias ON" else "Flat GPS Mode", color = dialogSubText, fontSize = 9.sp)
                                    }
                                }
                                Text(if (isTerrainCompensationEnabled) "ON" else "OFF", color = if (isTerrainCompensationEnabled) (if (isDaylight) Color(0xFF047857) else Color(0xFF00F59B)) else dialogSubText, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }

                    // 7. SATELLITE & AERIAL BASEMAPS
                    Text("SATELLITE & AERIAL BASEMAPS (RASTER TILES)", color = if (isDaylight) Color(0xFF0369A1) else Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.8.sp)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (!showSatelliteOverlay) (if (isDaylight) Color(0xFFD1FAE5) else Color(0x3300F59B)) else (if (isDaylight) Color(0xFFE2E8F0) else Color(0x1F94A3B8)),
                        border = BorderStroke(1.5.dp, if (!showSatelliteOverlay) (if (isDaylight) Color(0xFF047857) else Color(0xFF00F59B)) else Color(0x3394A3B8)),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).clickable { onSelectBasemapProvider(null) }
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Default.LayersClear, contentDescription = null, tint = if (!showSatelliteOverlay) (if (isDaylight) Color(0xFF047857) else Color(0xFF00F59B)) else dialogSubText, modifier = Modifier.size(20.dp))
                                Column {
                                    Text("Map OFF (Precision Field Grid Only)", color = dialogText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("Pure vector guidance canvas (fastest render)", color = dialogSubText, fontSize = 10.sp)
                                }
                            }
                            if (!showSatelliteOverlay) {
                                Text("ACTIVE", color = if (isDaylight) Color(0xFF047857) else Color(0xFF00F59B), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }

                    val basemapOptions = listOf(
                        Pair(MapTileProvider.GOOGLE_SATELLITE, "Google Satellite (High-Res)"),
                        Pair(MapTileProvider.ESRI_WORLD_TOPO, "Esri World Topographic"),
                        Pair(MapTileProvider.USGS_TOPOGRAPHIC, "USGS The National Map (Topo)"),
                        Pair(MapTileProvider.USGS_IMAGERY_TOPO, "USGS Ortho + Contours"),
                        Pair(MapTileProvider.OPEN_TOPO_MAP, "OpenTopo Elevation Contours"),
                        Pair(MapTileProvider.ESRI_WORLD_IMAGERY, "Esri World Imagery (NAIP)")
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (pair in basemapOptions.chunked(2)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (opt in pair) {
                                    val isSelected = showSatelliteOverlay && satelliteMapProvider == opt.first
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) (if (isDaylight) Color(0xFFE0F2FE) else Color(0x3338BDF8)) else (if (isDaylight) Color(0xFFE2E8F0) else Color(0x1F94A3B8)),
                                        border = BorderStroke(1.5.dp, if (isSelected) (if (isDaylight) Color(0xFF0369A1) else Color(0xFF38BDF8)) else Color(0x2294A3B8)),
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).clickable { onSelectBasemapProvider(opt.first) }
                                    ) {
                                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                                Icon(if (opt.first.isTopographic) Icons.Default.Terrain else Icons.Default.Public, contentDescription = null, tint = if (isSelected) (if (isDaylight) Color(0xFF0369A1) else Color(0xFF38BDF8)) else dialogSubText, modifier = Modifier.size(16.dp))
                                                Text(text = opt.second, color = dialogText, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, fontSize = 10.sp, maxLines = 1)
                                            }
                                            if (isSelected) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = if (isDaylight) Color(0xFF0369A1) else Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }
                                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    // GNSS Hardware vs Simulation Mode Selector
                    Surface(color = if (isDaylight) Color(0xFFE2E8F0) else Color(0x1F94A3B8), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, glassBorder), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                Icon(if (gnssSource == GnssSourceMode.SIMULATOR) Icons.Default.SmartToy else Icons.Default.GpsFixed, contentDescription = null, tint = if (gnssSource == GnssSourceMode.SIMULATOR) Color(0xFFD97706) else Color(0xFF059669), modifier = Modifier.size(20.dp))
                                Column {
                                    Text(if (gnssSource == GnssSourceMode.SIMULATOR) "Simulator Mode Active" else "Hardware GNSS Mode (${gnssSource.name})", color = dialogText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("Switch between bench simulation and live tractor GPS", color = dialogSubText, fontSize = 10.sp)
                                }
                            }
                            Button(
                                onClick = onToggleSimulationMode,
                                colors = ButtonDefaults.buttonColors(containerColor = if (gnssSource == GnssSourceMode.SIMULATOR) Color(0xFFF59E0B) else Color(0xFF10B981)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(if (gnssSource == GnssSourceMode.SIMULATOR) "USE GPS" else "USE SIM", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = Color.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DockControlGroup(
    title: String,
    backgroundColor: Color,
    borderColor: Color,
    titleColor: Color,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                color = titleColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}

@Composable
private fun ScopedSetPointBButton(
    isSettingAB: Boolean,
    tempPointA: Vec2?,
    fallbackPivot: Vec2,
    telemetryFlow: StateFlow<LiveTelemetryState>?,
    onSetPointB: () -> Unit
) {
    val activePivot = if (isSettingAB && tempPointA != null && telemetryFlow != null) {
        telemetryFlow.collectAsStateWithLifecycle().value.vehicleState.localPivotPosition
    } else {
        fallbackPivot
    }
    val distFromA = if (isSettingAB && tempPointA != null) tempPointA.distanceTo(activePivot) else 0.0
    val distFt = distFromA * 3.28084

    Button(
        onClick = onSetPointB,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSettingAB && tempPointA != null) {
                if (distFromA >= 1.0) Color(0xFF047857) else Color(0xFFD97706)
            } else Color(0xFF0F766E),
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 56.dp).testTag("set_b_button")
    ) {
        Text(
            text = if (isSettingAB && tempPointA != null) {
                if (distFromA >= 1.0) "SET B (${String.format(Locale.US, "%.0f", distFromA)}m / ${String.format(Locale.US, "%.0f", distFt)}ft)" else "SET B (Lock)"
            } else "SET B",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun NudgePairButton(
    leftLabel: String,
    rightLabel: String,
    containerColor: Color,
    borderColor: Color,
    textColor: Color,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    leftTag: String,
    rightTag: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.5.dp, borderColor)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .widthIn(min = 50.dp)
                    .clickable(onClick = onLeft)
                    .padding(horizontal = 9.dp)
                    .testTag(leftTag)
            ) {
                Text(leftLabel, color = textColor, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(26.dp)
                    .background(borderColor.copy(alpha = 0.55f))
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .widthIn(min = 50.dp)
                    .clickable(onClick = onRight)
                    .padding(horizontal = 9.dp)
                    .testTag(rightTag)
            ) {
                Text(rightLabel, color = textColor, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun ThemeChoiceTile(
    label: String,
    icon: ImageVector,
    accent: Color,
    isSelected: Boolean,
    isDaylight: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) accent.copy(alpha = if (isDaylight) 0.2f else 0.25f) else (if (isDaylight) Color(0xFFE2E8F0) else Color(0x1F94A3B8)),
        border = BorderStroke(1.5.dp, if (isSelected) accent else Color(0x3394A3B8)),
        modifier = modifier.heightIn(min = 56.dp).clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                if (isSelected) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = accent, modifier = Modifier.size(13.dp))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = if (isDaylight) Color(0xFF0F172A) else Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ModernDockButton(
    icon: ImageVector,
    label: String,
    accentColor: Color,
    themeMode: DisplayThemeMode,
    onClick: () -> Unit,
    tag: String
) {
    val isDaylight = themeMode == DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST
    val bgColor = when (themeMode) {
        DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST -> Color(0xFFE2E8F0)
        DisplayThemeMode.CAB_OLED_NIGHT -> Color(0xFF140A00)
        DisplayThemeMode.STANDARD_AG -> Color(0x2694A3B8)
    }
    val borderColor = when (themeMode) {
        DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST -> Color(0xFF94A3B8)
        DisplayThemeMode.CAB_OLED_NIGHT -> Color(0x44FF9100)
        DisplayThemeMode.STANDARD_AG -> Color(0x3394A3B8)
    }
    val labelColor = when (themeMode) {
        DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST -> Color(0xFF0F172A)
        DisplayThemeMode.CAB_OLED_NIGHT -> Color(0xFFFFE0B2)
        DisplayThemeMode.STANDARD_AG -> Color(0xFFF1F5F9)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(if (isDaylight) 1.2.dp else 1.dp, borderColor),
        modifier = Modifier
            .size(width = 58.dp, height = 50.dp)
            .clickable { onClick() }
            .testTag(tag)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = accentColor, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = label, color = labelColor, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

@Composable
private fun ModernToolCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    isDaylight: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = if (isDaylight) Color(0xFFE2E8F0) else Color(0x1F94A3B8),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (isDaylight) Color(0xFF94A3B8) else Color(0x2894A3B8)),
        modifier = modifier.heightIn(min = 56.dp).clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(accentColor.copy(alpha = if (isDaylight) 0.18f else 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(19.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = if (isDaylight) Color(0xFF0F172A) else Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(subtitle, color = if (isDaylight) Color(0xFF475569) else Color(0xFF94A3B8), fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

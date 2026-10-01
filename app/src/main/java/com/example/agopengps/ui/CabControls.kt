package com.example.agopengps.ui

import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.agopengps.CameraViewMode
import com.example.agopengps.GnssSourceMode
import com.example.agopengps.GuidanceMode
import com.example.agopengps.map.MapTileProvider
import com.example.agopengps.navigation.*

/**
 * Modern In-Cab Floating Action Dock & Guidance Controller.
 * Transformed from harsh industrial gray blocks into sleek, aerospace/automotive floating glass interfaces:
 * - Floating Frosted-Glass Vertical Dock (Collapsible, high-polish rounded capsules)
 * - Hero Dual-Ring Auto-Steer Master Button
 * - Floating Guidance Swath Control Island with smooth section indicator pills
 * - Modern Glass Drawer for submenus & diagnostics
 */
@Composable
fun CabControls(
    isAutoSteerEngaged: Boolean,
    isSectionMasterActive: Boolean,
    isAutoSectionControl: Boolean,
    sectionStates: List<Boolean>,
    sectionOverrides: List<SectionOverride>,
    isSettingAB: Boolean,
    guidanceMode: GuidanceMode,
    cameraViewMode: CameraViewMode,
    gnssSource: GnssSourceMode,
    simSpeedKmh: Double,
    isNightMode: Boolean,
    themeMode: DisplayThemeMode = DisplayThemeMode.STANDARD_AG,
    showSatelliteOverlay: Boolean = false,
    satelliteMapProvider: MapTileProvider = MapTileProvider.GOOGLE_SATELLITE,
    isAutoUTurnEnabled: Boolean = false,
    autoUTurnActive: Boolean = false,
    isFieldJobActive: Boolean = false,
    activeJobName: String = "",
    workedAcres: Double = 0.0,
    isRecordingBoundary: Boolean = false,
    onToggleAutoSteer: () -> Unit,
    onToggleAutoUTurn: () -> Unit = {},
    onTriggerManualUTurn: () -> Unit = {},
    onToggleCameraMode: () -> Unit,
    onSetPointA: () -> Unit,
    onSetPointB: () -> Unit,
    onSnapABLine: () -> Unit,
    onReverseDirection: () -> Unit,
    onCycleSwath: (Int) -> Unit,
    onSkipPass: (Int) -> Unit = {},
    onNudgeSwath: (Double) -> Unit,
    onNudgeHalfRow22: (Boolean) -> Unit = {},
    onNudgeFullRow22: (Boolean) -> Unit = {},
    onZeroWas: () -> Unit = {},
    onToggleSectionOverride: (Int) -> Unit,
    onToggleMasterSection: () -> Unit = {},
    onToggleAutoSection: () -> Unit = {},
    onToggleNightMode: () -> Unit = {},
    onCycleThemeMode: () -> Unit = {},
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
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFieldDialog: () -> Unit,
    onOpenAgIoDialog: () -> Unit,
    onOpenVirtualDrive: () -> Unit = {},
    onOpenWasDialog: () -> Unit = {},
    onOpenUm982Dialog: () -> Unit = {},
    onToggleDashboard: () -> Unit = {},
    onOpenNmeaDiagnostics: () -> Unit = {},
    slidingHitchConfig: SlidingHitchConfig = SlidingHitchConfig(),
    slidingHitchState: SlidingHitchState = SlidingHitchState(),
    onOpenSlidingHitch: () -> Unit = {},
    onToggleSimulationMode: () -> Unit,
    onSetSimSpeed: (Double) -> Unit,
    onToggleSimReverse: () -> Unit,
    onRecenterGps: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Sleek frosted glass tokens
    val panelBg = when (themeMode) {
        DisplayThemeMode.SUNLIGHT_HIGH_CONTRAST -> Color(0xF2F1F5F9)
        DisplayThemeMode.CAB_OLED_NIGHT -> Color(0xE6070B14)
        DisplayThemeMode.STANDARD_AG -> if (isNightMode) Color(0xE60B1220) else Color(0xEB0F172A)
    }
    val glassBorder = Color(0x3394A3B8)
    var isDockExpanded by remember { mutableStateOf(true) }
    var showToolsSheet by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {

        // --- TOP PERSISTENT ACTIVE FIELD SESSION PILL (Modern Floating Glass) ---
        if (isFieldJobActive) {
            Surface(
                color = panelBg,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0x4400F59B)),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF00F59B), CircleShape)
                    )
                    Text(
                        text = "FIELD: ${activeJobName.uppercase()}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp,
                        maxLines = 1
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0x2200F59B)
                    ) {
                        Text(
                            text = "${String.format("%.2f", workedAcres)} ac",
                            color = Color(0xFF00F59B),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // 1-Tap Save & Finish Field Job Button
                    Button(
                        onClick = onSaveAndFinishField,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F59B)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF003822), modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("FINISH", color = Color(0xFF003822), fontWeight = FontWeight.Black, fontSize = 10.sp)
                    }
                }
            }
        }

        // --- RIGHT-SIDE VERTICAL CONTROL DOCK (Floating Aero Capsule) ---
        AnimatedVisibility(
            visible = isDockExpanded,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 10.dp)
            ) {
                // Dock Collapse Micro-Handle
                Surface(
                    color = panelBg,
                    shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
                    border = BorderStroke(1.dp, glassBorder),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .clickable { isDockExpanded = false }
                        .testTag("dock_collapse_handle")
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Hide Menu Dock",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier
                            .padding(vertical = 14.dp, horizontal = 3.dp)
                            .size(16.dp)
                    )
                }

                // Main Floating Dock Body
                Surface(
                    color = panelBg,
                    shape = RoundedCornerShape(26.dp),
                    border = BorderStroke(1.dp, glassBorder),
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { _, dragAmount ->
                                if (dragAmount > 20f) isDockExpanded = false
                            }
                        }
                        .testTag("agopengps_right_dock")
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. HERO AUTO-STEER ENGAGEMENT BUTTON (Jeweled Dual-Ring Centerpiece)
                        val steerGlowColor by animateColorAsState(
                            targetValue = if (isAutoSteerEngaged) Color(0xFF00F59B) else Color(0xFFF43F5E),
                            label = "steer_glow"
                        )
                        val steerBrush = if (isAutoSteerEngaged) {
                            Brush.radialGradient(
                                listOf(Color(0xFF00F59B), Color(0xFF059669))
                            )
                        } else {
                            Brush.radialGradient(
                                listOf(Color(0xFFF43F5E), Color(0xFF9F1239))
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(steerBrush)
                                .border(2.dp, steerGlowColor.copy(alpha = 0.6f), CircleShape)
                                .clickable { onToggleAutoSteer() }
                                .shadow(if (isAutoSteerEngaged) 12.dp else 4.dp, CircleShape)
                                .testTag("auto_steer_engage_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = if (isAutoSteerEngaged) Icons.Default.Navigation else Icons.Default.NearMeDisabled,
                                    contentDescription = "AutoSteer & Implement",
                                    tint = if (isAutoSteerEngaged) Color(0xFF003822) else Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = if (isAutoSteerEngaged) "AUTO" else "MANUAL",
                                    color = if (isAutoSteerEngaged) Color(0xFF003822) else Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 8.sp,
                                    letterSpacing = 0.4.sp
                                )
                            }
                        }

                        // 2. FIELD & BOUNDARIES
                        ModernDockButton(
                            icon = Icons.Default.FolderOpen,
                            label = "Field",
                            accentColor = Color(0xFF10B981),
                            onClick = onOpenFieldDialog,
                            tag = "field_dialog_button"
                        )

                        // 3. WIZARD (1-Tap Setup)
                        ModernDockButton(
                            icon = Icons.Default.AutoFixHigh,
                            label = "Wizard",
                            accentColor = Color(0xFF2DD4BF),
                            onClick = onOpenFieldWizard,
                            tag = "field_wizard_dock_button"
                        )

                        // 4. CAMERA VIEW (3D / 2D / North)
                        ModernDockButton(
                            icon = when (cameraViewMode) {
                                CameraViewMode.CAB_3D -> Icons.Default.ViewInAr
                                CameraViewMode.BIRD_EYE_2D -> Icons.Default.Explore
                                CameraViewMode.NORTH_UP -> Icons.Default.Navigation
                                CameraViewMode.FREE_PAN -> Icons.Default.PanTool
                            },
                            label = when (cameraViewMode) {
                                CameraViewMode.CAB_3D -> "3D Cab"
                                CameraViewMode.BIRD_EYE_2D -> "2D Top"
                                CameraViewMode.NORTH_UP -> "North"
                                CameraViewMode.FREE_PAN -> "Pan"
                            },
                            accentColor = Color(0xFF38BDF8),
                            onClick = onToggleCameraMode,
                            tag = "camera_mode_button"
                        )

                        // 5. RECENTER GPS
                        ModernDockButton(
                            icon = Icons.Default.MyLocation,
                            label = "Center",
                            accentColor = Color(0xFFFBBF24),
                            onClick = onRecenterGps,
                            tag = "center_gps_button"
                        )

                        // 6. TOOLS & APPS DRAWER
                        ModernDockButton(
                            icon = Icons.Default.Widgets,
                            label = "Tools",
                            accentColor = Color(0xFF818CF8),
                            onClick = { showToolsSheet = true },
                            tag = "dock_tools_drawer_button"
                        )

                        // 7. SETUP & CONFIGURATION
                        ModernDockButton(
                            icon = Icons.Default.Settings,
                            label = "Setup",
                            accentColor = Color(0xFF94A3B8),
                            onClick = onOpenSettings,
                            tag = "settings_button"
                        )
                    }
                }
            }
        }

        // Floating Pull-Out Tab when Dock is collapsed
        AnimatedVisibility(
            visible = !isDockExpanded,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Surface(
                color = panelBg,
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                border = BorderStroke(1.dp, glassBorder),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .clickable { isDockExpanded = true }
                    .testTag("dock_expand_tab")
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Show Tools Menu",
                        tint = Color(0xFF00F59B),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "MENU",
                        color = Color(0xFFF1F5F9),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 8.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // --- BOTTOM FLOATING GUIDANCE & SWATH STRIP (Clean Modern Glass Capsule) ---
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    start = 14.dp,
                    end = if (isDockExpanded) 82.dp else 36.dp,
                    bottom = 12.dp
                )
        ) {
            Surface(
                color = panelBg,
                border = BorderStroke(1.dp, glassBorder),
                shape = RoundedCornerShape(22.dp),
                shadowElevation = 10.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    // Modern Section / Row Unit Status Strip (Smooth rounded capsules)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val secCount = minOf(16, sectionStates.size)
                        for (i in 0 until secCount) {
                            val isActive = sectionStates[i]
                            val override = sectionOverrides.getOrElse(i) { SectionOverride.AUTO }
                            val dotColor = when (override) {
                                SectionOverride.FORCED_ON -> Color(0xFF38BDF8)
                                SectionOverride.FORCED_OFF -> Color(0xFFF43F5E)
                                SectionOverride.AUTO -> if (isActive) Color(0xFF00F59B) else Color(0x33475569)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(dotColor)
                                    .clickable { onToggleSectionOverride(i) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Guidance Swath Navigation Action Strip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: AB Line Actions
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Mode Badge (Capsule)
                            Surface(
                                color = if (guidanceMode == GuidanceMode.CONTOUR_CURVE) Color(0x33A855F7) else Color(0x3338BDF8),
                                border = BorderStroke(1.dp, if (guidanceMode == GuidanceMode.CONTOUR_CURVE) Color(0x66A855F7) else Color(0x6638BDF8)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = if (guidanceMode == GuidanceMode.CONTOUR_CURVE) "CURVE" else "A-B SWATH",
                                    color = if (guidanceMode == GuidanceMode.CONTOUR_CURVE) Color(0xFFD8B4FE) else Color(0xFFBAE6FD),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            // SET A Button
                            Button(
                                onClick = onSetPointA,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(36.dp).testTag("set_point_a_button")
                            ) {
                                Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("SET A", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }

                            // SET B Button
                            Button(
                                onClick = onSetPointB,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSettingAB) Color(0xFFF59E0B) else Color(0xFF0284C7)
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(36.dp).testTag("set_point_b_button")
                            ) {
                                Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("SET B", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }

                            // SNAP Button
                            FilledTonalButton(
                                onClick = onSnapABLine,
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0x2294A3B8)),
                                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Default.FilterCenterFocus, contentDescription = null, tint = Color(0xFFE2E8F0), modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("SNAP", color = Color(0xFFE2E8F0), fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                            }

                            // 180° Direction Reverse
                            FilledTonalButton(
                                onClick = onReverseDirection,
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0x2294A3B8)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("180°", color = Color(0xFFE2E8F0), fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                            }
                        }

                        // Right: Pass Stepping & Centimeter Nudge Controls
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Unified Pass Stepper Control
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0x1F94A3B8),
                                border = BorderStroke(1.dp, Color(0x3394A3B8))
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(
                                        onClick = { onCycleSwath(-1) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text("‹ PASS", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color(0x3394A3B8)))
                                    TextButton(
                                        onClick = { onCycleSwath(1) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text("PASS ›", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Fine 1-inch Lateral Nudge Controls
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0x1F94A3B8),
                                border = BorderStroke(1.dp, Color(0x3394A3B8))
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(
                                        onClick = { onNudgeSwath(-2.54) },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text("‹ 1\"", color = Color(0xFFFBBF24), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color(0x3394A3B8)))
                                    TextButton(
                                        onClick = { onNudgeSwath(2.54) },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text("1\" ›", color = Color(0xFFFBBF24), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // 22-Inch Organic Row-Crop Nudge Controls (11" Half-Row / 22" Full-Row)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0x1F0284C7),
                                border = BorderStroke(1.dp, Color(0x4438BDF8))
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(
                                        onClick = { onNudgeHalfRow22(false) },
                                        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 2.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text("‹ 11\"", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color(0x4438BDF8)))
                                    TextButton(
                                        onClick = { onNudgeHalfRow22(true) },
                                        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 2.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text("11\" ›", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color(0x4438BDF8)))
                                    TextButton(
                                        onClick = { onNudgeFullRow22(false) },
                                        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 2.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text("‹ 22\"", color = Color(0xFF34D399), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color(0x4438BDF8)))
                                    TextButton(
                                        onClick = { onNudgeFullRow22(true) },
                                        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 2.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text("22\" ›", color = Color(0xFF34D399), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // 1-Tap Zero WAS Action
                            FilledTonalButton(
                                onClick = onZeroWas,
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0x33D97706)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(36.dp).testTag("cab_zero_was_button")
                            ) {
                                Icon(Icons.Default.CenterFocusStrong, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("ZERO WAS", color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // --- SECONDARY TOOLS & DIAGNOSTICS MODAL SHEET (Clean Glass Cards) ---
    if (showToolsSheet) {
        Dialog(onDismissRequest = { showToolsSheet = false }) {
            Surface(
                color = Color(0xFA0F172A),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color(0x3394A3B8)),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0x2200F59B)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Widgets, contentDescription = null, tint = Color(0xFF00F59B), modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text("AgSteer Precision Tools", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                Text("Diagnostics, Telemetry & Setup", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            }
                        }
                        IconButton(onClick = { showToolsSheet = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                        }
                    }

                    // 1. Navigation & Sensors
                    Text("NAVIGATION & SENSORS", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.8.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModernToolCard(
                            icon = Icons.Default.Troubleshoot,
                            title = "NMEA Diagnostics",
                            subtitle = "GNSS sentences & fix",
                            accentColor = Color(0xFF0284C7),
                            modifier = Modifier.weight(1f),
                            onClick = { showToolsSheet = false; onOpenNmeaDiagnostics() }
                        )
                        ModernToolCard(
                            icon = Icons.Default.CellTower,
                            title = "RTK Base Station",
                            subtitle = "Survey & baseline",
                            accentColor = Color(0xFF0D9488),
                            modifier = Modifier.weight(1f),
                            onClick = { showToolsSheet = false; onOpenRtkBaseDialog() }
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModernToolCard(
                            icon = Icons.Default.Router,
                            title = "AgIO Network Hub",
                            subtitle = "UDP / USB telemetry",
                            accentColor = Color(0xFF6366F1),
                            modifier = Modifier.weight(1f),
                            onClick = { showToolsSheet = false; onOpenAgIoDialog() }
                        )
                        ModernToolCard(
                            icon = Icons.Default.Tune,
                            title = "WAS Calibration",
                            subtitle = "Steer sensor zero & span",
                            accentColor = Color(0xFF10B981),
                            modifier = Modifier.weight(1f),
                            onClick = { showToolsSheet = false; onOpenWasDialog() }
                        )
                    }

                    // 2. Hardware & Implements
                    Text("HARDWARE & IMPLEMENTS", color = Color(0xFF34D399), fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.8.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModernToolCard(
                            icon = Icons.Default.Build,
                            title = "Implement Presets",
                            subtitle = "22\" Beet, 30\" Corn, Custom",
                            accentColor = Color(0xFF14B8A6),
                            modifier = Modifier.weight(1f),
                            onClick = { showToolsSheet = false; onOpenImplementPresetsDialog() }
                        )
                        ModernToolCard(
                            icon = Icons.AutoMirrored.Filled.CompareArrows,
                            title = "Sliding Hitch",
                            subtitle = "Implement steering",
                            accentColor = Color(0xFF06B6D4),
                            modifier = Modifier.weight(1f),
                            onClick = { showToolsSheet = false; onOpenSlidingHitch() }
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModernToolCard(
                            icon = Icons.Default.GpsFixed,
                            title = "UM982 Dual Antenna",
                            subtitle = "True Heading & Pitch",
                            accentColor = Color(0xFF3B82F6),
                            modifier = Modifier.weight(1f),
                            onClick = { showToolsSheet = false; onOpenUm982Dialog() }
                        )
                        ModernToolCard(
                            icon = Icons.Default.Warning,
                            title = "Hazard Pins",
                            subtitle = "Rocks & obstacles",
                            accentColor = Color(0xFFF59E0B),
                            modifier = Modifier.weight(1f),
                            onClick = { showToolsSheet = false; onOpenObstacleDialog() }
                        )
                    }

                    // 3. Mapping & Simulation
                    Text("MAPPING & SIMULATION", color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.8.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModernToolCard(
                            icon = Icons.Default.Share,
                            title = "Export Field",
                            subtitle = "KML, CSV, GeoJSON",
                            accentColor = Color(0xFF0EA5E9),
                            modifier = Modifier.weight(1f),
                            onClick = { showToolsSheet = false; onOpenExportDialog() }
                        )
                        ModernToolCard(
                            icon = Icons.Default.SportsMotorsports,
                            title = "Virtual Drive",
                            subtitle = "Steering & speed test",
                            accentColor = Color(0xFF10B981),
                            modifier = Modifier.weight(1f),
                            onClick = { showToolsSheet = false; onOpenVirtualDrive() }
                        )
                    }

                    // Simulator Speed Control (if in Simulator Mode)
                    if (gnssSource == GnssSourceMode.SIMULATOR) {
                        Surface(
                            color = Color(0x22F59E0B),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0x44F59E0B)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = onToggleSimReverse,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("REV / FWD", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                val simSpeedMph = simSpeedKmh * 0.621371
                                Text(
                                    text = "SIM: ${String.format("%.1f", simSpeedMph)} mph",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Slider(
                                    value = simSpeedKmh.toFloat(),
                                    onValueChange = { onSetSimSpeed(it.toDouble()) },
                                    valueRange = 0f..25f,
                                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                                )
                            }
                        }
                    }

                    // Basemap & Simulation Toggles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = { onToggleSimulationMode() },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (gnssSource == GnssSourceMode.SIMULATOR) Color(0x33F59E0B) else Color(0x3310B981)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (gnssSource == GnssSourceMode.SIMULATOR) "Simulator Mode" else "Hardware GPS")
                        }

                        FilledTonalButton(
                            onClick = { onToggleSatelliteOverlay() },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (showSatelliteOverlay) Color(0x3338BDF8) else Color(0x2294A3B8)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Terrain, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (showSatelliteOverlay) "Topo Map ON" else "Topo Map OFF")
                        }

                        if (showSatelliteOverlay) {
                            FilledTonalButton(
                                onClick = { onCycleSatelliteProvider() },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0x2200E5FF)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(satelliteMapProvider.label.take(18))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Modern Dock Button with smooth squircle styling and soft hover feedback.
 */
@Composable
private fun ModernDockButton(
    icon: ImageVector,
    label: String,
    accentColor: Color,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        color = Color(0x1F94A3B8),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0x2294A3B8)),
        modifier = Modifier
            .size(width = 54.dp, height = 48.dp)
            .clickable { onClick() }
            .testTag(tag)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = Color(0xFFF1F5F9),
                fontWeight = FontWeight.SemiBold,
                fontSize = 8.sp,
                maxLines = 1
            )
        }
    }
}

/**
 * Modern Tool Card with clean rounded corners and vibrant accent tinting.
 */
@Composable
private fun ModernToolCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = Color(0x1F1E293B),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(subtitle, color = Color(0xFF94A3B8), fontSize = 9.sp, maxLines = 1)
            }
        }
    }
}

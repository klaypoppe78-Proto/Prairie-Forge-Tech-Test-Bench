package com.example.agopengps.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.agopengps.io.*

/**
 * In-Cab SCV Hydraulics & 3-Point Hitch Trigger Mapping Setup Wizard.
 * Allows farmers to configure exactly what buttons, soft keys, or automated events
 * trigger 3-point lift, SCV 1-4 remotes, cultivator pass toggles, and guidance actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScvHitchMappingWizardDialog(
    threePointConfig: ThreePointHitchConfig,
    scvValves: List<ScvValve>,
    triggerBindings: List<TriggerBinding>,
    onUpdateThreePointConfig: (ThreePointHitchConfig) -> Unit,
    onSetScvState: (Int, ScvValveState) -> Unit,
    onUpdateScvValve: (ScvValve) -> Unit,
    onSaveTriggerBinding: (TriggerBinding) -> Unit,
    onDeleteTriggerBinding: (Long) -> Unit,
    onResetDefaultBindings: () -> Unit,
    onExecuteAction: (CabAction) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = Keybindings Matrix, 1 = SCV & 3-Point Setup
    var showAddBindingDialog by remember { mutableStateOf(false) }

    var selectedTriggerForAdd by remember { mutableStateOf(CabTrigger.SOFTKEY_1) }
    var selectedActionForAdd by remember { mutableStateOf(CabAction.LOWER_3_POINT) }
    var delayMsInput by remember { mutableStateOf("0") }
    var labelInput by remember { mutableStateOf("") }

    var lastTriggeredTestAction by remember { mutableStateOf<CabAction?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .testTag("scv_hitch_mapping_wizard_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0A0F1D),
            border = BorderStroke(1.dp, Color(0x4438BDF8)),
            shadowElevation = 24.dp
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {

                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0x2238BDF8),
                            border = BorderStroke(1.dp, Color(0x4438BDF8)),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                "SCV & 3-Point Control Setup Wizard",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                            Text(
                                "Pick and choose what soft keys and automated triggers activate hydraulics",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Navigation Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0x1F1E293B),
                    contentColor = Color(0xFF38BDF8),
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("TRIGGER & KEYBINDING MATRIX", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.Gamepad, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("SCV & 3-POINT HYDRAULICS SETUP", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.SettingsApplications, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (selectedTab == 0) {
                    // TAB 0: TRIGGER & KEYBINDING MATRIX
                    // Live In-Cab Soft-Key Test Pod
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0x1F1E293B),
                        border = BorderStroke(1.dp, Color(0x3338BDF8)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "IN-CAB QUICK SOFT-KEYS (TAP TO TEST)",
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    letterSpacing = 0.5.sp
                                )
                                if (lastTriggeredTestAction != null) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0x3300F59B),
                                        border = BorderStroke(1.dp, Color(0xFF00F59B))
                                    ) {
                                        Text(
                                            "Fired: ${lastTriggeredTestAction?.title}",
                                            color = Color(0xFF00F59B),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    CabTrigger.SOFTKEY_1 to "SK 1",
                                    CabTrigger.SOFTKEY_2 to "SK 2",
                                    CabTrigger.SOFTKEY_3 to "SK 3",
                                    CabTrigger.SOFTKEY_4 to "SK 4",
                                    CabTrigger.SOFTKEY_5 to "SK 5",
                                    CabTrigger.SOFTKEY_6 to "SK 6"
                                ).forEach { (trigger, label) ->
                                    val bound = triggerBindings.firstOrNull { it.trigger == trigger && it.isEnabled }
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (bound != null) Color(0x330284C7) else Color(0x1F64748B),
                                        border = BorderStroke(1.dp, if (bound != null) Color(0xFF38BDF8) else Color(0x3394A3B8)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                bound?.let {
                                                    lastTriggeredTestAction = it.action
                                                    onExecuteAction(it.action)
                                                }
                                            }
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(label, color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = bound?.action?.title?.take(12) ?: "Unassigned",
                                                color = if (bound != null) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                                fontSize = 9.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Keybindings Header & Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "ACTIVE HYDRAULIC & CONTROL BINDINGS (${triggerBindings.size})",
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 0.8.sp
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onResetDefaultBindings,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8)),
                                border = BorderStroke(1.dp, Color(0x3394A3B8)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset Defaults", fontSize = 11.sp)
                            }

                            Button(
                                onClick = { showAddBindingDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Trigger / Keybinding", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Scrollable List of Bindings
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(triggerBindings, key = { it.id }) { binding ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0x1F1E293B),
                                border = BorderStroke(1.dp, Color(0x3394A3B8)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Trigger Pill Badge
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0x3338BDF8),
                                            border = BorderStroke(1.dp, Color(0x4438BDF8))
                                        ) {
                                            Text(
                                                text = binding.trigger.title,
                                                color = Color(0xFF38BDF8),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }

                                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))

                                        // Action Description
                                        Column {
                                            Text(
                                                binding.label.ifBlank { binding.action.title },
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                "Action: ${binding.action.title} ${if (binding.delayMs > 0) "• Delay: ${binding.delayMs}ms" else ""}",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        // Test Fire Button
                                        OutlinedButton(
                                            onClick = {
                                                lastTriggeredTestAction = binding.action
                                                onExecuteAction(binding.action)
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00F59B)),
                                            border = BorderStroke(1.dp, Color(0x4400F59B)),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(30.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text("Test", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }

                                        // Delete Button
                                        IconButton(
                                            onClick = { onDeleteTriggerBinding(binding.id) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color(0xFFF43F5E), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // TAB 1: SCV & 3-POINT HYDRAULICS SETUP
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. 3-Point Hitch Configuration Card
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0x1F1E293B),
                                border = BorderStroke(1.dp, Color(0x3334D399)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("3-Point Hitch / Toolbar Control", color = Color(0xFF34D399), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text("Work depth height and headland auto-lift thresholds", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (threePointConfig.state == ThreePointState.LOWERED) Color(0x3300F59B) else Color(0x33F59E0B),
                                            border = BorderStroke(1.dp, if (threePointConfig.state == ThreePointState.LOWERED) Color(0xFF00F59B) else Color(0xFFF59E0B))
                                        ) {
                                            Text(
                                                threePointConfig.state.label,
                                                color = if (threePointConfig.state == ThreePointState.LOWERED) Color(0xFF00F59B) else Color(0xFFF59E0B),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Quick Raise / Lower Action Buttons
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                onUpdateThreePointConfig(
                                                    threePointConfig.copy(
                                                        state = ThreePointState.LOWERED,
                                                        currentHeightPercent = threePointConfig.targetWorkDepthPercent
                                                    )
                                                )
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Lower to Work Depth (${threePointConfig.targetWorkDepthPercent}%)")
                                        }

                                        Button(
                                            onClick = {
                                                onUpdateThreePointConfig(
                                                    threePointConfig.copy(
                                                        state = ThreePointState.RAISED,
                                                        currentHeightPercent = threePointConfig.targetHeadlandRaisePercent
                                                    )
                                                )
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Raise to Headland (${threePointConfig.targetHeadlandRaisePercent}%)")
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Sliders for Work Depth and Raise Height
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Work Depth Height: ${threePointConfig.targetWorkDepthPercent}%", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Slider(
                                                value = threePointConfig.targetWorkDepthPercent.toFloat(),
                                                onValueChange = { onUpdateThreePointConfig(threePointConfig.copy(targetWorkDepthPercent = it.toInt())) },
                                                valueRange = 0f..50f
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Headland Lift Height: ${threePointConfig.targetHeadlandRaisePercent}%", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Slider(
                                                value = threePointConfig.targetHeadlandRaisePercent.toFloat(),
                                                onValueChange = { onUpdateThreePointConfig(threePointConfig.copy(targetHeadlandRaisePercent = it.toInt())) },
                                                valueRange = 50f..100f
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 2. SCV 1 to SCV 4 Valves Cards
                        items(scvValves, key = { it.id }) { valve ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0x1F1E293B),
                                border = BorderStroke(1.dp, Color(0x3338BDF8)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(valve.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("Flow: ${valve.flowRatePercent}% • Detent: ${valve.detentTimeSeconds}s", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                        }

                                        // State Toggle Buttons
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            ScvStateButton("Extend", valve.state == ScvValveState.EXTEND, Color(0xFF0284C7)) {
                                                onSetScvState(valve.id, ScvValveState.EXTEND)
                                            }
                                            ScvStateButton("Neutral", valve.state == ScvValveState.NEUTRAL, Color(0xFF64748B)) {
                                                onSetScvState(valve.id, ScvValveState.NEUTRAL)
                                            }
                                            ScvStateButton("Retract", valve.state == ScvValveState.RETRACT, Color(0xFF059669)) {
                                                onSetScvState(valve.id, ScvValveState.RETRACT)
                                            }
                                            ScvStateButton("Float", valve.state == ScvValveState.FLOAT, Color(0xFFF59E0B)) {
                                                onSetScvState(valve.id, ScvValveState.FLOAT)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Flow % and Detent Timing Sliders
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Flow Rate: ${valve.flowRatePercent}%", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                            Slider(
                                                value = valve.flowRatePercent.toFloat(),
                                                onValueChange = { onUpdateScvValve(valve.copy(flowRatePercent = it.toInt())) },
                                                valueRange = 10f..100f
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Detent Time: ${String.format("%.1f", valve.detentTimeSeconds)}s", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                            Slider(
                                                value = valve.detentTimeSeconds.toFloat(),
                                                onValueChange = { onUpdateScvValve(valve.copy(detentTimeSeconds = it.toDouble())) },
                                                valueRange = 0.5f..10.0f
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Trigger / Keybinding Sub-Dialog
    if (showAddBindingDialog) {
        AlertDialog(
            onDismissRequest = { showAddBindingDialog = false },
            title = { Text("Map Trigger to Hydraulic / Ag Action", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("1. When this Trigger occurs:", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 12.sp)

                    var expandedTriggerMenu by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expandedTriggerMenu,
                        onExpandedChange = { expandedTriggerMenu = !expandedTriggerMenu }
                    ) {
                        OutlinedTextField(
                            value = selectedTriggerForAdd.title,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTriggerMenu) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedTriggerMenu,
                            onDismissRequest = { expandedTriggerMenu = false }
                        ) {
                            CabTrigger.values().forEach { trig ->
                                DropdownMenuItem(
                                    text = { Text("${trig.title} (${trig.category})") },
                                    onClick = {
                                        selectedTriggerForAdd = trig
                                        expandedTriggerMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Text("2. Execute this Action:", color = Color(0xFF34D399), fontWeight = FontWeight.Bold, fontSize = 12.sp)

                    var expandedActionMenu by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expandedActionMenu,
                        onExpandedChange = { expandedActionMenu = !expandedActionMenu }
                    ) {
                        OutlinedTextField(
                            value = selectedActionForAdd.title,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedActionMenu) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedActionMenu,
                            onDismissRequest = { expandedActionMenu = false }
                        ) {
                            CabAction.values().forEach { act ->
                                DropdownMenuItem(
                                    text = { Text("${act.title} (${act.category})") },
                                    onClick = {
                                        selectedActionForAdd = act
                                        expandedActionMenu = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = labelInput,
                        onValueChange = { labelInput = it },
                        label = { Text("Custom Description (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = delayMsInput,
                        onValueChange = { delayMsInput = it },
                        label = { Text("Trigger Delay (ms, e.g. 300)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val delay = delayMsInput.toLongOrNull() ?: 0L
                        onSaveTriggerBinding(
                            TriggerBinding(
                                id = 0L,
                                label = labelInput.ifBlank { "${selectedTriggerForAdd.title} → ${selectedActionForAdd.title}" },
                                trigger = selectedTriggerForAdd,
                                action = selectedActionForAdd,
                                delayMs = delay,
                                isEnabled = true,
                                isCustom = true
                            )
                        )
                        showAddBindingDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                ) {
                    Text("Save Binding")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddBindingDialog = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }
}

@Composable
private fun ScvStateButton(label: String, isActive: Boolean, activeColor: Color, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isActive) activeColor else Color(0x1F64748B),
        border = BorderStroke(1.dp, if (isActive) activeColor else Color(0x3394A3B8)),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            color = if (isActive) Color.White else Color(0xFF94A3B8),
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

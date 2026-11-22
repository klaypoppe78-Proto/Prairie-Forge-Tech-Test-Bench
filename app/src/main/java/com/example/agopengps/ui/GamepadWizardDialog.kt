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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.agopengps.io.AgAction
import com.example.agopengps.io.GamepadBinding
import com.example.agopengps.io.GamepadButton

/**
 * PS4 & Joystick Gamepad Setup Wizard Dialog.
 * Enables in-cab operators to program and customize physical joystick / PS4 controller buttons
 * for precision autosteer, row nudges, cultivator splitting, and implement actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamepadWizardDialog(
    bindings: List<GamepadBinding>,
    lastPressedButton: GamepadButton?,
    onSaveBinding: (GamepadBinding) -> Unit,
    onDeleteBinding: (Long) -> Unit,
    onResetDefaultBindings: () -> Unit,
    onDismiss: () -> Unit
) {
    var showAddCustomDialog by remember { mutableStateOf(false) }
    var selectedButtonForAdd by remember { mutableStateOf(GamepadButton.CROSS) }
    var selectedActionForAdd by remember { mutableStateOf(AgAction.TOGGLE_AUTO_STEER) }
    var customLabelInput by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .testTag("gamepad_wizard_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0A0F1D),
            border = BorderStroke(1.dp, Color(0x446366F1)),
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
                            color = Color(0x226366F1),
                            border = BorderStroke(1.dp, Color(0x446366F1)),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.SportsEsports,
                                    contentDescription = null,
                                    tint = Color(0xFF818CF8),
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                "PS4 & Gamepad Control Setup Wizard",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                            Text(
                                "Map physical joystick & gamepad buttons to in-cab agricultural functions",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // PS4 Visual Controller Layout & Live Test Pod
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0x1F1E293B),
                    border = BorderStroke(1.dp, Color(0x336366F1)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "PHYSICAL CONTROLLER TEST & SCHEMATIC",
                                color = Color(0xFF818CF8),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.8.sp
                            )
                            if (lastPressedButton != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0x3300F59B),
                                    border = BorderStroke(1.dp, Color(0xFF00F59B))
                                ) {
                                    Text(
                                        "Signal: ${lastPressedButton.ps4Label}",
                                        color = Color(0xFF00F59B),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Interactive PS4 Button Badges Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left Shoulder / DPad Cluster
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                ControllerButtonChip(GamepadButton.L1, bindings, lastPressedButton)
                                ControllerButtonChip(GamepadButton.L2, bindings, lastPressedButton)
                                ControllerButtonChip(GamepadButton.DPAD_LEFT, bindings, lastPressedButton)
                                ControllerButtonChip(GamepadButton.DPAD_RIGHT, bindings, lastPressedButton)
                            }

                            // Center Stick / Options
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                ControllerButtonChip(GamepadButton.OPTIONS_START, bindings, lastPressedButton)
                                ControllerButtonChip(GamepadButton.TOUCHPAD, bindings, lastPressedButton)
                                ControllerButtonChip(GamepadButton.L3_THUMB, bindings, lastPressedButton)
                                ControllerButtonChip(GamepadButton.R3_THUMB, bindings, lastPressedButton)
                            }

                            // Right Action Buttons Cluster
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                ControllerButtonChip(GamepadButton.TRIANGLE, bindings, lastPressedButton)
                                ControllerButtonChip(GamepadButton.CROSS, bindings, lastPressedButton)
                                ControllerButtonChip(GamepadButton.SQUARE, bindings, lastPressedButton)
                                ControllerButtonChip(GamepadButton.CIRCLE, bindings, lastPressedButton)
                            }

                            // Right Shoulder Cluster
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                ControllerButtonChip(GamepadButton.R1, bindings, lastPressedButton)
                                ControllerButtonChip(GamepadButton.R2, bindings, lastPressedButton)
                                ControllerButtonChip(GamepadButton.DPAD_UP, bindings, lastPressedButton)
                                ControllerButtonChip(GamepadButton.DPAD_DOWN, bindings, lastPressedButton)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Controls Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "CONFIGURED BUTTON MAPPINGS (${bindings.size})",
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
                            Text("Reset PS4 Defaults", fontSize = 11.sp)
                        }

                        Button(
                            onClick = { showAddCustomDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add / Change Control", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable List of Mappings
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(bindings, key = { "${it.button.code}_${it.id}" }) { binding ->
                        val isHighlighted = lastPressedButton == binding.button
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isHighlighted) Color(0x3300F59B) else Color(0x1F1E293B),
                            border = BorderStroke(1.dp, if (isHighlighted) Color(0xFF00F59B) else Color(0x3394A3B8)),
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
                                    // Button Badge
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = when (binding.button) {
                                            GamepadButton.CROSS -> Color(0x3338BDF8)
                                            GamepadButton.CIRCLE -> Color(0x33EF4444)
                                            GamepadButton.SQUARE -> Color(0x33EC4899)
                                            GamepadButton.TRIANGLE -> Color(0x3310B981)
                                            else -> Color(0x336366F1)
                                        },
                                        border = BorderStroke(1.dp, Color(0x44FFFFFF))
                                    ) {
                                        Text(
                                            text = binding.button.ps4Label,
                                            color = Color.White,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }

                                    Column {
                                        Text(
                                            binding.displayLabel,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            "Category: ${binding.action.category}",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = { onDeleteBinding(binding.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove Binding", tint = Color(0xFFF43F5E), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Custom Binding Sub-Dialog
    if (showAddCustomDialog) {
        AlertDialog(
            onDismissRequest = { showAddCustomDialog = false },
            title = { Text("Assign Gamepad Button", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select Gamepad Hardware Button:", color = Color(0xFF94A3B8), fontSize = 12.sp)

                    var expandedButtonMenu by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expandedButtonMenu,
                        onExpandedChange = { expandedButtonMenu = !expandedButtonMenu }
                    ) {
                        OutlinedTextField(
                            value = selectedButtonForAdd.ps4Label,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedButtonMenu) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedButtonMenu,
                            onDismissRequest = { expandedButtonMenu = false }
                        ) {
                            GamepadButton.values().forEach { btn ->
                                DropdownMenuItem(
                                    text = { Text(btn.ps4Label) },
                                    onClick = {
                                        selectedButtonForAdd = btn
                                        expandedButtonMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Text("Select Agricultural Action:", color = Color(0xFF94A3B8), fontSize = 12.sp)

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
                            AgAction.values().forEach { act ->
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
                        value = customLabelInput,
                        onValueChange = { customLabelInput = it },
                        label = { Text("Custom Description (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveBinding(
                            GamepadBinding(
                                id = 0L,
                                button = selectedButtonForAdd,
                                action = selectedActionForAdd,
                                customLabel = customLabelInput,
                                isCustom = true
                            )
                        )
                        showAddCustomDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                ) {
                    Text("Save Binding")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCustomDialog = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }
}

@Composable
private fun ControllerButtonChip(
    button: GamepadButton,
    bindings: List<GamepadBinding>,
    lastPressed: GamepadButton?
) {
    val isPressed = lastPressed == button
    val boundAction = bindings.firstOrNull { it.button == button }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isPressed) Color(0xFF00F59B) else if (boundAction != null) Color(0x336366F1) else Color(0x1F64748B),
        border = BorderStroke(1.dp, if (isPressed) Color(0xFF00F59B) else Color(0x446366F1)),
        modifier = Modifier.width(90.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                button.ps4Label,
                color = if (isPressed) Color.Black else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
            Text(
                boundAction?.action?.title?.take(10) ?: "Unassigned",
                color = if (isPressed) Color.Black else Color(0xFF94A3B8),
                fontSize = 8.sp,
                maxLines = 1
            )
        }
    }
}

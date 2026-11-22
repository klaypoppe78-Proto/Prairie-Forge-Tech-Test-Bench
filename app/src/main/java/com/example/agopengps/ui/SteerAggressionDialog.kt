package com.example.agopengps.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.window.Dialog
import com.example.agopengps.navigation.GuidanceSettings

/**
 * Precision Steering Aggression, Sensitivity, and Operator Safety Override Dialog.
 * Allows quick in-cab tuning of auto-steer aggressiveness and steering wheel disengage safety.
 */
@Composable
fun SteerAggressionDialog(
    guidanceSettings: GuidanceSettings,
    isAutoSteerEngaged: Boolean,
    onSetSteeringAggression: (Int) -> Unit,
    onSetOperatorOverrideSensitivity: (Double) -> Unit,
    onToggleOperatorOverride: (Boolean) -> Unit,
    onUpdateGuidanceSettings: (GuidanceSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var aggression by remember(guidanceSettings.steeringAggressionPercent) {
        mutableFloatStateOf(guidanceSettings.steeringAggressionPercent.toFloat())
    }
    var overrideSens by remember(guidanceSettings.operatorOverrideSensitivityDeg) {
        mutableFloatStateOf(guidanceSettings.operatorOverrideSensitivityDeg.toFloat())
    }
    var isOverrideEnabled by remember(guidanceSettings.isOperatorOverrideEnabled) {
        mutableStateOf(guidanceSettings.isOperatorOverrideEnabled)
    }
    var stanleyGain by remember(guidanceSettings.stanleyGain) {
        mutableFloatStateOf(guidanceSettings.stanleyGain.toFloat())
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color(0xFF0F172A),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
            shadowElevation = 24.dp,
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .padding(vertical = 12.dp)
                .testTag("steer_aggression_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E5FF).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Steering Aggression & Safety",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Guidance response & operator wheel override",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }

                HorizontalDivider(color = Color(0xFF334155))

                // 1. Steering Aggression Slider Card
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "STEERING AGGRESSION",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Surface(
                                color = Color(0xFF0284C7),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${aggression.toInt()}%",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Text(
                            text = when {
                                aggression < 75f -> "Gentle / Soft (loose sandy soil, high transport speeds)"
                                aggression < 125f -> "Balanced Standard (general planting & tillage)"
                                aggression < 175f -> "Responsive / Tight (furrows, ridge-till, high precision)"
                                else -> "Maximum Aggressive (fast line acquisition, heavy side-hill draft)"
                            },
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )

                        Slider(
                            value = aggression,
                            onValueChange = {
                                aggression = it
                                onSetSteeringAggression(it.toInt())
                            },
                            valueRange = 50f..220f,
                            steps = 16,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00E5FF),
                                activeTrackColor = Color(0xFF00E5FF),
                                inactiveTrackColor = Color(0xFF334155)
                            ),
                            modifier = Modifier.testTag("aggression_slider")
                        )

                        // Quick Presets
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(70 to "Soft", 100 to "Norm", 140 to "Firm", 180 to "Aggr").forEach { (valPct, label) ->
                                OutlinedButton(
                                    onClick = {
                                        aggression = valPct.toFloat()
                                        onSetSteeringAggression(valPct)
                                    },
                                    modifier = Modifier.weight(1f).height(32.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (aggression.toInt() == valPct) Color(0x3300E5FF) else Color.Transparent
                                    ),
                                    border = BorderStroke(1.dp, if (aggression.toInt() == valPct) Color(0xFF00E5FF) else Color(0xFF475569))
                                ) {
                                    Text(label, fontSize = 11.sp, color = if (aggression.toInt() == valPct) Color(0xFF00E5FF) else Color(0xFFCBD5E1), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // 2. Operator Steering Wheel Override Safety Card
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.SportsMotorsports, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "OPERATOR WHEEL OVERRIDE",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "Cancel AutoSteer instantly when wheel is turned",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Switch(
                                checked = isOverrideEnabled,
                                onCheckedChange = {
                                    isOverrideEnabled = it
                                    onToggleOperatorOverride(it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFFF59E0B),
                                    uncheckedThumbColor = Color(0xFF64748B),
                                    uncheckedTrackColor = Color(0xFF334155)
                                )
                            )
                        }

                        if (isOverrideEnabled) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Disengage Sensitivity Threshold", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                                Text("${String.format(java.util.Locale.US, "%.1f", overrideSens)}°", color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            Slider(
                                value = overrideSens,
                                onValueChange = {
                                    overrideSens = it
                                    onSetOperatorOverrideSensitivity(it.toDouble())
                                },
                                valueRange = 1.0f..6.0f,
                                steps = 9,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFF59E0B),
                                    activeTrackColor = Color(0xFFF59E0B),
                                    inactiveTrackColor = Color(0xFF334155)
                                )
                            )

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("⚡ Hair-Trigger (1.5°)", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                Text("Standard (2.5°)", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                Text("Firm Resistance (5.0°)", color = Color(0xFF94A3B8), fontSize = 10.sp)
                            }
                        }
                    }
                }

                // 3. Proportional Cross-Track Gain (Kp)
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("STANLEY CROSS-TRACK GAIN (Kp)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Text(String.format(java.util.Locale.US, "%.2f", stanleyGain), color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Slider(
                            value = stanleyGain,
                            onValueChange = {
                                stanleyGain = it
                                onUpdateGuidanceSettings(guidanceSettings.copy(stanleyGain = it.toDouble()))
                            },
                            valueRange = 0.5f..2.5f,
                            steps = 19,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF10B981),
                                activeTrackColor = Color(0xFF10B981),
                                inactiveTrackColor = Color(0xFF334155)
                            )
                        )
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("DONE / APPLY", color = Color(0xFF0F172A), fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

package com.example.agopengps.ui

import android.content.Context
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.agopengps.data.FieldDataExporter
import com.example.agopengps.navigation.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ExportFieldDialog(
    fieldName: String,
    originGeo: GeoPoint,
    fieldBoundary: List<Vec2>,
    headlandBoundary: List<Vec2>,
    abLine: ABLine?,
    obstacles: List<FieldObstacle>,
    appliedSegments: List<AppliedSwathSegment>,
    workedAcres: Double,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val bgDark = Color(0xFF141E18)
    val cardBg = Color(0xFF1B2820)
    val accentGreen = Color(0xFF00E676)

    val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
    val sanitizedFieldName = fieldName.replace("[^a-zA-Z0-9_-]".toRegex(), "_")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.80f)
                .testTag("export_field_dialog"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = bgDark)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF29B6F6)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                tint = Color.Black
                            )
                        }
                        Column {
                            Text(
                                text = "Export Field & Coverage Data",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Google Earth KML, CSV Summary, and GeoJSON",
                                color = Color(0xFF81C784),
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Field Overview Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Field: $fieldName",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Worked Area:", color = Color.LightGray, fontSize = 12.sp)
                            Text(
                                "${String.format(Locale.US, "%.2f", workedAcres)} Acres",
                                color = accentGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Applied Swath Passes:", color = Color.LightGray, fontSize = 12.sp)
                            Text("${appliedSegments.size} segments", color = Color.White, fontSize = 12.sp)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Flagged Hazards:", color = Color.LightGray, fontSize = 12.sp)
                            Text("${obstacles.size} obstacles", color = Color(0xFFFF9800), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("AB Guidance Line:", color = Color.LightGray, fontSize = 12.sp)
                            Text(abLine?.name ?: "None", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Select Export Format to Share:",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Format Export Buttons
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Google Earth KML
                    ExportOptionCard(
                        title = "Google Earth KML (.kml)",
                        subtitle = "Boundary polygon, AB guidance line, obstacles, and 3D coverage passes for Google Earth.",
                        icon = Icons.Default.Public,
                        buttonColor = Color(0xFF00E676),
                        onClick = {
                            val kmlContent = FieldDataExporter.generateKml(
                                fieldName = fieldName,
                                originGeo = originGeo,
                                fieldBoundary = fieldBoundary,
                                headlandBoundary = headlandBoundary,
                                abLine = abLine,
                                obstacles = obstacles,
                                appliedSegments = appliedSegments,
                                workedAcres = workedAcres
                            )
                            FieldDataExporter.shareFile(
                                context = context,
                                filename = "${sanitizedFieldName}_${dateStr}.kml",
                                content = kmlContent,
                                mimeType = "application/vnd.google-earth.kml+xml"
                            )
                        }
                    )

                    // 2. CSV Acreage & Work Report
                    ExportOptionCard(
                        title = "Acreage & Hazard CSV Report (.csv)",
                        subtitle = "Spreadsheet table of worked acres, GPS coordinates, timestamps, and obstacle locations.",
                        icon = Icons.Default.TableChart,
                        buttonColor = Color(0xFFFFB300),
                        onClick = {
                            val csvContent = FieldDataExporter.generateCsv(
                                fieldName = fieldName,
                                originGeo = originGeo,
                                workedAcres = workedAcres,
                                fieldBoundary = fieldBoundary,
                                abLine = abLine,
                                obstacles = obstacles,
                                appliedSegmentsCount = appliedSegments.size
                            )
                            FieldDataExporter.shareFile(
                                context = context,
                                filename = "${sanitizedFieldName}_AcreageReport_${dateStr}.csv",
                                content = csvContent,
                                mimeType = "text/csv"
                            )
                        }
                    )

                    // 3. GIS GeoJSON
                    ExportOptionCard(
                        title = "Standard GIS GeoJSON (.geojson)",
                        subtitle = "Standard geographic features collection for QGIS, SMS Advanced, or Climate FieldView.",
                        icon = Icons.Default.Map,
                        buttonColor = Color(0xFF42A5F5),
                        onClick = {
                            val geoJsonContent = FieldDataExporter.generateGeoJson(
                                fieldName = fieldName,
                                originGeo = originGeo,
                                fieldBoundary = fieldBoundary,
                                abLine = abLine,
                                obstacles = obstacles
                            )
                            FieldDataExporter.shareFile(
                                context = context,
                                filename = "${sanitizedFieldName}_${dateStr}.geojson",
                                content = geoJsonContent,
                                mimeType = "application/geo+json"
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportOptionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    buttonColor: Color,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2820)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(buttonColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = buttonColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = subtitle,
                        color = Color.LightGray,
                        fontSize = 10.sp
                    )
                }
            }

            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "SHARE",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

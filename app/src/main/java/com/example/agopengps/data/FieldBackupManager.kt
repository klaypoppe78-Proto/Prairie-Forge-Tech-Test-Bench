package com.example.agopengps.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class BackupPackageResult(
    val fields: List<FieldEntity> = emptyList(),
    val abLines: List<ABLineEntity> = emptyList(),
    val recordedPaths: List<RecordedPathEntity> = emptyList(),
    val implementProfiles: List<ImplementProfileEntity> = emptyList(),
    val obstacles: List<FieldObstacleEntity> = emptyList(),
    val sourceTractor: String = "Sentinel Guidance Tractor"
)

object FieldBackupManager {

    fun createBackupPackage(
        fields: List<FieldEntity>,
        abLines: List<ABLineEntity>,
        recordedPaths: List<RecordedPathEntity>,
        implementProfiles: List<ImplementProfileEntity>,
        obstacles: List<FieldObstacleEntity>,
        tractorName: String = "Sentinel Guidance Tractor"
    ): String {
        val root = JSONObject()
        root.put("version", "1.0")
        root.put("backupDate", System.currentTimeMillis())
        root.put("tractorName", tractorName)

        val fieldsArr = JSONArray()
        fields.forEach { f ->
            fieldsArr.put(JSONObject().apply {
                put("id", f.id)
                put("name", f.name)
                put("areaAcres", f.areaAcres)
                put("workedAcres", f.workedAcres)
                put("originLat", f.originLat)
                put("originLon", f.originLon)
                put("boundaryPointsJson", f.boundaryPointsJson)
                put("headlandPointsJson", f.headlandPointsJson)
                put("activeAbLineName", f.activeAbLineName)
                put("activeAbLineHeading", f.activeAbLineHeading)
                put("activeAbLineAX", f.activeAbLineAX)
                put("activeAbLineAY", f.activeAbLineAY)
                put("activeAbLineBX", f.activeAbLineBX)
                put("activeAbLineBY", f.activeAbLineBY)
                put("hasActiveAbLine", f.hasActiveAbLine)
                put("createdAt", f.createdAt)
                put("lastModifiedAt", f.lastModifiedAt)
            })
        }
        root.put("fields", fieldsArr)

        val abArr = JSONArray()
        abLines.forEach { ab ->
            abArr.put(JSONObject().apply {
                put("id", ab.id)
                put("fieldId", ab.fieldId)
                put("name", ab.name)
                put("headingDeg", ab.headingDeg)
                put("aLocalX", ab.aLocalX)
                put("aLocalY", ab.aLocalY)
                put("bLocalX", ab.bLocalX)
                put("bLocalY", ab.bLocalY)
                put("aLat", ab.aLat)
                put("aLon", ab.aLon)
                put("bLat", ab.bLat)
                put("bLon", ab.bLon)
                put("createdAt", ab.createdAt)
            })
        }
        root.put("abLines", abArr)

        val pathsArr = JSONArray()
        recordedPaths.forEach { p ->
            pathsArr.put(JSONObject().apply {
                put("id", p.id)
                put("fieldId", p.fieldId)
                put("name", p.name)
                put("totalDistanceMeters", p.totalDistanceMeters)
                put("rowCount", p.rowCount)
                put("rowSpacingInches", p.rowSpacingInches)
                put("isPlanterTrack", p.isPlanterTrack)
                put("pointsJson", p.pointsJson)
                put("createdAt", p.createdAt)
            })
        }
        root.put("recordedPaths", pathsArr)

        val impArr = JSONArray()
        implementProfiles.forEach { imp ->
            impArr.put(JSONObject().apply {
                put("id", imp.id)
                put("name", imp.name)
                put("implementType", imp.implementType)
                put("toolWidthMeters", imp.toolWidthMeters)
                put("offsetBehindTractorMeters", imp.offsetBehindTractorMeters)
                put("numSections", imp.numSections)
                put("overlapReductionCm", imp.overlapReductionCm)
                put("lookaheadOnSec", imp.lookaheadOnSec)
                put("lookaheadOffSec", imp.lookaheadOffSec)
                put("isAutoSectionControl", imp.isAutoSectionControl)
            })
        }
        root.put("implementProfiles", impArr)

        val obsArr = JSONArray()
        obstacles.forEach { obs ->
            obsArr.put(JSONObject().apply {
                put("id", obs.id)
                put("fieldId", obs.fieldId)
                put("name", obs.name)
                put("type", obs.type)
                put("localX", obs.localX)
                put("localY", obs.localY)
                put("lat", obs.lat)
                put("lon", obs.lon)
                put("radiusMeters", obs.radiusMeters)
                put("notes", obs.notes)
                put("timestamp", obs.timestamp)
            })
        }
        root.put("obstacles", obsArr)

        return root.toString(2)
    }

    fun parseBackupPackage(jsonStr: String): BackupPackageResult {
        val root = JSONObject(jsonStr)
        val sourceTractor = root.optString("tractorName", "Sentinel Guidance Tractor")

        val fieldsList = mutableListOf<FieldEntity>()
        val fieldsArr = root.optJSONArray("fields")
        if (fieldsArr != null) {
            for (i in 0 until fieldsArr.length()) {
                val obj = fieldsArr.getJSONObject(i)
                fieldsList.add(
                    FieldEntity(
                        id = obj.optLong("id", 0L),
                        name = obj.optString("name", "Field"),
                        areaAcres = obj.optDouble("areaAcres", 0.0),
                        workedAcres = obj.optDouble("workedAcres", 0.0),
                        originLat = obj.optDouble("originLat", 44.9542),
                        originLon = obj.optDouble("originLon", -96.0825),
                        boundaryPointsJson = obj.optString("boundaryPointsJson", ""),
                        headlandPointsJson = obj.optString("headlandPointsJson", ""),
                        activeAbLineName = obj.optString("activeAbLineName", ""),
                        activeAbLineHeading = obj.optDouble("activeAbLineHeading", 0.0),
                        activeAbLineAX = obj.optDouble("activeAbLineAX", 0.0),
                        activeAbLineAY = obj.optDouble("activeAbLineAY", 0.0),
                        activeAbLineBX = obj.optDouble("activeAbLineBX", 0.0),
                        activeAbLineBY = obj.optDouble("activeAbLineBY", 0.0),
                        hasActiveAbLine = obj.optBoolean("hasActiveAbLine", false),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        lastModifiedAt = obj.optLong("lastModifiedAt", System.currentTimeMillis())
                    )
                )
            }
        }

        val abList = mutableListOf<ABLineEntity>()
        val abArr = root.optJSONArray("abLines")
        if (abArr != null) {
            for (i in 0 until abArr.length()) {
                val obj = abArr.getJSONObject(i)
                abList.add(
                    ABLineEntity(
                        id = obj.optLong("id", 0L),
                        fieldId = obj.optLong("fieldId", 0L),
                        name = obj.optString("name", "AB Line"),
                        headingDeg = obj.optDouble("headingDeg", 0.0),
                        aLocalX = obj.optDouble("aLocalX", 0.0),
                        aLocalY = obj.optDouble("aLocalY", 0.0),
                        bLocalX = obj.optDouble("bLocalX", 0.0),
                        bLocalY = obj.optDouble("bLocalY", 0.0),
                        aLat = obj.optDouble("aLat", 0.0),
                        aLon = obj.optDouble("aLon", 0.0),
                        bLat = obj.optDouble("bLat", 0.0),
                        bLon = obj.optDouble("bLon", 0.0),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }

        val pathList = mutableListOf<RecordedPathEntity>()
        val pathsArr = root.optJSONArray("recordedPaths")
        if (pathsArr != null) {
            for (i in 0 until pathsArr.length()) {
                val obj = pathsArr.getJSONObject(i)
                val jsonPoints = obj.optString("pointsJson", "")
                pathList.add(
                    RecordedPathEntity(
                        id = obj.optLong("id", 0L),
                        name = obj.optString("name", "Recorded Path"),
                        fieldId = obj.optLong("fieldId", 0L),
                        pointsJson = jsonPoints,
                        totalDistanceMeters = obj.optDouble("totalDistanceMeters", 0.0),
                        rowCount = obj.optInt("rowCount", 24),
                        rowSpacingInches = obj.optDouble("rowSpacingInches", 22.0),
                        isPlanterTrack = obj.optBoolean("isPlanterTrack", true),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }

        val impList = mutableListOf<ImplementProfileEntity>()
        val impArr = root.optJSONArray("implementProfiles")
        if (impArr != null) {
            for (i in 0 until impArr.length()) {
                val obj = impArr.getJSONObject(i)
                impList.add(
                    ImplementProfileEntity(
                        id = obj.optLong("id", 0L),
                        name = obj.optString("name", "Implement"),
                        implementType = obj.optString("implementType", "THREE_POINT_MOUNTED"),
                        toolWidthMeters = obj.optDouble("toolWidthMeters", 9.144),
                        offsetBehindTractorMeters = obj.optDouble("offsetBehindTractorMeters", 0.914),
                        numSections = obj.optInt("numSections", 6),
                        overlapReductionCm = obj.optDouble("overlapReductionCm", 15.0),
                        lookaheadOnSec = obj.optDouble("lookaheadOnSec", 0.8),
                        lookaheadOffSec = obj.optDouble("lookaheadOffSec", 0.4),
                        isAutoSectionControl = obj.optBoolean("isAutoSectionControl", true)
                    )
                )
            }
        }

        val obsList = mutableListOf<FieldObstacleEntity>()
        val obsArr = root.optJSONArray("obstacles")
        if (obsArr != null) {
            for (i in 0 until obsArr.length()) {
                val obj = obsArr.getJSONObject(i)
                obsList.add(
                    FieldObstacleEntity(
                        id = obj.optLong("id", 0L),
                        fieldId = obj.optLong("fieldId", 0L),
                        name = obj.optString("name", "Obstacle"),
                        type = obj.optString("type", "ROCK"),
                        localX = obj.optDouble("localX", 0.0),
                        localY = obj.optDouble("localY", 0.0),
                        lat = obj.optDouble("lat", 0.0),
                        lon = obj.optDouble("lon", 0.0),
                        radiusMeters = obj.optDouble("radiusMeters", 3.0),
                        notes = obj.optString("notes", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        }

        return BackupPackageResult(
            fields = fieldsList,
            abLines = abList,
            recordedPaths = pathList,
            implementProfiles = impList,
            obstacles = obsList,
            sourceTractor = sourceTractor
        )
    }

    fun exportBackupAndShare(context: Context, backupJson: String) {
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val filename = "SentinelGuidance_Backup_$dateStr.json"
        try {
            val exportDir = File(context.cacheDir, "backups").apply { mkdirs() }
            val file = File(exportDir, filename)
            file.writeText(backupJson)

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, filename)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Sync Field Data to USB / Drive"))
        } catch (e: Exception) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, backupJson)
                putExtra(Intent.EXTRA_SUBJECT, filename)
            }
            context.startActivity(Intent.createChooser(intent, "Sync Field Data"))
        }
    }
}

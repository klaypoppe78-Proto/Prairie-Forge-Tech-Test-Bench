package com.example.agopengps.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fields")
data class FieldEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val areaAcres: Double = 0.0,
    val workedAcres: Double = 0.0,
    val originLat: Double = 44.3114,
    val originLon: Double = -96.6328,
    val boundaryPointsJson: String = "",
    val headlandPointsJson: String = "",
    val activeAbLineName: String = "",
    val activeAbLineHeading: Double = 0.0,
    val activeAbLineAX: Double = 0.0,
    val activeAbLineAY: Double = 0.0,
    val activeAbLineBX: Double = 0.0,
    val activeAbLineBY: Double = 0.0,
    val hasActiveAbLine: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModifiedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "ab_lines")
data class ABLineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val fieldId: Long,
    val name: String,
    val headingDeg: Double,
    val aLocalX: Double,
    val aLocalY: Double,
    val bLocalX: Double,
    val bLocalY: Double,
    val aLat: Double = 0.0,
    val aLon: Double = 0.0,
    val bLat: Double = 0.0,
    val bLon: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "covered_passes")
data class CoveredPassEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val fieldId: Long,
    val leftStartX: Double,
    val leftStartY: Double,
    val rightStartX: Double,
    val rightStartY: Double,
    val leftEndX: Double,
    val leftEndY: Double,
    val rightEndX: Double,
    val rightEndY: Double,
    val centerX: Double,
    val centerY: Double,
    val radius: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "field_obstacles")
data class FieldObstacleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val fieldId: Long = 0L,
    val name: String = "Obstacle",
    val type: String = "ROCK",
    val localX: Double = 0.0,
    val localY: Double = 0.0,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val radiusMeters: Double = 3.0,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

typealias ObstacleEntity = FieldObstacleEntity

@Entity(tableName = "vehicle_profiles")
data class VehicleProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String = "New Holland 8870",
    val wheelbase: Double = 2.845,
    val trackWidth: Double = 2.083,
    val antennaHeight: Double = 3.20,
    val antennaPivotOffset: Double = 0.20,
    val hitchLength: Double = -1.22,
    val maxSteerAngleDeg: Double = 42.0
)

@Entity(tableName = "implement_profiles")
data class ImplementProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val implementType: String,
    val toolWidthMeters: Double,
    val offsetBehindTractorMeters: Double,
    val numSections: Int,
    val overlapReductionCm: Double,
    val lookaheadOnSec: Double,
    val lookaheadOffSec: Double,
    val isAutoSectionControl: Boolean = true
)

@Entity(tableName = "recorded_paths")
data class RecordedPathEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val fieldId: Long,
    val name: String,
    val pointsJson: String,
    val totalDistanceMeters: Double,
    val rowCount: Int = 24,
    val rowSpacingInches: Double = 22.0,
    val isPlanterTrack: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "gamepad_bindings")
data class GamepadBindingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val buttonCode: String,
    val actionCode: String,
    val label: String,
    val isCustom: Boolean = false
)

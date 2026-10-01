package com.example.agopengps.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fields")
data class FieldEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val areaAcres: Double = 0.0,
    val workedAcres: Double = 0.0,
    val originLat: Double,
    val originLon: Double,
    val boundaryPointsJson: String = "", // JSON list of Vec2 points
    val headlandPointsJson: String = "", // JSON list of headland Vec2 points
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

@Entity(tableName = "covered_passes")
data class CoveredPassEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
    val radius: Double,
    val passTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "ab_lines")
data class ABLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fieldId: Long,
    val name: String,
    val aLat: Double,
    val aLon: Double,
    val bLat: Double,
    val bLon: Double,
    val headingDeg: Double,
    val swathWidthMeters: Double
)

@Entity(tableName = "vehicle_profiles")
data class VehicleProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "Tractor 1",
    val wheelbase: Double = 2.85,
    val trackWidth: Double = 2.10,
    val antennaHeight: Double = 3.20,
    val antennaOffset: Double = 0.20,
    val hitchLength: Double = -1.20,
    val maxSteerAngleDeg: Double = 38.0,
    val toolWidth: Double = 9.0,
    val numSections: Int = 6,
    val controllerType: String = "STANLEY",
    val stanleyGain: Double = 1.25,
    val lookaheadGain: Double = 0.85
)

@Entity(tableName = "field_obstacles")
data class ObstacleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fieldId: Long,
    val name: String,
    val type: String = "ROCK",
    val localX: Double,
    val localY: Double,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double = 3.0,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "implement_profiles")
data class ImplementProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val implementType: String = "THREE_POINT_MOUNTED",
    val toolWidthMeters: Double = 9.144,
    val numSections: Int = 6,
    val offsetBehindTractorMeters: Double = 0.914,
    val overlapMeters: Double = 0.15,
    val lateralOffsetMeters: Double = 0.0,
    val isAutoSectionControl: Boolean = true,
    val category: String = "General"
)


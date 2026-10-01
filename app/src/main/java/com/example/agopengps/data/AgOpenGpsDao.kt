package com.example.agopengps.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AgOpenGpsDao {

    @Query("SELECT * FROM fields ORDER BY lastModifiedAt DESC")
    fun getAllFields(): Flow<List<FieldEntity>>

    @Query("SELECT * FROM fields WHERE id = :fieldId LIMIT 1")
    suspend fun getFieldById(fieldId: Long): FieldEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertField(field: FieldEntity): Long

    @Update
    suspend fun updateField(field: FieldEntity)

    @Query("DELETE FROM fields WHERE id = :fieldId")
    suspend fun deleteField(fieldId: Long)

    @Query("SELECT * FROM covered_passes WHERE fieldId = :fieldId ORDER BY id ASC")
    fun getPassesForField(fieldId: Long): Flow<List<CoveredPassEntity>>

    @Query("SELECT * FROM covered_passes WHERE fieldId = :fieldId ORDER BY id ASC")
    suspend fun getPassesForFieldSync(fieldId: Long): List<CoveredPassEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPasses(passes: List<CoveredPassEntity>)

    @Query("DELETE FROM covered_passes WHERE fieldId = :fieldId")
    suspend fun deletePassesForField(fieldId: Long)

    @Query("SELECT * FROM ab_lines WHERE fieldId = :fieldId ORDER BY id ASC")
    fun getABLinesForField(fieldId: Long): Flow<List<ABLineEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertABLine(abLine: ABLineEntity): Long

    @Query("DELETE FROM ab_lines WHERE id = :abLineId")
    suspend fun deleteABLine(abLineId: Long)

    @Query("SELECT * FROM vehicle_profiles ORDER BY id ASC")
    fun getAllVehicleProfiles(): Flow<List<VehicleProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicleProfile(profile: VehicleProfileEntity): Long

    // --- OBSTACLES & HAZARDS ---
    @Query("SELECT * FROM field_obstacles WHERE fieldId = :fieldId ORDER BY timestamp DESC")
    fun getObstaclesForField(fieldId: Long): Flow<List<ObstacleEntity>>

    @Query("SELECT * FROM field_obstacles WHERE fieldId = :fieldId ORDER BY timestamp DESC")
    suspend fun getObstaclesForFieldSync(fieldId: Long): List<ObstacleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertObstacle(obstacle: ObstacleEntity): Long

    @Query("DELETE FROM field_obstacles WHERE id = :obstacleId")
    suspend fun deleteObstacle(obstacleId: Long)

    @Query("DELETE FROM field_obstacles WHERE fieldId = :fieldId")
    suspend fun deleteObstaclesForField(fieldId: Long)

    // --- IMPLEMENT PROFILES ---
    @Query("SELECT * FROM implement_profiles ORDER BY id ASC")
    fun getAllImplementProfiles(): Flow<List<ImplementProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImplementProfile(profile: ImplementProfileEntity): Long

    @Query("DELETE FROM implement_profiles WHERE id = :profileId")
    suspend fun deleteImplementProfile(profileId: Long)
}

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

    // --- RECORDED PATHS / EXACT TRACK MEMORY ---
    @Query("SELECT * FROM recorded_paths WHERE fieldId = :fieldId ORDER BY createdAt DESC")
    fun getRecordedPathsForField(fieldId: Long): Flow<List<RecordedPathEntity>>

    @Query("SELECT * FROM recorded_paths WHERE fieldId = :fieldId ORDER BY createdAt DESC")
    suspend fun getRecordedPathsForFieldSync(fieldId: Long): List<RecordedPathEntity>

    @Query("SELECT * FROM recorded_paths ORDER BY createdAt DESC")
    fun getAllRecordedPaths(): Flow<List<RecordedPathEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecordedPath(path: RecordedPathEntity): Long

    @Query("DELETE FROM recorded_paths WHERE id = :pathId")
    suspend fun deleteRecordedPath(pathId: Long)

    @Query("DELETE FROM recorded_paths WHERE fieldId = :fieldId")
    suspend fun deleteRecordedPathsForField(fieldId: Long)

    // --- GAMEPAD / CONTROLLER BINDINGS ---
    @Query("SELECT * FROM gamepad_bindings ORDER BY id ASC")
    fun getAllGamepadBindings(): Flow<List<GamepadBindingEntity>>

    @Query("SELECT * FROM gamepad_bindings ORDER BY id ASC")
    suspend fun getAllGamepadBindingsSync(): List<GamepadBindingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGamepadBinding(binding: GamepadBindingEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGamepadBindings(bindings: List<GamepadBindingEntity>)

    @Query("DELETE FROM gamepad_bindings WHERE id = :bindingId")
    suspend fun deleteGamepadBinding(bindingId: Long)

    @Query("DELETE FROM gamepad_bindings")
    suspend fun clearAllGamepadBindings()
}


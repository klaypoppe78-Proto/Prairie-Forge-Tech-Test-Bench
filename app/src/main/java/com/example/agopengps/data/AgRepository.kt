package com.example.agopengps.data

import kotlinx.coroutines.flow.Flow

class AgRepository(private val dao: AgOpenGpsDao) {
    val allFields: Flow<List<FieldEntity>> = dao.getAllFields()
    val allVehicleProfiles: Flow<List<VehicleProfileEntity>> = dao.getAllVehicleProfiles()

    suspend fun getFieldById(id: Long): FieldEntity? = dao.getFieldById(id)
    suspend fun saveField(field: FieldEntity): Long = dao.insertField(field)
    suspend fun updateField(field: FieldEntity) = dao.updateField(field)
    suspend fun deleteField(id: Long) {
        dao.deletePassesForField(id)
        dao.deleteField(id)
    }

    suspend fun savePassesForField(fieldId: Long, passes: List<CoveredPassEntity>) {
        dao.deletePassesForField(fieldId)
        if (passes.isNotEmpty()) {
            dao.insertPasses(passes)
        }
    }

    suspend fun getPassesForField(fieldId: Long): List<CoveredPassEntity> {
        return dao.getPassesForFieldSync(fieldId)
    }

    fun getPassesForFieldFlow(fieldId: Long): Flow<List<CoveredPassEntity>> {
        return dao.getPassesForField(fieldId)
    }

    fun getABLinesForField(fieldId: Long): Flow<List<ABLineEntity>> = dao.getABLinesForField(fieldId)
    suspend fun saveABLine(abLine: ABLineEntity): Long = dao.insertABLine(abLine)
    suspend fun deleteABLine(id: Long) = dao.deleteABLine(id)

    suspend fun saveVehicleProfile(profile: VehicleProfileEntity): Long = dao.insertVehicleProfile(profile)

    // Obstacles
    fun getObstaclesForField(fieldId: Long): Flow<List<ObstacleEntity>> = dao.getObstaclesForField(fieldId)
    suspend fun getObstaclesForFieldSync(fieldId: Long): List<ObstacleEntity> = dao.getObstaclesForFieldSync(fieldId)
    suspend fun saveObstacle(obstacle: ObstacleEntity): Long = dao.insertObstacle(obstacle)
    suspend fun deleteObstacle(id: Long) = dao.deleteObstacle(id)
    suspend fun deleteObstaclesForField(fieldId: Long) = dao.deleteObstaclesForField(fieldId)

    // Implements
    fun getAllImplementProfiles(): Flow<List<ImplementProfileEntity>> = dao.getAllImplementProfiles()
    suspend fun saveImplementProfile(profile: ImplementProfileEntity): Long = dao.insertImplementProfile(profile)
    suspend fun deleteImplementProfile(id: Long) = dao.deleteImplementProfile(id)

    // Recorded Paths / Exact Track Memory
    fun getRecordedPathsForField(fieldId: Long): Flow<List<RecordedPathEntity>> = dao.getRecordedPathsForField(fieldId)
    suspend fun getRecordedPathsForFieldSync(fieldId: Long): List<RecordedPathEntity> = dao.getRecordedPathsForFieldSync(fieldId)
    fun getAllRecordedPaths(): Flow<List<RecordedPathEntity>> = dao.getAllRecordedPaths()
    suspend fun saveRecordedPath(path: RecordedPathEntity): Long = dao.insertRecordedPath(path)
    suspend fun deleteRecordedPath(id: Long) = dao.deleteRecordedPath(id)
    suspend fun deleteRecordedPathsForField(fieldId: Long) = dao.deleteRecordedPathsForField(fieldId)

    // Gamepad / Controller Bindings
    fun getAllGamepadBindings(): Flow<List<GamepadBindingEntity>> = dao.getAllGamepadBindings()
    suspend fun getAllGamepadBindingsSync(): List<GamepadBindingEntity> = dao.getAllGamepadBindingsSync()
    suspend fun saveGamepadBinding(binding: GamepadBindingEntity): Long = dao.insertGamepadBinding(binding)
    suspend fun saveGamepadBindings(bindings: List<GamepadBindingEntity>) = dao.insertGamepadBindings(bindings)
    suspend fun deleteGamepadBinding(id: Long) = dao.deleteGamepadBinding(id)
    suspend fun clearAllGamepadBindings() = dao.clearAllGamepadBindings()
}

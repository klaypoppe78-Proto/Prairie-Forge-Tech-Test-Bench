package com.example.agopengps.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FieldEntity::class,
        CoveredPassEntity::class,
        ABLineEntity::class,
        VehicleProfileEntity::class,
        ObstacleEntity::class,
        ImplementProfileEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AgOpenGpsDatabase : RoomDatabase() {
    abstract fun agDao(): AgOpenGpsDao

    companion object {
        @Volatile
        private var INSTANCE: AgOpenGpsDatabase? = null

        fun getDatabase(context: Context): AgOpenGpsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AgOpenGpsDatabase::class.java,
                    "agopengps_database.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

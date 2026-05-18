package com.example.drivemate.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.drivemate.Car
import com.example.drivemate.CarDao

@Database(entities = [Car::class], version = 3, exportSchema = false)
abstract class CarDatabase : RoomDatabase() {
    abstract fun carDao(): CarDao

    companion object {
        @Volatile
        private var INSTANCE: CarDatabase? = null

        fun getDatabase(context: Context): CarDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CarDatabase::class.java,
                    "car_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE cars ADD COLUMN oilChangeMileage REAL NOT NULL DEFAULT 0.0")
                database.execSQL("ALTER TABLE cars ADD COLUMN oilIntervalKm INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE cars ADD COLUMN lastOilNotificationDate INTEGER")
                database.execSQL("ALTER TABLE cars ADD COLUMN lastInspectionNotificationDate INTEGER")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE cars ADD COLUMN hasNitro INTEGER NOT NULL DEFAULT 0")
                database.execSQL("UPDATE cars SET engineLevel = 1 WHERE engineLevel = 0")
                database.execSQL("UPDATE cars SET suspensionLevel = 1 WHERE suspensionLevel = 0")
            }
        }
    }
}
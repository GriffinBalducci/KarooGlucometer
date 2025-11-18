package com.example.karooglucometer.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

@Database(
    entities = [GlucoseReading::class],
    version = 1,
    exportSchema = false
)
abstract class GlucoseDatabase : RoomDatabase() {
    abstract fun glucoseDao(): GlucoseDao
}
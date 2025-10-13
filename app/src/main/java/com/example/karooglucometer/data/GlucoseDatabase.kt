/*
This file sets up the Room database for GlucoseReading.
 */

package com.example.karooglucometer.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [GlucoseReading::class], version = 1) // Entities and version number
abstract class GlucoseDatabase : RoomDatabase() { // Set up database
    abstract fun glucoseDao(): GlucoseDao // Call the DAO for the database
}

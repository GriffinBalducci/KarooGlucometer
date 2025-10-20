/*
This file contains the database entity for GlucoseReading.
 */

package com.example.karooglucometer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// GlucoseReading entity
@Entity(tableName = "glucose_readings")
data class GlucoseReading(
    @PrimaryKey(autoGenerate = true) val id: Int = 0, // Autogenerate unique ID
    val timestamp: Long,
    val glucoseValue: Int
)

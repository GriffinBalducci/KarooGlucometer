package com.example.karooglucometer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "glucose_readings")
data class GlucoseReading(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Long,
    val glucoseValue: Int
)

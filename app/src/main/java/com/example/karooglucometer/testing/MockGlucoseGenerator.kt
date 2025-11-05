package com.example.karooglucometer.testing

import com.example.karooglucometer.data.GlucoseReading
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Simple mock data generator for testing in IDE without phone connection
 */
class MockGlucoseGenerator {
    
    private var baseGlucose = 120
    private var lastTimestamp = System.currentTimeMillis()
    
    fun generateRealisticReading(): GlucoseReading {
        val currentTime = System.currentTimeMillis()
        
        // Simulate realistic glucose changes (small variations)
        val change = Random.nextInt(-8, 9) // Change between -8 to +8
        baseGlucose = (baseGlucose + change).coerceIn(80, 200)
        
        lastTimestamp = currentTime
        
        return GlucoseReading(
            timestamp = currentTime,
            glucoseValue = baseGlucose
        )
    }
    
    fun generateHistoricalData(hours: Int = 6): List<GlucoseReading> {
        val readings = mutableListOf<GlucoseReading>()
        val startTime = System.currentTimeMillis() - (hours * 60 * 60 * 1000L)
        
        // Generate reading every 5 minutes
        for (i in 0 until (hours * 12)) {
            val timestamp = startTime + (i * 5 * 60 * 1000L)
            
            // Simulate some realistic patterns
            val timeOfDay = (timestamp / (60 * 60 * 1000L)) % 24
            val baseForTime = when (timeOfDay.toInt()) {
                in 6..10 -> 110 + Random.nextInt(-15, 25) // Morning rise
                in 12..14 -> 140 + Random.nextInt(-20, 30) // Post-lunch
                in 18..20 -> 130 + Random.nextInt(-20, 25) // Post-dinner  
                else -> 105 + Random.nextInt(-10, 20) // Normal times
            }
            
            readings.add(GlucoseReading(
                timestamp = timestamp,
                glucoseValue = baseForTime.coerceIn(70, 250)
            ))
        }
        
        return readings.sortedByDescending { it.timestamp }
    }
}
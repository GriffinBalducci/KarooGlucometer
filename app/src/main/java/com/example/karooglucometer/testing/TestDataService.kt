package com.example.karooglucometer.testing

import android.content.Context
import androidx.room.Room
import com.example.karooglucometer.data.GlucoseDatabase
import com.example.karooglucometer.data.GlucoseReading
import com.example.karooglucometer.monitoring.DataSourceMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Service to populate database with test data for development
 */
class TestDataService(context: Context, private val monitor: DataSourceMonitor? = null) {
    
    private val db: GlucoseDatabase = Room.databaseBuilder(
        context.applicationContext,
        GlucoseDatabase::class.java,
        "glucose_db"
    ).build()
    
    private var totalGenerated = 0
    
    private val dao = db.glucoseDao()
    private val mockGenerator = MockGlucoseGenerator()
    
    fun populateTestData() {
        CoroutineScope(Dispatchers.IO).launch {
            monitor?.updateTestDataStatus(true, System.currentTimeMillis(), totalGenerated)
            
            // Clear existing data
            dao.clear()
            
            // Add some historical data
            val historicalReadings = mockGenerator.generateHistoricalData(6)
            
            historicalReadings.forEach { reading ->
                dao.insert(reading)
                totalGenerated++
            }
            
            // Update monitor with final status
            val readCount = dao.getRecent().size
            monitor?.updateDatabaseStatus(true, readCount, System.currentTimeMillis())
            monitor?.updateTestDataStatus(true, System.currentTimeMillis(), totalGenerated)
        }
    }
    
    fun addSingleTestReading() {
        CoroutineScope(Dispatchers.IO).launch {
            val newReading = mockGenerator.generateRealisticReading()
            dao.insert(newReading)
            totalGenerated++
            
            // Update monitor
            val readCount = dao.getRecent().size
            monitor?.updateDatabaseStatus(true, readCount, System.currentTimeMillis())
            monitor?.updateTestDataStatus(true, System.currentTimeMillis(), totalGenerated)
        }
    }
    
    fun clearAllData() {
        CoroutineScope(Dispatchers.IO).launch {
            dao.clear()
        }
    }
}
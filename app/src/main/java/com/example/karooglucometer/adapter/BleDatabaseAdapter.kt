package com.example.karooglucometer.adapter

import android.content.Context
import android.util.Log
import com.example.karooglucometer.data.GlucoseReading
import com.example.karooglucometer.datasource.GlucoseDataSourceManager
import kotlinx.coroutines.*
import androidx.room.Room
import com.example.karooglucometer.data.GlucoseDatabase

/**
 * Adapter to integrate BLE GATT system with existing Room database structure
 */
class BleDatabaseAdapter(private val context: Context) {
    companion object {
        private const val TAG = "BleDatabaseAdapter"
    }

    private val glucoseDataManager = GlucoseDataSourceManager(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val db = Room.databaseBuilder(
        context,
        GlucoseDatabase::class.java,
        "glucose_db"
    ).build()
    
    private val dao = db.glucoseDao()

    private var syncJob: Job? = null

    /**
     * Start BLE monitoring and sync to Room database
     */
    fun startMonitoring() {
        Log.d(TAG, "Starting BLE to Room database sync")
        
        // Start the BLE GATT system
        glucoseDataManager.startMonitoring(GlucoseDataSourceManager.DataSource.AUTO)
        
        // Start syncing BLE readings to Room database
        syncJob = scope.launch {
            glucoseDataManager.combinedReadings.collect { readings ->
                if (readings.isNotEmpty()) {
                    val latestReading = readings.first()
                    
                    // Convert to Room entity format
                    val roomReading = GlucoseReading(
                        timestamp = latestReading.timestamp,
                        glucoseValue = latestReading.value.toInt()
                    )
                    
                    // Check if this reading already exists
                    val existingReading = dao.getReadingByTimestamp(latestReading.timestamp)
                    if (existingReading == null) {
                        dao.insert(roomReading)
                        Log.d(TAG, "Synced new BLE reading to Room: ${roomReading.glucoseValue} mg/dL from ${latestReading.source}")
                    }
                }
            }
        }
    }

    /**
     * Stop monitoring
     */
    fun stopMonitoring() {
        Log.d(TAG, "Stopping BLE to Room database sync")
        syncJob?.cancel()
        glucoseDataManager.stopMonitoring()
    }

    /**
     * Get connection status for debugging
     */
    fun getConnectionStatus(): String {
        return glucoseDataManager.getConnectionSummary()
    }

    /**
     * Get active data source
     */
    fun getActiveDataSource(): String {
        return glucoseDataManager.activeDataSource.value.name
    }

    /**
     * Manually refresh data sources
     */
    fun refresh() {
        glucoseDataManager.refreshAllSources()
    }

    /**
     * Switch data source
     */
    fun switchDataSource(source: String) {
        val dataSource = when (source.uppercase()) {
            "BLE" -> GlucoseDataSourceManager.DataSource.BLE
            "XDRIP" -> GlucoseDataSourceManager.DataSource.XDRIP
            else -> GlucoseDataSourceManager.DataSource.AUTO
        }
        glucoseDataManager.switchDataSource(dataSource)
    }

    /**
     * Get the underlying data source manager for advanced monitoring
     */
    fun getDataSourceManager(): GlucoseDataSourceManager {
        return glucoseDataManager
    }

    /**
     * Cleanup resources
     */
    fun destroy() {
        stopMonitoring()
        glucoseDataManager.destroy()
        scope.cancel()
        db.close()
    }
}
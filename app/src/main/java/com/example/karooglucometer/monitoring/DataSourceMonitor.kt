package com.example.karooglucometer.monitoring

import android.content.Context

/**
 * Simple data source monitor for BLE GATT system
 */
class DataSourceMonitor(private val context: Context) {
    
    private var appStatus = AppStatus()
    private var databaseStatus = DatabaseStatus()

    data class AppStatus(
        val debugMode: Boolean = false,
        val uptimeMinutes: Long = 0
    )

    data class DatabaseStatus(
        val connected: Boolean = false,
        val recordCount: Int = 0,
        val lastUpdateTime: Long = 0
    )

    fun updateAppStatus(debugMode: Boolean, uptimeMs: Long) {
        appStatus = AppStatus(
            debugMode = debugMode,
            uptimeMinutes = uptimeMs / 60000
        )
    }

    fun updateDatabaseStatus(connected: Boolean, recordCount: Int, lastUpdateTime: Long) {
        databaseStatus = DatabaseStatus(
            connected = connected,
            recordCount = recordCount,
            lastUpdateTime = lastUpdateTime
        )
    }

    fun getAppStatus(): AppStatus = appStatus
    fun getDatabaseStatus(): DatabaseStatus = databaseStatus
}
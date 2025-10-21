package com.example.karooglucometer.monitoring

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors the status of various data sources and provides debugging information
 */
class DataSourceMonitor(private val context: Context) {
    
    private val _statusFlow = MutableStateFlow(DataSourceStatus())
    val statusFlow: StateFlow<DataSourceStatus> = _statusFlow.asStateFlow()
    
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()
    
    fun updateDatabaseStatus(isConnected: Boolean, lastReadCount: Int, lastWriteTime: Long) {
        val current = _statusFlow.value
        _statusFlow.value = current.copy(
            database = DatabaseStatus(
                isConnected = isConnected,
                lastReadCount = lastReadCount,
                lastWriteTime = lastWriteTime
            )
        )
        addLog("Database", "Connected: $isConnected, Records: $lastReadCount")
    }
    
    fun updateTestDataStatus(isEnabled: Boolean, lastGeneratedTime: Long, totalGenerated: Int) {
        val current = _statusFlow.value
        _statusFlow.value = current.copy(
            testData = TestDataStatus(
                isEnabled = isEnabled,
                lastGeneratedTime = lastGeneratedTime,
                totalGenerated = totalGenerated
            )
        )
        addLog("TestData", "Enabled: $isEnabled, Generated: $totalGenerated")
    }
    
    fun updateHttpStatus(isAttempting: Boolean, lastSuccessTime: Long, lastErrorMessage: String?) {
        val current = _statusFlow.value
        _statusFlow.value = current.copy(
            http = HttpStatus(
                isAttempting = isAttempting,
                lastSuccessTime = lastSuccessTime,
                lastErrorMessage = lastErrorMessage
            )
        )
        addLog("HTTP", if (isAttempting) "Attempting connection" else "Idle")
    }
    
    fun updateAppStatus(debugMode: Boolean, uptime: Long) {
        val current = _statusFlow.value
        _statusFlow.value = current.copy(
            app = AppStatus(
                debugMode = debugMode,
                uptime = uptime
            )
        )
    }
    
    private fun addLog(source: String, message: String) {
        val newLog = LogEntry(
            timestamp = System.currentTimeMillis(),
            source = source,
            message = message
        )
        val currentLogs = _logs.value.toMutableList()
        currentLogs.add(0, newLog) // Add to front
        if (currentLogs.size > 50) { // Keep only last 50 logs
            currentLogs.removeAt(currentLogs.lastIndex)
        }
        _logs.value = currentLogs
        
        // Also log to Android Log
        Log.d("DataSourceMonitor", "[$source] $message")
    }
    
    fun clearLogs() {
        _logs.value = emptyList()
    }
}

data class DataSourceStatus(
    val database: DatabaseStatus = DatabaseStatus(),
    val testData: TestDataStatus = TestDataStatus(),
    val http: HttpStatus = HttpStatus(),
    val app: AppStatus = AppStatus()
)

data class DatabaseStatus(
    val isConnected: Boolean = false,
    val lastReadCount: Int = 0,
    val lastWriteTime: Long = 0
)

data class TestDataStatus(
    val isEnabled: Boolean = false,
    val lastGeneratedTime: Long = 0,
    val totalGenerated: Int = 0
)

data class HttpStatus(
    val isAttempting: Boolean = false,
    val lastSuccessTime: Long = 0,
    val lastErrorMessage: String? = null
)

data class AppStatus(
    val debugMode: Boolean = false,
    val uptime: Long = 0
)

data class LogEntry(
    val timestamp: Long,
    val source: String,
    val message: String
)
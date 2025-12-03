package com.example.karooglucometer.monitoring

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.example.karooglucometer.datasource.GlucoseDataSourceManager
import com.example.karooglucometer.ble.GlucoseBluetoothManager
import com.example.karooglucometer.xdrip.XDripContentProvider

/**
 * Comprehensive connection health monitoring for stable glucose data sources
 */
class ConnectionHealthMonitor(private val context: Context) {
    companion object {
        private const val TAG = "ConnectionHealthMonitor"
        
        // Health thresholds
        private const val DATA_FRESHNESS_THRESHOLD_MS = 300000L // 5 minutes
        private const val CONNECTION_TIMEOUT_THRESHOLD_MS = 120000L // 2 minutes
        private const val HEALTH_CHECK_INTERVAL_MS = 30000L // 30 seconds
        private const val MAX_RECONNECT_ATTEMPTS = 5
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Health monitoring state
    private val _healthStatus = MutableStateFlow(HealthStatus())
    val healthStatus: StateFlow<HealthStatus> = _healthStatus.asStateFlow()

    private val _connectionMetrics = MutableStateFlow(ConnectionMetrics())
    val connectionMetrics: StateFlow<ConnectionMetrics> = _connectionMetrics.asStateFlow()

    private var healthJob: Job? = null
    private var dataSourceManager: GlucoseDataSourceManager? = null

    data class HealthStatus(
        val overall: OverallHealth = OverallHealth.UNKNOWN,
        val bleHealth: ConnectionHealth = ConnectionHealth.UNKNOWN,
        val xdripHealth: ConnectionHealth = ConnectionHealth.UNKNOWN,
        val dataFreshness: DataFreshness = DataFreshness.UNKNOWN,
        val lastHealthCheck: Long = 0L,
        val activeSource: String = "UNKNOWN",
        val isStable: Boolean = false
    )

    data class ConnectionMetrics(
        val uptime: Long = 0L,
        val reconnectionAttempts: Int = 0,
        val sourceSwitches: Int = 0,
        val dataGaps: Int = 0,
        val averageLatency: Long = 0L,
        val errorRate: Double = 0.0,
        val lastReconnectTime: Long = 0L,
        val connectionStability: Double = 0.0
    )

    enum class OverallHealth {
        EXCELLENT, GOOD, FAIR, POOR, CRITICAL, UNKNOWN
    }

    enum class ConnectionHealth {
        CONNECTED, CONNECTING, DISCONNECTED, TIMEOUT, ERROR, UNKNOWN
    }

    enum class DataFreshness {
        FRESH, ACCEPTABLE, STALE, CRITICAL, UNKNOWN
    }

    /**
     * Initialize health monitoring with data source manager
     */
    fun initialize(dataSourceManager: GlucoseDataSourceManager) {
        Log.d(TAG, "Initializing connection health monitor")
        this.dataSourceManager = dataSourceManager
        startHealthMonitoring()
    }

    /**
     * Start comprehensive health monitoring
     */
    private fun startHealthMonitoring() {
        healthJob?.cancel()
        healthJob = scope.launch {
            while (isActive) {
                try {
                    performHealthCheck()
                    updateConnectionMetrics()
                    checkForStabilityIssues()
                    
                    delay(HEALTH_CHECK_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in health monitoring", e)
                    delay(HEALTH_CHECK_INTERVAL_MS)
                }
            }
        }
        
        // Monitor data source changes
        dataSourceManager?.let { manager ->
            scope.launch {
                manager.activeDataSource.collect { source ->
                    updateSourceSwitch(source.name)
                }
            }
            
            scope.launch {
                manager.connectionStatus.collect { statusMap ->
                    updateConnectionStatus(statusMap)
                }
            }
            
            scope.launch {
                manager.combinedReadings.collect { readings ->
                    updateDataFreshness(readings)
                }
            }
        }
    }

    /**
     * Perform comprehensive health check
     */
    private suspend fun performHealthCheck() {
        val currentTime = System.currentTimeMillis()
        val manager = dataSourceManager ?: return

        // Check BLE health
        val bleHealth = checkBLEHealth(manager)
        
        // Check xDrip+ health
        val xdripHealth = checkXDripHealth(manager)
        
        // Check data freshness
        val dataFreshness = checkDataFreshness(manager)
        
        // Calculate overall health
        val overallHealth = calculateOverallHealth(bleHealth, xdripHealth, dataFreshness)
        
        // Check stability
        val isStable = checkStability(overallHealth, dataFreshness)
        
        val healthStatus = HealthStatus(
            overall = overallHealth,
            bleHealth = bleHealth,
            xdripHealth = xdripHealth,
            dataFreshness = dataFreshness,
            lastHealthCheck = currentTime,
            activeSource = manager.activeDataSource.value.name,
            isStable = isStable
        )
        
        _healthStatus.value = healthStatus
        
        Log.d(TAG, "Health check: Overall=${overallHealth.name}, BLE=${bleHealth.name}, " +
               "xDrip+=${xdripHealth.name}, Freshness=${dataFreshness.name}, Stable=$isStable")
    }

    /**
     * Check BLE connection health
     */
    private fun checkBLEHealth(manager: GlucoseDataSourceManager): ConnectionHealth {
        val connectionStatus = manager.connectionStatus.value
        val bleConnected = connectionStatus[GlucoseDataSourceManager.DataSource.BLE] == true
        
        return when {
            bleConnected -> {
                val readings = manager.combinedReadings.value
                val bleReadings = readings.filter { it.source == GlucoseDataSourceManager.DataSource.BLE }
                if (bleReadings.isNotEmpty() && 
                    (System.currentTimeMillis() - bleReadings.first().timestamp) < DATA_FRESHNESS_THRESHOLD_MS) {
                    ConnectionHealth.CONNECTED
                } else {
                    ConnectionHealth.TIMEOUT
                }
            }
            else -> ConnectionHealth.DISCONNECTED
        }
    }

    /**
     * Check xDrip+ content provider health
     */
    private fun checkXDripHealth(manager: GlucoseDataSourceManager): ConnectionHealth {
        val connectionStatus = manager.connectionStatus.value
        val xdripConnected = connectionStatus[GlucoseDataSourceManager.DataSource.XDRIP] == true
        
        return when {
            xdripConnected -> {
                val readings = manager.combinedReadings.value
                val xdripReadings = readings.filter { it.source == GlucoseDataSourceManager.DataSource.XDRIP }
                if (xdripReadings.isNotEmpty() && 
                    (System.currentTimeMillis() - xdripReadings.first().timestamp) < DATA_FRESHNESS_THRESHOLD_MS) {
                    ConnectionHealth.CONNECTED
                } else {
                    ConnectionHealth.TIMEOUT
                }
            }
            else -> ConnectionHealth.DISCONNECTED
        }
    }

    /**
     * Check data freshness
     */
    private fun checkDataFreshness(manager: GlucoseDataSourceManager): DataFreshness {
        val latestReading = manager.getLatestReading()
        if (latestReading == null) return DataFreshness.CRITICAL
        
        val ageMs = System.currentTimeMillis() - latestReading.timestamp
        
        return when {
            ageMs < 60000L -> DataFreshness.FRESH // < 1 minute
            ageMs < 300000L -> DataFreshness.ACCEPTABLE // < 5 minutes
            ageMs < 900000L -> DataFreshness.STALE // < 15 minutes
            else -> DataFreshness.CRITICAL // > 15 minutes
        }
    }

    /**
     * Calculate overall health score
     */
    private fun calculateOverallHealth(
        bleHealth: ConnectionHealth,
        xdripHealth: ConnectionHealth,
        dataFreshness: DataFreshness
    ): OverallHealth {
        val healthScore = calculateHealthScore(bleHealth, xdripHealth, dataFreshness)
        
        return when {
            healthScore >= 90 -> OverallHealth.EXCELLENT
            healthScore >= 75 -> OverallHealth.GOOD
            healthScore >= 50 -> OverallHealth.FAIR
            healthScore >= 25 -> OverallHealth.POOR
            else -> OverallHealth.CRITICAL
        }
    }

    /**
     * Calculate numeric health score (0-100)
     */
    private fun calculateHealthScore(
        bleHealth: ConnectionHealth,
        xdripHealth: ConnectionHealth,
        dataFreshness: DataFreshness
    ): Int {
        val bleScore = when (bleHealth) {
            ConnectionHealth.CONNECTED -> 50
            ConnectionHealth.CONNECTING -> 25
            ConnectionHealth.TIMEOUT -> 15
            else -> 0
        }
        
        val xdripScore = when (xdripHealth) {
            ConnectionHealth.CONNECTED -> 50
            ConnectionHealth.TIMEOUT -> 25
            else -> 0
        }
        
        val freshnessScore = when (dataFreshness) {
            DataFreshness.FRESH -> 30
            DataFreshness.ACCEPTABLE -> 20
            DataFreshness.STALE -> 10
            else -> 0
        }
        
        // Take the best available connection + freshness
        return maxOf(bleScore, xdripScore) + freshnessScore
    }

    /**
     * Check connection stability
     */
    private fun checkStability(overallHealth: OverallHealth, dataFreshness: DataFreshness): Boolean {
        val currentMetrics = _connectionMetrics.value
        
        return overallHealth in listOf(OverallHealth.EXCELLENT, OverallHealth.GOOD) &&
               dataFreshness in listOf(DataFreshness.FRESH, DataFreshness.ACCEPTABLE) &&
               currentMetrics.errorRate < 10.0 &&
               currentMetrics.reconnectionAttempts < MAX_RECONNECT_ATTEMPTS
    }

    /**
     * Update connection metrics
     */
    private fun updateConnectionMetrics() {
        val current = _connectionMetrics.value
        val currentTime = System.currentTimeMillis()
        
        // Calculate connection stability percentage
        val stability = when (_healthStatus.value.overall) {
            OverallHealth.EXCELLENT -> 100.0
            OverallHealth.GOOD -> 85.0
            OverallHealth.FAIR -> 65.0
            OverallHealth.POOR -> 35.0
            OverallHealth.CRITICAL -> 10.0
            OverallHealth.UNKNOWN -> 0.0
        }
        
        val updatedMetrics = current.copy(
            uptime = if (current.uptime == 0L) currentTime else current.uptime,
            connectionStability = stability,
            lastReconnectTime = if (current.reconnectionAttempts > 0) currentTime else current.lastReconnectTime
        )
        
        _connectionMetrics.value = updatedMetrics
    }

    /**
     * Update source switch tracking
     */
    private fun updateSourceSwitch(newSource: String) {
        if (_healthStatus.value.activeSource != newSource && _healthStatus.value.activeSource != "UNKNOWN") {
            val current = _connectionMetrics.value
            _connectionMetrics.value = current.copy(sourceSwitches = current.sourceSwitches + 1)
            Log.d(TAG, "Data source switched from ${_healthStatus.value.activeSource} to $newSource")
        }
    }

    /**
     * Update connection status changes
     */
    private fun updateConnectionStatus(statusMap: Map<GlucoseDataSourceManager.DataSource, Boolean>) {
        // Track connection changes for metrics
        val bleConnected = statusMap[GlucoseDataSourceManager.DataSource.BLE] == true
        val xdripConnected = statusMap[GlucoseDataSourceManager.DataSource.XDRIP] == true
        
        // If neither source is connected, increment reconnection attempts
        if (!bleConnected && !xdripConnected) {
            val current = _connectionMetrics.value
            _connectionMetrics.value = current.copy(reconnectionAttempts = current.reconnectionAttempts + 1)
        }
    }

    /**
     * Update data freshness tracking
     */
    private fun updateDataFreshness(readings: List<GlucoseDataSourceManager.GlucoseReading>) {
        if (readings.isEmpty()) {
            val current = _connectionMetrics.value
            _connectionMetrics.value = current.copy(dataGaps = current.dataGaps + 1)
        }
    }

    /**
     * Check for stability issues and take corrective action
     */
    private suspend fun checkForStabilityIssues() {
        val health = _healthStatus.value
        val metrics = _connectionMetrics.value
        
        // If stability is poor, attempt to improve
        if (!health.isStable || health.overall == OverallHealth.CRITICAL) {
            Log.w(TAG, "Stability issues detected, attempting corrective action")
            
            // Refresh data sources
            dataSourceManager?.refreshAllSources()
            
            // If too many reconnection attempts, reset
            if (metrics.reconnectionAttempts > MAX_RECONNECT_ATTEMPTS) {
                Log.w(TAG, "Too many reconnection attempts, resetting connection tracking")
                _connectionMetrics.value = metrics.copy(reconnectionAttempts = 0)
            }
        }
    }

    /**
     * Get formatted health report
     */
    fun getHealthReport(): String {
        val health = _healthStatus.value
        val metrics = _connectionMetrics.value
        
        return buildString {
            appendLine("=== Connection Health Report ===")
            appendLine("Overall Health: ${health.overall.name}")
            appendLine("BLE Status: ${health.bleHealth.name}")
            appendLine("xDrip+ Status: ${health.xdripHealth.name}")
            appendLine("Data Freshness: ${health.dataFreshness.name}")
            appendLine("Active Source: ${health.activeSource}")
            appendLine("Stability: ${if (health.isStable) "STABLE" else "UNSTABLE"}")
            appendLine()
            appendLine("=== Connection Metrics ===")
            appendLine("Uptime: ${(System.currentTimeMillis() - metrics.uptime) / 60000} minutes")
            appendLine("Reconnection Attempts: ${metrics.reconnectionAttempts}")
            appendLine("Source Switches: ${metrics.sourceSwitches}")
            appendLine("Data Gaps: ${metrics.dataGaps}")
            appendLine("Connection Stability: ${metrics.connectionStability.toInt()}%")
            appendLine("Error Rate: ${metrics.errorRate}%")
        }
    }

    /**
     * Get health status for debug overlay
     */
    fun getHealthForDebugOverlay(): Map<String, String> {
        val health = _healthStatus.value
        val metrics = _connectionMetrics.value
        
        return mapOf(
            "overall_health" to health.overall.name,
            "ble_health" to health.bleHealth.name,
            "xdrip_health" to health.xdripHealth.name,
            "data_freshness" to health.dataFreshness.name,
            "stability" to if (health.isStable) "STABLE" else "UNSTABLE",
            "uptime" to "${(System.currentTimeMillis() - metrics.uptime) / 60000}m",
            "reconnections" to metrics.reconnectionAttempts.toString(),
            "source_switches" to metrics.sourceSwitches.toString(),
            "stability_score" to "${metrics.connectionStability.toInt()}%",
            "error_rate" to "${metrics.errorRate.toInt()}%"
        )
    }

    /**
     * Stop health monitoring
     */
    fun stop() {
        Log.d(TAG, "Stopping connection health monitor")
        healthJob?.cancel()
        healthJob = null
    }

    /**
     * Cleanup resources
     */
    fun destroy() {
        stop()
        scope.cancel()
    }
}
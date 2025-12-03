package com.example.karooglucometer.datasource

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.example.karooglucometer.ble.GlucoseBluetoothManager
import com.example.karooglucometer.xdrip.XDripContentProvider

/**
 * Intelligent data source switching between onboard xDrip+ and BLE GATT
 */
class GlucoseDataSourceManager(private val context: Context) {
    companion object {
        private const val TAG = "GlucoseDataSourceManager"
        private const val XDRIP_TIMEOUT_MS = 60000L // 1 minute without xDrip+ data before switching to BLE
        private const val BLE_TIMEOUT_MS = 120000L // 2 minutes without BLE data before switching to xDrip+
    }

    // Data source components
    private val bluetoothManager = GlucoseBluetoothManager(context)
    private val xdripProvider = XDripContentProvider(context)
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Current data source state
    private val _activeDataSource = MutableStateFlow(DataSource.XDRIP)
    val activeDataSource: StateFlow<DataSource> = _activeDataSource.asStateFlow()

    private val _combinedReadings = MutableStateFlow<List<GlucoseReading>>(emptyList())
    val combinedReadings: StateFlow<List<GlucoseReading>> = _combinedReadings.asStateFlow()

    private val _connectionStatus = MutableStateFlow<Map<DataSource, Boolean>>(mapOf(
        DataSource.XDRIP to false,
        DataSource.BLE to false
    ))
    val connectionStatus: StateFlow<Map<DataSource, Boolean>> = _connectionStatus.asStateFlow()

    enum class DataSource {
        XDRIP, BLE, AUTO
    }

    data class GlucoseReading(
        val value: Double,
        val timestamp: Long,
        val unit: String = "mg/dL",
        val trend: String = "Unknown",
        val source: DataSource,
        val delta: Double = 0.0
    )

    private var switchingJob: Job? = null

    init {
        setupDataSourceMonitoring()
    }

    /**
     * Start glucose monitoring with automatic source switching
     */
    fun startMonitoring(preferredSource: DataSource = DataSource.AUTO) {
        Log.d(TAG, "Starting glucose monitoring with preferred source: $preferredSource")

        when (preferredSource) {
            DataSource.XDRIP -> {
                _activeDataSource.value = DataSource.XDRIP
                startXDripMonitoring()
                startFallbackBLE()
            }
            DataSource.BLE -> {
                _activeDataSource.value = DataSource.BLE
                startBLEMonitoring()
                startFallbackXDrip()
            }
            DataSource.AUTO -> {
                // Start with xDrip+ (usually more reliable if available)
                _activeDataSource.value = DataSource.XDRIP
                startXDripMonitoring()
                startBLEMonitoring()
                startIntelligentSwitching()
            }
        }
    }

    /**
     * Stop all monitoring
     */
    fun stopMonitoring() {
        Log.d(TAG, "Stopping glucose monitoring")
        
        switchingJob?.cancel()
        bluetoothManager.disconnect()
        xdripProvider.stopMonitoring()
        
        _connectionStatus.value = mapOf(
            DataSource.XDRIP to false,
            DataSource.BLE to false
        )
    }

    /**
     * Manually switch data source
     */
    fun switchDataSource(newSource: DataSource) {
        if (newSource == DataSource.AUTO) {
            startIntelligentSwitching()
            return
        }

        Log.d(TAG, "Manually switching data source to: $newSource")
        _activeDataSource.value = newSource
        
        when (newSource) {
            DataSource.XDRIP -> {
                if (!xdripProvider.isAvailable.value) {
                    startXDripMonitoring()
                }
            }
            DataSource.BLE -> {
                if (bluetoothManager.connectionState.value == GlucoseBluetoothManager.BluetoothGattState.DISCONNECTED) {
                    bluetoothManager.startScan()
                }
            }
            else -> {} // Already handled above
        }
    }

    /**
     * Get latest reading from active source
     */
    fun getLatestReading(): GlucoseReading? {
        return _combinedReadings.value.firstOrNull()
    }

    private fun setupDataSourceMonitoring() {
        // Monitor xDrip+ readings
        scope.launch {
            xdripProvider.glucoseReadings.collect { xdripReadings ->
                if (xdripReadings.isNotEmpty()) {
                    updateConnectionStatus(DataSource.XDRIP, true)
                    
                    if (_activeDataSource.value == DataSource.XDRIP) {
                        val convertedReadings = xdripReadings.map { reading ->
                            GlucoseReading(
                                value = reading.value,
                                timestamp = reading.timestamp,
                                unit = "mg/dL",
                                trend = reading.direction,
                                source = DataSource.XDRIP,
                                delta = reading.delta
                            )
                        }
                        _combinedReadings.value = convertedReadings
                        Log.d(TAG, "Updated readings from xDrip+: ${convertedReadings.size} readings")
                    }
                }
            }
        }

        // Monitor BLE readings
        scope.launch {
            bluetoothManager.glucoseReadings.collect { bleReadings ->
                if (bleReadings.isNotEmpty()) {
                    updateConnectionStatus(DataSource.BLE, true)
                    
                    if (_activeDataSource.value == DataSource.BLE) {
                        val convertedReadings = bleReadings.map { reading ->
                            GlucoseReading(
                                value = reading.value,
                                timestamp = reading.timestamp,
                                unit = reading.unit,
                                trend = reading.trend,
                                source = DataSource.BLE,
                                delta = 0.0
                            )
                        }
                        _combinedReadings.value = convertedReadings
                        Log.d(TAG, "Updated readings from BLE: ${convertedReadings.size} readings")
                    }
                }
            }
        }

        // Monitor connection states
        scope.launch {
            bluetoothManager.connectionState.collect { state ->
                val isConnected = state == GlucoseBluetoothManager.BluetoothGattState.CONNECTED
                updateConnectionStatus(DataSource.BLE, isConnected)
            }
        }

        scope.launch {
            xdripProvider.isAvailable.collect { isAvailable ->
                updateConnectionStatus(DataSource.XDRIP, isAvailable)
            }
        }
    }

    private fun startXDripMonitoring() {
        Log.d(TAG, "Starting xDrip+ monitoring")
        xdripProvider.startMonitoring()
    }

    private fun startBLEMonitoring() {
        Log.d(TAG, "Starting BLE monitoring")
        bluetoothManager.startScan()
    }

    private fun startFallbackXDrip() {
        scope.launch {
            delay(BLE_TIMEOUT_MS)
            if (_connectionStatus.value[DataSource.BLE] == false && 
                _connectionStatus.value[DataSource.XDRIP] == true) {
                Log.d(TAG, "BLE timeout, switching to xDrip+")
                _activeDataSource.value = DataSource.XDRIP
                startXDripMonitoring()
            }
        }
    }

    private fun startFallbackBLE() {
        scope.launch {
            delay(XDRIP_TIMEOUT_MS)
            if (_connectionStatus.value[DataSource.XDRIP] == false && 
                _connectionStatus.value[DataSource.BLE] != true) {
                Log.d(TAG, "xDrip+ timeout, switching to BLE")
                _activeDataSource.value = DataSource.BLE
                startBLEMonitoring()
            }
        }
    }

    private fun startIntelligentSwitching() {
        switchingJob?.cancel()
        switchingJob = scope.launch {
            while (isActive) {
                delay(30000L) // Check every 30 seconds
                
                val xdripConnected = _connectionStatus.value[DataSource.XDRIP] == true
                val bleConnected = _connectionStatus.value[DataSource.BLE] == true
                val currentSource = _activeDataSource.value
                
                // Get latest reading timestamps
                val xdripLastUpdate = xdripProvider.lastUpdate.value
                val bleLastReading = bluetoothManager.glucoseReadings.value.firstOrNull()?.timestamp ?: 0L
                val currentTime = System.currentTimeMillis()
                
                // Switch logic
                when {
                    // xDrip+ is available and recent, prefer it
                    xdripConnected && (currentTime - xdripLastUpdate) < XDRIP_TIMEOUT_MS -> {
                        if (currentSource != DataSource.XDRIP) {
                            Log.d(TAG, "Intelligent switch to xDrip+ (recent data available)")
                            _activeDataSource.value = DataSource.XDRIP
                        }
                    }
                    // xDrip+ data is stale but BLE is connected
                    bleConnected && (currentTime - xdripLastUpdate) > XDRIP_TIMEOUT_MS -> {
                        if (currentSource != DataSource.BLE) {
                            Log.d(TAG, "Intelligent switch to BLE (xDrip+ data stale)")
                            _activeDataSource.value = DataSource.BLE
                        }
                    }
                    // Neither source has recent data, try to restart
                    (currentTime - xdripLastUpdate) > XDRIP_TIMEOUT_MS && 
                    (currentTime - bleLastReading) > BLE_TIMEOUT_MS -> {
                        Log.w(TAG, "Both sources stale, attempting to restart")
                        xdripProvider.refresh()
                        if (!bluetoothManager.isScanning.value) {
                            bluetoothManager.startScan()
                        }
                    }
                }
            }
        }
    }

    private fun updateConnectionStatus(source: DataSource, connected: Boolean) {
        val currentStatus = _connectionStatus.value.toMutableMap()
        currentStatus[source] = connected
        _connectionStatus.value = currentStatus
    }

    /**
     * Force refresh all data sources
     */
    fun refreshAllSources() {
        Log.d(TAG, "Refreshing all data sources")
        xdripProvider.refresh()
        if (!bluetoothManager.isScanning.value) {
            bluetoothManager.startScan()
        }
    }

    /**
     * Get connection summary for debugging
     */
    fun getConnectionSummary(): String {
        val xdripStatus = if (_connectionStatus.value[DataSource.XDRIP] == true) "Connected" else "Disconnected"
        val bleStatus = if (_connectionStatus.value[DataSource.BLE] == true) "Connected" else "Disconnected"
        val activeSource = _activeDataSource.value
        val readingCount = _combinedReadings.value.size
        
        return "Active: $activeSource | xDrip+: $xdripStatus | BLE: $bleStatus | Readings: $readingCount"
    }

    /**
     * Cleanup resources
     */
    fun destroy() {
        stopMonitoring()
        bluetoothManager.destroy()
        xdripProvider.destroy()
        scope.cancel()
    }
}
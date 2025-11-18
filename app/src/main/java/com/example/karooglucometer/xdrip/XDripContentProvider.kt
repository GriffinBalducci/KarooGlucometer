package com.example.karooglucometer.xdrip

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Local xDrip+ content provider access for onboard glucose readings
 */
class XDripContentProvider(private val context: Context) {
    companion object {
        private const val TAG = "XDripContentProvider"
        
        // xDrip+ content provider URIs
        private val XDRIP_URI = Uri.parse("content://com.eveningoutpost.dexdrip.BgReadings")
        private val XDRIP_TREATMENTS_URI = Uri.parse("content://com.eveningoutpost.dexdrip.Treatments")
        
        private const val QUERY_LIMIT = 20
        private const val UPDATE_INTERVAL_MS = 30000L // 30 seconds
    }

    private val contentResolver: ContentResolver = context.contentResolver
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var updateJob: Job? = null

    // State flows for reactive UI
    private val _glucoseReadings = MutableStateFlow<List<XDripReading>>(emptyList())
    val glucoseReadings: StateFlow<List<XDripReading>> = _glucoseReadings.asStateFlow()

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _lastUpdate = MutableStateFlow(0L)
    val lastUpdate: StateFlow<Long> = _lastUpdate.asStateFlow()

    data class XDripReading(
        val value: Double,
        val timestamp: Long,
        val direction: String = "Unknown",
        val delta: Double = 0.0,
        val source: String = "xDrip+"
    )

    /**
     * Start monitoring xDrip+ for glucose readings
     */
    fun startMonitoring() {
        Log.d(TAG, "Starting xDrip+ content provider monitoring")
        
        // Check if xDrip+ is available
        checkXDripAvailability()
        
        if (_isAvailable.value) {
            // Initial load
            loadGlucoseReadings()
            
            // Start periodic updates
            updateJob = scope.launch {
                while (isActive) {
                    delay(UPDATE_INTERVAL_MS)
                    loadGlucoseReadings()
                }
            }
        }
    }

    /**
     * Stop monitoring
     */
    fun stopMonitoring() {
        Log.d(TAG, "Stopping xDrip+ content provider monitoring")
        updateJob?.cancel()
        updateJob = null
    }

    /**
     * Check if xDrip+ content provider is available
     */
    private fun checkXDripAvailability() {
        try {
            val cursor = contentResolver.query(
                XDRIP_URI,
                arrayOf("_id"), // Just query for ID to test availability
                null,
                null,
                "_id DESC LIMIT 1"
            )
            
            _isAvailable.value = cursor != null
            cursor?.close()
            
            Log.d(TAG, "xDrip+ availability: ${_isAvailable.value}")
        } catch (e: Exception) {
            Log.w(TAG, "xDrip+ not available: ${e.message}")
            _isAvailable.value = false
        }
    }

    /**
     * Load glucose readings from xDrip+ content provider
     */
    private fun loadGlucoseReadings() {
        if (!_isAvailable.value) return

        try {
            val cursor = contentResolver.query(
                XDRIP_URI,
                arrayOf(
                    "_id",
                    "timestamp",
                    "calculated_value",
                    "filtered_calculated_value",
                    "hide_slope",
                    "delta_name"
                ),
                null,
                null,
                "timestamp DESC LIMIT $QUERY_LIMIT"
            )

            cursor?.use { c ->
                val readings = mutableListOf<XDripReading>()
                
                while (c.moveToNext()) {
                    try {
                        val timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp"))
                        val calculatedValue = c.getDouble(c.getColumnIndexOrThrow("calculated_value"))
                        val filteredValue = c.getDouble(c.getColumnIndexOrThrow("filtered_calculated_value"))
                        val hideSlope = c.getInt(c.getColumnIndexOrThrow("hide_slope"))
                        val deltaName = c.getString(c.getColumnIndexOrThrow("delta_name")) ?: "Unknown"
                        
                        // Use filtered value if available, otherwise calculated value
                        val glucoseValue = if (filteredValue > 0) filteredValue else calculatedValue
                        
                        if (glucoseValue > 0) {
                            val reading = XDripReading(
                                value = glucoseValue,
                                timestamp = timestamp,
                                direction = deltaName,
                                delta = calculateDelta(readings.lastOrNull()?.value ?: 0.0, glucoseValue),
                                source = "xDrip+"
                            )
                            readings.add(reading)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error parsing xDrip+ reading: ${e.message}")
                    }
                }
                
                if (readings.isNotEmpty()) {
                    _glucoseReadings.value = readings
                    _lastUpdate.value = System.currentTimeMillis()
                    
                    Log.d(TAG, "Loaded ${readings.size} xDrip+ readings, latest: ${readings.first().value} mg/dL")
                } else {
                    Log.w(TAG, "No valid xDrip+ readings found")
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied accessing xDrip+ content provider: ${e.message}")
            _isAvailable.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Error loading xDrip+ readings: ${e.message}")
        }
    }

    /**
     * Calculate delta between readings
     */
    private fun calculateDelta(previousValue: Double, currentValue: Double): Double {
        return if (previousValue > 0) currentValue - previousValue else 0.0
    }

    /**
     * Get the latest glucose reading
     */
    fun getLatestReading(): XDripReading? {
        return _glucoseReadings.value.firstOrNull()
    }

    /**
     * Force refresh readings
     */
    fun refresh() {
        scope.launch {
            checkXDripAvailability()
            if (_isAvailable.value) {
                loadGlucoseReadings()
            }
        }
    }

    /**
     * Cleanup resources
     */
    fun destroy() {
        stopMonitoring()
        scope.cancel()
    }
}
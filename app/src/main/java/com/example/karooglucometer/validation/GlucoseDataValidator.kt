package com.example.karooglucometer.validation

import android.util.Log
import com.example.karooglucometer.datasource.GlucoseDataSourceManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.abs
import kotlin.math.pow

/**
 * Comprehensive data validation and quality monitoring for glucose readings
 */
class GlucoseDataValidator {
    companion object {
        private const val TAG = "GlucoseDataValidator"
        
        // Validation thresholds
        private const val MIN_GLUCOSE_VALUE = 40.0  // mg/dL
        private const val MAX_GLUCOSE_VALUE = 600.0 // mg/dL
        private const val MAX_RATE_OF_CHANGE = 10.0 // mg/dL per minute
        private const val OUTLIER_THRESHOLD = 3.0   // Standard deviations
        private const val MINIMUM_DATA_POINTS = 3   // For trend analysis
        
        // Data quality thresholds
        private const val EXCELLENT_QUALITY_THRESHOLD = 95.0
        private const val GOOD_QUALITY_THRESHOLD = 85.0
        private const val POOR_QUALITY_THRESHOLD = 50.0
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Validation state
    private val _validationResults = MutableStateFlow<ValidationResult>(ValidationResult())
    val validationResults: StateFlow<ValidationResult> = _validationResults.asStateFlow()
    
    private val _dataQuality = MutableStateFlow<DataQuality>(DataQuality())
    val dataQuality: StateFlow<DataQuality> = _dataQuality.asStateFlow()

    // Historical data for trend analysis
    private val recentReadings = mutableListOf<ValidatedReading>()
    private val maxHistorySize = 50

    data class ValidationResult(
        val isValid: Boolean = true,
        val validationErrors: List<ValidationError> = emptyList(),
        val qualityScore: Double = 100.0,
        val lastValidatedReading: ValidatedReading? = null,
        val totalValidated: Int = 0,
        val totalRejected: Int = 0,
        val rejectionRate: Double = 0.0
    )

    data class DataQuality(
        val overallQuality: QualityLevel = QualityLevel.UNKNOWN,
        val bleQuality: QualityLevel = QualityLevel.UNKNOWN,
        val xdripQuality: QualityLevel = QualityLevel.UNKNOWN,
        val consistencyScore: Double = 100.0,
        val noiseLevel: NoiseLevel = NoiseLevel.LOW,
        val trendReliability: Double = 100.0,
        val lastQualityCheck: Long = 0L
    )

    data class ValidatedReading(
        val originalReading: GlucoseDataSourceManager.GlucoseReading,
        val isValid: Boolean,
        val validationErrors: List<ValidationError>,
        val qualityScore: Double,
        val processedValue: Double, // May be filtered/adjusted
        val confidenceLevel: ConfidenceLevel,
        val validatedAt: Long = System.currentTimeMillis()
    )

    enum class ValidationError {
        OUT_OF_RANGE,
        RATE_OF_CHANGE_TOO_HIGH,
        STATISTICAL_OUTLIER,
        DUPLICATE_TIMESTAMP,
        MISSING_DATA,
        CORRUPTED_DATA,
        SOURCE_MISMATCH,
        STALE_DATA
    }

    enum class QualityLevel {
        EXCELLENT, GOOD, FAIR, POOR, CRITICAL, UNKNOWN
    }

    enum class NoiseLevel {
        LOW, MODERATE, HIGH, EXCESSIVE
    }

    enum class ConfidenceLevel {
        HIGH, MEDIUM, LOW, VERY_LOW
    }

    /**
     * Initialize validator with data source monitoring
     */
    fun initialize(dataSourceManager: GlucoseDataSourceManager) {
        Log.d(TAG, "Initializing glucose data validator")
        
        // Monitor all incoming readings for validation
        scope.launch {
            dataSourceManager.combinedReadings.collect { readings ->
                readings.forEach { reading ->
                    validateReading(reading)
                }
                updateQualityMetrics()
            }
        }
    }

    /**
     * Validate a single glucose reading
     */
    private suspend fun validateReading(reading: GlucoseDataSourceManager.GlucoseReading) {
        val errors = mutableListOf<ValidationError>()
        var qualityScore = 100.0
        
        // 1. Range validation
        if (reading.value < MIN_GLUCOSE_VALUE || reading.value > MAX_GLUCOSE_VALUE) {
            errors.add(ValidationError.OUT_OF_RANGE)
            qualityScore -= 30.0
            Log.w(TAG, "Reading out of range: ${reading.value} mg/dL from ${reading.source}")
        }
        
        // 2. Rate of change validation
        val previousReading = recentReadings.lastOrNull { it.originalReading.source == reading.source }
        if (previousReading != null) {
            val timeDiff = (reading.timestamp - previousReading.originalReading.timestamp) / 60000.0 // minutes
            val valueDiff = abs(reading.value - previousReading.originalReading.value)
            
            if (timeDiff > 0 && (valueDiff / timeDiff) > MAX_RATE_OF_CHANGE) {
                errors.add(ValidationError.RATE_OF_CHANGE_TOO_HIGH)
                qualityScore -= 25.0
                Log.w(TAG, "Rate of change too high: ${valueDiff/timeDiff} mg/dL/min from ${reading.source}")
            }
        }
        
        // 3. Statistical outlier detection
        if (recentReadings.size >= MINIMUM_DATA_POINTS) {
            val sameSourceReadings = recentReadings.filter { 
                it.originalReading.source == reading.source && it.isValid 
            }.takeLast(10)
            
            if (sameSourceReadings.isNotEmpty()) {
                val mean = sameSourceReadings.map { it.originalReading.value }.average()
                val variance = sameSourceReadings.map { (it.originalReading.value - mean).pow(2) }.average()
                val stdDev = kotlin.math.sqrt(variance)
                val zScore = abs(reading.value - mean) / stdDev
                
                if (zScore > OUTLIER_THRESHOLD) {
                    errors.add(ValidationError.STATISTICAL_OUTLIER)
                    qualityScore -= 20.0
                    Log.w(TAG, "Statistical outlier detected: z-score=$zScore from ${reading.source}")
                }
            }
        }
        
        // 4. Duplicate timestamp validation
        val duplicateReading = recentReadings.find { 
            it.originalReading.timestamp == reading.timestamp && 
            it.originalReading.source == reading.source 
        }
        if (duplicateReading != null) {
            errors.add(ValidationError.DUPLICATE_TIMESTAMP)
            qualityScore -= 15.0
            Log.w(TAG, "Duplicate timestamp detected from ${reading.source}")
        }
        
        // 5. Data staleness validation
        val ageMinutes = (System.currentTimeMillis() - reading.timestamp) / 60000.0
        if (ageMinutes > 15) { // More than 15 minutes old
            errors.add(ValidationError.STALE_DATA)
            qualityScore -= 10.0
            Log.w(TAG, "Stale data detected: ${ageMinutes} minutes old from ${reading.source}")
        }
        
        // Create validated reading
        val isValid = errors.isEmpty() || (errors.size <= 1 && qualityScore > 50.0)
        val processedValue = if (isValid) reading.value else filterReading(reading)
        val confidenceLevel = determineConfidenceLevel(qualityScore, errors)
        
        val validatedReading = ValidatedReading(
            originalReading = reading,
            isValid = isValid,
            validationErrors = errors,
            qualityScore = qualityScore,
            processedValue = processedValue,
            confidenceLevel = confidenceLevel
        )
        
        // Add to history
        recentReadings.add(validatedReading)
        if (recentReadings.size > maxHistorySize) {
            recentReadings.removeAt(0)
        }
        
        // Update validation results
        updateValidationResults(validatedReading)
        
        Log.d(TAG, "Validated reading: ${reading.value} mg/dL -> Valid: $isValid, " +
               "Quality: ${qualityScore.toInt()}%, Confidence: $confidenceLevel")
    }

    /**
     * Filter/adjust reading value if needed
     */
    private fun filterReading(reading: GlucoseDataSourceManager.GlucoseReading): Double {
        // Simple filtering - could be enhanced with Kalman filter or other algorithms
        val sameSourceReadings = recentReadings.filter { 
            it.originalReading.source == reading.source && it.isValid 
        }.takeLast(3)
        
        return if (sameSourceReadings.isNotEmpty()) {
            // Use median of recent valid readings
            sameSourceReadings.map { it.originalReading.value }.sorted().let { sorted ->
                sorted[sorted.size / 2]
            }
        } else {
            reading.value
        }
    }

    /**
     * Determine confidence level based on quality score and errors
     */
    private fun determineConfidenceLevel(qualityScore: Double, errors: List<ValidationError>): ConfidenceLevel {
        return when {
            qualityScore >= 95.0 && errors.isEmpty() -> ConfidenceLevel.HIGH
            qualityScore >= 80.0 && errors.size <= 1 -> ConfidenceLevel.MEDIUM
            qualityScore >= 60.0 && errors.size <= 2 -> ConfidenceLevel.LOW
            else -> ConfidenceLevel.VERY_LOW
        }
    }

    /**
     * Update validation results state
     */
    private fun updateValidationResults(newValidation: ValidatedReading) {
        val current = _validationResults.value
        val totalValidated = current.totalValidated + 1
        val totalRejected = current.totalRejected + if (!newValidation.isValid) 1 else 0
        val rejectionRate = if (totalValidated > 0) (totalRejected.toDouble() / totalValidated) * 100.0 else 0.0
        
        val overallQuality = if (recentReadings.isNotEmpty()) {
            recentReadings.takeLast(10).map { it.qualityScore }.average()
        } else 100.0
        
        _validationResults.value = ValidationResult(
            isValid = newValidation.isValid,
            validationErrors = newValidation.validationErrors,
            qualityScore = overallQuality,
            lastValidatedReading = newValidation,
            totalValidated = totalValidated,
            totalRejected = totalRejected,
            rejectionRate = rejectionRate
        )
    }

    /**
     * Update data quality metrics
     */
    private suspend fun updateQualityMetrics() {
        if (recentReadings.isEmpty()) return
        
        // Calculate quality by source
        val bleReadings = recentReadings.filter { it.originalReading.source == GlucoseDataSourceManager.DataSource.BLE }
        val xdripReadings = recentReadings.filter { it.originalReading.source == GlucoseDataSourceManager.DataSource.XDRIP }
        
        val bleQuality = calculateQualityLevel(bleReadings)
        val xdripQuality = calculateQualityLevel(xdripReadings)
        
        // Calculate overall quality
        val overallQuality = calculateOverallQuality(bleQuality, xdripQuality)
        
        // Calculate consistency between sources
        val consistencyScore = calculateConsistencyScore(bleReadings, xdripReadings)
        
        // Calculate noise level
        val noiseLevel = calculateNoiseLevel(recentReadings.takeLast(20))
        
        // Calculate trend reliability
        val trendReliability = calculateTrendReliability(recentReadings.takeLast(10))
        
        _dataQuality.value = DataQuality(
            overallQuality = overallQuality,
            bleQuality = bleQuality,
            xdripQuality = xdripQuality,
            consistencyScore = consistencyScore,
            noiseLevel = noiseLevel,
            trendReliability = trendReliability,
            lastQualityCheck = System.currentTimeMillis()
        )
    }

    /**
     * Calculate quality level for a set of readings
     */
    private fun calculateQualityLevel(readings: List<ValidatedReading>): QualityLevel {
        if (readings.isEmpty()) return QualityLevel.UNKNOWN
        
        val averageQuality = readings.map { it.qualityScore }.average()
        val validPercentage = readings.count { it.isValid }.toDouble() / readings.size * 100.0
        
        val combinedScore = (averageQuality + validPercentage) / 2.0
        
        return when {
            combinedScore >= EXCELLENT_QUALITY_THRESHOLD -> QualityLevel.EXCELLENT
            combinedScore >= GOOD_QUALITY_THRESHOLD -> QualityLevel.GOOD
            combinedScore >= POOR_QUALITY_THRESHOLD -> QualityLevel.FAIR
            combinedScore >= 25.0 -> QualityLevel.POOR
            else -> QualityLevel.CRITICAL
        }
    }

    /**
     * Calculate overall quality from individual source qualities
     */
    private fun calculateOverallQuality(bleQuality: QualityLevel, xdripQuality: QualityLevel): QualityLevel {
        val scores = listOf(
            getQualityScore(bleQuality),
            getQualityScore(xdripQuality)
        ).filter { it > 0 }
        
        if (scores.isEmpty()) return QualityLevel.UNKNOWN
        
        val averageScore = scores.average()
        return getQualityFromScore(averageScore)
    }

    private fun getQualityScore(quality: QualityLevel): Double {
        return when (quality) {
            QualityLevel.EXCELLENT -> 95.0
            QualityLevel.GOOD -> 85.0
            QualityLevel.FAIR -> 65.0
            QualityLevel.POOR -> 35.0
            QualityLevel.CRITICAL -> 15.0
            QualityLevel.UNKNOWN -> 0.0
        }
    }

    private fun getQualityFromScore(score: Double): QualityLevel {
        return when {
            score >= 90 -> QualityLevel.EXCELLENT
            score >= 80 -> QualityLevel.GOOD
            score >= 60 -> QualityLevel.FAIR
            score >= 30 -> QualityLevel.POOR
            score > 0 -> QualityLevel.CRITICAL
            else -> QualityLevel.UNKNOWN
        }
    }

    /**
     * Calculate consistency score between BLE and xDrip+ readings
     */
    private fun calculateConsistencyScore(
        bleReadings: List<ValidatedReading>,
        xdripReadings: List<ValidatedReading>
    ): Double {
        if (bleReadings.isEmpty() || xdripReadings.isEmpty()) return 100.0
        
        // Find readings within similar time windows (±5 minutes)
        val consistencyChecks = mutableListOf<Double>()
        
        bleReadings.takeLast(5).forEach { bleReading ->
            val closeXdripReading = xdripReadings.find { xdripReading ->
                abs(bleReading.originalReading.timestamp - xdripReading.originalReading.timestamp) <= 300000 // 5 minutes
            }
            
            closeXdripReading?.let { xdripReading ->
                val difference = abs(bleReading.originalReading.value - xdripReading.originalReading.value)
                val averageValue = (bleReading.originalReading.value + xdripReading.originalReading.value) / 2.0
                val percentDifference = (difference / averageValue) * 100.0
                
                // Good consistency if difference is < 10%
                val consistencyScore = maxOf(0.0, 100.0 - (percentDifference * 2.0))
                consistencyChecks.add(consistencyScore)
            }
        }
        
        return if (consistencyChecks.isNotEmpty()) {
            consistencyChecks.average()
        } else {
            100.0 // No data to compare
        }
    }

    /**
     * Calculate noise level in the data
     */
    private fun calculateNoiseLevel(readings: List<ValidatedReading>): NoiseLevel {
        if (readings.size < 3) return NoiseLevel.LOW
        
        val values = readings.map { it.originalReading.value }
        val smoothedValues = values.windowed(3) { window -> window.average() }
        
        val noiseSum = values.zip(smoothedValues).sumOf { (original, smoothed) ->
            abs(original - smoothed)
        }
        
        val averageNoise = noiseSum / smoothedValues.size
        
        return when {
            averageNoise <= 2.0 -> NoiseLevel.LOW
            averageNoise <= 5.0 -> NoiseLevel.MODERATE
            averageNoise <= 10.0 -> NoiseLevel.HIGH
            else -> NoiseLevel.EXCESSIVE
        }
    }

    /**
     * Calculate trend reliability
     */
    private fun calculateTrendReliability(readings: List<ValidatedReading>): Double {
        if (readings.size < 3) return 100.0
        
        val validReadings = readings.filter { it.isValid }
        if (validReadings.size < 3) return 50.0
        
        // Check consistency of trend direction
        val trends = validReadings.windowed(2) { (first, second) ->
            when {
                second.originalReading.value > first.originalReading.value + 2 -> 1 // Rising
                second.originalReading.value < first.originalReading.value - 2 -> -1 // Falling
                else -> 0 // Stable
            }
        }
        
        // Calculate trend consistency
        val trendChanges = trends.windowed(2).count { (first, second) -> first != second }
        val maxChanges = trends.size - 1
        
        return if (maxChanges > 0) {
            ((maxChanges - trendChanges).toDouble() / maxChanges) * 100.0
        } else {
            100.0
        }
    }

    /**
     * Get validation summary for debug overlay
     */
    fun getValidationSummary(): Map<String, String> {
        val validation = _validationResults.value
        val quality = _dataQuality.value
        
        return mapOf(
            "overall_quality" to quality.overallQuality.name,
            "ble_quality" to quality.bleQuality.name,
            "xdrip_quality" to quality.xdripQuality.name,
            "rejection_rate" to "${validation.rejectionRate.toInt()}%",
            "consistency" to "${quality.consistencyScore.toInt()}%",
            "noise_level" to quality.noiseLevel.name,
            "trend_reliability" to "${quality.trendReliability.toInt()}%",
            "total_validated" to validation.totalValidated.toString(),
            "total_rejected" to validation.totalRejected.toString()
        )
    }

    /**
     * Get the latest validated reading
     */
    fun getLatestValidatedReading(): ValidatedReading? {
        return recentReadings.lastOrNull { it.isValid }
    }

    /**
     * Get quality report
     */
    fun getQualityReport(): String {
        val validation = _validationResults.value
        val quality = _dataQuality.value
        
        return buildString {
            appendLine("=== Data Quality Report ===")
            appendLine("Overall Quality: ${quality.overallQuality.name}")
            appendLine("BLE Quality: ${quality.bleQuality.name}")
            appendLine("xDrip+ Quality: ${quality.xdripQuality.name}")
            appendLine("Consistency Score: ${quality.consistencyScore.toInt()}%")
            appendLine("Noise Level: ${quality.noiseLevel.name}")
            appendLine("Trend Reliability: ${quality.trendReliability.toInt()}%")
            appendLine()
            appendLine("=== Validation Statistics ===")
            appendLine("Total Validated: ${validation.totalValidated}")
            appendLine("Total Rejected: ${validation.totalRejected}")
            appendLine("Rejection Rate: ${validation.rejectionRate.toInt()}%")
            appendLine("Current Quality Score: ${validation.qualityScore.toInt()}%")
        }
    }

    /**
     * Stop validation monitoring
     */
    fun stop() {
        Log.d(TAG, "Stopping glucose data validator")
        scope.cancel()
    }
}
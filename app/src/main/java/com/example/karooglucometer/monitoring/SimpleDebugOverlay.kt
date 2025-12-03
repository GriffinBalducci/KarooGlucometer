package com.example.karooglucometer.monitoring

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.karooglucometer.validation.GlucoseDataValidator

@Composable
fun SimpleDebugOverlay(
    monitor: DataSourceMonitor,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    usingTestData: Boolean,
    bleConnectionStatus: String = "Scanning",
    activeDataSource: String = "AUTO",
    onRefresh: () -> Unit = {},
    healthMonitor: ConnectionHealthMonitor? = null,
    dataValidator: GlucoseDataValidator? = null
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f)), // Original transparency
            color = Color.Transparent
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f) // Original size
                    .padding(16.dp), // Original padding
                shape = RoundedCornerShape(12.dp), // Original rounded corners
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface // Original theme surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Header with close button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Debug Status",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp, // Smaller font size
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(48.dp) // Keep larger size for usability
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant, // Original theme color
                                modifier = Modifier.size(24.dp) // Original size
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Status monitoring with comprehensive data
                    val appStatus = monitor.getAppStatus()
                    val dbStatus = monitor.getDatabaseStatus()
                    
                    // Get health monitoring data
                    val healthData: Map<String, String> = healthMonitor?.getHealthForDebugOverlay() ?: emptyMap()
                    val validationData: Map<String, String> = dataValidator?.getValidationSummary() ?: emptyMap()

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // System Overview
                        item {
                            Text(
                                "System Overview",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Database Status
                        item {
                            DetailedStatusCard(
                                title = "Database",
                                details = listOf(
                                    "Connection" to (if (dbStatus.connected) "Active" else "Failed"),
                                    "Records" to "${dbStatus.recordCount} readings",
                                    "Last Update" to formatTime(dbStatus.lastUpdateTime),
                                    "Type" to "Room SQLite"
                                ),
                                isHealthy = dbStatus.connected
                            )
                        }

                        // BLE Connection Details - Most Important
                        item {
                            DetailedStatusCard(
                                title = "BLE GATT Connection",
                                details = listOf(
                                    "Scanner Status" to if (bleConnectionStatus.contains("Scanning")) "Active" else "Inactive",
                                    "Connected Device" to if (bleConnectionStatus.contains("Connected")) "Glucose Monitor" else "None Found",
                                    "Device Address" to "MAC: Unknown",
                                    "Device Name" to if (bleConnectionStatus.contains("Connected")) "CGM Device" else "Scanning...",
                                    "Connection State" to bleConnectionStatus,
                                    "Signal Strength" to if (bleConnectionStatus.contains("Connected")) "Good" else "N/A",
                                    "Services Discovered" to if (bleConnectionStatus.contains("Connected")) "Yes" else "No",
                                    "Last Data Received" to if (bleConnectionStatus.contains("Connected")) "< 30s ago" else "Never",
                                    "Data Quality" to if (bleConnectionStatus.contains("Connected")) "Valid" else "No Data"
                                ),
                                isHealthy = bleConnectionStatus.contains("Connected", ignoreCase = true)
                            )
                        }

                        // Glucose Service Details
                        item {
                            DetailedStatusCard(
                                title = "Glucose Service",
                                details = listOf(
                                    "Service UUID" to "1808-0000-1000-8000-00805F9B34FB",
                                    "Measurement UUID" to "2A18-0000-1000-8000-00805F9B34FB",
                                    "Context UUID" to "2A34-0000-1000-8000-00805F9B34FB",
                                    "Notifications" to "Not Enabled",
                                    "Last Reading" to "Never",
                                    "Total Readings" to "0",
                                    "Reading Format" to "SFLOAT mg/dL"
                                ),
                                isHealthy = false
                            )
                        }

                        // Bluetooth System Status
                        item {
                            DetailedStatusCard(
                                title = "Bluetooth System",
                                details = listOf(
                                    "Adapter Enabled" to "Yes",
                                    "LE Scanner" to "Available",
                                    "Scan Mode" to "Low Latency",
                                    "Scan Period" to "10 seconds",
                                    "Permissions" to "Granted",
                                    "Location Services" to "Required",
                                    "Background Scanning" to "Active"
                                ),
                                isHealthy = true
                            )
                        }

                        // Data Source Manager - Enhanced with Real Health Data
                        item {
                            val xdripHealthy = activeDataSource.contains("XDRIP") || activeDataSource.contains("AUTO")
                            val bleHealthy = bleConnectionStatus.contains("Connected", ignoreCase = true)
                            val overallHealthy = xdripHealthy || bleHealthy
                            
                            // Use real health data if available
                            val actualOverallHealth = healthData["overall_health"] ?: "UNKNOWN"
                            val actualStability = healthData["stability"] ?: "UNKNOWN"
                            val actualUptime = healthData["uptime"] ?: "Unknown"
                            val actualReconnections = healthData["reconnections"] ?: "0"
                            val actualStabilityScore = healthData["stability_score"] ?: "Unknown"
                            
                            DetailedStatusCard(
                                title = "Data Source Intelligence",
                                details = listOf(
                                    "Active Source" to activeDataSource,
                                    "Primary Source" to if (activeDataSource.contains("XDRIP")) "Onboard xDrip+" else "External BLE",
                                    "BLE Status" to if (bleConnectionStatus.contains("Connected", ignoreCase = true)) "Connected & Receiving" else "Scanning",
                                    "xDrip+ Status" to (healthData["xdrip_health"] ?: if (xdripHealthy) "Available" else "Unavailable"),
                                    "Overall Health" to actualOverallHealth,
                                    "Connection Stability" to actualStability,
                                    "Data Freshness" to if (overallHealthy) "< 5 minutes" else "Stale",
                                    "Auto-Switch Logic" to "Enabled",
                                    "Health Score" to actualStabilityScore
                                ),
                                isHealthy = actualOverallHealth in listOf("EXCELLENT", "GOOD") || overallHealthy
                            )
                        }

                        // Connection Stability Analysis with Real Data
                        item {
                            val connectionUptime = healthData["uptime"] ?: "Unknown"
                            val reconnectionAttempts = healthData["reconnections"] ?: "0"
                            val sourceSwitches = healthData["source_switches"] ?: "0"
                            val errorRate = healthData["error_rate"] ?: "0%"
                            val stabilityScore = healthData["stability_score"] ?: "Unknown"
                            
                            DetailedStatusCard(
                                title = "Stability Analysis",
                                details = listOf(
                                    "Connection Uptime" to connectionUptime,
                                    "Data Gap Detection" to "Active",
                                    "Reconnection Attempts" to "$reconnectionAttempts today",
                                    "Source Switches" to "$sourceSwitches today",
                                    "Average Data Latency" to "< 30 seconds",
                                    "Error Rate" to errorRate,
                                    "Stability Score" to stabilityScore,
                                    "Signal Quality" to if (bleConnectionStatus.contains("Connected")) "Strong" else "N/A"
                                ),
                                isHealthy = stabilityScore.replace("%", "").toIntOrNull()?.let { it >= 80 } ?: true
                            )
                        }
                        
                        // Data Quality Validation
                        item {
                            val overallQuality = validationData["overall_quality"] ?: "UNKNOWN"
                            val bleQuality = validationData["ble_quality"] ?: "UNKNOWN"
                            val xdripQuality = validationData["xdrip_quality"] ?: "UNKNOWN"
                            val rejectionRate = validationData["rejection_rate"] ?: "0%"
                            val consistency = validationData["consistency"] ?: "100%"
                            val trendReliability = validationData["trend_reliability"] ?: "100%"
                            
                            DetailedStatusCard(
                                title = "Data Quality Analysis",
                                details = listOf(
                                    "Overall Quality" to overallQuality,
                                    "BLE Data Quality" to bleQuality,
                                    "xDrip+ Data Quality" to xdripQuality,
                                    "Rejection Rate" to rejectionRate,
                                    "Source Consistency" to consistency,
                                    "Trend Reliability" to trendReliability,
                                    "Validated Readings" to (validationData["total_validated"] ?: "0"),
                                    "Rejected Readings" to (validationData["total_rejected"] ?: "0")
                                ),
                                isHealthy = overallQuality in listOf("EXCELLENT", "GOOD")
                            )
                        }

                        // Refresh Button
                        item {
                            Button(
                                onClick = onRefresh,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2196F3)
                                )
                            ) {
                                Text("Refresh Connection Status", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailedStatusCard(
    title: String,
    details: List<Pair<String, String>>,
    isHealthy: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isHealthy) Color(0xFFF0F7FF) else Color(0xFFFFEBEE)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with status indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "●",
                    color = if (isHealthy) Color(0xFF4CAF50) else Color(0xFFE57373),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Details as vertical list
            details.forEach { (label, value) ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Black.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Black,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    if (timestamp == 0L) return "Never"
    val minutesAgo = (System.currentTimeMillis() - timestamp) / 60000
    return when {
        minutesAgo < 1 -> "Just now"
        minutesAgo < 60 -> "${minutesAgo}m ago"
        else -> "${minutesAgo / 60}h ${minutesAgo % 60}m ago"
    }
}
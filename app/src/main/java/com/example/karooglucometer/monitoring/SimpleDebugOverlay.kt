package com.example.karooglucometer.monitoring

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.karooglucometer.network.NetworkDetector
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SimpleDebugOverlay(
    monitor: DataSourceMonitor,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    usingTestData: Boolean,
    networkDetector: NetworkDetector,
    currentPhoneIp: String,
    onIpChanged: (String) -> Unit
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

                    // Status monitoring
                    val status by monitor.statusFlow.collectAsState()
                    val logs by monitor.logs.collectAsState()

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // IP Configuration
                        item {
                            var ipInputText by remember { mutableStateOf(currentPhoneIp) }
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFF5F5F5)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        "IP Configuration",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    // Stack vertically on small screens
                                    Column(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedTextField(
                                            value = ipInputText,
                                            onValueChange = { ipInputText = it },
                                            label = { Text("Phone IP Address") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        Button(
                                            onClick = { onIpChanged(ipInputText) },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Apply IP Address")
                                        }
                                    }
                                }
                            }
                        }

                        // System Status List
                        item {
                            Text(
                                "System Status",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        // Database Status
                        item {
                            StatusListItem(
                                title = "Database Connection",
                                status = if (status.database.isConnected) "Connected" else "Disconnected",
                                details = "Records: ${status.database.lastReadCount}",
                                isHealthy = status.database.isConnected
                            )
                        }
                        
                        // HTTP Status
                        item {
                            StatusListItem(
                                title = "HTTP Connection",
                                status = when {
                                    status.http.isAttempting -> "Connecting..."
                                    status.http.lastErrorMessage.isNullOrEmpty() -> "Connected"
                                    else -> "Error"
                                },
                                details = if (usingTestData) "Test Mode" else "Real xDrip",
                                isHealthy = !status.http.isAttempting && status.http.lastErrorMessage.isNullOrEmpty()
                            )
                        }

                        // Network Status
                        item {
                            val networkStatus = remember { networkDetector.getNetworkStatus() }
                            
                            StatusListItem(
                                title = "Network Connection",
                                status = networkStatus.networkType.name.replace("_", " "),
                                details = if (networkStatus.isBluetoothPanActive) 
                                    "Bluetooth PAN Active\nIP: ${networkStatus.bluetoothPanIp ?: "Unknown"}" 
                                else "Standard connection",
                                isHealthy = networkStatus.networkType != com.example.karooglucometer.network.NetworkType.NONE
                            )
                        }

                        // Test Data Status
                        item {
                            StatusListItem(
                                title = "Test Data Mode",
                                status = if (usingTestData) "Enabled" else "Real Mode",
                                details = if (usingTestData) "Using mock glucose data" else "Fetching from xDrip",
                                isHealthy = true
                            )
                        }
                        
                        // App Status  
                        item {
                            StatusListItem(
                                title = "Application",
                                status = if (status.app.debugMode) "Debug Mode" else "Release Mode",
                                details = "Debug mode active",
                                isHealthy = true
                            )
                        }

                        // Karoo Status (simplified)
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFF0F7FF)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp)
                                ) {
                                    Text(
                                        "Karoo Integration",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.Black
                                    )
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Text(
                                        "Service: Active",
                                        fontSize = 12.sp,
                                        color = Color.Black.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        "Data fields: glucose_current, glucose_trend, glucose_time",
                                        fontSize = 11.sp,
                                        color = Color.Black.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }

                        // Recent Activity
                        item {
                            Text(
                                "Recent Activity",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        items(logs.takeLast(8)) { log ->
                            SimpleLogItem(log)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SimpleStatusCard(
    title: String,
    isHealthy: Boolean,
    details: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(80.dp), // Fixed height for consistency
        colors = CardDefaults.cardColors(
            containerColor = if (isHealthy) Color(0xFFE8F5E8) else Color(0xFFFFEBEE) // Color coding
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "●",
                    color = if (isHealthy) Color(0xFF2E7D32) else Color(0xFFD32F2F), // Higher contrast
                    fontSize = 16.sp // Larger indicator
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp, // Larger text for Karoo
                    color = Color.Black // High contrast
                )
            }
            Text(
                details,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 13.sp, // Readable size
                color = Color.Black.copy(alpha = 0.8f) // High contrast
            )
        }
    }
}

@Composable
fun SimpleLogItem(log: LogEntry) {
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF5F5F5)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Source and time on top row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    log.source,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    timeFormat.format(Date(log.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Message on separate line with full width
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                log.message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth() // Allow full width for wrapping
            )
        }
    }
}

@Composable
fun StatusListItem(
    title: String,
    status: String,
    details: String,
    isHealthy: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF5F5F5)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Title at the top
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Status indicator below title
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "●",
                    color = if (isHealthy) Color(0xFF4CAF50) else Color(0xFFE57373),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    status,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            
            // Details at the bottom with full width
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
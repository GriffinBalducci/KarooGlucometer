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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DebugOverlay(
    monitor: DataSourceMonitor,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    usingTestData: Boolean = false,
    networkDetector: com.example.karooglucometer.network.NetworkDetector? = null
) {
    val status by monitor.statusFlow.collectAsState()
    val logs by monitor.logs.collectAsState()
    
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(),
        exit = slideOutVertically()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(8.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .align(Alignment.Center),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Debug Monitor",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                        
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    
                    Divider(modifier = Modifier.padding(vertical = 6.dp))
                    
                    // Everything in a scrollable list
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Status Cards section
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StatusCard(
                                    title = "Database",
                                    isHealthy = status.database.isConnected,
                                    details = "Records: ${status.database.lastReadCount}",
                                    modifier = Modifier.weight(1f)
                                )
                                StatusCard(
                                    title = "Test Data",
                                    isHealthy = status.testData.isEnabled,
                                    details = "Gen: ${status.testData.totalGenerated}",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StatusCard(
                                    title = "HTTP",
                                    isHealthy = status.http.lastSuccessTime > 0 && status.http.lastErrorMessage == null,
                                    details = when {
                                        usingTestData -> "Test Mode"
                                        status.http.isAttempting -> "Connecting..."
                                        status.http.lastSuccessTime > 0 -> "Connected"
                                        status.http.lastErrorMessage != null -> "Failed"
                                        else -> "No Data"
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                StatusCard(
                                    title = "App",
                                    isHealthy = true,
                                    details = "Debug: ${if (status.app.debugMode) "ON" else "OFF"}",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        
                        // Network status (if detector available)
                        networkDetector?.let { detector ->
                            item {
                                var networkStatus by remember { mutableStateOf<com.example.karooglucometer.network.NetworkStatus?>(null) }
                                
                                LaunchedEffect(Unit) {
                                    networkStatus = withContext(Dispatchers.IO) {
                                        detector.getNetworkStatus()
                                    }
                                }
                                
                                networkStatus?.let { netStatus ->
                                    StatusCard(
                                        title = "Network",
                                        isHealthy = netStatus.isBluetoothPanActive,
                                        details = when {
                                            netStatus.isBluetoothPanActive -> "BT PAN (${netStatus.bluetoothPanIp?.takeLast(8) ?: "No IP"})"
                                            netStatus.networkType == com.example.karooglucometer.network.NetworkType.WIFI -> "WiFi"
                                            netStatus.networkType == com.example.karooglucometer.network.NetworkType.CELLULAR -> "Cellular"
                                            else -> "No Network"
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                        
                        // Recent Activity header
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Recent Activity",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        
                        // Logs
                        items(logs) { log ->
                            LogItem(log)
                        }
                    }
                }
            }
        }
    }
}@Composable
fun StatusCard(
    title: String,
    isHealthy: Boolean,
    details: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(70.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHealthy) 
                Color(0xFF4CAF50).copy(alpha = 0.15f) 
            else 
                Color(0xFFF44336).copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
            
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (isHealthy) Color(0xFF4CAF50) else Color(0xFFF44336),
                        shape = RoundedCornerShape(4.dp)
                    )
            )
            
            Text(
                details,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 9.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
fun LogItem(log: LogEntry) {
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                timeFormat.format(Date(log.timestamp)),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(65.dp)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                log.source,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = when(log.source) {
                    "Database" -> Color(0xFF2196F3)
                    "TestData" -> Color(0xFF4CAF50)
                    "HTTP" -> Color(0xFFFF9800)
                    else -> MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.width(75.dp)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                log.message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
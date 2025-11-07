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
                .background(Color.Black.copy(alpha = 0.8f)),
            color = Color.Transparent
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
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
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Debug Status",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = ipInputText,
                                            onValueChange = { ipInputText = it },
                                            label = { Text("IP Address") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                        
                                        Button(
                                            onClick = { onIpChanged(ipInputText) }
                                        ) {
                                            Text("Apply")
                                        }
                                    }
                                }
                            }
                        }

                        // System Status Cards with Better Styling
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SimpleStatusCard(
                                    title = "Database",
                                    isHealthy = status.database.isConnected,
                                    details = "Records: ${status.database.lastReadCount}",
                                    modifier = Modifier.weight(1f)
                                )
                                SimpleStatusCard(
                                    title = "HTTP",
                                    isHealthy = !status.http.isAttempting && status.http.lastErrorMessage.isNullOrEmpty(),
                                    details = when {
                                        status.http.isAttempting -> "Connecting..."
                                        status.http.lastErrorMessage.isNullOrEmpty() -> "Connected"
                                        else -> "Error"
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Network Status Section
                        item {
                            val networkStatus = remember { networkDetector.getNetworkStatus() }
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFF5F5F5)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        "Network Status",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        SimpleStatusCard(
                                            title = "Network Type",
                                            isHealthy = networkStatus.networkType != com.example.karooglucometer.network.NetworkType.NONE,
                                            details = networkStatus.networkType.name.replace("_", " "),
                                            modifier = Modifier.weight(1f)
                                        )
                                        SimpleStatusCard(
                                            title = "Bluetooth PAN",
                                            isHealthy = networkStatus.isBluetoothPanActive,
                                            details = if (networkStatus.isBluetoothPanActive) 
                                                "Active: ${networkStatus.bluetoothPanIp ?: "Unknown IP"}" 
                                            else "Inactive",
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SimpleStatusCard(
                                    title = "Test Data",
                                    isHealthy = usingTestData,
                                    details = if (usingTestData) "Enabled" else "Real Mode",
                                    modifier = Modifier.weight(1f)
                                )
                                SimpleStatusCard(
                                    title = "App",
                                    isHealthy = true,
                                    details = if (status.app.debugMode) "Debug" else "Release",
                                    modifier = Modifier.weight(1f)
                                )
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
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF5F5F5)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
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
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                log.message,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
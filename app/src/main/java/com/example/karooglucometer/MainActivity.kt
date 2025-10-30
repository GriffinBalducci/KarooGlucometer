package com.example.karooglucometer

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.North
import androidx.compose.material.icons.filled.South
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.Room
import com.example.karooglucometer.data.GlucoseDatabase
import com.example.karooglucometer.data.GlucoseReading
import com.example.karooglucometer.monitoring.DataSourceMonitor
import com.example.karooglucometer.monitoring.DebugOverlay
import com.example.karooglucometer.network.ConnectionTester
import com.example.karooglucometer.network.GlucoseFetcher
import com.example.karooglucometer.network.NetworkDetector
import com.example.karooglucometer.testing.TestDataService
import com.example.karooglucometer.ui.theme.KarooGlucometerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {  
    // Only initialize on runtime (for preview compatibility)
    private lateinit var db: GlucoseDatabase
    private lateinit var fetcher: GlucoseFetcher
    private lateinit var monitor: DataSourceMonitor
    private lateinit var networkDetector: NetworkDetector
    private lateinit var connectionTester: ConnectionTester
    private val phoneIp = "10.0.2.2" // Special IP for emulator to access host machine (use real phone IP like "192.168.1.100" for actual device)
    
    // Set to true for testing with mock data, false to force real xDrip fetching
    private val useTestData = false // Change to false when testing with real xDrip
    private val debugMode = BuildConfig.DEBUG
    private val appStartTime = System.currentTimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize monitoring
        monitor = DataSourceMonitor(this)
        networkDetector = NetworkDetector(this)
        connectionTester = ConnectionTester()

        // Create database, and fetcher
        db = Room.databaseBuilder(
            applicationContext,
            GlucoseDatabase::class.java,
            "glucose_db"
        ).build()
        fetcher = GlucoseFetcher(applicationContext)
        
        // Initialize app status monitoring
        monitor.updateAppStatus(debugMode, System.currentTimeMillis() - appStartTime)
        
        // In debug mode with test data enabled, populate with test data for easy testing
        if (debugMode && useTestData) {
            val testDataService = TestDataService(applicationContext, monitor)
            testDataService.populateTestData()
        }

        setContent {
            KarooGlucometerTheme {
                var showDebugOverlay by remember { mutableStateOf(false) }
                var showFullscreenGlucose by remember { mutableStateOf(false) }
                
                Box(modifier = Modifier.fillMaxSize()) {
                    // Render main surface container
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        if (showFullscreenGlucose) {
                            FullscreenGlucoseView(
                                onBack = { showFullscreenGlucose = false }
                            )
                        } else {
                            GlucoseDisplay(
                                onGlucoseClick = { showFullscreenGlucose = true }
                            )
                        }
                    }
                    
                    // Debug overlay (only in debug mode)
                    if (debugMode) {
                        // Debug toggle button
                        FloatingActionButton(
                            onClick = { showDebugOverlay = !showDebugOverlay },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = "Debug Info")
                        }
                        
                        // Debug overlay
                        DebugOverlay(
                            monitor = monitor,
                            isVisible = showDebugOverlay,
                            onDismiss = { showDebugOverlay = false },
                            usingTestData = useTestData,
                            networkDetector = networkDetector
                        )
                    }
                }
            }
        }
    }

    // Glucose display composable
    @Composable
    fun GlucoseDisplay(onGlucoseClick: () -> Unit = {}) {
        val dao = remember { db.glucoseDao() }
        var recent by remember { mutableStateOf(emptyList<GlucoseReading>()) }

        // Background refresh loop (used to update the grid periodically)
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                while (isActive) {
                    try {
                        // Update app uptime
                        monitor.updateAppStatus(debugMode, System.currentTimeMillis() - appStartTime)
                        
                        // Log network status for debugging (helps verify Bluetooth PAN)
                        if (debugMode && !useTestData) {
                            networkDetector.logNetworkStatus()
                        }
                        
                        // Use test data if enabled, otherwise fetch from xDrip
                        if (useTestData) {
                            val testDataService = TestDataService(applicationContext, monitor)
                            testDataService.addSingleTestReading()
                        } else {
                            // Update HTTP status - attempting connection
                            monitor.updateHttpStatus(true, 0, null)
                            
                            try {
                                // First, test basic connectivity (faster than HTTP timeout)
                                val connectionTest = connectionTester.testConnection(phoneIp, 17580, 3000)
                                
                                if (!connectionTest.success) {
                                    // Connection test failed - provide detailed error
                                    throw Exception(connectionTest.errorMessage ?: "Connection test failed")
                                }
                                
                                // Connection test passed, now fetch glucose data
                                fetcher.fetchAndSave(phoneIp)
                                
                                // Update HTTP status - success
                                monitor.updateHttpStatus(false, System.currentTimeMillis(), null)
                            } catch (e: Exception) {
                                // Update HTTP status - error with detailed message
                                Log.e("MainActivity", "Failed to fetch glucose data", e)
                                monitor.updateHttpStatus(false, 0, e.message)
                            }
                        }

                        // Load the 5 most recent readings from the database
                        val updated = dao.getRecent()
                        
                        // Update database status
                        monitor.updateDatabaseStatus(true, updated.size, System.currentTimeMillis())

                        // Push results to UI thread
                        withContext(Dispatchers.Main) { recent = updated }
                    }
                    catch (e: Exception) {
                        Log.e("GlucoseApp", "Error updating DB", e)
                    }

                    // Repeat every 60 seconds
                    delay(60_000L)
                }
            }
        }

        // Modern UI
        ModernGlucoseUI(recent = recent, onGlucoseClick = onGlucoseClick)
    }

    // Modern redesigned UI matching debug overlay style
    @Composable
    fun ModernGlucoseUI(recent: List<GlucoseReading>, onGlucoseClick: () -> Unit) {
        val latest = recent.firstOrNull()
        val previous = recent.getOrNull(1)
        val diff = latest?.glucoseValue?.minus(previous?.glucoseValue ?: latest.glucoseValue) ?: 0
        
        val arrow = when {
            diff > 5 -> Icons.Filled.North
            diff < -5 -> Icons.Filled.South
            else -> Icons.Filled.Check
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Large glucose value card at top (clickable) - FIXED, DOESN'T SCROLL
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onGlucoseClick),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (latest != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "${latest.glucoseValue}",
                                fontSize = 72.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Icon(
                                arrow,
                                contentDescription = "Trend",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "mg/dL",
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        val minutesAgo = (System.currentTimeMillis() - latest.timestamp) / 60000
                        Text(
                            text = "$minutesAgo min ago",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        )
                    } else {
                        Text(
                            text = "No data",
                            fontSize = 32.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Scrollable content below the fixed glucose card
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Graph card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            Text(
                                text = "Glucose Trend",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(modifier = Modifier.fillMaxSize()) {
                                GlucoseLineChart(readings = recent)
                            }
                        }
                    }
                }
                
                // Recent readings header
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Text(
                            text = "Recent Readings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                
                // Individual reading items
                items(recent.size) { index ->
                    GlucoseReadingItem(recent[index])
                }
            }
        }
    }
    
    // Individual reading item in the list
    @Composable
    fun GlucoseReadingItem(reading: GlucoseReading) {
        val minutesAgo = (System.currentTimeMillis() - reading.timestamp) / 60000
        val hoursAgo = minutesAgo / 60
        val timeText = if (hoursAgo > 0) {
            "${hoursAgo}h ${minutesAgo % 60}m ago"
        } else {
            "${minutesAgo}m ago"
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(8.dp)
                )
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${reading.glucoseValue} mg/dL",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = timeText,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
    
    // Full screen glucose view
    @Composable
    fun FullscreenGlucoseView(onBack: () -> Unit) {
        val dao = remember { db.glucoseDao() }
        var recent by remember { mutableStateOf(emptyList<GlucoseReading>()) }
        
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                val readings = dao.getRecent()
                withContext(Dispatchers.Main) {
                    recent = readings
                }
            }
        }
        
        val latest = recent.firstOrNull()
        val previous = recent.getOrNull(1)
        val diff = latest?.glucoseValue?.minus(previous?.glucoseValue ?: latest.glucoseValue) ?: 0
        
        val arrow = when {
            diff > 5 -> Icons.Filled.North
            diff < -5 -> Icons.Filled.South
            else -> Icons.Filled.Check
        }
        
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primaryContainer),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (latest != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "${latest.glucoseValue}",
                            fontSize = 120.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(24.dp))
                        Icon(
                            arrow,
                            contentDescription = "Trend",
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "mg/dL",
                        fontSize = 32.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val minutesAgo = (System.currentTimeMillis() - latest.timestamp) / 60000
                    Text(
                        text = "$minutesAgo minutes ago",
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    )
                } else {
                    Text(
                        text = "No data",
                        fontSize = 48.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            // Back button
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }


    // Line chart composable
    @Composable
    fun GlucoseLineChart(readings: List<GlucoseReading>) {
        val lineColor = MaterialTheme.colorScheme.primary

        Canvas(modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
        ) {
            val points = readings.take(24).sortedBy { it.timestamp }
            if (points.size > 1) {
                val max = points.maxOf { it.glucoseValue }
                val min = points.minOf { it.glucoseValue }
                val range = (max - min).coerceAtLeast(1)
                val xStep = size.width / (points.size - 1)
                val yScale = size.height / range.toFloat()

                for (i in 0 until points.lastIndex) {
                    val x1 = i * xStep
                    val x2 = (i + 1) * xStep
                    val y1 = size.height - (points[i].glucoseValue - min) * yScale
                    val y2 = size.height - (points[i + 1].glucoseValue - min) * yScale

                    drawLine(
                        color = lineColor,
                        start = Offset(x1, y1),
                        end = Offset(x2, y2),
                        strokeWidth = 4f
                    )
                }
            }
        }
    }



    // Will be the main grid layout
    @Composable
    fun GlucoseGrid(recent: List<GlucoseReading>) {
        val latest = recent.firstOrNull() // Latest BG
        val previous = recent.getOrNull(1) // (Latest - 1) BG

        // Calculate the difference between the two - used for trend indicator
        val diff =
            latest?.glucoseValue?.minus(previous?.glucoseValue ?: latest.glucoseValue) ?: 0

        // Determine trend indicator
        val arrow = when {
            diff > 5 -> Icons.Filled.North
            diff < -5 -> Icons.Filled.South
            else -> Icons.Filled.Check
        }

        Row(Modifier.fillMaxSize().padding(8.dp)) {
            // Left column (two stacked boxes)
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Top Left Box --------------------------------------------------------------------
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    // Display latest reading
                    if (latest != null) {
                        Column(Modifier.align(Alignment.Center)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${latest.glucoseValue}",
                                    fontSize = 64.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(arrow, contentDescription = null,
                                    Modifier.size(32.dp))
                            }
                            // Calculate time since latest reading
                            Text("${(System.currentTimeMillis() - latest.timestamp) 
                                    / 60000} min ago")
                        }
                    }
                }
                // Bottom Left Box -----------------------------------------------------------------
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    // Display historical readings
                    Column(Modifier.align(Alignment.CenterStart).padding(8.dp)) {
                        recent.drop(1).forEach {
                            Text("${it.glucoseValue} mg/dL", fontSize = 20.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            // Right large chart box
            Box(
                Modifier
                    .weight(2f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.secondary)
            ) {
                // Display line chart
                GlucoseLineChart(readings = recent)
            }
        }
    }

    // PREVIEWS: -----------------------------------------------------------------------------------
    @Preview(showBackground = true, widthDp = 400, heightDp = 800)
    @Composable
    fun PreviewModernGlucoseUI() {
        val sample = listOf(
            GlucoseReading(id = 1, timestamp = System.currentTimeMillis(), glucoseValue = 125),
            GlucoseReading(id = 2, timestamp = System.currentTimeMillis() - 5 * 60_000, glucoseValue = 118),
            GlucoseReading(id = 3, timestamp = System.currentTimeMillis() - 10 * 60_000, glucoseValue = 112),
            GlucoseReading(id = 4, timestamp = System.currentTimeMillis() - 15 * 60_000, glucoseValue = 108),
            GlucoseReading(id = 5, timestamp = System.currentTimeMillis() - 20 * 60_000, glucoseValue = 102),
            GlucoseReading(id = 6, timestamp = System.currentTimeMillis() - 25 * 60_000, glucoseValue = 98)
        )

        KarooGlucometerTheme {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                ModernGlucoseUI(recent = sample, onGlucoseClick = {})
            }
        }
    }
    
    @Preview(showBackground = true, widthDp = 400, heightDp = 800)
    @Composable
    fun PreviewFullscreenGlucose() {
        KarooGlucometerTheme {
            FullscreenGlucoseView(onBack = {})
        }
    }
}

package com.example.karooglucometer

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.North
import androidx.compose.material.icons.filled.South
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.Room
import com.example.karooglucometer.data.GlucoseDatabase
import com.example.karooglucometer.data.GlucoseReading
import com.example.karooglucometer.monitoring.DataSourceMonitor
import com.example.karooglucometer.monitoring.SimpleDebugOverlay
import com.example.karooglucometer.network.ConnectionTester
import com.example.karooglucometer.network.GlucoseFetcher
import com.example.karooglucometer.network.NetworkDetector
import com.example.karooglucometer.testing.TestDataService
import com.example.karooglucometer.ui.theme.KarooGlucometerTheme
import com.example.karooglucometer.karoo.KarooDataFieldService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import androidx.core.graphics.toColorInt


class MainActivity : ComponentActivity() {
    // Only initialize on runtime (for preview compatibility)
    private lateinit var db: GlucoseDatabase
    private lateinit var fetcher: GlucoseFetcher
    private lateinit var monitor: DataSourceMonitor
    private lateinit var networkDetector: NetworkDetector
    private lateinit var connectionTester: ConnectionTester
    private var karooDataFieldService: KarooDataFieldService? = null
    private var phoneIp by mutableStateOf("10.0.2.2") // Special IP for emulator to access host machine (use real phone IP like "192.168.1.100" for actual device)

    // Set to true for testing with mock data, false to force real xDrip fetching
    private val useTestData = false // Changed to false to enable real connections
    private val debugMode = BuildConfig.DEBUG
    private val appStartTime = System.currentTimeMillis()
    
    // Chart performance optimization - disable complex animations on real device
    private val enableChartAnimations = false // Disabled to improve performance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // This code block ensures upper icons remain black
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = true
        window.statusBarColor = android.graphics.Color.TRANSPARENT

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

        // Start Karoo Data Field Service for ride profile integration
        startKarooDataFieldService()

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
                                .padding(16.dp),
                            containerColor = Color(0xFFF5F5F5)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = "Debug Info")
                        }

                        // Debug overlay
                        SimpleDebugOverlay(
                            monitor = monitor,
                            isVisible = showDebugOverlay,
                            onDismiss = { showDebugOverlay = false },
                            usingTestData = useTestData,
                            networkDetector = networkDetector,
                            currentPhoneIp = phoneIp,
                            onIpChanged = { newIp -> phoneIp = newIp }
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
                            
                            // Explicit Bluetooth PAN verification
                            val networkStatus = networkDetector.getNetworkStatus()
                            Log.d("BluetoothPAN", "=== BLUETOOTH PAN VERIFICATION ===")
                            Log.d("BluetoothPAN", "Network Type: ${networkStatus.networkType}")
                            Log.d("BluetoothPAN", "Is Bluetooth PAN Active: ${networkStatus.isBluetoothPanActive}")
                            Log.d("BluetoothPAN", "Bluetooth PAN IP: ${networkStatus.bluetoothPanIp ?: "None detected"}")
                            
                            if (networkStatus.isBluetoothPanActive) {
                                Log.d("BluetoothPAN", "BLUETOOTH PAN IS WORKING!")
                                Log.d("BluetoothPAN", "Current IP: $phoneIp, Detected BT-PAN IP: ${networkStatus.bluetoothPanIp}")
                            } else {
                                Log.d("BluetoothPAN", "Bluetooth PAN not detected - using ${networkStatus.networkType}")
                            }
                            Log.d("BluetoothPAN", "==============================")
                        }

                        // Use test data if enabled, otherwise fetch from xDrip
                        if (useTestData) {
                            val testDataService = TestDataService(applicationContext, monitor)
                            testDataService.addSingleTestReading()
                        } else {
                            // Update HTTP status - attempting connection
                            monitor.updateHttpStatus(true, 0, null)
                            Log.d("MainActivity", "Starting glucose fetch from $phoneIp")

                            try {
                                // First, test basic connectivity (faster than HTTP timeout)
                                val connectionTest = connectionTester.testConnection(phoneIp, 17580, 3000)

                                if (!connectionTest.success) {
                                    // Connection test failed - provide detailed error
                                    Log.e("MainActivity", "Connection test failed: ${connectionTest.errorMessage}")
                                    throw Exception(connectionTest.errorMessage ?: "Connection test failed")
                                }

                                Log.d("MainActivity", "Connection test passed, fetching glucose data...")
                                // Connection test passed, now fetch glucose data
                                fetcher.fetchAndSave(phoneIp)

                                // Update HTTP status - success
                                monitor.updateHttpStatus(false, System.currentTimeMillis(), null)
                                Log.d("MainActivity", "Glucose fetch successful")
                                
                                // Update Karoo Data Field Service with new glucose reading
                                val latestReading = dao.getRecent().firstOrNull()
                                if (latestReading != null) {
                                    Log.d("MainActivity", "Publishing to Karoo: ${latestReading.glucoseValue} mg/dL")
                                    karooDataFieldService?.updateGlucoseReading(latestReading)
                                }
                            } catch (e: Exception) {
                                // Update HTTP status - error with detailed message
                                Log.e("MainActivity", "Failed to fetch glucose data from $phoneIp", e)
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
            // Top glucose summary card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onGlucoseClick),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF5F5F5)
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

                        val minutesAgo = (System.currentTimeMillis() - latest.timestamp) / 60000
                        Spacer(modifier = Modifier.height(4.dp))
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

            // Scrollable section
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Expanded chart card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF5F5F5)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Glucose Trend (mg/dL)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                GlucoseLineChart(readings = recent)
                            }
                        }
                    }
                }

                // Recent readings list
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
                    Color(0xFFF5F5F5),
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
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }

    // Generates the line chart
    @SuppressLint("UseKtx")
    @Composable
    fun GlucoseLineChart(readings: List<GlucoseReading>) {
        val themePrimary = MaterialTheme.colorScheme.primary.toArgb()  // Onyx
        val themeText = MaterialTheme.colorScheme.onSurface.toArgb()   // Black
        val themeGrid = MaterialTheme.colorScheme.outlineVariant.toArgb()  // Light gray
        val backgroundColor = MaterialTheme.colorScheme.surface.toArgb()  // White

        // Your custom colors for thresholds
        val bloodRed = "#C1121F".toColorInt()    // BloodRed
        val amber = "#FFBF00".toColorInt()       // Amber
        val forestGreen = "#228B22".toColorInt() // Forest_Green

        AndroidView(
            factory = { context ->
                LineChart(context).apply {
                    // Basic chart setup
                    description.isEnabled = false
                    setTouchEnabled(true)
                    isDragEnabled = true
                    setScaleEnabled(true)
                    setPinchZoom(true)
                    setDrawGridBackground(false)
                    setBackgroundColor(backgroundColor)
                    setNoDataText("No glucose data available")

                    // Extra offset for better visibility
                    extraBottomOffset = 10f
                    extraTopOffset = 10f

                    // Legend configuration
                    legend.apply {
                        isEnabled = true
                        textSize = 12f
                        textColor = themeText
                        form = com.github.mikephil.charting.components.Legend.LegendForm.LINE
                        formSize = 12f
                        xEntrySpace = 10f
                        yEntrySpace = 5f
                    }

                    // Right axis - disabled
                    axisRight.isEnabled = false

                    // Left axis - glucose values
                    axisLeft.apply {
                        textColor = themeText
                        textSize = 11f
                        gridColor = themeGrid
                        setDrawGridLines(true)
                        setDrawAxisLine(true)
                        axisLineColor = themeText
                        axisLineWidth = 1.5f

                        // Set reasonable glucose range
                        axisMinimum = 50f
                        axisMaximum = 250f

                        // Add target range limit lines with YOUR colors
                        removeAllLimitLines()

                        // Low threshold (70 mg/dL) - Blood Red
                        addLimitLine(
                            com.github.mikephil.charting.components.LimitLine(70f, "Low").apply {
                                lineWidth = 2f
                                lineColor = bloodRed
                                labelPosition = com.github.mikephil.charting.components.LimitLine.LimitLabelPosition.RIGHT_TOP
                                textSize = 10f
                                textColor = bloodRed
                            }
                        )

                        // High threshold (180 mg/dL) - Amber
                        addLimitLine(
                            com.github.mikephil.charting.components.LimitLine(180f, "High").apply {
                                lineWidth = 2f
                                lineColor = amber
                                labelPosition = com.github.mikephil.charting.components.LimitLine.LimitLabelPosition.RIGHT_BOTTOM
                                textSize = 10f
                                textColor = amber
                            }
                        )

                        // Target zone (125 mg/dL) - Forest Green
                        addLimitLine(
                            com.github.mikephil.charting.components.LimitLine(125f, "Target").apply {
                                lineWidth = 1f
                                lineColor = forestGreen
                                enableDashedLine(10f, 5f, 0f)
                                labelPosition = com.github.mikephil.charting.components.LimitLine.LimitLabelPosition.RIGHT_TOP
                                textSize = 9f
                                textColor = forestGreen
                            }
                        )

                        // Draw limit lines behind data for better visibility
                        setDrawLimitLinesBehindData(true)
                    }

                    // X-Axis - time labels
                    xAxis.apply {
                        position = XAxis.XAxisPosition.BOTTOM
                        textColor = themeText
                        textSize = 10f
                        gridColor = themeGrid
                        setDrawGridLines(true)
                        setDrawAxisLine(true)
                        axisLineColor = themeText
                        axisLineWidth = 1.5f
                        granularity = 1f
                        labelRotationAngle = -45f

                        // Show max 12 labels for readability
                        setLabelCount(12, false)
                    }

                    // Animation - only enable on debug builds for performance
                    if (enableChartAnimations) {
                        animateX(800)
                    }
                }
            },
            update = { chart ->
                if (readings.isEmpty()) {
                    chart.clear()
                    chart.notifyDataSetChanged()
                    chart.invalidate()
                    return@AndroidView
                }

                // Sort by timestamp (oldest to newest for proper line drawing)
                val sorted = readings.sortedBy { it.timestamp }

                // Create entries with sequential x-values
                val entries = sorted.mapIndexed { index, reading ->
                    Entry(index.toFloat(), reading.glucoseValue.toFloat())
                }

                // Create dataset with enhanced styling - ONYX line color
                val dataSet = LineDataSet(entries, "Blood Glucose").apply {
                    // Line appearance - Onyx (your primary color)
                    color = themePrimary
                    lineWidth = 3f

                    // Smooth curve for better visualization
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    cubicIntensity = 0.15f

                    // Circles at data points - Onyx
                    setDrawCircles(true)
                    circleRadius = 5f
                    setCircleColor(themePrimary)
                    circleHoleRadius = 2.5f
                    circleHoleColor = backgroundColor
                    setDrawCircleHole(true)

                    // Fill under the line - Onyx with transparency
                    setDrawFilled(true)
                    fillColor = themePrimary
                    fillAlpha = 40

                    // Value labels on points
                    setDrawValues(true)
                    valueTextSize = 9f
                    valueTextColor = themeText
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return "${value.toInt()}"
                        }
                    }

                    // Highlight settings for touch - Amber highlight
                    highLightColor = amber
                    setDrawHighlightIndicators(true)
                    highlightLineWidth = 1.5f
                }

                // Custom X-axis formatter showing time
                chart.xAxis.valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val idx = value.roundToInt().coerceIn(sorted.indices)
                        val minutesAgo = (System.currentTimeMillis() - sorted[idx].timestamp) / 60000

                        return when {
                            minutesAgo < 60 -> "${minutesAgo}m"
                            minutesAgo < 1440 -> "${minutesAgo / 60}h ${minutesAgo % 60}m"
                            else -> "${minutesAgo / 1440}d"
                        }
                    }
                }

                // Set data and refresh
                chart.data = LineData(dataSet)
                chart.notifyDataSetChanged()
                chart.invalidate()

                // Auto-scale to show all data with some padding
                chart.fitScreen()
            },
            modifier = Modifier.fillMaxSize()
        )
    }

    // PREVIEWS
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
    
    /**
     * Start Karoo Data Field Service for ride profile integration
     */
    private fun startKarooDataFieldService() {
        try {
            val serviceIntent = Intent(this, KarooDataFieldService::class.java)
            val connection = object : android.content.ServiceConnection {
                override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
                    val binder = service as KarooDataFieldService.LocalBinder
                    karooDataFieldService = binder.getService()
                    Log.d("MainActivity", "Connected to Karoo Data Field Service")
                }
                
                override fun onServiceDisconnected(name: android.content.ComponentName?) {
                    karooDataFieldService = null
                    Log.d("MainActivity", "Disconnected from Karoo Data Field Service")
                }
            }
            
            bindService(serviceIntent, connection, BIND_AUTO_CREATE)
            startService(serviceIntent) // Also start as a regular service
            
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start Karoo Data Field Service", e)
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
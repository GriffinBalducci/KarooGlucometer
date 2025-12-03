package com.example.karooglucometer

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
import com.example.karooglucometer.monitoring.ConnectionHealthMonitor
import com.example.karooglucometer.validation.GlucoseDataValidator
import com.example.karooglucometer.adapter.BleDatabaseAdapter
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
    private lateinit var monitor: DataSourceMonitor
    private lateinit var bleAdapter: BleDatabaseAdapter
    private lateinit var healthMonitor: ConnectionHealthMonitor
    private lateinit var dataValidator: GlucoseDataValidator

    // Test data mode for demonstration - can be toggled in debug interface
    private var useTestData by mutableStateOf(false)
    private var needsInitialTestData = false
    private val debugMode = BuildConfig.DEBUG
    private val appStartTime = System.currentTimeMillis()
    
    // Chart performance optimization - disable complex animations on real device
    private val enableChartAnimations = false // Disabled to improve performance

    // Permission request code
    private val PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request BLE permissions first
        requestBluetoothPermissions()

        // This code block ensures upper icons remain black
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = true
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        // Initialize monitoring
        monitor = DataSourceMonitor(this)
        healthMonitor = ConnectionHealthMonitor(this)
        dataValidator = GlucoseDataValidator()
        
        // Initialize BLE adapter
        bleAdapter = BleDatabaseAdapter(this)

        // Create database
        db = Room.databaseBuilder(
            applicationContext,
            GlucoseDatabase::class.java,
            "glucose_db"
        ).build()

        // Initialize app status monitoring
        monitor.updateAppStatus(debugMode, System.currentTimeMillis() - appStartTime)

        // Initialize comprehensive monitoring system
        val dataSourceManager = bleAdapter.getDataSourceManager() // We'll need to expose this
        healthMonitor.initialize(dataSourceManager)
        dataValidator.initialize(dataSourceManager)

        // Start BLE GATT monitoring (only after permissions)
        if (hasBluetoothPermissions()) {
            bleAdapter.startMonitoring()
        }

        setContent {
            MaterialTheme {
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
                            bleConnectionStatus = bleAdapter.getConnectionStatus(),
                            activeDataSource = bleAdapter.getActiveDataSource(),
                            onRefresh = { bleAdapter.refresh() },
                            healthMonitor = healthMonitor,
                            dataValidator = dataValidator
                        )
                    }
                }
            }
        }
    }

    private fun hasBluetoothPermissions(): Boolean {
        val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ permissions
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            // Pre-Android 12 permissions
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        return bluetoothPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBluetoothPermissions() {
        if (hasBluetoothPermissions()) {
            return // Already have permissions
        }

        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        ActivityCompat.requestPermissions(this, permissionsToRequest, PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.i("BLE", "All Bluetooth permissions granted - starting BLE monitoring")
                bleAdapter.startMonitoring()
            } else {
                Log.w("BLE", "Some Bluetooth permissions denied - BLE scanning may not work")
                // Still try to start monitoring, some functionality might work
                bleAdapter.startMonitoring()
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

                        // Generate test data if enabled
                        if (useTestData) {
                            // Generate initial batch if needed
                            if (needsInitialTestData) {
                                generateInitialTestData(dao)
                                needsInitialTestData = false
                            }
                            generateTestGlucoseReading(dao)
                        }

                        // BLE monitoring handles data automatically via bleAdapter
                        
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

                    // Repeat every 10 seconds (faster when using test data)
                    delay(10_000L)
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
            // Top glucose summary card (Karoo optimized)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onGlucoseClick)
                    .height(150.dp), // Karoo: Consistent height
                shape = RoundedCornerShape(8.dp), // Karoo: Less rounded
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF5F5F5) // Original light gray
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
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
                                fontSize = 76.sp, // Karoo: Large but not excessive
                                fontWeight = FontWeight.ExtraBold, // Karoo: Heavy for outdoor visibility
                                color = MaterialTheme.colorScheme.onPrimaryContainer // Original theme color
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
                            .height(290.dp), // Karoo: Slightly smaller
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF5F5F5) // Original light gray
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
    
    // Test data generator for demonstration purposes
    private var lastTestReading: Long = 0
    private var testBaseValue = 120.0 // Starting glucose value
    private var testTrendDirection = 1 // 1 for up, -1 for down
    
    private suspend fun generateTestGlucoseReading(dao: com.example.karooglucometer.data.GlucoseDao) {
        try {
            val currentTime = System.currentTimeMillis()
            
            // Generate new reading every 30 seconds when in test mode for quick demonstration
            if (currentTime - lastTestReading < 30_000) return
            
            // Create realistic glucose fluctuation
            val variation = (kotlin.random.Random.nextDouble() - 0.5) * 10 // ±5 mg/dL variation
            val trend = testTrendDirection * kotlin.random.Random.nextDouble() * 2 // ±2 mg/dL trend
            
            testBaseValue += trend + variation
            
            // Keep values in realistic range (70-300 mg/dL)
            testBaseValue = testBaseValue.coerceIn(70.0, 300.0)
            
            // Change trend direction occasionally
            if (kotlin.random.Random.nextDouble() < 0.1) { // 10% chance
                testTrendDirection *= -1
            }
            
            // Create test reading
            val testReading = GlucoseReading(
                id = 0, // Room will auto-generate
                timestamp = currentTime,
                glucoseValue = testBaseValue.roundToInt()
            )
            
            dao.insert(testReading)
            lastTestReading = currentTime
            
            Log.d("TestData", "Generated test glucose reading: ${testBaseValue.roundToInt()} mg/dL")
            
        } catch (e: Exception) {
            Log.e("TestData", "Error generating test data", e)
        }
    }
    
    // Generate initial batch of test data when test mode is first enabled
    private suspend fun generateInitialTestData(dao: com.example.karooglucometer.data.GlucoseDao) {
        try {
            val currentTime = System.currentTimeMillis()
            val readings = mutableListOf<GlucoseReading>()
            
            // Generate 5 readings going back in time (30 minutes, 25 min, 20 min, 15 min, 10 min, 5 min ago)
            for (i in 6 downTo 1) {
                val timeOffset = i * 5 * 60 * 1000L // 5 minutes each
                val timestamp = currentTime - timeOffset
                
                // Create realistic progression
                val baseValue = 115 + (i * 2) + (kotlin.random.Random.nextDouble() - 0.5) * 8
                val clampedValue = baseValue.coerceIn(70.0, 200.0)
                
                readings.add(GlucoseReading(
                    id = 0,
                    timestamp = timestamp,
                    glucoseValue = clampedValue.roundToInt()
                ))
            }
            
            // Insert all readings
            readings.forEach { dao.insert(it) }
            
            // Update the test baseline to match the last reading
            testBaseValue = readings.last().glucoseValue.toDouble()
            lastTestReading = currentTime - 30_000 // Set to allow immediate next generation
            
            Log.d("TestData", "Generated ${readings.size} initial test readings")
            
        } catch (e: Exception) {
            Log.e("TestData", "Error generating initial test data", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Cleanup monitoring resources
        try {
            healthMonitor.destroy()
            dataValidator.stop()
            bleAdapter.destroy()
        } catch (e: Exception) {
            Log.w("MainActivity", "Error cleaning up monitoring resources", e)
        }
    }
}
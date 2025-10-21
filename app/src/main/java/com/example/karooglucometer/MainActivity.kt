package com.example.karooglucometer

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.North
import androidx.compose.material.icons.filled.South
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.Room
import com.example.karooglucometer.data.GlucoseDatabase
import com.example.karooglucometer.data.GlucoseReading
import com.example.karooglucometer.network.GlucoseFetcher
import com.example.karooglucometer.testing.TestDataService
import com.example.karooglucometer.ui.theme.KarooGlucometerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    // Only initialize on runtime (for preview compatibility)
    private lateinit var db: GlucoseDatabase
    private lateinit var fetcher: GlucoseFetcher
    private val phoneIp = "YOUR_PHONE_IP_HERE" // REPLACE THIS WITH PHONE'S IP
    
    // Set to true for testing with mock data in IDE
    private val debugMode = BuildConfig.DEBUG

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create database, and fetcher
        db = Room.databaseBuilder(
            applicationContext,
            GlucoseDatabase::class.java,
            "glucose_db"
        ).build()
        fetcher = GlucoseFetcher(applicationContext)
        
        // In debug mode, populate with test data for easy testing
        if (debugMode) {
            val testDataService = TestDataService(applicationContext)
            testDataService.populateTestData()
        }

        setContent {
            KarooGlucometerTheme {
                // Render main surface container
                Surface(
                    modifier = Modifier.fillMaxSize(), // Take up whole phone screen
                    color = MaterialTheme.colorScheme.background // Set background color
                ) {
                    // Display glucose data
                    GlucoseDisplay()
                }
            }
        }
    }

    // Glucose display composable
    @Composable
    fun GlucoseDisplay() {
        val dao = remember { db.glucoseDao() }
        var recent by remember { mutableStateOf(emptyList<GlucoseReading>()) }

        // Background refresh loop (used to update the grid periodically)
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                while (isActive) {
                    try {
                        // In debug mode, add mock data instead of fetching from phone
                        if (debugMode) {
                            val testDataService = TestDataService(applicationContext)
                            testDataService.addSingleTestReading()
                        } else {
                            // Fetch new glucose data from the phone and save to Room
                            fetcher.fetchAndSave(phoneIp)
                        }

                        // Load the 5 most recent readings from the database
                        val updated = dao.getRecent()

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

        // Draw the grid
        GlucoseGrid(recent)
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
    @Preview(showBackground = true, widthDp = 600, heightDp = 400)
    @Composable
    fun PreviewGlucoseGrid() {
        val sample = listOf(
            GlucoseReading(id = 1, timestamp = System.currentTimeMillis(), glucoseValue = 125),
            GlucoseReading(id = 2, timestamp = System.currentTimeMillis() - 5 * 60_000, glucoseValue = 118),
            GlucoseReading(id = 3, timestamp = System.currentTimeMillis() - 10 * 60_000, glucoseValue = 112),
            GlucoseReading(id = 4, timestamp = System.currentTimeMillis() - 15 * 60_000, glucoseValue = 108),
            GlucoseReading(id = 5, timestamp = System.currentTimeMillis() - 20 * 60_000, glucoseValue = 102)
        )

        KarooGlucometerTheme {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(8.dp)
            ) {
                GlucoseGrid(recent = sample)
            }
        }
    }
}

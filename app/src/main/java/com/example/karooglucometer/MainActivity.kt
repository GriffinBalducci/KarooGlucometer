package com.example.karooglucometer

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.North
import androidx.compose.material.icons.filled.South
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import com.example.karooglucometer.ui.theme.KarooGlucometerTheme

// GlucoseData object
data class GlucoseData(
    val sgv: String, // JSON key for glucose
    val direction: String,
    val age: Int
)

class MainActivity : ComponentActivity() {
    // Only initialize on runtime (for preview compatibility)
    private lateinit var client: OkHttpClient
    private val phoneIp = "YOUR_PHONE_IP_HERE" // REPLACE THIS WITH PHONE'S IP
    private val url = "http://$phoneIp:17580/sgv.json"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        client = OkHttpClient()

        setContent {
            KarooGlucometerTheme {
                // Render main surface container
                Surface(
                    modifier = Modifier.fillMaxSize(), // Take up whole phone screen
                    color = MaterialTheme.colorScheme.background // Set background color
                ) {
                    // Display glucose data
                    GlucoseDisplay(client, url)
                }
            }
        }
    }

    // This is the "Stateful" Composable that handles data and logic
    @Composable
    fun GlucoseDisplay(client: OkHttpClient, url: String) {
        val glucoseValue = remember { mutableStateOf("---") }
        val minutesAgo = remember { mutableStateOf("-- min ago") }

        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                while (isActive) {
                    try {
                        val request = Request.Builder().url(url).build()
                        val response = client.newCall(request).execute()

                        if (response.isSuccessful) {
                            response.body.string().let { jsonData ->
                                val data = Gson().fromJson(jsonData, GlucoseData::class.java)
                                withContext(Dispatchers.Main) {
                                    glucoseValue.value = data.sgv
                                    minutesAgo.value = "${data.age} min ago"
                                }
                            }
                        } else {
                            Log.e("GlucoseApp", "Network request failed: ${response.code}")
                        }
                    } catch (e: Exception) {
                        Log.e("GlucoseApp", "Error fetching data", e)
                    }
                    delay(30000L)
                }
            }
        }

        GlucoseLayout(
            glucoseValue = glucoseValue.value,
            minutesAgo = minutesAgo.value,
        )
    }

    // Stateless UI layout
    @Composable
    fun GlucoseLayout(glucoseValue: String, minutesAgo: String) {
        val value = glucoseValue.toIntOrNull() ?: -1

        // Decide which icon to show
        val icon = when {
            value in 80..140 -> Icons.Filled.Check // in range
            value in 0..80 -> Icons.Filled.South // low
            value > 140 -> Icons.Filled.North // high
            else -> null
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = glucoseValue,
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary
                )
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = "Glucose Status",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(48.dp)
                            .padding(start = 16.dp)
                    )
                }
            }
            Text(
                text = minutesAgo,
                fontSize = 24.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }

    @Preview(showBackground = true, name = "In Range")
    @Composable
    fun PreviewInRange() {
        KarooGlucometerTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                GlucoseLayout(glucoseValue = "90", minutesAgo = "1 min ago")
            }
        }
    }

    @Preview(showBackground = true, name = "Low")
    @Composable
    fun PreviewLow() {
        KarooGlucometerTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                GlucoseLayout(glucoseValue = "60", minutesAgo = "4 min ago")
            }
        }
    }

    @Preview(showBackground = true, name = "High")
    @Composable
    fun PreviewHigh() {
        KarooGlucometerTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                GlucoseLayout(glucoseValue = "200", minutesAgo = "2 min ago")
            }
        }
    }
}

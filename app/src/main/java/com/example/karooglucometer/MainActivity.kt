package com.example.karooglucometer

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
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
    private val client = OkHttpClient()
    private val phoneIp = "YOUR_PHONE_IP_HERE" // REPLACE THIS WITH PHONE'S IP
    private val url = "http://$phoneIp:17580/sgv.json"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        // State variable that holds the real-time glucose information
        val glucoseValue = remember { mutableStateOf("---") }
        val minutesAgo = remember { mutableStateOf("-- min ago") }
        val trendArrow = remember { mutableStateOf("▲") }

        // Use LaunchedEffect with coroutine to continuously fetch data every 30 seconds
        // Unit key: This ensures the coroutine is launched only once
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                while (isActive) {
                    try {
                        val request = Request.Builder().url(url).build()
                        val response = client.newCall(request).execute()

                        if (response.isSuccessful) {
                            // Take the JSON data from the response body and parse it into a
                            // GlucoseData object
                            response.body?.string()?.let { jsonData ->
                                val data = Gson().fromJson(jsonData, GlucoseData::class.java)

                                // Update the state variables with the new data
                                withContext(Dispatchers.Main) {
                                    glucoseValue.value = data.sgv
                                    minutesAgo.value = "${data.age} min ago"
                                    trendArrow.value = data.direction
                                }
                            }
                        } else {
                            Log.e("GlucoseApp", "Network request failed: ${response.code}")
                        }
                    } catch (e: Exception) {
                        Log.e("GlucoseApp", "Error fetching data", e)
                    }
                    delay(30000L) // Wait 30 seconds before fetching again
                }
            }
        }

        // Pass the data to the stateless layout
        GlucoseLayout(
            glucoseValue = glucoseValue.value,
            minutesAgo = minutesAgo.value,
            trendArrow = trendArrow.value
        )
    }

    // This is the new "Stateless" Composable for the UI layout
    @Composable
    fun GlucoseLayout(glucoseValue: String, minutesAgo: String, trendArrow: String) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = glucoseValue,
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = trendArrow,
                    fontSize = 48.sp,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
            Text(
                text = minutesAgo,
                fontSize = 24.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }

    // Preview: Used for Android Studio DEVELOPMENT Previewing
    @Preview(showBackground = true)
    @Composable
    fun DefaultPreview() {
        KarooGlucometerTheme {
            // Call the stateless layout directly with hardcoded example data
            GlucoseLayout(
                glucoseValue = "105",
                minutesAgo = "5 min ago",
                trendArrow = "↘︎"
            )
        }
    }
}
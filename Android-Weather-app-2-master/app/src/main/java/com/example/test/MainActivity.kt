package com.example.test

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.get
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import com.beust.klaxon.lookup
import com.google.android.material.appbar.CollapsingToolbarLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis

class MainActivity : AppCompatActivity() {

    private lateinit var unitsSwitch: SwitchCompat
    private lateinit var searchView: android.widget.SearchView
    private lateinit var toolbar_layout: CollapsingToolbarLayout
    private lateinit var dateView: TextView
    private lateinit var backgroundImageView: ImageView
    private lateinit var bufferView: ProgressBar
    private lateinit var currentWeatherIcon: ImageView
    private lateinit var currentMaxMinView: TextView
    private lateinit var currentTemperatureView: TextView
    private lateinit var feelsLikeView: TextView
    private lateinit var currentDescriptionView: TextView
    private lateinit var hourlyRecyclerView: RecyclerView
    private lateinit var dailyRecyclerView: RecyclerView
    private lateinit var sunriseTextView: TextView
    private lateinit var sunsetTextView: TextView
    private lateinit var UVIndexTextView: TextView
    private lateinit var humidityTextView: TextView
    private lateinit var windTextView: TextView

    var temperatureUnit = "°C"
    var speedUnit = "m/s"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        unitsSwitch = findViewById(R.id.unitsSwitch)
        searchView = findViewById(R.id.searchView)
        toolbar_layout = findViewById(R.id.toolbar_layout)
        dateView = findViewById(R.id.dateView)
        backgroundImageView = findViewById(R.id.backgroundImageView)
        bufferView = findViewById(R.id.bufferView)
        currentWeatherIcon = findViewById(R.id.currentWeatherIcon)
        currentMaxMinView = findViewById(R.id.currentMaxMinView)
        currentTemperatureView = findViewById(R.id.currentTemperatureView)
        feelsLikeView = findViewById(R.id.feelsLikeView)
        currentDescriptionView = findViewById(R.id.currentDescriptionView)
        hourlyRecyclerView = findViewById(R.id.hourlyRecyclerView)
        dailyRecyclerView = findViewById(R.id.dailyRecyclerView)
        sunriseTextView = findViewById(R.id.sunriseTextView)
        sunsetTextView = findViewById(R.id.sunsetTextView)
        UVIndexTextView = findViewById(R.id.UVIndexTextView)
        humidityTextView = findViewById(R.id.humidityTextView)
        windTextView = findViewById(R.id.windTextView)

        unitsSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                temperatureUnit = "°F"
                speedUnit = "m/h"

                toImperial()
            } else {
                temperatureUnit = "°C"
                speedUnit = "m/s"

                toMetric()
            }
        }

        searchView.setOnQueryTextListener(object : android.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                Log.d("TAG", "Searching weather for $query")
                Toast.makeText(this@MainActivity, "Searching.. please wait", Toast.LENGTH_SHORT)
                    .show()
                CoroutineScope(IO).launch {
                    makeApiRequest(query)
                }
                return false
            }

            override fun onQueryTextChange(p0: String?): Boolean {
                return false
            }
        })

        val executionTime = measureTimeMillis {
            CoroutineScope(IO).launch {
                makeApiRequest("prague")
            }
        }
        Log.d("TAG", "Time required: $executionTime")
    }

    private fun setNewText(
        cityName: String,
        timeZone: String,
        current: JsonObject,
        forecast: JsonObject,
        sunrise: Int,
        sunset: Int
    ) {
        // Collapsing toolbar
        toolbar_layout.title = cityName
        dateView.text = dateTime(current.lookup<Int>("dt")[0], timeZone)

        // Current weather
        bufferView.visibility = View.GONE
        currentWeatherIcon.visibility = View.VISIBLE

        val uri = "@drawable/a${current.lookup<String>("weather.icon")[0]}"
        val imageResource = resources.getIdentifier(uri, null, packageName)
        currentWeatherIcon.setImageResource(imageResource)

        // Daily max/min (take first 8 forecast entries = 24h)
        val maxTemp = forecast.lookup<Double>("list.main.temp_max")
            .take(8).maxOrNull()?.roundToInt() ?: 0
        val minTemp = forecast.lookup<Double>("list.main.temp_min")
            .take(8).minOrNull()?.roundToInt() ?: 0

        currentMaxMinView.text = "$maxTemp° / $minTemp°"

        currentTemperatureView.text =
            "${current.lookup<Double>("main.temp")[0].roundToInt()}$temperatureUnit"

        feelsLikeView.text =
            "Feels like ${current.lookup<Double>("main.feels_like")[0].roundToInt()}°"

        currentDescriptionView.text =
            current.lookup<String>("weather.main")[0]

        // Hourly recycler view
        hourlyRecyclerView.apply {
            val myLayoutManager = LinearLayoutManager(context)
            myLayoutManager.orientation = LinearLayoutManager.HORIZONTAL
            layoutManager = myLayoutManager
            adapter = HourlyAdapter(forecast, timeZone)
        }

        // Daily recycler view
        dailyRecyclerView.apply {
            val myLayoutManager = LinearLayoutManager(context)
            myLayoutManager.orientation = LinearLayoutManager.HORIZONTAL
            layoutManager = myLayoutManager
            adapter = DailyAdapter(forecast, timeZone)
        }

        // Current weather details
        sunriseTextView.text = dateTime(sunrise, timeZone, "K:mm a")
        sunsetTextView.text = dateTime(sunset, timeZone, "K:mm a")

        // UV index not available in these APIs
        UVIndexTextView.text = "--"

        humidityTextView.text =
            "${current.lookup<Int>("main.humidity")[0]}%"

        windTextView.text = "${current.lookup<Double>("wind.speed")[0].roundToInt()} $speedUnit ${
            windDirection(current.lookup<Int>("wind.deg")[0])
        }"

        // Set background picture to night if after sunset
        val date = Date(current.lookup<Int>("dt")[0].toLong() * 1000)
        val sunriseDate = Date(sunrise * 1000L)
        val sunsetDate = Date(sunset * 1000L)

        setBackground(date, sunriseDate, sunsetDate)
    }

    private suspend fun setTextOnMainThread(
        cityName: String,
        timeZone: String,
        current: JsonObject,
        forecast: JsonObject,
        sunrise: Int,
        sunset: Int
    ) {
        withContext(Main) {
            setNewText(cityName, timeZone, current, forecast, sunrise, sunset)
        }
    }

    private suspend fun makeApiRequest(city: String) {
        try {
            val result1 = getResult1FromApi(city)

            val lat: String =
                degreeConversion((result1).lookup<String>("results.annotations.DMS.lat")[0])
            val lon: String =
                degreeConversion((result1).lookup<String>("results.annotations.DMS.lng")[0])

            val result2 = getResult2FromApi(lat, lon)
            val (currentWeather, forecast) = getResult2FromApi(lat, lon)

            val cityName = result1.lookup<String>("results.formatted")[0]
            val timeZone = result1.lookup<String>("results.annotations.timezone.name")[0]

            val sunrise = result1.lookup<Int>("results.annotations.sun.rise.apparent")[0]
            val sunset = result1.lookup<Int>("results.annotations.sun.set.apparent")[0]

            setTextOnMainThread(cityName, timeZone, currentWeather, forecast, sunrise, sunset)
        } catch (exception: java.lang.Exception) {
            withContext(Main) {
                Toast.makeText(
                    this@MainActivity,
                    "Oops.. that didn't work out. Please try again",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private suspend fun getResult1FromApi(city: String): JsonObject {
        Log.d("TAG", "Getting result 1")
        return stringToJSON(Geo().makeRequest(Geo().makeUrl(URLEncoder.encode(city, "utf-8"))))
    }

    private suspend fun getResult2FromApi(lat: String, lon: String): Pair<JsonObject, JsonObject> {
        Log.d("TAG", "Getting weather + forecast")

        val weather = Weather(lat, lon)

        val currentWeatherJson = weather.makeRequest(weather.currentWeatherUrl())
        val forecastJson = weather.makeRequest(weather.forecastUrl())

        val current = stringToJSON(currentWeatherJson)
        val forecast = stringToJSON(forecastJson)

        return Pair(current, forecast)
    }

    private fun stringToJSON(string: String): JsonObject {
        val parser: Parser = Parser.default()
        val stringBuilder: StringBuilder = StringBuilder(string)
        return parser.parse(stringBuilder) as JsonObject
    }

    private fun degreeConversion(deg: String): String {
        val direction: Map<String, Int> = mapOf(" N" to 1, " S" to -1, " E" to 1, " W" to -1)
        val new = deg.replace('°', ' ').replace('\'', ' ').replace("\'\'", " ")
        val newList = new.split("  ")
        return (newList[0].toInt() + newList[1].toInt() / 60.0 * direction.getValue(newList[3])).round(
            3
        ).toString()
    }

    private fun Double.round(decimals: Int = 2): Double = "%.${decimals}f".format(this).toDouble()

    private fun dateTime(time: Int, zone: String, format: String = "EEE, MMMM d K:mm a"): String {
        return try {
            val sdf = SimpleDateFormat(format)
            val netDate = Date(time * 1000L)
            sdf.timeZone = TimeZone.getTimeZone(zone)
            sdf.format(netDate)
        } catch (e: Exception) {
            e.toString()
        }
    }

    private fun UVIndex(uv: Double): String {
        val index: String
        when {
            uv < 3 -> {
                index = "Low"
            }

            uv < 6 -> {
                index = "Moderate"
            }

            uv < 8 -> {
                index = "High"
            }

            uv < 11 -> {
                index = "Very High"
            }

            else -> {
                index = "Extreme"
            }
        }
        return index
    }

    private fun windDirection(deg: Int): Char {
        val directions: List<Char> =
            listOf('\u2191', '\u2197', '\u2192', '\u2198', '\u2193', '\u2199', '\u2190', '\u2196')
        return directions[(deg / (360.0 / 8)).roundToInt() % 8]
    }

    private fun setBackground(date: Date, sunrise: Date, sunset: Date) {
        val cal = Calendar.getInstance()
        cal.time = date
        val hoursNow = cal.get(Calendar.HOUR_OF_DAY)
        val minutesNow = cal.get(Calendar.MINUTE)
        cal.time = sunrise
        val hoursSunrise = cal.get(Calendar.HOUR_OF_DAY)
        val minutesSunrise = cal.get(Calendar.MINUTE)
        cal.time = sunset
        val hoursSunset = cal.get(Calendar.HOUR_OF_DAY)
        val minutesSunset = cal.get(Calendar.MINUTE)

        if ((hoursNow > hoursSunset && minutesNow > minutesSunset) || (hoursNow <= hoursSunrise && minutesNow <= minutesSunrise)) {
            backgroundImageView.setImageResource(R.drawable.background2)
        } else {
            backgroundImageView.setImageResource(R.drawable.background)
        }
    }

    private fun numberToImperialConverter(number: String): Int {
        return ((number.toInt() * 9 / 5) + 32)
    }

    private fun numberToMetricConverter(number: String): Int {
        return ((number.toInt() - 32) * 5 / 9)
    }

    private fun speedToImperialConverter(number: String): Int {
        return (number.toInt() * 2.237).roundToInt()
    }

    private fun speedToMetricConverter(number: String): Int {
        return (number.toInt() / 2.237).roundToInt()
    }

    private fun toMetric() {
        // Current weather
        val a = currentMaxMinView.text.split(" / ")
        currentMaxMinView.text = "${numberToMetricConverter(a[0].substring(0, a[0].length - 1))}°" +
                " / ${numberToMetricConverter(a[1].substring(0, a[1].length - 1))}°"
        currentTemperatureView.text = "${
            numberToMetricConverter(
                currentTemperatureView.text.substring(
                    0,
                    currentTemperatureView.text.length - 2
                )
            )
        }$temperatureUnit"
        feelsLikeView.text = "Feels like ${
            numberToMetricConverter(
                feelsLikeView.text.substring(
                    11,
                    feelsLikeView.text.length - 1
                )
            )
        }°"

        // Hourly weather
        for (i in 0 until hourlyRecyclerView.childCount) {
            val aa = hourlyRecyclerView[i].findViewById<TextView>(R.id.temperatureView)
            aa.text = "${numberToMetricConverter(aa.text.substring(0, aa.text.length - 1))}°"
        }

        // Daily weather
        for (i in 0 until dailyRecyclerView.childCount) {
            val aa = dailyRecyclerView[i].findViewById<TextView>(R.id.temperatureMaxView)
            aa.text = "${numberToMetricConverter(aa.text.substring(0, aa.text.length - 1))}°"

            val bb = dailyRecyclerView[i].findViewById<TextView>(R.id.temperatureMinView)
            bb.text = "${numberToMetricConverter(bb.text.substring(0, bb.text.length - 1))}°"
        }

        // Current weather details
        val b = windTextView.text.split(" m/h ")
        if (b.size > 1) {
            windTextView.text = "${speedToMetricConverter(b[0])} $speedUnit ${b[1]}"
        }

    }

    private fun toImperial() {
        // Current weather
        val a = currentMaxMinView.text.split(" / ")
        currentMaxMinView.text =
            "${numberToImperialConverter(a[0].substring(0, a[0].length - 1))}°" +
                    " / ${numberToImperialConverter(a[1].substring(0, a[1].length - 1))}°"
        currentTemperatureView.text = "${
            numberToImperialConverter(
                currentTemperatureView.text.substring(
                    0,
                    currentTemperatureView.text.length - 2
                )
            )
        }$temperatureUnit"
        feelsLikeView.text = "Feels like ${
            numberToImperialConverter(
                feelsLikeView.text.substring(
                    11,
                    feelsLikeView.text.length - 1
                )
            )
        }°"

        // Hourly weather
        for (i in 0 until hourlyRecyclerView.childCount) {
            val aa = hourlyRecyclerView[i].findViewById<TextView>(R.id.temperatureView)
            aa.text = "${numberToImperialConverter(aa.text.substring(0, aa.text.length - 1))}°"
        }

        // Daily weather
        for (i in 0 until dailyRecyclerView.childCount) {
            val aa = dailyRecyclerView[i].findViewById<TextView>(R.id.temperatureMaxView)
            aa.text = "${numberToImperialConverter(aa.text.substring(0, aa.text.length - 1))}°"

            val bb = dailyRecyclerView[i].findViewById<TextView>(R.id.temperatureMinView)
            bb.text = "${numberToImperialConverter(bb.text.substring(0, bb.text.length - 1))}°"
        }

        // Current weather details
        val b = windTextView.text.split(" m/s ")
        if (b.size > 1) {
            windTextView.text = "${speedToImperialConverter(b[0])} $speedUnit ${b[1]}"
        }
    }
}
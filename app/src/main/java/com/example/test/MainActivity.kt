package com.example.test

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.get
import androidx.recyclerview.widget.LinearLayoutManager
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import com.beust.klaxon.lookup
import com.google.android.gms.location.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity(), SuggestionClickListener {

    private val LOCATION_PERMISSION_CODE = 111
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    lateinit var result: JsonObject
    lateinit var currentCityName: String

    var temperatureUnit = "°C"
    var speedUnit = "m/s"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                locationResult ?: return
                for (location in locationResult.locations){
                    Log.d("TAG", "Callback: $location")
                    CoroutineScope(IO).launch {
                        getResultsFromLocation(location)
                    }
                }
            }
        }

        getLastKnownLocation()

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
                // Prevent from running twice
                searchView.isIconified = true
                Toast.makeText(this@MainActivity, "Searching.. please wait", Toast.LENGTH_SHORT)
                    .show()
                CoroutineScope(IO).launch {
                    makeApiRequest(query)
                }
                return false
            }

            override fun onQueryTextChange(query: String?): Boolean {
                if (!query.isNullOrBlank()) {
                    CoroutineScope(IO).launch {
                        result = stringToJSON(Geo().makeRequest(Geo().makeUrlFromPartData(URLEncoder.encode(query, "utf-8"))))
                        withContext(Main) {
                            suggestionsRecyclerView.apply {
                                layoutManager = LinearLayoutManager(context)
                                adapter = SuggestionsAdapter(result, this@MainActivity)
                            }
                            suggestionsRecyclerView.visibility = View.VISIBLE
                            toolbar_layout.title = ""
                        }
                    }
                }
                else {
                    toolbar_layout.title = currentCityName
                    suggestionsRecyclerView.visibility = View.GONE
                }
                return true
            }
        })
    }

    private fun setNewText(cityName: String, timeZone: String, result2: JsonObject, sunrise: Int, sunset: Int){
        currentCityName = cityName
        // Collapsing toolbar
        toolbar_layout.title = cityName
        dateView.text = dateTime(result2.lookup<Int>("current.dt")[0], timeZone)

        // Current weather
        bufferView.visibility = View.GONE
        currentWeatherIcon.visibility = View.VISIBLE
        val uri = "@drawable/a${result2.lookup<String>("current.weather.icon")[0]}"
        val imageResource = resources.getIdentifier(uri, null, packageName)
        currentWeatherIcon.setImageResource(imageResource)
        currentMaxMinView.text = "${result2.lookup<Double>("daily.temp.max")[0].roundToInt()}°" +
                " / ${result2.lookup<Double>("daily.temp.min")[0].roundToInt()}°"
        currentTemperatureView.text = "${result2.lookup<Double>("current.temp")[0].roundToInt()}$temperatureUnit"
        feelsLikeView.text = "Feels like ${result2.lookup<Double>("current.feels_like")[0].roundToInt()}°"
        currentDescriptionView.text = result2.lookup<String>("current.weather.main")[0]

        // Hourly recycler view
        hourlyRecyclerView.apply {
            val myLayoutManager = LinearLayoutManager(context)
            myLayoutManager.orientation = LinearLayoutManager.HORIZONTAL
            layoutManager = myLayoutManager
            adapter = HourlyAdapter(result2, timeZone)
        }

        // Daily recycler view
        dailyRecyclerView.apply {
            val myLayoutManager = LinearLayoutManager(context)
            myLayoutManager.orientation = LinearLayoutManager.HORIZONTAL
            layoutManager = myLayoutManager
            adapter = DailyAdapter(result2, timeZone)
        }

        // Current weather details
        sunriseTextView.text = dateTime(sunrise, timeZone, "K:mm a")
        sunsetTextView.text = dateTime(sunset, timeZone, "K:mm a")
        UVIndexTextView.text = UVIndex(result2.lookup<Double>("current.uvi")[0]) + " (${result2.lookup<Double>("current.uvi")[0].roundToInt()})"
        humidityTextView.text = "${result2.lookup<Int>("current.humidity")[0]}%"
        windTextView.text = "${result2.lookup<Double>("current.wind_speed")[0].roundToInt()} $speedUnit ${windDirection(result2.lookup<Int>("current.wind_deg")[0])}"

        // Set background picture to night if after sunset
        val date = Date((result2.lookup<Int>("current.dt")[0]) * 1000L)
        val sunrise = Date(sunrise * 1000L)
        val sunset = Date(sunset * 1000L)
        setBackground(date, sunrise, sunset, timeZone)
    }

    private suspend fun setTextOnMainThread(cityName: String, timeZone: String, result2: JsonObject, sunrise: Int, sunset: Int) {
        withContext(Main) {
            setNewText(cityName, timeZone, result2, sunrise, sunset)
        }
    }

    private suspend fun makeApiRequest(city: String) {
        try {
            val result1 = getResult1FromApi(city)

            val lat:String = degreeConversion((result1).lookup<String>("results.annotations.DMS.lat")[0])
            val lon:String = degreeConversion((result1).lookup<String>("results.annotations.DMS.lng")[0])

            val result2 = getResult2FromApi(lat, lon)

            val cityName = result1.lookup<String>("results.formatted")[0]
            val timeZone = result1.lookup<String>("results.annotations.timezone.name")[0]

            val sunrise = result1.lookup<Int>("results.annotations.sun.rise.apparent")[0]
            val sunset = result1.lookup<Int>("results.annotations.sun.set.apparent")[0]

            setTextOnMainThread(cityName, timeZone, result2, sunrise, sunset)
        }
        catch (exception: java.lang.Exception){
            withContext(Main) {
                Toast.makeText(this@MainActivity, "Oops.. that didn't work out. Please try again", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun getResult1FromApi(city: String): JsonObject {
        return stringToJSON(Geo().makeRequest(Geo().makeUrl(URLEncoder.encode(city, "utf-8"))))
    }

    private suspend fun getResult2FromApi(lat: String, lon: String): JsonObject {
        val myWeather = Weather(lat, lon)
        return stringToJSON(myWeather.makeRequest(myWeather.makeUrl()))
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

    private fun UVIndex(uv: Double):String {
        val index:String
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
            uv < 6 -> {
                index = "Moderate"
            }
            else -> {
                index = "Extreme"
            }
        }
        return index
    }

    private fun windDirection(deg: Int): Char {
        val directions: List<Char> = listOf('\u2191', '\u2197', '\u2192', '\u2198', '\u2193', '\u2199', '\u2190', '\u2196')
        return directions[(deg / (360.0 / 8)).roundToInt() % 8]
    }

    private fun setBackground(date: Date, sunrise: Date, sunset: Date, timeZone: String) {
        val cal = Calendar.getInstance()
        cal.timeZone = TimeZone.getTimeZone(timeZone)
        cal.time = date
        val hoursNow = cal.get(Calendar.HOUR_OF_DAY)
        val minutesNow = cal.get(Calendar.MINUTE)
        cal.time = sunrise
        val hoursSunrise = cal.get(Calendar.HOUR_OF_DAY)
        val minutesSunrise = cal.get(Calendar.MINUTE)
        cal.time = sunset
        val hoursSunset = cal.get(Calendar.HOUR_OF_DAY)
        val minutesSunset = cal.get(Calendar.MINUTE)

        if (((hoursNow > hoursSunset) || (hoursNow == hoursSunset && minutesNow > minutesSunset)) || ((hoursNow < hoursSunrise) || (hoursNow == hoursSunrise && minutesNow < minutesSunset))) {
            backgroundImageView.setImageResource(R.drawable.background2)
        }
        else{
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
        currentTemperatureView.text = "${numberToMetricConverter(currentTemperatureView.text.substring(0, currentTemperatureView.text.length - 2))}$temperatureUnit"
        feelsLikeView.text = "Feels like ${numberToMetricConverter(feelsLikeView.text.substring(11, feelsLikeView.text.length - 1))}°"

        // Hourly weather
        for(i in 0..5){
            val aa = hourlyRecyclerView[i].findViewById<TextView>(R.id.temperatureView)
            aa.text = "${numberToMetricConverter(aa.text.substring(0, aa.text.length - 1))}°"
        }

        // Daily weather
        for(i in 0..5){
            val aa = dailyRecyclerView[i].findViewById<TextView>(R.id.temperatureMaxView)
            aa.text = "${numberToMetricConverter(aa.text.substring(0, aa.text.length - 1))}°"
            val bb = dailyRecyclerView[i].findViewById<TextView>(R.id.temperatureMinView)
            bb.text = "${numberToMetricConverter(bb.text.substring(0, bb.text.length - 1))}°"
        }

        // Current weather details
        val b = windTextView.text.split(" m/h ")
        windTextView.text = "${speedToMetricConverter(b[0])} $speedUnit ${b[1]}"

    }

    private fun toImperial() {
        // Current weather
        val a = currentMaxMinView.text.split(" / ")
        currentMaxMinView.text = "${numberToImperialConverter(a[0].substring(0, a[0].length - 1))}°" +
                " / ${numberToImperialConverter(a[1].substring(0, a[1].length - 1))}°"
        currentTemperatureView.text = "${numberToImperialConverter(currentTemperatureView.text.substring(0, currentTemperatureView.text.length - 2))}$temperatureUnit"
        feelsLikeView.text = "Feels like ${numberToImperialConverter(feelsLikeView.text.substring(11, feelsLikeView.text.length - 1))}°"

        // Hourly weather
        for(i in 0..5){
            val aa = hourlyRecyclerView[i].findViewById<TextView>(R.id.temperatureView)
            aa.text = "${numberToImperialConverter(aa.text.substring(0, aa.text.length - 1))}°"
        }

        // Daily weather
        for(i in 0..5){
            val aa = dailyRecyclerView[i].findViewById<TextView>(R.id.temperatureMaxView)
            aa.text = "${numberToImperialConverter(aa.text.substring(0, aa.text.length - 1))}°"
            val bb = dailyRecyclerView[i].findViewById<TextView>(R.id.temperatureMinView)
            bb.text = "${numberToImperialConverter(bb.text.substring(0, bb.text.length - 1))}°"
        }

        // Current weather details
        val b = windTextView.text.split(" m/s ")
        windTextView.text = "${speedToImperialConverter(b[0])} $speedUnit ${b[1]}"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this@MainActivity, "Location permission granted", Toast.LENGTH_SHORT).show()
                getLastKnownLocation()
            }
            else {
                Toast.makeText(this@MainActivity, "Location permission denied", Toast.LENGTH_SHORT).show()
                CoroutineScope(IO).launch {
                    makeApiRequest("prague")
                }
            }
        }
    }

    private fun getLastKnownLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,), LOCATION_PERMISSION_CODE)
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
            getResultsFromLocation(location)
            }
            else {
                fusedLocationClient.requestLocationUpdates(LocationRequest(), locationCallback, Looper.getMainLooper())
            }
        }
    }

    private fun getResultsFromLocation(location: Location) {
        CoroutineScope(IO).launch {

            val result1 = stringToJSON(Geo().makeRequest(Geo().makeUrlFromCoords(location.latitude.round().toString(), location.longitude.round().toString())))
            val result2 = getResult2FromApi(location.latitude.round().toString(), location.longitude.round().toString())

            val cityName = result1.lookup<String>("results.formatted")[0]
            val timeZone = result1.lookup<String>("results.annotations.timezone.name")[0]

            val sunrise = result1.lookup<Int>("results.annotations.sun.rise.apparent")[0]
            val sunset = result1.lookup<Int>("results.annotations.sun.set.apparent")[0]

            setTextOnMainThread(cityName, timeZone, result2, sunrise, sunset)
        }
    }

    override fun onSuggestionClickClickAction(position: Int) {
        CoroutineScope(IO).launch {
            val query = result.lookup<String>("geonames.toponymName")[position] + ", " +
                    result.lookup<String>("geonames.adminName1")[position] + ", " +
                    result.lookup<String>("geonames.countryName")[position]
            Log.d("TAG", "Searching weather for ${result.lookup<String>("geonames.toponymName")[position] + ", " +
                    result.lookup<String>("geonames.adminName1")[position] + ", " +
                    result.lookup<String>("geonames.countryName")[position]}")
            makeApiRequest(query)
        }

        toolbar_layout.title = currentCityName
        suggestionsRecyclerView.visibility = View.GONE
    }
}
package com.example.test

import java.net.URL

class Weather(
    private var lat: String = "0",
    private var lng: String = "0",
    private var metricUnit: String = "metric"
) {

    private val key = "appid=1ade6c52c486365b513da49f16926093"
    private val weatherUrl = "https://api.openweathermap.org/data/2.5/weather"
    private val forecastUrl = "https://api.openweathermap.org/data/2.5/forecast"


    fun currentWeatherUrl(): String {
        return "$weatherUrl?lat=$lat&lon=$lng&units=$metricUnit&$key"
    }

    fun forecastUrl(): String {
        return "$forecastUrl?lat=$lat&lon=$lng&units=$metricUnit&$key"
    }

    fun makeRequest(url: String): String {
        println(url)
        return URL(url).readText()
    }
}
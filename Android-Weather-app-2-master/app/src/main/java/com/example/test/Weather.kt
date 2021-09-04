package com.example.test

import java.net.URL

class Weather(private var lat:String = "0", private var lng:String = "0", private var metricUnit:String = "metric") {
    private val key:String = "&appid=c08b3f72d1bbfdc53e34c571a5cc5c86"
    private var url:String = "https://api.openweathermap.org/data/2.5/onecall"
//
//    private var metricUnit:String = "metric"
//    var temperatureUnit:String = "°C"
//    var speedUnit:String = "m/s"

    fun makeUrl():String {
        return "$url?lat=$lat&lon=$lng&exclude=minutely&units=$metricUnit$key"
    }

    fun makeRequest(url:String):String {
        return URL(url).readText()
    }
}
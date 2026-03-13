package com.example.test

import java.net.URL

class Geo {
    private val key:String = "&key=9dac5e97ac964493a86ec434f622eb48"
    private val url:String = "https://api.opencagedata.com/geocode/v1/json"

    fun makeUrl(place: String):String {
        return "$url?q=$place&limit=1$key"
    }

    fun makeRequest(url:String):String {
        println(url)
        return URL(url).readText()
    }
}
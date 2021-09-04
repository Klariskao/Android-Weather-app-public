package com.example.test

import java.net.URL

class Geo {
    private val key:String = "&key=0826219a14f94bfbb49a56b9902f31e8"
    private val url:String = "https://api.opencagedata.com/geocode/v1/json"

    fun makeUrl(place: String):String {
        return "$url?q=$place&limit=1$key"
    }

    fun makeRequest(url:String):String {
        return URL(url).readText()
    }
}
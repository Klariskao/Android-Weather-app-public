package com.example.test

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.beust.klaxon.JsonObject
import com.beust.klaxon.lookup
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class HourlyAdapter(private val forecast: JsonObject, private val timeZone: String) :
    RecyclerView.Adapter<HourlyAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.hourly_view, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = 6

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(forecast, timeZone, position)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        fun bind(forecast: JsonObject, timeZone: String, position: Int) {

            val images = arrayOf(
                R.drawable.a01d, R.drawable.a01n, R.drawable.a02d, R.drawable.a02n,
                R.drawable.a03d, R.drawable.a03n, R.drawable.a04d, R.drawable.a04n,
                R.drawable.a09d, R.drawable.a09n, R.drawable.a10d, R.drawable.a10n,
                R.drawable.a11d, R.drawable.a11n, R.drawable.a13d, R.drawable.a13n,
                R.drawable.a50d, R.drawable.a50n
            )

            val indexes = arrayOf(
                "01d","01n","02d","02n","03d","03n","04d","04n",
                "09d","09n","10d","10n","11d","11n","13d","13n","50d","50n"
            )

            val dt = forecast.lookup<Int>("list.dt")[position]

            itemView.findViewById<TextView>(R.id.hourView).text =
                dateTime(dt, timeZone, "K:mm a")

            val icon = forecast.lookup<String>("list.weather.icon")[position]
            itemView.findViewById<ImageView>(R.id.weatherIconView)
                .setImageResource(images[indexes.indexOf(icon)])

            val pop = forecast.lookup<Double>("list.pop")[position]
            itemView.findViewById<TextView>(R.id.rainView).text =
                "${(pop * 100).roundToInt()}%"

            val temp = forecast.lookup<Double>("list.main.temp")[position]
            itemView.findViewById<TextView>(R.id.temperatureView).text =
                "${temp.roundToInt()}°"
        }

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
    }
}
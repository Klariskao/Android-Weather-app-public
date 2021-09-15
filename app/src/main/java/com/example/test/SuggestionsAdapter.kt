package com.example.test

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.beust.klaxon.JsonObject
import com.beust.klaxon.lookup

class SuggestionsAdapter(private val suggestionsList: JsonObject, private val suggestionClickListener: SuggestionClickListener) : RecyclerView.Adapter<SuggestionsAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.suggestion_view, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = if (suggestionsList.lookup<Int>("totalResultsCount")[0] < 5)
        suggestionsList.lookup<Int>("totalResultsCount")[0]
        else 5

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(position, suggestionsList, suggestionClickListener)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(position: Int, suggestionsList: JsonObject, suggestionClickListener: SuggestionClickListener) {
            itemView.findViewById<TextView>(R.id.suggestionTextView).text =
                suggestionsList.lookup<String>("geonames.toponymName")[position] + ", " +
                            suggestionsList.lookup<String>("geonames.adminName1")[position] + ", " +
                            suggestionsList.lookup<String>("geonames.countryName")[position]
            itemView.findViewById<CardView>(R.id.suggestionCardView).setOnClickListener {
                suggestionClickListener.onSuggestionClickClickAction(position)
            }
        }
    }
}
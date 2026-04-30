package com.example.lifeline

import android.content.Context
import android.location.Geocoder
import java.util.Locale

object PickupAddressHelper {

    data class PickupLoc(
        val address: String,
        val lat: Double,
        val lng: Double
    )

    fun findPickupAddress(
        context: Context,
        text: String,
        onSuccess: (PickupLoc) -> Unit,
        onFailure: (String) -> Unit
    ) {
        Thread {
            try {
                val cleanText = cleanAddress(text)
                val geocoder = Geocoder(context, Locale.getDefault())

                val queries = listOf(
                    cleanText,
                    "$cleanText Pune",
                    "$cleanText Talegaon",
                    "$cleanText Maharashtra",
                    "$cleanText India"
                )

                for (query in queries) {
                    val list = geocoder.getFromLocationName(query, 5)

                    if (!list.isNullOrEmpty()) {
                        val best = list[0]

                        onSuccess(
                            PickupLoc(
                                address = best.getAddressLine(0) ?: query,
                                lat = best.latitude,
                                lng = best.longitude
                            )
                        )
                        return@Thread
                    }
                }

                onFailure("Address not found. Try more clear location name.")

            } catch (e: Exception) {
                onFailure(e.message ?: "Failed to search address")
            }
        }.start()
    }

    private fun cleanAddress(input: String): String {
        return input.trim()
            .replace("talegoan", "talegaon", ignoreCase = true)
            .replace("durga tekdi", "durga tekdi talegaon", ignoreCase = true)
            .replace("punee", "pune", ignoreCase = true)
            .replace("hospiatl", "hospital", ignoreCase = true)
            .replace(Regex("\\s+"), " ")
    }
}
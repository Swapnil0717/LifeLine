package com.example.lifeline

import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL

object RouteHelper {

    data class RouteResult(
        val points: List<GeoPoint>,
        val distanceText: String,
        val durationText: String
    )

    fun getRoute(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double
    ): RouteResult? {
        return try {
            val urlString =
                "https://router.project-osrm.org/route/v1/driving/$startLng,$startLat;$endLng,$endLat?overview=full&geometries=geojson"

            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val response = connection.inputStream.bufferedReader().use { it.readText() }

            val json = JSONObject(response)
            val routes = json.getJSONArray("routes")

            if (routes.length() == 0) return null

            val route = routes.getJSONObject(0)
            val distanceMeters = route.getDouble("distance")
            val durationSeconds = route.getDouble("duration")

            val geometry = route.getJSONObject("geometry")
            val coordinates = geometry.getJSONArray("coordinates")

            val routePoints = mutableListOf<GeoPoint>()

            for (i in 0 until coordinates.length()) {
                val point = coordinates.getJSONArray(i)
                val lng = point.getDouble(0)
                val lat = point.getDouble(1)
                routePoints.add(GeoPoint(lat, lng))
            }

            RouteResult(
                points = routePoints,
                distanceText = formatDistance(distanceMeters),
                durationText = formatDuration(durationSeconds)
            )

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun formatDistance(distanceMeters: Double): String {
        return if (distanceMeters >= 1000) {
            String.format("%.1f km", distanceMeters / 1000)
        } else {
            "${distanceMeters.toInt()} m"
        }
    }

    private fun formatDuration(durationSeconds: Double): String {
        val minutes = (durationSeconds / 60).toInt()
        return if (minutes <= 1) "1 min" else "$minutes mins"
    }
}
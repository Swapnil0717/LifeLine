package com.example.lifeline

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.net.HttpURLConnection
import java.net.URL

class ChooseHospitalActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var map: MapView

    private var requestId = ""
    private var patientLat = 18.7357
    private var patientLng = 73.6756

    data class Hospital(
        val name: String,
        val lat: Double,
        val lng: Double
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Configuration.getInstance().load(
            applicationContext,
            getSharedPreferences("osm_pref", MODE_PRIVATE)
        )

        setContentView(R.layout.activity_choose_hospital)

        db = FirebaseFirestore.getInstance()

        requestId = intent.getStringExtra("requestId") ?: ""
        patientLat = intent.getDoubleExtra("patientLat", 18.7357)
        patientLng = intent.getDoubleExtra("patientLng", 73.6756)

        if (requestId.isEmpty()) {
            Toast.makeText(this, "Request not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(15.0)
        map.controller.setCenter(GeoPoint(patientLat, patientLng))

        showPatientMarker()
        fetchNearbyHospitals()
    }

    private fun showPatientMarker() {
        val patientPoint = GeoPoint(patientLat, patientLng)

        val patientMarker = Marker(map)
        patientMarker.position = patientPoint
        patientMarker.icon = androidx.core.content.ContextCompat.getDrawable(
            this,
            R.drawable.patient_marker
        )
        patientMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        patientMarker.title = "Patient Pickup"

        map.overlays.add(patientMarker)
        map.invalidate()
    }

    private fun fetchNearbyHospitals() {
        Thread {
            val hospitals = mutableListOf<Hospital>()

            try {
                val query = """
                    [out:json][timeout:25];
                    (
                      node["amenity"="hospital"](around:7000,$patientLat,$patientLng);
                      way["amenity"="hospital"](around:7000,$patientLat,$patientLng);
                      relation["amenity"="hospital"](around:7000,$patientLat,$patientLng);
                      node["healthcare"="hospital"](around:7000,$patientLat,$patientLng);
                      way["healthcare"="hospital"](around:7000,$patientLat,$patientLng);
                    );
                    out center tags;
                """.trimIndent()

                val urlString =
                    "https://overpass-api.de/api/interpreter?data=${java.net.URLEncoder.encode(query, "UTF-8")}"

                val connection = URL(urlString).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 20000
                connection.readTimeout = 20000

                val response = connection.inputStream.bufferedReader().use { it.readText() }

                val json = JSONObject(response)
                val elements = json.getJSONArray("elements")

                for (i in 0 until elements.length()) {
                    val obj = elements.getJSONObject(i)

                    val tags = obj.optJSONObject("tags")
                    val name = tags?.optString("name") ?: "Nearby Hospital"

                    val lat: Double
                    val lng: Double

                    if (obj.has("lat") && obj.has("lon")) {
                        lat = obj.getDouble("lat")
                        lng = obj.getDouble("lon")
                    } else if (obj.has("center")) {
                        val center = obj.getJSONObject("center")
                        lat = center.getDouble("lat")
                        lng = center.getDouble("lon")
                    } else {
                        continue
                    }

                    hospitals.add(Hospital(name, lat, lng))
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }

            runOnUiThread {
                if (hospitals.isEmpty()) {
                    Toast.makeText(
                        this,
                        "No hospital found nearby. Long press on map to select manually.",
                        Toast.LENGTH_LONG
                    ).show()
                    enableManualMapSelection()
                } else {
                    showHospitalMarkers(hospitals)
                    enableManualMapSelection()
                }
            }
        }.start()
    }

    private fun showHospitalMarkers(hospitals: List<Hospital>) {
        hospitals.forEach { hospital ->
            val marker = Marker(map)
            marker.position = GeoPoint(hospital.lat, hospital.lng)
            marker.icon = androidx.core.content.ContextCompat.getDrawable(
                this,
                R.drawable.hospital_marker
            )
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.title = hospital.name
            marker.snippet = "Tap to select this hospital"

            marker.setOnMarkerClickListener { clickedMarker, _ ->
                selectHospital(
                    Hospital(
                        clickedMarker.title ?: "Selected Hospital",
                        clickedMarker.position.latitude,
                        clickedMarker.position.longitude
                    )
                )
                true
            }

            map.overlays.add(marker)
        }

        map.invalidate()
    }

    private fun enableManualMapSelection() {
        map.setOnLongClickListener {
            val projection = map.projection
            val geoPoint = projection.fromPixels(
                map.width / 2,
                map.height / 2
            ) as GeoPoint

            selectHospital(
                Hospital(
                    "Selected Hospital",
                    geoPoint.latitude,
                    geoPoint.longitude
                )
            )

            true
        }
    }

    private fun selectHospital(hospital: Hospital) {
        db.collection("ambulanceRequests")
            .document(requestId)
            .update(
                mapOf(
                    "status" to "HOSPITAL_SELECTED",
                    "hospitalName" to hospital.name,
                    "hospitalLat" to hospital.lat,
                    "hospitalLng" to hospital.lng
                )
            )
            .addOnSuccessListener {
                Toast.makeText(this, "Hospital selected: ${hospital.name}", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, it.message ?: "Failed to select hospital", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }
}
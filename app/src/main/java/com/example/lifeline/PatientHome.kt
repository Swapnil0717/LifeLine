package com.example.lifeline

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.Locale

class PatientHome : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var tvCurrentLocation: TextView
    private lateinit var db: FirebaseFirestore

    private var loggedInUserName = ""
    private var loggedInUserPhone = ""
    private var loggedInUserEmail = ""

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

            if (fineGranted || coarseGranted) {
                showCurrentLocation()
            } else {
                showDefaultLocation()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Configuration.getInstance().load(
            applicationContext,
            getSharedPreferences("osm_pref", MODE_PRIVATE)
        )

        setContentView(R.layout.activity_home)

        db = FirebaseFirestore.getInstance()

        map = findViewById(R.id.map)
        tvCurrentLocation = findViewById(R.id.tvCurrentLocation)

        val btnBookAmbulance = findViewById<Button>(R.id.next1)
        val btnContinueHome = findViewById<Button>(R.id.next)
        val btnEmergencyHelp = findViewById<TextView>(R.id.btnEmergencyHelp)

        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(17.0)

        checkLocationPermissionAndLoadMap()

        if (isUserLoggedIn()) {
            fetchLoggedInUserData()
        }

        btnEmergencyHelp.setOnClickListener {
            if (isUserLoggedIn()) {
                openEmergencyAmbulance()
            } else {
                Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, Login::class.java))
            }
        }

        btnBookAmbulance.setOnClickListener {
            if (isUserLoggedIn()) {
                openEmergencyAmbulance()
            } else {
                startActivity(Intent(this, PhoneNumberActivity::class.java))
            }
        }

        btnContinueHome.setOnClickListener {
            if (isUserLoggedIn()) {
                startActivity(Intent(this, Main::class.java))
            } else {
                Toast.makeText(this, "Please login to continue", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, Login::class.java))
            }
        }
    }

    private fun openEmergencyAmbulance() {
        val intent = Intent(this, BookAmbulance::class.java)
        intent.putExtra("bookingType", "EMERGENCY")
        intent.putExtra("name", loggedInUserName)
        intent.putExtra("phone", loggedInUserPhone)
        intent.putExtra("email", loggedInUserEmail)
        startActivity(intent)
    }

    private fun callIndianAmbulanceNumber() {
        val intent = Intent(Intent.ACTION_DIAL)
        intent.data = Uri.parse("tel:108")
        startActivity(intent)
    }

    private fun fetchLoggedInUserData() {
        val sharedPref = getSharedPreferences("LifeLineSession", MODE_PRIVATE)
        val collection = sharedPref.getString("collection", "") ?: ""
        val userId = sharedPref.getString("userId", "") ?: ""

        if (collection.isEmpty() || userId.isEmpty()) return

        db.collection(collection)
            .document(userId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    loggedInUserName = doc.getString("name") ?: ""
                    loggedInUserPhone = doc.getString("phone") ?: ""
                    loggedInUserEmail = doc.getString("email") ?: ""
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to fetch user data", Toast.LENGTH_SHORT).show()
            }
    }

    private fun isUserLoggedIn(): Boolean {
        val sharedPref = getSharedPreferences("LifeLineSession", MODE_PRIVATE)
        return sharedPref.getBoolean("isLoggedIn", false)
    }

    private fun checkLocationPermissionAndLoadMap() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            showCurrentLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun showCurrentLocation() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            showDefaultLocation()
            return
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    val userLocation = GeoPoint(location.latitude, location.longitude)
                    map.controller.setCenter(userLocation)

                    val marker = Marker(map)
                    marker.position = userLocation
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    marker.title = "You are here"

                    map.overlays.clear()
                    map.overlays.add(marker)
                    map.invalidate()

                    setLocationName(location.latitude, location.longitude)
                } else {
                    showDefaultLocation()
                }
            }
            .addOnFailureListener {
                showDefaultLocation()
            }
    }

    private fun setLocationName(latitude: Double, longitude: Double) {
        try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)

            tvCurrentLocation.text =
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    address.featureName ?: address.subLocality ?: address.locality ?: "Current Location"
                } else {
                    "Current Location"
                }
        } catch (_: Exception) {
            tvCurrentLocation.text = "Current Location"
        }
    }

    private fun showDefaultLocation() {
        val defaultLocation = GeoPoint(18.7357, 73.6756)
        map.controller.setCenter(defaultLocation)

        val marker = Marker(map)
        marker.position = defaultLocation
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = "Default Location"

        map.overlays.clear()
        map.overlays.add(marker)
        map.invalidate()

        tvCurrentLocation.text = "Talegaon"
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
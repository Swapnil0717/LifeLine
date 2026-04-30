package com.example.lifeline

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.util.Locale

class PickupLocationPickerActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var etCurrentLocation: EditText

    private var selectedLat = 18.7357
    private var selectedLng = 73.6756

    private val handler = Handler(Looper.getMainLooper())
    private var reverseRunnable: Runnable? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            loadCurrentLocation()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Configuration.getInstance().load(
            applicationContext,
            getSharedPreferences("osm_pref", MODE_PRIVATE)
        )

        setContentView(R.layout.activity_pickup_location_picker)

        map = findViewById(R.id.map)
        etCurrentLocation = findViewById(R.id.tvCurrentLocation)

        val btnConfirm = findViewById<Button>(R.id.next1)
        val btnBack = findViewById<ImageButton>(R.id.back)
        val btnMyLocation = findViewById<ImageButton>(R.id.btnMyLocation)

        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(17.0)

        btnBack.setOnClickListener {
            finish()
        }

        btnMyLocation.setOnClickListener {
            loadCurrentLocation()
        }

        btnConfirm.setOnClickListener {
            val result = Intent()
            result.putExtra("pickupAddress", etCurrentLocation.text.toString().trim())
            result.putExtra("pickupLat", selectedLat)
            result.putExtra("pickupLng", selectedLng)
            setResult(Activity.RESULT_OK, result)
            finish()
        }

        etCurrentLocation.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                searchTypedAddress(etCurrentLocation.text.toString().trim())
                true
            } else {
                false
            }
        }

        map.addMapListener(
            SimpleMapListener {
                val center = map.mapCenter as GeoPoint
                selectedLat = center.latitude
                selectedLng = center.longitude
                reverseAddressDebounced(selectedLat, selectedLng)
            }
        )

        checkPermission()
    }

    private fun checkPermission() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            loadCurrentLocation()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun loadCurrentLocation() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            moveMapTo(18.7357, 73.6756)
            return
        }

        LocationServices.getFusedLocationProviderClient(this).lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    moveMapTo(location.latitude, location.longitude)
                } else {
                    moveMapTo(18.7357, 73.6756)
                }
            }
            .addOnFailureListener {
                moveMapTo(18.7357, 73.6756)
            }
    }

    private fun moveMapTo(lat: Double, lng: Double) {
        selectedLat = lat
        selectedLng = lng
        map.controller.setCenter(GeoPoint(lat, lng))
        reverseAddress(lat, lng)
    }

    private fun reverseAddressDebounced(lat: Double, lng: Double) {
        reverseRunnable?.let { handler.removeCallbacks(it) }

        reverseRunnable = Runnable {
            reverseAddress(lat, lng)
        }

        handler.postDelayed(reverseRunnable!!, 700)
    }

    private fun reverseAddress(lat: Double, lng: Double) {
        Thread {
            try {
                val geocoder = Geocoder(this, Locale.getDefault())
                val list = geocoder.getFromLocation(lat, lng, 1)

                if (!list.isNullOrEmpty()) {
                    val address = list[0].getAddressLine(0) ?: "Selected Location"
                    runOnUiThread {
                        etCurrentLocation.setText(address)
                        etCurrentLocation.setSelection(etCurrentLocation.text.length)
                    }
                }
            } catch (_: Exception) {
            }
        }.start()
    }

    private fun searchTypedAddress(addressText: String) {
        if (addressText.isEmpty()) {
            Toast.makeText(this, "Enter pickup address", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Searching location...", Toast.LENGTH_SHORT).show()

        PickupAddressHelper.findPickupAddress(
            context = this,
            text = addressText,
            onSuccess = { loc ->
                runOnUiThread {
                    selectedLat = loc.lat
                    selectedLng = loc.lng
                    etCurrentLocation.setText(loc.address)
                    etCurrentLocation.setSelection(etCurrentLocation.text.length)
                    map.controller.setCenter(GeoPoint(loc.lat, loc.lng))
                }
            },
            onFailure = { message ->
                runOnUiThread {
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
            }
        )
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
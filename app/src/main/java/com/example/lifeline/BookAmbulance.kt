package com.example.lifeline

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Locale

class BookAmbulance : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var map: MapView
    private lateinit var tvCurrentLocation: TextView

    private lateinit var findingDriverLayout: LinearLayout
    private lateinit var driverFoundLayout: LinearLayout
    private lateinit var tvFindingTitle: TextView
    private lateinit var tvFindingSubTitle: TextView

    private lateinit var tvRideStatus: TextView
    private lateinit var tvDriverName: TextView
    private lateinit var tvAmbulanceNumber: TextView
    private lateinit var tvDriverLocation: TextView
    private lateinit var btnTrackDriver: Button
    private lateinit var btnCallDriver: Button
    private lateinit var btnCancelRide: Button

    private var bookingListener: ListenerRegistration? = null

    private var bookingId = ""
    private var patientLat = 18.7357
    private var patientLng = 73.6756
    private var pickupAddress = "Pickup Location"

    private var latestDriverLat = 0.0
    private var latestDriverLng = 0.0

    private var hospitalName = ""
    private var hospitalLat = 0.0
    private var hospitalLng = 0.0

    private var driverPhone = ""
    private var requestCreated = false
    private var notificationShownForDriver = false
    private var noDriverRetryRunning = false

    private var currentRideStatus = ""

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

            if (fine || coarse) {
                showCurrentLocationAndCreateRequest()
            } else {
                showDefaultLocationAndCreateRequest()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Configuration.getInstance().load(
            applicationContext,
            getSharedPreferences("osm_pref", MODE_PRIVATE)
        )

        setContentView(R.layout.activity_book_ambulance)

        db = FirebaseFirestore.getInstance()

        map = findViewById(R.id.map)
        tvCurrentLocation = findViewById(R.id.tvCurrentLocation)

        findingDriverLayout = findViewById(R.id.findingDriverLayout)
        driverFoundLayout = findViewById(R.id.driverFoundLayout)
        tvFindingTitle = findViewById(R.id.tvFindingTitle)
        tvFindingSubTitle = findViewById(R.id.tvFindingSubTitle)

        tvRideStatus = findViewById(R.id.tvRideStatus)
        tvDriverName = findViewById(R.id.tvDriverName)
        tvAmbulanceNumber = findViewById(R.id.tvAmbulanceNumber)
        tvDriverLocation = findViewById(R.id.tvDriverLocation)
        btnTrackDriver = findViewById(R.id.btnTrackDriver)
        btnCallDriver = findViewById(R.id.btnCallDriver)
        btnCancelRide = findViewById(R.id.btnCancelRide)

        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(17.0)

        btnTrackDriver.setOnClickListener {
            when (currentRideStatus) {
                "HOSPITAL_SELECTED" -> drawRouteToHospital()
                "ACCEPTED", "REACHED_PATIENT" -> drawRouteDriverToPatient()
                else -> Toast.makeText(this, "Route not available yet", Toast.LENGTH_SHORT).show()
            }
        }

        btnCallDriver.setOnClickListener {
            callNumber(driverPhone)
        }

        btnCancelRide.setOnClickListener {
            cancelRide()
        }

        resumeOrStartBooking()
    }

    private fun resumeOrStartBooking() {
        val bookingFromIntent = intent.getStringExtra("bookingId") ?: ""

        if (bookingFromIntent.isNotEmpty()) {
            bookingId = bookingFromIntent
            requestCreated = true
            saveActiveBookingId(bookingId)
            trackExistingBooking(bookingId)
            return
        }

        val activeBookingId = getSharedPreferences("LifeLineRide", MODE_PRIVATE)
            .getString("activeBookingId", "") ?: ""

        if (activeBookingId.isNotEmpty()) {
            trackExistingOrStartEmergency(activeBookingId)
        } else {
            checkLocationPermission()
        }
    }

    private fun trackExistingOrStartEmergency(activeBookingId: String) {
        db.collection("ambulanceRequests")
            .document(activeBookingId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    clearActiveBooking()
                    checkLocationPermission()
                    return@addOnSuccessListener
                }

                val status = doc.getString("status") ?: ""

                if (isActiveStatus(status)) {
                    bookingId = activeBookingId
                    requestCreated = true
                    loadBookingFields(doc)
                    showPatientMarker()
                    listenToBookingUpdates()

                    if (status == "FINDING_DRIVER" || status == "NO_DRIVER_AVAILABLE") {
                        findNearestHospitalAndDriver()
                    }
                } else {
                    clearActiveBooking()
                    checkLocationPermission()
                }
            }
            .addOnFailureListener {
                clearActiveBooking()
                checkLocationPermission()
            }
    }

    private fun trackExistingBooking(id: String) {
        db.collection("ambulanceRequests")
            .document(id)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(this, "Booking not found", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                val status = doc.getString("status") ?: ""

                if (!isActiveStatus(status)) {
                    clearActiveBooking()
                    Toast.makeText(this, "Booking already closed", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                loadBookingFields(doc)
                showPatientMarker()
                listenToBookingUpdates()
            }
            .addOnFailureListener {
                Toast.makeText(this, it.message ?: "Failed to load booking", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun isActiveStatus(status: String): Boolean {
        return status == "SCHEDULED" ||
                status == "FINDING_DRIVER" ||
                status == "SEARCHING_DRIVER" ||
                status == "ACCEPTED" ||
                status == "REACHED_PATIENT" ||
                status == "HOSPITAL_SELECTED" ||
                status == "NO_DRIVER_AVAILABLE"
    }

    private fun loadBookingFields(doc: DocumentSnapshot) {
        patientLat = doc.getDouble("pickupLat") ?: patientLat
        patientLng = doc.getDouble("pickupLng") ?: patientLng
        pickupAddress = doc.getString("pickupAddress") ?: pickupAddress

        latestDriverLat = doc.getDouble("driverLat") ?: latestDriverLat
        latestDriverLng = doc.getDouble("driverLng") ?: latestDriverLng

        hospitalName = doc.getString("hospitalName") ?: hospitalName
        hospitalLat = doc.getDouble("hospitalLat") ?: hospitalLat
        hospitalLng = doc.getDouble("hospitalLng") ?: hospitalLng

        tvCurrentLocation.text = pickupAddress
    }

    private fun checkLocationPermission() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            showCurrentLocationAndCreateRequest()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun showCurrentLocationAndCreateRequest() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            showDefaultLocationAndCreateRequest()
            return
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    patientLat = location.latitude
                    patientLng = location.longitude

                    fetchReadablePickupAddress()
                    showPatientMarker()

                    if (!requestCreated) {
                        createEmergencyRequest()
                    }
                } else {
                    showDefaultLocationAndCreateRequest()
                }
            }
            .addOnFailureListener {
                showDefaultLocationAndCreateRequest()
            }
    }

    private fun showDefaultLocationAndCreateRequest() {
        patientLat = 18.7357
        patientLng = 73.6756
        pickupAddress = "Talegaon"

        tvCurrentLocation.text = pickupAddress
        showPatientMarker()

        if (!requestCreated) {
            createEmergencyRequest()
        }
    }

    private fun fetchReadablePickupAddress() {
        try {
            val addresses = Geocoder(this, Locale.getDefault())
                .getFromLocation(patientLat, patientLng, 1)

            pickupAddress =
                if (!addresses.isNullOrEmpty()) {
                    addresses[0].getAddressLine(0) ?: "Current Pickup"
                } else {
                    "Current Pickup"
                }

            tvCurrentLocation.text = pickupAddress
        } catch (_: Exception) {
            pickupAddress = "Current Pickup"
            tvCurrentLocation.text = pickupAddress
        }
    }

    private fun showPatientMarker() {
        val point = GeoPoint(patientLat, patientLng)

        map.overlays.clear()

        val marker = Marker(map)
        marker.position = point
        marker.icon = ContextCompat.getDrawable(this, R.drawable.patient_marker)
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

        map.overlays.add(marker)
        map.controller.setCenter(point)
        map.invalidate()
    }

    private fun createEmergencyRequest() {
        requestCreated = true

        val sharedPref = getSharedPreferences("LifeLineSession", MODE_PRIVATE)
        val patientId = sharedPref.getString("userId", "") ?: ""
        val patientName = sharedPref.getString("userName", "Patient") ?: "Patient"
        val patientPhone = sharedPref.getString("userPhone", "") ?: ""
        val patientEmail = sharedPref.getString("userEmail", "") ?: ""

        val requestData = hashMapOf<String, Any>(
            "bookingType" to "EMERGENCY",
            "status" to "FINDING_DRIVER",
            "patientId" to patientId,
            "patientCollection" to "patients",
            "patientName" to patientName,
            "patientPhone" to patientPhone,
            "patientEmail" to patientEmail,
            "pickupAddress" to pickupAddress,
            "pickupLat" to patientLat,
            "pickupLng" to patientLng,
            "createdAt" to Timestamp.now(),
            "updatedAt" to Timestamp.now()
        )

        db.collection("ambulanceRequests")
            .add(requestData)
            .addOnSuccessListener { ref ->
                bookingId = ref.id
                saveActiveBookingId(bookingId)

                tvFindingTitle.text = "Finding nearest ambulance..."
                tvFindingSubTitle.text = "Searching best available driver for emergency"

                listenToBookingUpdates()
                findNearestHospitalAndDriver()
            }
            .addOnFailureListener {
                requestCreated = false
                Toast.makeText(this, it.message ?: "Failed to create request", Toast.LENGTH_SHORT).show()
            }
    }

    private fun findNearestHospitalAndDriver() {
        if (bookingId.isEmpty()) return

        db.collection("hospitals")
            .get()
            .addOnSuccessListener { hospitals ->
                if (hospitals.isEmpty) {
                    hospitalName = "Nearest Hospital"
                    hospitalLat = patientLat
                    hospitalLng = patientLng
                    searchNearestAvailableDriverGlobally("")
                    return@addOnSuccessListener
                }

                var nearestHospitalId = ""
                var nearestHospitalName = ""
                var nearestHospitalLat = 0.0
                var nearestHospitalLng = 0.0
                var nearestDistance = Double.MAX_VALUE

                for (doc in hospitals.documents) {
                    val hLat = doc.getDouble("lat") ?: continue
                    val hLng = doc.getDouble("lng") ?: continue

                    if (hLat == 0.0 || hLng == 0.0) continue

                    val distance = LocationUtils.distanceKm(patientLat, patientLng, hLat, hLng)

                    if (distance < nearestDistance) {
                        nearestDistance = distance
                        nearestHospitalId = doc.id
                        nearestHospitalName = doc.getString("name") ?: "Hospital"
                        nearestHospitalLat = hLat
                        nearestHospitalLng = hLng
                    }
                }

                hospitalName = nearestHospitalName.ifEmpty { "Nearest Hospital" }
                hospitalLat = if (nearestHospitalLat != 0.0) nearestHospitalLat else patientLat
                hospitalLng = if (nearestHospitalLng != 0.0) nearestHospitalLng else patientLng

                searchNearestAvailableDriverGlobally(nearestHospitalId)
            }
            .addOnFailureListener {
                hospitalName = "Nearest Hospital"
                hospitalLat = patientLat
                hospitalLng = patientLng
                searchNearestAvailableDriverGlobally("")
            }
    }

    private fun searchNearestAvailableDriverGlobally(hospitalId: String) {
        db.collection("drivers")
            .whereEqualTo("isAvailable", true)
            .get()
            .addOnSuccessListener { drivers ->
                if (drivers.isEmpty) {
                    markNoDriverAvailable()
                    return@addOnSuccessListener
                }

                var nearestDriverId = ""
                var nearestDistance = Double.MAX_VALUE

                for (doc in drivers.documents) {
                    val dLat = doc.getDouble("currentLat") ?: continue
                    val dLng = doc.getDouble("currentLng") ?: continue

                    if (dLat == 0.0 || dLng == 0.0) continue

                    val distance = LocationUtils.distanceKm(patientLat, patientLng, dLat, dLng)

                    if (distance < nearestDistance) {
                        nearestDistance = distance
                        nearestDriverId = doc.id
                    }
                }

                if (nearestDriverId.isEmpty()) {
                    markNoDriverAvailable()
                    return@addOnSuccessListener
                }

                db.collection("ambulanceRequests")
                    .document(bookingId)
                    .update(
                        mapOf(
                            "status" to "SEARCHING_DRIVER",
                            "hospitalId" to hospitalId,
                            "hospitalName" to hospitalName,
                            "hospitalLat" to hospitalLat,
                            "hospitalLng" to hospitalLng,
                            "targetDriverId" to nearestDriverId,
                            "nearestDistanceKm" to nearestDistance,
                            "updatedAt" to Timestamp.now()
                        )
                    )
            }
            .addOnFailureListener {
                markNoDriverAvailable()
            }
    }

    private fun markNoDriverAvailable() {
        if (bookingId.isEmpty()) return

        db.collection("ambulanceRequests")
            .document(bookingId)
            .update(
                mapOf(
                    "status" to "NO_DRIVER_AVAILABLE",
                    "updatedAt" to Timestamp.now()
                )
            )
    }

    private fun retryFindingDriverIfNeeded() {
        if (noDriverRetryRunning || bookingId.isEmpty()) return

        noDriverRetryRunning = true

        findingDriverLayout.postDelayed({
            noDriverRetryRunning = false

            if (bookingId.isEmpty()) return@postDelayed

            db.collection("ambulanceRequests")
                .document(bookingId)
                .get()
                .addOnSuccessListener { doc ->
                    val status = doc.getString("status") ?: ""
                    if (status == "NO_DRIVER_AVAILABLE") {
                        db.collection("ambulanceRequests")
                            .document(bookingId)
                            .update(
                                mapOf(
                                    "status" to "FINDING_DRIVER",
                                    "updatedAt" to Timestamp.now()
                                )
                            )
                            .addOnSuccessListener {
                                findNearestHospitalAndDriver()
                            }
                    }
                }
        }, 5000)
    }

    private fun listenToBookingUpdates() {
        if (bookingId.isEmpty()) return

        bookingListener?.remove()

        bookingListener = db.collection("ambulanceRequests")
            .document(bookingId)
            .addSnapshotListener { doc, error ->
                if (error != null) {
                    Toast.makeText(this, error.message ?: "Failed to listen ride updates", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (doc == null || !doc.exists()) {
                    clearActiveBooking()
                    Toast.makeText(this, "Booking removed", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addSnapshotListener
                }

                loadBookingFields(doc)

                val status = doc.getString("status") ?: "SEARCHING_DRIVER"
                currentRideStatus = status

                when (status) {
                    "SCHEDULED" -> showScheduledState(doc)
                    "FINDING_DRIVER" -> showFindingDriverState()
                    "SEARCHING_DRIVER" -> showSearchingState()
                    "ACCEPTED" -> showAcceptedState(doc)
                    "REACHED_PATIENT" -> showReachedPatientState(doc)
                    "HOSPITAL_SELECTED" -> showHospitalSelectedState(doc)
                    "NO_DRIVER_AVAILABLE" -> showNoDriverState()
                    "COMPLETED" -> showCompletedState()
                    "CANCELLED_BY_DRIVER" -> showCancelledState("Driver cancelled the ride")
                    "CANCELLED_BY_PATIENT" -> showCancelledState("Ride cancelled")
                    else -> showSearchingState()
                }
            }
    }

    private fun showScheduledState(doc: DocumentSnapshot) {
        findingDriverLayout.visibility = View.VISIBLE
        driverFoundLayout.visibility = View.GONE
        btnCancelRide.visibility = View.VISIBLE

        val date = doc.getString("bookingDate") ?: ""
        val time = doc.getString("bookingTime") ?: ""

        tvFindingTitle.text = "Ambulance Scheduled"
        tvFindingSubTitle.text =
            if (date.isNotEmpty() && time.isNotEmpty()) {
                "Driver will receive your request on $date at $time."
            } else {
                "Driver will receive your request at scheduled time."
            }

        showPatientMarker()
    }

    private fun showFindingDriverState() {
        findingDriverLayout.visibility = View.VISIBLE
        driverFoundLayout.visibility = View.GONE
        btnCancelRide.visibility = View.VISIBLE

        tvFindingTitle.text = "Finding Ambulance"
        tvFindingSubTitle.text = "Searching nearest available ambulance..."

        showPatientMarker()
    }

    private fun showSearchingState() {
        findingDriverLayout.visibility = View.VISIBLE
        driverFoundLayout.visibility = View.GONE
        btnCancelRide.visibility = View.VISIBLE

        tvFindingTitle.text = "Finding Driver"
        tvFindingSubTitle.text = "Waiting for driver to accept your request."

        showPatientMarker()
    }

    private fun showAcceptedState(doc: DocumentSnapshot) {
        findingDriverLayout.visibility = View.GONE
        driverFoundLayout.visibility = View.VISIBLE
        btnCancelRide.visibility = View.VISIBLE

        val driverName = doc.getString("driverName") ?: "Driver"
        val ambulanceNumber = doc.getString("ambulanceNumber") ?: "Ambulance"
        driverPhone = doc.getString("driverPhone") ?: ""

        tvRideStatus.text = "Driver Found"
        tvDriverName.text = driverName
        tvAmbulanceNumber.text = ambulanceNumber

        if (!notificationShownForDriver) {
            notificationShownForDriver = true
            NotificationHelper.showNotification(
                this,
                "Driver assigned",
                "$driverName is coming in ambulance $ambulanceNumber"
            )
        }

        drawRouteDriverToPatient()
    }

    private fun showReachedPatientState(doc: DocumentSnapshot) {
        findingDriverLayout.visibility = View.GONE
        driverFoundLayout.visibility = View.VISIBLE
        btnCancelRide.visibility = View.VISIBLE

        tvRideStatus.text = "Driver Reached"
        tvDriverName.text = doc.getString("driverName") ?: "Driver"
        tvAmbulanceNumber.text = doc.getString("ambulanceNumber") ?: "Ambulance"
        tvDriverLocation.text = "Driver reached your pickup location."

        drawRouteDriverToPatient()
    }

    private fun showHospitalSelectedState(doc: DocumentSnapshot) {
        findingDriverLayout.visibility = View.GONE
        driverFoundLayout.visibility = View.VISIBLE
        btnCancelRide.visibility = View.VISIBLE

        tvRideStatus.text = "Going To Hospital"
        tvDriverName.text = doc.getString("driverName") ?: "Driver"
        tvAmbulanceNumber.text = doc.getString("ambulanceNumber") ?: "Ambulance"
        tvDriverLocation.text = hospitalName.ifEmpty { "Selected Hospital" }

        drawRouteToHospital()
    }

    private fun showNoDriverState() {
        findingDriverLayout.visibility = View.VISIBLE
        driverFoundLayout.visibility = View.GONE
        btnCancelRide.visibility = View.VISIBLE

        tvFindingTitle.text = "No Driver Available"
        tvFindingSubTitle.text = "Waiting for a driver to come online..."

        showPatientMarker()
        retryFindingDriverIfNeeded()
    }

    private fun showCompletedState() {
        clearActiveBooking()
        Toast.makeText(this, "Ride completed", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun showCancelledState(message: String) {
        clearActiveBooking()
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun drawRouteDriverToPatient() {
        if (latestDriverLat == 0.0 || latestDriverLng == 0.0) {
            tvDriverLocation.text = "Waiting for driver live location..."
            showPatientMarker()
            return
        }

        drawRoadRoute(
            startLat = latestDriverLat,
            startLng = latestDriverLng,
            endLat = patientLat,
            endLng = patientLng,
            startIcon = R.drawable.ambulance_marker,
            endIcon = R.drawable.patient_marker,
            showDriverComingText = true
        )
    }

    private fun drawRouteToHospital() {
        if (hospitalLat == 0.0 || hospitalLng == 0.0) {
            tvDriverLocation.text = "Hospital location not available"
            return
        }

        val startLat = if (latestDriverLat != 0.0) latestDriverLat else patientLat
        val startLng = if (latestDriverLng != 0.0) latestDriverLng else patientLng

        drawRoadRoute(
            startLat = startLat,
            startLng = startLng,
            endLat = hospitalLat,
            endLng = hospitalLng,
            startIcon = R.drawable.ambulance_marker,
            endIcon = R.drawable.hospital_marker,
            showDriverComingText = false
        )
    }

    private fun drawRoadRoute(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
        startIcon: Int,
        endIcon: Int,
        showDriverComingText: Boolean
    ) {
        Thread {
            val route = RouteHelper.getRoute(startLat, startLng, endLat, endLng)

            runOnUiThread {
                val startPoint = GeoPoint(startLat, startLng)
                val endPoint = GeoPoint(endLat, endLng)

                map.overlays.clear()

                val line = Polyline()
                line.setPoints(route?.points ?: listOf(startPoint, endPoint))
                line.outlinePaint.color = Color.rgb(244, 63, 70)
                line.outlinePaint.strokeWidth = 9f
                map.overlays.add(line)

                if (route != null) {
                    tvDriverLocation.text =
                        if (showDriverComingText) {
                            "Arriving in ${route.durationText} • ${route.distanceText}"
                        } else {
                            "Route to hospital: ${route.durationText} • ${route.distanceText}"
                        }
                }

                val startMarker = Marker(map)
                startMarker.position = startPoint
                startMarker.icon = ContextCompat.getDrawable(this, startIcon)
                startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                val endMarker = Marker(map)
                endMarker.position = endPoint
                endMarker.icon = ContextCompat.getDrawable(this, endIcon)
                endMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                map.overlays.add(startMarker)
                map.overlays.add(endMarker)

                map.controller.setZoom(16.0)
                map.controller.setCenter(startPoint)
                map.invalidate()
            }
        }.start()
    }

    private fun cancelRide() {
        if (bookingId.isEmpty()) {
            Toast.makeText(this, "Booking not found", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("ambulanceRequests")
            .document(bookingId)
            .update(
                mapOf(
                    "status" to "CANCELLED_BY_PATIENT",
                    "cancelledBy" to "patient",
                    "cancelledAt" to Timestamp.now(),
                    "updatedAt" to Timestamp.now()
                )
            )
            .addOnSuccessListener {
                clearActiveBooking()
                Toast.makeText(this, "Ride cancelled", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, it.message ?: "Failed to cancel ride", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveActiveBookingId(id: String) {
        getSharedPreferences("LifeLineRide", MODE_PRIVATE)
            .edit()
            .putString("activeBookingId", id)
            .apply()
    }

    private fun clearActiveBooking() {
        getSharedPreferences("LifeLineRide", MODE_PRIVATE)
            .edit()
            .remove("activeBookingId")
            .apply()
    }

    private fun callNumber(phone: String) {
        if (phone.isEmpty()) {
            Toast.makeText(this, "Driver phone not available", Toast.LENGTH_SHORT).show()
            return
        }

        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }

    override fun onDestroy() {
        bookingListener?.remove()
        super.onDestroy()
    }
}
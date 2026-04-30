package com.example.lifeline

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Geocoder
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
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

class DriverHome : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var map: MapView
    private lateinit var tvCurrentLocation: TextView

    private lateinit var toggleAvailability: FrameLayout
    private lateinit var toggleCircle: View
    private lateinit var tvAvailabilityStatus: TextView

    private lateinit var noRequestLayout: LinearLayout
    private lateinit var requestLayout: LinearLayout
    private lateinit var tvRequestTitle: TextView
    private lateinit var tvRequesterAddress: TextView
    private lateinit var tvRequesterPhone: TextView

    private lateinit var btnAcceptRequest: Button
    private lateinit var btnLiveLocation: Button
    private lateinit var btnCallPatient: Button
    private lateinit var btnReachedPatient: Button
    private lateinit var btnPickedPatient: Button
    private lateinit var btnCompleteRide: Button
    private lateinit var btnCancelRide: Button

    private var requestListener: ListenerRegistration? = null
    private var activeRideListener: ListenerRegistration? = null

    private val locationHandler = Handler(Looper.getMainLooper())
    private var isLiveLocationRunning = false

    private var alarmPlayer: MediaPlayer? = null
    private val alarmHandler = Handler(Looper.getMainLooper())

    private var isAvailable = true
    private var currentRequestId = ""
    private var rideStatus = ""

    private var driverName = ""
    private var driverPhone = ""
    private var ambulanceNumber = ""

    private var driverLat = 18.7357
    private var driverLng = 73.6756

    private var patientName = "Patient"
    private var patientLat = 18.7369
    private var patientLng = 73.6769
    private var patientPhone = ""
    private var pickupAddress = ""

    private var bookingType = "EMERGENCY"
    private var hospitalName = ""
    private var hospitalLat = 0.0
    private var hospitalLng = 0.0

    private var reachedPatientAuto = false
    private var reachedHospitalAuto = false

    private var routeDrawInProgress = false
    private var routeGeneration = 0
    private var lastRouteDrawAt = 0L
    private var lastRouteMode = ""
    private var lastRouteStartLat = 0.0
    private var lastRouteStartLng = 0.0
    private var lastRouteEndLat = 0.0
    private var lastRouteEndLng = 0.0

    private val liveLocationRunnable = object : Runnable {
        override fun run() {
            if (isLiveLocationRunning && currentRequestId.isNotEmpty()) {
                updateDriverLiveLocationToFirestore()
                locationHandler.postDelayed(this, 5000)
            }
        }
    }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

            if (fine || coarse) {
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

        setContentView(R.layout.activity_driver_home)

        db = FirebaseFirestore.getInstance()

        map = findViewById(R.id.map)
        tvCurrentLocation = findViewById(R.id.tvCurrentLocation)

        toggleAvailability = findViewById(R.id.toggleAvailability)
        toggleCircle = findViewById(R.id.toggleCircle)
        tvAvailabilityStatus = findViewById(R.id.tvAvailabilityStatus)

        noRequestLayout = findViewById(R.id.noRequestLayout)
        requestLayout = findViewById(R.id.requestLayout)
        tvRequestTitle = findViewById(R.id.tvRequestTitle)
        tvRequesterAddress = findViewById(R.id.tvRequesterAddress)
        tvRequesterPhone = findViewById(R.id.tvRequesterPhone)

        btnAcceptRequest = findViewById(R.id.btnAcceptRequest)
        btnLiveLocation = findViewById(R.id.btnLiveLocation)
        btnCallPatient = findViewById(R.id.btnCallPatient)
        btnReachedPatient = findViewById(R.id.btnReachedPatient)
        btnPickedPatient = findViewById(R.id.btnPickedPatient)
        btnCompleteRide = findViewById(R.id.btnCompleteRide)
        btnCancelRide = findViewById(R.id.btnCancelRide)

        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(17.0)

        fetchLoggedInDriverData()
        checkLocationPermissionAndLoadMap()
        updateAvailabilityUI()
        showNoRequest()

        findViewById<TextView>(R.id.btnDriverDashboard).setOnClickListener {
            startActivity(Intent(this, DriverDashboard::class.java))
        }

        toggleAvailability.setOnClickListener {
            isAvailable = !isAvailable
            updateAvailabilityUI()
            updateDriverDocumentLocation()

            if (isAvailable) {
                Toast.makeText(this, "You are available now", Toast.LENGTH_SHORT).show()
                showCurrentLocation()
                resumeActiveRideIfExists()
            } else {
                Toast.makeText(this, "You are unavailable now", Toast.LENGTH_SHORT).show()

                if (currentRequestId.isEmpty()) {
                    stopLiveLocationUpdates()
                    stopRequestAlarm()
                    requestListener?.remove()
                    requestListener = null
                    showNoRequest()
                }
            }
        }

        btnAcceptRequest.setOnClickListener { acceptCurrentRequest() }

        btnLiveLocation.setOnClickListener {
            when (rideStatus) {
                "HOSPITAL_SELECTED" -> drawRoutePatientToHospital(force = true)
                "ACCEPTED", "REACHED_PATIENT" -> drawRouteDriverToPatient(force = true)
                else -> Toast.makeText(this, "Route not available", Toast.LENGTH_SHORT).show()
            }
        }

        btnCallPatient.setOnClickListener { callNumber(patientPhone) }
        btnReachedPatient.setOnClickListener { manualReachedPatient() }
        btnPickedPatient.setOnClickListener { openHospitalMapSelection() }
        btnCompleteRide.setOnClickListener { completeRide() }
        btnCancelRide.setOnClickListener { cancelRideByDriver() }

        resumeActiveRideIfExists()
    }

    private fun fetchLoggedInDriverData() {
        val sharedPref = getSharedPreferences("LifeLineSession", MODE_PRIVATE)
        val collection = sharedPref.getString("collection", "") ?: ""
        val userId = sharedPref.getString("userId", "") ?: ""

        if (collection.isEmpty() || userId.isEmpty()) return

        db.collection(collection)
            .document(userId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    driverName = doc.getString("name") ?: "Driver"
                    driverPhone = doc.getString("phone") ?: ""
                    ambulanceNumber = doc.getString("ambulanceNumber") ?: "Ambulance"
                }
            }
    }

    private fun resumeActiveRideIfExists() {
        val activeRequestId = getSharedPreferences("LifeLineRide", MODE_PRIVATE)
            .getString("activeRequestId", "") ?: ""

        if (activeRequestId.isEmpty()) {
            if (isAvailable) listenForRequests()
            return
        }

        currentRequestId = activeRequestId

        db.collection("ambulanceRequests")
            .document(currentRequestId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    clearActiveRequestId()
                    currentRequestId = ""
                    if (isAvailable) listenForRequests()
                    return@addOnSuccessListener
                }

                val status = doc.getString("status") ?: ""
                rideStatus = status

                if (status == "ACCEPTED" || status == "REACHED_PATIENT" || status == "HOSPITAL_SELECTED") {
                    requestListener?.remove()
                    requestListener = null

                    loadRequestData(doc)
                    listenToActiveRideUpdates()
                    startLiveLocationUpdates()

                    when (status) {
                        "ACCEPTED" -> {
                            showAcceptedRideUI()
                            drawRouteDriverToPatient(force = true)
                        }

                        "REACHED_PATIENT" -> {
                            showReachedPatientUI()
                            drawRouteDriverToPatient(force = true)
                        }

                        "HOSPITAL_SELECTED" -> {
                            showHospitalSelectedUI()
                            drawRoutePatientToHospital(force = true)
                        }
                    }
                } else {
                    clearActiveRequestId()
                    currentRequestId = ""
                    if (isAvailable) listenForRequests()
                }
            }
            .addOnFailureListener {
                clearActiveRequestId()
                currentRequestId = ""
                if (isAvailable) listenForRequests()
            }
    }

    private fun listenForRequests() {
        if (!isAvailable) return
        if (currentRequestId.isNotEmpty()) return

        val sharedPref = getSharedPreferences("LifeLineSession", MODE_PRIVATE)
        val driverId = sharedPref.getString("userId", "") ?: ""

        if (driverId.isEmpty()) {
            Toast.makeText(this, "Driver session not found", Toast.LENGTH_SHORT).show()
            return
        }

        requestListener?.remove()

        requestListener = db.collection("ambulanceRequests")
            .whereEqualTo("status", "SEARCHING_DRIVER")
            .whereEqualTo("targetDriverId", driverId)
            .limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, error.message ?: "Request listener failed", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (currentRequestId.isNotEmpty()) return@addSnapshotListener

                if (snapshot == null || snapshot.isEmpty) {
                    showNoRequest()
                    return@addSnapshotListener
                }

                val doc = snapshot.documents[0]
                val status = doc.getString("status") ?: ""

                if (status != "SEARCHING_DRIVER") return@addSnapshotListener

                currentRequestId = doc.id
                rideStatus = status

                loadRequestData(doc)
                reachedPatientAuto = false
                reachedHospitalAuto = false

                showIncomingRequest()
            }
    }

    private fun loadRequestData(doc: DocumentSnapshot) {
        bookingType = doc.getString("bookingType") ?: "EMERGENCY"

        patientLat = doc.getDouble("pickupLat") ?: 18.7369
        patientLng = doc.getDouble("pickupLng") ?: 73.6769
        patientPhone = doc.getString("patientPhone") ?: ""
        patientName = doc.getString("patientName") ?: "Patient"
        pickupAddress = doc.getString("pickupAddress") ?: "Pickup Location"

        hospitalName = doc.getString("hospitalName") ?: ""
        hospitalLat = doc.getDouble("hospitalLat") ?: 0.0
        hospitalLng = doc.getDouble("hospitalLng") ?: 0.0
    }

    private fun listenToActiveRideUpdates() {
        if (currentRequestId.isEmpty()) return

        activeRideListener?.remove()

        activeRideListener = db.collection("ambulanceRequests")
            .document(currentRequestId)
            .addSnapshotListener { doc, error ->
                if (error != null || doc == null || !doc.exists()) return@addSnapshotListener

                val status = doc.getString("status") ?: ""
                rideStatus = status
                loadRequestData(doc)

                when (status) {
                    "ACCEPTED" -> {
                        requestListener?.remove()
                        requestListener = null
                        showAcceptedRideUI()
                        drawRouteDriverToPatient(force = false)
                    }

                    "REACHED_PATIENT" -> {
                        showReachedPatientUI()
                        drawRouteDriverToPatient(force = false)
                    }

                    "HOSPITAL_SELECTED" -> {
                        showHospitalSelectedUI()
                        drawRoutePatientToHospital(force = false)
                    }

                    "COMPLETED" -> closeRide("Ride completed")
                    "CANCELLED_BY_PATIENT" -> closeRide("Patient cancelled the ride")
                    "CANCELLED_BY_DRIVER" -> closeRide("Ride cancelled")
                    "NO_DRIVER_AVAILABLE" -> closeRide("No driver available")
                }
            }
    }

    private fun hideAllRideButtons() {
        btnAcceptRequest.visibility = View.GONE
        btnLiveLocation.visibility = View.GONE
        btnCallPatient.visibility = View.GONE
        btnReachedPatient.visibility = View.GONE
        btnPickedPatient.visibility = View.GONE
        btnCompleteRide.visibility = View.GONE
        btnCancelRide.visibility = View.GONE
    }

    private fun showNoRequest() {
        currentRequestId = ""
        rideStatus = ""

        noRequestLayout.visibility = View.VISIBLE
        requestLayout.visibility = View.GONE

        hideAllRideButtons()
        stopRequestAlarm()
        resetRouteCache()
    }

    private fun showIncomingRequest() {
        noRequestLayout.visibility = View.GONE
        requestLayout.visibility = View.VISIBLE

        val typeText =
            if (bookingType == "SCHEDULED") "Scheduled ambulance request"
            else "Emergency ambulance request"

        tvRequestTitle.text = "$patientName needs ambulance assistance"
        tvRequesterAddress.text = "$typeText\nAddress: $pickupAddress"
        tvRequesterPhone.text = "Phone: $patientPhone"

        hideAllRideButtons()
        btnAcceptRequest.visibility = View.VISIBLE

        drawRouteDriverToPatient(force = true)
        startRequestAlarm()
    }

    private fun showAcceptedRideUI() {
        noRequestLayout.visibility = View.GONE
        requestLayout.visibility = View.VISIBLE

        tvRequestTitle.text = "Patient: $patientName"
        tvRequesterAddress.text = "Address: $pickupAddress"
        tvRequesterPhone.text = "Phone: $patientPhone"

        hideAllRideButtons()
        btnLiveLocation.visibility = View.VISIBLE
        btnCallPatient.visibility = View.VISIBLE
        btnReachedPatient.visibility = View.VISIBLE
        btnCancelRide.visibility = View.VISIBLE
    }

    private fun showReachedPatientUI() {
        noRequestLayout.visibility = View.GONE
        requestLayout.visibility = View.VISIBLE

        tvRequestTitle.text = "Reached Patient: $patientName"
        tvRequesterAddress.text = "Address: $pickupAddress"
        tvRequesterPhone.text = "Phone: $patientPhone"

        hideAllRideButtons()
        btnLiveLocation.visibility = View.VISIBLE
        btnCallPatient.visibility = View.VISIBLE
        btnPickedPatient.visibility = View.VISIBLE
        btnCancelRide.visibility = View.VISIBLE
    }

    private fun showHospitalSelectedUI() {
        noRequestLayout.visibility = View.GONE
        requestLayout.visibility = View.VISIBLE

        tvRequestTitle.text = "Taking $patientName to hospital"
        tvRequesterAddress.text =
            if (hospitalName.isNotEmpty()) "Hospital: $hospitalName"
            else "Hospital selected"
        tvRequesterPhone.text = "Phone: $patientPhone"

        hideAllRideButtons()
        btnLiveLocation.visibility = View.VISIBLE
        btnCallPatient.visibility = View.VISIBLE
        btnCompleteRide.visibility = View.VISIBLE
        btnCancelRide.visibility = View.VISIBLE
    }

    private fun acceptCurrentRequest() {
        if (currentRequestId.isEmpty()) {
            Toast.makeText(this, "No request found", Toast.LENGTH_SHORT).show()
            return
        }

        val sharedPref = getSharedPreferences("LifeLineSession", MODE_PRIVATE)
        val driverId = sharedPref.getString("userId", "") ?: ""

        if (driverId.isEmpty()) {
            Toast.makeText(this, "Driver session not found", Toast.LENGTH_SHORT).show()
            return
        }

        val requestRef = db.collection("ambulanceRequests").document(currentRequestId)
        val driverRef = db.collection("drivers").document(driverId)

        db.runTransaction { transaction ->
            val requestSnapshot = transaction.get(requestRef)
            val driverSnapshot = transaction.get(driverRef)

            val status = requestSnapshot.getString("status") ?: ""
            val requestBookingType = requestSnapshot.getString("bookingType") ?: "EMERGENCY"
            val scheduledHospitalId = requestSnapshot.getString("hospitalId") ?: ""
            val loggedDriverHospitalId = driverSnapshot.getString("hospitalId") ?: ""

            if (status != "SEARCHING_DRIVER") {
                throw Exception("Already accepted by another driver")
            }

            if (
                requestBookingType == "SCHEDULED" &&
                scheduledHospitalId.isNotEmpty() &&
                loggedDriverHospitalId != scheduledHospitalId
            ) {
                throw Exception("This scheduled patient belongs to another hospital")
            }

            transaction.update(
                requestRef,
                mapOf(
                    "status" to "ACCEPTED",
                    "driverId" to driverId,
                    "driverCollection" to "drivers",
                    "driverName" to driverName,
                    "driverPhone" to driverPhone,
                    "ambulanceNumber" to ambulanceNumber,
                    "driverHospitalId" to loggedDriverHospitalId,
                    "driverLat" to driverLat,
                    "driverLng" to driverLng,
                    "acceptedAt" to Timestamp.now(),
                    "updatedAt" to Timestamp.now()
                )
            )
        }.addOnSuccessListener {
            requestListener?.remove()
            requestListener = null

            stopRequestAlarm()
            saveActiveRequestId()

            rideStatus = "ACCEPTED"

            Toast.makeText(this, "Request accepted", Toast.LENGTH_SHORT).show()

            showAcceptedRideUI()
            listenToActiveRideUpdates()
            startLiveLocationUpdates()
            drawRouteDriverToPatient(force = true)
        }.addOnFailureListener {
            Toast.makeText(this, it.message ?: "Failed to accept request", Toast.LENGTH_SHORT).show()
            currentRequestId = ""
            rideStatus = ""
            if (isAvailable) listenForRequests()
        }
    }

    private fun manualReachedPatient() {
        if (currentRequestId.isEmpty()) {
            Toast.makeText(this, "Request not found", Toast.LENGTH_SHORT).show()
            return
        }

        rideStatus = "REACHED_PATIENT"
        showReachedPatientUI()

        db.collection("ambulanceRequests")
            .document(currentRequestId)
            .update(
                mapOf(
                    "status" to "REACHED_PATIENT",
                    "reachedPatientAt" to Timestamp.now(),
                    "updatedAt" to Timestamp.now()
                )
            )
            .addOnSuccessListener {
                Toast.makeText(this, "Reached patient. Tap Picked Up Patient after pickup.", Toast.LENGTH_SHORT).show()
                drawRouteDriverToPatient(force = true)
            }
            .addOnFailureListener {
                Toast.makeText(this, it.message ?: "Failed to update ride", Toast.LENGTH_SHORT).show()
            }
    }

    private fun checkIfDriverReachedPatient() {
        if (reachedPatientAuto) return
        if (currentRequestId.isEmpty()) return
        if (rideStatus != "ACCEPTED") return

        val distance = LocationUtils.distanceKm(driverLat, driverLng, patientLat, patientLng)

        if (distance <= 0.05) {
            reachedPatientAuto = true
            rideStatus = "REACHED_PATIENT"
            showReachedPatientUI()

            db.collection("ambulanceRequests")
                .document(currentRequestId)
                .update(
                    mapOf(
                        "status" to "REACHED_PATIENT",
                        "reachedPatientAt" to Timestamp.now(),
                        "updatedAt" to Timestamp.now()
                    )
                )
        }
    }

    private fun checkIfDriverReachedHospital() {
        if (reachedHospitalAuto) return
        if (currentRequestId.isEmpty()) return
        if (rideStatus != "HOSPITAL_SELECTED") return
        if (hospitalLat == 0.0 || hospitalLng == 0.0) return

        val distance = LocationUtils.distanceKm(driverLat, driverLng, hospitalLat, hospitalLng)

        if (distance <= 0.07) {
            reachedHospitalAuto = true

            clearRouteAndShowHospital()
            Toast.makeText(this, "Reached hospital. Completing ride...", Toast.LENGTH_SHORT).show()

            db.collection("ambulanceRequests")
                .document(currentRequestId)
                .update(
                    mapOf(
                        "status" to "COMPLETED",
                        "completedAt" to Timestamp.now(),
                        "updatedAt" to Timestamp.now()
                    )
                )
        }
    }

    private fun openHospitalMapSelection() {
        if (currentRequestId.isEmpty()) {
            Toast.makeText(this, "Request not found", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("ambulanceRequests")
            .document(currentRequestId)
            .get()
            .addOnSuccessListener { doc ->
                val requestBookingType = doc.getString("bookingType") ?: "EMERGENCY"

                val fixedHospitalName = doc.getString("hospitalName") ?: ""
                val fixedHospitalLat = doc.getDouble("hospitalLat") ?: 0.0
                val fixedHospitalLng = doc.getDouble("hospitalLng") ?: 0.0

                if (
                    requestBookingType == "SCHEDULED" &&
                    fixedHospitalName.isNotEmpty() &&
                    fixedHospitalLat != 0.0 &&
                    fixedHospitalLng != 0.0
                ) {
                    hospitalName = fixedHospitalName
                    hospitalLat = fixedHospitalLat
                    hospitalLng = fixedHospitalLng
                    rideStatus = "HOSPITAL_SELECTED"

                    db.collection("ambulanceRequests")
                        .document(currentRequestId)
                        .update(
                            mapOf(
                                "status" to "HOSPITAL_SELECTED",
                                "hospitalName" to fixedHospitalName,
                                "hospitalLat" to fixedHospitalLat,
                                "hospitalLng" to fixedHospitalLng,
                                "hospitalSelectedAt" to Timestamp.now(),
                                "updatedAt" to Timestamp.now()
                            )
                        )
                        .addOnSuccessListener {
                            showHospitalSelectedUI()
                            drawRoutePatientToHospital(force = true)
                        }
                } else {
                    val intent = Intent(this, ChooseHospitalActivity::class.java)
                    intent.putExtra("requestId", currentRequestId)
                    intent.putExtra("patientLat", patientLat)
                    intent.putExtra("patientLng", patientLng)
                    startActivity(intent)
                }
            }
    }

    private fun completeRide() {
        if (currentRequestId.isEmpty()) {
            Toast.makeText(this, "Request not found", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("ambulanceRequests")
            .document(currentRequestId)
            .update(
                mapOf(
                    "status" to "COMPLETED",
                    "completedAt" to Timestamp.now(),
                    "updatedAt" to Timestamp.now()
                )
            )
    }

    private fun cancelRideByDriver() {
        if (currentRequestId.isEmpty()) {
            Toast.makeText(this, "Request not found", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("ambulanceRequests")
            .document(currentRequestId)
            .update(
                mapOf(
                    "status" to "CANCELLED_BY_DRIVER",
                    "cancelledBy" to "driver",
                    "cancelledAt" to Timestamp.now(),
                    "updatedAt" to Timestamp.now()
                )
            )
    }

    private fun closeRide(message: String) {
        clearActiveRequestId()
        stopLiveLocationUpdates()
        stopRequestAlarm()
        activeRideListener?.remove()
        activeRideListener = null

        clearRouteAndShowDriver()

        currentRequestId = ""
        rideStatus = ""

        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        showNoRequest()

        if (isAvailable) {
            listenForRequests()
        }
    }

    private fun saveActiveRequestId() {
        getSharedPreferences("LifeLineRide", MODE_PRIVATE)
            .edit()
            .putString("activeRequestId", currentRequestId)
            .apply()
    }

    private fun clearActiveRequestId() {
        getSharedPreferences("LifeLineRide", MODE_PRIVATE)
            .edit()
            .remove("activeRequestId")
            .apply()
    }

    private fun startLiveLocationUpdates() {
        isLiveLocationRunning = true
        locationHandler.removeCallbacks(liveLocationRunnable)
        locationHandler.post(liveLocationRunnable)
    }

    private fun stopLiveLocationUpdates() {
        isLiveLocationRunning = false
        locationHandler.removeCallbacks(liveLocationRunnable)
    }

    @SuppressLint("MissingPermission")
    private fun updateDriverLiveLocationToFirestore() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null && currentRequestId.isNotEmpty()) {
                    driverLat = location.latitude
                    driverLng = location.longitude

                    db.collection("ambulanceRequests")
                        .document(currentRequestId)
                        .update(
                            mapOf(
                                "driverLat" to driverLat,
                                "driverLng" to driverLng,
                                "updatedAt" to Timestamp.now()
                            )
                        )

                    updateDriverDocumentLocation()

                    when (rideStatus) {
                        "ACCEPTED" -> {
                            checkIfDriverReachedPatient()
                            drawRouteDriverToPatient(force = false)
                        }

                        "REACHED_PATIENT" -> {
                            drawRouteDriverToPatient(force = false)
                        }

                        "HOSPITAL_SELECTED" -> {
                            checkIfDriverReachedHospital()
                            drawRoutePatientToHospital(force = false)
                        }
                    }
                }
            }
    }

    private fun updateDriverDocumentLocation() {
        val sharedPref = getSharedPreferences("LifeLineSession", MODE_PRIVATE)
        val driverId = sharedPref.getString("userId", "") ?: ""

        if (driverId.isEmpty()) return

        db.collection("drivers")
            .document(driverId)
            .update(
                mapOf(
                    "isAvailable" to isAvailable,
                    "currentLat" to driverLat,
                    "currentLng" to driverLng,
                    "lastLocationUpdatedAt" to Timestamp.now()
                )
            )
    }

    private fun drawRouteDriverToPatient(force: Boolean = false) {
        if (driverLat == 0.0 || driverLng == 0.0 || patientLat == 0.0 || patientLng == 0.0) {
            showDriverAndPatientMarkersOnly()
            return
        }

        if (!shouldRedrawRoute("DRIVER_TO_PATIENT", driverLat, driverLng, patientLat, patientLng, force)) {
            return
        }

        drawRoadRoute(
            mode = "DRIVER_TO_PATIENT",
            startLat = driverLat,
            startLng = driverLng,
            endLat = patientLat,
            endLng = patientLng,
            startIcon = R.drawable.ambulance_marker,
            endIcon = R.drawable.patient_marker
        )
    }

    private fun drawRoutePatientToHospital(force: Boolean = false) {
        if (hospitalLat == 0.0 || hospitalLng == 0.0) {
            showDriverAndPatientMarkersOnly()
            return
        }

        val startLat = if (driverLat != 0.0) driverLat else patientLat
        val startLng = if (driverLng != 0.0) driverLng else patientLng

        if (!shouldRedrawRoute("DRIVER_TO_HOSPITAL", startLat, startLng, hospitalLat, hospitalLng, force)) {
            return
        }

        drawRoadRoute(
            mode = "DRIVER_TO_HOSPITAL",
            startLat = startLat,
            startLng = startLng,
            endLat = hospitalLat,
            endLng = hospitalLng,
            startIcon = R.drawable.ambulance_marker,
            endIcon = R.drawable.hospital_marker
        )
    }

    private fun shouldRedrawRoute(
        mode: String,
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
        force: Boolean
    ): Boolean {
        if (force) return true

        val now = System.currentTimeMillis()

        if (routeDrawInProgress) return false
        if (lastRouteMode != mode) return true
        if (now - lastRouteDrawAt < 8000) return false

        val movedFromLastStart = LocationUtils.distanceKm(
            lastRouteStartLat,
            lastRouteStartLng,
            startLat,
            startLng
        )

        val movedFromLastEnd = LocationUtils.distanceKm(
            lastRouteEndLat,
            lastRouteEndLng,
            endLat,
            endLng
        )

        return movedFromLastStart >= 0.04 || movedFromLastEnd >= 0.04
    }

    private fun drawRoadRoute(
        mode: String,
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
        startIcon: Int,
        endIcon: Int
    ) {
        val generation = ++routeGeneration
        routeDrawInProgress = true

        Thread {
            val route = RouteHelper.getRoute(startLat, startLng, endLat, endLng)

            runOnUiThread {
                if (generation != routeGeneration) {
                    routeDrawInProgress = false
                    return@runOnUiThread
                }

                val startPoint = GeoPoint(startLat, startLng)
                val endPoint = GeoPoint(endLat, endLng)

                map.overlays.clear()

                val polyline = Polyline()
                polyline.setPoints(route?.points ?: listOf(startPoint, endPoint))
                polyline.outlinePaint.color = Color.rgb(244, 63, 70)
                polyline.outlinePaint.strokeWidth = 9f
                map.overlays.add(polyline)

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

                map.controller.setCenter(startPoint)
                map.controller.setZoom(16.0)
                map.invalidate()

                lastRouteDrawAt = System.currentTimeMillis()
                lastRouteMode = mode
                lastRouteStartLat = startLat
                lastRouteStartLng = startLng
                lastRouteEndLat = endLat
                lastRouteEndLng = endLng

                routeDrawInProgress = false
            }
        }.start()
    }

    private fun showDriverAndPatientMarkersOnly() {
        map.overlays.clear()

        if (driverLat != 0.0 && driverLng != 0.0) {
            val driverPoint = GeoPoint(driverLat, driverLng)

            val driverMarker = Marker(map)
            driverMarker.position = driverPoint
            driverMarker.icon = ContextCompat.getDrawable(this, R.drawable.ambulance_marker)
            driverMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            map.overlays.add(driverMarker)
            map.controller.setCenter(driverPoint)
        }

        if (patientLat != 0.0 && patientLng != 0.0) {
            val patientPoint = GeoPoint(patientLat, patientLng)

            val patientMarker = Marker(map)
            patientMarker.position = patientPoint
            patientMarker.icon = ContextCompat.getDrawable(this, R.drawable.patient_marker)
            patientMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            map.overlays.add(patientMarker)
        }

        map.invalidate()
    }

    private fun clearRouteAndShowHospital() {
        resetRouteCache()
        map.overlays.clear()

        if (hospitalLat != 0.0 && hospitalLng != 0.0) {
            val hospitalPoint = GeoPoint(hospitalLat, hospitalLng)

            val hospitalMarker = Marker(map)
            hospitalMarker.position = hospitalPoint
            hospitalMarker.icon = ContextCompat.getDrawable(this, R.drawable.hospital_marker)
            hospitalMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            hospitalMarker.title = "Hospital Reached"

            map.overlays.add(hospitalMarker)
            map.controller.setCenter(hospitalPoint)
            map.controller.setZoom(17.0)
        }

        map.invalidate()
    }

    private fun clearRouteAndShowDriver() {
        resetRouteCache()
        map.overlays.clear()

        val driverPoint = GeoPoint(driverLat, driverLng)

        val marker = Marker(map)
        marker.position = driverPoint
        marker.icon = ContextCompat.getDrawable(this, R.drawable.ambulance_marker)
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = "Driver Location"

        map.overlays.add(marker)
        map.controller.setCenter(driverPoint)
        map.controller.setZoom(17.0)
        map.invalidate()
    }

    private fun resetRouteCache() {
        routeDrawInProgress = false
        routeGeneration++
        lastRouteDrawAt = 0L
        lastRouteMode = ""
        lastRouteStartLat = 0.0
        lastRouteStartLng = 0.0
        lastRouteEndLat = 0.0
        lastRouteEndLng = 0.0
    }

    private fun callNumber(phone: String) {
        if (phone.isEmpty()) {
            Toast.makeText(this, "Phone number not available", Toast.LENGTH_SHORT).show()
            return
        }

        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
    }

    private fun startRequestAlarm() {
        stopRequestAlarm()

        alarmPlayer = MediaPlayer.create(this, R.raw.request_alarm)
        alarmPlayer?.isLooping = true
        alarmPlayer?.start()

        alarmHandler.postDelayed({
            stopRequestAlarm()
        }, 10_000)
    }

    private fun stopRequestAlarm() {
        alarmHandler.removeCallbacksAndMessages(null)

        alarmPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (_: Exception) {
            }
        }

        alarmPlayer = null
    }

    private fun updateAvailabilityUI() {
        if (isAvailable) {
            toggleAvailability.setBackgroundResource(R.drawable.toggle_on_bg)
            tvAvailabilityStatus.text = "Online"
            tvAvailabilityStatus.setTextColor(ContextCompat.getColor(this, R.color.primaryRed))

            val params = toggleCircle.layoutParams as FrameLayout.LayoutParams
            params.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            params.marginEnd = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._2sdp)
            params.marginStart = 0
            toggleCircle.layoutParams = params
        } else {
            toggleAvailability.setBackgroundResource(R.drawable.toggle_off_bg)
            tvAvailabilityStatus.text = "Offline"
            tvAvailabilityStatus.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))

            val params = toggleCircle.layoutParams as FrameLayout.LayoutParams
            params.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            params.marginStart = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._2sdp)
            params.marginEnd = 0
            toggleCircle.layoutParams = params
        }
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

    @SuppressLint("MissingPermission")
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
                    driverLat = location.latitude
                    driverLng = location.longitude

                    val driverPoint = GeoPoint(driverLat, driverLng)
                    map.controller.setCenter(driverPoint)

                    val marker = Marker(map)
                    marker.position = driverPoint
                    marker.icon = ContextCompat.getDrawable(this, R.drawable.ambulance_marker)
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    marker.title = "Driver Location"

                    map.overlays.clear()
                    map.overlays.add(marker)
                    map.invalidate()

                    setLocationName(driverLat, driverLng)
                    updateDriverDocumentLocation()

                    if (currentRequestId.isEmpty() && isAvailable) {
                        listenForRequests()
                    }
                } else {
                    showDefaultLocation()
                }
            }
            .addOnFailureListener {
                showDefaultLocation()
            }
    }

    private fun showDefaultLocation() {
        driverLat = 18.7357
        driverLng = 73.6756

        val driverPoint = GeoPoint(driverLat, driverLng)
        map.controller.setCenter(driverPoint)

        val marker = Marker(map)
        marker.position = driverPoint
        marker.icon = ContextCompat.getDrawable(this, R.drawable.ambulance_marker)
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = "Driver Location"

        map.overlays.clear()
        map.overlays.add(marker)
        map.invalidate()

        tvCurrentLocation.text = "Talegaon"
        updateDriverDocumentLocation()

        if (currentRequestId.isEmpty() && isAvailable) {
            listenForRequests()
        }
    }

    private fun setLocationName(latitude: Double, longitude: Double) {
        try {
            val addresses = Geocoder(this, Locale.getDefault()).getFromLocation(latitude, longitude, 1)

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

    override fun onResume() {
        super.onResume()
        map.onResume()

        if (currentRequestId.isNotEmpty()) {
            startLiveLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }

    override fun onDestroy() {
        stopLiveLocationUpdates()
        stopRequestAlarm()
        requestListener?.remove()
        activeRideListener?.remove()
        super.onDestroy()
    }
}
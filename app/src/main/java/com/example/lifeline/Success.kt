package com.example.lifeline

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale

class Success : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore

    private var bookingId = ""

    private var hospitalId = ""
    private var hospitalName = ""
    private var hospitalAddress = ""
    private var hospitalLat = 0.0
    private var hospitalLng = 0.0

    private var pickupLocation = ""
    private var pickupLat = 0.0
    private var pickupLng = 0.0

    private var bookingDate = ""
    private var bookingTime = ""
    private var patientName = ""
    private var patientPhone = ""

    private var age = ""
    private var totalAmount = 2
    private var paymentMethod = "online"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_success)

        db = FirebaseFirestore.getInstance()

        val tvSuccessStatus = findViewById<TextView>(R.id.tvSuccessStatus)
        val tvBookingId = findViewById<TextView>(R.id.tvBookingId)
        val tvDateTime = findViewById<TextView>(R.id.tvDateTime)
        val tvPickupLocation = findViewById<TextView>(R.id.tvPickupLocation)
        val tvHospitalName = findViewById<TextView>(R.id.tvHospitalName)

        val btnTrack = findViewById<Button>(R.id.next)
        val btnHome = findViewById<Button>(R.id.next1)

        bookingId = intent.getStringExtra("bookingId") ?: ""
        if (bookingId.isEmpty()) {
            bookingId = "AMB" + System.currentTimeMillis().toString().takeLast(6)
        }

        hospitalId = intent.getStringExtra("hospitalId") ?: ""
        hospitalName = intent.getStringExtra("hospitalName") ?: ""
        hospitalAddress = intent.getStringExtra("hospitalAddress") ?: ""
        hospitalLat = intent.getDoubleExtra("hospitalLat", 0.0)
        hospitalLng = intent.getDoubleExtra("hospitalLng", 0.0)

        pickupLocation = intent.getStringExtra("pickupLocation") ?: ""
        pickupLat = intent.getDoubleExtra("pickupLat", 0.0)
        pickupLng = intent.getDoubleExtra("pickupLng", 0.0)

        bookingDate = intent.getStringExtra("bookingDate") ?: ""
        bookingTime = intent.getStringExtra("bookingTime") ?: ""
        patientName = intent.getStringExtra("patientName") ?: "Patient"
        age = intent.getStringExtra("age") ?: ""
        totalAmount = intent.getIntExtra("totalAmount", 2)
        paymentMethod = intent.getStringExtra("paymentMethod") ?: "online"

        val session = getSharedPreferences("LifeLineSession", MODE_PRIVATE)
        patientPhone = session.getString("userPhone", "") ?: ""

        tvBookingId.text = "#$bookingId"
        tvDateTime.text = "$bookingDate • $bookingTime"
        tvPickupLocation.text = pickupLocation
        tvHospitalName.text = hospitalName
        tvSuccessStatus.text = "Your ambulance booking is confirmed."

        getCurrentLocationThenSchedule()

        btnTrack.setOnClickListener {
            getSharedPreferences("LifeLineRide", MODE_PRIVATE)
                .edit()
                .putString("activeBookingId", bookingId)
                .apply()

            val intent = Intent(this, BookAmbulance::class.java)
            intent.putExtra("bookingId", bookingId)
            startActivity(intent)
        }

        btnHome.setOnClickListener {
            val intent = Intent(this, PatientHome::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun getCurrentLocationThenSchedule() {
        if (pickupLat != 0.0 && pickupLng != 0.0) {
            scheduleOrCreateRequest()
            return
        }

        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            pickupLat = 18.7357
            pickupLng = 73.6756
            scheduleOrCreateRequest()
            return
        }

        LocationServices.getFusedLocationProviderClient(this).lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    pickupLat = location.latitude
                    pickupLng = location.longitude
                } else {
                    pickupLat = 18.7357
                    pickupLng = 73.6756
                }

                scheduleOrCreateRequest()
            }
            .addOnFailureListener {
                pickupLat = 18.7357
                pickupLng = 73.6756
                scheduleOrCreateRequest()
            }
    }

    private fun scheduleOrCreateRequest() {
        val scheduledMillis = getScheduledMillis()

        if (scheduledMillis <= System.currentTimeMillis()) {
            createAmbulanceRequestNow()
        } else {
            createScheduledBookingOnly(scheduledMillis)
            scheduleAlarm(scheduledMillis)
        }
    }

    private fun createScheduledBookingOnly(scheduledMillis: Long) {
        val data = baseRequestData()
        data["scheduledAtMillis"] = scheduledMillis
        data["status"] = "SCHEDULED"

        saveActiveBookingId()

        db.collection("ambulanceRequests")
            .document(bookingId)
            .set(data)
            .addOnSuccessListener {
                Toast.makeText(this, "Ambulance scheduled successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, it.message ?: "Failed to schedule request", Toast.LENGTH_SHORT).show()
            }
    }

    private fun createAmbulanceRequestNow() {
        val data = baseRequestData()
        data["status"] = "FINDING_DRIVER"

        findNearestDriverAndSendRequest(data)
    }

    private fun baseRequestData(): HashMap<String, Any> {
        val session = getSharedPreferences("LifeLineSession", MODE_PRIVATE)

        return hashMapOf(
            "id" to bookingId,
            "bookingType" to "SCHEDULED",

            "patientId" to (session.getString("userId", "") ?: ""),
            "patientCollection" to (session.getString("collection", "") ?: ""),
            "patientName" to patientName,
            "patientPhone" to patientPhone,
            "patientAge" to age,

            "hospitalId" to hospitalId,
            "hospitalName" to hospitalName,
            "hospitalAddress" to hospitalAddress,
            "hospitalLat" to hospitalLat,
            "hospitalLng" to hospitalLng,

            "pickupAddress" to pickupLocation,
            "pickupLat" to pickupLat,
            "pickupLng" to pickupLng,

            "bookingDate" to bookingDate,
            "bookingTime" to bookingTime,

            "paymentMethod" to paymentMethod,
            "totalAmount" to totalAmount,

            "targetDriverId" to "",
            "driverId" to "",
            "driverName" to "",
            "driverPhone" to "",
            "ambulanceNumber" to "",

            "createdAt" to Timestamp.now(),
            "updatedAt" to Timestamp.now()
        )
    }

    private fun findNearestDriverAndSendRequest(data: HashMap<String, Any>) {
        val requestHospitalId = data["hospitalId"] as? String ?: ""

        if (requestHospitalId.isEmpty()) {
            data["status"] = "NO_DRIVER_AVAILABLE"

            db.collection("ambulanceRequests")
                .document(bookingId)
                .set(data)

            Toast.makeText(this, "Hospital is missing. Cannot assign hospital driver.", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("drivers")
            .whereEqualTo("isAvailable", true)
            .whereEqualTo("hospitalId", requestHospitalId)
            .get()
            .addOnSuccessListener { result ->

                if (result.isEmpty) {
                    data["status"] = "NO_DRIVER_AVAILABLE"

                    db.collection("ambulanceRequests")
                        .document(bookingId)
                        .set(data)

                    Toast.makeText(
                        this,
                        "No driver available from selected hospital",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@addOnSuccessListener
                }

                var nearestDriverId = ""
                var nearestDistance = Double.MAX_VALUE

                for (doc in result.documents) {
                    val lat = doc.getDouble("currentLat") ?: continue
                    val lng = doc.getDouble("currentLng") ?: continue

                    val distance = LocationUtils.distanceKm(
                        pickupLat,
                        pickupLng,
                        lat,
                        lng
                    )

                    if (distance < nearestDistance) {
                        nearestDistance = distance
                        nearestDriverId = doc.id
                    }
                }

                if (nearestDriverId.isEmpty()) {
                    data["status"] = "NO_DRIVER_AVAILABLE"

                    db.collection("ambulanceRequests")
                        .document(bookingId)
                        .set(data)

                    Toast.makeText(this, "No nearby hospital driver found", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                data["targetDriverId"] = nearestDriverId
                data["nearestDistanceKm"] = nearestDistance
                data["status"] = "SEARCHING_DRIVER"
                data["updatedAt"] = Timestamp.now()

                saveActiveBookingId()

                db.collection("ambulanceRequests")
                    .document(bookingId)
                    .set(data)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Request sent to hospital driver", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, it.message ?: "Failed to send request", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, it.message ?: "Failed to find driver", Toast.LENGTH_SHORT).show()
            }
    }

    private fun scheduleAlarm(scheduledMillis: Long) {
        val receiverIntent = Intent(this, ScheduledAmbulanceReceiver::class.java)
        receiverIntent.putExtra("bookingId", bookingId)

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            bookingId.hashCode(),
            receiverIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        scheduledMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        scheduledMillis,
                        pendingIntent
                    )

                    Toast.makeText(
                        this,
                        "Exact alarm permission is off. Request is still scheduled.",
                        Toast.LENGTH_LONG
                    ).show()

                    openExactAlarmSettings()
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    scheduledMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                scheduledMillis,
                pendingIntent
            )

            Toast.makeText(
                this,
                "Exact alarm denied. Normal scheduled alarm set.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
            } catch (_: Exception) {
            }
        }
    }

    private fun saveActiveBookingId() {
        getSharedPreferences("LifeLineRide", MODE_PRIVATE)
            .edit()
            .putString("activeBookingId", bookingId)
            .apply()
    }

    private fun getScheduledMillis(): Long {
        return try {
            val format = SimpleDateFormat("dd MMMM yyyy hh:mm a", Locale.ENGLISH)
            val parsedDate = format.parse("$bookingDate $bookingTime")
            parsedDate?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}
package com.example.lifeline

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class DriverDashboard : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore

    private lateinit var tvDriverInfo: TextView
    private lateinit var tvCompletedCount: TextView
    private lateinit var tvCancelledCount: TextView
    private lateinit var tvTotalRequests: TextView
    private lateinit var recentRidesContainer: LinearLayout
    private lateinit var tvNoRides: TextView

    private var driverId = ""
    private var driverEmail = ""
    private var driverName = ""
    private var ambulanceNumber = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_driver_dashboard)

        db = FirebaseFirestore.getInstance()

        val back = findViewById<ImageButton>(R.id.back)
        val btnResetPassword = findViewById<Button>(R.id.btnResetPassword)
        val btnRefresh = findViewById<Button>(R.id.btnRefresh)
        val btnLogout = findViewById<Button>(R.id.btnLogout)

        tvDriverInfo = findViewById(R.id.tvDriverInfo)
        tvCompletedCount = findViewById(R.id.tvCompletedCount)
        tvCancelledCount = findViewById(R.id.tvCancelledCount)
        tvTotalRequests = findViewById(R.id.tvTotalRequests)
        recentRidesContainer = findViewById(R.id.recentRidesContainer)
        tvNoRides = findViewById(R.id.tvNoRides)

        val sharedPref = getSharedPreferences("LifeLineSession", MODE_PRIVATE)
        driverId = sharedPref.getString("userId", "") ?: ""
        driverEmail = sharedPref.getString("userEmail", "") ?: ""

        if (driverId.isEmpty()) {
            Toast.makeText(this, "Driver session not found", Toast.LENGTH_SHORT).show()
            openLogin()
            return
        }

        back.setOnClickListener { finish() }

        btnRefresh.setOnClickListener {
            loadDashboard()
        }

        btnResetPassword.setOnClickListener {
            val intent = Intent(this, ForgetPassword::class.java)
            startActivity(intent)
        }

        btnLogout.setOnClickListener {
            logoutDriver()
        }

        loadDashboard()
    }

    private fun loadDashboard() {
        fetchDriverProfile()
        fetchRideStats()
    }

    private fun fetchDriverProfile() {
        db.collection("drivers")
            .document(driverId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    driverName = doc.getString("name") ?: "Driver"
                    ambulanceNumber = doc.getString("ambulanceNumber") ?: "Ambulance"

                    val phone = doc.getString("phone") ?: ""
                    val available = doc.getBoolean("isAvailable") ?: false
                    val status = if (available) "Online" else "Offline"

                    tvDriverInfo.text =
                        "$driverName • $ambulanceNumber\n$phone • $status"
                } else {
                    tvDriverInfo.text = "Driver profile not found"
                }
            }
            .addOnFailureListener {
                tvDriverInfo.text = "Failed to load driver profile"
            }
    }

    private fun fetchRideStats() {
        db.collection("ambulanceRequests")
            .whereEqualTo("driverId", driverId)
            .get()
            .addOnSuccessListener { result ->
                var completed = 0
                var cancelled = 0

                recentRidesContainer.removeViews(2, recentRidesContainer.childCount - 2)

                for (doc in result.documents) {
                    val status = doc.getString("status") ?: "UNKNOWN"

                    if (status == "COMPLETED") completed++
                    if (status == "CANCELLED_BY_DRIVER" || status == "CANCELLED_BY_PATIENT") cancelled++

                    if (
                        status == "COMPLETED" ||
                        status == "CANCELLED_BY_DRIVER" ||
                        status == "CANCELLED_BY_PATIENT"
                    ) {
                        addRideCard(
                            patientName = doc.getString("patientName") ?: "Patient",
                            phone = doc.getString("patientPhone") ?: "N/A",
                            address = doc.getString("pickupAddress") ?: "Pickup Location",
                            hospital = doc.getString("hospitalName") ?: "Not selected",
                            status = status
                        )
                    }
                }

                tvCompletedCount.text = completed.toString()
                tvCancelledCount.text = cancelled.toString()
                tvTotalRequests.text = "Total handled requests: ${completed + cancelled}"

                tvNoRides.visibility =
                    if (completed + cancelled == 0) android.view.View.VISIBLE
                    else android.view.View.GONE
            }
            .addOnFailureListener {
                Toast.makeText(this, it.message ?: "Failed to load rides", Toast.LENGTH_SHORT).show()
            }
    }

    private fun addRideCard(
        patientName: String,
        phone: String,
        address: String,
        hospital: String,
        status: String
    ) {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(24, 20, 24, 20)
        card.setBackgroundResource(R.drawable.modern_edit_bg)

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 16, 0, 0)
        card.layoutParams = params

        val title = TextView(this)
        title.text = "$patientName • $status"
        title.textSize = 14f
        title.setTextColor(android.graphics.Color.parseColor("#0D2A4E"))
        title.typeface = android.graphics.Typeface.DEFAULT_BOLD

        val details = TextView(this)
        details.text = "Phone: $phone\nPickup: $address\nHospital: $hospital"
        details.textSize = 11f
        details.setTextColor(android.graphics.Color.parseColor("#555555"))
        details.setPadding(0, 8, 0, 0)

        card.addView(title)
        card.addView(details)

        recentRidesContainer.addView(card)
    }

    private fun logoutDriver() {
        db.collection("drivers")
            .document(driverId)
            .update("isAvailable", false)

        getSharedPreferences("LifeLineSession", MODE_PRIVATE)
            .edit()
            .clear()
            .apply()

        getSharedPreferences("LifeLineRide", MODE_PRIVATE)
            .edit()
            .clear()
            .apply()

        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
        openLogin()
    }

    private fun openLogin() {
        val intent = Intent(this, Login::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
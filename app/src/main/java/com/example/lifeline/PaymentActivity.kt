package com.example.lifeline

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.razorpay.Checkout
import com.razorpay.PaymentResultListener
import org.json.JSONObject

class PaymentActivity : AppCompatActivity(), PaymentResultListener {

    private lateinit var db: FirebaseFirestore

    private lateinit var payOnlineBox: LinearLayout
    private lateinit var payOnSiteBox: LinearLayout
    private lateinit var next: Button

    private lateinit var tvAmbulanceFare: TextView
    private lateinit var tvPlatformFee: TextView
    private lateinit var tvGst: TextView
    private lateinit var tvTotalAmount: TextView

    private var selectedPaymentMethod = "online"
    private var totalAmount = 300
    private var appointmentId = ""

    private var hospitalId = ""
    private var hospitalName = ""
    private var hospitalAddress = ""
    private var hospitalLat = 0.0
    private var hospitalLng = 0.0

    private var doctorId = ""
    private var doctorName = ""
    private var doctorPhone = ""
    private var doctorEmail = ""
    private var specialization = ""

    private var appointmentDate = ""
    private var appointmentTime = ""

    private var patientId = ""
    private var patientName = ""
    private var patientAge = ""
    private var patientPhone = ""
    private var patientEmail = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_payment2)

        db = FirebaseFirestore.getInstance()
        Checkout.preload(applicationContext)

        payOnlineBox = findViewById(R.id.payOnlineBox)
        payOnSiteBox = findViewById(R.id.payOnSiteBox)
        next = findViewById(R.id.next)

        tvAmbulanceFare = findViewById(R.id.tvAmbulanceFare)
        tvPlatformFee = findViewById(R.id.tvPlatformFee)
        tvGst = findViewById(R.id.tvGst)
        tvTotalAmount = findViewById(R.id.tvTotalAmount)

        findViewById<ImageButton>(R.id.back).setOnClickListener {
            finish()
        }

        readIntentData()
        setupPriceDetails()
        updatePaymentSelection()

        payOnlineBox.setOnClickListener {
            selectedPaymentMethod = "online"
            updatePaymentSelection()
        }

        payOnSiteBox.setOnClickListener {
            selectedPaymentMethod = "site"
            updatePaymentSelection()
        }

        next.setOnClickListener {
            if (selectedPaymentMethod == "online") {
                startRazorpayPayment()
            } else {
                saveAppointment("PENDING")
            }
        }
    }

    private fun readIntentData() {
        appointmentId = "APT" + System.currentTimeMillis().toString().takeLast(8)

        totalAmount = intent.getIntExtra("totalAmount", 300)

        hospitalId = intent.getStringExtra("hospitalId") ?: ""
        hospitalName = intent.getStringExtra("hospitalName") ?: ""
        hospitalAddress = intent.getStringExtra("hospitalAddress") ?: ""
        hospitalLat = intent.getDoubleExtra("hospitalLat", 0.0)
        hospitalLng = intent.getDoubleExtra("hospitalLng", 0.0)

        doctorId = intent.getStringExtra("doctorId") ?: ""
        doctorName = intent.getStringExtra("doctorName") ?: ""
        doctorPhone = intent.getStringExtra("doctorPhone") ?: ""
        doctorEmail = intent.getStringExtra("doctorEmail") ?: ""
        specialization = intent.getStringExtra("specialization") ?: ""

        appointmentDate = intent.getStringExtra("appointmentDate") ?: ""
        appointmentTime = intent.getStringExtra("appointmentTime") ?: ""

        patientId = intent.getStringExtra("patientId") ?: ""
        patientName = intent.getStringExtra("patientName") ?: "Patient"
        patientAge = intent.getStringExtra("patientAge") ?: ""
        patientPhone = intent.getStringExtra("patientPhone") ?: ""
        patientEmail = intent.getStringExtra("patientEmail") ?: ""
    }

    private fun setupPriceDetails() {
        tvAmbulanceFare.text = "₹$totalAmount"
        tvPlatformFee.text = "₹0"
        tvGst.text = "₹0"
        tvTotalAmount.text = "₹$totalAmount"
    }

    private fun updatePaymentSelection() {
        if (selectedPaymentMethod == "online") {
            payOnlineBox.setBackgroundResource(R.drawable.payment_selected_bg)
            payOnSiteBox.setBackgroundResource(R.drawable.payment_unselected_black_bg)
            next.text = "Pay ₹$totalAmount Securely"
        } else {
            payOnlineBox.setBackgroundResource(R.drawable.payment_unselected_black_bg)
            payOnSiteBox.setBackgroundResource(R.drawable.payment_selected_bg)
            next.text = "Confirm Pay on Site"
        }
    }

    private fun startRazorpayPayment() {
        val checkout = Checkout()
        checkout.setKeyID("rzp_test_Sj7bsQViIGG5G6")

        try {
            val options = JSONObject()
            options.put("name", "LifeLine")
            options.put("description", "Doctor Appointment Payment")
            options.put("currency", "INR")
            options.put("amount", totalAmount * 100)

            val prefill = JSONObject()
            prefill.put("email", patientEmail)
            prefill.put("contact", patientPhone)

            options.put("prefill", prefill)
            checkout.open(this, options)

        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "Payment error", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?) {
        saveAppointment("PAID", razorpayPaymentId ?: "")
    }

    override fun onPaymentError(code: Int, response: String?) {
        Toast.makeText(this, "Payment failed: $response", Toast.LENGTH_LONG).show()
    }

    private fun saveAppointment(paymentStatus: String, razorpayPaymentId: String = "") {
        next.isEnabled = false
        next.text = "Saving appointment..."

        val data = hashMapOf<String, Any>(
            "appointmentId" to appointmentId,

            "patientId" to patientId,
            "patientName" to patientName,
            "patientAge" to patientAge,
            "patientPhone" to patientPhone,
            "patientEmail" to patientEmail,

            "doctorId" to doctorId,
            "doctorName" to doctorName,
            "doctorPhone" to doctorPhone,
            "doctorEmail" to doctorEmail,
            "specialization" to specialization,

            "hospitalId" to hospitalId,
            "hospitalName" to hospitalName,
            "hospitalAddress" to hospitalAddress,
            "hospitalLat" to hospitalLat,
            "hospitalLng" to hospitalLng,

            "appointmentDate" to appointmentDate,
            "appointmentTime" to appointmentTime,

            "totalAmount" to totalAmount,
            "paymentMethod" to selectedPaymentMethod,
            "paymentStatus" to paymentStatus,
            "razorpayPaymentId" to razorpayPaymentId,

            "status" to "BOOKED",
            "createdAt" to Timestamp.now(),
            "updatedAt" to Timestamp.now()
        )

        db.collection("appointments")
            .document(appointmentId)
            .set(data)
            .addOnSuccessListener {
                openDoctorSuccess(paymentStatus)
            }
            .addOnFailureListener {
                next.isEnabled = true
                updatePaymentSelection()
                Toast.makeText(this, it.message ?: "Failed to save appointment", Toast.LENGTH_LONG).show()
            }
    }

    private fun openDoctorSuccess(paymentStatus: String) {
        val intent = Intent(this, DoctorSuccess::class.java)

        intent.putExtra("appointmentId", appointmentId)
        intent.putExtra("doctorName", doctorName)
        intent.putExtra("specialization", specialization)
        intent.putExtra("hospitalName", hospitalName)
        intent.putExtra("hospitalAddress", hospitalAddress)
        intent.putExtra("appointmentDate", appointmentDate)
        intent.putExtra("appointmentTime", appointmentTime)
        intent.putExtra("patientName", patientName)
        intent.putExtra("patientAge", patientAge)
        intent.putExtra("paymentMethod", selectedPaymentMethod)
        intent.putExtra("paymentStatus", paymentStatus)
        intent.putExtra("totalAmount", totalAmount)

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
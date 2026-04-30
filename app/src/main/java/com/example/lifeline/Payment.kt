package com.example.lifeline

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.razorpay.Checkout
import com.razorpay.PaymentResultListener
import org.json.JSONObject

class Payment : AppCompatActivity(), PaymentResultListener {

    private lateinit var payOnlineBox: LinearLayout
    private lateinit var payOnSiteBox: LinearLayout
    private lateinit var next: Button

    private var selectedPaymentMethod = "online"
    private var totalAmount = 2

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
    private var age = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_payment2)

        Checkout.preload(applicationContext)

        payOnlineBox = findViewById(R.id.payOnlineBox)
        payOnSiteBox = findViewById(R.id.payOnSiteBox)
        next = findViewById(R.id.next)

        bookingId = "AMB" + System.currentTimeMillis().toString().takeLast(6)

        totalAmount = intent.getIntExtra("totalAmount", 2)

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

        findViewById<ImageButton>(R.id.back).setOnClickListener {
            finish()
        }

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
                openSuccessActivity()
            }
        }

        updatePaymentSelection()
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
            options.put("description", "Ambulance Booking Payment")
            options.put("currency", "INR")
            options.put("amount", totalAmount * 100)

            val session = getSharedPreferences("LifeLineSession", MODE_PRIVATE)

            val prefill = JSONObject()
            prefill.put("email", session.getString("userEmail", "") ?: "")
            prefill.put("contact", session.getString("userPhone", "") ?: "")

            options.put("prefill", prefill)

            checkout.open(this, options)

        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "Payment error", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?) {
        Toast.makeText(this, "Payment successful", Toast.LENGTH_SHORT).show()
        openSuccessActivity()
    }

    override fun onPaymentError(code: Int, response: String?) {
        Toast.makeText(this, "Payment failed: $response", Toast.LENGTH_LONG).show()
    }

    private fun openSuccessActivity() {
        val intent = Intent(this, Success::class.java)

        intent.putExtra("bookingId", bookingId)

        intent.putExtra("hospitalId", hospitalId)
        intent.putExtra("hospitalName", hospitalName)
        intent.putExtra("hospitalAddress", hospitalAddress)
        intent.putExtra("hospitalLat", hospitalLat)
        intent.putExtra("hospitalLng", hospitalLng)

        intent.putExtra("pickupLocation", pickupLocation)
        intent.putExtra("pickupLat", pickupLat)
        intent.putExtra("pickupLng", pickupLng)

        intent.putExtra("bookingDate", bookingDate)
        intent.putExtra("bookingTime", bookingTime)
        intent.putExtra("patientName", patientName)
        intent.putExtra("age", age)
        intent.putExtra("totalAmount", totalAmount)
        intent.putExtra("paymentMethod", selectedPaymentMethod)

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
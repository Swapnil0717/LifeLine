package com.example.lifeline

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class DoctorSuccess : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activitydoctorsuccess)

        val appointmentId = intent.getStringExtra("appointmentId") ?: "APT"
        val doctorName = intent.getStringExtra("doctorName") ?: "Doctor"
        val specialization = intent.getStringExtra("specialization") ?: "Specialization"
        val hospitalName = intent.getStringExtra("hospitalName") ?: "Hospital"
        val hospitalAddress = intent.getStringExtra("hospitalAddress") ?: ""
        val appointmentDate = intent.getStringExtra("appointmentDate") ?: ""
        val appointmentTime = intent.getStringExtra("appointmentTime") ?: ""
        val paymentMethod = intent.getStringExtra("paymentMethod") ?: "site"
        val paymentStatus = intent.getStringExtra("paymentStatus") ?: "PENDING"
        val totalAmount = intent.getIntExtra("totalAmount", 300)

        findViewById<TextView>(R.id.tvAppointmentId).text = "#$appointmentId"
        findViewById<TextView>(R.id.tvDateTime).text = "$appointmentDate • $appointmentTime"
        findViewById<TextView>(R.id.tvDoctorName).text = doctorName
        findViewById<TextView>(R.id.tvSpecialization).text = specialization
        findViewById<TextView>(R.id.tvHospitalName).text = hospitalName
        findViewById<TextView>(R.id.tvHospitalAddress).text = hospitalAddress
        findViewById<TextView>(R.id.tvPaymentInfo).text =
            "Payment: ${if (paymentMethod == "online") "Online" else "Pay on Site"} • $paymentStatus • ₹$totalAmount"

        findViewById<Button>(R.id.next).setOnClickListener {
            val intent = Intent(this, ViewAppointment::class.java)
            intent.putExtra("appointmentId", appointmentId)
            startActivity(intent)
        }

        findViewById<Button>(R.id.next1).setOnClickListener {
            val intent = Intent(this, Main::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }
    }
}
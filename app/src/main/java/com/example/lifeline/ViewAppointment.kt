package com.example.lifeline

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class ViewAppointment : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore

    private lateinit var tvDoctorName: TextView
    private lateinit var tvSpecialization: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvDateTime: TextView
    private lateinit var tvHospitalName: TextView
    private lateinit var tvHospitalAddress: TextView
    private lateinit var tvPayment: TextView
    private lateinit var tvPatientName: TextView
    private lateinit var tvPatientPhone: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_view_appointment)

        db = FirebaseFirestore.getInstance()

        findViewById<ImageButton>(R.id.back).setOnClickListener {
            finish()
        }

        tvDoctorName = findViewById(R.id.tvDoctorName)
        tvSpecialization = findViewById(R.id.tvSpecialization)
        tvStatus = findViewById(R.id.tvStatus)
        tvDateTime = findViewById(R.id.tvDateTime)
        tvHospitalName = findViewById(R.id.tvHospitalName)
        tvHospitalAddress = findViewById(R.id.tvHospitalAddress)
        tvPayment = findViewById(R.id.tvPayment)
        tvPatientName = findViewById(R.id.tvPatientName)
        tvPatientPhone = findViewById(R.id.tvPatientPhone)

        val appointmentId = intent.getStringExtra("appointmentId") ?: ""

        if (appointmentId.isEmpty()) {
            Toast.makeText(this, "Appointment not available", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadAppointmentDetails(appointmentId)
    }

    private fun loadAppointmentDetails(appointmentId: String) {
        db.collection("appointments")
            .whereEqualTo("appointmentId", appointmentId)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->

                if (snapshot.isEmpty) {
                    Toast.makeText(this, "Appointment data missing", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                val doc = snapshot.documents[0]

                val doctorName = doc.getString("doctorName") ?: "Doctor"
                val specialization = doc.getString("specialization") ?: "Specialization"
                val status = doc.getString("status") ?: "BOOKED"
                val date = doc.getString("appointmentDate") ?: "Date not set"
                val time = doc.getString("appointmentTime") ?: "Time not set"
                val hospitalName = doc.getString("hospitalName") ?: "Hospital"
                val hospitalAddress = doc.getString("hospitalAddress") ?: "Address not available"
                val paymentStatus = doc.getString("paymentStatus") ?: "PENDING"
                val amount = doc.getLong("totalAmount") ?: 0L
                val patientName = doc.getString("patientName") ?: "Patient"
                val patientPhone = doc.getString("patientPhone") ?: "Phone not available"

                tvDoctorName.text = doctorName
                tvSpecialization.text = specialization
                tvStatus.text = status
                tvDateTime.text = "📅 $date • $time"
                tvHospitalName.text = "🏥 $hospitalName"
                tvHospitalAddress.text = hospitalAddress
                tvPayment.text = "Payment: $paymentStatus • ₹$amount"
                tvPatientName.text = "Patient: $patientName"
                tvPatientPhone.text = "Phone: $patientPhone"
            }
            .addOnFailureListener {
                Toast.makeText(this, it.message ?: "Failed to load appointment", Toast.LENGTH_SHORT).show()
            }
    }
}
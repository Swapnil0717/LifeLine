package com.example.lifeline

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

class Doctor : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var recyclerView: RecyclerView
    private lateinit var btn: Button
    private lateinit var back: ImageButton

    private val doctors = mutableListOf<DoctorModel>()
    private lateinit var adapter: DoctorAdapter

    private var selectedPosition = -1

    private var hospitalId = ""
    private var hospitalName = ""
    private var hospitalAddress = ""
    private var hospitalLat = 0.0
    private var hospitalLng = 0.0

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
        setContentView(R.layout.activity_doctor)

        db = FirebaseFirestore.getInstance()

        recyclerView = findViewById(R.id.doctorRecyclerView)
        btn = findViewById(R.id.next)
        back = findViewById(R.id.back)

        hospitalId = intent.getStringExtra("hospitalId") ?: ""
        hospitalName = intent.getStringExtra("hospitalName") ?: ""
        hospitalAddress = intent.getStringExtra("hospitalAddress") ?: ""
        hospitalLat = intent.getDoubleExtra("hospitalLat", 0.0)
        hospitalLng = intent.getDoubleExtra("hospitalLng", 0.0)

        specialization = intent.getStringExtra("specialization") ?: ""
        appointmentDate = intent.getStringExtra("appointmentDate") ?: ""
        appointmentTime = intent.getStringExtra("appointmentTime") ?: ""

        patientId = intent.getStringExtra("patientId") ?: ""
        patientName = intent.getStringExtra("patientName") ?: "Patient"
        patientAge = intent.getStringExtra("patientAge") ?: ""
        patientPhone = intent.getStringExtra("patientPhone") ?: ""
        patientEmail = intent.getStringExtra("patientEmail") ?: ""

        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = DoctorAdapter()
        recyclerView.adapter = adapter

        back.setOnClickListener {
            finish()
        }

        btn.setOnClickListener {
            if (selectedPosition == -1) {
                Toast.makeText(this, "Please select doctor", Toast.LENGTH_SHORT).show()
            } else {
                openAppointmentPayment()
            }
        }

        fetchDoctors()
    }

    private fun fetchDoctors() {
        btn.isEnabled = false
        btn.text = "Loading doctors..."

        db.collection("doctors")
            .whereEqualTo("hospitalId", hospitalId)
            .whereEqualTo("specialization", specialization)
            .get()
            .addOnSuccessListener { snapshot ->

                doctors.clear()

                for (doc in snapshot.documents) {
                    val doctor = DoctorModel(
                        id = doc.id,
                        name = doc.getString("name") ?: "Doctor",
                        specialization = doc.getString("specialization") ?: specialization,
                        hospitalId = doc.getString("hospitalId") ?: hospitalId,
                        hospitalName = doc.getString("hospitalName") ?: hospitalName,
                        hospitalAddress = doc.getString("hospitalAddress") ?: hospitalAddress,
                        phone = doc.getString("phone") ?: "",
                        email = doc.getString("email") ?: "",
                        isOnline = doc.getBoolean("isOnline") ?: true
                    )

                    if (doctor.isOnline) {
                        doctors.add(doctor)
                    }
                }

                adapter.notifyDataSetChanged()

                if (doctors.isEmpty()) {
                    Toast.makeText(
                        this,
                        "No $specialization doctor found in $hospitalName",
                        Toast.LENGTH_LONG
                    ).show()
                }

                btn.isEnabled = true
                btn.text = "Continue"
            }
            .addOnFailureListener {
                btn.isEnabled = true
                btn.text = "Continue"
                Toast.makeText(this, it.message ?: "Failed to load doctors", Toast.LENGTH_SHORT).show()
            }
    }

    private fun openAppointmentPayment() {
        val selectedDoctor = doctors[selectedPosition]

        val intent = Intent(this, PaymentActivity::class.java)

        intent.putExtra("hospitalId", hospitalId)
        intent.putExtra("hospitalName", hospitalName)
        intent.putExtra("hospitalAddress", hospitalAddress)
        intent.putExtra("hospitalLat", hospitalLat)
        intent.putExtra("hospitalLng", hospitalLng)

        intent.putExtra("doctorId", selectedDoctor.id)
        intent.putExtra("doctorName", selectedDoctor.name)
        intent.putExtra("doctorPhone", selectedDoctor.phone)
        intent.putExtra("doctorEmail", selectedDoctor.email)
        intent.putExtra("specialization", selectedDoctor.specialization)

        intent.putExtra("appointmentDate", appointmentDate)
        intent.putExtra("appointmentTime", appointmentTime)

        intent.putExtra("patientId", patientId)
        intent.putExtra("patientName", patientName)
        intent.putExtra("patientAge", patientAge)
        intent.putExtra("patientPhone", patientPhone)
        intent.putExtra("patientEmail", patientEmail)

        intent.putExtra("totalAmount", 300)

        startActivity(intent)
    }

    inner class DoctorAdapter : RecyclerView.Adapter<DoctorAdapter.DoctorViewHolder>() {

        inner class DoctorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val imgDoctor: ImageView = itemView.findViewById(R.id.imgDoctor)
            val tvDoctorName: TextView = itemView.findViewById(R.id.tvDoctorName)
            val tvSpecialization: TextView = itemView.findViewById(R.id.tvSpecialization)
            val tvHospitalName: TextView = itemView.findViewById(R.id.tvHospitalName)
            val radioSelect: RadioButton = itemView.findViewById(R.id.radioSelect)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DoctorViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_doctor, parent, false)
            return DoctorViewHolder(view)
        }

        override fun onBindViewHolder(holder: DoctorViewHolder, position: Int) {

            val currentPosition = holder.bindingAdapterPosition
            if (currentPosition == RecyclerView.NO_POSITION) return

            val doctor = doctors[currentPosition]

            holder.imgDoctor.setImageResource(R.drawable.profile)
            holder.tvDoctorName.text = doctor.name
            holder.tvSpecialization.text = doctor.specialization
            holder.tvHospitalName.text = doctor.hospitalName

            holder.radioSelect.isChecked = selectedPosition == currentPosition

            holder.itemView.setOnClickListener {
                selectedPosition = currentPosition
                notifyDataSetChanged()
            }

            holder.radioSelect.setOnClickListener {
                selectedPosition = currentPosition
                notifyDataSetChanged()
            }
        }

        override fun getItemCount(): Int {
            return doctors.size
        }
    }
}
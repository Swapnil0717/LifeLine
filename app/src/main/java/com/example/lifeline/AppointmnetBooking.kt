package com.example.lifeline

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class AppointmnetBooking : AppCompatActivity() {

    private lateinit var btn: Button
    private lateinit var hospitalName: AutoCompleteTextView
    private lateinit var specialization: AutoCompleteTextView
    private lateinit var date: EditText
    private lateinit var time: EditText
    private lateinit var patientName: EditText
    private lateinit var age: EditText
    private lateinit var db: FirebaseFirestore

    private val specializationList = arrayOf(
        "General Medicine",
        "Emergency",
        "Orthopedic",
        "Gynecology",
        "Pediatrics",
        "General Surgery",
        "Cardiology",
        "Neurology"
    )

    private val hospitalList = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_appointmnet_booking)

        db = FirebaseFirestore.getInstance()

        findViewById<ImageButton>(R.id.back).setOnClickListener {
            finish()
        }

        hospitalName = findViewById(R.id.etHospitalName)
        specialization = findViewById(R.id.etSpecialization)
        date = findViewById(R.id.etDate)
        time = findViewById(R.id.etTime)
        patientName = findViewById(R.id.etPatientName)
        age = findViewById(R.id.etAge)
        btn = findViewById(R.id.next)

        setupHospitalDropdown()
        setupSpecializationDropdown()

        date.setOnClickListener { openDatePicker() }
        time.setOnClickListener { openTimePicker() }

        btn.setOnClickListener {
            validateAndFindHospital()
        }
    }

    private fun setupHospitalDropdown() {
        db.collection("hospitals")
            .get()
            .addOnSuccessListener { snapshot ->
                hospitalList.clear()

                for (doc in snapshot.documents) {
                    val name = doc.getString("name") ?: ""
                    if (name.isNotEmpty() && !hospitalList.contains(name)) {
                        hospitalList.add(name)
                    }
                }

                val adapter = HospitalDropdownAdapter(hospitalList)
                hospitalName.setAdapter(adapter)
                hospitalName.threshold = 1

                hospitalName.setOnClickListener {
                    hospitalName.showDropDown()
                }

                hospitalName.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) hospitalName.showDropDown()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load hospitals", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupSpecializationDropdown() {
        val adapter = SpecializationDropdownAdapter(specializationList.toList())
        specialization.setAdapter(adapter)
        specialization.threshold = 1

        specialization.setOnClickListener {
            specialization.showDropDown()
        }

        specialization.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) specialization.showDropDown()
        }
    }

    private fun openDatePicker() {
        val calendar = Calendar.getInstance()

        val picker = DatePickerDialog(
            this,
            { _, year, month, day ->
                val selected = Calendar.getInstance()
                selected.set(year, month, day)

                val format = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                date.setText(format.format(selected.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        picker.datePicker.minDate = System.currentTimeMillis()
        picker.show()
    }

    private fun openTimePicker() {
        val calendar = Calendar.getInstance()

        TimePickerDialog(
            this,
            { _, hour, minute ->
                val selected = Calendar.getInstance()
                selected.set(Calendar.HOUR_OF_DAY, hour)
                selected.set(Calendar.MINUTE, minute)

                val format = SimpleDateFormat("hh:mm a", Locale.getDefault())
                time.setText(format.format(selected.time))
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            false
        ).show()
    }

    private fun validateAndFindHospital() {
        val hospitalText = hospitalName.text.toString().trim()
        val specializationText = specialization.text.toString().trim()
        val dateText = date.text.toString().trim()
        val timeText = time.text.toString().trim()
        val patientText = patientName.text.toString().trim()
        val ageText = age.text.toString().trim()

        when {
            hospitalText.isEmpty() -> {
                hospitalName.error = "Enter hospital name"
                hospitalName.requestFocus()
                hospitalName.showDropDown()
            }

            specializationText.isEmpty() -> {
                specialization.error = "Select specialization"
                specialization.requestFocus()
                specialization.showDropDown()
            }

            !specializationList.any { it.equals(specializationText, ignoreCase = true) } -> {
                specialization.error = "Select valid specialization"
                specialization.requestFocus()
                specialization.showDropDown()
            }

            dateText.isEmpty() -> {
                date.error = "Select date"
            }

            timeText.isEmpty() -> {
                time.error = "Select time"
            }

            ageText.isNotEmpty() && ageText.toIntOrNull() == null -> {
                age.error = "Enter valid age"
            }

            else -> {
                setLoading(true)
                Toast.makeText(this, "Checking hospital location...", Toast.LENGTH_SHORT).show()

                HospitalLocationHelper.findOrCreateHospital(
                    context = this,
                    hospitalName = hospitalText,
                    role = "appointment",
                    onSuccess = { hospital ->
                        runOnUiThread {
                            setLoading(false)

                            val session = getSharedPreferences("LifeLineSession", MODE_PRIVATE)

                            val intent = Intent(this, Doctor::class.java)

                            intent.putExtra("hospitalId", hospital.id)
                            intent.putExtra("hospitalName", hospital.name)
                            intent.putExtra("hospitalAddress", hospital.address)
                            intent.putExtra("hospitalLat", hospital.lat)
                            intent.putExtra("hospitalLng", hospital.lng)

                            intent.putExtra("specialization", getCorrectSpecialization(specializationText))
                            intent.putExtra("appointmentDate", dateText)
                            intent.putExtra("appointmentTime", timeText)

                            intent.putExtra(
                                "patientName",
                                patientText.ifEmpty {
                                    session.getString("userName", "Patient") ?: "Patient"
                                }
                            )

                            intent.putExtra("patientAge", ageText)
                            intent.putExtra("patientId", session.getString("userId", "") ?: "")
                            intent.putExtra("patientPhone", session.getString("userPhone", "") ?: "")
                            intent.putExtra("patientEmail", session.getString("userEmail", "") ?: "")

                            startActivity(intent)
                        }
                    },
                    onFailure = { message ->
                        runOnUiThread {
                            setLoading(false)
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }
        }
    }

    private fun getCorrectSpecialization(input: String): String {
        return specializationList.firstOrNull {
            it.equals(input.trim(), ignoreCase = true)
        } ?: input.trim()
    }

    private fun setLoading(isLoading: Boolean) {
        btn.isEnabled = !isLoading
        btn.text = if (isLoading) "Please wait..." else "Continue"
    }

    inner class HospitalDropdownAdapter(
        private val items: List<String>
    ) : ArrayAdapter<String>(this, R.layout.item_hospital_dropdown, items) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(
                R.layout.item_hospital_dropdown,
                parent,
                false
            )

            val tvName = view.findViewById<TextView>(R.id.tvHospitalName)
            val tvSub = view.findViewById<TextView>(R.id.tvHospitalSub)

            tvName.text = items[position]
            tvSub.text = "Tap to select hospital"

            return view
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            return getView(position, convertView, parent)
        }
    }

    inner class SpecializationDropdownAdapter(
        private val items: List<String>
    ) : ArrayAdapter<String>(this, R.layout.item_specialization_dropdown, items) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(
                R.layout.item_specialization_dropdown,
                parent,
                false
            )

            val tvName = view.findViewById<TextView>(R.id.tvSpecializationName)
            val tvSub = view.findViewById<TextView>(R.id.tvSpecializationSub)

            tvName.text = items[position]
            tvSub.text = getSpecializationSubText(items[position])

            return view
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            return getView(position, convertView, parent)
        }
    }

    private fun getSpecializationSubText(value: String): String {
        return when (value) {
            "General Medicine" -> "Fever, BP, diabetes, infection"
            "Emergency" -> "Urgent and critical care"
            "Orthopedic" -> "Bone, fracture, injury"
            "Gynecology" -> "Pregnancy and women care"
            "Pediatrics" -> "Child and baby specialist"
            "General Surgery" -> "Surgery and wound care"
            "Cardiology" -> "Heart and chest pain"
            "Neurology" -> "Brain, nerves, stroke"
            else -> "Tap to select specialization"
        }
    }
}
package com.example.lifeline

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class DoctorRegister : AppCompatActivity() {

    private var isPasswordVisible = false
    private var isConfirmPasswordVisible = false

    private lateinit var btn: Button
    private lateinit var btnProgress: ProgressBar

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_doctor_register)

        val back = findViewById<ImageButton>(R.id.back)
        val doctorName = findViewById<EditText>(R.id.etDoctorName)
        val specialization = findViewById<AutoCompleteTextView>(R.id.etSpecialization)
        val hospitalName = findViewById<EditText>(R.id.etHospitalName)
        val email = findViewById<EditText>(R.id.etEmail)
        val phone = findViewById<EditText>(R.id.etPhone)
        val password = findViewById<EditText>(R.id.etPassword)
        val confirmPassword = findViewById<EditText>(R.id.etConfirmPassword)
        val togglePassword = findViewById<ImageView>(R.id.ivTogglePassword)
        val toggleConfirmPassword = findViewById<ImageView>(R.id.ivToggleConfirmPassword)

        btn = findViewById(R.id.next)
        btnProgress = findViewById(R.id.btnProgress)

        val tvLogin = findViewById<TextView>(R.id.tvLogin)

        setupSpecializationDropdown(specialization)

        back.setOnClickListener { finish() }

        tvLogin.setOnClickListener {
            startActivity(Intent(this, Login::class.java))
        }

        togglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            togglePasswordVisibility(password, isPasswordVisible)
        }

        toggleConfirmPassword.setOnClickListener {
            isConfirmPasswordVisible = !isConfirmPasswordVisible
            togglePasswordVisibility(confirmPassword, isConfirmPasswordVisible)
        }

        btn.setOnClickListener {
            val nameText = doctorName.text.toString().trim()
            val specializationText = specialization.text.toString().trim()
            val hospitalText = hospitalName.text.toString().trim()
            val emailText = email.text.toString().trim()
            val phoneText = phone.text.toString().trim()
            val passwordText = password.text.toString().trim()
            val confirmPasswordText = confirmPassword.text.toString().trim()

            when {
                nameText.isEmpty() -> {
                    doctorName.error = "Enter doctor name"
                    doctorName.requestFocus()
                }

                nameText.length < 3 -> {
                    doctorName.error = "Name must be at least 3 characters"
                    doctorName.requestFocus()
                }

                specializationText.isEmpty() -> {
                    specialization.error = "Select specialization"
                    specialization.requestFocus()
                    specialization.showDropDown()
                }

                !isValidSpecialization(specializationText) -> {
                    specialization.error = "Please select valid specialization from list"
                    specialization.requestFocus()
                    specialization.showDropDown()
                }

                hospitalText.isEmpty() -> {
                    hospitalName.error = "Enter hospital name"
                    hospitalName.requestFocus()
                }

                emailText.isEmpty() -> {
                    email.error = "Enter email"
                    email.requestFocus()
                }

                !Patterns.EMAIL_ADDRESS.matcher(emailText).matches() -> {
                    email.error = "Enter valid email"
                    email.requestFocus()
                }

                phoneText.isEmpty() -> {
                    phone.error = "Enter phone number"
                    phone.requestFocus()
                }

                phoneText.length != 10 -> {
                    phone.error = "Enter valid 10 digit phone number"
                    phone.requestFocus()
                }

                passwordText.isEmpty() -> {
                    password.error = "Enter password"
                    password.requestFocus()
                }

                passwordText.length < 6 -> {
                    password.error = "Password must be at least 6 characters"
                    password.requestFocus()
                }

                confirmPasswordText.isEmpty() -> {
                    confirmPassword.error = "Confirm password"
                    confirmPassword.requestFocus()
                }

                passwordText != confirmPasswordText -> {
                    confirmPassword.error = "Password does not match"
                    confirmPassword.requestFocus()
                }

                else -> {
                    setButtonLoading(true)
                    Toast.makeText(this, "Finding hospital location...", Toast.LENGTH_SHORT).show()

                    HospitalLocationHelper.findOrCreateHospital(
                        context = this,
                        hospitalName = hospitalText,
                        role = "doctor",
                        onSuccess = { hospital ->
                            runOnUiThread {
                                setButtonLoading(false)

                                val intent = Intent(this, OtpVerification::class.java)

                                intent.putExtra("role", "doctor")
                                intent.putExtra("name", nameText)
                                intent.putExtra("specialization", getCorrectSpecializationName(specializationText))

                                intent.putExtra("hospitalId", hospital.id)
                                intent.putExtra("hospitalName", hospital.name)
                                intent.putExtra("hospitalAddress", hospital.address)
                                intent.putExtra("hospitalLat", hospital.lat)
                                intent.putExtra("hospitalLng", hospital.lng)

                                intent.putExtra("email", emailText)
                                intent.putExtra("phone", phoneText)
                                intent.putExtra("password", PasswordUtils.hashPassword(passwordText))

                                startActivity(intent)
                            }
                        },
                        onFailure = { message ->
                            runOnUiThread {
                                setButtonLoading(false)
                                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                }
            }
        }
    }

    private fun setupSpecializationDropdown(specialization: AutoCompleteTextView) {
        val adapter = SpecializationDropdownAdapter(specializationList.toList())

        specialization.setAdapter(adapter)
        specialization.threshold = 1

        specialization.setOnClickListener {
            specialization.showDropDown()
        }

        specialization.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                specialization.showDropDown()
            }
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

            view.findViewById<TextView>(R.id.tvSpecializationName).text = items[position]
            view.findViewById<TextView>(R.id.tvSpecializationSub).text =
                getSpecializationSubText(items[position])

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

    private fun isValidSpecialization(input: String): Boolean {
        return specializationList.any {
            it.equals(input.trim(), ignoreCase = true)
        }
    }

    private fun getCorrectSpecializationName(input: String): String {
        return specializationList.firstOrNull {
            it.equals(input.trim(), ignoreCase = true)
        } ?: input.trim()
    }

    override fun onResume() {
        super.onResume()

        if (::btn.isInitialized) {
            setButtonLoading(false)
        }
    }

    private fun setButtonLoading(isLoading: Boolean) {
        btn.isEnabled = !isLoading
        btn.text = if (isLoading) "" else "Register Doctor"
        btnProgress.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun togglePasswordVisibility(editText: EditText, isVisible: Boolean) {
        editText.inputType =
            if (isVisible) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }

        editText.setSelection(editText.text.length)
    }
}
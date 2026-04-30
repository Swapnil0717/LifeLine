package com.example.lifeline

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class DriverRegister : AppCompatActivity() {

    private var isPasswordVisible = false
    private var isConfirmPasswordVisible = false

    private lateinit var btn: Button
    private lateinit var btnProgress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_driver_register)

        val back = findViewById<ImageButton>(R.id.back)
        val driverName = findViewById<EditText>(R.id.etDoctorName)
        val ambulanceNumber = findViewById<EditText>(R.id.etambulancenumbere)
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

        back.setOnClickListener {
            finish()
        }

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
            val nameText = driverName.text.toString().trim()
            val ambulanceText = ambulanceNumber.text.toString().trim()
            val hospitalText = hospitalName.text.toString().trim()
            val emailText = email.text.toString().trim()
            val phoneText = phone.text.toString().trim()
            val passwordText = password.text.toString().trim()
            val confirmPasswordText = confirmPassword.text.toString().trim()

            when {
                nameText.isEmpty() -> driverName.error = "Enter driver name"
                nameText.length < 3 -> driverName.error = "Name must be at least 3 characters"

                ambulanceText.isEmpty() -> ambulanceNumber.error = "Enter ambulance number"

                hospitalText.isEmpty() -> hospitalName.error = "Enter hospital name"

                emailText.isEmpty() -> email.error = "Enter email"
                !Patterns.EMAIL_ADDRESS.matcher(emailText).matches() -> email.error = "Enter valid email"

                phoneText.isEmpty() -> phone.error = "Enter phone number"
                phoneText.length != 10 -> phone.error = "Enter valid 10 digit phone number"

                passwordText.isEmpty() -> password.error = "Enter password"
                passwordText.length < 6 -> password.error = "Password must be at least 6 characters"

                confirmPasswordText.isEmpty() -> confirmPassword.error = "Confirm password"
                passwordText != confirmPasswordText -> confirmPassword.error = "Password does not match"

                else -> {
                    setButtonLoading(true)
                    Toast.makeText(this, "Finding hospital location...", Toast.LENGTH_SHORT).show()

                    HospitalLocationHelper.findOrCreateHospital(
                        context = this,
                        hospitalName = hospitalText,
                        role = "driver",
                        onSuccess = { hospital ->
                            runOnUiThread {
                                val intent = Intent(this, OtpVerification::class.java)

                                intent.putExtra("role", "driver")
                                intent.putExtra("name", nameText)
                                intent.putExtra("ambulanceNumber", ambulanceText)

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

    override fun onResume() {
        super.onResume()

        if (::btn.isInitialized) {
            setButtonLoading(false)
        }
    }

    private fun setButtonLoading(isLoading: Boolean) {
        btn.isEnabled = !isLoading
        btn.text = if (isLoading) "" else "Register Driver"
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
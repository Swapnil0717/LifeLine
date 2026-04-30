package com.example.lifeline

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class PatientRegister : AppCompatActivity() {

    private var isPasswordVisible = false
    private var isConfirmPasswordVisible = false

    private lateinit var registerBtn: Button
    private lateinit var btnProgress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)

        val back = findViewById<ImageButton>(R.id.back)
        val name = findViewById<EditText>(R.id.etName)
        val email = findViewById<EditText>(R.id.etEmail)
        val phone = findViewById<EditText>(R.id.etPhone)
        val password = findViewById<EditText>(R.id.etPassword)
        val confirmPassword = findViewById<EditText>(R.id.etConfirmPassword)
        val togglePassword = findViewById<ImageView>(R.id.ivTogglePassword)
        val toggleConfirmPassword = findViewById<ImageView>(R.id.ivToggleConfirmPassword)
        registerBtn = findViewById(R.id.next)
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

        registerBtn.setOnClickListener {
            val fullName = name.text.toString().trim()
            val userEmail = email.text.toString().trim()
            val userPhone = phone.text.toString().trim()
            val userPassword = password.text.toString().trim()
            val userConfirmPassword = confirmPassword.text.toString().trim()

            when {
                fullName.isEmpty() -> name.error = "Enter full name"
                fullName.length < 3 -> name.error = "Name must be at least 3 characters"

                userEmail.isEmpty() -> email.error = "Enter email"
                !Patterns.EMAIL_ADDRESS.matcher(userEmail).matches() -> email.error = "Enter valid email"

                userPhone.isEmpty() -> phone.error = "Enter phone number"
                userPhone.length != 10 -> phone.error = "Enter valid 10 digit phone number"

                userPassword.isEmpty() -> password.error = "Enter password"
                userPassword.length < 6 -> password.error = "Password must be at least 6 characters"

                userConfirmPassword.isEmpty() -> confirmPassword.error = "Confirm password"
                userPassword != userConfirmPassword -> confirmPassword.error = "Password does not match"

                else -> {
                    setButtonLoading(true)

                    val intent = Intent(this, OtpVerification::class.java)
                    intent.putExtra("role", "patient")
                    intent.putExtra("name", fullName)
                    intent.putExtra("email", userEmail)
                    intent.putExtra("phone", userPhone)
                    intent.putExtra("password", PasswordUtils.hashPassword(userPassword))

                    startActivity(intent)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (::registerBtn.isInitialized) {
            setButtonLoading(false)
        }
    }

    private fun setButtonLoading(isLoading: Boolean) {
        registerBtn.isEnabled = !isLoading
        registerBtn.text = if (isLoading) "" else "Register"
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
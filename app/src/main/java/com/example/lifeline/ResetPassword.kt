package com.example.lifeline

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class ResetPassword : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore

    private lateinit var btnReset: Button
    private lateinit var btnProgress: ProgressBar

    private var isPasswordVisible = false
    private var isConfirmPasswordVisible = false

    private var token = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_reset_password)

        db = FirebaseFirestore.getInstance()

        token = intent?.data?.getQueryParameter("token")
            ?: intent.getStringExtra("token")
                    ?: ""

        val back = findViewById<ImageButton>(R.id.back)
        val tvEmail = findViewById<TextView>(R.id.tvEmail)
        val password = findViewById<EditText>(R.id.etPassword)
        val confirmPassword = findViewById<EditText>(R.id.etConfirmPassword)
        val togglePassword = findViewById<ImageView>(R.id.ivTogglePassword)
        val toggleConfirmPassword = findViewById<ImageView>(R.id.ivToggleConfirmPassword)

        btnReset = findViewById(R.id.btnReset)
        btnProgress = findViewById(R.id.btnProgress)

        back.setOnClickListener {
            startActivity(Intent(this, Login::class.java))
            finish()
        }

        togglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            togglePasswordVisibility(password, isPasswordVisible)
        }

        toggleConfirmPassword.setOnClickListener {
            isConfirmPasswordVisible = !isConfirmPasswordVisible
            togglePasswordVisibility(confirmPassword, isConfirmPasswordVisible)
        }

        if (token.isEmpty()) {
            Toast.makeText(this, "Invalid reset link", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, ForgetPassword::class.java))
            finish()
            return
        }

        loadTokenInfo(tvEmail)

        btnReset.setOnClickListener {
            val newPassword = password.text.toString().trim()
            val confirmNewPassword = confirmPassword.text.toString().trim()

            when {
                newPassword.isEmpty() -> password.error = "Enter new password"
                newPassword.length < 6 -> password.error = "Password must be at least 6 characters"
                confirmNewPassword.isEmpty() -> confirmPassword.error = "Confirm password"
                newPassword != confirmNewPassword -> confirmPassword.error = "Password does not match"
                else -> resetPassword(newPassword)
            }
        }
    }

    private fun loadTokenInfo(tvEmail: TextView) {
        setLoading(true)

        db.collection("passwordResetTokens")
            .document(token)
            .get()
            .addOnSuccessListener { doc ->
                setLoading(false)

                if (!doc.exists()) {
                    Toast.makeText(this, "Invalid reset link", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, ForgetPassword::class.java))
                    finish()
                    return@addOnSuccessListener
                }

                val used = doc.getBoolean("used") ?: false
                val expiresAt = doc.getLong("expiresAt") ?: 0L
                val email = doc.getString("email") ?: ""

                when {
                    used -> {
                        Toast.makeText(this, "Reset link already used", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, ForgetPassword::class.java))
                        finish()
                    }

                    System.currentTimeMillis() > expiresAt -> {
                        Toast.makeText(this, "Reset link expired", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, ForgetPassword::class.java))
                        finish()
                    }

                    else -> {
                        tvEmail.text = "Reset password for $email"
                    }
                }
            }
            .addOnFailureListener {
                setLoading(false)
                Toast.makeText(this, it.message ?: "Failed to load reset link", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun resetPassword(newPassword: String) {
        setLoading(true)

        db.collection("passwordResetTokens")
            .document(token)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    setLoading(false)
                    Toast.makeText(this, "Invalid reset link", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val used = doc.getBoolean("used") ?: false
                val expiresAt = doc.getLong("expiresAt") ?: 0L
                val collection = doc.getString("collection") ?: ""
                val userId = doc.getString("userId") ?: ""

                when {
                    used -> {
                        setLoading(false)
                        Toast.makeText(this, "Reset link already used", Toast.LENGTH_SHORT).show()
                    }

                    System.currentTimeMillis() > expiresAt -> {
                        setLoading(false)
                        Toast.makeText(this, "Reset link expired", Toast.LENGTH_SHORT).show()
                    }

                    collection.isEmpty() || userId.isEmpty() -> {
                        setLoading(false)
                        Toast.makeText(this, "Invalid reset data", Toast.LENGTH_SHORT).show()
                    }

                    else -> {
                        val hashedPassword = PasswordUtils.hashPassword(newPassword)

                        db.collection(collection)
                            .document(userId)
                            .update("password", hashedPassword)
                            .addOnSuccessListener {
                                db.collection("passwordResetTokens")
                                    .document(token)
                                    .update("used", true)
                                    .addOnSuccessListener {
                                        setLoading(false)
                                        Toast.makeText(
                                            this,
                                            "Password reset successful",
                                            Toast.LENGTH_SHORT
                                        ).show()

                                        val intent = Intent(this, Login::class.java)
                                        intent.flags =
                                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                        startActivity(intent)
                                        finish()
                                    }
                                    .addOnFailureListener {
                                        setLoading(false)
                                        Toast.makeText(
                                            this,
                                            it.message ?: "Password changed but token not updated",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                            }
                            .addOnFailureListener {
                                setLoading(false)
                                Toast.makeText(
                                    this,
                                    it.message ?: "Failed to reset password",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    }
                }
            }
            .addOnFailureListener {
                setLoading(false)
                Toast.makeText(this, it.message ?: "Failed to verify reset link", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setLoading(isLoading: Boolean) {
        btnReset.isEnabled = !isLoading
        btnReset.text = if (isLoading) "" else "Reset Password"
        btnProgress.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun togglePasswordVisibility(editText: EditText, isVisible: Boolean) {
        editText.inputType =
            if (isVisible) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

        editText.setSelection(editText.text.length)
    }
}
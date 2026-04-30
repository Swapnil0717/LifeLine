package com.example.lifeline

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class Login : AppCompatActivity() {

    private var isPasswordVisible = false
    private lateinit var db: FirebaseFirestore

    private val collections = listOf(
        LoginCollection("patients", "patient", PatientHome::class.java),
        LoginCollection("doctors", "doctor", DoctorHome::class.java),
        LoginCollection("drivers", "driver", DriverHome::class.java)
    )

    data class LoginCollection(
        val collection: String,
        val role: String,
        val homeClass: Class<*>
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (isUserAlreadyLoggedIn()) {
            openHomeByRole()
            return
        }

        setContentView(R.layout.activity_login)

        db = FirebaseFirestore.getInstance()

        val back = findViewById<ImageButton>(R.id.back)
        val email = findViewById<EditText>(R.id.email)
        val password = findViewById<EditText>(R.id.etPassword)
        val toggle = findViewById<ImageView>(R.id.ivToggle)
        val forgot = findViewById<TextView>(R.id.forgot)
        val register = findViewById<TextView>(R.id.tvRegister)
        val btn = findViewById<Button>(R.id.next)

        back.setOnClickListener { finish() }

        toggle.setOnClickListener {
            isPasswordVisible = !isPasswordVisible

            password.inputType =
                if (isPasswordVisible) {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                } else {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }

            password.setSelection(password.text.length)
        }

        forgot.setOnClickListener {
            startActivity(Intent(this, ForgetPassword::class.java))
        }

        register.setOnClickListener {
            startActivity(Intent(this, Register::class.java))
        }

        btn.setOnClickListener {
            val userEmail = email.text.toString().trim()
            val userPassword = password.text.toString().trim()

            when {
                userEmail.isEmpty() -> email.error = "Please enter email"
                !Patterns.EMAIL_ADDRESS.matcher(userEmail).matches() -> email.error = "Enter valid email"
                userPassword.isEmpty() -> password.error = "Please enter password"
                userPassword.length < 6 -> password.error = "Password must be minimum 6 characters"
                else -> {
                    val hashedPassword = PasswordUtils.hashPassword(userPassword)
                    loginUser(userEmail, hashedPassword)
                }
            }
        }
    }

    private fun loginUser(email: String, hashedPassword: String) {
        checkCollection(
            email = email,
            hashedPassword = hashedPassword,
            index = 0
        )
    }

    private fun checkCollection(
        email: String,
        hashedPassword: String,
        index: Int
    ) {
        if (index >= collections.size) {
            Toast.makeText(this, "Invalid email or password", Toast.LENGTH_SHORT).show()
            return
        }

        val current = collections[index]

        db.collection(current.collection)
            .whereEqualTo("email", email)
            .whereEqualTo("password", hashedPassword)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->

                if (!result.isEmpty) {
                    val doc = result.documents[0]

                    val roleFromDoc = doc.getString("role")
                        ?: doc.getString("type")
                        ?: current.role

                    val finalRole = roleFromDoc.lowercase()

                    saveLoginSession(
                        userId = doc.id,
                        email = doc.getString("email") ?: email,
                        phone = doc.getString("phone") ?: "",
                        name = doc.getString("name") ?: "",
                        role = finalRole,
                        collection = current.collection,
                        hospitalId = doc.getString("hospitalId") ?: "",
                        hospitalName = doc.getString("hospitalName") ?: "",
                        hospitalAddress = doc.getString("hospitalAddress") ?: "",
                        hospitalLat = doc.getDouble("hospitalLat") ?: 0.0,
                        hospitalLng = doc.getDouble("hospitalLng") ?: 0.0
                    )

                    FcmTokenManager.refreshAndSaveToken(this)

                    Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()

                    val intent = Intent(this, current.homeClass)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    checkCollection(
                        email = email,
                        hashedPassword = hashedPassword,
                        index = index + 1
                    )
                }
            }
            .addOnFailureListener {
                checkCollection(
                    email = email,
                    hashedPassword = hashedPassword,
                    index = index + 1
                )
            }
    }

    private fun saveLoginSession(
        userId: String,
        email: String,
        phone: String,
        name: String,
        role: String,
        collection: String,
        hospitalId: String,
        hospitalName: String,
        hospitalAddress: String,
        hospitalLat: Double,
        hospitalLng: Double
    ) {
        val sharedPref = getSharedPreferences("LifeLineSession", MODE_PRIVATE)

        sharedPref.edit()
            .putBoolean("isLoggedIn", true)
            .putString("userId", userId)
            .putString("userEmail", email)
            .putString("userPhone", phone)
            .putString("userName", name)
            .putString("role", role.lowercase())
            .putString("collection", collection)
            .putString("hospitalId", hospitalId)
            .putString("hospitalName", hospitalName)
            .putString("hospitalAddress", hospitalAddress)
            .putFloat("hospitalLat", hospitalLat.toFloat())
            .putFloat("hospitalLng", hospitalLng.toFloat())
            .apply()
    }

    private fun isUserAlreadyLoggedIn(): Boolean {
        val sharedPref = getSharedPreferences("LifeLineSession", MODE_PRIVATE)
        return sharedPref.getBoolean("isLoggedIn", false)
    }

    private fun openHomeByRole() {
        val sharedPref = getSharedPreferences("LifeLineSession", MODE_PRIVATE)
        val role = sharedPref.getString("role", "")?.lowercase() ?: ""

        val homeClass = when (role) {
            "patient" -> PatientHome::class.java
            "doctor" -> DoctorHome::class.java
            "driver" -> DriverHome::class.java
            else -> PatientHome::class.java
        }

        val intent = Intent(this, homeClass)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
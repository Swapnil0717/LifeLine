package com.example.lifeline

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore

class OtpVerification : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var otpFields: List<EditText>

    private lateinit var back: ImageButton
    private lateinit var verifyBtn: Button
    private lateinit var btnProgress: ProgressBar
    private lateinit var tvResend: TextView
    private lateinit var tvPhone: TextView

    private var resendTimer: CountDownTimer? = null

    private var sentOtp = ""
    private var role = ""
    private var email = ""
    private var phone = ""
    private var name = ""

    private var hospitalId = ""
    private var hospitalName = ""
    private var hospitalAddress = ""
    private var hospitalLat = 0.0
    private var hospitalLng = 0.0

    private var isOtpSending = false
    private var isVerifying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_otp_verification)

        db = FirebaseFirestore.getInstance()

        role = (intent.getStringExtra("role") ?: "").lowercase()
        name = intent.getStringExtra("name") ?: "User"
        email = intent.getStringExtra("email") ?: ""
        phone = intent.getStringExtra("phone") ?: ""

        hospitalId = intent.getStringExtra("hospitalId") ?: ""
        hospitalName = intent.getStringExtra("hospitalName") ?: ""
        hospitalAddress = intent.getStringExtra("hospitalAddress") ?: ""
        hospitalLat = intent.getDoubleExtra("hospitalLat", 0.0)
        hospitalLng = intent.getDoubleExtra("hospitalLng", 0.0)

        back = findViewById(R.id.back)
        verifyBtn = findViewById(R.id.next)
        btnProgress = findViewById(R.id.btnProgress)
        tvResend = findViewById(R.id.tvResend)
        tvPhone = findViewById(R.id.tvPhone)

        tvPhone.text = email

        otpFields = listOf(
            findViewById(R.id.e1),
            findViewById(R.id.e2),
            findViewById(R.id.e3),
            findViewById(R.id.e4),
            findViewById(R.id.e5),
            findViewById(R.id.e6)
        )

        setupOtpInputs()
        focusFirstOtpBox()

        back.setOnClickListener {
            if (!isOtpSending && !isVerifying) {
                finish()
            }
        }

        verifyBtn.setOnClickListener {
            verifyOtp()
        }

        tvResend.setOnClickListener {
            if (!isOtpSending && !isVerifying && tvResend.isEnabled) {
                clearOtpFields()
                sendEmailOtp()
            }
        }

        sendEmailOtp()
    }

    private fun sendEmailOtp() {
        if (email.isEmpty()) {
            Toast.makeText(this, "Email not found", Toast.LENGTH_SHORT).show()
            return
        }

        isOtpSending = true
        setButtonLoading(true, "Sending OTP")

        sentOtp = (100000..999999).random().toString()

        Thread {
            val isSent = EmailSender.sendOtpEmail(email, name, sentOtp)

            runOnUiThread {
                isOtpSending = false
                setButtonLoading(false, "Verify and Continue")

                if (isSent) {
                    Toast.makeText(this, "OTP sent to your email", Toast.LENGTH_SHORT).show()
                    startResendCountdown()
                } else {
                    Toast.makeText(this, "Failed to send OTP email", Toast.LENGTH_LONG).show()
                    tvResend.text = "Resend OTP"
                    tvResend.isEnabled = true
                    tvResend.setTextColor(resources.getColor(R.color.primaryRed, theme))
                }
            }
        }.start()
    }

    private fun startResendCountdown() {
        tvResend.isEnabled = false
        tvResend.setTextColor(resources.getColor(android.R.color.darker_gray, theme))

        resendTimer?.cancel()

        resendTimer = object : CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                tvResend.text = String.format("Resend OTP in 00:%02d", seconds)
            }

            override fun onFinish() {
                tvResend.text = "Resend OTP"
                tvResend.isEnabled = true
                tvResend.setTextColor(resources.getColor(R.color.primaryRed, theme))
            }
        }.start()
    }

    private fun verifyOtp() {
        if (isOtpSending || isVerifying) return

        val enteredOtp = otpFields.joinToString("") {
            it.text.toString().trim()
        }

        when {
            enteredOtp.length < 6 -> {
                Toast.makeText(this, "Please enter 6 digit OTP", Toast.LENGTH_SHORT).show()
            }

            enteredOtp != sentOtp -> {
                Toast.makeText(this, "Invalid OTP", Toast.LENGTH_SHORT).show()
            }

            else -> {
                isVerifying = true
                setButtonLoading(true, "Verifying")
                saveUserData()
            }
        }
    }

    private fun saveUserData() {
        val collections = listOf("patients", "doctors", "drivers")
        val collectionName = roleCollection()

        if (collectionName == "users") {
            stopVerificationLoading()
            Toast.makeText(this, "Invalid role", Toast.LENGTH_SHORT).show()
            return
        }

        var checkedCount = 0
        var alreadyExists = false

        for (collection in collections) {
            db.collection(collection)
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener { emailDocs ->

                    if (!alreadyExists && !emailDocs.isEmpty) {
                        alreadyExists = true
                        stopVerificationLoading()
                        Toast.makeText(this, "Email is already registered", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }

                    db.collection(collection)
                        .whereEqualTo("phone", phone)
                        .get()
                        .addOnSuccessListener { phoneDocs ->

                            checkedCount++

                            if (!alreadyExists && !phoneDocs.isEmpty) {
                                alreadyExists = true
                                stopVerificationLoading()
                                Toast.makeText(
                                    this,
                                    "Phone number is already registered",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            if (checkedCount == collections.size && !alreadyExists) {
                                proceedToSave(collectionName)
                            }
                        }
                        .addOnFailureListener {
                            checkedCount++
                            if (checkedCount == collections.size && !alreadyExists) {
                                proceedToSave(collectionName)
                            }
                        }
                }
                .addOnFailureListener {
                    checkedCount++
                    if (checkedCount == collections.size && !alreadyExists) {
                        proceedToSave(collectionName)
                    }
                }
        }
    }

    private fun proceedToSave(collectionName: String) {
        val documentId = db.collection(collectionName).document().id

        val data = hashMapOf<String, Any>(
            "id" to documentId,
            "role" to role,
            "type" to role,
            "name" to name,
            "email" to email,
            "phone" to phone,
            "password" to (intent.getStringExtra("password") ?: ""),
            "isVerified" to true,
            "createdAt" to Timestamp.now()
        )

        if (role == "doctor" || role == "driver") {
            data["hospitalId"] = hospitalId
            data["hospitalName"] = hospitalName
            data["hospitalAddress"] = hospitalAddress
            data["hospitalLat"] = hospitalLat
            data["hospitalLng"] = hospitalLng
        }

        when (role) {
            "patient" -> {
                data["patientType"] = "normal"
                data["patientFcmToken"] = ""
            }

            "doctor" -> {
                data["specialization"] = intent.getStringExtra("specialization") ?: ""
                data["doctorFcmToken"] = ""
            }

            "driver" -> {
                data["ambulanceNumber"] = intent.getStringExtra("ambulanceNumber") ?: ""
                data["driverFcmToken"] = ""
                data["isAvailable"] = false
                data["currentLat"] = hospitalLat.takeIf { it != 0.0 } ?: 18.7357
                data["currentLng"] = hospitalLng.takeIf { it != 0.0 } ?: 73.6756
            }

            else -> {
                stopVerificationLoading()
                Toast.makeText(this, "Invalid role", Toast.LENGTH_SHORT).show()
                return
            }
        }

        db.collection(collectionName)
            .document(documentId)
            .set(data)
            .addOnSuccessListener {

                if (role == "doctor" || role == "driver") {
                    HospitalLocationHelper.addUserToHospital(
                        hospitalId = hospitalId,
                        role = role,
                        userId = documentId
                    )
                }

                Thread {
                    EmailSender.sendSuccessEmail(email, name, role)
                }.start()

                saveLoginSession(
                    userId = documentId,
                    email = email,
                    phone = phone,
                    name = name,
                    role = role,
                    collection = collectionName,
                    hospitalId = hospitalId,
                    hospitalName = hospitalName,
                    hospitalAddress = hospitalAddress,
                    hospitalLat = hospitalLat,
                    hospitalLng = hospitalLng
                )

                FcmTokenManager.refreshAndSaveToken(this)

                Toast.makeText(this, "Registration successful", Toast.LENGTH_SHORT).show()

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
            .addOnFailureListener {
                stopVerificationLoading()
                Toast.makeText(this, it.message ?: "Failed to save data", Toast.LENGTH_SHORT).show()
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

    private fun stopVerificationLoading() {
        isVerifying = false
        setButtonLoading(false, "Verify and Continue")
    }

    private fun setButtonLoading(isLoading: Boolean, normalText: String) {
        verifyBtn.isEnabled = !isLoading
        back.isEnabled = !isLoading
        tvResend.isEnabled = !isLoading && tvResend.text.toString() == "Resend OTP"

        verifyBtn.text = if (isLoading) "" else normalText
        btnProgress.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun roleCollection(): String {
        return when (role.lowercase()) {
            "patient" -> "patients"
            "doctor" -> "doctors"
            "driver" -> "drivers"
            else -> "users"
        }
    }

    private fun focusFirstOtpBox() {
        otpFields[0].requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(otpFields[0], InputMethodManager.SHOW_IMPLICIT)
    }

    private fun clearOtpFields() {
        otpFields.forEach { it.text.clear() }
        focusFirstOtpBox()
    }

    private fun setupOtpInputs() {
        for (i in otpFields.indices) {
            val currentEditText = otpFields[i]

            currentEditText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {}

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    if (s?.length == 1 && i < otpFields.size - 1) {
                        otpFields[i + 1].requestFocus()
                    }

                    if ((s?.length ?: 0) > 1) {
                        currentEditText.setText(s.toString().last().toString())
                        currentEditText.setSelection(1)
                    }
                }

                override fun afterTextChanged(s: Editable?) {}
            })

            currentEditText.setOnKeyListener { _, keyCode, event ->
                if (
                    keyCode == KeyEvent.KEYCODE_DEL &&
                    event.action == KeyEvent.ACTION_DOWN &&
                    currentEditText.text.isEmpty() &&
                    i > 0
                ) {
                    otpFields[i - 1].requestFocus()
                    true
                } else {
                    false
                }
            }
        }
    }

    override fun onDestroy() {
        resendTimer?.cancel()
        super.onDestroy()
    }
}
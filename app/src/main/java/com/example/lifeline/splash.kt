package com.example.lifeline

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class splash : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            openScreenBySession()
        }, 3000)
    }

    private fun openScreenBySession() {
        val sharedPref = getSharedPreferences("LifeLineSession", MODE_PRIVATE)

        val isLoggedIn = sharedPref.getBoolean("isLoggedIn", false)
        val role = sharedPref.getString("role", "") ?: ""

        val nextIntent = if (isLoggedIn) {
            when (role) {
                "patient" -> Intent(this, PatientHome::class.java)
                "doctor" -> Intent(this, DoctorHome::class.java)
                "driver" -> Intent(this, DriverHome::class.java)
                else -> Intent(this, PatientHome::class.java)
            }
        } else {
            Intent(this, PatientHome::class.java)
        }

        nextIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(nextIntent)
        finish()
    }
}
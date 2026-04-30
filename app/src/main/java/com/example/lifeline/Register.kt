package com.example.lifeline

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class Register : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register2)

        val cardPatient = findViewById<LinearLayout>(R.id.cardPatient)
        val cardDoctor = findViewById<LinearLayout>(R.id.cardDoctor)
        val cardDriver = findViewById<LinearLayout>(R.id.cardDriver)

        cardPatient.setOnClickListener {
            startActivity(Intent(this, PatientRegister::class.java))
        }

        cardDoctor.setOnClickListener {
            startActivity(Intent(this, DoctorRegister::class.java))
        }

        cardDriver.setOnClickListener {
            startActivity(Intent(this, DriverRegister::class.java))
        }
    }
}
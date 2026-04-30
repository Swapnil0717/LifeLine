package com.example.lifeline

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class PhoneNumberActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_phone_number)

        val back = findViewById<ImageButton>(R.id.back)
        val phone = findViewById<EditText>(R.id.etPhone)
        val btnContinue = findViewById<Button>(R.id.btnContinue)

        back.setOnClickListener { finish() }

        btnContinue.setOnClickListener {
            val phoneText = phone.text.toString().trim()

            when {
                phoneText.isEmpty() -> phone.error = "Enter phone number"
                phoneText.length != 10 -> phone.error = "Enter valid 10 digit phone number"
                else -> {
                    val intent = Intent(this, BookAmbulance::class.java)
                    intent.putExtra("phone", phoneText)
                    intent.putExtra("guestBooking", true)
                    startActivity(intent)
                }
            }
        }
    }
}
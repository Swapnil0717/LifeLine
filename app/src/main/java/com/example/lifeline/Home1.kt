package com.example.lifeline

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView

class Home1 : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home1, container, false)

        val ambulance = view.findViewById<ImageView>(R.id.ambulance)
        val appointment = view.findViewById<ImageView>(R.id.appointment)

        ambulance.setOnClickListener {
            val intent = Intent(requireContext(), Booking::class.java)
            startActivity(intent)
        }

        appointment.setOnClickListener {
            val intent = Intent(requireContext(), AppointmnetBooking::class.java)
            startActivity(intent)
        }

        return view
    }
}
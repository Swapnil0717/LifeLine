package com.example.lifeline

data class DoctorModel(
    val id: String = "",
    val name: String = "",
    val specialization: String = "",
    val hospitalId: String = "",
    val hospitalName: String = "",
    val hospitalAddress: String = "",
    val phone: String = "",
    val email: String = "",
    val isOnline: Boolean = true
)
package com.example.lifeline

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

object FcmTokenManager {

    fun refreshAndSaveToken(context: Context) {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                saveTokenToCurrentUser(context, token)
            }
    }

    fun saveTokenToCurrentUser(context: Context, token: String) {
        val sharedPref = context.getSharedPreferences("LifeLineSession", Context.MODE_PRIVATE)

        val isLoggedIn = sharedPref.getBoolean("isLoggedIn", false)
        val userId = sharedPref.getString("userId", "") ?: ""
        val collection = sharedPref.getString("collection", "") ?: ""
        val role = (sharedPref.getString("role", "") ?: "").lowercase()

        if (!isLoggedIn || userId.isEmpty() || collection.isEmpty()) return

        val fieldName = when (role) {
            "patient" -> "patientFcmToken"
            "driver" -> "driverFcmToken"
            "doctor" -> "doctorFcmToken"
            else -> "fcmToken"
        }

        FirebaseFirestore.getInstance()
            .collection(collection)
            .document(userId)
            .update(fieldName, token)
    }
}
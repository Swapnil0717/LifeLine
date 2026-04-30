package com.example.lifeline

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore

class ScheduledAmbulanceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val bookingId = intent.getStringExtra("bookingId") ?: return
        val db = FirebaseFirestore.getInstance()

        db.collection("ambulanceRequests")
            .document(bookingId)
            .get()
            .addOnSuccessListener { requestDoc ->

                if (!requestDoc.exists()) return@addOnSuccessListener

                val status = requestDoc.getString("status") ?: ""
                if (status != "SCHEDULED") return@addOnSuccessListener

                val hospitalId = requestDoc.getString("hospitalId") ?: ""
                val pickupLat = requestDoc.getDouble("pickupLat") ?: 0.0
                val pickupLng = requestDoc.getDouble("pickupLng") ?: 0.0

                if (hospitalId.isEmpty()) {
                    db.collection("ambulanceRequests")
                        .document(bookingId)
                        .update(
                            mapOf(
                                "status" to "NO_DRIVER_AVAILABLE",
                                "updatedAt" to Timestamp.now()
                            )
                        )
                    return@addOnSuccessListener
                }

                db.collection("drivers")
                    .whereEqualTo("isAvailable", true)
                    .whereEqualTo("hospitalId", hospitalId)
                    .get()
                    .addOnSuccessListener { drivers ->

                        if (drivers.isEmpty) {
                            db.collection("ambulanceRequests")
                                .document(bookingId)
                                .update(
                                    mapOf(
                                        "status" to "NO_DRIVER_AVAILABLE",
                                        "updatedAt" to Timestamp.now()
                                    )
                                )
                            return@addOnSuccessListener
                        }

                        var nearestDriverId = ""
                        var nearestDistance = Double.MAX_VALUE

                        for (doc in drivers.documents) {
                            val lat = doc.getDouble("currentLat") ?: continue
                            val lng = doc.getDouble("currentLng") ?: continue

                            val distance = LocationUtils.distanceKm(
                                pickupLat,
                                pickupLng,
                                lat,
                                lng
                            )

                            if (distance < nearestDistance) {
                                nearestDistance = distance
                                nearestDriverId = doc.id
                            }
                        }

                        if (nearestDriverId.isEmpty()) {
                            db.collection("ambulanceRequests")
                                .document(bookingId)
                                .update(
                                    mapOf(
                                        "status" to "NO_DRIVER_AVAILABLE",
                                        "updatedAt" to Timestamp.now()
                                    )
                                )
                            return@addOnSuccessListener
                        }

                        db.collection("ambulanceRequests")
                            .document(bookingId)
                            .update(
                                mapOf(
                                    "targetDriverId" to nearestDriverId,
                                    "nearestDistanceKm" to nearestDistance,
                                    "status" to "SEARCHING_DRIVER",
                                    "updatedAt" to Timestamp.now()
                                )
                            )
                    }
                    .addOnFailureListener {
                        db.collection("ambulanceRequests")
                            .document(bookingId)
                            .update(
                                mapOf(
                                    "status" to "NO_DRIVER_AVAILABLE",
                                    "updatedAt" to Timestamp.now()
                                )
                            )
                    }
            }
    }
}
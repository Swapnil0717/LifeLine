package com.example.lifeline

import android.content.Context
import android.location.Geocoder
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale
import kotlin.math.min

object HospitalLocationHelper {

    data class HospitalLocation(
        val id: String,
        val name: String,
        val searchName: String,
        val address: String,
        val lat: Double,
        val lng: Double
    )

    fun findOrCreateHospital(
        context: Context,
        hospitalName: String,
        role: String,
        userId: String? = null,
        onSuccess: (HospitalLocation) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()
        val cleanName = cleanHospitalName(hospitalName)

        if (cleanName.isEmpty()) {
            onFailure("Enter valid hospital name")
            return
        }

        val searchName = normalize(cleanName)

        db.collection("hospitals")
            .get()
            .addOnSuccessListener { result ->
                var matchedHospital: HospitalLocation? = null

                for (doc in result.documents) {
                    val existingName = doc.getString("name") ?: ""
                    val existingSearchName = doc.getString("searchName") ?: normalize(existingName)

                    if (existingSearchName.isEmpty()) continue

                    val distance = levenshtein(searchName, existingSearchName)

                    if (
                        existingSearchName == searchName ||
                        existingSearchName.contains(searchName) ||
                        searchName.contains(existingSearchName) ||
                        distance <= 3
                    ) {
                        matchedHospital = HospitalLocation(
                            id = doc.id,
                            name = existingName.ifEmpty { cleanName },
                            searchName = existingSearchName,
                            address = doc.getString("address") ?: "",
                            lat = doc.getDouble("lat") ?: 0.0,
                            lng = doc.getDouble("lng") ?: 0.0
                        )
                        break
                    }
                }

                if (
                    matchedHospital != null &&
                    matchedHospital.lat != 0.0 &&
                    matchedHospital.lng != 0.0
                ) {
                    if (!userId.isNullOrEmpty()) {
                        addUserToHospital(matchedHospital.id, role, userId)
                    }

                    onSuccess(matchedHospital)
                } else {
                    geocodeAndCreateHospital(
                        context = context,
                        db = db,
                        hospitalName = cleanName,
                        searchName = searchName,
                        role = role,
                        userId = userId,
                        onSuccess = onSuccess,
                        onFailure = onFailure
                    )
                }
            }
            .addOnFailureListener {
                onFailure(it.message ?: "Failed to check hospital")
            }
    }

    private fun geocodeAndCreateHospital(
        context: Context,
        db: FirebaseFirestore,
        hospitalName: String,
        searchName: String,
        role: String,
        userId: String?,
        onSuccess: (HospitalLocation) -> Unit,
        onFailure: (String) -> Unit
    ) {
        Thread {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())

                val queries = listOf(
                    hospitalName,
                    "$hospitalName hospital",
                    "$hospitalName hospital Pune",
                    "$hospitalName hospital Talegaon",
                    "$hospitalName healthcare Pune",
                    "$hospitalName medical center Pune",
                    "$hospitalName clinic Pune",
                    "$hospitalName India"
                )

                var foundAddress = ""
                var foundLat = 0.0
                var foundLng = 0.0

                for (query in queries) {
                    val results = geocoder.getFromLocationName(query, 5)

                    if (!results.isNullOrEmpty()) {
                        val best = results[0]
                        foundAddress = best.getAddressLine(0) ?: query
                        foundLat = best.latitude
                        foundLng = best.longitude
                        break
                    }
                }

                if (foundLat == 0.0 || foundLng == 0.0) {
                    onFailure("Hospital location not found. Try correct hospital name with city.")
                    return@Thread
                }

                val hospitalDoc = db.collection("hospitals").document()
                val hospitalId = hospitalDoc.id

                val data = hashMapOf<String, Any>(
                    "id" to hospitalId,
                    "name" to hospitalName,
                    "searchName" to searchName,
                    "address" to foundAddress,
                    "lat" to foundLat,
                    "lng" to foundLng,
                    "doctorIds" to arrayListOf<String>(),
                    "driverIds" to arrayListOf<String>(),
                    "createdAt" to Timestamp.now(),
                    "updatedAt" to Timestamp.now()
                )

                hospitalDoc
                    .set(data)
                    .addOnSuccessListener {
                        if (!userId.isNullOrEmpty()) {
                            addUserToHospital(hospitalId, role, userId)
                        }

                        onSuccess(
                            HospitalLocation(
                                id = hospitalId,
                                name = hospitalName,
                                searchName = searchName,
                                address = foundAddress,
                                lat = foundLat,
                                lng = foundLng
                            )
                        )
                    }
                    .addOnFailureListener {
                        onFailure(it.message ?: "Failed to save hospital")
                    }

            } catch (e: Exception) {
                onFailure(e.message ?: "Failed to find hospital location")
            }
        }.start()
    }

    fun addUserToHospital(
        hospitalId: String,
        role: String,
        userId: String
    ) {
        if (hospitalId.isEmpty() || userId.isEmpty()) return

        val fieldName = when (role.lowercase()) {
            "doctor" -> "doctorIds"
            "driver" -> "driverIds"
            else -> return
        }

        FirebaseFirestore.getInstance()
            .collection("hospitals")
            .document(hospitalId)
            .update(
                mapOf(
                    fieldName to FieldValue.arrayUnion(userId),
                    "updatedAt" to Timestamp.now()
                )
            )
    }

    private fun cleanHospitalName(input: String): String {
        return input.trim()
            .replace("hospiatl", "hospital", ignoreCase = true)
            .replace("hsopital", "hospital", ignoreCase = true)
            .replace("hospitl", "hospital", ignoreCase = true)
            .replace("hostpital", "hospital", ignoreCase = true)
            .replace("talegoan", "talegaon", ignoreCase = true)
            .replace("punee", "pune", ignoreCase = true)
            .replace("medicl", "medical", ignoreCase = true)
            .replace("clnic", "clinic", ignoreCase = true)
            .replace(Regex("\\s+"), " ")
    }

    private fun normalize(input: String): String {
        return input.lowercase(Locale.getDefault())
            .replace("hospital", "")
            .replace("clinic", "")
            .replace("medical", "")
            .replace("center", "")
            .replace("centre", "")
            .replace("healthcare", "")
            .replace("pvt", "")
            .replace("ltd", "")
            .replace(".", "")
            .replace(",", "")
            .replace("-", "")
            .replace(Regex("\\s+"), "")
            .trim()
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val dp = Array(a.length + 1) { IntArray(b.length + 1) }

        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1

                dp[i][j] = min(
                    min(
                        dp[i - 1][j] + 1,
                        dp[i][j - 1] + 1
                    ),
                    dp[i - 1][j - 1] + cost
                )
            }
        }

        return dp[a.length][b.length]
    }
}
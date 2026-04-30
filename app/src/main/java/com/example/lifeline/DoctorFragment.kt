package com.example.lifeline

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DoctorFragment : Fragment() {

    private lateinit var db: FirebaseFirestore

    private lateinit var tvDoctorName: TextView
    private lateinit var tvDoctorStatus: TextView
    private lateinit var btnToggleOnline: TextView

    private lateinit var tvTodayCount: TextView
    private lateinit var tvCompletedCount: TextView
    private lateinit var tvCancelledCount: TextView

    private lateinit var emergencyCard: LinearLayout
    private lateinit var tvEmergencyTitle: TextView
    private lateinit var tvEmergencySub: TextView

    private lateinit var nextPatientCard: LinearLayout
    private lateinit var tvNextPatientName: TextView
    private lateinit var tvNextPatientTime: TextView
    private lateinit var tvNextPatientReason: TextView

    private lateinit var appointmentsContainer: LinearLayout
    private lateinit var emptyLayout: LinearLayout
    private lateinit var progressBar: ProgressBar

    private lateinit var tabToday: TextView
    private lateinit var tabUpcoming: TextView
    private lateinit var tabCompleted: TextView
    private lateinit var tabCancelled: TextView

    private var appointmentListener: ListenerRegistration? = null
    private var emergencyListener: ListenerRegistration? = null

    private var doctorId = ""
    private var doctorName = "Doctor"
    private var hospitalId = ""
    private var selectedFilter = "TODAY"
    private var isDoctorOnline = true

    private val todayDate: String
        get() = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_doctor, container, false)

        db = FirebaseFirestore.getInstance()

        bindViews(view)
        loadSession()

        tvDoctorName.text = "Hello, Dr. $doctorName 👋"

        btnToggleOnline.setOnClickListener {
            isDoctorOnline = !isDoctorOnline
            updateDoctorOnlineStatus()
        }

        tabToday.setOnClickListener {
            selectedFilter = "TODAY"
            updateTabs()
            listenAppointments()
        }

        tabUpcoming.setOnClickListener {
            selectedFilter = "UPCOMING"
            updateTabs()
            listenAppointments()
        }

        tabCompleted.setOnClickListener {
            selectedFilter = "COMPLETED"
            updateTabs()
            listenAppointments()
        }

        tabCancelled.setOnClickListener {
            selectedFilter = "CANCELLED"
            updateTabs()
            listenAppointments()
        }

        updateTabs()

        if (doctorId.isEmpty()) {
            showEmpty("Doctor session not found")
        } else {
            loadDoctorStatus()
            listenAppointments()
            listenEmergencyCases()
        }

        return view
    }

    private fun bindViews(view: View) {
        tvDoctorName = view.findViewById(R.id.tvDoctorName)
        tvDoctorStatus = view.findViewById(R.id.tvDoctorStatus)
        btnToggleOnline = view.findViewById(R.id.btnToggleOnline)

        tvTodayCount = view.findViewById(R.id.tvTodayCount)
        tvCompletedCount = view.findViewById(R.id.tvCompletedCount)
        tvCancelledCount = view.findViewById(R.id.tvCancelledCount)

        emergencyCard = view.findViewById(R.id.emergencyCard)
        tvEmergencyTitle = view.findViewById(R.id.tvEmergencyTitle)
        tvEmergencySub = view.findViewById(R.id.tvEmergencySub)

        nextPatientCard = view.findViewById(R.id.nextPatientCard)
        tvNextPatientName = view.findViewById(R.id.tvNextPatientName)
        tvNextPatientTime = view.findViewById(R.id.tvNextPatientTime)
        tvNextPatientReason = view.findViewById(R.id.tvNextPatientReason)

        appointmentsContainer = view.findViewById(R.id.appointmentsContainer)
        emptyLayout = view.findViewById(R.id.emptyLayout)
        progressBar = view.findViewById(R.id.progressBar)

        tabToday = view.findViewById(R.id.tabToday)
        tabUpcoming = view.findViewById(R.id.tabUpcoming)
        tabCompleted = view.findViewById(R.id.tabCompleted)
        tabCancelled = view.findViewById(R.id.tabCancelled)
    }

    private fun loadSession() {
        val sharedPref = requireActivity().getSharedPreferences("LifeLineSession", 0)

        doctorId = sharedPref.getString("userId", "") ?: ""
        doctorName = sharedPref.getString("userName", "Doctor") ?: "Doctor"
        hospitalId = sharedPref.getString("hospitalId", "") ?: ""
    }

    private fun loadDoctorStatus() {
        db.collection("doctors")
            .document(doctorId)
            .get()
            .addOnSuccessListener { doc ->
                isDoctorOnline = doc.getBoolean("isOnline") ?: true
                updateOnlineUI()
            }
    }

    private fun updateDoctorOnlineStatus() {
        db.collection("doctors")
            .document(doctorId)
            .update(
                mapOf(
                    "isOnline" to isDoctorOnline,
                    "updatedAt" to Timestamp.now()
                )
            )
            .addOnSuccessListener {
                updateOnlineUI()
            }
            .addOnFailureListener {
                isDoctorOnline = !isDoctorOnline
                updateOnlineUI()
                Toast.makeText(requireContext(), it.message ?: "Failed to update status", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateOnlineUI() {
        if (isDoctorOnline) {
            tvDoctorStatus.text = "Status: Online"
            btnToggleOnline.text = "Go Offline"
        } else {
            tvDoctorStatus.text = "Status: Offline"
            btnToggleOnline.text = "Go Online"
        }
    }

    private fun listenAppointments() {
        progressBar.visibility = View.VISIBLE
        emptyLayout.visibility = View.GONE
        appointmentsContainer.removeAllViews()

        appointmentListener?.remove()

        appointmentListener = db.collection("appointments")
            .whereEqualTo("doctorId", doctorId)
            .addSnapshotListener { snapshot, error ->

                progressBar.visibility = View.GONE

                if (error != null) {
                    showEmpty(error.message ?: "Failed to load appointments")
                    return@addSnapshotListener
                }

                if (snapshot == null || snapshot.isEmpty) {
                    updateStats(emptyList())
                    showEmpty("No appointments found")
                    return@addSnapshotListener
                }

                val allAppointments = snapshot.documents
                updateStats(allAppointments)
                updateNextPatient(allAppointments)

                val filtered = filterAppointments(allAppointments)

                if (filtered.isEmpty()) {
                    showEmpty("No $selectedFilter appointments")
                    return@addSnapshotListener
                }

                emptyLayout.visibility = View.GONE
                appointmentsContainer.removeAllViews()

                val sorted = filtered.sortedWith(
                    compareBy<DocumentSnapshot> {
                        it.getString("appointmentDate") ?: ""
                    }.thenBy {
                        it.getString("appointmentTime") ?: ""
                    }
                )

                for (doc in sorted) {
                    addAppointmentCard(doc)
                }
            }
    }

    private fun filterAppointments(list: List<DocumentSnapshot>): List<DocumentSnapshot> {
        return when (selectedFilter) {
            "TODAY" -> list.filter {
                val status = it.getString("status") ?: ""
                val date = it.getString("appointmentDate") ?: ""
                status == "BOOKED" && date == todayDate
            }

            "UPCOMING" -> list.filter {
                val status = it.getString("status") ?: ""
                status == "BOOKED"
            }

            "COMPLETED" -> list.filter {
                val status = it.getString("status") ?: ""
                status == "COMPLETED"
            }

            "CANCELLED" -> list.filter {
                val status = it.getString("status") ?: ""
                status.contains("CANCELLED")
            }

            else -> list
        }
    }

    private fun updateStats(list: List<DocumentSnapshot>) {
        val today = list.count {
            it.getString("appointmentDate") == todayDate &&
                    it.getString("status") == "BOOKED"
        }

        val completed = list.count {
            it.getString("status") == "COMPLETED"
        }

        val cancelled = list.count {
            val status = it.getString("status") ?: ""
            status.contains("CANCELLED")
        }

        tvTodayCount.text = today.toString()
        tvCompletedCount.text = completed.toString()
        tvCancelledCount.text = cancelled.toString()
    }

    private fun updateNextPatient(list: List<DocumentSnapshot>) {
        val booked = list.filter {
            it.getString("status") == "BOOKED"
        }

        if (booked.isEmpty()) {
            nextPatientCard.visibility = View.GONE
            return
        }

        val next = booked.sortedWith(
            compareBy<DocumentSnapshot> {
                it.getString("appointmentDate") ?: ""
            }.thenBy {
                it.getString("appointmentTime") ?: ""
            }
        ).first()

        nextPatientCard.visibility = View.VISIBLE

        val patientName = next.getString("patientName") ?: "Patient"
        val time = next.getString("appointmentTime") ?: "Time not set"
        val date = next.getString("appointmentDate") ?: "Date not set"
        val reason = next.getString("reason") ?: "General checkup"

        tvNextPatientName.text = patientName
        tvNextPatientTime.text = "$date • $time"
        tvNextPatientReason.text = "Reason: $reason"
    }

    private fun listenEmergencyCases() {
        emergencyListener?.remove()

        var query = db.collection("ambulanceRequests")
            .whereEqualTo("status", "HOSPITAL_SELECTED")

        emergencyListener = query.addSnapshotListener { snapshot, _ ->
            if (snapshot == null || snapshot.isEmpty) {
                emergencyCard.visibility = View.GONE
                return@addSnapshotListener
            }

            val docs =
                if (hospitalId.isNotEmpty()) {
                    snapshot.documents.filter {
                        it.getString("hospitalId") == hospitalId
                    }
                } else {
                    snapshot.documents
                }

            if (docs.isEmpty()) {
                emergencyCard.visibility = View.GONE
                return@addSnapshotListener
            }

            val emergency = docs.first()
            val patientName = emergency.getString("patientName") ?: "Patient"
            val hospitalName = emergency.getString("hospitalName") ?: "Hospital"
            val pickup = emergency.getString("pickupAddress") ?: "Pickup location"

            emergencyCard.visibility = View.VISIBLE
            tvEmergencyTitle.text = "Emergency Incoming Case"
            tvEmergencySub.text = "$patientName is coming to $hospitalName\nPickup: $pickup"
        }
    }

    private fun addAppointmentCard(doc: DocumentSnapshot) {
        val card = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_doctor_appointment, appointmentsContainer, false)

        val appointmentId = doc.id

        val patientName = doc.getString("patientName") ?: "Patient"
        val patientAge = doc.getString("patientAge") ?: "Not added"
        val patientGender = doc.getString("patientGender") ?: "Not added"
        val patientPhone = doc.getString("patientPhone") ?: ""
        val appointmentDate = doc.getString("appointmentDate") ?: "Date not set"
        val appointmentTime = doc.getString("appointmentTime") ?: "Time not set"
        val reason = doc.getString("reason") ?: "General checkup"
        val status = doc.getString("status") ?: "BOOKED"

        card.findViewById<TextView>(R.id.tvPatientName).text = patientName
        card.findViewById<TextView>(R.id.tvAppointmentDate).text = "Date: $appointmentDate"
        card.findViewById<TextView>(R.id.tvAppointmentTime).text = "Time: $appointmentTime"
        card.findViewById<TextView>(R.id.tvPatientPhone).text = "Phone: ${patientPhone.ifEmpty { "Not available" }}"
        card.findViewById<TextView>(R.id.tvPatientAgeGender).text = "Age: $patientAge • Gender: $patientGender"
        card.findViewById<TextView>(R.id.tvReason).text = "Reason: $reason"
        card.findViewById<TextView>(R.id.tvStatus).text = status

        val btnCall = card.findViewById<Button>(R.id.btnCallPatient)
        val btnComplete = card.findViewById<Button>(R.id.btnCompleteAppointment)
        val btnCancel = card.findViewById<Button>(R.id.btnCancelAppointment)
        val btnHistory = card.findViewById<Button>(R.id.btnViewHistory)

        btnCall.setOnClickListener {
            callPatient(patientPhone)
        }

        btnComplete.setOnClickListener {
            completeAppointment(appointmentId)
        }

        btnCancel.setOnClickListener {
            cancelAppointment(appointmentId)
        }

        btnHistory.setOnClickListener {
            Toast.makeText(requireContext(), "Patient history coming next", Toast.LENGTH_SHORT).show()
        }

        if (status != "BOOKED") {
            btnComplete.visibility = View.GONE
            btnCancel.visibility = View.GONE
        }

        appointmentsContainer.addView(card)
    }

    private fun completeAppointment(appointmentId: String) {
        db.collection("appointments")
            .document(appointmentId)
            .update(
                mapOf(
                    "status" to "COMPLETED",
                    "completedAt" to Timestamp.now(),
                    "updatedAt" to Timestamp.now()
                )
            )
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Appointment completed", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), it.message ?: "Failed to complete", Toast.LENGTH_SHORT).show()
            }
    }

    private fun cancelAppointment(appointmentId: String) {
        db.collection("appointments")
            .document(appointmentId)
            .update(
                mapOf(
                    "status" to "CANCELLED_BY_DOCTOR",
                    "cancelledBy" to "doctor",
                    "cancelledAt" to Timestamp.now(),
                    "updatedAt" to Timestamp.now()
                )
            )
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Appointment cancelled", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), it.message ?: "Failed to cancel", Toast.LENGTH_SHORT).show()
            }
    }

    private fun callPatient(phone: String) {
        if (phone.isEmpty()) {
            Toast.makeText(requireContext(), "Phone number not available", Toast.LENGTH_SHORT).show()
            return
        }

        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
    }

    private fun updateTabs() {
        val tabs = listOf(tabToday, tabUpcoming, tabCompleted, tabCancelled)

        tabs.forEach {
            it.setBackgroundResource(R.drawable.tab_unselected_bg)
            it.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
        }

        val selectedTab = when (selectedFilter) {
            "TODAY" -> tabToday
            "UPCOMING" -> tabUpcoming
            "COMPLETED" -> tabCompleted
            "CANCELLED" -> tabCancelled
            else -> tabToday
        }

        selectedTab.setBackgroundResource(R.drawable.tab_selected_bg)
        selectedTab.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
    }

    private fun showEmpty(message: String) {
        appointmentsContainer.removeAllViews()
        emptyLayout.visibility = View.VISIBLE
        progressBar.visibility = View.GONE
    }

    override fun onDestroyView() {
        appointmentListener?.remove()
        emergencyListener?.remove()
        super.onDestroyView()
    }
}
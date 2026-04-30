package com.example.lifeline

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class HistoryFragment : Fragment() {

    private lateinit var db: FirebaseFirestore

    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyLayout: LinearLayout
    private lateinit var historyContainer: LinearLayout

    private var appointmentListener: ListenerRegistration? = null
    private var ambulanceListener: ListenerRegistration? = null

    private var userId = ""
    private var userEmail = ""
    private var userPhone = ""
    private var role = ""

    private val historyItems = mutableListOf<HistoryItem>()

    data class HistoryItem(
        val type: String,
        val title: String,
        val subtitle: String,
        val status: String,
        val dateTime: String,
        val extra: String
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_history, container, false)

        db = FirebaseFirestore.getInstance()

        tvTitle = view.findViewById(R.id.tvHistoryTitle)
        tvSubtitle = view.findViewById(R.id.tvHistorySubtitle)
        progressBar = view.findViewById(R.id.progressBar)
        emptyLayout = view.findViewById(R.id.emptyLayout)
        historyContainer = view.findViewById(R.id.historyContainer)

        loadSession()
        setupTitle()

        if (userId.isEmpty() && userEmail.isEmpty() && userPhone.isEmpty()) {
            showEmpty("User session not found")
        } else {
            loadHistory()
        }

        return view
    }

    private fun loadSession() {
        val session = requireActivity().getSharedPreferences("LifeLineSession", 0)

        userId = session.getString("userId", "") ?: ""
        userEmail = session.getString("userEmail", "") ?: ""
        userPhone = session.getString("userPhone", "") ?: ""
        role = session.getString("role", session.getString("collection", "")) ?: ""
    }

    private fun isDoctor(): Boolean {
        return role.equals("doctor", true) || role.equals("doctors", true)
    }

    private fun setupTitle() {
        if (isDoctor()) {
            tvTitle.text = "Doctor Activity"
            tvSubtitle.text = "Your appointments and consultation history"
        } else {
            tvTitle.text = "My Activity"
            tvSubtitle.text = "Your appointments and ambulance booking history"
        }
    }

    private fun loadHistory() {
        progressBar.visibility = View.VISIBLE
        emptyLayout.visibility = View.GONE
        historyContainer.removeAllViews()
        historyItems.clear()

        if (isDoctor()) {
            loadDoctorAppointments()
        } else {
            loadPatientAppointments()
            loadPatientAmbulanceHistory()
        }
    }

    private fun loadDoctorAppointments() {
        appointmentListener?.remove()

        appointmentListener = db.collection("appointments")
            .whereEqualTo("doctorId", userId)
            .addSnapshotListener { snapshot, error ->
                progressBar.visibility = View.GONE

                if (error != null) {
                    showEmpty(error.message ?: "Failed to load history")
                    return@addSnapshotListener
                }

                historyItems.clear()

                snapshot?.documents?.forEach { doc ->
                    historyItems.add(createDoctorAppointmentItem(doc))
                }

                renderHistory()
            }
    }

    private fun loadPatientAppointments() {
        appointmentListener?.remove()

        appointmentListener = db.collection("appointments")
            .addSnapshotListener { snapshot, error ->
                progressBar.visibility = View.GONE

                if (error != null) {
                    showEmpty(error.message ?: "Failed to load appointments")
                    return@addSnapshotListener
                }

                historyItems.removeAll { it.type == "APPOINTMENT" }

                snapshot?.documents?.forEach { doc ->
                    val docPatientId = doc.getString("patientId") ?: ""
                    val docPatientEmail = doc.getString("patientEmail") ?: ""
                    val docPatientPhone = doc.getString("patientPhone") ?: ""

                    val belongsToPatient =
                        docPatientId == userId ||
                                (userEmail.isNotEmpty() && docPatientEmail == userEmail) ||
                                (userPhone.isNotEmpty() && docPatientPhone == userPhone)

                    if (belongsToPatient) {
                        historyItems.add(createPatientAppointmentItem(doc))
                    }
                }

                renderHistory()
            }
    }

    private fun loadPatientAmbulanceHistory() {
        ambulanceListener?.remove()

        ambulanceListener = db.collection("ambulanceRequests")
            .addSnapshotListener { snapshot, error ->
                progressBar.visibility = View.GONE

                if (error != null) {
                    Toast.makeText(
                        requireContext(),
                        error.message ?: "Failed to load ambulance history",
                        Toast.LENGTH_SHORT
                    ).show()
                    renderHistory()
                    return@addSnapshotListener
                }

                historyItems.removeAll { it.type == "AMBULANCE" }

                snapshot?.documents?.forEach { doc ->
                    val docPatientId = doc.getString("patientId") ?: ""
                    val docPatientEmail = doc.getString("patientEmail") ?: ""
                    val docPatientPhone = doc.getString("patientPhone") ?: ""

                    val belongsToPatient =
                        docPatientId == userId ||
                                (userEmail.isNotEmpty() && docPatientEmail == userEmail) ||
                                (userPhone.isNotEmpty() && docPatientPhone == userPhone)

                    if (belongsToPatient) {
                        historyItems.add(createAmbulanceItem(doc))
                    }
                }

                renderHistory()
            }
    }

    private fun createDoctorAppointmentItem(doc: DocumentSnapshot): HistoryItem {
        val patientName = doc.getString("patientName") ?: "Patient"
        val reason = doc.getString("reason") ?: "General checkup"
        val date = doc.getString("appointmentDate") ?: "Date not set"
        val time = doc.getString("appointmentTime") ?: "Time not set"
        val status = doc.getString("status") ?: "BOOKED"
        val phone = doc.getString("patientPhone") ?: "Not available"

        return HistoryItem(
            type = "APPOINTMENT",
            title = "Appointment with $patientName",
            subtitle = reason,
            status = status,
            dateTime = "$date • $time",
            extra = "Patient Phone: $phone"
        )
    }

    private fun createPatientAppointmentItem(doc: DocumentSnapshot): HistoryItem {
        val doctorName = doc.getString("doctorName") ?: "Doctor"
        val specialization = doc.getString("specialization") ?: "Specialization"
        val hospitalName = doc.getString("hospitalName") ?: "Hospital"
        val date = doc.getString("appointmentDate") ?: "Date not set"
        val time = doc.getString("appointmentTime") ?: "Time not set"
        val status = doc.getString("status") ?: "BOOKED"
        val paymentStatus = doc.getString("paymentStatus") ?: "PENDING"

        return HistoryItem(
            type = "APPOINTMENT",
            title = "Appointment with $doctorName",
            subtitle = "$specialization • $hospitalName",
            status = status,
            dateTime = "$date • $time",
            extra = "Payment: $paymentStatus"
        )
    }

    private fun createAmbulanceItem(doc: DocumentSnapshot): HistoryItem {
        val hospitalName = doc.getString("hospitalName") ?: "Hospital not selected"
        val pickup = doc.getString("pickupAddress") ?: "Pickup location"
        val status = doc.getString("status") ?: "REQUESTED"
        val driverName = doc.getString("driverName") ?: "Driver not assigned"
        val bookingType = doc.getString("bookingType") ?: "AMBULANCE"

        return HistoryItem(
            type = "AMBULANCE",
            title = "$bookingType Ambulance",
            subtitle = "To: $hospitalName",
            status = status,
            dateTime = "Pickup: $pickup",
            extra = "Driver: $driverName"
        )
    }

    private fun renderHistory() {
        progressBar.visibility = View.GONE
        historyContainer.removeAllViews()

        if (historyItems.isEmpty()) {
            showEmpty("No history found")
            return
        }

        emptyLayout.visibility = View.GONE

        val sorted = historyItems.sortedByDescending { it.dateTime }

        sorted.forEach { item ->
            addHistoryCard(item)
        }
    }

    private fun addHistoryCard(item: HistoryItem) {
        val card = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_history, historyContainer, false)

        card.findViewById<TextView>(R.id.tvType).text = item.type
        card.findViewById<TextView>(R.id.tvTitle).text = item.title
        card.findViewById<TextView>(R.id.tvSubtitle).text = item.subtitle
        card.findViewById<TextView>(R.id.tvDateTime).text = item.dateTime
        card.findViewById<TextView>(R.id.tvExtra).text = item.extra
        card.findViewById<TextView>(R.id.tvStatus).text = item.status

        historyContainer.addView(card)
    }

    private fun showEmpty(message: String) {
        progressBar.visibility = View.GONE
        historyContainer.removeAllViews()
        emptyLayout.visibility = View.VISIBLE
        emptyLayout.findViewById<TextView>(R.id.tvEmptyMessage).text = message
    }

    override fun onDestroyView() {
        appointmentListener?.remove()
        ambulanceListener?.remove()
        super.onDestroyView()
    }
}
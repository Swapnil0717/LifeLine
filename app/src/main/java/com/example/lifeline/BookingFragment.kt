package com.example.lifeline

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class BookingFragment : Fragment() {

    private lateinit var db: FirebaseFirestore

    private lateinit var progressBar: ProgressBar
    private lateinit var emptyLayout: LinearLayout
    private lateinit var bookingContainer: LinearLayout

    private var appointmentListener: ListenerRegistration? = null
    private var ambulanceListener: ListenerRegistration? = null

    private var patientId = ""
    private var patientEmail = ""
    private var patientPhone = ""

    private val bookingItems = mutableListOf<BookingItem>()

    data class BookingItem(
        val id: String,
        val type: String,
        val title: String,
        val subtitle: String,
        val dateTime: String,
        val status: String,
        val canCancel: Boolean
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_booking, container, false)

        db = FirebaseFirestore.getInstance()

        progressBar = view.findViewById(R.id.progressBar)
        emptyLayout = view.findViewById(R.id.emptyLayout)
        bookingContainer = view.findViewById(R.id.bookingContainer)

        loadSession()

        if (patientId.isEmpty() && patientEmail.isEmpty() && patientPhone.isEmpty()) {
            showEmpty("Patient session not found")
        } else {
            loadBookings()
        }

        return view
    }

    private fun loadSession() {
        val session = requireActivity().getSharedPreferences("LifeLineSession", 0)

        patientId = session.getString("userId", "") ?: ""
        patientEmail = session.getString("userEmail", "") ?: ""
        patientPhone = session.getString("userPhone", "") ?: ""
    }

    private fun loadBookings() {
        progressBar.visibility = View.VISIBLE
        emptyLayout.visibility = View.GONE
        bookingContainer.removeAllViews()
        bookingItems.clear()

        loadAppointments()
        loadAmbulanceBookings()
    }

    private fun loadAppointments() {
        appointmentListener?.remove()

        appointmentListener = db.collection("appointments")
            .addSnapshotListener { snapshot, error ->
                progressBar.visibility = View.GONE

                if (error != null) {
                    showEmpty(error.message ?: "Failed to load appointments")
                    return@addSnapshotListener
                }

                bookingItems.removeAll { it.type == "APPOINTMENT" }

                snapshot?.documents?.forEach { doc ->
                    val docPatientId = doc.getString("patientId") ?: ""
                    val docPatientEmail = doc.getString("patientEmail") ?: ""
                    val docPatientPhone = doc.getString("patientPhone") ?: ""

                    val belongsToPatient =
                        docPatientId == patientId ||
                                (patientEmail.isNotEmpty() && docPatientEmail == patientEmail) ||
                                (patientPhone.isNotEmpty() && docPatientPhone == patientPhone)

                    if (belongsToPatient) {
                        bookingItems.add(createAppointmentItem(doc))
                    }
                }

                renderBookings()
            }
    }

    private fun loadAmbulanceBookings() {
        ambulanceListener?.remove()

        ambulanceListener = db.collection("ambulanceRequests")
            .addSnapshotListener { snapshot, error ->
                progressBar.visibility = View.GONE

                if (error != null) {
                    Toast.makeText(
                        requireContext(),
                        error.message ?: "Failed to load ambulance bookings",
                        Toast.LENGTH_SHORT
                    ).show()
                    renderBookings()
                    return@addSnapshotListener
                }

                bookingItems.removeAll { it.type == "AMBULANCE" }

                snapshot?.documents?.forEach { doc ->
                    val docPatientId = doc.getString("patientId") ?: ""
                    val docPatientEmail = doc.getString("patientEmail") ?: ""
                    val docPatientPhone = doc.getString("patientPhone") ?: ""

                    val belongsToPatient =
                        docPatientId == patientId ||
                                (patientEmail.isNotEmpty() && docPatientEmail == patientEmail) ||
                                (patientPhone.isNotEmpty() && docPatientPhone == patientPhone)

                    if (belongsToPatient) {
                        bookingItems.add(createAmbulanceItem(doc))
                    }
                }

                renderBookings()
            }
    }

    private fun createAppointmentItem(doc: DocumentSnapshot): BookingItem {
        val status = doc.getString("status") ?: "BOOKED"
        val doctorName = doc.getString("doctorName") ?: "Doctor"
        val specialization = doc.getString("specialization") ?: "Specialization"
        val hospitalName = doc.getString("hospitalName") ?: "Hospital"
        val date = doc.getString("appointmentDate") ?: "Date not set"
        val time = doc.getString("appointmentTime") ?: "Time not set"

        return BookingItem(
            id = doc.id,
            type = "APPOINTMENT",
            title = "Appointment with $doctorName",
            subtitle = "$specialization • $hospitalName",
            dateTime = "$date • $time",
            status = status,
            canCancel = status == "BOOKED"
        )
    }

    private fun createAmbulanceItem(doc: DocumentSnapshot): BookingItem {
        val status = doc.getString("status") ?: "REQUESTED"
        val bookingType = doc.getString("bookingType") ?: "AMBULANCE"
        val hospitalName = doc.getString("hospitalName") ?: "Hospital not selected"
        val pickup = doc.getString("pickupAddress") ?: "Pickup location"
        val date = doc.getString("bookingDate") ?: ""
        val time = doc.getString("bookingTime") ?: ""

        val activeStatuses = listOf(
            "SCHEDULED",
            "FINDING_DRIVER",
            "SEARCHING_DRIVER",
            "NO_DRIVER_AVAILABLE",
            "ACCEPTED",
            "REACHED_PATIENT",
            "HOSPITAL_SELECTED"
        )

        return BookingItem(
            id = doc.id,
            type = "AMBULANCE",
            title = "$bookingType Ambulance",
            subtitle = "To: $hospitalName\nPickup: $pickup",
            dateTime = if (date.isNotEmpty() || time.isNotEmpty()) {
                "$date • $time"
            } else {
                "Emergency booking"
            },
            status = status,
            canCancel = activeStatuses.contains(status)
        )
    }

    private fun renderBookings() {
        progressBar.visibility = View.GONE
        bookingContainer.removeAllViews()

        if (bookingItems.isEmpty()) {
            showEmpty("No bookings found")
            return
        }

        emptyLayout.visibility = View.GONE

        val activeFirst = bookingItems.sortedWith(
            compareByDescending<BookingItem> { it.canCancel }
                .thenByDescending { it.dateTime }
        )

        activeFirst.forEach { item ->
            addBookingCard(item)
        }
    }

    private fun addBookingCard(item: BookingItem) {
        val card = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_booking, bookingContainer, false)

        card.findViewById<TextView>(R.id.tvType).text = item.type
        card.findViewById<TextView>(R.id.tvTitle).text = item.title
        card.findViewById<TextView>(R.id.tvSubtitle).text = item.subtitle
        card.findViewById<TextView>(R.id.tvDateTime).text = item.dateTime
        card.findViewById<TextView>(R.id.tvStatus).text = item.status

        val btnView = card.findViewById<Button>(R.id.btnView)
        val btnCancel = card.findViewById<Button>(R.id.btnCancel)

        btnCancel.visibility = if (item.canCancel) View.VISIBLE else View.GONE

        btnView.setOnClickListener {
            if (item.type == "AMBULANCE") {
                val intent = Intent(requireContext(), BookAmbulance::class.java)
                intent.putExtra("bookingId", item.id)
                startActivity(intent)
            } else {
                val intent = Intent(requireContext(), ViewAppointment::class.java)
                intent.putExtra("appointmentId", item.id)
                startActivity(intent)
            }
        }

        btnCancel.setOnClickListener {
            if (item.type == "AMBULANCE") {
                cancelAmbulance(item.id)
            } else {
                cancelAppointment(item.id)
            }
        }

        bookingContainer.addView(card)
    }

    private fun cancelAppointment(appointmentId: String) {
        db.collection("appointments")
            .document(appointmentId)
            .update(
                mapOf(
                    "status" to "CANCELLED_BY_PATIENT",
                    "cancelledBy" to "patient",
                    "cancelledAt" to Timestamp.now(),
                    "updatedAt" to Timestamp.now()
                )
            )
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Appointment cancelled", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(
                    requireContext(),
                    it.message ?: "Failed to cancel appointment",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun cancelAmbulance(requestId: String) {
        db.collection("ambulanceRequests")
            .document(requestId)
            .update(
                mapOf(
                    "status" to "CANCELLED_BY_PATIENT",
                    "cancelledBy" to "patient",
                    "cancelledAt" to Timestamp.now(),
                    "updatedAt" to Timestamp.now()
                )
            )
            .addOnSuccessListener {
                val activeBookingId = requireActivity()
                    .getSharedPreferences("LifeLineRide", 0)
                    .getString("activeBookingId", "") ?: ""

                if (activeBookingId == requestId) {
                    requireActivity()
                        .getSharedPreferences("LifeLineRide", 0)
                        .edit()
                        .remove("activeBookingId")
                        .apply()
                }

                Toast.makeText(requireContext(), "Ambulance booking cancelled", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(
                    requireContext(),
                    it.message ?: "Failed to cancel ambulance",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun showEmpty(message: String) {
        progressBar.visibility = View.GONE
        bookingContainer.removeAllViews()
        emptyLayout.visibility = View.VISIBLE
        emptyLayout.findViewById<TextView>(R.id.tvEmptyMessage).text = message
    }

    override fun onDestroyView() {
        appointmentListener?.remove()
        ambulanceListener?.remove()
        super.onDestroyView()
    }
}
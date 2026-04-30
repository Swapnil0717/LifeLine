package com.example.lifeline

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class ProfileFragment : Fragment() {

    private lateinit var db: FirebaseFirestore

    private lateinit var tvDashboardTitle: TextView
    private lateinit var tvUserInfo: TextView

    private lateinit var tvSummaryTitle: TextView
    private lateinit var tvFirstCount: TextView
    private lateinit var tvFirstLabel: TextView
    private lateinit var tvSecondCount: TextView
    private lateinit var tvSecondLabel: TextView
    private lateinit var tvTotalInfo: TextView

    private lateinit var tvRecentTitle: TextView
    private lateinit var tvNoRecent: TextView
    private lateinit var recentContainer: LinearLayout

    private lateinit var btnResetPassword: Button
    private lateinit var btnRefresh: Button
    private lateinit var btnLogout: Button

    private var appointmentListener: ListenerRegistration? = null
    private var ambulanceListener: ListenerRegistration? = null

    private var userId = ""
    private var userName = ""
    private var userEmail = ""
    private var userPhone = ""
    private var role = ""

    private var completedCount = 0
    private var cancelledCount = 0
    private var totalCount = 0

    private val recentItems = mutableListOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        db = FirebaseFirestore.getInstance()

        bindViews(view)
        loadSession()
        setupUI()
        setupClicks()
        loadDashboardData()

        return view
    }

    private fun bindViews(view: View) {
        tvDashboardTitle = view.findViewById(R.id.tvDashboardTitle)
        tvUserInfo = view.findViewById(R.id.tvUserInfo)

        tvSummaryTitle = view.findViewById(R.id.tvSummaryTitle)
        tvFirstCount = view.findViewById(R.id.tvFirstCount)
        tvFirstLabel = view.findViewById(R.id.tvFirstLabel)
        tvSecondCount = view.findViewById(R.id.tvSecondCount)
        tvSecondLabel = view.findViewById(R.id.tvSecondLabel)
        tvTotalInfo = view.findViewById(R.id.tvTotalInfo)

        tvRecentTitle = view.findViewById(R.id.tvRecentTitle)
        tvNoRecent = view.findViewById(R.id.tvNoRecent)
        recentContainer = view.findViewById(R.id.recentContainer)

        btnResetPassword = view.findViewById(R.id.btnResetPassword)
        btnRefresh = view.findViewById(R.id.btnRefresh)
        btnLogout = view.findViewById(R.id.btnLogout)
    }

    private fun loadSession() {
        val session = requireActivity().getSharedPreferences("LifeLineSession", 0)

        userId = session.getString("userId", "") ?: ""
        userName = session.getString("userName", "User") ?: "User"
        userEmail = session.getString("userEmail", "") ?: ""
        userPhone = session.getString("userPhone", "") ?: ""
        role = session.getString("role", session.getString("collection", "patient")) ?: "patient"
    }

    private fun isDoctor(): Boolean {
        return role.equals("doctor", true) || role.equals("doctors", true)
    }

    private fun setupUI() {
        if (isDoctor()) {
            tvDashboardTitle.text = "Doctor Dashboard"
            tvSummaryTitle.text = "Appointment Summary"
            tvFirstLabel.text = "Completed"
            tvSecondLabel.text = "Cancelled"
            tvRecentTitle.text = "Recent Appointments"
        } else {
            tvDashboardTitle.text = "Patient Dashboard"
            tvSummaryTitle.text = "Activity Summary"
            tvFirstLabel.text = "Appointments"
            tvSecondLabel.text = "Ambulance"
            tvRecentTitle.text = "Recent Activity"
        }

        tvUserInfo.text = buildString {
            append(userName)
            if (userPhone.isNotEmpty()) append(" • $userPhone")
            if (userEmail.isNotEmpty()) append("\n$userEmail")
        }
    }

    private fun setupClicks() {
        btnRefresh.setOnClickListener {
            loadDashboardData()
            Toast.makeText(requireContext(), "Dashboard refreshed", Toast.LENGTH_SHORT).show()
        }

        btnResetPassword.setOnClickListener {
            val intent = Intent(requireContext(), ForgetPassword::class.java)
            startActivity(intent)
        }

        btnLogout.setOnClickListener {
            requireActivity()
                .getSharedPreferences("LifeLineSession", 0)
                .edit()
                .clear()
                .apply()

            val intent = Intent(requireContext(), Login::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }

    private fun loadDashboardData() {
        if (userId.isEmpty()) {
            tvTotalInfo.text = "User session not found"
            return
        }

        completedCount = 0
        cancelledCount = 0
        totalCount = 0
        recentItems.clear()
        recentContainer.removeAllViews()
        tvNoRecent.visibility = View.VISIBLE

        appointmentListener?.remove()
        ambulanceListener?.remove()

        if (isDoctor()) {
            loadDoctorDashboard()
        } else {
            loadPatientDashboard()
        }
    }

    private fun loadDoctorDashboard() {
        appointmentListener = db.collection("appointments")
            .whereEqualTo("doctorId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    tvTotalInfo.text = error.message ?: "Failed to load dashboard"
                    return@addSnapshotListener
                }

                val docs = snapshot?.documents ?: emptyList()

                totalCount = docs.size
                completedCount = docs.count { it.getString("status") == "COMPLETED" }
                cancelledCount = docs.count {
                    (it.getString("status") ?: "").contains("CANCELLED")
                }

                recentItems.clear()

                docs.take(5).forEach {
                    val patientName = it.getString("patientName") ?: "Patient"
                    val date = it.getString("appointmentDate") ?: "Date"
                    val time = it.getString("appointmentTime") ?: "Time"
                    val status = it.getString("status") ?: "BOOKED"
                    recentItems.add("$patientName • $date $time • $status")
                }

                tvFirstCount.text = completedCount.toString()
                tvSecondCount.text = cancelledCount.toString()
                tvTotalInfo.text = "Total appointments handled: $totalCount"

                renderRecentItems()
            }
    }

    private fun loadPatientDashboard() {
        var appointmentCount = 0
        var ambulanceCount = 0

        appointmentListener = db.collection("appointments")
            .whereEqualTo("patientId", userId)
            .addSnapshotListener { snapshot, _ ->
                val docs = snapshot?.documents ?: emptyList()

                appointmentCount = docs.size
                recentItems.removeAll { it.startsWith("Appointment") }

                docs.take(3).forEach {
                    val doctorName = it.getString("doctorName") ?: "Doctor"
                    val date = it.getString("appointmentDate") ?: "Date"
                    val time = it.getString("appointmentTime") ?: "Time"
                    val status = it.getString("status") ?: "BOOKED"
                    recentItems.add("Appointment • $doctorName • $date $time • $status")
                }

                tvFirstCount.text = appointmentCount.toString()
                tvTotalInfo.text = "Total activities: ${appointmentCount + ambulanceCount}"

                renderRecentItems()
            }

        ambulanceListener = db.collection("ambulanceRequests")
            .whereEqualTo("patientId", userId)
            .addSnapshotListener { snapshot, _ ->
                val docs = snapshot?.documents ?: emptyList()

                ambulanceCount = docs.size
                recentItems.removeAll { it.startsWith("Ambulance") }

                docs.take(3).forEach {
                    val hospitalName = it.getString("hospitalName") ?: "Hospital"
                    val status = it.getString("status") ?: "REQUESTED"
                    recentItems.add("Ambulance • $hospitalName • $status")
                }

                tvSecondCount.text = ambulanceCount.toString()
                tvTotalInfo.text = "Total activities: ${appointmentCount + ambulanceCount}"

                renderRecentItems()
            }
    }

    private fun renderRecentItems() {
        recentContainer.removeAllViews()

        if (recentItems.isEmpty()) {
            tvNoRecent.visibility = View.VISIBLE
            return
        }

        tvNoRecent.visibility = View.GONE

        recentItems.take(6).forEach { text ->
            val item = TextView(requireContext())
            item.text = text
            item.textSize = 13f
            item.setTextColor(resources.getColor(android.R.color.darker_gray, null))
            item.setPadding(0, 10, 0, 10)
            item.typeface = ResourcesCompat.getFont(requireContext(), R.font.poppins_bold)
            recentContainer.addView(item)
        }
    }

    override fun onDestroyView() {
        appointmentListener?.remove()
        ambulanceListener?.remove()
        super.onDestroyView()
    }
}
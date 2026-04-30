package com.example.lifeline

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class Booking : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var hospitalInput: AutoCompleteTextView

    private lateinit var pickupLocation: EditText
    private lateinit var patientName: EditText
    private lateinit var age: EditText
    private lateinit var date: EditText
    private lateinit var time: EditText
    private lateinit var btnPickupMap: ImageButton

    private var selectedHospitalId = ""
    private var selectedHospitalName = ""
    private var selectedHospitalAddress = ""
    private var selectedHospitalLat = 0.0
    private var selectedHospitalLng = 0.0

    private var selectedPickupLat = 0.0
    private var selectedPickupLng = 0.0

    private val hospitalMap = HashMap<String, HospitalData>()

    data class HospitalData(
        val id: String,
        val name: String,
        val address: String,
        val lat: Double,
        val lng: Double
    )

    private val pickupPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val address = result.data?.getStringExtra("pickupAddress") ?: ""
                selectedPickupLat = result.data?.getDoubleExtra("pickupLat", 0.0) ?: 0.0
                selectedPickupLng = result.data?.getDoubleExtra("pickupLng", 0.0) ?: 0.0

                pickupLocation.setText(address)
                pickupLocation.setSelection(pickupLocation.text.length)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_booking)

        db = FirebaseFirestore.getInstance()

        hospitalInput = findViewById(R.id.etHospitalName)
        pickupLocation = findViewById(R.id.etPickupLocation)
        patientName = findViewById(R.id.etPatientName)
        age = findViewById(R.id.etAge)
        date = findViewById(R.id.etDate)
        time = findViewById(R.id.etTime)
        btnPickupMap = findViewById(R.id.btnPickupMap)

        val next = findViewById<Button>(R.id.next)
        val back = findViewById<ImageButton>(R.id.back)

        back.setOnClickListener { finish() }

        loadHospitals()

        date.setOnClickListener { openDatePicker() }
        time.setOnClickListener { openTimePicker() }

        btnPickupMap.setOnClickListener {
            openPickupPicker()
        }

        pickupLocation.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                pickupLocation.requestFocus()
                pickupLocation.setSelection(pickupLocation.text.length)
            }
            false
        }

        next.setOnClickListener {
            validateHospitalLocationAndGoToPayment()
        }
    }

    private fun openPickupPicker() {
        val intent = Intent(this, PickupLocationPickerActivity::class.java)
        pickupPickerLauncher.launch(intent)
    }

    private fun validateHospitalLocationAndGoToPayment() {
        val hospitalNameText = hospitalInput.text.toString().trim()
        val pickup = pickupLocation.text.toString().trim()
        val bookingDate = date.text.toString().trim()
        val bookingTime = time.text.toString().trim()
        val patient = patientName.text.toString().trim()
        val patientAge = age.text.toString().trim()

        when {
            hospitalNameText.isEmpty() -> {
                hospitalInput.error = "Select hospital"
                hospitalInput.requestFocus()
                hospitalInput.showDropDown()
            }

            pickup.isEmpty() -> {
                pickupLocation.error = "Enter pickup location"
            }

            bookingDate.isEmpty() -> {
                date.error = "Select date"
            }

            bookingTime.isEmpty() -> {
                time.error = "Select time"
            }

            patientAge.isNotEmpty() && patientAge.toIntOrNull() == null -> {
                age.error = "Enter valid age"
            }

            else -> {
                if (selectedPickupLat == 0.0 || selectedPickupLng == 0.0) {
                    searchTypedPickupBeforePayment(
                        pickup,
                        bookingDate,
                        bookingTime,
                        patient,
                        patientAge
                    )
                    return
                }

                if (
                    selectedHospitalId.isNotEmpty() &&
                    selectedHospitalLat != 0.0 &&
                    selectedHospitalLng != 0.0
                ) {
                    goToPayment(pickup, bookingDate, bookingTime, patient, patientAge)
                } else {
                    Toast.makeText(this, "Finding hospital location...", Toast.LENGTH_SHORT).show()

                    HospitalLocationHelper.findOrCreateHospital(
                        context = this,
                        hospitalName = hospitalNameText,
                        role = "patient",
                        onSuccess = { hospital: HospitalLocationHelper.HospitalLocation ->
                            runOnUiThread {
                                selectedHospitalId = hospital.id
                                selectedHospitalName = hospital.name
                                selectedHospitalAddress = hospital.address
                                selectedHospitalLat = hospital.lat
                                selectedHospitalLng = hospital.lng

                                goToPayment(pickup, bookingDate, bookingTime, patient, patientAge)
                            }
                        },
                        onFailure = { message: String ->
                            runOnUiThread {
                                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                }
            }
        }
    }

    private fun searchTypedPickupBeforePayment(
        pickup: String,
        bookingDate: String,
        bookingTime: String,
        patient: String,
        patientAge: String
    ) {
        Toast.makeText(this, "Finding pickup location...", Toast.LENGTH_SHORT).show()

        PickupAddressHelper.findPickupAddress(
            context = this,
            text = pickup,
            onSuccess = { loc ->
                runOnUiThread {
                    selectedPickupLat = loc.lat
                    selectedPickupLng = loc.lng
                    pickupLocation.setText(loc.address)
                    pickupLocation.setSelection(pickupLocation.text.length)

                    validateHospitalLocationAndGoToPayment()
                }
            },
            onFailure = { message ->
                runOnUiThread {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun goToPayment(
        pickup: String,
        bookingDate: String,
        bookingTime: String,
        patient: String,
        patientAge: String
    ) {
        val intent = Intent(this, Payment::class.java)

        intent.putExtra("hospitalId", selectedHospitalId)
        intent.putExtra("hospitalName", selectedHospitalName)
        intent.putExtra("hospitalAddress", selectedHospitalAddress)
        intent.putExtra("hospitalLat", selectedHospitalLat)
        intent.putExtra("hospitalLng", selectedHospitalLng)

        intent.putExtra("pickupLocation", pickupLocation.text.toString().trim())
        intent.putExtra("pickupLat", selectedPickupLat)
        intent.putExtra("pickupLng", selectedPickupLng)

        intent.putExtra("bookingDate", bookingDate)
        intent.putExtra("bookingTime", bookingTime)
        intent.putExtra("patientName", patient.ifEmpty { "Patient" })
        intent.putExtra("age", patientAge)
        intent.putExtra("totalAmount", 2)

        startActivity(intent)
    }

    private fun loadHospitals() {
        db.collection("hospitals")
            .get()
            .addOnSuccessListener { result ->
                val hospitalNames = ArrayList<String>()
                hospitalMap.clear()

                for (doc in result.documents) {
                    val id = doc.getString("id") ?: doc.id
                    val name = doc.getString("name") ?: ""

                    if (name.isNotEmpty() && !hospitalNames.contains(name)) {
                        val address = doc.getString("address") ?: ""
                        val lat = doc.getDouble("lat") ?: 0.0
                        val lng = doc.getDouble("lng") ?: 0.0

                        hospitalNames.add(name)

                        hospitalMap[name] = HospitalData(
                            id = id,
                            name = name,
                            address = address,
                            lat = lat,
                            lng = lng
                        )
                    }
                }

                val adapter = HospitalDropdownAdapter(hospitalNames)

                hospitalInput.setAdapter(adapter)
                hospitalInput.threshold = 1

                hospitalInput.setOnClickListener {
                    hospitalInput.showDropDown()
                }

                hospitalInput.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        hospitalInput.showDropDown()
                    }
                }

                hospitalInput.setOnItemClickListener { _, _, position, _ ->
                    val selectedName = adapter.getItem(position) ?: ""
                    val hospital = hospitalMap[selectedName]

                    if (hospital != null) {
                        selectedHospitalId = hospital.id
                        selectedHospitalName = hospital.name
                        selectedHospitalAddress = hospital.address
                        selectedHospitalLat = hospital.lat
                        selectedHospitalLng = hospital.lng
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(
                    this,
                    it.message ?: "Failed to load hospitals",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    inner class HospitalDropdownAdapter(
        private val items: List<String>
    ) : ArrayAdapter<String>(this, R.layout.item_hospital_dropdown, items) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(
                R.layout.item_hospital_dropdown,
                parent,
                false
            )

            view.findViewById<TextView>(R.id.tvHospitalName).text = items[position]
            view.findViewById<TextView>(R.id.tvHospitalSub).text = "Tap to select hospital"

            return view
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            return getView(position, convertView, parent)
        }
    }

    private fun openDatePicker() {
        val calendar = Calendar.getInstance()

        val dialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedCalendar = Calendar.getInstance()
                selectedCalendar.set(year, month, dayOfMonth)

                val formatter = SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH)
                date.setText(formatter.format(selectedCalendar.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        dialog.datePicker.minDate = System.currentTimeMillis()
        dialog.show()
    }

    private fun openTimePicker() {
        val calendar = Calendar.getInstance()

        val dialog = TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                val selectedCalendar = Calendar.getInstance()
                selectedCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                selectedCalendar.set(Calendar.MINUTE, minute)

                val formatter = SimpleDateFormat("hh:mm a", Locale.ENGLISH)
                time.setText(formatter.format(selectedCalendar.time))
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            false
        )

        dialog.show()
    }
}
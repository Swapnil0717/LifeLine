# 🚑 LifeLine – Smart Ambulance & Doctor Appointment Booking App

<div align="center">

# 🚑 LifeLine

### *Emergency Ambulance Booking & Doctor Appointment Scheduling Platform*

📍 Real-Time Tracking • 🚑 Instant Ambulance Booking • 👨‍⚕️ Doctor Appointments • 📞 In-App Communication

</div>

---

## 📖 Overview

**LifeLine** is an Android-based healthcare assistance application designed to provide quick access to emergency ambulance services and doctor appointments.

The application allows patients to:

* 🚑 Book an ambulance instantly during emergencies
* 📅 Schedule ambulance rides for a specific date and time
* 👨‍⚕️ Book doctor appointments
* 📍 Track ambulance location in real time
* 📞 Contact ambulance drivers directly
* 🗺️ View live locations of both patient and driver
* 🔔 Receive booking and appointment updates

The platform also provides dedicated functionality for doctors and ambulance drivers to efficiently manage requests and appointments.

---

## ✨ Key Features

### 🚑 Ambulance Module

* Emergency ambulance booking
* Scheduled ambulance booking
* Live ambulance tracking
* Driver location sharing
* Patient location sharing
* Direct call between patient and driver
* Route visualization on map
* Booking history management

### 👨‍⚕️ Doctor Appointment Module

* Browse available doctors
* Book appointments
* Appointment scheduling
* Appointment cancellation
* Appointment status tracking
* Doctor approval/rejection system
* Doctor appointment management dashboard

### 📍 Real-Time Tracking

* Live GPS tracking
* Driver location updates
* Patient location updates
* Route display on map
* Real-time movement monitoring

### 🔐 Authentication & Security

* Firebase Authentication
* Secure user login
* Role-based access

  * Patient
  * Doctor
  * Ambulance Driver

---

## 🏗️ System Roles

### 👤 Patient

* Register/Login
* Book ambulance
* Schedule ambulance
* Track ambulance
* Book doctor appointment
* Cancel appointment
* Contact driver

### 🚑 Driver

* Accept ambulance requests
* Share live location
* Call patient
* View patient location
* Manage assigned rides

### 👨‍⚕️ Doctor

* View appointments
* Accept appointments
* Reject appointments
* Cancel appointments
* Manage patient schedules

---

## 📱 Screens Included

* 🔐 Login Screen
* 📝 Registration Screen
* 🏠 Home Dashboard
* 🚑 Ambulance Booking Screen
* 📅 Ambulance Scheduling Screen
* 👨‍⚕️ Doctor Listing Screen
* 📋 Appointment Booking Screen
* 🗺️ Live Tracking Screen
* 📞 Call Interface
* 👨‍⚕️ Doctor Dashboard
* 🚑 Driver Dashboard

---

# 🛠️ Tech Stack

<div align="center">

| Technology                                                                                                                                      | Usage                     |
| ----------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------- |
| <img src="https://raw.githubusercontent.com/devicons/devicon/master/icons/androidstudio/androidstudio-original.svg" width="30"/> Android Studio | Android Development       |
| <img src="https://raw.githubusercontent.com/devicons/devicon/master/icons/kotlin/kotlin-original.svg" width="30"/> Kotlin                       | Application Development   |
| <img src="https://raw.githubusercontent.com/devicons/devicon/master/icons/firebase/firebase-plain.svg" width="30"/> Firebase                    | Authentication & Database |
| 🗺️ Overpass API                                                                                                                                | Map & Location Services   |
| 📍 GPS Location Services                                                                                                                        | Real-Time Tracking        |
| ☁️ Firebase Firestore                                                                                                                           | Cloud Database            |
| 🔥 Firebase Authentication                                                                                                                      | User Authentication       |
| 📞 Android Telephony API                                                                                                                        | Calling Functionality     |

</div>

---

## 🏛️ Architecture

```text
                    ┌──────────────┐
                    │   Patient    │
                    └──────┬───────┘
                           │
                           ▼
                 ┌──────────────────┐
                 │ Firebase Backend │
                 └──────────────────┘
                     ▲          ▲
                     │          │
          ┌──────────┘          └──────────┐
          ▼                               ▼
 ┌─────────────────┐             ┌─────────────────┐
 │ Ambulance Driver│             │     Doctor      │
 └─────────────────┘             └─────────────────┘
          │                               │
          └───────────Map Tracking────────┘
                        Overpass API
```

---

## 📂 Project Structure

```bash
LifeLine/
│
├── app/
│   ├── activities/
│   ├── fragments/
│   ├── adapters/
│   ├── models/
│   ├── firebase/
│   ├── maps/
│   ├── services/
│   └── utils/
│
├── assets/
├── res/
├── AndroidManifest.xml
└── build.gradle
```

---

## 🚀 Installation

### Clone Repository

```bash
git clone https://github.com/your-username/lifeline.git
```

### Open Project

```bash
Android Studio → Open Existing Project
```

### Configure Firebase

1. Create Firebase Project
2. Add Android App
3. Download `google-services.json`
4. Place it inside:

```bash
app/google-services.json
```

### Sync Gradle

```bash
Sync Project with Gradle Files
```

### Run Application

```bash
Run ▶️ on Android Emulator or Physical Device
```

---

## 🔄 Application Workflow

```text
Patient
   │
   ├── Book Ambulance
   │       │
   │       ▼
   │  Driver Receives Request
   │       │
   │       ▼
   │  Accept Request
   │       │
   │       ▼
   │  Real-Time Tracking
   │       │
   │       ▼
   │  Emergency Service Completed
   │
   └── Book Doctor Appointment
           │
           ▼
      Doctor Reviews
           │
      Accept/Reject
           │
           ▼
      Appointment Confirmed
```

---

## 🎯 Future Enhancements

* 💳 Online Payments
* 🤖 AI Emergency Assistance
* 📹 Video Consultation
* 🏥 Hospital Bed Availability
* 🚨 SOS Button
* 📊 Health Records Management
* 🌐 Multi-language Support
* 🔔 Push Notifications

---

## 📸 Screenshots

```markdown
Add your screenshots here

screenshots/
├── login.png
├── home.png
├── ambulance_booking.png
├── tracking.png
├── doctor_appointment.png
└── driver_dashboard.png
```

---

## 👩‍💻 Developed By

### Team LifeLine

🚑 Emergency Healthcare & Ambulance Management System

---

## ⭐ Support

If you found this project useful:

⭐ Star the repository

🍴 Fork the project

🛠️ Contribute to improve LifeLine

---

<div align="center">

### 🚑 Saving Lives Through Technology ❤️

**LifeLine – Emergency Assistance at Your Fingertips**

</div>

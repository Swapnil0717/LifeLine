const admin = require("firebase-admin");
const { onDocumentUpdated } = require("firebase-functions/v2/firestore");

admin.initializeApp();

exports.notifyPatientWhenDriverAccepts = onDocumentUpdated(
  "ambulanceRequests/{requestId}",
  async (event) => {
    const before = event.data.before.data();
    const after = event.data.after.data();

    if (!before || !after) return;

    const beforeStatus = before.status;
    const afterStatus = after.status;

    if (beforeStatus === "ACCEPTED" || afterStatus !== "ACCEPTED") {
      return;
    }

    const patientId = after.patientId;
    const driverName = after.driverName || "Your driver";
    const ambulanceNumber = after.ambulanceNumber || "Ambulance";

    if (!patientId) {
      console.log("Guest booking or missing patientId. Cannot send FCM.");
      return;
    }

    const patientDoc = await admin
      .firestore()
      .collection("patients")
      .doc(patientId)
      .get();

    if (!patientDoc.exists) {
      console.log("Patient document not found.");
      return;
    }

    const patientData = patientDoc.data();
    const patientToken = patientData.patientFcmToken;

    if (!patientToken) {
      console.log("Patient FCM token not found.");
      return;
    }

    const message = {
      token: patientToken,
      notification: {
        title: "Driver is coming",
        body: `${driverName} is coming in ambulance ${ambulanceNumber}`
      },
      data: {
        type: "DRIVER_ACCEPTED",
        requestId: event.params.requestId,
        driverName: driverName,
        ambulanceNumber: ambulanceNumber
      },
      android: {
        priority: "high",
        notification: {
          channelId: "lifeline_alerts",
          sound: "default"
        }
      }
    };

    await admin.messaging().send(message);

    console.log("Notification sent to patient:", patientId);
  }
);
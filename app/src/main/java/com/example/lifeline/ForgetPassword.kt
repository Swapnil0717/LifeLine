package com.example.lifeline

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

class ForgetPassword : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var btnSend: Button
    private lateinit var btnProgress: ProgressBar

    private val collections = listOf("patients", "doctors", "drivers")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_forget_password)

        db = FirebaseFirestore.getInstance()

        val back = findViewById<ImageButton>(R.id.back)
        val email = findViewById<EditText>(R.id.etEmail)
        btnSend = findViewById(R.id.btnSend)
        btnProgress = findViewById(R.id.btnProgress)

        back.setOnClickListener { finish() }

        btnSend.setOnClickListener {
            val userEmail = email.text.toString().trim()

            when {
                userEmail.isEmpty() -> email.error = "Enter email"
                !Patterns.EMAIL_ADDRESS.matcher(userEmail).matches() -> email.error = "Enter valid email"
                else -> findUserAndSendResetLink(userEmail)
            }
        }
    }

    private fun findUserAndSendResetLink(email: String) {
        setLoading(true)

        searchUserByEmail(email, 0) { found, collectionName, documentId, name ->
            if (!found || collectionName == null || documentId == null) {
                setLoading(false)
                Toast.makeText(this, "No account found with this email", Toast.LENGTH_SHORT).show()
                return@searchUserByEmail
            }

            val token = UUID.randomUUID().toString()
            val expiresAt = System.currentTimeMillis() + (15 * 60 * 1000)

            val tokenData = hashMapOf<String, Any>(
                "email" to email,
                "collection" to collectionName,
                "userId" to documentId,
                "token" to token,
                "expiresAt" to expiresAt,
                "used" to false,
                "createdAt" to Timestamp.now()
            )

            db.collection("passwordResetTokens")
                .document(token)
                .set(tokenData)
                .addOnSuccessListener {
                    Thread {
                        val sent = EmailSender.sendResetPasswordEmail(
                            toEmail = email,
                            userName = name ?: "User",
                            token = token
                        )

                        runOnUiThread {
                            setLoading(false)

                            if (sent) {
                                Toast.makeText(this, "Reset link sent to your email", Toast.LENGTH_LONG).show()
                                finish()
                            } else {
                                Toast.makeText(this, "Failed to send reset email", Toast.LENGTH_LONG).show()
                            }
                        }
                    }.start()
                }
                .addOnFailureListener {
                    setLoading(false)
                    Toast.makeText(this, it.message ?: "Failed to create reset token", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun searchUserByEmail(
        email: String,
        index: Int,
        callback: (Boolean, String?, String?, String?) -> Unit
    ) {
        if (index >= collections.size) {
            callback(false, null, null, null)
            return
        }

        val collection = collections[index]

        db.collection(collection)
            .whereEqualTo("email", email)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    val doc = result.documents[0]
                    val name = doc.getString("name")
                    callback(true, collection, doc.id, name)
                } else {
                    searchUserByEmail(email, index + 1, callback)
                }
            }
            .addOnFailureListener {
                searchUserByEmail(email, index + 1, callback)
            }
    }

    private fun setLoading(isLoading: Boolean) {
        btnSend.isEnabled = !isLoading
        btnSend.text = if (isLoading) "" else "Send Reset Link"
        btnProgress.visibility = if (isLoading) View.VISIBLE else View.GONE
    }
}
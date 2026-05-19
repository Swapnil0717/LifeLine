package com.example.lifeline

import android.os.StrictMode
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

object EmailSender {

    private const val SENDER_EMAIL = "your-email"
    private const val APP_PASSWORD = "your-email-app password"

    private fun createSession(): Session {
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        val props = Properties()
        props["mail.smtp.auth"] = "true"
        props["mail.smtp.starttls.enable"] = "true"
        props["mail.smtp.host"] = "smtp.gmail.com"
        props["mail.smtp.port"] = "587"

        return Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(SENDER_EMAIL, APP_PASSWORD)
            }
        })
    }

    fun sendOtpEmail(toEmail: String, userName: String, otp: String): Boolean {
        return try {
            val session = createSession()

            val message = MimeMessage(session)
            message.setFrom(InternetAddress(SENDER_EMAIL, "LifeLine Ambulance Service"))
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail))
            message.subject = "Your LifeLine Verification Code"

            val html = """
                <div style="font-family:Arial,sans-serif;background:#f6f7fb;padding:24px;">
                    <div style="max-width:600px;margin:auto;background:white;border-radius:18px;overflow:hidden;">
                        <div style="background:#F43F46;padding:24px;text-align:center;">
                            <h1 style="color:white;margin:0;">LifeLine</h1>
                            <p style="color:#ffeaea;margin:6px 0 0;">Ambulance Service</p>
                        </div>

                        <div style="padding:28px;">
                            <h2 style="color:#0D2A4E;">Verify Your Account</h2>
                            <p>Hello <b>$userName</b>,</p>
                            <p>Your LifeLine verification code is:</p>

                            <div style="font-size:32px;font-weight:bold;color:#F43F46;letter-spacing:8px;text-align:center;margin:24px 0;">
                                $otp
                            </div>

                            <p>This OTP is valid for a short time. Do not share it with anyone.</p>
                            <p style="color:#777;font-size:13px;margin-top:24px;">Stay safe,<br><b>Team LifeLine</b></p>
                        </div>
                    </div>
                </div>
            """.trimIndent()

            message.setContent(html, "text/html; charset=utf-8")
            Transport.send(message)
            true

        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun sendResetPasswordEmail(toEmail: String, userName: String, token: String): Boolean {
        return try {
            val session = createSession()

            val resetLink = "https://lifeline/reset-password?token=$token"
            val customLink = "lifeline://reset-password?token=$token"

            val message = MimeMessage(session)
            message.setFrom(InternetAddress(SENDER_EMAIL, "LifeLine Ambulance Service"))
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail))
            message.subject = "Reset Your LifeLine Password"

            val html = """
                <div style="font-family:Arial,sans-serif;background:#f6f7fb;padding:24px;">
                    <div style="max-width:600px;margin:auto;background:white;border-radius:18px;overflow:hidden;">
                        <div style="background:#F43F46;padding:24px;text-align:center;">
                            <h1 style="color:white;margin:0;">LifeLine</h1>
                            <p style="color:#ffeaea;margin:6px 0 0;">Password Reset Request</p>
                        </div>

                        <div style="padding:28px;">
                            <h2 style="color:#0D2A4E;">Reset Your Password</h2>
                            <p>Hello <b>$userName</b>,</p>
                            <p>Click the button below to reset your LifeLine account password.</p>

                            <div style="text-align:center;margin:28px 0;">
                                <a href="$resetLink"
                                   style="background:#F43F46;color:white;padding:14px 24px;text-decoration:none;border-radius:10px;font-weight:bold;">
                                   Reset Password
                                </a>
                            </div>

                            <p>This link is valid for 15 minutes.</p>

                            <p><b>Backup app link:</b></p>
                            <p style="word-break:break-all;color:#F43F46;">$customLink</p>

                            <p style="color:#777;font-size:13px;margin-top:24px;">Stay safe,<br><b>Team LifeLine</b></p>
                        </div>
                    </div>
                </div>
            """.trimIndent()

            message.setContent(html, "text/html; charset=utf-8")
            Transport.send(message)
            true

        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun sendSuccessEmail(toEmail: String, userName: String, role: String): Boolean {
        return try {
            val session = createSession()

            val message = MimeMessage(session)
            message.setFrom(InternetAddress(SENDER_EMAIL, "LifeLine Ambulance Service"))
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail))
            message.subject = "Welcome to LifeLine"

            val html = """
                <div style="font-family:Arial,sans-serif;background:#f6f7fb;padding:24px;">
                    <div style="max-width:600px;margin:auto;background:white;border-radius:18px;overflow:hidden;">
                        <div style="background:#F43F46;padding:24px;text-align:center;">
                            <h1 style="color:white;margin:0;">Registration Successful 🎉</h1>
                        </div>

                        <div style="padding:28px;">
                            <h2 style="color:#0D2A4E;">Welcome, $userName</h2>
                            <p>Your LifeLine account has been successfully registered as <b>$role</b>.</p>
                            <p>You can now login and start using LifeLine services.</p>
                            <p style="color:#777;font-size:13px;margin-top:24px;">Stay safe,<br><b>Team LifeLine</b></p>
                        </div>
                    </div>
                </div>
            """.trimIndent()

            message.setContent(html, "text/html; charset=utf-8")
            Transport.send(message)
            true

        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

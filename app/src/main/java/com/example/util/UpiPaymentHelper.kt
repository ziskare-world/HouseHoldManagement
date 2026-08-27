package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object UpiPaymentHelper {

    /**
     * Builds standard UPI URI:
     * upi://pay?pa=upiId&pn=name&am=amount&cu=INR&tn=note
     */
    fun buildUpiUri(
        payeeUpiId: String,
        payeeName: String,
        amount: Double,
        note: String
    ): Uri {
        val formattedAmount = String.format(java.util.Locale.US, "%.2f", amount)
        val cleanNote = Uri.encode(note.take(40))
        val cleanName = Uri.encode(payeeName)
        val cleanUpi = payeeUpiId.trim()

        val uriString = "upi://pay?pa=$cleanUpi&pn=$cleanName&am=$formattedAmount&cu=INR&tn=$cleanNote"
        return Uri.parse(uriString)
    }

    /**
     * Launch UPI payment via Google Pay or standard UPI chooser
     */
    fun launchUpiPayment(
        context: Context,
        payeeUpiId: String,
        payeeName: String,
        amount: Double,
        note: String,
        preferredApp: String? = null // e.g. "com.google.android.apps.nbu.paisa.user"
    ): Boolean {
        if (payeeUpiId.isBlank()) {
            Toast.makeText(context, "Recipient UPI ID is missing!", Toast.LENGTH_LONG).show()
            return false
        }

        val upiUri = buildUpiUri(payeeUpiId, payeeName, amount, note)
        val intent = Intent(Intent.ACTION_VIEW, upiUri)

        if (preferredApp != null) {
            intent.setPackage(preferredApp)
        }

        return try {
            val chooser = if (preferredApp == null) {
                Intent.createChooser(intent, "Pay ₹$amount to $payeeName via UPI")
            } else {
                intent
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            // Fallback to general intent without specific package
            try {
                val genericIntent = Intent(Intent.ACTION_VIEW, upiUri)
                context.startActivity(Intent.createChooser(genericIntent, "Select UPI Payment App"))
                true
            } catch (ex: Exception) {
                Toast.makeText(
                    context,
                    "No UPI App found (Google Pay / PhonePe / Paytm). You can manually mark as paid.",
                    Toast.LENGTH_LONG
                ).show()
                false
            }
        }
    }

    /**
     * Launch payment from a raw UPI URI string
     */
    fun launchUpiUri(
        context: Context,
        upiUriString: String,
        preferredPackage: String? = null
    ): Boolean {
        return try {
            val uri = Uri.parse(upiUriString)
            val intent = Intent(Intent.ACTION_VIEW, uri)
            if (!preferredPackage.isNullOrBlank()) {
                intent.setPackage(preferredPackage)
            }
            val chooser = if (preferredPackage.isNullOrBlank()) {
                Intent.createChooser(intent, "Pay via UPI App")
            } else {
                intent
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            try {
                val uri = Uri.parse(upiUriString)
                val genericIntent = Intent(Intent.ACTION_VIEW, uri)
                context.startActivity(Intent.createChooser(genericIntent, "Select UPI Payment App"))
                true
            } catch (ex: Exception) {
                Toast.makeText(
                    context,
                    "No UPI App found. You can copy the recipient UPI ID to pay manually.",
                    Toast.LENGTH_LONG
                ).show()
                false
            }
        }
    }
}

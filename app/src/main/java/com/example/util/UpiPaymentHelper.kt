package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

data class UpiPaymentResult(
    val isSuccess: Boolean,
    val transactionId: String?,
    val approvalRefNo: String?,
    val responseCode: String?,
    val rawResponse: String,
    val status: String
)

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
     * Creates an Intent to launch UPI payment with activity result tracking
     */
    fun createUpiIntent(
        payeeUpiId: String,
        payeeName: String,
        amount: Double,
        note: String,
        preferredApp: String? = null
    ): Intent {
        val upiUri = buildUpiUri(payeeUpiId, payeeName, amount, note)
        val intent = Intent(Intent.ACTION_VIEW, upiUri)
        if (!preferredApp.isNullOrBlank()) {
            intent.setPackage(preferredApp)
        }
        return if (preferredApp.isNullOrBlank()) {
            Intent.createChooser(intent, "Pay ₹$amount to $payeeName via UPI")
        } else {
            intent
        }
    }

    /**
     * Creates an Intent from raw UPI URI string
     */
    fun createUpiIntentFromUri(
        upiUriString: String,
        preferredPackage: String? = null
    ): Intent {
        val uri = Uri.parse(upiUriString)
        val intent = Intent(Intent.ACTION_VIEW, uri)
        if (!preferredPackage.isNullOrBlank()) {
            intent.setPackage(preferredPackage)
        }
        return if (preferredPackage.isNullOrBlank()) {
            Intent.createChooser(intent, "Pay via UPI App")
        } else {
            intent
        }
    }

    /**
     * Parses standard UPI response string from payment app result intent
     */
    fun parseUpiResponse(rawResponse: String?): UpiPaymentResult {
        if (rawResponse.isNullOrBlank()) {
            return UpiPaymentResult(
                isSuccess = false,
                transactionId = null,
                approvalRefNo = null,
                responseCode = null,
                rawResponse = "",
                status = "UNKNOWN"
            )
        }

        val params = mutableMapOf<String, String>()
        val tokens = rawResponse.split("&")
        for (token in tokens) {
            val parts = token.split("=")
            if (parts.size >= 2) {
                params[parts[0].trim().lowercase()] = parts[1].trim()
            }
        }

        val status = params["status"]?.uppercase()
            ?: if (rawResponse.contains("success", ignoreCase = true)) "SUCCESS"
            else if (rawResponse.contains("fail", ignoreCase = true)) "FAILURE"
            else "UNKNOWN"

        val isSuccess = status == "SUCCESS" || params["responsecode"] == "00" || params["responsecode"] == "0"
        val txnId = params["txnid"] ?: params["txnref"]
        val refNo = params["approvalrefno"] ?: params["ref"] ?: params["approval_ref_no"]

        return UpiPaymentResult(
            isSuccess = isSuccess,
            transactionId = txnId,
            approvalRefNo = refNo,
            responseCode = params["responsecode"],
            rawResponse = rawResponse,
            status = status
        )
    }

    /**
     * Direct launch helper with error toast fallback
     */
    fun launchUpiPayment(
        context: Context,
        payeeUpiId: String,
        payeeName: String,
        amount: Double,
        note: String,
        preferredApp: String? = null
    ): Boolean {
        if (payeeUpiId.isBlank()) {
            Toast.makeText(context, "Recipient UPI ID is missing!", Toast.LENGTH_LONG).show()
            return false
        }

        return try {
            val intent = createUpiIntent(payeeUpiId, payeeName, amount, note, preferredApp)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "No UPI App found (Google Pay / PhonePe / Paytm).",
                Toast.LENGTH_LONG
            ).show()
            false
        }
    }

    fun launchUpiUri(
        context: Context,
        upiUriString: String,
        preferredPackage: String? = null
    ): Boolean {
        return try {
            val intent = createUpiIntentFromUri(upiUriString, preferredPackage)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "No UPI App found on device.",
                Toast.LENGTH_LONG
            ).show()
            false
        }
    }
}

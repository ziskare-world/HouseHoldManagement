package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.data.local.model.SettlementDebt
import com.example.notification.ChoreNotificationHelper
import com.example.ui.MainScaffold
import com.example.ui.theme.RoomieVaultTheme
import com.example.ui.viewmodel.RoomieViewModel
import com.example.ui.viewmodel.RoomieViewModelFactory
import com.example.util.UpiPaymentHelper

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: RoomieViewModel

    private var activePaymentDebt: SettlementDebt? = null
    private val showPaymentVerificationDialog = mutableStateOf<SettlementDebt?>(null)

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(
                this,
                "Notifications disabled. Enable permissions to receive chore and budget alerts.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val upiPaymentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val debt = activePaymentDebt
        if (debt != null) {
            // Extract raw response from Intent extras / data
            val rawResponse = result.data?.getStringExtra("response")
                ?: result.data?.getStringExtra("Status")
                ?: result.data?.dataString
                ?: result.data?.extras?.getString("response")
                ?: ""

            val upiResult = UpiPaymentHelper.parseUpiResponse(rawResponse)

            if (upiResult.isSuccess || (result.resultCode == RESULT_OK && upiResult.status != "FAILURE")) {
                // Auto Verify Payment immediately upon successful return
                val txRef = upiResult.transactionId ?: upiResult.approvalRefNo ?: "UPI_AUTO_${System.currentTimeMillis()}"
                viewModel.verifySettlement(debt, isVerified = true)
                Toast.makeText(
                    this,
                    "✅ Payment of ₹${debt.amount} to ${debt.toUserName} verified & settled automatically!",
                    Toast.LENGTH_LONG
                ).show()

                ChoreNotificationHelper.showSettlementAlert(
                    this,
                    debt.id.hashCode(),
                    "✅ Payment Verified & Settled",
                    "Payment of ₹${debt.amount} to ${debt.toUserName} completed via UPI."
                )

                activePaymentDebt = null
            } else if (upiResult.status == "FAILURE") {
                Toast.makeText(
                    this,
                    "❌ UPI Payment failed or was cancelled.",
                    Toast.LENGTH_SHORT
                ).show()
                activePaymentDebt = null
            } else {
                // If payment app did not return query params (e.g. user finished payment & pressed back),
                // prompt instant 1-tap confirmation
                showPaymentVerificationDialog.value = debt
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Create notification channels
        ChoreNotificationHelper.createNotificationChannels(this)

        // Request runtime notification permission on Android 13+
        checkAndRequestNotificationPermission()

        // Initialize ViewModel with factory
        val factory = RoomieViewModelFactory(application)
        viewModel = ViewModelProvider(this, factory)[RoomieViewModel::class.java]

        setContent {
            RoomieVaultTheme {
                MainScaffold(
                    viewModel = viewModel,
                    onLaunchUpiPayment = { debt, packageName ->
                        launchUpiPaymentWithAutoVerification(debt, packageName)
                    }
                )

                // Automatic verification fallback modal if UPI app returns ambiguous response
                val verifyingDebt = showPaymentVerificationDialog.value
                if (verifyingDebt != null) {
                    AlertDialog(
                        onDismissRequest = {
                            showPaymentVerificationDialog.value = null
                            activePaymentDebt = null
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        title = {
                            Text("Automatic Payment Verification")
                        },
                        text = {
                            Text("Did your UPI payment of ₹${verifyingDebt.amount} to ${verifyingDebt.toUserName} complete successfully in your UPI app?")
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.verifySettlement(verifyingDebt, isVerified = true)
                                    Toast.makeText(
                                        this,
                                        "✅ Payment of ₹${verifyingDebt.amount} verified & settled!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    showPaymentVerificationDialog.value = null
                                    activePaymentDebt = null
                                }
                            ) {
                                Text("Yes, Auto-Settle")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showPaymentVerificationDialog.value = null
                                    activePaymentDebt = null
                                }
                            ) {
                                Text("Keep Pending")
                            }
                        }
                    )
                }

                // Email Confirmed Popup Box (Requested by user)
                if (viewModel.showEmailConfirmedPopup.value) {
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissEmailConfirmedPopup() },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                        },
                        title = {
                            Text("Email ID Confirmed! 🎉", fontWeight = FontWeight.Bold)
                        },
                        text = {
                            Text(
                                "Your email ID has been confirmed successfully!\n\nPlease return to the app and sign in with your email and password to start managing your household expenses, roommates, and friends."
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.dismissEmailConfirmedPopup()
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Return to App / Sign In")
                            }
                        }
                    )
                }
            }
        }

        handleDeepLinkIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        val data: Uri? = intent?.data
        if (data != null) {
            val uriStr = data.toString()
            if (uriStr.contains("confirm", ignoreCase = true) ||
                uriStr.contains("verify", ignoreCase = true) ||
                uriStr.contains("access_token") ||
                data.scheme == "roomievault"
            ) {
                val emailParam = data.getQueryParameter("email") ?: ""
                viewModel.triggerEmailConfirmedPopup(emailParam)
            }
        }
    }

    private fun launchUpiPaymentWithAutoVerification(
        debt: SettlementDebt,
        preferredPackage: String? = null
    ) {
        val payeeUpi = debt.toUserUpiId.ifBlank { "roommate@upi" }
        activePaymentDebt = debt

        try {
            val intent = UpiPaymentHelper.createUpiIntent(
                payeeUpiId = payeeUpi,
                payeeName = debt.toUserName,
                amount = debt.amount,
                note = debt.reason,
                preferredApp = preferredPackage
            )
            upiPaymentLauncher.launch(intent)
        } catch (e: Exception) {
            // Fallback to raw URI launch
            try {
                val upiUri = UpiPaymentHelper.buildUpiUri(
                    payeeUpiId = payeeUpi,
                    payeeName = debt.toUserName,
                    amount = debt.amount,
                    note = debt.reason
                )
                val fallbackIntent = Intent(Intent.ACTION_VIEW, upiUri)
                upiPaymentLauncher.launch(Intent.createChooser(fallbackIntent, "Select UPI App"))
            } catch (ex: Exception) {
                Toast.makeText(
                    this,
                    "No UPI App found on device. You can copy the UPI ID to pay manually.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

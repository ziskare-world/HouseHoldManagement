package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.data.local.AppDatabase
import com.example.data.repository.RoomieRepository
import com.example.notification.ChoreNotificationHelper
import com.example.ui.MainScaffold
import com.example.ui.theme.RoomieVaultTheme
import com.example.ui.viewmodel.RoomieViewModel
import com.example.ui.viewmodel.RoomieViewModelFactory
import com.example.util.UpiPaymentHelper

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: RoomieViewModel

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
                    onLaunchUpiPayment = { upiUri, packageName ->
                        UpiPaymentHelper.launchUpiUri(
                            context = this,
                            upiUriString = upiUri,
                            preferredPackage = packageName
                        )
                    }
                )
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

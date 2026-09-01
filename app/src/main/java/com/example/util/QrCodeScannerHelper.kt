package com.example.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.util.concurrent.Executors

object QrCodeScannerHelper {

    /**
     * Parses UPI QR code or raw UPI ID.
     * Returns Pair(upiId, payeeName) or null.
     */
    fun parseUpiQr(rawText: String): Pair<String, String>? {
        if (rawText.isBlank()) return null
        val trimmed = rawText.trim()

        if (trimmed.startsWith("upi://pay", ignoreCase = true)) {
            try {
                val uri = Uri.parse(trimmed)
                val upiId = uri.getQueryParameter("pa") ?: ""
                val rawName = uri.getQueryParameter("pn") ?: ""
                val name = try {
                    URLDecoder.decode(rawName, "UTF-8")
                } catch (e: Exception) {
                    rawName
                }
                if (upiId.isNotBlank()) {
                    return Pair(upiId, name)
                }
            } catch (e: Exception) {
                // fallback to regex query
            }
        }

        // Try extracting pa= and pn= via regex
        val paRegex = Regex("[?&]pa=([^&]+)", RegexOption.IGNORE_CASE)
        val pnRegex = Regex("[?&]pn=([^&]+)", RegexOption.IGNORE_CASE)
        val paMatch = paRegex.find(trimmed)?.groupValues?.getOrNull(1)
        val pnMatch = pnRegex.find(trimmed)?.groupValues?.getOrNull(1)

        if (paMatch != null) {
            val name = try {
                URLDecoder.decode(pnMatch ?: "", "UTF-8")
            } catch (e: Exception) {
                pnMatch ?: ""
            }
            return Pair(paMatch, name)
        }

        // If it looks like a valid UPI ID (e.g. user@oksbi, 9876543210@paytm)
        if (trimmed.contains("@") && !trimmed.contains(" ") && trimmed.length in 5..50) {
            return Pair(trimmed, "")
        }

        return null
    }

    /**
     * Decodes a QR code from a Bitmap (e.g. image from gallery).
     */
    fun decodeQrFromBitmap(bitmap: Bitmap): String? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val result = MultiFormatReader().decode(binaryBitmap)
            result.text
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Decodes a QR code from a gallery Uri.
     */
    fun decodeQrFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream) ?: return null
                decodeQrFromBitmap(bitmap)
            }
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * High-performance full-screen / popup Camera QR Scanner with gallery picker fallback.
 */
@Composable
fun QrCameraScannerDialog(
    onDismiss: () -> Unit,
    onQrScanned: (upiId: String, payeeName: String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    var scanErrorMessage by remember { mutableStateOf<String?>(null) }
    var isScanned by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            scanErrorMessage = "Camera permission is required to scan QR code"
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val qrText = QrCodeScannerHelper.decodeQrFromUri(context, uri)
            if (qrText != null) {
                val parsed = QrCodeScannerHelper.parseUpiQr(qrText)
                if (parsed != null) {
                    onQrScanned(parsed.first, parsed.second)
                    onDismiss()
                } else {
                    scanErrorMessage = "QR found ($qrText), but not a valid UPI code"
                }
            } else {
                scanErrorMessage = "Could not detect a QR code in this image"
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (hasCameraPermission) {
                val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                if (!isScanned) {
                                    val qrText = processImageProxy(imageProxy)
                                    if (qrText != null) {
                                        val parsed = QrCodeScannerHelper.parseUpiQr(qrText)
                                        if (parsed != null) {
                                            isScanned = true
                                            (context as? android.app.Activity)?.runOnUiThread {
                                                onQrScanned(parsed.first, parsed.second)
                                                onDismiss()
                                            }
                                        }
                                    }
                                }
                                imageProxy.close()
                            }

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                DisposableEffect(Unit) {
                    onDispose {
                        cameraExecutor.shutdown()
                    }
                }
            }

            // Scanner Overlay UI
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .size(44.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "Scan UPI QR Code",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    IconButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .size(44.dp)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = "Upload from Gallery", tint = Color.White)
                    }
                }

                // Center Scanning Square
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(260.dp)
                            .border(3.dp, Color(0xFF10B981), RoundedCornerShape(24.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.size(90.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Point camera at any UPI QR code\n(Google Pay, PhonePe, Paytm, BHIM)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center
                    )

                    if (scanErrorMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
                        ) {
                            Text(
                                text = scanErrorMessage ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Bottom Action Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    FilledTonalButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Upload QR from Photos")
                    }
                }
            }
        }
    }
}

/**
 * Decodes QR from CameraX ImageProxy using ZXing PlanarYUVLuminanceSource.
 */
private fun processImageProxy(image: ImageProxy): String? {
    if (image.format != ImageFormat.YUV_420_888 && image.format != ImageFormat.YUV_422_888 && image.format != ImageFormat.YUV_444_888) {
        return null
    }

    val buffer: ByteBuffer = image.planes[0].buffer
    val data = ByteArray(buffer.remaining())
    buffer.get(data)

    val width = image.width
    val height = image.height

    val source = PlanarYUVLuminanceSource(
        data,
        width,
        height,
        0,
        0,
        width,
        height,
        false
    )

    val bitmap = BinaryBitmap(HybridBinarizer(source))
    return try {
        MultiFormatReader().decode(bitmap)?.text
    } catch (e: NotFoundException) {
        null
    } catch (e: Exception) {
        null
    }
}

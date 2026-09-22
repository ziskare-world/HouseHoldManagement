package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MintSuccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onSignIn: (email: String, password: String, onResult: (Boolean, String) -> Unit) -> Unit,
    onSignUp: (
        email: String,
        password: String,
        fullName: String,
        upiId: String,
        householdCode: String,
        householdName: String,
        onResult: (Boolean, String) -> Unit
    ) -> Unit,
    onForgotPassword: ((email: String, onResult: (Boolean, String) -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Sign In, 1 = Sign Up
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var upiId by remember { mutableStateOf("") }
    var householdCode by remember { mutableStateOf("") }
    var householdName by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var loadingStatusText by remember { mutableStateOf("Connecting to Cloud...") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    var showForgotPasswordDialog by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    val headerGradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            MaterialTheme.colorScheme.background
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(headerGradient)
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // App Brand Icon & Title
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                Color(0xFF10B981)
                            )
                        )
                    )
                    .shadow(16.dp, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "RoomieVault Logo",
                    tint = Color.White,
                    modifier = Modifier.size(42.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "RoomieVault",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Smart Household Expenses & Chores Manager",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Main Auth Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, shape = RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Segmented Tab Switcher (Sign In vs Create Account)
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.primary,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = MaterialTheme.colorScheme.primary,
                                height = 3.dp
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = {
                                selectedTab = 0
                                errorMessage = null
                                successMessage = null
                            },
                            text = {
                                Text(
                                    text = "Sign In",
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 15.sp
                                )
                            }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = {
                                selectedTab = 1
                                errorMessage = null
                                successMessage = null
                            },
                            text = {
                                Text(
                                    text = "Create Account",
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 15.sp
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Error Message Alert
                    AnimatedVisibility(
                        visible = errorMessage != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Text(
                                text = errorMessage ?: "",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // Success Message Alert
                    AnimatedVisibility(
                        visible = successMessage != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MintSuccess.copy(alpha = 0.15f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Text(
                                text = successMessage ?: "",
                                color = Color(0xFF0F5132),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    if (selectedTab == 0) {
                        // --- SIGN IN FORM ---
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email Address") },
                            placeholder = { Text("roomie@example.com") },
                            leadingIcon = {
                                Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle password"
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            singleLine = true
                        )

                        // Forgot Password Link
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { showForgotPasswordDialog = true },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = "Forgot Password?",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                if (email.isBlank() || password.isBlank()) {
                                    errorMessage = "Please enter both email and password."
                                    return@Button
                                }
                                errorMessage = null
                                loadingStatusText = "Verifying credentials & signing in..."
                                isLoading = true
                                onSignIn(email.trim(), password) { success, msg ->
                                    isLoading = false
                                    if (!success) {
                                        errorMessage = msg
                                    } else {
                                        successMessage = "Signed in successfully! Loading Dashboard..."
                                    }
                                }
                            },
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = "Sign In & Enter",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        // --- CREATE ACCOUNT FORM ---
                        OutlinedTextField(
                            value = fullName,
                            onValueChange = { fullName = it },
                            label = { Text("Your Full Name") },
                            placeholder = { Text("e.g. Alex Sharma") },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email Address") },
                            placeholder = { Text("alex@example.com") },
                            leadingIcon = {
                                Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Create Password") },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle password"
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Next
                            ),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = upiId,
                            onValueChange = { upiId = it },
                            label = { Text("Your UPI ID (For Settlements)") },
                            placeholder = { Text("alex@okhdfcbank") },
                            leadingIcon = {
                                Icon(Icons.Default.Payment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = householdName,
                            onValueChange = { householdName = it },
                            label = { Text("Household / Apartment Name") },
                            placeholder = { Text("e.g. Skyline Residency, Flat 302") },
                            leadingIcon = {
                                Icon(Icons.Default.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = householdCode,
                            onValueChange = { householdCode = it.uppercase() },
                            label = { Text("Shared Invite Code") },
                            placeholder = { Text("e.g. SKY302 or MYHOME") },
                            leadingIcon = {
                                Icon(Icons.Default.QrCode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                if (fullName.isBlank() || email.isBlank() || password.isBlank()) {
                                    errorMessage = "Please fill in Name, Email and Password."
                                    return@Button
                                }
                                if (password.length < 6) {
                                    errorMessage = "Password must be at least 6 characters."
                                    return@Button
                                }
                                errorMessage = null
                                loadingStatusText = "Creating your account & setting up ledger..."
                                isLoading = true
                                val finalCode = householdCode.trim().ifBlank { "HOME" + (100..999).random() }
                                val finalName = householdName.trim().ifBlank { "${fullName.trim()}'s Household" }
                                onSignUp(
                                    email.trim(),
                                    password,
                                    fullName.trim(),
                                    upiId.trim(),
                                    finalCode,
                                    finalName
                                ) { success, msg ->
                                    isLoading = false
                                    if (!success) {
                                        errorMessage = msg
                                    } else {
                                        successMessage = "Account created successfully! Loading Dashboard..."
                                    }
                                }
                            },
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = "Create Account & Enter",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // --- BEAUTIFUL ANIMATED FULLSCREEN LOADER OVERLAY ---
        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(animationSpec = tween(250)) + scaleIn(initialScale = 0.95f),
            exit = fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 0.95f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "LoaderAnimation")
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "Rotation"
                )
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 0.92f,
                    targetValue = 1.08f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "Pulse"
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .shadow(24.dp, shape = RoundedCornerShape(28.dp)),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Glowing Pulsing Spinner Ring
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .scale(pulseScale),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(84.dp)
                                    .rotate(rotation)
                                    .border(
                                        width = 4.dp,
                                        brush = Brush.sweepGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary,
                                                Color(0xFF10B981),
                                                Color.Transparent
                                            )
                                        ),
                                        shape = CircleShape
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary,
                                                Color(0xFF10B981)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "RoomieVault",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = loadingStatusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Please wait a moment...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // --- FORGOT PASSWORD DIALOG ---
        if (showForgotPasswordDialog) {
            var resetEmail by remember { mutableStateOf(email) }
            var isSendingReset by remember { mutableStateOf(false) }
            var resetStatusMsg by remember { mutableStateOf<String?>(null) }
            var isResetSuccess by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = {
                    if (!isSendingReset) showForgotPasswordDialog = false
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.LockReset,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text("Reset Your Password", fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Enter your registered email address to receive a secure password reset link via Supabase Auth.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = resetEmail,
                            onValueChange = { resetEmail = it },
                            label = { Text("Email Address") },
                            placeholder = { Text("roomie@example.com") },
                            leadingIcon = {
                                Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        if (resetStatusMsg != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isResetSuccess) MintSuccess.copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = resetStatusMsg ?: "",
                                    color = if (isResetSuccess) Color(0xFF0F5132) else MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isResetSuccess) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (resetEmail.isBlank()) {
                                resetStatusMsg = "Please enter your email."
                                isResetSuccess = false
                                return@Button
                            }
                            isSendingReset = true
                            resetStatusMsg = null
                            onForgotPassword?.invoke(resetEmail.trim()) { success, msg ->
                                isSendingReset = false
                                isResetSuccess = success
                                resetStatusMsg = msg
                            }
                        },
                        enabled = !isSendingReset,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSendingReset) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (isResetSuccess) "Sent!" else "Send Reset Link")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showForgotPasswordDialog = false },
                        enabled = !isSendingReset
                    ) {
                        Text(if (isResetSuccess) "Done" else "Cancel")
                    }
                }
            )
        }
    }
}

package com.example.data.remote

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class SupabaseUser(
    val id: String,
    val email: String,
    val fullName: String = "",
    val upiId: String = "",
    val householdId: String = "HOUSE_DEFAULT",
    val householdName: String = "My Household",
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresAt: Long = 0L
)

sealed class SupabaseAuthState {
    object LoggedOut : SupabaseAuthState()
    object Loading : SupabaseAuthState()
    data class Authenticated(val user: SupabaseUser) : SupabaseAuthState()
    data class Error(val message: String) : SupabaseAuthState()
}

class SupabaseAuthManager(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("supabase_auth_prefs", Context.MODE_PRIVATE)

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val _authState = MutableStateFlow<SupabaseAuthState>(SupabaseAuthState.LoggedOut)
    val authState: StateFlow<SupabaseAuthState> = _authState.asStateFlow()

    init {
        loadSavedSession()
    }

    val isUserLoggedIn: Boolean
        get() = prefs.getBoolean("is_logged_in", false) && !accessToken.isNullOrBlank()

    val accessToken: String?
        get() = prefs.getString("access_token", null)

    val currentUserId: String?
        get() = prefs.getString("user_id", null)

    val currentUserEmail: String?
        get() = prefs.getString("user_email", null)

    val currentUserName: String?
        get() = prefs.getString("user_name", null)

    val currentUserUpi: String?
        get() = prefs.getString("user_upi", null)

    val currentHouseholdId: String?
        get() = prefs.getString("household_id", "HOUSE_FLAT_402")

    private fun loadSavedSession() {
        val token = prefs.getString("access_token", null)
        val userId = prefs.getString("user_id", null)
        val email = prefs.getString("user_email", null)
        val name = prefs.getString("user_name", "") ?: ""
        val upi = prefs.getString("user_upi", "") ?: ""
        val householdId = prefs.getString("household_id", "HOUSE_DEFAULT") ?: "HOUSE_DEFAULT"
        val householdName = prefs.getString("household_name", "My Household") ?: "My Household"
        val refreshToken = prefs.getString("refresh_token", "") ?: ""
        val expiresAt = prefs.getLong("expires_at", 0L)

        if (!token.isNullOrBlank() && !userId.isNullOrBlank() && !email.isNullOrBlank()) {
            val user = SupabaseUser(
                id = userId,
                email = email,
                fullName = name,
                upiId = upi,
                householdId = householdId,
                householdName = householdName,
                accessToken = token,
                refreshToken = refreshToken,
                expiresAt = expiresAt
            )
            _authState.value = SupabaseAuthState.Authenticated(user)
        } else {
            _authState.value = SupabaseAuthState.LoggedOut
        }
    }

    /**
     * Sign Up with Email & Password via Supabase Auth API
     */
    suspend fun signUp(
        baseUrl: String,
        anonKey: String,
        email: String,
        password: String,
        fullName: String,
        upiId: String,
        householdCode: String,
        householdName: String
    ): Result<SupabaseUser> = withContext(Dispatchers.IO) {
        _authState.value = SupabaseAuthState.Loading
        try {
            val endpoint = "${baseUrl.removeSuffix("/")}/auth/v1/signup"

            val userMetadata = JSONObject().apply {
                put("full_name", fullName)
                put("upi_id", upiId)
                put("household_id", "HOUSE_${householdCode.uppercase().trim().ifBlank { "FLAT402" }}")
                put("household_name", householdName.ifBlank { "Household ${householdCode.uppercase()}" })
            }

            val requestBodyJson = JSONObject().apply {
                put("email", email.trim())
                put("password", password)
                put("data", userMetadata)
            }

            val body = requestBodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                if (response.isSuccessful || response.code == 200 || response.code == 201) {
                    val json = JSONObject(responseStr)
                    val accessToken = json.optString("access_token", "")
                    val refreshToken = json.optString("refresh_token", "")
                    val expiresIn = json.optLong("expires_in", 3600L)
                    val expiresAt = System.currentTimeMillis() + (expiresIn * 1000)

                    val userObj = json.optJSONObject("user") ?: json
                    val userId = userObj.optString("id", "USR_${System.currentTimeMillis()}")
                    val userEmail = userObj.optString("email", email)

                    val metadata = userObj.optJSONObject("user_metadata")
                    val parsedName = metadata?.optString("full_name", fullName) ?: fullName
                    val parsedUpi = metadata?.optString("upi_id", upiId) ?: upiId
                    val parsedHouseholdId = metadata?.optString("household_id", "HOUSE_${householdCode.uppercase().trim()}") ?: "HOUSE_FLAT402"
                    val parsedHouseholdName = metadata?.optString("household_name", householdName) ?: householdName

                    val tokenToSave = if (accessToken.isNotBlank()) accessToken else anonKey

                    saveUserSession(
                        userId = userId,
                        email = userEmail,
                        name = parsedName,
                        upi = parsedUpi,
                        householdId = parsedHouseholdId,
                        householdName = parsedHouseholdName,
                        accessToken = tokenToSave,
                        refreshToken = refreshToken,
                        expiresAt = expiresAt
                    )

                    val user = SupabaseUser(
                        id = userId,
                        email = userEmail,
                        fullName = parsedName,
                        upiId = parsedUpi,
                        householdId = parsedHouseholdId,
                        householdName = parsedHouseholdName,
                        accessToken = tokenToSave,
                        refreshToken = refreshToken,
                        expiresAt = expiresAt
                    )
                    _authState.value = SupabaseAuthState.Authenticated(user)
                    Result.success(user)
                } else {
                    val errorMsg = parseErrorMessage(responseStr, "Sign up failed (${response.code})")
                    _authState.value = SupabaseAuthState.Error(errorMsg)
                    Result.failure(Exception(errorMsg))
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseAuth", "Sign up exception", e)
            val msg = formatExceptionMessage(e, "Sign up failed")
            _authState.value = SupabaseAuthState.Error(msg)
            Result.failure(Exception(msg))
        }
    }

    /**
     * Sign In with Email & Password via Supabase Auth API
     */
    suspend fun signIn(
        baseUrl: String,
        anonKey: String,
        email: String,
        password: String
    ): Result<SupabaseUser> = withContext(Dispatchers.IO) {
        _authState.value = SupabaseAuthState.Loading
        try {
            val endpoint = "${baseUrl.removeSuffix("/")}/auth/v1/token?grant_type=password"

            val requestBodyJson = JSONObject().apply {
                put("email", email.trim())
                put("password", password)
            }

            val body = requestBodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                if (response.isSuccessful || response.code == 200) {
                    val json = JSONObject(responseStr)
                    val accessToken = json.optString("access_token", "")
                    val refreshToken = json.optString("refresh_token", "")
                    val expiresIn = json.optLong("expires_in", 3600L)
                    val expiresAt = System.currentTimeMillis() + (expiresIn * 1000)

                    val userObj = json.optJSONObject("user") ?: JSONObject()
                    val userId = userObj.optString("id", "USR_${System.currentTimeMillis()}")
                    val userEmail = userObj.optString("email", email)

                    val metadata = userObj.optJSONObject("user_metadata")
                    val name = metadata?.optString("full_name", userEmail.substringBefore("@")) ?: userEmail.substringBefore("@")
                    val upi = metadata?.optString("upi_id", "") ?: ""
                    val householdId = metadata?.optString("household_id", "HOUSE_DEFAULT") ?: "HOUSE_DEFAULT"
                    val householdName = metadata?.optString("household_name", "My Household") ?: "My Household"

                    saveUserSession(
                        userId = userId,
                        email = userEmail,
                        name = name,
                        upi = upi,
                        householdId = householdId,
                        householdName = householdName,
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        expiresAt = expiresAt
                    )

                    val user = SupabaseUser(
                        id = userId,
                        email = userEmail,
                        fullName = name,
                        upiId = upi,
                        householdId = householdId,
                        householdName = householdName,
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        expiresAt = expiresAt
                    )
                    _authState.value = SupabaseAuthState.Authenticated(user)
                    Result.success(user)
                } else {
                    val errorMsg = parseErrorMessage(responseStr, "Invalid email or password (${response.code})")
                    _authState.value = SupabaseAuthState.Error(errorMsg)
                    Result.failure(Exception(errorMsg))
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseAuth", "Sign in exception", e)
            val msg = formatExceptionMessage(e, "Failed to connect to Supabase")
            _authState.value = SupabaseAuthState.Error(msg)
            Result.failure(Exception(msg))
        }
    }

    /**
     * Send password recovery email via Supabase Auth
     */
    suspend fun resetPassword(
        baseUrl: String,
        anonKey: String,
        email: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "${baseUrl.removeSuffix("/")}/auth/v1/recover"
            val requestBodyJson = JSONObject().apply {
                put("email", email.trim())
            }

            val body = requestBodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code in 200..299) {
                    Result.success("Password reset email sent to $email. Please check your inbox.")
                } else {
                    val res = response.body?.string() ?: ""
                    val msg = parseErrorMessage(res, "Failed to send reset email")
                    Result.failure(Exception(msg))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sign out user and clear session
     */
    suspend fun signOut(
        baseUrl: String,
        anonKey: String
    ) = withContext(Dispatchers.IO) {
        try {
            val token = accessToken
            if (!token.isNullOrBlank()) {
                val endpoint = "${baseUrl.removeSuffix("/")}/auth/v1/logout"
                val body = "{}".toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $token")
                    .post(body)
                    .build()
                client.newCall(request).execute().close()
            }
        } catch (e: Exception) {
            Log.d("SupabaseAuth", "Logout cleanup: ${e.localizedMessage}")
        } finally {
            clearSession()
        }
    }

    private fun saveUserSession(
        userId: String,
        email: String,
        name: String,
        upi: String,
        householdId: String,
        householdName: String,
        accessToken: String,
        refreshToken: String,
        expiresAt: Long
    ) {
        prefs.edit()
            .putBoolean("is_logged_in", true)
            .putString("user_id", userId)
            .putString("user_email", email)
            .putString("user_name", name)
            .putString("user_upi", upi)
            .putString("household_id", householdId)
            .putString("household_name", householdName)
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .putLong("expires_at", expiresAt)
            .apply()
    }

    fun clearSession() {
        prefs.edit()
            .remove("is_logged_in")
            .remove("user_id")
            .remove("user_email")
            .remove("user_name")
            .remove("user_upi")
            .remove("access_token")
            .remove("refresh_token")
            .remove("expires_at")
            .apply()
        _authState.value = SupabaseAuthState.LoggedOut
    }

    private fun parseErrorMessage(jsonStr: String, fallback: String): String {
        return try {
            val obj = JSONObject(jsonStr)
            obj.optString("error_description",
                obj.optString("msg",
                    obj.optString("message", fallback)
                )
            )
        } catch (e: Exception) {
            fallback
        }
    }

    private fun formatExceptionMessage(e: Exception, defaultMsg: String): String {
        val msg = e.localizedMessage.orEmpty()
        return when {
            e is java.net.UnknownHostException || msg.contains("Unable to resolve host", ignoreCase = true) || msg.contains("No address associated", ignoreCase = true) ->
                "Cloud server unreachable. Please check your internet connection or verify the server status."
            e is java.net.SocketTimeoutException ->
                "Cloud connection timed out. Please check your internet connection and try again."
            msg.isNotBlank() -> msg
            else -> defaultMsg
        }
    }
}

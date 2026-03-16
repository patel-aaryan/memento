package com.example.mementoandroid

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.foundation.Image
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.mementoandroid.api.BackendClient
import com.example.mementoandroid.ui.theme.MementoAndroidTheme
import com.example.mementoandroid.util.DarkModeStore
import com.example.mementoandroid.util.AuthTokenStore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuthTokenStore.init(applicationContext)
        DarkModeStore.init(applicationContext)
        if (AuthTokenStore.get() != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        setContent {
            MementoAndroidTheme(darkTheme = DarkModeStore.get(applicationContext)) {
                LoginScreen(
                    onLoginSuccess = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                    onSignUp = {
                        startActivity(Intent(this, RegisterActivity::class.java))
                    }
                )
            }
        }
    }
}

private suspend fun getFcmToken(): String? = suspendCancellableCoroutine { cont ->
    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
        if (!cont.isCompleted) {
            cont.resume(if (task.isSuccessful) task.result else null)
        }
    }
}

private suspend fun loginToBackend(email: String, password: String, fcmToken: String? = null): Result<String> {
    val body = JSONObject().apply {
        put("email", email)
        put("password", password)
        if (fcmToken != null) put("fcm_token", fcmToken)
    }
    return BackendClient.post(
        "/auth/login",
        body,
        errorMessageFallback = { "Login failed (HTTP $it)" }
    ).map { it.getString("access_token") }
}

private suspend fun requestPasswordResetCode(email: String): Result<Unit> {
    val body = JSONObject().apply {
        put("email", email)
    }
    return BackendClient.post(
        "/auth/forgot-password",
        body,
        errorMessageFallback = { "Could not request password reset (HTTP $it)" }
    ).map { }
}

private suspend fun resetPasswordWithCode(email: String, code: String, newPassword: String): Result<String> {
    val body = JSONObject().apply {
        put("email", email)
        put("code", code)
        put("new_password", newPassword)
    }
    return BackendClient.post(
        "/auth/reset-password",
        body,
        errorMessageFallback = { "Password reset failed (HTTP $it)" }
    ).map { response ->
        response.optString("message", "Password updated successfully")
    }
}

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onSignUp: () -> Unit = {}
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showForgotDialog by remember { mutableStateOf(false) }
    var forgotStep by remember { mutableStateOf(1) } // 1=request code, 2=submit code + new password
    var forgotEmail by remember { mutableStateOf("") }
    var resetCode by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }
    var forgotMessage by remember { mutableStateOf<String?>(null) }
    var forgotLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.memento_logo),
                contentDescription = "Memento",
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .heightIn(max = 300.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
//                    if (email=="alex"){onLoginSuccess()}
                    if (email.isBlank() || password.isBlank() || isLoading) {
                        return@Button
                    }
                    errorMessage = null
                    isLoading = true
                    coroutineScope.launch {
                        val fcmToken = getFcmToken()
                        val result = loginToBackend(email.trim(), password, fcmToken)
                        isLoading = false
                        result
                            .onSuccess { accessToken ->
                                AuthTokenStore.save(accessToken)
                                onLoginSuccess()
                            }
                            .onFailure { error ->
                                errorMessage = error.message ?: "Login failed"
                            }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Login")
                }
            }

            errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = {
                    forgotEmail = email.trim()
                    resetCode = ""
                    newPassword = ""
                    confirmNewPassword = ""
                    forgotMessage = null
                    forgotStep = 1
                    showForgotDialog = true
                }
            ) {
                Text("Forgot password?")
            }

            TextButton(onClick = onSignUp) {
                Text("Sign up")
            }
        }
    }

    if (showForgotDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!forgotLoading) showForgotDialog = false
            },
            title = {
                Text(if (forgotStep == 1) "Reset password" else "Enter reset code")
            },
            text = {
                Column {
                    if (forgotStep == 1) {
                        Text(
                            text = "Enter your account email and we'll send you a reset code."
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = forgotEmail,
                            onValueChange = { forgotEmail = it },
                            label = { Text("Email") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = "Enter the code from your email, then set a new password."
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = forgotEmail,
                            onValueChange = { forgotEmail = it },
                            label = { Text("Email") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = resetCode,
                            onValueChange = { resetCode = it },
                            label = { Text("Reset code") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = { Text("New password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = confirmNewPassword,
                            onValueChange = { confirmNewPassword = it },
                            label = { Text("Confirm new password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    forgotMessage?.let { message ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = message,
                            color = if (message.contains("sent", ignoreCase = true) || message.contains("success", ignoreCase = true)) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !forgotLoading,
                    onClick = {
                        if (forgotStep == 1) {
                            if (forgotEmail.isBlank()) {
                                forgotMessage = "Please enter your email."
                                return@Button
                            }
                            forgotLoading = true
                            forgotMessage = null
                            coroutineScope.launch {
                                val result = requestPasswordResetCode(forgotEmail.trim())
                                forgotLoading = false
                                result
                                    .onSuccess {
                                        forgotStep = 2
                                        forgotMessage = "Code sent. Check your email."
                                    }
                                    .onFailure { error ->
                                        forgotMessage = error.message ?: "Failed to send reset code."
                                    }
                            }
                        } else {
                            if (forgotEmail.isBlank() || resetCode.isBlank() || newPassword.isBlank() || confirmNewPassword.isBlank()) {
                                forgotMessage = "Please fill all fields."
                                return@Button
                            }
                            if (newPassword != confirmNewPassword) {
                                forgotMessage = "Passwords do not match."
                                return@Button
                            }
                            if (newPassword.length < 8) {
                                forgotMessage = "Password must be at least 8 characters."
                                return@Button
                            }
                            forgotLoading = true
                            forgotMessage = null
                            coroutineScope.launch {
                                val result = resetPasswordWithCode(
                                    forgotEmail.trim(),
                                    resetCode.trim(),
                                    newPassword
                                )
                                forgotLoading = false
                                result
                                    .onSuccess {
                                        forgotMessage = "Password reset successful. You can now log in."
                                        resetCode = ""
                                        newPassword = ""
                                        confirmNewPassword = ""
                                        showForgotDialog = false
                                    }
                                    .onFailure { error ->
                                        forgotMessage = error.message ?: "Failed to reset password."
                                    }
                            }
                        }
                    }
                ) {
                    if (forgotLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(if (forgotStep == 1) "Send code" else "Reset password")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !forgotLoading,
                    onClick = {
                        showForgotDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.mementoandroid.api.BackendClient
import com.example.mementoandroid.ui.theme.MementoAndroidTheme
import com.example.mementoandroid.util.AuthTokenStore
import kotlinx.coroutines.launch
import org.json.JSONObject

class RegisterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuthTokenStore.init(applicationContext)
        setContent {
            MementoAndroidTheme {
                RegisterScreen(
                    onRegisterSuccess = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                    onBackToLogin = {
                        finish()
                    }
                )
            }
        }
    }
}

private suspend fun loginToBackend(email: String, password: String): Result<String> {
    val body = JSONObject().apply {
        put("email", email)
        put("password", password)
    }
    return BackendClient.post(
        "/auth/login",
        body,
        errorMessageFallback = { "Login failed (HTTP $it)" }
    ).map { it.getString("access_token") }
}

private suspend fun registerToBackend(name: String, email: String, password: String): Result<Unit> {
    val body = JSONObject().apply {
        put("name", name)
        put("email", email)
        put("password", password)
    }
    return BackendClient.post(
        "/auth/register",
        body,
        errorMessageFallback = { "Registration failed (HTTP $it)" }
    ).map { }
}

@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onBackToLogin: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Sign up",
                style = MaterialTheme.typography.headlineLarge
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

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
                    if (name.isBlank() || email.isBlank() || password.isBlank() || isLoading) return@Button
                    errorMessage = null
                    isLoading = true
                    coroutineScope.launch {
                        val registerResult = registerToBackend(name.trim(), email.trim(), password)
                        registerResult
                            .onSuccess {
                                val loginResult = loginToBackend(email.trim(), password)
                                isLoading = false
                                loginResult
                                    .onSuccess { accessToken ->
                                        AuthTokenStore.save(accessToken)
                                        onRegisterSuccess()
                                    }
                                    .onFailure { errorMessage = it.message ?: "Account created. Please log in." }
                            }
                            .onFailure {
                                isLoading = false
                                errorMessage = it.message ?: "Registration failed"
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
                    Text("Register")
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

            TextButton(onClick = onBackToLogin) {
                Text("Back to login")
            }
        }
    }
}

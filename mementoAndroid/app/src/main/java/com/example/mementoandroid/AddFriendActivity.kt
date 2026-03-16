package com.example.mementoandroid

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mementoandroid.api.BackendClient
import com.example.mementoandroid.api.BackendException
import com.example.mementoandroid.ui.theme.MementoAndroidTheme
import com.example.mementoandroid.util.DarkModeStore
import com.example.mementoandroid.util.AuthTokenStore
import com.example.mementoandroid.util.PendingFriendTokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Handles friend invite deep links (memento://add_friend?token=...).
 * When opened from a friend link, calls the backend to add the inviter as a friend.
 */
class AddFriendActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuthTokenStore.init(applicationContext)
        DarkModeStore.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            MementoAndroidTheme(darkTheme = DarkModeStore.get(applicationContext)) {
                var status by remember { mutableStateOf<AddFriendStatus>(AddFriendStatus.Loading) }
                val token = intent?.data?.getQueryParameter("token")

                LaunchedEffect(token) {
                    if (token.isNullOrBlank()) {
                        status = AddFriendStatus.Error("Invalid link")
                        return@LaunchedEffect
                    }
                    val authToken = AuthTokenStore.get()
                    if (authToken == null) {
                        PendingFriendTokenStore.save(applicationContext, token)
                        startActivity(Intent(this@AddFriendActivity, LoginActivity::class.java))
                        finish()
                        return@LaunchedEffect
                    }
                    val body = JSONObject().put("token", token)
                    val result = BackendClient.post("/friends/add_friend_by_link", body, token = authToken)
                    withContext(Dispatchers.Main) {
                        result.fold(
                            onSuccess = {
                                status = AddFriendStatus.Success
                                Toast.makeText(this@AddFriendActivity, "Friend added!", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this@AddFriendActivity, MainActivity::class.java))
                                finish()
                            },
                            onFailure = { e ->
                                when {
                                    e is BackendException && e.statusCode == 401 -> {
                                        AuthTokenStore.clear()
                                        startActivity(Intent(this@AddFriendActivity, LoginActivity::class.java))
                                        finish()
                                    }
                                    else -> status = AddFriendStatus.Error(e.message ?: "Failed to add friend")
                                }
                            }
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (val currentStatus = status) {
                        AddFriendStatus.Loading -> {
                            CircularProgressIndicator()
                            Text("Adding friend...", modifier = Modifier.padding(top = 16.dp))
                        }
                        AddFriendStatus.Success -> {
                            Text("Friend added!")
                        }
                        is AddFriendStatus.Error -> {
                            Text(currentStatus.message)
                        }
                    }
                }
            }
        }
    }

    private sealed class AddFriendStatus {
        data object Loading : AddFriendStatus()
        data object Success : AddFriendStatus()
        data class Error(val message: String) : AddFriendStatus()
    }
}

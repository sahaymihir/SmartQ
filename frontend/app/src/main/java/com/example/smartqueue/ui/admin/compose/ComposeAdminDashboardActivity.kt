package com.example.smartqueue.ui.admin.compose

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.smartqueue.network.ApiClient
import com.example.smartqueue.network.ApiService
import com.example.smartqueue.ui.auth.LoginActivity
import com.example.smartqueue.ui.theme.SmartQTheme
import com.example.smartqueue.utils.SessionManager
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * Compose-based Admin Dashboard Activity
 * Material 3 with heavy animations, minimal modern design
 */
class ComposeAdminDashboardActivity : ComponentActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var apiService: ApiService
    private lateinit var doctorId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)
        apiService = ApiClient.getInstance().create(ApiService::class.java)
        doctorId = sessionManager.userId.orEmpty()

        setContent {
            var adminName by remember { mutableStateOf(sessionManager.getName()) }
            var isLoading by remember { mutableStateOf(false) }

            SmartQTheme {
                AdminDashboardScreen(
                    adminName = adminName,
                    onLogout = {
                        sessionManager.clearSession()
                        startActivity(Intent(this@ComposeAdminDashboardActivity, LoginActivity::class.java))
                        finish()
                    },
                    onCallNext = {
                        callNextPatient()
                    },
                    onPauseToggle = {
                        togglePause()
                    },
                    onRefreshML = {
                        // Trigger ML Ops refresh
                    },
                )
            }
        }
    }

    private fun callNextPatient() {
        apiService.callNextPatient(doctorId).enqueue(object : Callback<com.example.smartqueue.models.response.MessageResponse> {
            override fun onResponse(
                call: Call<com.example.smartqueue.models.response.MessageResponse>,
                response: Response<com.example.smartqueue.models.response.MessageResponse>,
            ) {
                if (response.isSuccessful && response.body() != null) {
                    Toast.makeText(
                        this@ComposeAdminDashboardActivity,
                        response.body()?.message,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }

            override fun onFailure(call: Call<com.example.smartqueue.models.response.MessageResponse>, t: Throwable) {
                Toast.makeText(
                    this@ComposeAdminDashboardActivity,
                    "Network error: ${t.message}",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        })
    }

    private fun togglePause() {
        // Implementation for pause toggle
    }
}

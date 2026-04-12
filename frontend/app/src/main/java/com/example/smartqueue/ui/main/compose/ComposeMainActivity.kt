package com.example.smartqueue.ui.main.compose

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.smartqueue.network.ApiClient
import com.example.smartqueue.network.ApiService
import com.example.smartqueue.ui.admin.compose.AdminDashboardScreen
import com.example.smartqueue.ui.auth.compose.LoginScreen
import com.example.smartqueue.ui.auth.compose.RegisterScreen
import com.example.smartqueue.ui.doctor.compose.DoctorDashboardScreen
import com.example.smartqueue.ui.navigation.Screen
import com.example.smartqueue.ui.patient.compose.PatientQueueScreen
import com.example.smartqueue.ui.patient.compose.SymptomInputScreen
import com.example.smartqueue.ui.theme.SmartQTheme
import com.example.smartqueue.utils.SessionManager
import retrofit2.Callback

/**
 * Main Compose Activity with Navigation
 * Handles routing between all screens with smooth animated transitions
 */
class ComposeMainActivity : ComponentActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var apiService: ApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)
        apiService = ApiClient.getInstance().create(ApiService::class.java)

        setContent {
            SmartQTheme {
                val navController = rememberNavController()
                var currentUserRole by remember { mutableStateOf(sessionManager.getUserRole() ?: "patient") }
                var isAuthenticated by remember { mutableStateOf(sessionManager.isLoggedIn()) }

                ComposeNavGraph(
                    navController = navController,
                    currentUserRole = currentUserRole,
                    isAuthenticated = isAuthenticated,
                    onAuthStateChange = { isAuth, role ->
                        isAuthenticated = isAuth
                        currentUserRole = role
                    },
                    onLoginSuccess = { email, password ->
                        performLogin(email, password, navController, currentUserRole)
                    },
                    onRegisterSuccess = { email, password, name ->
                        performRegister(email, password, name, navController)
                    },
                    onLogout = {
                        performLogout(navController)
                    },
                )
            }
        }
    }

    private fun performLogin(
        email: String,
        password: String,
        navController: NavController,
        currentRole: String,
    ) {
        // TODO: Call your login API
        sessionManager.saveSession(userId = "user_123", role = "doctor", name = "Dr. Smith")
        navController.navigate(Screen.DoctorHome.route) {
            popUpTo(Screen.Login.route) { inclusive = true }
        }
    }

    private fun performRegister(
        email: String,
        password: String,
        name: String,
        navController: NavController,
    ) {
        // TODO: Call your register API
        sessionManager.saveSession(userId = "user_123", role = "patient", name = name)
        navController.navigate(Screen.PatientHome.route) {
            popUpTo(Screen.Register.route) { inclusive = true }
        }
    }

    private fun performLogout(navController: NavController) {
        sessionManager.logout()
        navController.navigate(Screen.Login.route) {
            popUpTo(0) { inclusive = true }
        }
    }
}

/**
 * Navigation Graph for all screens with animated transitions
 */
@Composable
fun ComposeNavGraph(
    navController: NavHostController,
    currentUserRole: String,
    isAuthenticated: Boolean,
    onAuthStateChange: (Boolean, String) -> Unit,
    onLoginSuccess: (String, String) -> Unit,
    onRegisterSuccess: (String, String, String) -> Unit,
    onLogout: () -> Unit,
) {
    val startDestination = if (isAuthenticated) {
        when (currentUserRole.lowercase()) {
            "doctor" -> Screen.DoctorHome.route
            "admin" -> Screen.AdminDashboard.route
            else -> Screen.PatientHome.route
        }
    } else {
        Screen.Login.route
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = androidx.compose.material3.MaterialTheme.colorScheme.background,
    ) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(durationMillis = 400),
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(durationMillis = 400),
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(durationMillis = 400),
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(durationMillis = 400),
                )
            },
        ) {
            // Auth Screens
            composable(Screen.Login.route) {
                LoginScreen(
                    onLoginSuccess = onLoginSuccess,
                    onNavigateToRegister = { navController.navigate(Screen.Register.route) },
                    onForgotPassword = { navController.navigate(Screen.ForgotPassword.route) },
                )
            }

            composable(Screen.Register.route) {
                RegisterScreen(
                    onRegisterSuccess = onRegisterSuccess,
                    onNavigateToLogin = { navController.navigate(Screen.Login.route) },
                )
            }

            // Patient Screens
            composable(Screen.PatientHome.route) {
                PatientQueueScreen(
                    patientName = "John Patient",
                    onViewDetails = { navController.navigate(Screen.SymptomInput.route) },
                    onRefresh = { /* Handle refresh */ },
                )
            }

            composable(Screen.PatientQueue.route) {
                PatientQueueScreen(
                    onViewDetails = {},
                )
            }

            composable(Screen.SymptomInput.route) {
                SymptomInputScreen(
                    onSubmit = { symptoms ->
                        // Navigate to triage result
                        navController.navigate(Screen.TriageResult.createRoute("triage_123"))
                    },
                    onBack = { navController.popBackStack() },
                )
            }

            // Doctor Screens
            composable(Screen.DoctorHome.route) {
                DoctorDashboardScreen(
                    doctorName = "Dr. Smith",
                    onCallNext = { /* Handle call next */ },
                )
            }

            composable(Screen.DoctorQueue.route) {
                DoctorDashboardScreen()
            }

            // Admin Screens
            composable(Screen.AdminDashboard.route) {
                AdminDashboardScreen(
                    adminName = "Dr. Admin",
                    onLogout = onLogout,
                    onCallNext = { /* Handle */ },
                    onPauseToggle = { /* Handle */ },
                    onRefreshML = { /* Handle */ },
                )
            }
        }
    }
}

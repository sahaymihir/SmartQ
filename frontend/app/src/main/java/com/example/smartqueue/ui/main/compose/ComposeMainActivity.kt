package com.example.smartqueue.ui.main.compose

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.smartqueue.models.request.JoinQueueRequest
import com.example.smartqueue.models.request.LoginRequest
import com.example.smartqueue.models.request.RegisterRequest
import com.example.smartqueue.models.request.SymptomRequest
import com.example.smartqueue.models.response.AuthResponse
import com.example.smartqueue.models.response.DoctorsResponse
import com.example.smartqueue.models.response.MessageResponse
import com.example.smartqueue.models.response.QueueResponse
import com.example.smartqueue.models.response.SymptomPredictResponse
import com.example.smartqueue.models.response.TokenResponse
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
import kotlinx.coroutines.delay
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ComposeMainActivity : ComponentActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var apiService: ApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)
        sessionManager.token?.let { ApiClient.setAuthToken(it) }
        apiService = ApiClient.getInstance().create(ApiService::class.java)

        setContent {
            SmartQTheme {
                val navController = rememberNavController()

                var currentUserRole by remember { mutableStateOf(sessionManager.role ?: "patient") }
                var currentUserName by remember { mutableStateOf(sessionManager.name ?: "User") }
                var isAuthenticated by remember { mutableStateOf(sessionManager.isLoggedIn) }

                var patientPosition by remember { mutableStateOf(0) }
                var patientQueueLength by remember { mutableStateOf(0) }
                var patientEta by remember { mutableStateOf(0) }
                var patientPriority by remember { mutableStateOf("normal") }
                var patientDoctorName by remember { mutableStateOf("Not assigned") }
                var patientQueueAhead by remember { mutableStateOf<List<Triple<String, String, String>>>(emptyList()) }
                var patientRefreshing by remember { mutableStateOf(false) }

                var adminQueue by remember { mutableStateOf<List<QueueResponse.QueueEntry>>(emptyList()) }
                var adminPaused by remember { mutableStateOf(false) }
                var adminAvgConsultation by remember { mutableStateOf(8) }
                var adminActionLoading by remember { mutableStateOf(false) }

                LaunchedEffect(isAuthenticated, currentUserRole) {
                    if (!isAuthenticated) return@LaunchedEffect
                    if (currentUserRole.lowercase() == "patient") {
                        while (isAuthenticated && currentUserRole.lowercase() == "patient") {
                            refreshPatientStatus(
                                onSuccess = { queue ->
                                    patientRefreshing = false
                                    patientPosition = queue.position
                                    patientQueueLength = maxOf(patientQueueLength, queue.position)
                                    patientEta = queue.etaMinutes
                                    patientQueueAhead = buildAheadPlaceholders(queue.position)
                                    patientPriority = queue.triagePriorityClass?.let(::priorityClassToLabel)
                                        ?: queue.routingLane?.let(::routingToPriority)
                                        ?: "normal"
                                    patientDoctorName = queue.doctorName ?: "Not assigned"
                                },
                                onNoToken = {
                                    patientRefreshing = false
                                    patientPosition = 0
                                    patientEta = 0
                                    patientQueueLength = 0
                                    patientQueueAhead = emptyList()
                                },
                                onError = {
                                    patientRefreshing = false
                                },
                            )
                            delay(10_000)
                        }
                    } else if (currentUserRole.lowercase() == "admin" || currentUserRole.lowercase() == "doctor") {
                        refreshAdminQueue(
                            doctorId = sessionManager.userId,
                            onSuccess = { queue ->
                                adminQueue = queue.queue ?: emptyList()
                                adminPaused = queue.isPaused
                                adminAvgConsultation = queue.avgConsultationMinutes
                            },
                            onError = {},
                        )
                    }
                }

                ComposeNavGraph(
                    navController = navController,
                    currentUserRole = currentUserRole,
                    currentUserName = currentUserName,
                    isAuthenticated = isAuthenticated,
                    patientPosition = patientPosition,
                    patientQueueLength = patientQueueLength.coerceAtLeast(patientPosition),
                    patientEta = patientEta,
                    patientPriority = patientPriority,
                    patientDoctorName = patientDoctorName,
                    patientQueueAhead = patientQueueAhead,
                    patientRefreshing = patientRefreshing,
                    adminQueueItems = adminQueue,
                    adminPaused = adminPaused,
                    adminAvgConsultation = adminAvgConsultation,
                    adminActionLoading = adminActionLoading,
                    onLogin = { email, password ->
                        performLogin(
                            email = email,
                            password = password,
                            navController = navController,
                            onAuthSuccess = { role, name ->
                                isAuthenticated = true
                                currentUserRole = role
                                currentUserName = name
                            },
                            onError = { showToast(it) },
                        )
                    },
                    onRegister = { name, email, password, phone, age ->
                        performRegister(
                            name = name,
                            email = email,
                            password = password,
                            phone = phone,
                            age = age,
                            navController = navController,
                            onAuthSuccess = { role, authName ->
                                isAuthenticated = true
                                currentUserRole = role
                                currentUserName = authName
                            },
                            onError = { showToast(it) },
                        )
                    },
                    onSubmitSymptoms = { chiefComplaint, symptoms, severity ->
                        submitSymptomsAndJoin(
                            chiefComplaint = chiefComplaint,
                            symptoms = symptoms,
                            severity = severity,
                            onJoined = { token ->
                                showToast(token.message ?: "Queue joined successfully")
                                patientPosition = token.position
                                patientQueueLength = maxOf(patientQueueLength, token.position)
                                patientEta = token.etaMinutes
                                patientQueueAhead = buildAheadPlaceholders(token.position)
                                patientPriority = token.triagePriorityClass?.let(::priorityClassToLabel)
                                    ?: token.routingLane?.let(::routingToPriority)
                                    ?: "normal"
                                navController.navigate(Screen.PatientHome.route) {
                                    popUpTo(Screen.SymptomInput.route) { inclusive = true }
                                }
                            },
                            onError = { showToast(it) },
                        )
                    },
                    onPatientRefresh = {
                        patientRefreshing = true
                        refreshPatientStatus(
                            onSuccess = { queue ->
                                patientRefreshing = false
                                patientPosition = queue.position
                                patientQueueLength = maxOf(patientQueueLength, queue.position)
                                patientEta = queue.etaMinutes
                                patientQueueAhead = buildAheadPlaceholders(queue.position)
                                patientPriority = queue.triagePriorityClass?.let(::priorityClassToLabel)
                                    ?: queue.routingLane?.let(::routingToPriority)
                                    ?: "normal"
                                patientDoctorName = queue.doctorName ?: "Not assigned"
                            },
                            onNoToken = {
                                patientRefreshing = false
                                showToast("No active token")
                                patientPosition = 0
                                patientEta = 0
                                patientQueueLength = 0
                                patientQueueAhead = emptyList()
                            },
                            onError = {
                                patientRefreshing = false
                                showToast(it)
                            },
                        )
                    },
                    onPatientCheckIn = {
                        apiService.checkIn().enqueue(defaultMessageCallback("Check-in complete"))
                    },
                    onPatientSnooze = {
                        apiService.snoozeQueue(2).enqueue(defaultMessageCallback("Queue snoozed"))
                    },
                    onCallNext = {
                        adminActionLoading = true
                        apiService.callNextPatient(sessionManager.userId).enqueue(object : Callback<MessageResponse> {
                            override fun onResponse(call: Call<MessageResponse>, response: Response<MessageResponse>) {
                                adminActionLoading = false
                                if (response.isSuccessful && response.body() != null) {
                                    showToast(response.body()!!.message ?: "Next patient called")
                                    refreshAdminQueue(
                                        doctorId = sessionManager.userId,
                                        onSuccess = { queue ->
                                            adminQueue = queue.queue ?: emptyList()
                                            adminPaused = queue.isPaused
                                            adminAvgConsultation = queue.avgConsultationMinutes
                                        },
                                        onError = { showToast(it) },
                                    )
                                } else {
                                    showToast("Could not call next patient")
                                }
                            }

                            override fun onFailure(call: Call<MessageResponse>, t: Throwable) {
                                adminActionLoading = false
                                showToast("Network error: ${t.localizedMessage ?: "request failed"}")
                            }
                        })
                    },
                    onTogglePause = {
                        val nextPaused = !adminPaused
                        apiService.togglePause(sessionManager.userId, nextPaused)
                            .enqueue(object : Callback<MessageResponse> {
                                override fun onResponse(call: Call<MessageResponse>, response: Response<MessageResponse>) {
                                    if (response.isSuccessful) {
                                        adminPaused = nextPaused
                                        showToast(if (nextPaused) "Queue paused" else "Queue resumed")
                                    } else {
                                        showToast("Unable to update queue state")
                                    }
                                }

                                override fun onFailure(call: Call<MessageResponse>, t: Throwable) {
                                    showToast("Network error: ${t.localizedMessage ?: "request failed"}")
                                }
                            })
                    },
                    onLogout = {
                        sessionManager.clearSession()
                        ApiClient.setAuthToken(null)
                        isAuthenticated = false
                        currentUserRole = "patient"
                        currentUserName = "User"
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }
        }
    }

    private fun defaultMessageCallback(defaultMessage: String): Callback<MessageResponse> {
        return object : Callback<MessageResponse> {
            override fun onResponse(call: Call<MessageResponse>, response: Response<MessageResponse>) {
                if (response.isSuccessful) {
                    showToast(response.body()?.message ?: defaultMessage)
                } else {
                    showToast("Request failed")
                }
            }

            override fun onFailure(call: Call<MessageResponse>, t: Throwable) {
                showToast("Network error: ${t.localizedMessage ?: "request failed"}")
            }
        }
    }

    private fun performLogin(
        email: String,
        password: String,
        navController: NavController,
        onAuthSuccess: (role: String, name: String) -> Unit,
        onError: (String) -> Unit,
    ) {
        apiService.login(LoginRequest(email, password)).enqueue(object : Callback<AuthResponse> {
            override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                val body = response.body()
                if (response.isSuccessful && body != null && body.isSuccess()) {
                    val user = body.user
                    if (user == null || body.token.isNullOrBlank()) {
                        onError("Invalid login response")
                        return
                    }

                    sessionManager.saveSession(
                        body.token,
                        user.id,
                        user.name,
                        user.email,
                        user.role,
                        user.age,
                    )
                    ApiClient.setAuthToken(body.token)
                    onAuthSuccess(user.role ?: "patient", user.name ?: "User")
                    navigateByRole(navController, user.role ?: "patient")
                } else {
                    onError(body?.message ?: "Invalid email or password")
                }
            }

            override fun onFailure(call: Call<AuthResponse>, t: Throwable) {
                onError("Network error: ${t.localizedMessage ?: "request failed"}")
            }
        })
    }

    private fun performRegister(
        name: String,
        email: String,
        password: String,
        phone: String,
        age: Int,
        navController: NavController,
        onAuthSuccess: (role: String, name: String) -> Unit,
        onError: (String) -> Unit,
    ) {
        ApiClient.setAuthToken(null)
        apiService.register(
            RegisterRequest(
                name,
                email,
                password,
                phone,
                age,
                "patient",
            ),
        ).enqueue(object : Callback<AuthResponse> {
            override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                val body = response.body()
                if (response.isSuccessful && body != null && body.isSuccess()) {
                    val user = body.user
                    if (user == null || body.token.isNullOrBlank()) {
                        onError("Invalid register response")
                        return
                    }

                    sessionManager.saveSession(
                        body.token,
                        user.id,
                        user.name,
                        user.email,
                        user.role,
                        user.age,
                    )
                    ApiClient.setAuthToken(body.token)
                    onAuthSuccess(user.role ?: "patient", user.name ?: "User")
                    navigateByRole(navController, user.role ?: "patient")
                } else {
                    onError(body?.message ?: "Registration failed")
                }
            }

            override fun onFailure(call: Call<AuthResponse>, t: Throwable) {
                onError("Network error: ${t.localizedMessage ?: "request failed"}")
            }
        })
    }

    private fun refreshPatientStatus(
        onSuccess: (QueueResponse) -> Unit,
        onNoToken: () -> Unit,
        onError: (String) -> Unit,
    ) {
        apiService.getQueueStatus().enqueue(object : Callback<QueueResponse> {
            override fun onResponse(call: Call<QueueResponse>, response: Response<QueueResponse>) {
                if (response.isSuccessful && response.body() != null && response.body()!!.isSuccess()) {
                    onSuccess(response.body()!!)
                } else if (response.code() == 404) {
                    onNoToken()
                } else {
                    onError("Could not refresh queue")
                }
            }

            override fun onFailure(call: Call<QueueResponse>, t: Throwable) {
                onError("Network error: ${t.localizedMessage ?: "request failed"}")
            }
        })
    }

    private fun submitSymptomsAndJoin(
        chiefComplaint: String,
        symptoms: List<String>,
        severity: Int,
        onJoined: (TokenResponse) -> Unit,
        onError: (String) -> Unit,
    ) {
        val combinedSymptoms = buildString {
            append(chiefComplaint.trim())
            if (symptoms.isNotEmpty()) {
                append(", ")
                append(symptoms.joinToString(", "))
            }
            append(" (severity $severity/5)")
        }.trim()

        apiService.getDoctors().enqueue(object : Callback<DoctorsResponse> {
            override fun onResponse(call: Call<DoctorsResponse>, response: Response<DoctorsResponse>) {
                val doctors = response.body()?.doctors ?: emptyList()
                if (!response.isSuccessful || doctors.isEmpty()) {
                    onError("No doctors available")
                    return
                }

                apiService.predictDoctor(SymptomRequest(combinedSymptoms)).enqueue(object : Callback<SymptomPredictResponse> {
                    override fun onResponse(
                        call: Call<SymptomPredictResponse>,
                        response: Response<SymptomPredictResponse>,
                    ) {
                        val predictedDoctorId = response.body()?.recommendedDoctor?.id
                        val fallbackDoctorId = doctors.firstOrNull()?.id
                        val selectedDoctorId = predictedDoctorId ?: fallbackDoctorId

                        if (selectedDoctorId.isNullOrBlank()) {
                            onError("No doctor could be selected")
                            return
                        }

                        val joinRequest = JoinQueueRequest(combinedSymptoms)
                        apiService.joinQueue(selectedDoctorId, joinRequest).enqueue(object : Callback<TokenResponse> {
                            override fun onResponse(call: Call<TokenResponse>, response: Response<TokenResponse>) {
                                if (response.isSuccessful && response.body() != null && response.body()!!.isSuccess()) {
                                    onJoined(response.body()!!)
                                } else {
                                    onError(response.body()?.message ?: "Could not join queue")
                                }
                            }

                            override fun onFailure(call: Call<TokenResponse>, t: Throwable) {
                                onError("Network error: ${t.localizedMessage ?: "request failed"}")
                            }
                        })
                    }

                    override fun onFailure(call: Call<SymptomPredictResponse>, t: Throwable) {
                        val fallbackDoctorId = doctors.firstOrNull()?.id
                        if (fallbackDoctorId.isNullOrBlank()) {
                            onError("No doctor could be selected")
                            return
                        }
                        val joinRequest = JoinQueueRequest(combinedSymptoms)
                        apiService.joinQueue(fallbackDoctorId, joinRequest).enqueue(object : Callback<TokenResponse> {
                            override fun onResponse(call: Call<TokenResponse>, response: Response<TokenResponse>) {
                                if (response.isSuccessful && response.body() != null && response.body()!!.isSuccess()) {
                                    onJoined(response.body()!!)
                                } else {
                                    onError(response.body()?.message ?: "Could not join queue")
                                }
                            }

                            override fun onFailure(call: Call<TokenResponse>, t: Throwable) {
                                onError("Network error: ${t.localizedMessage ?: "request failed"}")
                            }
                        })
                    }
                })
            }

            override fun onFailure(call: Call<DoctorsResponse>, t: Throwable) {
                onError("Network error: ${t.localizedMessage ?: "request failed"}")
            }
        })
    }

    private fun refreshAdminQueue(
        doctorId: String?,
        onSuccess: (QueueResponse) -> Unit,
        onError: (String) -> Unit,
    ) {
        val id = doctorId.orEmpty()
        apiService.getAdminQueue(id).enqueue(object : Callback<QueueResponse> {
            override fun onResponse(call: Call<QueueResponse>, response: Response<QueueResponse>) {
                if (response.isSuccessful && response.body() != null && response.body()!!.isSuccess()) {
                    onSuccess(response.body()!!)
                } else {
                    onError("Could not load queue")
                }
            }

            override fun onFailure(call: Call<QueueResponse>, t: Throwable) {
                onError("Network error: ${t.localizedMessage ?: "request failed"}")
            }
        })
    }

    private fun navigateByRole(navController: NavController, role: String) {
        val destination = when (role.lowercase()) {
            "admin" -> Screen.AdminDashboard.route
            "doctor" -> Screen.DoctorHome.route
            else -> Screen.PatientHome.route
        }
        navController.navigate(destination) {
            popUpTo(Screen.Login.route) { inclusive = true }
        }
    }

    private fun priorityClassToLabel(priorityClass: Int): String = when {
        priorityClass >= 4 -> "high"
        priorityClass == 3 -> "medium"
        else -> "normal"
    }

    private fun routingToPriority(routingLane: String): String = when (routingLane.lowercase()) {
        "immediate_review" -> "high"
        else -> "normal"
    }

    private fun queueEntriesToTriples(queue: List<QueueResponse.QueueEntry>): List<Triple<String, String, String>> {
        return queue
            .sortedBy { it.position }
            .map {
                Triple(
                    it.patientName ?: "Unknown",
                    "T${it.tokenNumber}",
                    (it.priority ?: "normal").lowercase(),
                )
            }
    }

    private fun buildAheadPlaceholders(position: Int): List<Triple<String, String, String>> {
        val count = (position - 1).coerceAtLeast(0)
        return (1..count).map { index ->
            Triple("Queue Patient #$index", "--", "normal")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    @Composable
    fun ComposeNavGraph(
        navController: NavHostController,
        currentUserRole: String,
        currentUserName: String,
        isAuthenticated: Boolean,
        patientPosition: Int,
        patientQueueLength: Int,
        patientEta: Int,
        patientPriority: String,
        patientDoctorName: String,
        patientQueueAhead: List<Triple<String, String, String>>,
        patientRefreshing: Boolean,
        adminQueueItems: List<QueueResponse.QueueEntry>,
        adminPaused: Boolean,
        adminAvgConsultation: Int,
        adminActionLoading: Boolean,
        onLogin: (email: String, password: String) -> Unit,
        onRegister: (name: String, email: String, password: String, phone: String, age: Int) -> Unit,
        onSubmitSymptoms: (chiefComplaint: String, symptoms: List<String>, severity: Int) -> Unit,
        onPatientRefresh: () -> Unit,
        onPatientCheckIn: () -> Unit,
        onPatientSnooze: () -> Unit,
        onCallNext: () -> Unit,
        onTogglePause: () -> Unit,
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
            color = MaterialTheme.colorScheme.background,
        ) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(durationMillis = 350),
                    )
                },
                exitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(durationMillis = 350),
                    )
                },
                popEnterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(durationMillis = 350),
                    )
                },
                popExitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(durationMillis = 350),
                    )
                },
            ) {
                composable(Screen.Login.route) {
                    LoginScreen(
                        onLoginSuccess = onLogin,
                        onNavigateToRegister = { navController.navigate(Screen.Register.route) },
                        onForgotPassword = {},
                    )
                }

                composable(Screen.Register.route) {
                    RegisterScreen(
                        onRegisterSuccess = onRegister,
                        onNavigateToLogin = { navController.navigate(Screen.Login.route) },
                    )
                }

                composable(Screen.PatientHome.route) {
                    PatientQueueScreen(
                        patientName = currentUserName,
                        queuePosition = patientPosition,
                        queueLength = patientQueueLength,
                        estimatedWaitMin = patientEta,
                        priority = patientPriority,
                        doctorName = patientDoctorName,
                        queueAhead = patientQueueAhead,
                        isRefreshing = patientRefreshing,
                        onViewDetails = { navController.navigate(Screen.SymptomInput.route) },
                        onRefresh = onPatientRefresh,
                        onCheckIn = onPatientCheckIn,
                        onSnooze = onPatientSnooze,
                    )
                }

                composable(Screen.SymptomInput.route) {
                    SymptomInputScreen(
                        onSubmit = onSubmitSymptoms,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(Screen.DoctorHome.route) {
                    val queueTriples = queueEntriesToTriples(adminQueueItems)
                    DoctorDashboardScreen(
                        doctorName = currentUserName,
                        queueItems = queueTriples,
                        patientsServedCount = 0,
                        avgConsultationMinutesValue = adminAvgConsultation,
                        triageAccuracyValue = 0.97f,
                        callNextLoading = adminActionLoading,
                        onCallNext = onCallNext,
                    )
                }

                composable(Screen.AdminDashboard.route) {
                    val queueTriples = queueEntriesToTriples(adminQueueItems)
                    val completedCount = adminQueueItems.count { it.status == "completed" }
                    AdminDashboardScreen(
                        adminName = currentUserName,
                        queueItems = queueTriples,
                        queueCountValue = adminQueueItems.size,
                        completedCountValue = completedCount,
                        avgWaitValue = "${adminAvgConsultation}m",
                        pausedState = adminPaused,
                        isQueueLoading = adminActionLoading,
                        onLogout = onLogout,
                        onCallNext = onCallNext,
                        onPauseToggle = onTogglePause,
                        onRefreshML = {},
                    )
                }
            }
        }
    }
}

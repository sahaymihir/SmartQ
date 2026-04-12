package com.example.smartqueue.ui.navigation

/**
 * Navigation Destinations for SmartQ App
 * Used with Compose Navigation for routing between screens
 */
sealed class Screen(val route: String) {
    // Auth Navigation
    object Login : Screen("login")
    object Register : Screen("register")
    object ForgotPassword : Screen("forgot_password")

    // Patient Navigation
    object PatientHome : Screen("patient_home")
    object PatientQueue : Screen("patient_queue")
    object SymptomInput : Screen("symptom_input")
    object TriageResult : Screen("triage_result/{triageId}") {
        fun createRoute(triageId: String) = "triage_result/$triageId"
    }

    // Doctor Navigation
    object DoctorHome : Screen("doctor_home")
    object DoctorQueue : Screen("doctor_queue")
    object PatientDetail : Screen("patient_detail/{patientId}") {
        fun createRoute(patientId: String) = "patient_detail/$patientId"
    }

    // Admin Navigation
    object AdminDashboard : Screen("admin_dashboard")
    object ModelEval : Screen("model_eval")

    // Splash
    object Splash : Screen("splash")
}

/**
 * Navigation destination types for bottom nav
 */
enum class BottomNavDestination(val route: String, val label: String, val icon: String) {
    HOME("home", "Home", "home"),
    QUEUE("queue", "Queue", "queue"),
    PROFILE("profile", "Profile", "profile"),
}

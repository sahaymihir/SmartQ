package com.example.smartqueue.network;

import com.example.smartqueue.models.request.CreateUserRequest;
import com.example.smartqueue.models.request.EmergencyRequest;
import com.example.smartqueue.models.request.LoginRequest;
import com.example.smartqueue.models.request.JoinQueueRequest;
import com.example.smartqueue.models.request.NurseTriageRequest;
import com.example.smartqueue.models.request.RegisterRequest;
import com.example.smartqueue.models.request.PrescriptionRequest;
import com.example.smartqueue.models.request.SymptomRequest;
import com.example.smartqueue.models.response.AuthResponse;
import com.example.smartqueue.models.response.ConsultationHistoryResponse;
import com.example.smartqueue.models.response.DoctorsResponse;
import com.example.smartqueue.models.response.MessageResponse;
import com.example.smartqueue.models.response.MlOpsLogsResponse;
import com.example.smartqueue.models.response.ModelEvalHistoryResponse;
import com.example.smartqueue.models.response.PrescriptionResponse;
import com.example.smartqueue.models.response.QueueResponse;
import com.example.smartqueue.models.response.SymptomPredictResponse;
import com.example.smartqueue.models.response.TokenResponse;
import com.example.smartqueue.models.response.UserListResponse;

import retrofit2.Call;
import retrofit2.http.*;

public interface ApiService {

    @POST("auth/register")
    Call<AuthResponse> register(@Body RegisterRequest body);

    @POST("auth/login")
    Call<AuthResponse> login(@Body LoginRequest body);

    @POST("notifications/register-device")
    Call<MessageResponse> registerDeviceToken(@Body com.example.smartqueue.models.request.NotificationRegistrationRequest body);

    @POST("queue/join")
    Call<TokenResponse> joinQueue(@Query("doctorId") String doctorId, @Body JoinQueueRequest body);

    @GET("queue/status")
    Call<QueueResponse> getQueueStatus();

    @POST("queue/snooze")
    Call<MessageResponse> snoozeQueue(@Query("positions") int positions);

    @POST("queue/checkin")
    Call<MessageResponse> checkIn();

    @POST("queue/leave")
    Call<MessageResponse> leaveQueue();

    /** Patient's past consultations — used for follow-up visit linkage. */
    @GET("queue/history")
    Call<ConsultationHistoryResponse> getConsultationHistory();

    /**
     * Nurse / staff submit actual vitals and optionally override priority.
     * Roles: nurse, admin, doctor.
     */
    @PATCH("queue/nurse-triage/{tokenId}")
    Call<TokenResponse> nurseTriageToken(@Path("tokenId") String tokenId, @Body NurseTriageRequest body);

    /**
     * Nurse triage board: patients still waiting for vitals capture.
     * Roles: nurse, admin, doctor.
     */
    @GET("queue/nurse-board")
    Call<QueueResponse> getNurseQueue();

    /**
     * Staff create an emergency token for a patient who cannot self-register
     * (e.g. unconscious). Forces immediate_review routing lane (KTAS 1).
     * Roles: nurse, admin, doctor.
     */
    @POST("queue/emergency")
    Call<TokenResponse> createEmergencyToken(@Body EmergencyRequest body);

    // ── DOCTOR ENDPOINTS ─────────────────────────────────────

    @GET("doctors")
    Call<DoctorsResponse> getDoctors();

    @POST("doctors/symptom-predict")
    Call<SymptomPredictResponse> predictDoctor(@Body SymptomRequest body);

    // ── ADMIN ENDPOINTS ──────────────────────────────────────

    @GET("admin/queue")
    Call<QueueResponse> getAdminQueue(@Query("doctorId") String doctorId);

    @POST("admin/next")
    Call<MessageResponse> callNextPatient(@Query("doctorId") String doctorId);

    @POST("admin/pause")
    Call<MessageResponse> togglePause(@Query("doctorId") String doctorId, @Query("paused") boolean paused);

    @GET("admin/patient-history")
    Call<ConsultationHistoryResponse> getPatientHistory(@Query("patientId") String patientId,
                                                        @Query("limit") int limit);

    @POST("queue/noshow")
    Call<MessageResponse> markNoShow(@Query("tokenId") String tokenId);

    @GET("prescriptions/{tokenId}")
    Call<PrescriptionResponse> getPrescription(@Path("tokenId") String tokenId);

    @PUT("prescriptions/{tokenId}")
    Call<PrescriptionResponse> savePrescription(@Path("tokenId") String tokenId, @Body PrescriptionRequest body);

    @GET("admin/model-eval-history")
    Call<ModelEvalHistoryResponse> getModelEvalHistory();

    @GET("admin/ml-ops-logs")
    Call<MlOpsLogsResponse> getMlOpsLogs(@Query("limit") int limit);

    @POST("admin/model-eval-run")
    Call<SymptomPredictResponse> runAdminModelEval(@Body SymptomRequest body);

    @POST("admin/seed")
    Call<MessageResponse> seedDummyData();

    // ── USER MANAGEMENT ENDPOINTS (admin + superuser) ─────────

    /**
     * List all users with optional role filter and search.
     * Admin: sees doctor/nurse/patient only.
     * Superuser: sees all roles.
     */
    @GET("users")
    Call<UserListResponse> listUsers(
            @Query("role") String role,
            @Query("search") String search,
            @Query("page") int page,
            @Query("limit") int limit);

    /**
     * Create a new staff (doctor/nurse) or patient account.
     * Admin can create: doctor, nurse, patient.
     * Superuser can create: any role.
     */
    @POST("users")
    Call<MessageResponse> createUser(@Body CreateUserRequest body);

    /**
     * Delete a user by ID.
     * Admin: can delete doctor/nurse/patient.
     * Superuser: can delete anyone (except last superuser).
     */
    @DELETE("users/{id}")
    Call<MessageResponse> deleteUser(@Path("id") String userId);
}

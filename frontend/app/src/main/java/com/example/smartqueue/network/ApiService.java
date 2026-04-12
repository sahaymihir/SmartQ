package com.example.smartqueue.network;

import com.example.smartqueue.models.request.LoginRequest;
import com.example.smartqueue.models.request.JoinQueueRequest;
import com.example.smartqueue.models.request.RegisterRequest;
import com.example.smartqueue.models.request.PrescriptionRequest;
import com.example.smartqueue.models.request.SymptomRequest;
import com.example.smartqueue.models.response.AuthResponse;
import com.example.smartqueue.models.response.DoctorsResponse;
import com.example.smartqueue.models.response.ModelEvalHistoryResponse;
import com.example.smartqueue.models.response.MlOpsLogsResponse;
import com.example.smartqueue.models.response.QueueResponse;
import com.example.smartqueue.models.response.SymptomPredictResponse;
import com.example.smartqueue.models.response.TokenResponse;
import com.example.smartqueue.models.response.MessageResponse;

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

    @POST("admin/noshow")
    Call<MessageResponse> markNoShow(@Query("tokenId") String tokenId);

    @POST("admin/prescription")
    Call<MessageResponse> savePrescription(@Body PrescriptionRequest body);

    @GET("admin/model-eval-history")
    Call<ModelEvalHistoryResponse> getModelEvalHistory();

    @GET("admin/ml-ops-logs")
    Call<MlOpsLogsResponse> getMlOpsLogs(@Query("limit") int limit);

    @POST("admin/model-eval-run")
    Call<SymptomPredictResponse> runAdminModelEval(@Body SymptomRequest body);

    @POST("admin/seed")
    Call<MessageResponse> seedDummyData();
}

package com.example.smartqueue.utils;

import com.example.smartqueue.models.response.QueueResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * MockQueueManager — Fake queue data for UI testing without backend.
 *
 * Simulates:
 *  - Joining a queue (get token + position)
 *  - Getting live queue status (position + ETA)
 *  - Snoozing (push back 2 spots)
 *  - Geofence check-in
 *  - Auto-advancing queue every 10 seconds (simulates doctor calling next)
 */
public class MockQueueManager {

    public interface QueueCallback {
        void onSuccess(QueueResponse response);
        void onFailure(String message);
    }

    public interface SimpleCallback {
        void onSuccess(String message);
        void onFailure(String message);
    }

    // ── Singleton state ──────────────────────────────────
    private static int currentPosition  = 0;  // 0 = not in queue
    private static int tokenNumber      = 0;
    private static int etaMinutes       = 0;
    private static String queueStatus   = "idle"; // idle / waiting / called / checkedin
    private static boolean checkedIn    = false;
    private static int snoozeCount      = 0;
    private static String currentDoctor = "Dr. Nisha Shetty";

    // Auto-advance handler (simulates queue moving)
    private static android.os.Handler autoAdvanceHandler = null;
    private static Runnable autoAdvanceRunnable          = null;
    private static QueueUpdateListener updateListener    = null;

    public interface QueueUpdateListener {
        void onQueueUpdated(int newPosition, int newEta);
        void onCalled();
    }

    public static void setUpdateListener(QueueUpdateListener listener) {
        updateListener = listener;
    }

    // ── Join Queue ───────────────────────────────────────
    public static void joinQueue(String doctorName, QueueCallback callback) {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (!queueStatus.equals("idle")) {
                callback.onFailure("You already have an active token");
                return;
            }

            currentPosition = 4; // start at position 4
            tokenNumber     = (int)(Math.random() * 50) + 10;
            etaMinutes      = (currentPosition - 1) * 8; // 8 min avg per patient
            queueStatus     = "waiting";
            checkedIn       = false;
            snoozeCount     = 0;
            currentDoctor   = doctorName;

            startAutoAdvance();

            QueueResponse response = buildQueueResponse();
            callback.onSuccess(response);
        }, 800);
    }

    // ── Get Status ───────────────────────────────────────
    public static void getStatus(QueueCallback callback) {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (queueStatus.equals("idle")) {
                callback.onFailure("No active token");
                return;
            }
            callback.onSuccess(buildQueueResponse());
        }, 300);
    }

    // ── Snooze ───────────────────────────────────────────
    public static void snooze(SimpleCallback callback) {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (!queueStatus.equals("waiting")) {
                callback.onFailure("Can only snooze while waiting");
                return;
            }
            if (snoozeCount >= 2) {
                callback.onFailure("Maximum snooze limit (2) reached");
                return;
            }
            currentPosition += 2;
            etaMinutes = (currentPosition - 1) * 8;
            snoozeCount++;
            callback.onSuccess("Snoozed! New position: #" + currentPosition);
        }, 500);
    }

    // ── Geofence Check-in ────────────────────────────────
    public static void checkIn(SimpleCallback callback) {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (queueStatus.equals("idle")) {
                callback.onFailure("No active token to check in");
                return;
            }
            if (checkedIn) {
                callback.onSuccess("Already checked in!");
                return;
            }
            checkedIn   = true;
            queueStatus = "checkedin";
            callback.onSuccess("Checked in successfully!");
        }, 400);
    }

    // ── Leave Queue (cancel) ─────────────────────────────
    public static void leaveQueue() {
        stopAutoAdvance();
        currentPosition = 0;
        tokenNumber     = 0;
        etaMinutes      = 0;
        queueStatus     = "idle";
        checkedIn       = false;
        snoozeCount     = 0;
    }

    // ── Getters ──────────────────────────────────────────
    public static boolean isInQueue()   { return !queueStatus.equals("idle"); }
    public static int getPosition()     { return currentPosition; }
    public static int getTokenNumber()  { return tokenNumber; }
    public static String getStatus()    { return queueStatus; }
    public static boolean isCheckedIn() { return checkedIn; }
    public static int getSnoozeCount()  { return snoozeCount; }

    // ── Auto-advance (simulates queue moving every 10s) ──
    private static void startAutoAdvance() {
        stopAutoAdvance();
        autoAdvanceHandler  = new android.os.Handler(android.os.Looper.getMainLooper());
        autoAdvanceRunnable = new Runnable() {
            @Override
            public void run() {
                if (currentPosition > 1) {
                    currentPosition--;
                    etaMinutes = Math.max(0, (currentPosition - 1) * 8);

                    if (updateListener != null) {
                        if (currentPosition == 1) {
                            queueStatus = "called";
                            updateListener.onCalled();
                        } else {
                            updateListener.onQueueUpdated(currentPosition, etaMinutes);
                        }
                    }

                    if (currentPosition > 1) {
                        autoAdvanceHandler.postDelayed(this, 10000); // advance every 10s
                    }
                }
            }
        };
        autoAdvanceHandler.postDelayed(autoAdvanceRunnable, 10000);
    }

    private static void stopAutoAdvance() {
        if (autoAdvanceHandler != null && autoAdvanceRunnable != null) {
            autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable);
        }
    }

    // ── Build mock QueueResponse ─────────────────────────
    private static QueueResponse buildQueueResponse() {
        QueueResponse r = new QueueResponse();
        r.setSuccess(true);
        r.setPosition(currentPosition);
        r.setEtaMinutes(etaMinutes);
        r.setStatus(queueStatus);
        r.setTokenNumber(tokenNumber);
        r.setDoctorName(currentDoctor);
        r.setCheckedIn(checkedIn);

        // Build mock queue list (positions ahead)
        List<QueueResponse.QueueEntry> entries = new ArrayList<>();
        for (int i = 1; i <= Math.min(currentPosition, 6); i++) {
            QueueResponse.QueueEntry e = new QueueResponse.QueueEntry();
            e.setPosition(i);
            e.setPatientName(i == currentPosition ? "You" : "Patient " + i);
            e.setEtaMinutes((i - 1) * 8);
            e.setPriority(i == 1 ? "high" : "normal");
            e.setStatus(i == 1 ? "called" : "waiting");
            entries.add(e);
        }
        r.setQueue(entries);
        return r;
    }
}
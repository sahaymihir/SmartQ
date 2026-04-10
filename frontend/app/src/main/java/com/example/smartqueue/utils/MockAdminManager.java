package com.example.smartqueue.utils;

import com.example.smartqueue.models.response.QueueResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * MockAdminManager — Fake admin queue data for UI testing.
 *
 * Simulates:
 *  - Full patient queue list
 *  - Call next patient
 *  - Mark no-show
 *  - Pause / resume queue
 *  - Live stats (total waiting, avg ETA, consultations done)
 */
public class MockAdminManager {

    public interface AdminCallback {
        void onSuccess(List<QueueResponse.QueueEntry> queue);
        void onFailure(String message);
    }

    public interface SimpleCallback {
        void onSuccess(String message);
        void onFailure(String message);
    }

    // ── Queue State ──────────────────────────────────────
    private static boolean isPaused        = false;
    private static int consultationsDone   = 3;
    private static int avgConsultMins      = 8;
    private static String currentlyServing = "Token #10 — Arjun Mehta";

    private static final List<QueueResponse.QueueEntry> queue = new ArrayList<>();

    static {
        resetQueue();
    }

    private static void resetQueue() {
        queue.clear();
        String[][] patients = {
                { "1",  "Priya Sharma",   "high",   "waiting", "0"  },
                { "2",  "Rahul Verma",    "normal", "waiting", "8"  },
                { "3",  "Anita Desai",    "high",   "waiting", "16" },
                { "4",  "Karan Singh",    "normal", "waiting", "24" },
                { "5",  "Meera Nair",     "medium", "waiting", "32" },
                { "6",  "Suresh Pillai",  "normal", "waiting", "40" },
                { "7",  "Divya Menon",    "normal", "waiting", "48" },
        };

        for (String[] p : patients) {
            QueueResponse.QueueEntry e = new QueueResponse.QueueEntry();
            e.setPosition(Integer.parseInt(p[0]));
            e.setPatientName(p[1]);
            e.setPriority(p[2]);
            e.setStatus(p[3]);
            e.setEtaMinutes(Integer.parseInt(p[4]));
            e.setTokenId("token_" + p[0]);
            queue.add(e);
        }
    }

    // ── Get Queue ────────────────────────────────────────
    public static void getQueue(AdminCallback callback) {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            callback.onSuccess(new ArrayList<>(queue));
        }, 400);
    }

    // ── Call Next ────────────────────────────────────────
    public static void callNext(SimpleCallback callback) {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (isPaused) {
                callback.onFailure("Queue is paused. Resume first.");
                return;
            }
            if (queue.isEmpty()) {
                callback.onSuccess("Queue is empty — no more patients.");
                return;
            }

            QueueResponse.QueueEntry next = queue.remove(0);
            consultationsDone++;
            currentlyServing = "Token — " + next.getPatientName();

            // Reorder positions
            reorderPositions();

            callback.onSuccess("Now calling: " + next.getPatientName());
        }, 500);
    }

    // ── Mark No-Show ─────────────────────────────────────
    public static void markNoShow(String tokenId, SimpleCallback callback) {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            QueueResponse.QueueEntry toRemove = null;
            for (QueueResponse.QueueEntry e : queue) {
                if (e.getTokenId().equals(tokenId)) {
                    toRemove = e;
                    break;
                }
            }
            if (toRemove == null) {
                callback.onFailure("Patient not found");
                return;
            }
            String name = toRemove.getPatientName();
            queue.remove(toRemove);
            reorderPositions();
            callback.onSuccess(name + " marked as no-show");
        }, 400);
    }

    // ── Toggle Pause ─────────────────────────────────────
    public static void togglePause(SimpleCallback callback) {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            isPaused = !isPaused;
            callback.onSuccess(isPaused ? "Queue paused" : "Queue resumed");
        }, 300);
    }

    // ── Getters ──────────────────────────────────────────
    public static boolean isPaused()          { return isPaused; }
    public static int getConsultationsDone()  { return consultationsDone; }
    public static int getAvgConsultMins()     { return avgConsultMins; }
    public static String getCurrentlyServing(){ return currentlyServing; }
    public static int getWaitingCount()       { return queue.size(); }

    // ── Helpers ──────────────────────────────────────────
    private static void reorderPositions() {
        for (int i = 0; i < queue.size(); i++) {
            queue.get(i).setPosition(i + 1);
            queue.get(i).setEtaMinutes(i * avgConsultMins);
        }
    }
}
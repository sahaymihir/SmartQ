package com.example.smartqueue.utils;

import com.example.smartqueue.models.response.MessageResponse;
import com.google.gson.Gson;

import retrofit2.Response;

public final class ApiErrorParser {
    private static final Gson GSON = new Gson();

    private ApiErrorParser() {}

    public static MessageResponse parseMessage(Response<?> response) {
        try {
            if (response == null || response.errorBody() == null) {
                return null;
            }
            String raw = response.errorBody().string();
            if (raw == null || raw.trim().isEmpty()) {
                return null;
            }
            return GSON.fromJson(raw, MessageResponse.class);
        } catch (Exception ignored) {
            return null;
        }
    }
}

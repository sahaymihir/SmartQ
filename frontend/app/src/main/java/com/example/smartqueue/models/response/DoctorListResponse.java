package com.example.smartqueue.models.response;

import java.util.List;

public class DoctorListResponse {
    private boolean success;
    private String message;
    private List<Doctor> doctors;

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public List<Doctor> getDoctors() { return doctors; }

    public static class Doctor {
        private String id;
        private String name;
        private String email;

        public String getId() { return id; }
        public String getName() { return name; }
        public String getEmail() { return email; }
    }
}

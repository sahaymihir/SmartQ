package com.example.smartqueue.models.response;

import java.util.List;

public class DoctorsResponse {
    private boolean success;
    private List<Doctor> doctors;

    public boolean isSuccess() { return success; }
    public List<Doctor> getDoctors() { return doctors; }

    public static class Doctor {
        private String id;
        private String name;
        private String specialty;
        private boolean isAvailable;

        public String getId()       { return id; }
        public String getName()     { return name; }
        public String getSpecialty() { return specialty; }
        public boolean isAvailable() { return isAvailable; }
    }
}

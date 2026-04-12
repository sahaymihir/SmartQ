package com.example.smartqueue.models.response;

import java.util.List;

/**
 * Response model for GET /api/users
 * Returns a paginated list of users for the User Management screen.
 */
public class UserListResponse {
    private boolean success;
    private List<UserEntry> users;
    private int total;
    private int page;
    private int pages;

    public boolean isSuccess() { return success; }
    public List<UserEntry> getUsers() { return users; }
    public int getTotal() { return total; }
    public int getPage() { return page; }
    public int getPages() { return pages; }

    public static class UserEntry {
        private String id;
        private String name;
        private String email;
        private String phone;
        private int age;
        private String role;
        /** staffId is non-null for doctor / nurse / admin / superuser accounts. */
        private String staffId;
        private String specialty;
        private int priorityScore;
        private String createdAt;

        public String getId() { return id; }
        public String getName() { return name; }
        public String getEmail() { return email; }
        public String getPhone() { return phone; }
        public int getAge() { return age; }
        public String getRole() { return role; }
        public String getStaffId() { return staffId; }
        public String getSpecialty() { return specialty; }
        public int getPriorityScore() { return priorityScore; }
        public String getCreatedAt() { return createdAt; }

        /** Returns a role badge label suitable for display in the UI. */
        public String getRoleBadge() {
            if (role == null) return "PATIENT";
            switch (role.toLowerCase()) {
                case "doctor":    return "DOCTOR";
                case "nurse":     return "NURSE";
                case "admin":     return "ADMIN";
                case "superuser": return "SUPERUSER";
                default:          return "PATIENT";
            }
        }

        /** Returns a display label combining staffId and name for staff members. */
        public String getDisplayLabel() {
            if (staffId != null && !staffId.isEmpty()) {
                return "[" + staffId + "] " + name;
            }
            return name;
        }
    }
}

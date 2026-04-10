package com.example.smartqueue.models.response;

public class AuthResponse {
    private boolean success;
    private String token;
    private String message;
    private User user;

    // Getters
    public boolean isSuccess() { return success; }
    public String getToken() { return token; }
    public String getMessage() { return message; }
    public User getUser() { return user; }

    // Setters (needed for MockAuthManager)
    public void setSuccess(boolean success) { this.success = success; }
    public void setToken(String token) { this.token = token; }
    public void setMessage(String message) { this.message = message; }
    public void setUser(User user) { this.user = user; }

    public static class User {
        private String id;
        private String name;
        private String email;
        private String role;
        private int age;

        // Getters
        public String getId() { return id; }
        public String getName() { return name; }
        public String getEmail() { return email; }
        public String getRole() { return role; }
        public int getAge() { return age; }

        // Setters
        public void setId(String id) { this.id = id; }
        public void setName(String name) { this.name = name; }
        public void setEmail(String email) { this.email = email; }
        public void setRole(String role) { this.role = role; }
        public void setAge(int age) { this.age = age; }
    }
}
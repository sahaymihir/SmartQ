package com.example.smartqueue.models.request;

public class RegisterRequest {
    private String name;
    private String email;
    private String password;
    private String phone;
    private int age;
    private String role; // "patient" or "admin"

    public RegisterRequest(String name, String email, String password,
                           String phone, int age, String role) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.phone = phone;
        this.age = age;
        this.role = role;
    }

    // Getters
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getPhone() { return phone; }
    public int getAge() { return age; }
    public String getRole() { return role; }
}
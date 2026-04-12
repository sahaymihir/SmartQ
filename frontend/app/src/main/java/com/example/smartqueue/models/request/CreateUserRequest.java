package com.example.smartqueue.models.request;

/**
 * Request body for POST /api/users
 * Used by admin / superuser to create staff (doctor, nurse) or patient accounts.
 */
public class CreateUserRequest {
    private final String name;
    private final String email;
    private final String password;
    private final String phone;
    private final int age;
    private final String role;
    private final String specialty;  // only meaningful when role = "doctor"

    public CreateUserRequest(String name, String email, String password,
                             String phone, int age, String role, String specialty) {
        this.name      = name;
        this.email     = email;
        this.password  = password;
        this.phone     = phone;
        this.age       = age;
        this.role      = role;
        this.specialty = specialty;
    }

    public String getName()      { return name; }
    public String getEmail()     { return email; }
    public String getPassword()  { return password; }
    public String getPhone()     { return phone; }
    public int    getAge()       { return age; }
    public String getRole()      { return role; }
    public String getSpecialty() { return specialty; }
}

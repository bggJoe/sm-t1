package com.example.backend.admin.domain;

import jakarta.persistence.*;

/**
 * Admin credential entity: stores username + bcrypt password hash.
 * INV-005 enforcement happens in AdminAuthService (password comparison),
 * not at this layer.
 */
@Entity
@Table(name = "admin_credentials")
public class AdminCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    protected AdminCredential() {
    }

    public AdminCredential(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }
}

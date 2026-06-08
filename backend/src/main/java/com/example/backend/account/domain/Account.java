package com.example.backend.account.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Account entity — represents an approved user account.
 *
 * INV-011: email is UNIQUE (enforced by DB constraint + application layer check)
 * INV-012: passwordHash must be a BCrypt hash (never plaintext)
 * INV-013: initial status is always ACTIVE
 */
@Entity
@Table(name = "accounts", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_modified_at", nullable = false)
    private Instant lastModifiedAt;

    /** Required by JPA. Do not use directly. */
    protected Account() {}

    /**
     * Creates a new ACTIVE account. INV-013: status is always ACTIVE on creation.
     * @param email       unique email address (INV-011 enforced at application layer)
     * @param passwordHash BCrypt-encoded password (INV-012: caller must encode before passing)
     */
    public Account(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.status = AccountStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.lastModifiedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public AccountStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastModifiedAt() { return lastModifiedAt; }
}

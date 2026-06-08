package com.example.backend.admin.application;

import com.example.backend.admin.domain.AdminCredential;

import java.util.Optional;

/**
 * Application layer Port: defines the persistence contract for admin credential lookup.
 * JPA implementation lives in admin.infrastructure (module-private).
 */
public interface AdminCredentialRepository {

    Optional<AdminCredential> findByUsername(String username);

    AdminCredential save(AdminCredential credential);
}

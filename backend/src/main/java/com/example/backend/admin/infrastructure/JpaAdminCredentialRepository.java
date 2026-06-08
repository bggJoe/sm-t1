package com.example.backend.admin.infrastructure;

import com.example.backend.admin.application.AdminCredentialRepository;
import com.example.backend.admin.domain.AdminCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * JPA implementation of AdminCredentialRepository port.
 * Module-private: only accessible within admin module.
 */
interface JpaAdminCredentialRepository extends JpaRepository<AdminCredential, Long>, AdminCredentialRepository {

    @Override
    Optional<AdminCredential> findByUsername(String username);
}

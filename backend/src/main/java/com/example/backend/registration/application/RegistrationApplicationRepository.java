package com.example.backend.registration.application;

import com.example.backend.registration.domain.ApplicationStatus;
import com.example.backend.registration.domain.RegistrationApplication;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port interface for registration application persistence.
 * Implemented by infrastructure (JpaRegistrationApplicationRepository).
 * Application layer depends only on this interface — never on the JPA implementation.
 */
public interface RegistrationApplicationRepository {
    Optional<RegistrationApplication> findPendingByEmail(String email);
    List<RegistrationApplication> findAllByStatus(ApplicationStatus status);
    Optional<RegistrationApplication> findById(UUID id);
    RegistrationApplication save(RegistrationApplication application);
}

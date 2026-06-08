package com.example.backend.registration.infrastructure;

import com.example.backend.registration.application.RegistrationApplicationRepository;
import com.example.backend.registration.domain.ApplicationStatus;
import com.example.backend.registration.domain.RegistrationApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA implementation of RegistrationApplicationRepository (application-layer port).
 * Infrastructure depends on Application (port interface) and Domain (entity) — both are permitted
 * by the layer rules defined in ARCHITECTURE-LAYERS.md.
 */
public interface JpaRegistrationApplicationRepository
        extends JpaRepository<RegistrationApplication, UUID>,
                RegistrationApplicationRepository {

    @Query("SELECT r FROM RegistrationApplication r WHERE r.email = :email AND r.status = 'PENDING'")
    Optional<RegistrationApplication> findPendingByEmail(@Param("email") String email);

    List<RegistrationApplication> findAllByStatus(ApplicationStatus status);
}

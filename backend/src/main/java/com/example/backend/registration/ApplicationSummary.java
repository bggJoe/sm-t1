package com.example.backend.registration;

import com.example.backend.registration.domain.ApplicationStatus;
import java.util.UUID;

/**
 * Read-only projection of a RegistrationApplication for admin listing.
 * Decouples the admin portal from the domain entity.
 */
public record ApplicationSummary(UUID id, String email, String name, ApplicationStatus status) {}

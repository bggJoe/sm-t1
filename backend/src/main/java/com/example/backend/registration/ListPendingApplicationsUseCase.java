package com.example.backend.registration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public UseCase interface exposed by the registration module (root package = public API).
 * Allows the admin portal to query PENDING applications without depending
 * on registration internals (DEC-019).
 */
public interface ListPendingApplicationsUseCase {
    List<ApplicationSummary> listPending();

    /**
     * Finds any application by id regardless of status.
     * Used by admin module to retrieve email before Stage 2 account creation (DEC-021).
     */
    Optional<ApplicationSummary> findById(UUID id);
}

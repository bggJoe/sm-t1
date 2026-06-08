package com.example.backend.registration;

import java.util.UUID;

/**
 * Public UseCase interface exposed by the registration module (root package = public API).
 * Admin portal triggers approve/reject; business rules (INV-008/009/010)
 * are enforced inside the registration module — not in the admin portal (DEC-019).
 */
public interface ReviewApplicationUseCase {
    /**
     * @param applicationId    target application
     * @param decision         APPROVE or REJECT
     * @param reason           required when decision == REJECT (INV-010); ignored for APPROVE
     * @param reviewerUsername JWT sub claim extracted by the caller (INV-009)
     * @throws IllegalStateException    if application is not PENDING (INV-008)
     * @throws IllegalArgumentException if decision == REJECT and reason is blank (INV-010)
     */
    void review(UUID applicationId, ReviewDecision decision, String reason, String reviewerUsername);
}

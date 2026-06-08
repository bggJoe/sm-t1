package com.example.backend.admin.application;

import com.example.backend.account.CreateAccountUseCase;
import com.example.backend.registration.ListPendingApplicationsUseCase;
import com.example.backend.registration.ReviewApplicationUseCase;
import com.example.backend.registration.ReviewDecision;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Admin Application Service: coordinates the two-stage account provisioning process.
 * DEC-021: two separate transactions; no cross-module @Transactional.
 * Does not contain any business rules — only orchestrates UseCase calls.
 *
 * AC-011: Stage 1 — mark application as APPROVED (registration module, Tx #1)
 * AC-012: Stage 2 — create ACTIVE account (account module, Tx #2)
 * AC-013: Stage 2 failure is caught and reported without reverting Stage 1
 */
@Service
public class AdminApplicationService {

    private final ListPendingApplicationsUseCase listApplications;
    private final ReviewApplicationUseCase reviewApplication;
    private final CreateAccountUseCase createAccount;

    public AdminApplicationService(
            ListPendingApplicationsUseCase listApplications,
            ReviewApplicationUseCase reviewApplication,
            CreateAccountUseCase createAccount) {
        this.listApplications = listApplications;
        this.reviewApplication = reviewApplication;
        this.createAccount = createAccount;
    }

    /**
     * Processes a review decision.
     *
     * APPROVE path (DEC-021 two-stage):
     *   Stage 1 (Tx #1): approve the registration application
     *   Stage 2 (Tx #2): create account — failure observable in result, not propagated
     *
     * REJECT path:
     *   Single call to reviewApplicationUseCase; accountCreated always false.
     *
     * @param applicationId  the application to review
     * @param decision       APPROVE or REJECT
     * @param reason         required when decision == REJECT (INV-010)
     * @param reviewerUsername JWT sub claim of the reviewing admin
     * @return ProvisioningResult with both stage outcomes
     * @throws IllegalArgumentException if application not found
     */
    public ProvisioningResult processReview(
            UUID applicationId, ReviewDecision decision, String reason, String reviewerUsername) {

        if (decision != ReviewDecision.APPROVE) {
            reviewApplication.review(applicationId, decision, reason, reviewerUsername);
            return new ProvisioningResult(decision.name(), false);
        }

        String email = listApplications.findById(applicationId)
                .map(summary -> summary.email())
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));

        // Stage 1 — Tx #1: approve the registration application
        reviewApplication.review(applicationId, ReviewDecision.APPROVE, null, reviewerUsername);

        // Stage 2 — Tx #2: create account; failure is observable, not propagated
        boolean accountCreated;
        try {
            createAccount.create(email);
            accountCreated = true;
        } catch (Exception e) {
            accountCreated = false;
        }

        return new ProvisioningResult("APPROVED", accountCreated);
    }
}

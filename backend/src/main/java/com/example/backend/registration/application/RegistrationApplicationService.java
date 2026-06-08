package com.example.backend.registration.application;

import com.example.backend.registration.AccountExistencePort;
import com.example.backend.registration.ApplicationSummary;
import com.example.backend.registration.ListPendingApplicationsUseCase;
import com.example.backend.registration.ReviewApplicationUseCase;
import com.example.backend.registration.ReviewDecision;
import com.example.backend.registration.domain.ApplicationStatus;
import com.example.backend.registration.domain.RegistrationApplication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Transactional
public class RegistrationApplicationService
        implements ListPendingApplicationsUseCase, ReviewApplicationUseCase {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final RegistrationApplicationRepository repository;
    private final AccountExistencePort accountExistencePort;

    public RegistrationApplicationService(
            RegistrationApplicationRepository repository,
            AccountExistencePort accountExistencePort) {
        this.repository = repository;
        this.accountExistencePort = accountExistencePort;
    }

    public void submitApplication(SubmitApplicationCommand cmd) {
        // INV-003: email and name must not be blank
        if (cmd.email() == null || cmd.email().isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        if (cmd.name() == null || cmd.name().isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        // INV-004: email must match basic RFC 5322 format (backend enforcement)
        if (!EMAIL_PATTERN.matcher(cmd.email()).matches()) {
            throw new IllegalArgumentException("email format is invalid: " + cmd.email());
        }
        // INV-001: no existing PENDING application with same email
        if (repository.findPendingByEmail(cmd.email()).isPresent()) {
            throw new IllegalStateException(
                    "a pending application already exists for email: " + cmd.email());
        }
        // INV-001: no approved account with same email (via SEAM)
        if (accountExistencePort.hasApprovedAccount(cmd.email())) {
            throw new IllegalStateException(
                    "an approved account already exists for email: " + cmd.email());
        }
        // INV-002: RegistrationApplication constructor always sets status = PENDING
        repository.save(new RegistrationApplication(cmd.email(), cmd.name()));
    }

    // --- ListPendingApplicationsUseCase ---

    @Override
    @Transactional(readOnly = true)
    public List<ApplicationSummary> listPending() {
        return repository.findAllByStatus(ApplicationStatus.PENDING).stream()
                .map(a -> new ApplicationSummary(a.getId(), a.getEmail(), a.getName(), a.getStatus()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ApplicationSummary> findById(UUID id) {
        return repository.findById(id)
                .map(a -> new ApplicationSummary(a.getId(), a.getEmail(), a.getName(), a.getStatus()));
    }

    // --- ReviewApplicationUseCase ---

    @Override
    public void review(UUID applicationId, ReviewDecision decision, String reason, String reviewerUsername) {
        RegistrationApplication app = repository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));

        if (decision == ReviewDecision.APPROVE) {
            app.approve(reviewerUsername);   // INV-008/009 enforced in entity
        } else {
            app.reject(reason, reviewerUsername); // INV-008/009/010 enforced in entity
        }
        repository.save(app);
    }
}


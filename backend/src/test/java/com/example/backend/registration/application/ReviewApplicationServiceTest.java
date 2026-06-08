package com.example.backend.registration.application;

import com.example.backend.registration.AccountExistencePort;
import com.example.backend.registration.ReviewApplicationUseCase;
import com.example.backend.registration.ReviewDecision;
import com.example.backend.registration.domain.ApplicationStatus;
import com.example.backend.registration.domain.RegistrationApplication;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * INV tests for SLICE-003: Application review (approve / reject).
 * Pure unit tests — no Spring context, no DB.
 * Each method name matches the `對應測試` field in SLICE-003.md.
 */
class ReviewApplicationServiceTest {

    private final RegistrationApplicationRepository repository = mock(RegistrationApplicationRepository.class);
    private final AccountExistencePort accountExistencePort = mock(AccountExistencePort.class);
    private final ReviewApplicationUseCase service =
            new RegistrationApplicationService(repository, accountExistencePort);

    /**
     * INV-008: Attempting to review a non-PENDING application must throw IllegalStateException.
     * APPROVED and REJECTED are terminal states.
     */
    @Test
    void inv008_cannotReviewNonPendingApplication() {
        RegistrationApplication approved = new RegistrationApplication("a@example.com", "Alice");
        approved.approve("admin");    // moves to APPROVED

        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(approved));

        assertThrows(IllegalStateException.class,
                () -> service.review(id, ReviewDecision.APPROVE, null, "admin2"));

        verify(repository, never()).save(any());
    }

    /**
     * INV-009: After approve, reviewedBy must equal the supplied reviewerUsername.
     */
    @Test
    void inv009_reviewerIdentityIsRecorded() {
        RegistrationApplication app = new RegistrationApplication("b@example.com", "Bob");
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(app));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.review(id, ReviewDecision.APPROVE, null, "reviewer-alice");

        var captor = ArgumentCaptor.forClass(RegistrationApplication.class);
        verify(repository).save(captor.capture());
        assertEquals("reviewer-alice", captor.getValue().getReviewedBy());
        assertEquals(ApplicationStatus.APPROVED, captor.getValue().getStatus());
    }

    /**
     * INV-010: reject with blank reason must throw IllegalArgumentException before any state change.
     */
    @Test
    void inv010_rejectReasonMustNotBeBlank() {
        RegistrationApplication app = new RegistrationApplication("c@example.com", "Carol");
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(app));

        assertThrows(IllegalArgumentException.class,
                () -> service.review(id, ReviewDecision.REJECT, "  ", "admin"));

        // Status must remain PENDING — no save should have been called
        assertEquals(ApplicationStatus.PENDING, app.getStatus());
        verify(repository, never()).save(any());
    }
}

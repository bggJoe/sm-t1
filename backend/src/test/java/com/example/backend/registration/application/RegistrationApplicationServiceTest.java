package com.example.backend.registration.application;

import com.example.backend.registration.AccountExistencePort;
import com.example.backend.registration.domain.ApplicationStatus;
import com.example.backend.registration.domain.RegistrationApplication;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * INV tests for SLICE-001: Registration Application submission.
 * Pure unit tests — no Spring context, no DB.
 * Each method name matches the `對應測試` field in SLICE-001.md.
 */
class RegistrationApplicationServiceTest {

    private final RegistrationApplicationRepository repository = mock(RegistrationApplicationRepository.class);
    private final AccountExistencePort accountExistencePort = mock(AccountExistencePort.class);
    private final RegistrationApplicationService service =
            new RegistrationApplicationService(repository, accountExistencePort);

    /** INV-001 (part 1): duplicate PENDING email must be rejected. */
    @Test
    void inv001_duplicatePendingEmailRejected() {
        when(repository.findPendingByEmail("foo@example.com"))
                .thenReturn(Optional.of(new RegistrationApplication("foo@example.com", "Foo")));

        assertThrows(IllegalStateException.class,
                () -> service.submitApplication(new SubmitApplicationCommand("foo@example.com", "Foo")));
        verify(repository, never()).save(any());
    }

    /** INV-001 (part 2): email with existing APPROVED account must be rejected (via AccountExistencePort). */
    @Test
    void inv001_alreadyApprovedEmailRejected() {
        when(repository.findPendingByEmail("foo@example.com")).thenReturn(Optional.empty());
        when(accountExistencePort.hasApprovedAccount("foo@example.com")).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> service.submitApplication(new SubmitApplicationCommand("foo@example.com", "Foo")));
        verify(repository, never()).save(any());
    }

    /** INV-002: newly saved application must have status PENDING. */
    @Test
    void inv002_newApplicationMustBePending() {
        when(repository.findPendingByEmail("new@example.com")).thenReturn(Optional.empty());
        when(accountExistencePort.hasApprovedAccount("new@example.com")).thenReturn(false);

        service.submitApplication(new SubmitApplicationCommand("new@example.com", "New User"));

        var captor = ArgumentCaptor.forClass(RegistrationApplication.class);
        verify(repository).save(captor.capture());
        assertEquals(ApplicationStatus.PENDING, captor.getValue().getStatus());
    }

    /** INV-003: blank email or blank name must be rejected before any persistence. */
    @Test
    void inv003_blankEmailOrNameRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.submitApplication(new SubmitApplicationCommand("", "Name")));
        assertThrows(IllegalArgumentException.class,
                () -> service.submitApplication(new SubmitApplicationCommand("foo@example.com", "")));
        verify(repository, never()).save(any());
    }

    /** INV-004: invalid email format must be rejected at backend (bypass-frontend scenario). */
    @Test
    void inv004_invalidEmailFormatRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.submitApplication(new SubmitApplicationCommand("notanemail", "Name")));
        verify(repository, never()).save(any());
    }
}

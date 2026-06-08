package com.example.backend.admin.application;

import com.example.backend.admin.domain.AdminCredential;
import com.example.backend.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * INV tests for SLICE-002: Admin authentication.
 * Pure unit tests — no Spring context, no DB.
 */
class AdminAuthServiceTest {

    private AdminCredentialRepository repository;
    private JwtTokenProvider jwtTokenProvider;
    private PasswordEncoder passwordEncoder;
    private AdminAuthService service;

    @BeforeEach
    void setUp() {
        repository = mock(AdminCredentialRepository.class);
        jwtTokenProvider = mock(JwtTokenProvider.class);
        passwordEncoder = new BCryptPasswordEncoder();
        service = new AdminAuthService(repository, jwtTokenProvider, passwordEncoder);
    }

    /**
     * INV-005: Invalid credentials (wrong password or unknown username) must be rejected.
     */
    @Test
    void inv005_invalidCredentialsRejected() {
        String correctHash = passwordEncoder.encode("correctPassword");
        AdminCredential credential = new AdminCredential("admin", correctHash);
        when(repository.findByUsername("admin")).thenReturn(Optional.of(credential));

        assertThatThrownBy(() -> service.authenticate(new AuthenticateCommand("admin", "wrongPassword")))
                .isInstanceOf(BadCredentialsException.class);

        verify(jwtTokenProvider, never()).issueToken(any());
    }

    /**
     * INV-005 (unknown user): unknown username must also be rejected without issuing a token.
     */
    @Test
    void inv005_unknownUsernameRejected() {
        when(repository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authenticate(new AuthenticateCommand("ghost", "any")))
                .isInstanceOf(BadCredentialsException.class);

        verify(jwtTokenProvider, never()).issueToken(any());
    }

    /**
     * INV-006: Successfully issued JWT must contain sub (username), exp, and iat.
     * Verification via JwtTokenProvider (real impl would encode; we verify issueToken is called
     * with correct username and that the returned token equals what JwtTokenProvider produced).
     */
    @Test
    void inv006_jwtIssuedWithCorrectSubject() {
        String correctHash = passwordEncoder.encode("correctPassword");
        AdminCredential credential = new AdminCredential("admin", correctHash);
        when(repository.findByUsername("admin")).thenReturn(Optional.of(credential));
        when(jwtTokenProvider.issueToken("admin")).thenReturn("mock.jwt.token");

        String token = service.authenticate(new AuthenticateCommand("admin", "correctPassword"));

        assertThat(token).isEqualTo("mock.jwt.token");
        verify(jwtTokenProvider).issueToken("admin");
    }
}

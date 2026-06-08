package com.example.backend.account.application;

import com.example.backend.account.domain.Account;
import com.example.backend.account.domain.AccountStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * INV tests for SLICE-004 account creation.
 * Pure unit tests — no Spring context, no DB.
 */
class CreateAccountServiceTest {

    private AccountRepository accountRepository;
    private PasswordEncoder passwordEncoder;
    private AccountApplicationService service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        service = new AccountApplicationService(accountRepository, passwordEncoder);
    }

    /**
     * INV-011: email must be unique — creating an account with an existing email throws.
     */
    @Test
    void inv011_emailMustBeUnique() {
        when(accountRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create("existing@example.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("existing@example.com");

        verify(accountRepository, never()).save(any());
    }

    /**
     * INV-012: passwordHash stored in Account must be a BCrypt hash, not plaintext.
     */
    @Test
    void inv012_passwordHashMustBeBcrypt() {
        when(accountRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create("new@example.com");

        verify(accountRepository).save(argThat(account -> {
            String hash = account.getPasswordHash();
            assertThat(hash).startsWith("$2a$").as("passwordHash must be BCrypt encoded");
            assertThat(hash).isNotEqualTo("123456").as("passwordHash must not be plaintext");
            return true;
        }));
    }

    /**
     * INV-013: Account initial status must be ACTIVE.
     */
    @Test
    void inv013_initialStatusMustBeActive() {
        when(accountRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create("new@example.com");

        verify(accountRepository).save(argThat(account -> {
            assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
            return true;
        }));
    }
}

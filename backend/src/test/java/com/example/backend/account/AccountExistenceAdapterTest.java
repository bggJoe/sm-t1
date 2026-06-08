package com.example.backend.account;

import com.example.backend.account.application.AccountRepository;
import com.example.backend.account.domain.Account;
import com.example.backend.account.infrastructure.JpaAccountExistenceAdapter;
import com.example.backend.registration.AccountExistencePort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * INV-014: JpaAccountExistenceAdapter must accurately reflect actual account state.
 * Slice test — only JPA context; no full Spring context required.
 */
@DataJpaTest
@Import(JpaAccountExistenceAdapter.class)
class AccountExistenceAdapterTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountExistencePort accountExistencePort;

    /**
     * INV-014: existsByEmail returns true when account exists, false when it does not.
     * Both directions must be verified.
     */
    @Test
    void inv014_existsByEmailReflectsActualAccountState() {
        String email = "test-" + System.nanoTime() + "@example.com";
        String hash = new BCryptPasswordEncoder().encode("123456");

        // Before creation — must return false
        assertThat(accountExistencePort.hasApprovedAccount(email))
                .as("should return false before account is created")
                .isFalse();

        // Create account
        accountRepository.save(new Account(email, hash));

        // After creation — must return true
        assertThat(accountExistencePort.hasApprovedAccount(email))
                .as("should return true after account is created")
                .isTrue();
    }
}

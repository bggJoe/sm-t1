package com.example.backend.account.infrastructure;

import com.example.backend.account.application.AccountRepository;
import com.example.backend.registration.AccountExistencePort;
import org.springframework.stereotype.Component;

/**
 * SEAM-001 Real implementation: replaces StubAccountExistenceAdapter.
 *
 * Implements registration module's AccountExistencePort by querying account DB.
 * INV-014: existsByEmail must accurately reflect actual account state.
 *
 * Cross-module adapter: lives in account/infrastructure but implements a port
 * defined in registration root package (DEC-020).
 */
@Component
public class JpaAccountExistenceAdapter implements AccountExistencePort {

    private final AccountRepository accountRepository;

    public JpaAccountExistenceAdapter(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public boolean hasApprovedAccount(String email) {
        return accountRepository.existsByEmail(email);
    }
}

package com.example.backend.account.application;

import com.example.backend.account.CreateAccountUseCase;
import com.example.backend.account.domain.Account;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application Service for account creation.
 * Implements CreateAccountUseCase (SLICE-004, DEC-021).
 *
 * Enforces:
 *   INV-011: rejects duplicate email
 *   INV-012: encodes password with BCrypt before storing
 *   INV-013: initial status is always ACTIVE (delegated to Account constructor)
 */
@Service
@Transactional
public class AccountApplicationService implements CreateAccountUseCase {

    static final String DEFAULT_PASSWORD = "123456";

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountApplicationService(
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Creates a new ACTIVE account with the given email and a BCrypt-hashed default password.
     *
     * @throws IllegalStateException if email already has an existing account (INV-011)
     */
    @Override
    public void create(String email) {
        if (accountRepository.existsByEmail(email)) {
            throw new IllegalStateException("Account already exists for email: " + email);
        }
        String hash = passwordEncoder.encode(DEFAULT_PASSWORD);
        accountRepository.save(new Account(email, hash));
    }
}

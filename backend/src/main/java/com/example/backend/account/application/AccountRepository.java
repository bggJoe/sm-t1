package com.example.backend.account.application;

import com.example.backend.account.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Port interface for Account persistence.
 * Infrastructure implementation: JpaAccountRepository.
 */
public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByEmail(String email);
    boolean existsByEmail(String email);
}

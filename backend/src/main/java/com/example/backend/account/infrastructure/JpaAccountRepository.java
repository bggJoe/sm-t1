package com.example.backend.account.infrastructure;

import com.example.backend.account.application.AccountRepository;
import com.example.backend.account.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data JPA implementation of AccountRepository.
 */
public interface JpaAccountRepository extends AccountRepository, JpaRepository<Account, UUID> {
}

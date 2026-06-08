package com.example.backend.registration;

/**
 * Cross-module port: queries whether an email already has an approved account.
 * Promoted to registration root package (DEC-020, SLICE-004) so account module
 * can implement it without accessing registration internals.
 *
 * SEAM-001: Real implementation is JpaAccountExistenceAdapter in account module.
 * See DEC-005.
 */
public interface AccountExistencePort {
    boolean hasApprovedAccount(String email);
}

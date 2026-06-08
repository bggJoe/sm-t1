package com.example.backend.account;

/**
 * Cross-module public API: creates a new ACTIVE account.
 * Defined in account root package per DEC-020.
 * Called by admin module (DEC-021, SLICE-004 Stage 2).
 *
 * Enforces INV-011 (email uniqueness), INV-012 (BCrypt hash), INV-013 (ACTIVE status).
 *
 * @throws IllegalStateException if email already has an existing account (INV-011)
 */
public interface CreateAccountUseCase {
    void create(String email);
}

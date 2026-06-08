package com.example.backend.account.domain;

/**
 * Account lifecycle states.
 * ACTIVE: account can be used for login.
 * SUSPENDED: account is blocked (future SLICE).
 */
public enum AccountStatus {
    ACTIVE,
    SUSPENDED
}

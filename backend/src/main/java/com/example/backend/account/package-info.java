/**
 * Account module — manages Account lifecycle (creation, status).
 *
 * Public API (root package, DEC-020):
 *   - CreateAccountUseCase: cross-module interface called by admin module
 *
 * Internal sub-packages (module-private):
 *   - application/: AccountApplicationService, AccountRepository (Port)
 *   - domain/:      Account entity, AccountStatus enum
 *   - infrastructure/: JpaAccountRepository, JpaAccountExistenceAdapter
 *
 * Cross-module provided services:
 *   - CreateAccountUseCase (called by admin)
 *   - JpaAccountExistenceAdapter implements registration.AccountExistencePort (SEAM-001)
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Account"
)
package com.example.backend.account;

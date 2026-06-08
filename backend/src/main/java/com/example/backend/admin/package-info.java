/**
 * Admin module — admin authentication business logic.
 *
 * Public API: classes in this package only.
 * Internal sub-packages (web, application, domain, infrastructure) are module-private.
 *
 * Cross-module dependencies (DEC-019, DEC-014, DEC-021):
 *   - security: JwtTokenProvider, PublicPathContributor
 *   - registration: UseCase interfaces in registration root package (ListPendingApplicationsUseCase, ReviewApplicationUseCase)
 *   - account: CreateAccountUseCase (SLICE-004 — AdminApplicationService Stage 2)
 * Dependencies are one-way; those modules have no knowledge of admin.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Admin Portal",
        allowedDependencies = {"security", "registration", "account"}
)
package com.example.backend.admin;

/**
 * Security module — cross-cutting JWT authentication infrastructure.
 *
 * Public API (root package):
 *   - JwtTokenProvider: JWT issuance and verification
 *   - PublicPathContributor: interface for modules to declare their PUBLIC endpoints
 *
 * Internal (sub-packages, module-private):
 *   - infrastructure: SecurityConfig, JwtAuthFilter
 *
 * Other modules interact with this module only via root-package interfaces.
 * SecurityFilterChain must ONLY be declared here (see ARCH-RULE-006).
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Security"
)
package com.example.backend.security;

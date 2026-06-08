/**
 * Registration module — named interface boundary.
 *
 * This module owns the registration application lifecycle:
 *   - Accepting registration applications (SLICE-001)
 *   - Review and approval (SLICE-002)
 *
 * Public API: classes in this package only.
 * Internal: sub-packages (web, application, infrastructure) are module-private.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Registration"
)
package com.example.backend.registration;

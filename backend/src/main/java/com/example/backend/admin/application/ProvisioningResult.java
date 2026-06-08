package com.example.backend.admin.application;

/**
 * Composite result of the two-stage account provisioning process (DEC-021).
 * Partial failure is observable: caller knows which stage succeeded/failed.
 *
 * AC-011: applicationStatus reflects Stage 1 outcome
 * AC-012: accountCreated reflects Stage 2 outcome
 * AC-013: both fields are independently reported
 */
public record ProvisioningResult(String applicationStatus, boolean accountCreated) {
}

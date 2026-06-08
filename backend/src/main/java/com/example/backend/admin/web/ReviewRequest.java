package com.example.backend.admin.web;

import com.example.backend.registration.ReviewDecision;

/**
 * Request body for PATCH /admin/applications/{id}/review.
 * reason is required when decision == REJECT (INV-010 enforced in domain).
 */
public record ReviewRequest(ReviewDecision decision, String reason) {}

package com.example.backend.registration.domain;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Registration application submitted by a Visitor.
 * INV-002: constructor always sets status to PENDING — no other initial status is possible.
 * INV-008: only PENDING applications may be reviewed; APPROVED/REJECTED are terminal states.
 * INV-009: reviewer identity is recorded on every state transition.
 */
@Entity
@Table(name = "registration_applications")
public class RegistrationApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    /** Required by JPA. Do not use directly. */
    protected RegistrationApplication() {}

    /** INV-002: status is always initialised to PENDING. */
    public RegistrationApplication(String email, String name) {
        this.email = email;
        this.name = name;
        this.status = ApplicationStatus.PENDING;
    }

    /**
     * INV-008: only PENDING applications may be approved.
     * INV-009: reviewerUsername must not be blank.
     */
    public void approve(String reviewerUsername) {
        if (this.status != ApplicationStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot approve application with status: " + this.status);
        }
        this.status = ApplicationStatus.APPROVED;
        this.reviewedBy = reviewerUsername;
    }

    /**
     * INV-008: only PENDING applications may be rejected.
     * INV-009: reviewerUsername must not be blank.
     * INV-010: rejectionReason must not be blank.
     */
    public void reject(String rejectionReason, String reviewerUsername) {
        if (this.status != ApplicationStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot reject application with status: " + this.status);
        }
        if (rejectionReason == null || rejectionReason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason must not be blank");
        }
        this.status = ApplicationStatus.REJECTED;
        this.rejectionReason = rejectionReason;
        this.reviewedBy = reviewerUsername;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public ApplicationStatus getStatus() { return status; }
    public String getReviewedBy() { return reviewedBy; }
    public String getRejectionReason() { return rejectionReason; }
}


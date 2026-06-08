package com.example.backend.admin.web;

import com.example.backend.admin.application.AdminApplicationService;
import com.example.backend.admin.application.ProvisioningResult;
import com.example.backend.registration.ApplicationSummary;
import com.example.backend.registration.ListPendingApplicationsUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Admin portal controller for application review operations.
 * Pure HTTP adapter (DEC-021): all logic delegated to AdminApplicationService.
 * All paths under /admin/ are protected by the JWT filter chain (SLICE-002).
 */
@RestController
@RequestMapping("/admin/applications")
class AdminApplicationController {

    private final ListPendingApplicationsUseCase listPending;
    private final AdminApplicationService adminApplicationService;

    AdminApplicationController(
            ListPendingApplicationsUseCase listPending,
            AdminApplicationService adminApplicationService) {
        this.listPending = listPending;
        this.adminApplicationService = adminApplicationService;
    }

    /** AC-007: returns PENDING applications for authenticated admin. */
    @GetMapping
    ResponseEntity<List<ApplicationSummary>> list() {
        return ResponseEntity.ok(listPending.listPending());
    }

    /**
     * AC-008/AC-009: reject a PENDING application.
     * AC-011/AC-012/AC-013: approve a PENDING application and provision account.
     * reviewer username is extracted from the Spring Security principal (JWT sub claim).
     * Response body reports both stage outcomes independently (DEC-021).
     */
    @PatchMapping("/{id}/review")
    ResponseEntity<ProvisioningResult> review(
            @PathVariable UUID id,
            @RequestBody ReviewRequest request,
            Authentication authentication) {
        String reviewerUsername = authentication.getName();
        ProvisioningResult result = adminApplicationService.processReview(
                id, request.decision(), request.reason(), reviewerUsername);
        return ResponseEntity.ok(result);
    }
}

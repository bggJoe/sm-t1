package com.example.backend.admin;

import com.example.backend.account.application.AccountRepository;
import com.example.backend.admin.application.AdminCredentialRepository;
import com.example.backend.admin.domain.AdminCredential;
import com.example.backend.registration.application.RegistrationApplicationRepository;
import com.example.backend.registration.domain.RegistrationApplication;
import com.example.backend.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceptance tests for SLICE-003: Admin application review.
 *
 * Integration tests — loads full Spring context (Security filter chain, JPA, H2).
 * Seeds its own data in @BeforeEach. Each test is self-contained.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminReviewIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminCredentialRepository credentialRepository;

    @Autowired
    private RegistrationApplicationRepository applicationRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminUsername;
    private String validToken;

    @BeforeEach
    void seed() {
        adminUsername = "review-admin-" + System.nanoTime();
        credentialRepository.save(new AdminCredential(adminUsername, passwordEncoder.encode("secret")));
        validToken = jwtTokenProvider.issueToken(adminUsername);
    }

    /**
     * AC-007: Authenticated admin can list PENDING applications; APPROVED ones are excluded.
     *
     * Given:  2 PENDING applications + 1 APPROVED application in DB
     * When:   GET /admin/applications with valid JWT
     * Then:   200 OK; body is array of 2 entries; each has id, email, name, status=PENDING
     */
    @Test
    void ac007_adminCanListPendingApplications() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        RegistrationApplication pending1 = applicationRepository.save(
                new RegistrationApplication("pending1-" + suffix + "@example.com", "Pending One"));
        RegistrationApplication pending2 = applicationRepository.save(
                new RegistrationApplication("pending2-" + suffix + "@example.com", "Pending Two"));
        RegistrationApplication approved = new RegistrationApplication("approved-" + suffix + "@example.com", "Approved");
        approved.approve(adminUsername);
        applicationRepository.save(approved);

        MvcResult result = mockMvc.perform(get("/admin/applications")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        List<Object> list = objectMapper.readValue(body, List.class);

        // At minimum our 2 PENDING entries are present; other test runs may add more
        assertThat(list.size()).isGreaterThanOrEqualTo(2);
        // Verify none of the returned items are our approved entry by checking content
        assertThat(body).contains(pending1.getId().toString());
        assertThat(body).contains(pending2.getId().toString());
        assertThat(body).doesNotContain(approved.getId().toString());
    }

    /**
     * AC-008: Admin approves a PENDING application → status becomes APPROVED, reviewedBy recorded.
     *
     * Given:  1 PENDING application in DB
     * When:   PATCH /admin/applications/{id}/review {"decision":"APPROVE"} with valid JWT
     * Then:   200 OK; DB record has status=APPROVED, reviewedBy=adminUsername
     */
    @Test
    void ac008_adminApprovesApplication() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        RegistrationApplication app = applicationRepository.save(
                new RegistrationApplication("approve-" + suffix + "@example.com", "Approve Me"));

        mockMvc.perform(patch("/admin/applications/" + app.getId() + "/review")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision": "APPROVE"}
                                """))
                .andExpect(status().isOk());

        RegistrationApplication saved = applicationRepository.findById(app.getId()).orElseThrow();
        assertThat(saved.getStatus().name()).isEqualTo("APPROVED");
        assertThat(saved.getReviewedBy()).isEqualTo(adminUsername);
    }

    /**
     * AC-009: Admin rejects a PENDING application with a reason → status becomes REJECTED.
     *
     * Given:  1 PENDING application in DB
     * When:   PATCH /admin/applications/{id}/review {"decision":"REJECT","reason":"資料不完整"} with JWT
     * Then:   200 OK; DB record has status=REJECTED, reviewedBy=adminUsername, rejectionReason not blank
     */
    @Test
    void ac009_adminRejectsApplication() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        RegistrationApplication app = applicationRepository.save(
                new RegistrationApplication("reject-" + suffix + "@example.com", "Reject Me"));

        mockMvc.perform(patch("/admin/applications/" + app.getId() + "/review")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision": "REJECT", "reason": "資料不完整"}
                                """))
                .andExpect(status().isOk());

        RegistrationApplication saved = applicationRepository.findById(app.getId()).orElseThrow();
        assertThat(saved.getStatus().name()).isEqualTo("REJECTED");
        assertThat(saved.getReviewedBy()).isEqualTo(adminUsername);
        assertThat(saved.getRejectionReason()).isNotBlank();
    }

    /**
     * AC-010: Request without JWT is rejected by the filter chain with 401.
     *
     * Given:  1 PENDING application in DB
     * When:   PATCH /admin/applications/{id}/review with NO Authorization header
     * Then:   401 Unauthorized; application status unchanged
     */
    @Test
    void ac010_unauthenticatedReviewIsRejected() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        RegistrationApplication app = applicationRepository.save(
                new RegistrationApplication("unauth-" + suffix + "@example.com", "Unauth"));

        mockMvc.perform(patch("/admin/applications/" + app.getId() + "/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision": "APPROVE"}
                                """))
                .andExpect(status().isUnauthorized());

        RegistrationApplication unchanged = applicationRepository.findById(app.getId()).orElseThrow();
        assertThat(unchanged.getStatus().name()).isEqualTo("PENDING");
    }

    /**
     * AC-011 / AC-012: Admin approves a PENDING application → application APPROVED + Account created.
     *
     * Given:  1 PENDING application in DB
     * When:   PATCH /admin/applications/{id}/review {"decision":"APPROVE"} with valid JWT
     * Then:   200 OK; response body {applicationStatus:APPROVED, accountCreated:true};
     *         DB: application status=APPROVED; Account with same email exists
     */
    @Test
    void ac011_ac012_approveCreatesAccountAndApproves() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String email = "provision-" + suffix + "@example.com";
        RegistrationApplication app = applicationRepository.save(
                new RegistrationApplication(email, "Provision Me"));

        MvcResult result = mockMvc.perform(patch("/admin/applications/" + app.getId() + "/review")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision": "APPROVE"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"applicationStatus\":\"APPROVED\"");
        assertThat(body).contains("\"accountCreated\":true");

        RegistrationApplication savedApp = applicationRepository.findById(app.getId()).orElseThrow();
        assertThat(savedApp.getStatus().name()).isEqualTo("APPROVED");

        assertThat(accountRepository.existsByEmail(email)).isTrue();
    }

    /**
     * AC-013: Stage 2 failure (duplicate email) is observable in response without reverting Stage 1.
     *
     * Given:  1 PENDING application + an Account with the SAME email already in DB
     * When:   PATCH /admin/applications/{id}/review {"decision":"APPROVE"} with valid JWT
     * Then:   200 OK; response body {applicationStatus:APPROVED, accountCreated:false};
     *         DB: application status=APPROVED (Stage 1 committed); no new Account created
     */
    @Test
    void ac013_partialFailureObservable_stage2FailsDoesNotRevertStage1() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String email = "duplicate-" + suffix + "@example.com";
        RegistrationApplication app = applicationRepository.save(
                new RegistrationApplication(email, "Duplicate Email"));

        // Pre-seed an Account with the same email to trigger Stage 2 failure
        String hash = passwordEncoder.encode("123456");
        accountRepository.save(new com.example.backend.account.domain.Account(email, hash));

        MvcResult result = mockMvc.perform(patch("/admin/applications/" + app.getId() + "/review")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision": "APPROVE"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"applicationStatus\":\"APPROVED\"");
        assertThat(body).contains("\"accountCreated\":false");

        // Stage 1 committed — application is APPROVED even though Stage 2 failed
        RegistrationApplication savedApp = applicationRepository.findById(app.getId()).orElseThrow();
        assertThat(savedApp.getStatus().name()).isEqualTo("APPROVED");
    }
}

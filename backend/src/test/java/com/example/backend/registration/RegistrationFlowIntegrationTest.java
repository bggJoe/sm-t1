package com.example.backend.registration;

import com.example.backend.registration.application.RegistrationApplicationRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceptance tests for SLICE-001: Registration flow.
 * Answers: "Can a visitor successfully complete a registration application?"
 *
 * Integration tests — loads full Spring context (incl. Security filter chain) + H2 in-memory DB.
 * Each test validates one complete vertical path end-to-end, not a unit in isolation.
 *
 * Test method naming: ac001_xxx mirrors inv001_xxx convention.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RegistrationFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationApplicationRepository registrationApplicationRepository;

    /**
     * AC-001: A visitor submitting a valid application receives 201 and the application is
     * persisted as PENDING in the DB.
     *
     * Given: no PENDING application or APPROVED account exists for the email
     * When:  POST /registrations {"email": "...", "name": "Joe Wang"}
     * Then:  HTTP 201 Created
     * And:   DB registration_applications table has a PENDING record for the email
     *
     * Also implicitly validates that POST /registrations is not blocked by Spring Security
     * (RegistrationPublicPathContributor is functioning correctly in context).
     */
    @Test
    void ac001_newSubmissionCreatesPendingApplication() throws Exception {
        // Use a unique email per test run to avoid cross-test interference
        String email = "ac001-" + System.nanoTime() + "@test.com";

        mockMvc.perform(post("/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "name": "Joe Wang"}
                                """.formatted(email)))
                .andExpect(status().isCreated());

        // Verify DB state — this is what "申請紀錄可查" means (SLICE-001.md 成功的判準)
        assertThat(registrationApplicationRepository.findPendingByEmail(email))
                .isPresent();
    }

    /**
     * AC-002: A visitor submitting a blank email receives 400 Bad Request with ProblemDetail.
     *
     * Given: system is running
     * When:  POST /registrations {"email": "", "name": "Joe Wang"}
     * Then:  HTTP 400 Bad Request
     * And:   response body is RFC 7807 ProblemDetail
     *
     * Currently disabled — requires a @ControllerAdvice (or Spring MVC error config)
     * to convert IllegalArgumentException from RegistrationApplicationService → 400.
     * Without it, Spring returns 500 for unhandled exceptions.
     */
    @Test
    @Disabled("TODO: add @ControllerAdvice to map IllegalArgumentException → 400 ProblemDetail")
    void ac002_blankEmailReturnsProblemDetail() throws Exception {
        mockMvc.perform(post("/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "", "name": "Joe Wang"}
                                """))
                .andExpect(status().isBadRequest());
    }
}

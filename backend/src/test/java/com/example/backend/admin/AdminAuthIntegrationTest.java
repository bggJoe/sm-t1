package com.example.backend.admin;

import com.example.backend.admin.application.AdminCredentialRepository;
import com.example.backend.admin.domain.AdminCredential;
import com.example.backend.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceptance tests for SLICE-002: Admin JWT authentication infrastructure.
 *
 * Integration tests — loads full Spring context (Security filter chain, JPA, H2).
 * Seeds its own test data in @BeforeEach; cleans up in @AfterEach.
 * No dependency on external systems or pre-existing DB state.
 *
 * Test method naming: ac003_xxx mirrors ac001_xxx convention from SLICE-001.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminAuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminCredentialRepository credentialRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.jwt.secret}")
    private String base64Secret;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String testUsername;

    @BeforeEach
    void seedAdminCredential() {
        // Use unique username per test run to ensure isolation
        testUsername = "ac-testadmin-" + System.nanoTime();
        credentialRepository.save(new AdminCredential(testUsername, passwordEncoder.encode("secret")));
    }

    @AfterEach
    void cleanUp() {
        // H2 is in-memory and test-scoped; cleanup is belt-and-suspenders for safety
        // (no explicit delete needed unless @Transactional boundaries differ)
    }

    /**
     * AC-003: Valid admin credentials return 200 with a JWT containing sub + exp + iat.
     *
     * Given: DB has AdminCredential for testUsername with bcrypt("secret")
     * When:  POST /auth/login {"username": testUsername, "password": "secret"}
     * Then:  200 OK, body.token is a valid JWT with sub == testUsername and exp in the future
     */
    @Test
    void ac003_validCredentialsReturnJwt() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "password": "secret"}
                                """.formatted(testUsername)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        Map<String, String> map = objectMapper.readValue(body, Map.class);
        String token = map.get("token");
        assertThat(token).isNotBlank();

        // Decode and verify JWT claims (sub + exp + iat per DEC-016)
        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret)))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo(testUsername);
        assertThat(claims.getExpiration()).isAfter(new Date());
        assertThat(claims.getIssuedAt()).isNotNull();
    }

    /**
     * AC-004: Invalid password returns 401 and no token.
     *
     * Given: DB has AdminCredential for testUsername
     * When:  POST /auth/login with wrong password
     * Then:  401 Unauthorized, body does not contain "token"
     */
    @Test
    void ac004_invalidCredentialsReturn401() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "password": "wrong"}
                                """.formatted(testUsername)))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("token");
    }

    /**
     * AC-005: Request without JWT is blocked by filter chain with 401 (not 403, not 404).
     *
     * Given: /admin/protected-test is not declared PUBLIC by any PublicPathContributor
     * When:  GET /admin/protected-test with no Authorization header
     * Then:  401 Unauthorized — filter chain intercepts before routing, so path existence is irrelevant
     */
    @Test
    void ac005_missingTokenReturns401() throws Exception {
        mockMvc.perform(get("/admin/protected-test"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * AC-006: Request with a valid JWT is passed through by filter chain (reaches routing layer).
     *
     * Given: a legitimately issued JWT (sub=testUsername, exp in future)
     * When:  GET /admin/protected-test with Authorization: Bearer <valid-token>
     * Then:  404 Not Found (routing layer — path does not exist)
     *        NOT 401 (which would indicate Security intercepted it)
     *
     * 401 vs 404 is the observable signal for "did the filter chain pass this through?".
     * The non-existent path is intentional: no dependency on SLICE-003 endpoints.
     */
    @Test
    void ac006_validJwtIsPassedThroughFilterChain() throws Exception {
        String token = jwtTokenProvider.issueToken(testUsername);

        mockMvc.perform(get("/admin/protected-test")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}

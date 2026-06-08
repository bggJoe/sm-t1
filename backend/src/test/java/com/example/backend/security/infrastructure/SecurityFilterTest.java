package com.example.backend.security.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * INV-007: Protected endpoints must reject requests without a valid JWT.
 *
 * Integration test — loads full Spring context with Security filter chain.
 * Uses a path that is not registered as PUBLIC in any PublicPathContributor,
 * so Spring Security's default-protect-all policy applies.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityFilterTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * INV-007 (missing token): protected endpoint returns 401 with no Authorization header.
     */
    @Test
    void inv007_protectedEndpointRejectsUnauthenticated() throws Exception {
        mockMvc.perform(get("/admin/protected-test"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * INV-007 (invalid token): protected endpoint returns 401 with a garbage Bearer token.
     */
    @Test
    void inv007_protectedEndpointRejectsInvalidToken() throws Exception {
        mockMvc.perform(get("/admin/protected-test")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized());
    }
}

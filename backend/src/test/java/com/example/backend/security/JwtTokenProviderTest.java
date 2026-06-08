package com.example.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * INV-006: JWT issued by JwtTokenProvider must contain sub + exp + iat. (DEC-016)
 * Pure unit test — no Spring context.
 */
class JwtTokenProviderTest {

    private static final String TEST_SECRET = "aGVsbG9Xb3JsZEZvckpXVFNpZ25pbmdQdXJwb3NlcyE=";
    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "base64Secret", TEST_SECRET);
        ReflectionTestUtils.setField(provider, "expirationMs", 3_600_000L);
    }

    @Test
    void inv006_jwtContainsSubjectExpAndIat() {
        long before = System.currentTimeMillis();
        String token = provider.issueToken("admin");
        long after = System.currentTimeMillis();

        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET)))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        // sub must equal the username
        assertThat(claims.getSubject()).isEqualTo("admin");

        // iat must be present; JWT dates are second-precision, so allow up to 1s below lower bound
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getIssuedAt().getTime()).isBetween(before - 999, after + 1000);

        // exp must be after iat
        assertThat(claims.getExpiration()).isNotNull();
        assertThat(claims.getExpiration()).isAfter(new Date(before));
    }
}

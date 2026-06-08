package com.example.backend.admin.application;

import com.example.backend.admin.domain.AdminCredential;
import com.example.backend.security.JwtTokenProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Orchestrates admin authentication (INV-005, INV-006):
 * - INV-005: only valid credentials (existing username + matching bcrypt password) produce a token
 * - INV-006: issued token contains sub + exp + iat (delegated to JwtTokenProvider)
 */
@Service
public class AdminAuthService {

    private final AdminCredentialRepository credentialRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    public AdminAuthService(
            AdminCredentialRepository credentialRepository,
            JwtTokenProvider jwtTokenProvider,
            PasswordEncoder passwordEncoder
    ) {
        this.credentialRepository = credentialRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * @return signed JWT string on success
     * @throws BadCredentialsException if username not found or password does not match
     */
    public String authenticate(AuthenticateCommand command) {
        AdminCredential credential = credentialRepository.findByUsername(command.username())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(command.password(), credential.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        return jwtTokenProvider.issueToken(command.username());
    }
}

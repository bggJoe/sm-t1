package com.example.backend.admin.web;

import com.example.backend.admin.application.AdminAuthService;
import com.example.backend.admin.application.AuthenticateCommand;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Presentation layer for admin authentication.
 * Path /auth/login is declared PUBLIC via AdminPublicPathContributor (DEC-015).
 */
@RestController
@RequestMapping("/auth")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            String token = adminAuthService.authenticate(
                    new AuthenticateCommand(request.username(), request.password())
            );
            return ResponseEntity.ok(Map.of("token", token));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }
    }
}

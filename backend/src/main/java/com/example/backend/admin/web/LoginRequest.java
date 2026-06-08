package com.example.backend.admin.web;

/**
 * Request DTO for POST /auth/login.
 */
public record LoginRequest(String username, String password) {
}

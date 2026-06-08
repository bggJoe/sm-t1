package com.example.backend.admin.application;

/**
 * Command object for the admin authentication use case.
 */
public record AuthenticateCommand(String username, String password) {
}

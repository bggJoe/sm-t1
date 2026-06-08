package com.example.backend.admin.infrastructure;

import com.example.backend.security.PublicPathContributor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Declares /auth/login as a PUBLIC (unauthenticated) path.
 * SecurityConfig collects this at startup (DEC-015).
 */
@Component
public class AdminPublicPathContributor implements PublicPathContributor {

    @Override
    public List<String> publicPaths() {
        return List.of("/auth/login");
    }
}

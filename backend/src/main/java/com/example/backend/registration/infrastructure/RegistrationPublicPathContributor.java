package com.example.backend.registration.infrastructure;

import com.example.backend.security.PublicPathContributor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Declares /registrations as a PUBLIC (unauthenticated) path.
 * Allows visitors to submit registration applications without a JWT (DEC-015).
 * SecurityConfig collects this at startup.
 */
@Component
public class RegistrationPublicPathContributor implements PublicPathContributor {

    @Override
    public List<String> publicPaths() {
        return List.of("/registrations");
    }
}

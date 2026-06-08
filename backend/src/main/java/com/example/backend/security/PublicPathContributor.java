package com.example.backend.security;

import java.util.List;

/**
 * Module public API: any module that exposes unauthenticated endpoints
 * must implement this interface and declare those paths here.
 *
 * SecurityConfig collects all beans implementing this interface at startup
 * and configures them as permit-all in the SecurityFilterChain (DEC-015).
 */
public interface PublicPathContributor {

    /**
     * Returns the list of URL path patterns that this module considers public
     * (no authentication required). Ant-style patterns are supported, e.g. "/registrations/**".
     */
    List<String> publicPaths();
}

package com.example.backend.security.infrastructure;

import com.example.backend.security.PublicPathContributor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * Security configuration — module-private.
 * Collects all PublicPathContributor beans, then builds a stateless
 * SecurityFilterChain with JwtAuthFilter (DEC-015).
 *
 * Default policy: ALL paths require authentication unless a contributor declares them public.
 */
@Configuration
class SecurityConfig {

    private final List<PublicPathContributor> contributors;
    private final JwtAuthFilter jwtAuthFilter;

    SecurityConfig(List<PublicPathContributor> contributors, JwtAuthFilter jwtAuthFilter) {
        this.contributors = contributors;
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        String[] publicPaths = contributors.stream()
                .flatMap(c -> c.publicPaths().stream())
                .toArray(String[]::new);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(publicPaths).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

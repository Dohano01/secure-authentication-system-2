package com.example.auth_service.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    private final Argon2PasswordEncoder argon2PasswordEncoder;

    public SecurityConfig(Argon2PasswordEncoder argon2PasswordEncoder) {
        this.argon2PasswordEncoder = argon2PasswordEncoder;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // ✅ NOW USING ARGON2 (required by project objectives)
        return argon2PasswordEncoder;
    }

    // Simple security: permit /auth/**, swagger, H2 console
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated()
                )
                // No HTTP Basic Auth - using custom login endpoint
                .sessionManagement(session -> session.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS));
        return http.build();
    }
}
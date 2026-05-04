package com.example.auth_service.security;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Custom Argon2 Password Encoder
 * Argon2id variant - resistant to both side-channel and GPU attacks
 */
@Component
public class Argon2PasswordEncoder implements PasswordEncoder {

    private static final int ITERATIONS = 2;      // Number of iterations
    private static final int MEMORY = 65536;      // Memory cost in KB (64 MB)
    private static final int PARALLELISM = 1;     // Number of threads

    private final Argon2 argon2 = Argon2Factory.create(
            Argon2Factory.Argon2Types.ARGON2id,
            32,  // Salt length
            64   // Hash length
    );

    @Override
    public String encode(CharSequence rawPassword) {
        try {
            return argon2.hash(ITERATIONS, MEMORY, PARALLELISM, rawPassword.toString().toCharArray());
        } catch (Exception e) {
            throw new RuntimeException("Error encoding password", e);
        }
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        try {
            return argon2.verify(encodedPassword, rawPassword.toString().toCharArray());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if password needs rehashing (if security parameters changed)
     */
    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        // Could implement logic to check if hash uses old parameters
        return false;
    }
}

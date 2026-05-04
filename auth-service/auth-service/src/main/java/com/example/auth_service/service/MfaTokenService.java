package com.example.auth_service.service;

import com.example.auth_service.model.User;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to manage temporary MFA tokens during login flow
 * These tokens are used between password verification and MFA code verification
 */
@Service
public class MfaTokenService {

    // In-memory storage for MFA tokens (expires after 5 minutes)
    private final Map<String, MfaTokenData> mfaTokens = new ConcurrentHashMap<>();
    private static final int MFA_TOKEN_EXPIRY_MINUTES = 5;

    public static class MfaTokenData {
        private final User user;
        private final String ipAddress;
        private final String userAgent;
        private final LocalDateTime createdAt;

        public MfaTokenData(User user, String ipAddress, String userAgent) {
            this.user = user;
            this.ipAddress = ipAddress;
            this.userAgent = userAgent;
            this.createdAt = LocalDateTime.now();
        }

        public User getUser() {
            return user;
        }

        public String getIpAddress() {
            return ipAddress;
        }

        public String getUserAgent() {
            return userAgent;
        }

        public boolean isExpired() {
            return LocalDateTime.now().isAfter(createdAt.plusMinutes(MFA_TOKEN_EXPIRY_MINUTES));
        }
    }

    /**
     * Generate a temporary MFA token for login flow
     */
    public String generateMfaToken(User user, String ipAddress, String userAgent) {
        String token = UUID.randomUUID().toString();
        mfaTokens.put(token, new MfaTokenData(user, ipAddress, userAgent));
        return token;
    }

    /**
     * Validate and retrieve MFA token data
     */
    public MfaTokenData validateMfaToken(String token) {
        MfaTokenData data = mfaTokens.get(token);
        if (data == null || data.isExpired()) {
            if (data != null) {
                mfaTokens.remove(token); // Clean up expired token
            }
            return null;
        }
        return data;
    }

    /**
     * Remove MFA token after successful verification
     */
    public void removeMfaToken(String token) {
        mfaTokens.remove(token);
    }

    /**
     * Clean up expired tokens (can be called by scheduled task)
     */
    public void cleanupExpiredTokens() {
        mfaTokens.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
}

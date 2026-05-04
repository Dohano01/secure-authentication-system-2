package com.example.auth_service.service;

import com.example.auth_service.model.RefreshToken;
import com.example.auth_service.model.User;
import com.example.auth_service.repository.RefreshTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class RefreshTokenService {
    
    private final RefreshTokenRepository refreshTokenRepository;
    
    @Value("${jwt.refresh-expiration-days:30}")
    private int refreshExpirationDays;
    
    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }
    
    /**
     * Generate and save a new refresh token
     */
    @Transactional
    public RefreshToken createRefreshToken(User user, String ipAddress, String userAgent, String deviceType) {
        // Generate unique token
        String token = UUID.randomUUID().toString();
        
        // Calculate expiry date
        LocalDateTime expiryDate = LocalDateTime.now().plusDays(refreshExpirationDays);
        
        // Create refresh token
        RefreshToken refreshToken = RefreshToken.builder()
                .token(token)
                .user(user)
                .expiryDate(expiryDate)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .deviceType(deviceType)
                .tokenVersion(user.getTokenVersion())
                .revoked(false)
                .build();
        
        return refreshTokenRepository.save(refreshToken);
    }
    
    /**
     * Validate and return refresh token if valid
     */
    @Transactional
    public Optional<RefreshToken> validateRefreshToken(String token) {
        Optional<RefreshToken> refreshTokenOpt = refreshTokenRepository.findByToken(token);
        
        if (refreshTokenOpt.isEmpty()) {
            return Optional.empty();
        }
        
        RefreshToken refreshToken = refreshTokenOpt.get();
        
        // Check if token is valid
        if (!refreshToken.isValid()) {
            return Optional.empty();
        }
        
        // Check if token version matches user's current version
        User user = refreshToken.getUser();
        if (!refreshToken.getTokenVersion().equals(user.getTokenVersion())) {
            // Token version mismatch - token has been invalidated
            refreshToken.setRevoked(true);
            refreshTokenRepository.save(refreshToken);
            return Optional.empty();
        }
        
        return Optional.of(refreshToken);
    }
    
    /**
     * Revoke a specific refresh token
     */
    @Transactional
    public void revokeToken(String token) {
        refreshTokenRepository.findByToken(token).ifPresent(refreshToken -> {
            refreshToken.setRevoked(true);
            refreshTokenRepository.save(refreshToken);
            log.info("Refresh token revoked for user: {}", refreshToken.getUser().getUsername());
        });
    }
    
    /**
     * Revoke all tokens for a user (logout from all devices)
     */
    @Transactional
    public void revokeAllUserTokens(User user) {
        refreshTokenRepository.revokeAllUserTokens(user);
        log.info("All refresh tokens revoked for user: {}", user.getUsername());
    }
    
    /**
     * Revoke tokens with old token version (after token version increment)
     */
    @Transactional
    public void revokeTokensByVersion(User user, Integer oldTokenVersion) {
        refreshTokenRepository.revokeTokensByVersion(user, oldTokenVersion);
    }
    
    /**
     * Get all active sessions for a user
     */
    public List<RefreshToken> getUserSessions(User user) {
        return refreshTokenRepository.findByUserAndRevokedFalse(user);
    }
    
    /**
     * Clean up expired tokens (can be called by a scheduled task)
     */
    @Transactional
    public void deleteExpiredTokens() {
        refreshTokenRepository.deleteExpiredTokens(LocalDateTime.now());
        log.info("Expired refresh tokens cleaned up");
    }
}

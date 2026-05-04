package com.example.auth_service.service;

import com.example.auth_service.model.SecurityAuditLog;
import com.example.auth_service.repository.SecurityAuditLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class SecurityAuditService {

    private final SecurityAuditLogRepository auditLogRepository;

    public SecurityAuditService(SecurityAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Log a security event
     */
    @Transactional
    public void logEvent(String username, String eventType, String ipAddress,
                         String userAgent, String details, boolean success) {
        SecurityAuditLog auditLog = SecurityAuditLog.builder()
                .username(username)
                .eventType(eventType)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .details(details)
                .success(success)
                .timestamp(LocalDateTime.now())
                .build();

        auditLogRepository.save(auditLog);

        // Also log to application logs (using Slf4j logger)
        log.info("Security Event: {} | User: {} | IP: {} | Success: {}",
                eventType, username, ipAddress, success);
    }

    /**
     * Log successful login
     */
    public void logLoginSuccess(String username, String ipAddress, String userAgent) {
        logEvent(username, SecurityAuditLog.LOGIN_SUCCESS, ipAddress, userAgent,
                "User logged in successfully", true);
    }

    /**
     * Log failed login
     */
    public void logLoginFailed(String username, String ipAddress, String userAgent, String reason) {
        logEvent(username, SecurityAuditLog.LOGIN_FAILED, ipAddress, userAgent,
                "Login failed: " + reason, false);
    }

    /**
     * Log account locked
     */
    public void logAccountLocked(String username, String ipAddress, String reason) {
        logEvent(username, SecurityAuditLog.ACCOUNT_LOCKED, ipAddress, null,
                "Account locked: " + reason, false);
    }

    /**
     * Log account unlocked
     */
    public void logAccountUnlocked(String username) {
        logEvent(username, SecurityAuditLog.ACCOUNT_UNLOCKED, null, null,
                "Account automatically unlocked", true);
    }

    /**
     * Log registration
     */
    public void logRegistration(String username, String ipAddress) {
        logEvent(username, SecurityAuditLog.REGISTRATION, ipAddress, null,
                "New user registered", true);
    }

    /**
     * Get recent failed login attempts for user
     */
    public List<SecurityAuditLog> getRecentFailedAttempts(String username, int minutes) {
        LocalDateTime after = LocalDateTime.now().minusMinutes(minutes);
        return auditLogRepository.findByUsernameAndEventTypeAndTimestampAfter(
                username, SecurityAuditLog.LOGIN_FAILED, after
        );
    }

    /**
     * Get recent failed attempts from IP
     */
    public List<SecurityAuditLog> getRecentFailedAttemptsFromIp(String ipAddress, int minutes) {
        LocalDateTime after = LocalDateTime.now().minusMinutes(minutes);
        return auditLogRepository.findByIpAddressAndEventTypeAndTimestampAfter(
                ipAddress, SecurityAuditLog.LOGIN_FAILED, after
        );
    }

    /**
     * Get user's login history
     */
    public List<SecurityAuditLog> getUserLoginHistory(String username) {
        return auditLogRepository.findByUsernameOrderByTimestampDesc(username);
    }
}
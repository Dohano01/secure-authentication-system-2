package com.example.auth_service.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "security_audit_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
public class SecurityAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username")
    private String username;

    @Column(name = "event_type", nullable = false)
    private String eventType; // LOGIN_SUCCESS, LOGIN_FAILED, ACCOUNT_LOCKED, etc.

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "details", length = 1000)
    private String details;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "success")
    private Boolean success;

    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();
    }

    // Event type constants
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    public static final String ACCOUNT_UNLOCKED = "ACCOUNT_UNLOCKED";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String REGISTRATION = "REGISTRATION";
    public static final String TOKEN_GENERATED = "TOKEN_GENERATED";
    public static final String TOKEN_VALIDATED = "TOKEN_VALIDATED";
    public static final String TOKEN_REFRESH = "TOKEN_REFRESH";
    public static final String SUSPICIOUS_ACTIVITY = "SUSPICIOUS_ACTIVITY";
}
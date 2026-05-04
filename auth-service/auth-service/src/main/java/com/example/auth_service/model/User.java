package com.example.auth_service.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique=true, nullable=false)
    private String username;

    @Column(nullable=false)
    private String password; // stocké hashé avec Argon2

    private String fullName;
    private String email;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name="user_roles",
            joinColumns = @JoinColumn(name="user_id"),
            inverseJoinColumns = @JoinColumn(name="role_id"))
    private Set<Role> roles;

    // ========================================
    // 🔒 BRUTE-FORCE PROTECTION FIELDS
    // ========================================

    @Column(name = "failed_login_attempts")
    private Integer failedLoginAttempts = 0;

    @Column(name = "account_locked")
    private Boolean accountLocked = false;

    @Column(name = "lock_time")
    private LocalDateTime lockTime;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "last_failed_login")
    private LocalDateTime lastFailedLogin;

    // ========================================
    // 🔄 JWT TOKEN ROTATION (for later steps)
    // ========================================

    @Column(name = "token_version")
    private Integer tokenVersion = 0;

    // ========================================
    // 🛡️ SESSION SECURITY (for later steps)
    // ========================================

    @Column(name = "last_login_ip")
    private String lastLoginIp;

    @Column(name = "last_login_device")
    private String lastLoginDevice;

    // ========================================
    // 🔐 MULTI-FACTOR AUTHENTICATION (MFA)
    // ========================================

    @Column(name = "mfa_enabled")
    private Boolean mfaEnabled = false;

    @Column(name = "mfa_secret")
    private String mfaSecret;

    // ========================================
    // HELPER METHODS
    // ========================================

    /**
     * Check if account is currently locked
     */
    public boolean isAccountNonLocked() {
        if (!accountLocked) {
            return true;
        }

        // Auto-unlock after 15 minutes
        if (lockTime != null && LocalDateTime.now().isAfter(lockTime.plusMinutes(15))) {
            this.accountLocked = false;
            this.failedLoginAttempts = 0;
            this.lockTime = null;
            return true;
        }

        return false;
    }

    /**
     * Increment failed login attempts and lock if threshold reached
     */
    public void incrementFailedAttempts() {
        this.failedLoginAttempts++;
        this.lastFailedLogin = LocalDateTime.now();

        // Lock account after 5 failed attempts
        if (this.failedLoginAttempts >= 5) {
            this.accountLocked = true;
            this.lockTime = LocalDateTime.now();
        }
    }

    /**
     * Reset failed attempts on successful login
     */
    public void resetFailedAttempts() {
        this.failedLoginAttempts = 0;
        this.accountLocked = false;
        this.lockTime = null;
        this.lastLogin = LocalDateTime.now();
    }

    /**
     * Increment token version (invalidates all existing tokens)
     */
    public void incrementTokenVersion() {
        this.tokenVersion++;
    }
}
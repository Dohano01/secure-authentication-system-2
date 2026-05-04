package com.example.auth_service.service;

import com.example.auth_service.model.User;
import com.example.auth_service.model.Role;
import com.example.auth_service.repository.UserRepository;
import com.example.auth_service.repository.RoleRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAuditService auditService;

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       SecurityAuditService auditService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public User register(String username, String rawPassword, String email, String fullName,
                         Set<String> roleNames, String ipAddress) {
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("Username already exists");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setEmail(email);
        user.setFullName(fullName);

        // Initialize security fields
        user.setFailedLoginAttempts(0);
        user.setAccountLocked(false);
        user.setTokenVersion(0);

        Set<Role> roles = roleNames.stream()
                .map(rn -> roleRepository.findByName(rn)
                        .orElseThrow(() -> new RuntimeException("Role not found: " + rn)))
                .collect(Collectors.toSet());
        user.setRoles(roles);

        User savedUser = userRepository.save(user);

        // Log registration
        auditService.logRegistration(username, ipAddress);

        return savedUser;
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }
    
    @Transactional
    public User save(User user) {
        return userRepository.save(user);
    }

    /**
     * 🔒 Handle failed login attempt
     */
    @Transactional
    public void handleFailedLogin(String username, String ipAddress, String userAgent, String reason) {
        Optional<User> userOpt = userRepository.findByUsername(username);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.incrementFailedAttempts();
            userRepository.save(user);

            // Log the failure
            auditService.logLoginFailed(username, ipAddress, userAgent, reason);

            // If account just got locked, log that too
            if (user.getAccountLocked()) {
                auditService.logAccountLocked(username, ipAddress,
                        "Too many failed login attempts (" + user.getFailedLoginAttempts() + ")");
            }
        } else {
            // Username doesn't exist - still log for security monitoring
            auditService.logLoginFailed(username, ipAddress, userAgent, "User not found");
        }
    }

    /**
     * 🔒 Handle successful login
     */
    @Transactional
    public void handleSuccessfulLogin(User user, String ipAddress, String userAgent, String device) {
        user.resetFailedAttempts();
        user.setLastLoginIp(ipAddress);
        user.setLastLoginDevice(device);
        userRepository.save(user);

        // Log success
        auditService.logLoginSuccess(user.getUsername(), ipAddress, userAgent);
    }

    /**
     * 🔒 Check if account is locked
     */
    public boolean isAccountLocked(String username) {
        return userRepository.findByUsername(username)
                .map(user -> !user.isAccountNonLocked())
                .orElse(false);
    }

    /**
     * 🔒 Get remaining lock time in minutes
     */
    public long getRemainingLockTime(String username) {
        return userRepository.findByUsername(username)
                .map(user -> {
                    if (user.getAccountLocked() && user.getLockTime() != null) {
                        LocalDateTime unlockTime = user.getLockTime().plusMinutes(15);
                        LocalDateTime now = LocalDateTime.now();

                        if (now.isBefore(unlockTime)) {
                            return java.time.Duration.between(now, unlockTime).toMinutes();
                        }
                    }
                    return 0L;
                })
                .orElse(0L);
    }

    /**
     * 🔒 Manually unlock account (admin feature)
     */
    @Transactional
    public void unlockAccount(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setAccountLocked(false);
            user.setFailedLoginAttempts(0);
            user.setLockTime(null);
            userRepository.save(user);
            auditService.logAccountUnlocked(username);
        });
    }
}
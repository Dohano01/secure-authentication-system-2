package com.example.auth_service.controller;

import com.example.auth_service.dto.*;
import com.example.auth_service.model.RefreshToken;
import com.example.auth_service.model.User;
import com.example.auth_service.service.UserService;
import com.example.auth_service.service.SecurityAuditService;
import com.example.auth_service.service.RefreshTokenService;
import com.example.auth_service.service.MfaService;
import com.example.auth_service.service.MfaTokenService;
import com.example.auth_service.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAuditService auditService;
    private final RefreshTokenService refreshTokenService;
    private final MfaService mfaService;
    private final MfaTokenService mfaTokenService;

    public AuthController(UserService userService, JwtUtil jwtUtil,
                          PasswordEncoder passwordEncoder, SecurityAuditService auditService,
                          RefreshTokenService refreshTokenService, MfaService mfaService,
                          MfaTokenService mfaTokenService) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.refreshTokenService = refreshTokenService;
        this.mfaService = mfaService;
        this.mfaTokenService = mfaTokenService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req, HttpServletRequest request) {
        // default role USER if none provided
        if (req.getRoles() == null || req.getRoles().isEmpty()) {
            req.setRoles(java.util.Set.of("PATIENT"));
        }

        String ipAddress = getClientIp(request);

        User created = userService.register(req.getUsername(), req.getPassword(), req.getEmail(),
                req.getFullName(), req.getRoles(), ipAddress);

        return ResponseEntity.ok(Map.of(
                "message", "User registered successfully",
                "username", created.getUsername()
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request, HttpServletRequest httpRequest) {
        String ipAddress = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        // 🔒 STEP 1: Check if user exists
        Optional<User> userOpt = userService.findByUsername(request.getUsername());

        if (userOpt.isEmpty()) {
            userService.handleFailedLogin(request.getUsername(), ipAddress, userAgent, "User not found");
            return ResponseEntity.status(401).body(Map.of(
                    "error", "Invalid credentials",
                    "message", "Username or password is incorrect"
            ));
        }

        User user = userOpt.get();

        // 🔒 STEP 2: Check if account is locked
        if (!user.isAccountNonLocked()) {
            long remainingMinutes = userService.getRemainingLockTime(request.getUsername());
            return ResponseEntity.status(423).body(Map.of(
                    "error", "Account locked",
                    "message", "Too many failed login attempts. Account is locked.",
                    "remainingLockTimeMinutes", remainingMinutes,
                    "tryAgainIn", remainingMinutes + " minutes"
            ));
        }

        // 🔒 STEP 3: Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            userService.handleFailedLogin(request.getUsername(), ipAddress, userAgent, "Invalid password");

            // Get updated user to check if just locked
            user = userService.findByUsername(request.getUsername()).get();

            if (user.getAccountLocked()) {
                return ResponseEntity.status(423).body(Map.of(
                        "error", "Account locked",
                        "message", "Too many failed login attempts. Account locked for 15 minutes.",
                        "failedAttempts", user.getFailedLoginAttempts()
                ));
            }

            int remainingAttempts = 5 - user.getFailedLoginAttempts();
            return ResponseEntity.status(401).body(Map.of(
                    "error", "Invalid credentials",
                    "message", "Username or password is incorrect",
                    "remainingAttempts", remainingAttempts,
                    "warning", remainingAttempts <= 2 ? "Account will be locked after " + remainingAttempts + " more failed attempts" : ""
            ));
        }

        // 🔒 STEP 4: Check if MFA is enabled
        if (mfaService.isMfaEnabled(user)) {
            // MFA is enabled - return temporary token for MFA verification
            String mfaToken = mfaTokenService.generateMfaToken(user, ipAddress, userAgent);
            
            return ResponseEntity.status(200).body(MfaLoginResponse.builder()
                    .mfaRequired(true)
                    .mfaToken(mfaToken)
                    .build());
        }

        // 🔒 STEP 5: Login successful - reset attempts and generate tokens
        String device = extractDevice(userAgent);
        userService.handleSuccessfulLogin(user, ipAddress, userAgent, device);

        var roles = user.getRoles().stream().map(r -> r.getName()).collect(Collectors.toList());
        String accessToken = jwtUtil.generateToken(user.getUsername(), roles, user.getTokenVersion());
        
        // Generate refresh token
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user, ipAddress, userAgent, device);

        return ResponseEntity.ok(AuthResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken.getToken())
                .build());
    }

    @GetMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestHeader("Authorization") String header) {
        try {
            if (header == null || !header.startsWith("Bearer ")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing token"));
            }
            String token = header.substring(7);
            var claims = jwtUtil.validateTokenAndGetClaims(token);
            return ResponseEntity.ok(claims);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid token: " + e.getMessage()));
        }
    }

    /**
     * 🔒 Get user's login history (for testing/admin)
     */
    @GetMapping("/login-history/{username}")
    public ResponseEntity<?> getLoginHistory(@PathVariable String username) {
        var history = auditService.getUserLoginHistory(username);
        return ResponseEntity.ok(history);
    }

    /**
     * 🔒 Manually unlock account (admin endpoint)
     */
    @PostMapping("/unlock/{username}")
    public ResponseEntity<?> unlockAccount(@PathVariable String username) {
        userService.unlockAccount(username);
        return ResponseEntity.ok(Map.of(
                "message", "Account unlocked successfully",
                "username", username
        ));
    }

    // ========================================
    // 🔄 REFRESH TOKEN ENDPOINTS (STEP 3)
    // ========================================

    /**
     * 🔄 Refresh access token using refresh token
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody RefreshTokenRequest request, HttpServletRequest httpRequest) {
        String ipAddress = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        if (request.getRefreshToken() == null || request.getRefreshToken().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid request",
                    "message", "Refresh token is required"
            ));
        }

        // Validate refresh token
        Optional<RefreshToken> refreshTokenOpt = refreshTokenService.validateRefreshToken(request.getRefreshToken());

        if (refreshTokenOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", "Invalid refresh token",
                    "message", "Refresh token is invalid, expired, or revoked"
            ));
        }

        RefreshToken refreshToken = refreshTokenOpt.get();
        User user = refreshToken.getUser();

        // Check if account is locked
        if (!user.isAccountNonLocked()) {
            return ResponseEntity.status(423).body(Map.of(
                    "error", "Account locked",
                    "message", "Account is locked. Please contact administrator."
            ));
        }

        // Generate new access token with current token version
        var roles = user.getRoles().stream().map(r -> r.getName()).collect(Collectors.toList());
        String newAccessToken = jwtUtil.generateToken(user.getUsername(), roles, user.getTokenVersion());

        // Log token refresh
        auditService.logEvent(user.getUsername(), "TOKEN_REFRESH", ipAddress, userAgent,
                "Access token refreshed successfully", true);

        return ResponseEntity.ok(AuthResponse.builder()
                .token(newAccessToken)
                .refreshToken(refreshToken.getToken()) // Return same refresh token (rotation can be added later)
                .build());
    }

    /**
     * 🔄 Logout - Revoke current refresh token
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody RefreshTokenRequest request, HttpServletRequest httpRequest) {
        String ipAddress = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        if (request.getRefreshToken() == null || request.getRefreshToken().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid request",
                    "message", "Refresh token is required"
            ));
        }

        // Find and revoke the token
        Optional<RefreshToken> refreshTokenOpt = refreshTokenService.validateRefreshToken(request.getRefreshToken());
        
        if (refreshTokenOpt.isPresent()) {
            RefreshToken refreshToken = refreshTokenOpt.get();
            String username = refreshToken.getUser().getUsername();
            
            refreshTokenService.revokeToken(request.getRefreshToken());
            
            // Log logout
            auditService.logEvent(username, "LOGOUT", ipAddress, userAgent,
                    "User logged out successfully", true);
            
            return ResponseEntity.ok(Map.of(
                    "message", "Logged out successfully"
            ));
        }

        // Token not found or already revoked - still return success for security
        return ResponseEntity.ok(Map.of(
                "message", "Logged out successfully"
        ));
    }

    /**
     * 🔄 Logout from all devices - Revoke all refresh tokens for user
     */
    @PostMapping("/logout-all")
    public ResponseEntity<?> logoutAll(@RequestHeader("Authorization") String authHeader, HttpServletRequest httpRequest) {
        String ipAddress = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        try {
            // Extract username from access token
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing or invalid authorization header"));
            }

            String token = authHeader.substring(7);
            var claims = jwtUtil.validateTokenAndGetClaims(token);
            String username = claims.getSubject();

            // Find user and revoke all tokens
            Optional<User> userOpt = userService.findByUsername(username);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "User not found"));
            }

            User user = userOpt.get();
            
            // Increment token version to invalidate all tokens
            user.incrementTokenVersion();
            userService.save(user);
            
            // Revoke all refresh tokens
            refreshTokenService.revokeAllUserTokens(user);
            
            // Log logout all
            auditService.logEvent(username, "LOGOUT_ALL", ipAddress, userAgent,
                    "User logged out from all devices", true);

            return ResponseEntity.ok(Map.of(
                    "message", "Logged out from all devices successfully",
                    "username", username
            ));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid token: " + e.getMessage()));
        }
    }

    /**
     * 🔄 Get all active sessions for current user
     */
    @GetMapping("/sessions")
    public ResponseEntity<?> getSessions(@RequestHeader("Authorization") String authHeader) {
        try {
            // Extract username from access token
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing or invalid authorization header"));
            }

            String token = authHeader.substring(7);
            var claims = jwtUtil.validateTokenAndGetClaims(token);
            String username = claims.getSubject();

            // Find user and get all active sessions
            Optional<User> userOpt = userService.findByUsername(username);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "User not found"));
            }

            User user = userOpt.get();
            var sessions = refreshTokenService.getUserSessions(user);

            // Map to response DTO
            var sessionList = sessions.stream()
                    .map(rt -> Map.of(
                            "id", rt.getId(),
                            "deviceType", rt.getDeviceType() != null ? rt.getDeviceType() : "Unknown",
                            "ipAddress", rt.getIpAddress() != null ? rt.getIpAddress() : "Unknown",
                            "userAgent", rt.getUserAgent() != null ? rt.getUserAgent() : "Unknown",
                            "createdAt", rt.getCreatedAt().toString(),
                            "expiryDate", rt.getExpiryDate().toString()
                    ))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "username", username,
                    "totalSessions", sessions.size(),
                    "sessions", sessionList
            ));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid token: " + e.getMessage()));
        }
    }

    // ========================================
    // HELPER METHODS
    // ========================================

    /**
     * Extract client IP address
     */
    private String getClientIp(HttpServletRequest request) {
        // Try case-insensitive header lookup
        String ip = getHeaderCaseInsensitive(request, "X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = getHeaderCaseInsensitive(request, "X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // If multiple IPs, take the first one
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
    
    /**
     * Get header value case-insensitively
     */
    private String getHeaderCaseInsensitive(HttpServletRequest request, String headerName) {
        java.util.Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String header = headerNames.nextElement();
            if (header.equalsIgnoreCase(headerName)) {
                return request.getHeader(header);
            }
        }
        return null;
    }

    /**
     * Extract device type from User-Agent
     */
    private String extractDevice(String userAgent) {
        if (userAgent == null) return "Unknown";

        userAgent = userAgent.toLowerCase();

        if (userAgent.contains("mobile") || userAgent.contains("android") || userAgent.contains("iphone")) {
            return "Mobile";
        } else if (userAgent.contains("tablet") || userAgent.contains("ipad")) {
            return "Tablet";
        } else {
            return "Desktop";
        }
    }

    // ========================================
    // 🔐 MULTI-FACTOR AUTHENTICATION (MFA)
    // ========================================

    /**
     * 🔐 Setup MFA - Generate secret and QR code
     */
    @PostMapping("/mfa/setup")
    public ResponseEntity<?> setupMfa(@RequestHeader("Authorization") String authHeader) {
        try {
            // Extract username from access token
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing or invalid authorization header"));
            }

            String token = authHeader.substring(7);
            var claims = jwtUtil.validateTokenAndGetClaims(token);
            String username = claims.getSubject();

            Optional<User> userOpt = userService.findByUsername(username);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "User not found"));
            }

            User user = userOpt.get();

            // Generate secret and QR code
            String secret = mfaService.generateSecret();
            String qrCodeDataUrl = mfaService.generateQrCodeDataUrl(secret, user.getUsername());
            
            // Format secret for manual entry (add spaces every 4 characters)
            String manualEntryKey = secret.replaceAll("(.{4})", "$1 ").trim();

            return ResponseEntity.ok(MfaSetupResponse.builder()
                    .secret(secret)
                    .qrCodeDataUrl(qrCodeDataUrl)
                    .manualEntryKey(manualEntryKey)
                    .build());
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid token: " + e.getMessage()));
        }
    }

    /**
     * 🔐 Enable MFA - Verify code and enable MFA
     */
    @PostMapping("/mfa/enable-with-secret")
    public ResponseEntity<?> enableMfaWithSecret(@RequestHeader("Authorization") String authHeader,
                                                  @RequestBody Map<String, String> request) {
        try {
            // Extract username from access token
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing or invalid authorization header"));
            }

            String token = authHeader.substring(7);
            var claims = jwtUtil.validateTokenAndGetClaims(token);
            String username = claims.getSubject();

            Optional<User> userOpt = userService.findByUsername(username);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "User not found"));
            }

            User user = userOpt.get();

            String secret = request.get("secret");
            String code = request.get("code");

            if (secret == null || secret.isEmpty() || code == null || code.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Secret and code are required"));
            }

            // Verify the code
            if (!mfaService.verifyCode(secret, code)) {
                return ResponseEntity.status(401).body(Map.of(
                        "error", "Invalid MFA code",
                        "message", "The code you entered is incorrect. Please try again."
                ));
            }

            // Enable MFA
            mfaService.enableMfa(user, secret);
            userService.save(user);

            // Log MFA activation
            auditService.logEvent(username, "MFA_ENABLED", null, null,
                    "Multi-factor authentication enabled", true);

            return ResponseEntity.ok(Map.of(
                    "message", "MFA enabled successfully",
                    "username", username
            ));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid token: " + e.getMessage()));
        }
    }

    /**
     * 🔐 Disable MFA
     */
    @PostMapping("/mfa/disable")
    public ResponseEntity<?> disableMfa(@RequestHeader("Authorization") String authHeader) {
        try {
            // Extract username from access token
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing or invalid authorization header"));
            }

            String token = authHeader.substring(7);
            var claims = jwtUtil.validateTokenAndGetClaims(token);
            String username = claims.getSubject();

            Optional<User> userOpt = userService.findByUsername(username);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "User not found"));
            }

            User user = userOpt.get();

            if (!mfaService.isMfaEnabled(user)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "MFA is not enabled",
                        "message", "Multi-factor authentication is not enabled for this account"
                ));
            }

            // Disable MFA
            mfaService.disableMfa(user);
            userService.save(user);

            // Log MFA deactivation
            auditService.logEvent(username, "MFA_DISABLED", null, null,
                    "Multi-factor authentication disabled", true);

            return ResponseEntity.ok(Map.of(
                    "message", "MFA disabled successfully",
                    "username", username
            ));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid token: " + e.getMessage()));
        }
    }

    /**
     * 🔐 Verify MFA code during login
     */
    @PostMapping("/mfa/verify")
    public ResponseEntity<?> verifyMfa(@RequestBody MfaVerifyRequest request, HttpServletRequest httpRequest) {
        String ipAddress = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        if (request.getMfaToken() == null || request.getMfaToken().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid request",
                    "message", "MFA token is required"
            ));
        }

        if (request.getCode() == null || request.getCode().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid request",
                    "message", "MFA code is required"
            ));
        }

        // Validate MFA token
        MfaTokenService.MfaTokenData tokenData = mfaTokenService.validateMfaToken(request.getMfaToken());
        if (tokenData == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", "Invalid or expired MFA token",
                    "message", "Please login again"
            ));
        }

        User user = tokenData.getUser();

        // Verify MFA code
        if (!mfaService.verifyCode(user.getMfaSecret(), request.getCode())) {
            // Log failed MFA attempt
            auditService.logEvent(user.getUsername(), "MFA_VERIFICATION_FAILED", ipAddress, userAgent,
                    "MFA code verification failed", false);

            // Remove invalid token
            mfaTokenService.removeMfaToken(request.getMfaToken());

            return ResponseEntity.status(401).body(Map.of(
                    "error", "Invalid MFA code",
                    "message", "The code you entered is incorrect. Please try again."
            ));
        }

        // MFA verified - complete login
        String device = extractDevice(userAgent);
        userService.handleSuccessfulLogin(user, ipAddress, userAgent, device);

        var roles = user.getRoles().stream().map(r -> r.getName()).collect(Collectors.toList());
        String accessToken = jwtUtil.generateToken(user.getUsername(), roles, user.getTokenVersion());
        
        // Generate refresh token
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user, ipAddress, userAgent, device);

        // Remove MFA token
        mfaTokenService.removeMfaToken(request.getMfaToken());

        // Log successful MFA verification
        auditService.logEvent(user.getUsername(), "MFA_VERIFICATION_SUCCESS", ipAddress, userAgent,
                "MFA code verified successfully", true);

        return ResponseEntity.ok(AuthResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken.getToken())
                .build());
    }

    /**
     * 🔐 Get MFA status
     */
    @GetMapping("/mfa/status")
    public ResponseEntity<?> getMfaStatus(@RequestHeader("Authorization") String authHeader) {
        try {
            // Extract username from access token
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing or invalid authorization header"));
            }

            String token = authHeader.substring(7);
            var claims = jwtUtil.validateTokenAndGetClaims(token);
            String username = claims.getSubject();

            Optional<User> userOpt = userService.findByUsername(username);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "User not found"));
            }

            User user = userOpt.get();
            boolean mfaEnabled = mfaService.isMfaEnabled(user);

            return ResponseEntity.ok(Map.of(
                    "username", username,
                    "mfaEnabled", mfaEnabled
            ));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid token: " + e.getMessage()));
        }
    }
}
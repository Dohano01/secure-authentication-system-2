package com.example.auth_service.service;

import com.example.auth_service.model.User;
import dev.samstevens.totp.code.*;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Base64;

@Service
@Slf4j
public class MfaService {

    @Value("${app.name:Auth Service}")
    private String appName;

    private final SecretGenerator secretGenerator;
    private final QrGenerator qrGenerator;
    private final CodeVerifier codeVerifier;

    public MfaService() {
        this.secretGenerator = new DefaultSecretGenerator();
        this.qrGenerator = new ZxingPngQrGenerator();
        
        // Configure code verifier with default settings
        TimeProvider timeProvider = new SystemTimeProvider();
        CodeGenerator codeGenerator = new DefaultCodeGenerator();
        this.codeVerifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
    }

    /**
     * Generate a new MFA secret for a user
     */
    public String generateSecret() {
        return secretGenerator.generate();
    }

    /**
     * Generate QR code data URL for the secret
     */
    public String generateQrCodeDataUrl(String secret, String username) {
        try {
            QrData qrData = new QrData.Builder()
                    .label(username)
                    .secret(secret)
                    .issuer(appName)
                    .algorithm(HashingAlgorithm.SHA1)
                    .digits(6)
                    .period(30)
                    .build();

            byte[] qrCodeImage = qrGenerator.generate(qrData);
            String base64Image = Base64.getEncoder().encodeToString(qrCodeImage);
            return "data:image/png;base64," + base64Image;
        } catch (QrGenerationException e) {
            log.error("Error generating QR code", e);
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }

    /**
     * Verify a TOTP code against a secret
     */
    public boolean verifyCode(String secret, String code) {
        try {
            return codeVerifier.isValidCode(secret, code);
        } catch (Exception e) {
            log.error("Error verifying MFA code", e);
            return false;
        }
    }

    /**
     * Enable MFA for a user (after verifying the code)
     */
    public void enableMfa(User user, String secret) {
        user.setMfaEnabled(true);
        user.setMfaSecret(secret);
        log.info("MFA enabled for user: {}", user.getUsername());
    }

    /**
     * Disable MFA for a user
     */
    public void disableMfa(User user) {
        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        log.info("MFA disabled for user: {}", user.getUsername());
    }

    /**
     * Check if MFA is enabled for a user
     */
    public boolean isMfaEnabled(User user) {
        return user.getMfaEnabled() != null && user.getMfaEnabled() 
                && user.getMfaSecret() != null && !user.getMfaSecret().isEmpty();
    }
}

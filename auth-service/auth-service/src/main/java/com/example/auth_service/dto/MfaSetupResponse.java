package com.example.auth_service.dto;

import lombok.*;

@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MfaSetupResponse {
    private String secret;
    private String qrCodeDataUrl;
    private String manualEntryKey; // For manual entry if QR code doesn't work
}

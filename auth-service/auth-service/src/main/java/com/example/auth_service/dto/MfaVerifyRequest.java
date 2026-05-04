package com.example.auth_service.dto;

import lombok.*;

@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
public class MfaVerifyRequest {
    private String mfaToken; // Temporary token from login
    private String code; // TOTP code from authenticator app
}

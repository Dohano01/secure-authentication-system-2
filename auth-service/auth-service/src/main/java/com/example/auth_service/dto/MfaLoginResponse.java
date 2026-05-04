package com.example.auth_service.dto;

import lombok.*;

@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MfaLoginResponse {
    private Boolean mfaRequired;
    private String mfaToken; // Temporary token for MFA verification
    private String token; // Access token (if MFA not required)
    private String refreshToken; // Refresh token (if MFA not required)
}

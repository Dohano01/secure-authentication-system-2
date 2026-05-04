package com.example.auth_service.dto;
import lombok.*;
        import java.util.Set;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class RegisterRequest {
    private String username;
    private String password;
    private String email;
    private String fullName;
    private Set<String> roles; // ex: ["ADMIN"]
}

package com.example.auth_service.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "roles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // values: ADMIN, MEDECIN, INFIRMIER, PATIENT, USER
    @Column(unique = true, nullable = false)
    private String name;
}

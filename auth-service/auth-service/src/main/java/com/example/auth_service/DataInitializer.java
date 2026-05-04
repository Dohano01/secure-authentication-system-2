package com.example.auth_service;

import com.example.auth_service.model.Role;
import com.example.auth_service.model.User;
import com.example.auth_service.repository.RoleRepository;
import com.example.auth_service.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(RoleRepository roleRepository, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        // create roles if not exists
        String[] roleNames = {"ADMIN","MEDECIN","INFIRMIER","PATIENT"};
        for (String rn : roleNames) {
            roleRepository.findByName(rn).orElseGet(() -> roleRepository.save(new Role(null, rn)));
        }

        // create default admin
        if (userRepository.findByUsername("admin").isEmpty()) {
            var admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setFullName("Administrator");
            admin.setEmail("admin@example.com");
            var adminRole = roleRepository.findByName("ADMIN").get();
            admin.setRoles(Set.of(adminRole));
            userRepository.save(admin);
            System.out.println("Created default admin / password: admin123");
        }
    }
}

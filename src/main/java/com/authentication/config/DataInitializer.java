package com.authentication.config;

import com.authentication.entity.Role;
import com.authentication.repo.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

	@Autowired
    private RoleRepository roleRepository;

    @Override
    public void run(String... args) {
        seedRole(Role.RoleName.ROLE_STUDENT);
        seedRole(Role.RoleName.ROLE_RECRUITER);
        seedRole(Role.RoleName.ROLE_ADMIN);
        log.info("✅ Roles initialized successfully");
    }

    private void seedRole(Role.RoleName name) {
        if (roleRepository.findByName(name).isEmpty()) {
            roleRepository.save(Role.builder().name(name).build());
            log.info("Created role: {}", name);
        }
    }
}
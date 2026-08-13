package com.slotline.config;

import com.slotline.organization.Organization;
import com.slotline.organization.OrganizationService;
import com.slotline.user.Role;
import com.slotline.user.User;
import com.slotline.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final OrganizationService organizations;
    private final PasswordEncoder passwordEncoder;
    private final SlotlineProperties properties;

    public AdminBootstrap(UserRepository users, OrganizationService organizations, PasswordEncoder passwordEncoder,
                          SlotlineProperties properties) {
        this.users = users;
        this.organizations = organizations;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String username = properties.getAdmin().getUsername();
        if (users.existsByUsername(username)) {
            return;
        }
        Organization defaultOrganization = organizations.requireBySlug(OrganizationService.defaultSlug);
        String passwordHash = passwordEncoder.encode(properties.getAdmin().getPassword());
        users.save(new User(defaultOrganization.getId(), username, passwordHash, Role.ADMIN, Instant.now()));
        log.info("Seeded default ADMIN account '{}' in organization '{}'", username, defaultOrganization.getSlug());
    }
}

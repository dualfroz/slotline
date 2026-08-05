package com.slotline.user;

import com.slotline.common.InvalidCredentialsException;
import com.slotline.common.UsernameTakenException;
import com.slotline.config.SlotlineProperties;
import com.slotline.organization.Organization;
import com.slotline.organization.OrganizationService;
import com.slotline.security.JwtService;
import com.slotline.user.dto.LoginRequest;
import com.slotline.user.dto.RegisterRequest;
import com.slotline.user.dto.TokenResponse;
import com.slotline.user.dto.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuthService {

    private final UserRepository users;
    private final OrganizationService organizations;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SlotlineProperties properties;

    public AuthService(UserRepository users, OrganizationService organizations, PasswordEncoder passwordEncoder,
                       JwtService jwtService, SlotlineProperties properties) {
        this.users = users;
        this.organizations = organizations;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.properties = properties;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (users.existsByUsername(request.username())) {
            throw new UsernameTakenException(request.username());
        }
        Organization organization = resolveOrganization(request.organizationSlug());
        User user = new User(organization.getId(), request.username(), passwordEncoder.encode(request.password()),
                Role.USER, Instant.now());
        users.save(user);
        return new UserResponse(user.getId(), user.getUsername(), user.getRole(), user.getOrganizationId());
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = users.findByUsername(request.username())
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);
        String token = jwtService.issue(user.getId(), user.getUsername(), user.getRole(), user.getOrganizationId());
        return new TokenResponse(token, "Bearer", properties.getJwt().getExpiration().toSeconds());
    }

    private Organization resolveOrganization(String slug) {
        String target = (slug == null || slug.isBlank()) ? OrganizationService.defaultSlug : slug;
        return organizations.requireBySlug(target);
    }
}

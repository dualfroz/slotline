package com.slotline.security;

import com.slotline.config.SlotlineProperties;
import com.slotline.user.Role;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static JwtService serviceWithSecret(String secret) {
        SlotlineProperties properties = new SlotlineProperties();
        properties.getJwt().setSecret(secret);
        properties.getJwt().setIssuer("slotline");
        properties.getJwt().setExpiration(Duration.ofHours(1));
        return new JwtService(properties);
    }

    @Test
    void issuesAndParsesRoundTrip() {
        JwtService jwtService = serviceWithSecret("round-trip-secret-round-trip-secret-0123456789");

        String token = jwtService.issue(42L, "alice", Role.USER, 7L);
        AuthenticatedUser parsed = jwtService.parse(token);

        assertThat(parsed.id()).isEqualTo(42L);
        assertThat(parsed.username()).isEqualTo("alice");
        assertThat(parsed.role()).isEqualTo(Role.USER);
        assertThat(parsed.organizationId()).isEqualTo(7L);
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() {
        JwtService issuer = serviceWithSecret("issuer-secret-issuer-secret-issuer-0123456789");
        JwtService verifier = serviceWithSecret("verifier-secret-verifier-secret-diff-0123456789");

        String token = issuer.issue(1L, "bob", Role.ADMIN, 3L);

        assertThatThrownBy(() -> verifier.parse(token)).isInstanceOf(JwtException.class);
    }
}

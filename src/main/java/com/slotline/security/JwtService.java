package com.slotline.security;

import com.slotline.config.SlotlineProperties;
import com.slotline.user.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final Duration expiration;
    private final String issuer;

    public JwtService(SlotlineProperties properties) {
        this.signingKey = Keys.hmacShaKeyFor(properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
        this.expiration = properties.getJwt().getExpiration();
        this.issuer = properties.getJwt().getIssuer();
    }

    public String issue(Long userId, String username, Role role, Long organizationId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(username)
                .claim("uid", userId)
                .claim("role", role.name())
                .claim("org", organizationId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(signingKey)
                .compact();
    }

    public AuthenticatedUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Long userId = ((Number) claims.get("uid")).longValue();
        Role role = Role.valueOf(claims.get("role", String.class));
        Long organizationId = ((Number) claims.get("org")).longValue();
        return new AuthenticatedUser(userId, claims.getSubject(), role, organizationId);
    }
}

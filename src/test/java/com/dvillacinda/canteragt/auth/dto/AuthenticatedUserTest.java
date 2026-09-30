package com.dvillacinda.canteragt.auth.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AuthenticatedUserTest {
    @Test
    void keycloakIdComesFromAuthenticatedJwtSubject() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("creator-id")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("email", "admin@example.com")
                .claim("preferred_username", "admin")
                .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_ACADEMY_ADMIN")));

        AuthenticatedUser authenticated = AuthenticatedUser.fromAuthentication(authentication);

        assertEquals("creator-id", authenticated.keycloakId());
        assertEquals(List.of("ACADEMY_ADMIN"), authenticated.roles());
    }
}

package com.dvillacinda.canteragt.auth.dto;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public record AuthenticatedUser(
        String keycloakId,
        String email,
        String username,
        List<String> roles) {

    private static final String ROLE_PREFIX = "ROLE_";

    public static AuthenticatedUser fromAuthentication(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return new AuthenticatedUser(
                jwt.getClaimAsString("sub"),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("preferred_username"),
                extractRoles(authentication.getAuthorities()));
    }

    private static List<String> extractRoles(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream()
                
                .map(authority -> authority != null ? authority.getAuthority() : null)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .toList();
    }
}
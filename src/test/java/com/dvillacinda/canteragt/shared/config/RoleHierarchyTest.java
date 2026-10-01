package com.dvillacinda.canteragt.shared.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class RoleHierarchyTest {

    static class AcademyAdminAction {
        @PreAuthorize("hasRole('ACADEMY_ADMIN')")
        public void run() {
        }
    }

    @Configuration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
        @Bean
        static RoleHierarchy roleHierarchy() {
            return SecurityConfig.roleHierarchy();
        }

        @Bean
        static MethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy roleHierarchy) {
            return SecurityConfig.methodSecurityExpressionHandler(roleHierarchy);
        }

        @Bean
        AcademyAdminAction academyAdminAction() {
            return new AcademyAdminAction();
        }
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("user", null, List.of(new SimpleGrantedAuthority(role))));
    }

    @Test
    void systemAdminInheritsAcademyAdminPermissions() {
        try (var context = new AnnotationConfigApplicationContext(MethodSecurityTestConfig.class)) {
            authenticateAs("ROLE_SYSTEM_ADMIN");
            assertDoesNotThrow(() -> context.getBean(AcademyAdminAction.class).run());
        }
    }

    @Test
    void coachDoesNotInheritAcademyAdminPermissions() {
        try (var context = new AnnotationConfigApplicationContext(MethodSecurityTestConfig.class)) {
            authenticateAs("ROLE_COACH");
            assertThrows(AccessDeniedException.class, () -> context.getBean(AcademyAdminAction.class).run());
        }
    }
}

package com.dvillacinda.canteragt.training_session.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;

import com.dvillacinda.canteragt.auth.security.KeycloakJwtAuthenticationConverter;
import com.dvillacinda.canteragt.auth.security.RestAuthenticationEntryPoint;
import com.dvillacinda.canteragt.shared.config.SecurityConfig;
import com.dvillacinda.canteragt.shared.exception.GlobalExceptionHandler;
import com.dvillacinda.canteragt.training_session.dto.TrainingSessionFilter;
import com.dvillacinda.canteragt.training_session.service.TrainingSessionService;

@WebMvcTest(value = TrainingSessionController.class, properties = {
        "app.security.cors.allowed-origins=http://localhost",
        "app.security.oauth2.client-id=test",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.test"
})
@Import({ SecurityConfig.class, RestAuthenticationEntryPoint.class, GlobalExceptionHandler.class,
        TrainingSessionController.class, TrainingSessionSecurityHttpTest.WebSecurityEnabler.class })
class TrainingSessionSecurityHttpTest {
    private static final UUID ACADEMY_ID = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrainingSessionService trainingSessionService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private KeycloakJwtAuthenticationConverter jwtAuthenticationConverter;

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class WebSecurityEnabler {
    }

    @Test
    void routesRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/academy-categories/{id}/training-sessions", CATEGORY_ID)
                        .header("X-Academy-Id", ACADEMY_ID))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(trainingSessionService);
    }

    @Test
    void playerCannotAccessStaffSessionRoutes() throws Exception {
        mockMvc.perform(get("/api/v1/academy-categories/{id}/training-sessions", CATEGORY_ID)
                        .header("X-Academy-Id", ACADEMY_ID)
                        .with(user("player").roles("PLAYER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(trainingSessionService);
    }

    @Test
    void coachCanReachListEndpointAndContextHeaderIsRequired() throws Exception {
        when(trainingSessionService.list(
                eq(CATEGORY_ID), eq(ACADEMY_ID), any(TrainingSessionFilter.class), any(), any()))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/academy-categories/{id}/training-sessions", CATEGORY_ID)
                        .header("X-Academy-Id", ACADEMY_ID)
                        .with(user("coach").roles("COACH")))
                .andExpect(status().isOk());

        verify(trainingSessionService).list(
                eq(CATEGORY_ID), eq(ACADEMY_ID), any(TrainingSessionFilter.class), any(), any());

        mockMvc.perform(get("/api/v1/academy-categories/{id}/training-sessions", CATEGORY_ID)
                        .with(user("coach").roles("COACH")))
                .andExpect(status().isBadRequest());
    }
}

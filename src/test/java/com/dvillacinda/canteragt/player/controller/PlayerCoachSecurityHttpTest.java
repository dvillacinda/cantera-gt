package com.dvillacinda.canteragt.player.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.dvillacinda.canteragt.auth.security.KeycloakJwtAuthenticationConverter;
import com.dvillacinda.canteragt.auth.security.RestAuthenticationEntryPoint;
import com.dvillacinda.canteragt.coach.controller.CoachController;
import com.dvillacinda.canteragt.coach.service.CoachService;
import com.dvillacinda.canteragt.player.service.PlayerService;
import com.dvillacinda.canteragt.shared.config.SecurityConfig;
import com.dvillacinda.canteragt.shared.exception.GlobalExceptionHandler;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;

@WebMvcTest(value = { PlayerController.class, CoachController.class }, properties = {
        "app.security.cors.allowed-origins=http://localhost",
        "app.security.oauth2.client-id=test",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.test"
})
@Import({ PlayerController.class, CoachController.class, SecurityConfig.class, RestAuthenticationEntryPoint.class,
        GlobalExceptionHandler.class,
        PlayerCoachSecurityHttpTest.WebSecurityEnabler.class })
class PlayerCoachSecurityHttpTest {
    private static final UUID ACADEMY_ID = UUID.randomUUID();
    private static final UUID RESOURCE_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlayerService playerService;

    @MockitoBean
    private CoachService coachService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private KeycloakJwtAuthenticationConverter jwtAuthenticationConverter;

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class WebSecurityEnabler {}

    @Test
    void requestsWithoutTokenAreUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/players/get-player-by-id/{id}", RESOURCE_ID)
                        .header("X-Academy-Id", ACADEMY_ID))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/coaches/get-coach-by-id/{id}", RESOURCE_ID)
                        .header("X-Academy-Id", ACADEMY_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void playerRoleCannotReadStaffProfileEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/players/get-player-by-id/{id}", RESOURCE_ID)
                        .header("X-Academy-Id", ACADEMY_ID).with(user("player").roles("PLAYER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(playerService);
    }

    @Test
    void coachCrossScopeNotFoundPassesThroughHttpErrorHandling() throws Exception {
        org.mockito.Mockito.doThrow(new NotFoundException("Player not found in the selected academy or category"))
                .when(playerService).getPlayerById(org.mockito.ArgumentMatchers.eq(RESOURCE_ID),
                        org.mockito.ArgumentMatchers.eq(ACADEMY_ID), org.mockito.ArgumentMatchers.any());

        mockMvc.perform(get("/api/v1/players/get-player-by-id/{id}", RESOURCE_ID)
                        .header("X-Academy-Id", ACADEMY_ID).with(user("coach").roles("COACH")))
                .andExpect(status().isNotFound());
        verify(playerService).getPlayerById(org.mockito.ArgumentMatchers.eq(RESOURCE_ID),
                org.mockito.ArgumentMatchers.eq(ACADEMY_ID), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void academyAdminCrossScopeCoachIsNotFound() throws Exception {
        org.mockito.Mockito.doThrow(new NotFoundException("Coach not found in the selected academy"))
                .when(coachService).getCoachById(org.mockito.ArgumentMatchers.eq(RESOURCE_ID),
                        org.mockito.ArgumentMatchers.eq(ACADEMY_ID), org.mockito.ArgumentMatchers.any());

        mockMvc.perform(get("/api/v1/coaches/get-coach-by-id/{id}", RESOURCE_ID)
                        .header("X-Academy-Id", ACADEMY_ID).with(user("admin").roles("ACADEMY_ADMIN")))
                .andExpect(status().isNotFound());
        verify(coachService).getCoachById(org.mockito.ArgumentMatchers.eq(RESOURCE_ID),
                org.mockito.ArgumentMatchers.eq(ACADEMY_ID), org.mockito.ArgumentMatchers.any());
    }
}

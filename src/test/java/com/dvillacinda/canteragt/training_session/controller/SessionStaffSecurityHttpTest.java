package com.dvillacinda.canteragt.training_session.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.dvillacinda.canteragt.auth.security.KeycloakJwtAuthenticationConverter;
import com.dvillacinda.canteragt.auth.security.RestAuthenticationEntryPoint;
import com.dvillacinda.canteragt.shared.config.SecurityConfig;
import com.dvillacinda.canteragt.shared.exception.BadRequestException;
import com.dvillacinda.canteragt.shared.exception.ConflictException;
import com.dvillacinda.canteragt.shared.exception.GlobalExceptionHandler;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;
import com.dvillacinda.canteragt.shared.exception.UnauthorizedAccessException;
import com.dvillacinda.canteragt.training_session.dto.SessionStaffResponse;
import com.dvillacinda.canteragt.training_session.enums.SessionParticipationType;
import com.dvillacinda.canteragt.training_session.service.SessionStaffService;

/** HTTP boundary of the session staff routes; the service is mocked (access decisions live in the policy). */
@WebMvcTest(value = SessionStaffController.class, properties = {
        "app.security.cors.allowed-origins=http://localhost",
        "app.security.oauth2.client-id=test",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.test"
})
@Import({ SecurityConfig.class, RestAuthenticationEntryPoint.class, GlobalExceptionHandler.class,
        SessionStaffController.class, SessionStaffSecurityHttpTest.WebSecurityEnabler.class })
class SessionStaffSecurityHttpTest {
    private static final UUID ACADEMY_ID = UUID.randomUUID();
    private static final UUID FOREIGN_ACADEMY_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID COACH_ID = UUID.randomUUID();
    private static final String ADD_BODY =
            "{\"coach_id\":\"" + COACH_ID + "\",\"participation_type\":\"ASSISTANT\"}";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private SessionStaffService sessionStaffService;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private KeycloakJwtAuthenticationConverter jwtAuthenticationConverter;

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class WebSecurityEnabler {
    }

    @FunctionalInterface
    interface RouteFactory {
        MockHttpServletRequestBuilder build(UUID academyId);
    }

    static Stream<Arguments> managementRoutes() {
        return Stream.of(
                Arguments.of("add", (RouteFactory) academy -> post(
                        "/api/v1/training-sessions/{id}/staff", SESSION_ID)
                        .header("X-Academy-Id", academy)
                        .contentType(MediaType.APPLICATION_JSON).content(ADD_BODY)),
                Arguments.of("remove", (RouteFactory) academy -> delete(
                        "/api/v1/training-sessions/{id}/staff/{coachId}", SESSION_ID, COACH_ID)
                        .header("X-Academy-Id", academy)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("managementRoutes")
    void unauthenticatedIs401(String label, RouteFactory route) throws Exception {
        mockMvc.perform(route.build(ACADEMY_ID)).andExpect(status().isUnauthorized());
        verifyNoInteractions(sessionStaffService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("managementRoutes")
    void playerIs403BeforeReachingService(String label, RouteFactory route) throws Exception {
        mockMvc.perform(route.build(ACADEMY_ID).with(user("player").roles("PLAYER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access denied"));
        verifyNoInteractions(sessionStaffService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("managementRoutes")
    void nonHeadCoachIs403(String label, RouteFactory route) throws Exception {
        UnauthorizedAccessException denied =
                new UnauthorizedAccessException("Head coach assignment is required to manage training sessions");
        when(sessionStaffService.addStaff(any(), any(), any(), any())).thenThrow(denied);
        doThrow(denied).when(sessionStaffService).removeStaff(any(), any(), any(), any());

        mockMvc.perform(route.build(ACADEMY_ID).with(user("assistant").roles("COACH")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Access denied"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("managementRoutes")
    void foreignAcademyIs404(String label, RouteFactory route) throws Exception {
        NotFoundException notFound = new NotFoundException("Training session not found in the selected academy");
        when(sessionStaffService.addStaff(any(), eq(FOREIGN_ACADEMY_ID), any(), any())).thenThrow(notFound);
        doThrow(notFound).when(sessionStaffService).removeStaff(any(), eq(FOREIGN_ACADEMY_ID), any(), any());

        mockMvc.perform(route.build(FOREIGN_ACADEMY_ID).with(user("coach").roles("COACH")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not found"));
    }

    @Test
    void staffReadReturnsNameAndRoleOnly() throws Exception {
        when(sessionStaffService.list(eq(SESSION_ID), eq(ACADEMY_ID), any())).thenReturn(List.of(
                new SessionStaffResponse(UUID.randomUUID(), COACH_ID, "Luis", "Pérez",
                        SessionParticipationType.LEAD, OffsetDateTime.parse("2026-11-17T10:00:00-06:00"))));

        mockMvc.perform(get("/api/v1/training-sessions/{id}/staff", SESSION_ID)
                        .header("X-Academy-Id", ACADEMY_ID)
                        .with(user("coach").roles("COACH")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].coach_id").value(COACH_ID.toString()))
                .andExpect(jsonPath("$.data[0].first_name").value("Luis"))
                .andExpect(jsonPath("$.data[0].participation_type").value("LEAD"))
                .andExpect(jsonPath("$.data[0].created_at").value("2026-11-17T10:00:00-06:00"))
                .andExpect(jsonPath("$.data[0].email").doesNotExist())
                .andExpect(jsonPath("$.data[0].keycloak_id").doesNotExist());
    }

    @Test
    void addReturns201() throws Exception {
        when(sessionStaffService.addStaff(eq(SESSION_ID), eq(ACADEMY_ID), any(), any())).thenReturn(
                new SessionStaffResponse(UUID.randomUUID(), COACH_ID, "Luis", "Pérez",
                        SessionParticipationType.ASSISTANT, null));

        mockMvc.perform(post("/api/v1/training-sessions/{id}/staff", SESSION_ID)
                        .header("X-Academy-Id", ACADEMY_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(ADD_BODY)
                        .with(user("coach").roles("COACH")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.participation_type").value("ASSISTANT"));
    }

    @Test
    void secondLeadOrDuplicateIs409AndCoachWithoutAssignmentIs400() throws Exception {
        when(sessionStaffService.addStaff(eq(SESSION_ID), eq(ACADEMY_ID), any(), any()))
                .thenThrow(new ConflictException("The session already has a LEAD coach"))
                .thenThrow(new BadRequestException("Coach has no current assignment in the session's category"));

        mockMvc.perform(post("/api/v1/training-sessions/{id}/staff", SESSION_ID)
                        .header("X-Academy-Id", ACADEMY_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(ADD_BODY)
                        .with(user("coach").roles("COACH")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The session already has a LEAD coach"));
        mockMvc.perform(post("/api/v1/training-sessions/{id}/staff", SESSION_ID)
                        .header("X-Academy-Id", ACADEMY_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(ADD_BODY)
                        .with(user("coach").roles("COACH")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad request"));
    }

    @Test
    void removeReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/training-sessions/{id}/staff/{coachId}", SESSION_ID, COACH_ID)
                        .header("X-Academy-Id", ACADEMY_ID)
                        .with(user("coach").roles("COACH")))
                .andExpect(status().isNoContent());
    }

    @Test
    void invalidBodyIs400BeforeReachingService() throws Exception {
        mockMvc.perform(post("/api/v1/training-sessions/{id}/staff", SESSION_ID)
                        .header("X-Academy-Id", ACADEMY_ID)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"participation_type\":\"LEAD\"}")
                        .with(user("coach").roles("COACH")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.coachId").exists());
        mockMvc.perform(post("/api/v1/training-sessions/{id}/staff", SESSION_ID)
                        .header("X-Academy-Id", ACADEMY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coach_id\":\"" + COACH_ID + "\",\"participation_type\":\"OWNER\"}")
                        .with(user("coach").roles("COACH")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/training-sessions/{id}/staff", SESSION_ID)
                        .with(user("coach").roles("COACH")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(sessionStaffService);
    }
}

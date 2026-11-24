package com.dvillacinda.canteragt.training_session.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.dvillacinda.canteragt.shared.exception.ConflictException;
import com.dvillacinda.canteragt.shared.exception.GlobalExceptionHandler;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;
import com.dvillacinda.canteragt.shared.exception.UnauthorizedAccessException;
import com.dvillacinda.canteragt.training_session.dto.SessionEvaluationEventResponse;
import com.dvillacinda.canteragt.training_session.dto.SessionEventMetricResponse;
import com.dvillacinda.canteragt.training_session.enums.SessionEvaluationEventStatus;
import com.dvillacinda.canteragt.training_session.service.SessionEvaluationEventService;

/** HTTP boundary of the evaluation event routes; the service is mocked (access decisions live in the policy). */
@WebMvcTest(value = SessionEvaluationEventController.class, properties = {
        "app.security.cors.allowed-origins=http://localhost",
        "app.security.oauth2.client-id=test",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.test"
})
@Import({ SecurityConfig.class, RestAuthenticationEntryPoint.class, GlobalExceptionHandler.class,
        SessionEvaluationEventController.class, SessionEvaluationEventSecurityHttpTest.WebSecurityEnabler.class })
class SessionEvaluationEventSecurityHttpTest {
    private static final UUID ACADEMY_ID = UUID.randomUUID();
    private static final UUID FOREIGN_ACADEMY_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID TEMPLATE_ID = UUID.randomUUID();
    private static final UUID CRITERION_ID = UUID.randomUUID();
    private static final String CREATE_BODY = "{\"template_id\":\"" + TEMPLATE_ID + "\"}";
    private static final String METRICS_BODY = "{\"metric_criterion_ids\":[\"" + CRITERION_ID + "\"]}";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private SessionEvaluationEventService eventService;
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
                Arguments.of("create", (RouteFactory) academy -> post(
                        "/api/v1/training-sessions/{id}/evaluation-events", SESSION_ID)
                        .header("X-Academy-Id", academy)
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY)),
                Arguments.of("metrics", (RouteFactory) academy -> put(
                        "/api/v1/evaluation-events/{id}/metrics", EVENT_ID)
                        .header("X-Academy-Id", academy)
                        .contentType(MediaType.APPLICATION_JSON).content(METRICS_BODY)),
                Arguments.of("start", (RouteFactory) academy -> post(
                        "/api/v1/evaluation-events/{id}/start", EVENT_ID).header("X-Academy-Id", academy)),
                Arguments.of("complete", (RouteFactory) academy -> post(
                        "/api/v1/evaluation-events/{id}/complete", EVENT_ID).header("X-Academy-Id", academy)),
                Arguments.of("cancel", (RouteFactory) academy -> post(
                        "/api/v1/evaluation-events/{id}/cancel", EVENT_ID).header("X-Academy-Id", academy)));
    }

    static Stream<Arguments> allRoutes() {
        return Stream.concat(managementRoutes(), Stream.of(
                Arguments.of("list", (RouteFactory) academy -> get(
                        "/api/v1/training-sessions/{id}/evaluation-events", SESSION_ID)
                        .header("X-Academy-Id", academy)),
                Arguments.of("get", (RouteFactory) academy -> get(
                        "/api/v1/evaluation-events/{id}", EVENT_ID).header("X-Academy-Id", academy))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allRoutes")
    void unauthenticatedIs401(String label, RouteFactory route) throws Exception {
        mockMvc.perform(route.build(ACADEMY_ID)).andExpect(status().isUnauthorized());
        verifyNoInteractions(eventService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allRoutes")
    void playerIs403BeforeReachingService(String label, RouteFactory route) throws Exception {
        mockMvc.perform(route.build(ACADEMY_ID).with(user("player").roles("PLAYER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access denied"));
        verifyNoInteractions(eventService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("managementRoutes")
    void coachWithoutManageAccessIs403(String label, RouteFactory route) throws Exception {
        stubAll(ACADEMY_ID, new UnauthorizedAccessException(
                "Head coach assignment is required to manage training sessions"));

        mockMvc.perform(route.build(ACADEMY_ID).with(user("assistant").roles("COACH")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Access denied"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allRoutes")
    void foreignAcademyIs404(String label, RouteFactory route) throws Exception {
        stubAll(FOREIGN_ACADEMY_ID, new NotFoundException("Evaluation event not found in the selected academy"));

        mockMvc.perform(route.build(FOREIGN_ACADEMY_ID).with(user("coach").roles("COACH")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not found"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allRoutes")
    void missingAcademyHeaderIs400BeforeReachingService(String label, RouteFactory ignored) throws Exception {
        mockMvc.perform(withoutAcademyHeader(label).with(user("coach").roles("COACH")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(eventService);
    }

    @Test
    void createReturns201InSnakeCase() throws Exception {
        when(eventService.create(eq(SESSION_ID), eq(ACADEMY_ID), any(), any())).thenReturn(response());

        mockMvc.perform(post("/api/v1/training-sessions/{id}/evaluation-events", SESSION_ID)
                        .header("X-Academy-Id", ACADEMY_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY)
                        .with(user("coach").roles("COACH")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.session_event_id").value(EVENT_ID.toString()))
                .andExpect(jsonPath("$.data.block_order").value(1))
                .andExpect(jsonPath("$.data.template_code").value("JUEGO_REDUCIDO"))
                .andExpect(jsonPath("$.data.status").value("PLANNED"))
                .andExpect(jsonPath("$.data.metrics[0].criterion_id").value(CRITERION_ID.toString()))
                .andExpect(jsonPath("$.data.metrics[0].display_order").value(1));
    }

    @Test
    void stateConflictsAre409WithTheServiceMessage() throws Exception {
        when(eventService.start(eq(EVENT_ID), eq(ACADEMY_ID), any()))
                .thenThrow(new ConflictException("An event can only start while its session is IN_PROGRESS"));

        mockMvc.perform(post("/api/v1/evaluation-events/{id}/start", EVENT_ID)
                        .header("X-Academy-Id", ACADEMY_ID)
                        .with(user("coach").roles("COACH")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An event can only start while its session is IN_PROGRESS"));
    }

    @Test
    void invalidBodiesAre400BeforeReachingService() throws Exception {
        String fourMetrics = "{\"metric_criterion_ids\":[\"" + UUID.randomUUID() + "\",\"" + UUID.randomUUID()
                + "\",\"" + UUID.randomUUID() + "\",\"" + UUID.randomUUID() + "\"]}";
        List<MockHttpServletRequestBuilder> invalid = List.of(
                post("/api/v1/training-sessions/{id}/evaluation-events", SESSION_ID)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"),
                post("/api/v1/training-sessions/{id}/evaluation-events", SESSION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"template_id\":\"" + TEMPLATE_ID + "\",\"block_order\":2}"),
                post("/api/v1/training-sessions/{id}/evaluation-events", SESSION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateId\":\"" + TEMPLATE_ID + "\"}"),
                put("/api/v1/evaluation-events/{id}/metrics", EVENT_ID)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"metric_criterion_ids\":[]}"),
                put("/api/v1/evaluation-events/{id}/metrics", EVENT_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(fourMetrics),
                put("/api/v1/evaluation-events/{id}/metrics", EVENT_ID)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"));
        for (MockHttpServletRequestBuilder request : invalid) {
            mockMvc.perform(request.header("X-Academy-Id", ACADEMY_ID).with(user("coach").roles("COACH")))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(eventService);
    }

    private MockHttpServletRequestBuilder withoutAcademyHeader(String label) {
        return switch (label) {
            case "create" -> post("/api/v1/training-sessions/{id}/evaluation-events", SESSION_ID)
                    .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY);
            case "metrics" -> put("/api/v1/evaluation-events/{id}/metrics", EVENT_ID)
                    .contentType(MediaType.APPLICATION_JSON).content(METRICS_BODY);
            case "start", "complete", "cancel" -> post("/api/v1/evaluation-events/{id}/" + label, EVENT_ID);
            case "list" -> get("/api/v1/training-sessions/{id}/evaluation-events", SESSION_ID);
            case "get" -> get("/api/v1/evaluation-events/{id}", EVENT_ID);
            default -> throw new IllegalArgumentException(label);
        };
    }

    private void stubAll(UUID academyId, RuntimeException ex) {
        when(eventService.create(any(), eq(academyId), any(), any())).thenThrow(ex);
        when(eventService.replaceMetrics(any(), eq(academyId), any(), any())).thenThrow(ex);
        when(eventService.start(any(), eq(academyId), any())).thenThrow(ex);
        when(eventService.complete(any(), eq(academyId), any())).thenThrow(ex);
        when(eventService.cancel(any(), eq(academyId), any())).thenThrow(ex);
        when(eventService.list(any(), eq(academyId), any())).thenThrow(ex);
        when(eventService.get(any(), eq(academyId), any())).thenThrow(ex);
    }

    private static SessionEvaluationEventResponse response() {
        OffsetDateTime created = OffsetDateTime.parse("2026-11-23T10:00:00-06:00");
        return new SessionEvaluationEventResponse(EVENT_ID, SESSION_ID, TEMPLATE_ID, "JUEGO_REDUCIDO",
                "Juego reducido", 1, SessionEvaluationEventStatus.PLANNED, null, null, created, created,
                List.of(new SessionEventMetricResponse(CRITERION_ID, "PASES", "Pases", "count", 1)));
    }
}

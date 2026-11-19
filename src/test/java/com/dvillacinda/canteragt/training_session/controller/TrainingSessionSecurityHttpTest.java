package com.dvillacinda.canteragt.training_session.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.data.domain.Page;
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
import com.dvillacinda.canteragt.training_session.dto.TrainingSessionFilter;
import com.dvillacinda.canteragt.training_session.service.TrainingSessionService;

/**
 * HTTP boundary of the training session API: authentication, role gate, academy header handling and the
 * standard error body. The service is mocked; the HEAD_COACH / academy-scope decisions themselves are covered by
 * {@code TrainingSessionAccessPolicyTest}, here we verify how those decisions surface over HTTP.
 */
@WebMvcTest(value = TrainingSessionController.class, properties = {
        "app.security.cors.allowed-origins=http://localhost",
        "app.security.oauth2.client-id=test",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.test"
})
@Import({ SecurityConfig.class, RestAuthenticationEntryPoint.class, GlobalExceptionHandler.class,
        TrainingSessionController.class, TrainingSessionSecurityHttpTest.WebSecurityEnabler.class })
class TrainingSessionSecurityHttpTest {
    private static final UUID ACADEMY_ID = UUID.randomUUID();
    private static final UUID FOREIGN_ACADEMY_ID = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final String CREATE_BODY =
            "{\"name\":\"Technical session\",\"session_date\":\"2026-11-17T16:00:00-06:00\"}";
    private static final String UPDATE_BODY = "{\"name\":\"Renamed\"}";

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

    /** Builds a management request; a {@code null} academy omits the {@code X-Academy-Id} header. */
    @FunctionalInterface
    interface RouteFactory {
        MockHttpServletRequestBuilder build(UUID academyId);
    }

    private static MockHttpServletRequestBuilder withAcademy(MockHttpServletRequestBuilder builder, UUID academyId) {
        return academyId == null ? builder : builder.header("X-Academy-Id", academyId);
    }

    static Stream<Arguments> managementRoutes() {
        return Stream.of(
                Arguments.of("create", (RouteFactory) academy -> withAcademy(post(
                        "/api/v1/academy-categories/{id}/training-sessions", CATEGORY_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY), academy)),
                Arguments.of("update", (RouteFactory) academy -> withAcademy(patch(
                        "/api/v1/training-sessions/{id}", SESSION_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY), academy)),
                Arguments.of("start", (RouteFactory) academy -> withAcademy(post(
                        "/api/v1/training-sessions/{id}/start", SESSION_ID), academy)),
                Arguments.of("complete", (RouteFactory) academy -> withAcademy(post(
                        "/api/v1/training-sessions/{id}/complete", SESSION_ID), academy)),
                Arguments.of("cancel", (RouteFactory) academy -> withAcademy(post(
                        "/api/v1/training-sessions/{id}/cancel", SESSION_ID), academy)));
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("managementRoutes")
    void unauthenticatedManagementRoutesReturn401(String label, RouteFactory route) throws Exception {
        mockMvc.perform(route.build(ACADEMY_ID))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(trainingSessionService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("managementRoutes")
    void playerIsRejectedOnManagementRoutesBeforeReachingService(String label, RouteFactory route)
            throws Exception {
        mockMvc.perform(route.build(ACADEMY_ID).with(user("player").roles("PLAYER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Access denied"));
        verifyNoInteractions(trainingSessionService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("managementRoutes")
    void nonHeadCoachGets403WithStandardErrorBody(String label, RouteFactory route) throws Exception {
        UnauthorizedAccessException denied =
                new UnauthorizedAccessException("Head coach assignment is required to manage training sessions");
        when(trainingSessionService.create(any(), any(), any(), any())).thenThrow(denied);
        when(trainingSessionService.update(any(), any(), any(), any())).thenThrow(denied);
        when(trainingSessionService.start(any(), any(), any())).thenThrow(denied);
        when(trainingSessionService.complete(any(), any(), any())).thenThrow(denied);
        when(trainingSessionService.cancel(any(), any(), any())).thenThrow(denied);

        mockMvc.perform(route.build(ACADEMY_ID).with(user("assistant").roles("COACH")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Access denied"))
                .andExpect(jsonPath("$.message")
                        .value("Head coach assignment is required to manage training sessions"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("managementRoutes")
    void foreignAcademyHeaderGets404WithoutLeakingSession(String label, RouteFactory route) throws Exception {
        NotFoundException notFound = new NotFoundException("Training session not found in the selected academy");
        when(trainingSessionService.create(any(), eq(FOREIGN_ACADEMY_ID), any(), any())).thenThrow(notFound);
        when(trainingSessionService.update(any(), eq(FOREIGN_ACADEMY_ID), any(), any())).thenThrow(notFound);
        when(trainingSessionService.start(any(), eq(FOREIGN_ACADEMY_ID), any())).thenThrow(notFound);
        when(trainingSessionService.complete(any(), eq(FOREIGN_ACADEMY_ID), any())).thenThrow(notFound);
        when(trainingSessionService.cancel(any(), eq(FOREIGN_ACADEMY_ID), any())).thenThrow(notFound);

        mockMvc.perform(route.build(FOREIGN_ACADEMY_ID).with(user("coach").roles("COACH")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not found"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("managementRoutes")
    void missingAcademyHeaderIsRejectedBeforeReachingService(String label, RouteFactory route) throws Exception {
        mockMvc.perform(route.build(null).with(user("coach").roles("COACH")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(trainingSessionService);
    }

    @Test
    void malformedAcademyHeaderIsRejectedWithStandardErrorBody() throws Exception {
        mockMvc.perform(get("/api/v1/training-sessions/{id}", SESSION_ID)
                        .header("X-Academy-Id", "not-a-uuid")
                        .with(user("coach").roles("COACH")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Invalid parameter"));
        verifyNoInteractions(trainingSessionService);
    }

    @Test
    void readOfSessionInForeignAcademyReturns404() throws Exception {
        when(trainingSessionService.get(eq(SESSION_ID), eq(FOREIGN_ACADEMY_ID), any()))
                .thenThrow(new NotFoundException("Training session not found in the selected academy"));

        mockMvc.perform(get("/api/v1/training-sessions/{id}", SESSION_ID)
                        .header("X-Academy-Id", FOREIGN_ACADEMY_ID)
                        .with(user("coach").roles("COACH")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not found"));
    }

    @Test
    void invalidTransitionReturns409WithStandardErrorBody() throws Exception {
        when(trainingSessionService.complete(eq(SESSION_ID), eq(ACADEMY_ID), any()))
                .thenThrow(new ConflictException("Only in-progress sessions can be completed"));

        mockMvc.perform(post("/api/v1/training-sessions/{id}/complete", SESSION_ID)
                        .header("X-Academy-Id", ACADEMY_ID)
                        .with(user("coach").roles("COACH")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Only in-progress sessions can be completed"));
    }

    @Test
    void invalidCreateBodyReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/academy-categories/{id}/training-sessions", CATEGORY_ID)
                        .header("X-Academy-Id", ACADEMY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}")
                        .with(user("coach").roles("COACH")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.name").exists());
        verifyNoInteractions(trainingSessionService);
    }
}

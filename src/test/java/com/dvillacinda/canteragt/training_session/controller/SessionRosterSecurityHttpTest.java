package com.dvillacinda.canteragt.training_session.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
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
import com.dvillacinda.canteragt.shared.exception.RejectedItemsException;
import com.dvillacinda.canteragt.shared.exception.UnauthorizedAccessException;
import com.dvillacinda.canteragt.training_session.dto.SessionPlayerResponse;
import com.dvillacinda.canteragt.training_session.enums.AttendanceStatus;
import com.dvillacinda.canteragt.training_session.service.SessionRosterService;

/** HTTP boundary of the roster/attendance routes; the service is mocked (access decisions live in the policy). */
@WebMvcTest(value = SessionRosterController.class, properties = {
        "app.security.cors.allowed-origins=http://localhost",
        "app.security.oauth2.client-id=test",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.test"
})
@Import({ SecurityConfig.class, RestAuthenticationEntryPoint.class, GlobalExceptionHandler.class,
        SessionRosterController.class, SessionRosterSecurityHttpTest.WebSecurityEnabler.class })
class SessionRosterSecurityHttpTest {
    private static final UUID ACADEMY_ID = UUID.randomUUID();
    private static final UUID FOREIGN_ACADEMY_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID PLAYER_ID = UUID.randomUUID();

    @Autowired private MockMvc mockMvc;
    @MockitoBean private SessionRosterService sessionRosterService;
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
                        "/api/v1/training-sessions/{id}/players", SESSION_ID)
                        .header("X-Academy-Id", academy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"player_ids\":[\"" + PLAYER_ID + "\"]}")),
                Arguments.of("remove", (RouteFactory) academy -> delete(
                        "/api/v1/training-sessions/{id}/players/{playerId}", SESSION_ID, PLAYER_ID)
                        .header("X-Academy-Id", academy)),
                Arguments.of("attendance", (RouteFactory) academy -> patch(
                        "/api/v1/training-sessions/{id}/players/{playerId}/attendance", SESSION_ID, PLAYER_ID)
                        .header("X-Academy-Id", academy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attendance_status\":\"PRESENT\"}")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("managementRoutes")
    void unauthenticatedIs401(String label, RouteFactory route) throws Exception {
        mockMvc.perform(route.build(ACADEMY_ID)).andExpect(status().isUnauthorized());
        verifyNoInteractions(sessionRosterService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("managementRoutes")
    void playerIs403BeforeReachingService(String label, RouteFactory route) throws Exception {
        mockMvc.perform(route.build(ACADEMY_ID).with(user("player").roles("PLAYER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access denied"));
        verifyNoInteractions(sessionRosterService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("managementRoutes")
    void nonHeadCoachIs403(String label, RouteFactory route) throws Exception {
        UnauthorizedAccessException denied =
                new UnauthorizedAccessException("Head coach assignment is required to manage training sessions");
        when(sessionRosterService.addPlayers(any(), any(), any(), any())).thenThrow(denied);
        doThrow(denied).when(sessionRosterService).removePlayer(any(), any(), any(), any());
        when(sessionRosterService.updateAttendance(any(), any(), any(), any(), any())).thenThrow(denied);

        mockMvc.perform(route.build(ACADEMY_ID).with(user("assistant").roles("COACH")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Access denied"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("managementRoutes")
    void foreignAcademyIs404(String label, RouteFactory route) throws Exception {
        NotFoundException notFound = new NotFoundException("Training session not found in the selected academy");
        when(sessionRosterService.addPlayers(any(), eq(FOREIGN_ACADEMY_ID), any(), any())).thenThrow(notFound);
        doThrow(notFound).when(sessionRosterService).removePlayer(any(), eq(FOREIGN_ACADEMY_ID), any(), any());
        when(sessionRosterService.updateAttendance(any(), eq(FOREIGN_ACADEMY_ID), any(), any(), any()))
                .thenThrow(notFound);

        mockMvc.perform(route.build(FOREIGN_ACADEMY_ID).with(user("coach").roles("COACH")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not found"));
    }

    @Test
    void rosterReadReturnsOnlyStaffVisibleFields() throws Exception {
        when(sessionRosterService.list(eq(SESSION_ID), eq(ACADEMY_ID), any())).thenReturn(List.of(
                new SessionPlayerResponse(UUID.randomUUID(), PLAYER_ID, "Ana", "López", "GK",
                        AttendanceStatus.PENDING, OffsetDateTime.parse("2026-11-17T10:00:00-06:00"))));

        mockMvc.perform(get("/api/v1/training-sessions/{id}/players", SESSION_ID)
                        .header("X-Academy-Id", ACADEMY_ID)
                        .with(user("coach").roles("COACH")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].player_id").value(PLAYER_ID.toString()))
                .andExpect(jsonPath("$.data[0].first_name").value("Ana"))
                .andExpect(jsonPath("$.data[0].principal_position").value("GK"))
                .andExpect(jsonPath("$.data[0].attendance_status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].email").doesNotExist())
                .andExpect(jsonPath("$.data[0].keycloak_id").doesNotExist());
    }

    @Test
    void addReturns201WithAddedPlayers() throws Exception {
        when(sessionRosterService.addPlayers(eq(SESSION_ID), eq(ACADEMY_ID), any(), any())).thenReturn(List.of(
                new SessionPlayerResponse(UUID.randomUUID(), PLAYER_ID, "Ana", "López", "GK",
                        AttendanceStatus.PENDING, null)));

        mockMvc.perform(post("/api/v1/training-sessions/{id}/players", SESSION_ID)
                        .header("X-Academy-Id", ACADEMY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"all_active\":true}")
                        .with(user("coach").roles("COACH")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data[0].attendance_status").value("PENDING"));
    }

    @Test
    void rejectedBatchListsEveryRejectedIdWithItsReason() throws Exception {
        UUID other = UUID.randomUUID();
        when(sessionRosterService.addPlayers(eq(SESSION_ID), eq(ACADEMY_ID), any(), any()))
                .thenThrow(new RejectedItemsException(HttpStatus.BAD_REQUEST,
                        "Batch rejected: no player was added. See rejected player ids",
                        Map.of(PLAYER_ID.toString(), "NO_CURRENT_ASSIGNMENT_IN_CATEGORY",
                                other.toString(), "NO_CURRENT_ASSIGNMENT_IN_CATEGORY")));

        mockMvc.perform(post("/api/v1/training-sessions/{id}/players", SESSION_ID)
                        .header("X-Academy-Id", ACADEMY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"player_ids\":[\"" + PLAYER_ID + "\",\"" + other + "\"]}")
                        .with(user("coach").roles("COACH")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.field_errors['" + PLAYER_ID + "']").value("NO_CURRENT_ASSIGNMENT_IN_CATEGORY"))
                .andExpect(jsonPath("$.field_errors['" + other + "']").value("NO_CURRENT_ASSIGNMENT_IN_CATEGORY"));
    }

    @Test
    void duplicatePlayerIs409WithRejectedId() throws Exception {
        when(sessionRosterService.addPlayers(eq(SESSION_ID), eq(ACADEMY_ID), any(), any()))
                .thenThrow(new RejectedItemsException(HttpStatus.CONFLICT, "Batch rejected",
                        Map.of(PLAYER_ID.toString(), "ALREADY_IN_SESSION")));

        mockMvc.perform(post("/api/v1/training-sessions/{id}/players", SESSION_ID)
                        .header("X-Academy-Id", ACADEMY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"player_ids\":[\"" + PLAYER_ID + "\"]}")
                        .with(user("coach").roles("COACH")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.field_errors['" + PLAYER_ID + "']").value("ALREADY_IN_SESSION"));
    }

    @Test
    void batchOverSixtyIsRejectedBeforeReachingService() throws Exception {
        String ids = IntStream.range(0, 61).mapToObj(i -> "\"" + UUID.randomUUID() + "\"")
                .collect(Collectors.joining(","));

        mockMvc.perform(post("/api/v1/training-sessions/{id}/players", SESSION_ID)
                        .header("X-Academy-Id", ACADEMY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"player_ids\":[" + ids + "]}")
                        .with(user("coach").roles("COACH")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"));
        verifyNoInteractions(sessionRosterService);
    }

    @Test
    void removeReturns204AndRecordedAttendanceIs409() throws Exception {
        mockMvc.perform(delete("/api/v1/training-sessions/{id}/players/{playerId}", SESSION_ID, PLAYER_ID)
                        .header("X-Academy-Id", ACADEMY_ID)
                        .with(user("coach").roles("COACH")))
                .andExpect(status().isNoContent());

        doThrow(new ConflictException("Player attendance is already recorded (PRESENT)"))
                .when(sessionRosterService).removePlayer(eq(SESSION_ID), eq(ACADEMY_ID), eq(PLAYER_ID), any());
        mockMvc.perform(delete("/api/v1/training-sessions/{id}/players/{playerId}", SESSION_ID, PLAYER_ID)
                        .header("X-Academy-Id", ACADEMY_ID)
                        .with(user("coach").roles("COACH")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void attendanceRequiresStatusAndRejectsUnknownValues() throws Exception {
        mockMvc.perform(patch("/api/v1/training-sessions/{id}/players/{playerId}/attendance", SESSION_ID, PLAYER_ID)
                        .header("X-Academy-Id", ACADEMY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(user("coach").roles("COACH")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/training-sessions/{id}/players/{playerId}/attendance", SESSION_ID, PLAYER_ID)
                        .header("X-Academy-Id", ACADEMY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attendance_status\":\"MAYBE\"}")
                        .with(user("coach").roles("COACH")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(sessionRosterService);
    }

    @Test
    void missingAcademyHeaderIs400() throws Exception {
        mockMvc.perform(get("/api/v1/training-sessions/{id}/players", SESSION_ID)
                        .with(user("coach").roles("COACH")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(sessionRosterService);
    }
}

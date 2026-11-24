package com.dvillacinda.canteragt.evaluation_template.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.dvillacinda.canteragt.auth.security.KeycloakJwtAuthenticationConverter;
import com.dvillacinda.canteragt.auth.security.RestAuthenticationEntryPoint;
import com.dvillacinda.canteragt.evaluation_template.dto.EvaluationCreateRequest;
import com.dvillacinda.canteragt.evaluation_template.dto.EvaluationResponse;
import com.dvillacinda.canteragt.evaluation_template.enums.EvaluationWarningCode;
import com.dvillacinda.canteragt.evaluation_template.service.EvaluationService;
import com.dvillacinda.canteragt.evaluation_template.service.EvaluationVoidService;
import com.dvillacinda.canteragt.shared.config.SecurityConfig;
import com.dvillacinda.canteragt.shared.exception.ConflictException;
import com.dvillacinda.canteragt.shared.exception.GlobalExceptionHandler;

/** HTTP boundary of the evaluation POST (T4.4); the service is mocked, the access rules are tested elsewhere. */
@WebMvcTest(value = PlayerEvaluationController.class, properties = {
        "app.security.cors.allowed-origins=http://localhost",
        "app.security.oauth2.client-id=test",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.test"
})
@Import({ SecurityConfig.class, RestAuthenticationEntryPoint.class, GlobalExceptionHandler.class,
        PlayerEvaluationController.class, PlayerEvaluationSecurityHttpTest.WebSecurityEnabler.class })
class PlayerEvaluationSecurityHttpTest {
    private static final UUID PLAYER_ID = UUID.randomUUID();
    private static final UUID ACADEMY_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID TEMPLATE_ID = UUID.randomUUID();
    private static final UUID CRITERION_ID = UUID.randomUUID();
    private static final String EVENT_BODY = "{\"template_id\":\"" + TEMPLATE_ID + "\",\"template_version\":1,"
            + "\"results\":[{\"criterion_id\":\"" + CRITERION_ID + "\",\"level\":2}],"
            + "\"session_event_id\":\"" + EVENT_ID + "\"}";
    private static final String STANDALONE_BODY = "{\"template_id\":\"" + TEMPLATE_ID + "\",\"template_version\":1,"
            + "\"results\":[{\"criterion_id\":\"" + CRITERION_ID + "\",\"level\":2}]}";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private EvaluationService evaluationService;
    @MockitoBean private EvaluationVoidService evaluationVoidService;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private KeycloakJwtAuthenticationConverter jwtAuthenticationConverter;

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class WebSecurityEnabler {
    }

    @Test
    void unauthenticatedIs401() throws Exception {
        mockMvc.perform(post("/api/v1/players/{id}/evaluations", PLAYER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(EVENT_BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(evaluationService);
    }

    /** R-4: the route only admits COACH, so an admin without the COACH role never reaches data entry. */
    @Test
    void playerAndAdminsWithoutCoachRoleAre403() throws Exception {
        for (String role : List.of("PLAYER", "ACADEMY_ADMIN", "SYSTEM_ADMIN")) {
            mockMvc.perform(post("/api/v1/players/{id}/evaluations", PLAYER_ID)
                            .header("X-Academy-Id", ACADEMY_ID)
                            .contentType(MediaType.APPLICATION_JSON).content(EVENT_BODY)
                            .with(user("someone").roles(role)))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(evaluationService);
    }

    @Test
    void eventEvaluationPassesHeaderAndEventAndReturnsWarningsInSnakeCase() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(evaluationService.create(eq(PLAYER_ID), eq(ACADEMY_ID), any(), any())).thenReturn(
                new EvaluationResponse(UUID.randomUUID(), PLAYER_ID, TEMPLATE_ID, 1, null, "COMPLETED",
                        UUID.randomUUID(), List.of(), null, null, null, sessionId, EVENT_ID,
                        List.of(new EvaluationResponse.Warning(EvaluationWarningCode.FREQUENCY_TOO_SOON, "soon")),
                        null, null, null));

        mockMvc.perform(post("/api/v1/players/{id}/evaluations", PLAYER_ID)
                        .header("X-Academy-Id", ACADEMY_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(EVENT_BODY)
                        .with(user("coach").roles("COACH")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.session_id").value(sessionId.toString()))
                .andExpect(jsonPath("$.data.session_event_id").value(EVENT_ID.toString()))
                .andExpect(jsonPath("$.data.warnings[0].code").value("FREQUENCY_TOO_SOON"))
                .andExpect(jsonPath("$.data.warnings[0].message").value("soon"));

        ArgumentCaptor<EvaluationCreateRequest> request = ArgumentCaptor.forClass(EvaluationCreateRequest.class);
        verify(evaluationService).create(eq(PLAYER_ID), eq(ACADEMY_ID), request.capture(), any());
        assertEquals(EVENT_ID, request.getValue().sessionEventId());
    }

    /** E7: without session_event_id and without the header, the request reaches the service as before. */
    @Test
    void standaloneEvaluationNeedsNoAcademyHeader() throws Exception {
        when(evaluationService.create(eq(PLAYER_ID), eq(null), any(), any())).thenReturn(
                new EvaluationResponse(UUID.randomUUID(), PLAYER_ID, TEMPLATE_ID, 1, null, "COMPLETED",
                        UUID.randomUUID(), List.of(), null, null, null, null, null, List.of(), null, null, null));

        mockMvc.perform(post("/api/v1/players/{id}/evaluations", PLAYER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(STANDALONE_BODY)
                        .with(user("coach").roles("COACH")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.warnings").isEmpty());

        ArgumentCaptor<EvaluationCreateRequest> request = ArgumentCaptor.forClass(EvaluationCreateRequest.class);
        verify(evaluationService).create(eq(PLAYER_ID), eq(null), request.capture(), any());
        assertNull(request.getValue().sessionEventId());
    }

    @Test
    void conflictsAndMalformedValuesKeepTheStandardErrorFormat() throws Exception {
        when(evaluationService.create(eq(PLAYER_ID), eq(ACADEMY_ID), any(), any()))
                .thenThrow(new ConflictException("Player is not convened in the event's session"));

        mockMvc.perform(post("/api/v1/players/{id}/evaluations", PLAYER_ID)
                        .header("X-Academy-Id", ACADEMY_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(EVENT_BODY)
                        .with(user("coach").roles("COACH")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Player is not convened in the event's session"));
        mockMvc.perform(post("/api/v1/players/{id}/evaluations", PLAYER_ID)
                        .header("X-Academy-Id", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON).content(EVENT_BODY)
                        .with(user("coach").roles("COACH")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/players/{id}/evaluations", PLAYER_ID)
                        .header("X-Academy-Id", ACADEMY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EVENT_BODY.replace("\"session_event_id\"", "\"sessionEventId\""))
                        .with(user("coach").roles("COACH")))
                .andExpect(status().isBadRequest());
    }

    // ---- T4.5: reads by admins and voiding ------------------------------------------------------------------------

    /** T4.5-D2: admins reach the read service (the policy decides the scope); PLAYER does not. */
    @Test
    void adminsCanCallTheReadRouteButPlayersCannot() throws Exception {
        UUID evaluationId = UUID.randomUUID();
        when(evaluationService.get(eq(PLAYER_ID), eq(evaluationId), any())).thenReturn(voidedResponse(evaluationId));

        for (String role : List.of("SYSTEM_ADMIN", "ACADEMY_ADMIN", "COACH")) {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .get("/api/v1/players/{id}/evaluations/{evaluationId}", PLAYER_ID, evaluationId)
                            .with(user("someone").roles(role)))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/players/{id}/evaluations/{evaluationId}", PLAYER_ID, evaluationId)
                        .with(user("player").roles("PLAYER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void voidReturnsTheAuditFieldsInSnakeCase() throws Exception {
        UUID evaluationId = UUID.randomUUID();
        when(evaluationVoidService.voidEvaluation(eq(PLAYER_ID), eq(evaluationId), any(), any()))
                .thenReturn(voidedResponse(evaluationId));

        mockMvc.perform(post("/api/v1/players/{id}/evaluations/{evaluationId}/void", PLAYER_ID, evaluationId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Wrong player\"}")
                        .with(user("coach").roles("COACH")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VOIDED"))
                .andExpect(jsonPath("$.data.voided_at").exists())
                .andExpect(jsonPath("$.data.voided_by_coach_id").exists())
                .andExpect(jsonPath("$.data.void_reason").value("Wrong player"));
    }

    /** Admins and players cannot void at the boundary; voided_by_coach_id needs a coach (ADR-0004 DP-2.4). */
    @Test
    void voidIsCoachOnlyAndNeedsAToken() throws Exception {
        UUID evaluationId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/players/{id}/evaluations/{evaluationId}/void", PLAYER_ID, evaluationId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        for (String role : List.of("PLAYER", "ACADEMY_ADMIN", "SYSTEM_ADMIN")) {
            mockMvc.perform(post("/api/v1/players/{id}/evaluations/{evaluationId}/void", PLAYER_ID, evaluationId)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}")
                            .with(user("someone").roles(role)))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(evaluationVoidService);
    }

    @Test
    void voidReasonIsRequiredAndAtMost500CharactersAndAuthorCannotBeSent() throws Exception {
        UUID evaluationId = UUID.randomUUID();
        for (String body : List.of("{}", "{\"reason\":\"   \"}", "{\"reason\":\"" + "x".repeat(501) + "\"}",
                "{\"reason\":\"ok\",\"voided_by_coach_id\":\"" + UUID.randomUUID() + "\"}")) {
            mockMvc.perform(post("/api/v1/players/{id}/evaluations/{evaluationId}/void", PLAYER_ID, evaluationId)
                            .contentType(MediaType.APPLICATION_JSON).content(body)
                            .with(user("coach").roles("COACH")))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(evaluationVoidService);
    }

    @Test
    void secondVoidIs409() throws Exception {
        UUID evaluationId = UUID.randomUUID();
        when(evaluationVoidService.voidEvaluation(eq(PLAYER_ID), eq(evaluationId), any(), any()))
                .thenThrow(new ConflictException("Evaluation is already voided"));

        mockMvc.perform(post("/api/v1/players/{id}/evaluations/{evaluationId}/void", PLAYER_ID, evaluationId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"again\"}")
                        .with(user("coach").roles("COACH")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Evaluation is already voided"));
    }

    private static EvaluationResponse voidedResponse(UUID evaluationId) {
        return new EvaluationResponse(evaluationId, PLAYER_ID, TEMPLATE_ID, 1, null, "VOIDED", UUID.randomUUID(),
                List.of(), null, null, null, null, null, List.of(),
                java.time.LocalDateTime.of(2026, 11, 23, 10, 0), UUID.randomUUID(), "Wrong player");
    }
}

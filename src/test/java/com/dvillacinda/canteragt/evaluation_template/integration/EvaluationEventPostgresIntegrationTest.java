package com.dvillacinda.canteragt.evaluation_template.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.dvillacinda.canteragt.academy.entity.AcademyEntity;
import com.dvillacinda.canteragt.academy_admin.entity.AcademyAdminEntity;
import com.dvillacinda.canteragt.shared.enums.Status;
import com.dvillacinda.canteragt.academy_category.entity.AcademyCategoryEntity;
import com.dvillacinda.canteragt.coach.entity.CoachEntity;
import com.dvillacinda.canteragt.coach_role.enums.CoachRoleCode;
import com.dvillacinda.canteragt.evaluation_template.dto.EvaluationCreateRequest;
import com.dvillacinda.canteragt.evaluation_template.dto.EvaluationResponse;
import com.dvillacinda.canteragt.evaluation_template.service.EvaluationService;
import com.dvillacinda.canteragt.auth.service.KeycloakUserService;
import com.dvillacinda.canteragt.player.entity.PlayerEntity;
import com.dvillacinda.canteragt.shared.exception.ConflictException;
import com.dvillacinda.canteragt.support.TestDataFactory;
import com.dvillacinda.canteragt.training_session.entity.SessionPlayerEntity;
import com.dvillacinda.canteragt.training_session.entity.SessionStaffEntity;
import com.dvillacinda.canteragt.training_session.entity.TrainingSessionEntity;
import com.dvillacinda.canteragt.training_session.enums.AttendanceStatus;
import com.dvillacinda.canteragt.training_session.enums.SessionParticipationType;
import com.dvillacinda.canteragt.training_session.enums.TrainingSessionStatus;
import com.dvillacinda.canteragt.training_session.service.TrainingSessionAccessPolicy;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.ObjectMapper;

/**
 * T4.4 against PostgreSQL over HTTP, with the real security chain and the real {@code TrainingSessionAccessPolicy}
 * (only Keycloak's admin API is mocked): evaluations in an event, the data entry matrix (R-4, E5, E6), player
 * eligibility (E3), state and academy checks, duplicates and voiding (E8), non-blocking warnings, and the concurrent
 * duplicate (E4). The event uses two metrics that are not the template defaults (E2, R-1).
 * Requires the isolated {@code postgres-test} database (see docs/testing.md).
 */
@SpringBootTest
@AutoConfigureMockMvc
class EvaluationEventPostgresIntegrationTest {
    private static final String TEMPLATE = "JUEGO_REDUCIDO";   // RUBRIC 1-3 metrics, 3 of 6 active by default

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EvaluationService evaluationService;
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Clock clock;

    @MockitoBean private KeycloakUserService keycloakUserService;
    @MockitoSpyBean private TrainingSessionAccessPolicy accessPolicy;

    private TestDataFactory factory;
    private Fixture fx;
    private UUID templateId;
    private int templateVersion;
    /** Scored metrics of the template that are NOT active by default: the event activates these two. */
    private List<UUID> eventMetrics;
    private UUID defaultMetric;

    @BeforeEach
    void setUp() {
        templateId = jdbc.queryForObject(
                "SELECT template_id FROM evaluation_templates WHERE code = ? ORDER BY version DESC LIMIT 1",
                UUID.class, TEMPLATE);
        templateVersion = jdbc.queryForObject("SELECT version FROM evaluation_templates WHERE template_id = ?",
                Integer.class, templateId);
        eventMetrics = jdbc.queryForList("""
                SELECT criterion_id FROM evaluation_criteria
                 WHERE template_id = ? AND is_scored AND NOT is_active_by_default ORDER BY display_order LIMIT 2""",
                UUID.class, templateId);
        defaultMetric = jdbc.queryForObject("""
                SELECT criterion_id FROM evaluation_criteria
                 WHERE template_id = ? AND is_scored AND is_active_by_default ORDER BY display_order LIMIT 1""",
                UUID.class, templateId);
        fx = createFixture();
    }

    /** E1 + E2: the event's metrics are enough (no template defaults); session and category come from the event. */
    @Test
    void headCoachRecordsAnEvaluationWithOnlyTheEventMetrics() throws Exception {
        UUID event = event(fx.session(), "IN_PROGRESS");

        postInEvent(fx.present(), event, eventMetrics, "head", fx.academyId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.session_id").value(fx.session().toString()))
                .andExpect(jsonPath("$.data.session_event_id").value(event.toString()))
                .andExpect(jsonPath("$.data.warnings").isEmpty());

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT session_id, academy_category_id, status FROM evaluations
                 WHERE session_event_id = ? AND player_id = ?""", event, fx.present());
        assertEquals(fx.session(), row.get("session_id"));
        assertEquals(fx.categoryId(), row.get("academy_category_id"));
        assertEquals("COMPLETED", row.get("status"));

        // a scored default metric that the event did not activate is rejected
        postInEvent(fx.late(), event, List.of(eventMetrics.get(0), eventMetrics.get(1), defaultMetric), "head",
                fx.academyId())
                .andExpect(status().isBadRequest());
        // a missing event metric is rejected
        postInEvent(fx.late(), event, List.of(eventMetrics.get(0)), "head", fx.academyId())
                .andExpect(status().isBadRequest());
        assertEquals(0, evaluations(event, fx.late()));
    }

    /** R-4, E5, E6: requireDataEntryAccess over HTTP with real coach assignments and session staff. */
    @Test
    void dataEntryFollowsTheAccessPolicy() throws Exception {
        UUID event = event(fx.session(), "IN_PROGRESS");
        UUID plannedSessionEvent = event(fx.plannedSession(), "IN_PROGRESS");

        postInEvent(fx.present(), event, eventMetrics, "assistant", fx.academyId())
                .andExpect(status().isCreated());                       // staff ASSISTANT, session IN_PROGRESS
        postInEvent(fx.late(), event, eventMetrics, "outsider", fx.academyId())
                .andExpect(status().isForbidden());                     // E6: ASSISTANT outside session_staff
        postInEvent(fx.late(), event, eventMetrics, "fitness", fx.academyId())
                .andExpect(status().isForbidden());                     // FITNESS_COACH outside session_staff
        postInEvent(fx.late(), plannedSessionEvent, eventMetrics, "assistant", fx.academyId())
                .andExpect(status().isForbidden());                     // E5: staff, session not IN_PROGRESS
        postInEvent(fx.late(), event, eventMetrics, "foreign", fx.academyId())
                .andExpect(status().isNotFound());                      // coach of another academy
        assertEquals(0, evaluations(event, fx.late()));
    }

    /** E3 (T4.4-D2): absent or not convened players cannot be evaluated; nothing is persisted. */
    @Test
    void onlyConvenedPlayersPresentOrLateCanBeEvaluated() throws Exception {
        UUID event = event(fx.session(), "IN_PROGRESS");

        postInEvent(fx.late(), event, eventMetrics, "head", fx.academyId()).andExpect(status().isCreated());
        postInEvent(fx.absent(), event, eventMetrics, "head", fx.academyId()).andExpect(status().isConflict());
        postInEvent(fx.notConvened(), event, eventMetrics, "head", fx.academyId()).andExpect(status().isConflict());
        postInEvent(UUID.randomUUID(), event, eventMetrics, "head", fx.academyId()).andExpect(status().isNotFound());
        assertEquals(0, evaluations(event, fx.absent()) + evaluations(event, fx.notConvened()));
    }

    @Test
    void eventStateAcademyAndTemplateAreChecked() throws Exception {
        UUID planned = event(fx.session(), "PLANNED");
        UUID running = event(fx.session(), "IN_PROGRESS");

        postInEvent(fx.present(), planned, eventMetrics, "head", fx.academyId()).andExpect(status().isConflict());
        postInEvent(fx.present(), running, eventMetrics, "head", null).andExpect(status().isBadRequest());
        postInEvent(fx.present(), running, eventMetrics, "head", fx.otherAcademyId()).andExpect(status().isNotFound());
        postInEvent(fx.present(), UUID.randomUUID(), eventMetrics, "head", fx.academyId())
                .andExpect(status().isNotFound());

        Map<String, Object> wrongVersion = body(running, eventMetrics);
        wrongVersion.put("template_version", templateVersion + 1);
        post(fx.present(), wrongVersion, "head", fx.academyId()).andExpect(status().isBadRequest());
        assertEquals(0, evaluations(running, fx.present()));
    }

    /**
     * Rule 7, E8 and T4.5 over HTTP: the head coach voids what the assistant recorded (author from the JWT), a second
     * void is 409, and the player can be evaluated again in the same event.
     */
    @Test
    void voidOverHttpFreesTheEventForANewEvaluation() throws Exception {
        UUID event = event(fx.session(), "IN_PROGRESS");
        UUID evaluation = evaluationId(postInEvent(fx.present(), event, eventMetrics, "assistant", fx.academyId())
                .andExpect(status().isCreated()));
        postInEvent(fx.present(), event, eventMetrics, "head", fx.academyId()).andExpect(status().isConflict());

        voidEvaluation(fx.present(), evaluation, "head", "  Wrong player  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VOIDED"))
                .andExpect(jsonPath("$.data.voided_by_coach_id").value(fx.head().toString()))
                .andExpect(jsonPath("$.data.void_reason").value("Wrong player"));
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, voided_at, voided_by_coach_id, void_reason FROM evaluations WHERE evaluation_id = ?",
                evaluation);
        assertEquals("VOIDED", row.get("status"));
        assertEquals(fx.head(), row.get("voided_by_coach_id"));
        assertEquals("Wrong player", row.get("void_reason"));
        assertTrue(row.get("voided_at") != null, "evaluations_void_check requires voided_at");
        assertEquals(eventMetrics.size(), jdbc.queryForObject(
                "SELECT count(*) FROM evaluation_results WHERE evaluation_id = ?", Integer.class, evaluation),
                "voiding keeps the evidence");

        voidEvaluation(fx.present(), evaluation, "head", "again").andExpect(status().isConflict());
        postInEvent(fx.present(), event, eventMetrics, "head", fx.academyId()).andExpect(status().isCreated());
        assertEquals(2, evaluations(event, fx.present()));
    }

    /** DP-2.4, T4.5-D1/D2: creator, current head coach and admins in scope read; only coaches with access void. */
    @Test
    void readAndVoidFollowTheEvaluationAccessPolicy() throws Exception {
        UUID event = event(fx.session(), "IN_PROGRESS");
        UUID evaluation = evaluationId(postInEvent(fx.present(), event, eventMetrics, "assistant", fx.academyId())
                .andExpect(status().isCreated()));

        get(fx.present(), evaluation, coachJwt("assistant")).andExpect(status().isOk());      // creator
        get(fx.present(), evaluation, coachJwt("head")).andExpect(status().isOk());           // current head coach
        get(fx.present(), evaluation, adminJwt("admin")).andExpect(status().isOk());          // admin in scope
        get(fx.present(), evaluation, coachJwt("outsider")).andExpect(status().isNotFound()); // assistant, not creator
        get(fx.present(), evaluation, coachJwt("fitness")).andExpect(status().isNotFound());
        get(fx.present(), evaluation, coachJwt("foreign")).andExpect(status().isNotFound());
        get(fx.present(), evaluation, adminJwt("other-admin")).andExpect(status().isNotFound());
        get(fx.late(), evaluation, coachJwt("head")).andExpect(status().isNotFound());       // another player's path

        voidEvaluation(fx.present(), evaluation, "outsider", "x").andExpect(status().isNotFound());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/players/{playerId}/evaluations/{id}/void", fx.present(), evaluation)
                        .with(adminJwt("admin")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());                                              // admins never void
        assertEquals("COMPLETED", jdbc.queryForObject(
                "SELECT status FROM evaluations WHERE evaluation_id = ?", String.class, evaluation));
    }

    /** T4.5-D3: voiding is allowed after the session closes; then the event can be cancelled (DP-2.2). */
    @Test
    void voidingWorksOnClosedSessionsAndUnblocksCancellingTheEvent() throws Exception {
        UUID wrongBlock = event(fx.session(), "IN_PROGRESS");
        UUID first = evaluationId(postInEvent(fx.present(), wrongBlock, eventMetrics, "head", fx.academyId())
                .andExpect(status().isCreated()));
        UUID second = evaluationId(postInEvent(fx.late(), wrongBlock, eventMetrics, "head", fx.academyId())
                .andExpect(status().isCreated()));

        cancelEvent(wrongBlock).andExpect(status().isConflict());     // non-voided evaluations block the cancel
        voidEvaluation(fx.present(), first, "head", "Recorded in the wrong block").andExpect(status().isOk());
        cancelEvent(wrongBlock).andExpect(status().isConflict());     // one is still not voided
        voidEvaluation(fx.late(), second, "head", "Recorded in the wrong block").andExpect(status().isOk());
        cancelEvent(wrongBlock).andExpect(status().isOk());

        // D3: an evaluation can still be voided after its event and its session are closed
        UUID finished = event(fx.session(), "IN_PROGRESS");
        UUID third = evaluationId(postInEvent(fx.present(), finished, eventMetrics, "head", fx.academyId())
                .andExpect(status().isCreated()));
        jdbc.update("UPDATE session_evaluation_events SET status = 'COMPLETED', ended_at = started_at + interval "
                + "'1 minute' WHERE session_event_id = ?", finished);
        jdbc.update("UPDATE training_sessions SET status = 'COMPLETED', ended_at = started_at + interval '1 hour' "
                + "WHERE session_id = ?", fx.session());
        voidEvaluation(fx.present(), third, "head", "Found after the session").andExpect(status().isOk());
    }

    /** Rule 9: the second evaluation with the same template warns, but is recorded. */
    @Test
    void recentEvaluationWithTheSameTemplateWarnsWithoutBlocking() throws Exception {
        UUID first = event(fx.session(), "IN_PROGRESS");
        UUID second = event(fx.session(), "IN_PROGRESS");
        postInEvent(fx.present(), first, eventMetrics, "head", fx.academyId()).andExpect(status().isCreated());

        postInEvent(fx.present(), second, eventMetrics, "head", fx.academyId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.warnings[0].code").value("FREQUENCY_TOO_SOON"))
                .andExpect(jsonPath("$.data.warnings[1].code").value("PREVIOUS_STILL_VALID"));
    }

    /**
     * E4: two requests for the same (event, player) at once leave one evaluation. Under the session lock the loser
     * gets the service's 409; without the lock it would reach the unique index and fail with a different exception.
     */
    @Test
    void concurrentDuplicateLeavesOneEvaluationAndAServiceConflict() throws Exception {
        UUID event = event(fx.session(), "IN_PROGRESS");
        CyclicBarrier bothInside = new CyclicBarrier(2);
        doAnswer(invocation -> {
            bothInside.await(10, TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).when(accessPolicy).requireDataEntryAccess(any(), any());

        Callable<Object> call = () -> evaluationService.create(fx.present(), fx.academyId(), new EvaluationCreateRequest(
                templateId, templateVersion, results(eventMetrics), null, null, null, event), authentication("head"));
        List<Object> outcomes = concurrently(call, call);

        assertEquals(1, outcomes.stream().filter(EvaluationResponse.class::isInstance).count(), outcomes.toString());
        assertInstanceOf(ConflictException.class,
                outcomes.stream().filter(o -> !(o instanceof EvaluationResponse)).findFirst().orElseThrow(),
                outcomes.toString());
        assertEquals(1, evaluations(event, fx.present()));
    }

    /** R-11 (T4.4-D4, kept as debt): the standalone POST answers 403, not 404, for an existing unassigned player. */
    @Test
    void standalonePostStillRevealsExistingPlayersWithoutAssignment() throws Exception {
        Map<String, Object> standalone = body(null, eventMetrics);
        standalone.remove("session_event_id");

        post(fx.notConvened(), standalone, "head", null).andExpect(status().isForbidden());
        post(UUID.randomUUID(), standalone, "head", null).andExpect(status().isNotFound());
    }

    // ---- helpers ------------------------------------------------------------------------------------------------

    private UUID evaluationId(ResultActions created) throws Exception {
        return UUID.fromString(objectMapper.readTree(created.andReturn().getResponse().getContentAsString())
                .path("data").path("evaluation_id").asString());
    }

    private ResultActions get(UUID player, UUID evaluation, JwtRequestPostProcessor jwt) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/v1/players/{playerId}/evaluations/{id}", player, evaluation).with(jwt));
    }

    private ResultActions voidEvaluation(UUID player, UUID evaluation, String coach, String reason) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/players/{playerId}/evaluations/{id}/void", player, evaluation)
                .with(coachJwt(coach)).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", reason))));
    }

    private ResultActions cancelEvent(UUID event) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/evaluation-events/{id}/cancel", event)
                .header("X-Academy-Id", fx.academyId()).with(coachJwt("head")));
    }

    private JwtRequestPostProcessor adminJwt(String handle) {
        return jwt().jwt(token -> token.subject(factory.subject(handle)))
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_ACADEMY_ADMIN")));
    }

    private ResultActions postInEvent(UUID player, UUID event, List<UUID> metrics, String coach, UUID academyId)
            throws Exception {
        return post(player, body(event, metrics), coach, academyId);
    }

    private ResultActions post(UUID player, Map<String, Object> body, String coach, UUID academyId)
            throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/players/{playerId}/evaluations", player)
                .with(coachJwt(coach))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        if (academyId != null) {
            request.header("X-Academy-Id", academyId);
        }
        return mockMvc.perform(request);
    }

    private Map<String, Object> body(UUID event, List<UUID> metrics) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("template_id", templateId);
        body.put("template_version", templateVersion);
        body.put("results", metrics.stream().map(id -> Map.of("criterion_id", id, "level", 2)).toList());
        body.put("session_event_id", event);
        return body;
    }

    private static List<EvaluationCreateRequest.ResultRequest> results(List<UUID> metrics) {
        return metrics.stream().map(id -> new EvaluationCreateRequest.ResultRequest(
                id, null, null, null, null, 2, null, null, null)).toList();
    }

    private JwtRequestPostProcessor coachJwt(String handle) {
        return jwt().jwt(token -> token.subject(factory.subject(handle)))
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_COACH")));
    }

    private JwtAuthenticationToken authentication(String handle) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(factory.subject(handle)).build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_COACH")));
    }

    private UUID event(UUID session, String status) {
        int blockOrder = jdbc.queryForObject(
                "SELECT coalesce(max(block_order), 0) + 1 FROM session_evaluation_events WHERE session_id = ?",
                Integer.class, session);
        UUID event = jdbc.queryForObject("""
                INSERT INTO session_evaluation_events (session_id, template_id, block_order, status, started_at)
                VALUES (?, ?, ?, ?, CASE WHEN ? = 'IN_PROGRESS' THEN now() END) RETURNING session_event_id""",
                UUID.class, session, templateId, blockOrder, status, status);
        eventMetrics.forEach(metric -> jdbc.update(
                "INSERT INTO session_event_metrics (session_event_id, template_id, criterion_id) VALUES (?, ?, ?)",
                event, templateId, metric));
        return event;
    }

    private int evaluations(UUID event, UUID player) {
        return jdbc.queryForObject("SELECT count(*) FROM evaluations WHERE session_event_id = ? AND player_id = ?",
                Integer.class, event, player);
    }

    private List<Object> concurrently(Callable<Object> first, Callable<Object> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Object> outcomes = new ArrayList<>();
        try {
            for (Future<Object> future : executor.invokeAll(List.of(first, second), 30, TimeUnit.SECONDS)) {
                try {
                    outcomes.add(future.get());
                } catch (ExecutionException ex) {
                    outcomes.add(ex.getCause());
                }
            }
        } finally {
            executor.shutdownNow();
        }
        return outcomes;
    }

    private Fixture createFixture() {
        factory = new TestDataFactory(entityManager);
        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        return inTransaction(() -> {
            AcademyEntity academy = factory.academy("Eval events");
            AcademyEntity otherAcademy = factory.academy("Eval events other");
            AcademyCategoryEntity category = factory.academyCategory(academy, "EVAL_EVENTS", today);
            AcademyCategoryEntity otherCategory = factory.academyCategory(otherAcademy, "EVAL_EVENTS_OTHER", today);
            CoachEntity head = factory.currentCoach("head", "Head", category, CoachRoleCode.HEAD_COACH, today);
            CoachEntity assistant = factory.currentCoach("assistant", "Assistant", category, CoachRoleCode.ASSISTANT,
                    today);
            factory.currentCoach("outsider", "Outsider", category, CoachRoleCode.ASSISTANT, today);
            factory.currentCoach("fitness", "Fitness", category, CoachRoleCode.FITNESS_COACH, today);
            factory.currentCoach("foreign", "Foreign", otherCategory, CoachRoleCode.HEAD_COACH, today);
            entityManager.persist(AcademyAdminEntity.builder().user(factory.user("admin", "Admin", "Academy"))
                    .academy(academy).status(Status.ACTIVE).build());
            entityManager.persist(AcademyAdminEntity.builder().user(factory.user("other-admin", "Admin", "Other"))
                    .academy(otherAcademy).status(Status.ACTIVE).build());

            TrainingSessionEntity session = factory.session(category.getAcademyCategoryId(), head.getCoachId(), null,
                    "Eval events session", now, TrainingSessionStatus.IN_PROGRESS, now, null);
            TrainingSessionEntity planned = factory.session(category.getAcademyCategoryId(), head.getCoachId(), null,
                    "Eval events planned", now.plusDays(1), TrainingSessionStatus.PLANNED, null, null);
            for (TrainingSessionEntity s : List.of(session, planned)) {
                staff(s, head, SessionParticipationType.LEAD);
                staff(s, assistant, SessionParticipationType.ASSISTANT);
            }
            PlayerEntity present = convened(session, "present", AttendanceStatus.PRESENT, category, today);
            PlayerEntity late = convened(session, "late", AttendanceStatus.LATE, category, today);
            PlayerEntity absent = convened(session, "absent", AttendanceStatus.ABSENT, category, today);
            PlayerEntity notConvened = factory.player("not-convened", "Nadie");   // exists, no assignment
            entityManager.flush();
            return new Fixture(academy.getAcademyId(), otherAcademy.getAcademyId(),
                    category.getAcademyCategoryId(), head.getCoachId(), session.getSessionId(),
                    planned.getSessionId(), present.getPlayerId(), late.getPlayerId(), absent.getPlayerId(),
                    notConvened.getPlayerId());
        });
    }

    private void staff(TrainingSessionEntity session, CoachEntity coach, SessionParticipationType type) {
        entityManager.persist(SessionStaffEntity.builder().trainingSession(session).coach(coach)
                .participationType(type).build());
    }

    private PlayerEntity convened(TrainingSessionEntity session, String handle, AttendanceStatus attendance,
            AcademyCategoryEntity category, LocalDate today) {
        PlayerEntity player = factory.currentPlayer(handle, handle, category, today);
        entityManager.persist(SessionPlayerEntity.builder().trainingSession(session).player(player)
                .attendanceStatus(attendance).build());
        return player;
    }

    private <T> T inTransaction(Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }

    private record Fixture(UUID academyId, UUID otherAcademyId, UUID categoryId, UUID head, UUID session,
            UUID plannedSession, UUID present, UUID late, UUID absent, UUID notConvened) {
    }
}

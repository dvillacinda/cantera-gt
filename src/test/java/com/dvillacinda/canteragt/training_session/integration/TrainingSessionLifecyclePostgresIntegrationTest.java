package com.dvillacinda.canteragt.training_session.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import com.dvillacinda.canteragt.academy.entity.AcademyEntity;
import com.dvillacinda.canteragt.academy_category.entity.AcademyCategoryEntity;
import com.dvillacinda.canteragt.coach.entity.CoachEntity;
import com.dvillacinda.canteragt.coach_role.enums.CoachRoleCode;
import com.dvillacinda.canteragt.player.entity.PlayerEntity;
import com.dvillacinda.canteragt.player_assignment.enums.PlayerAssignmentStatus;
import com.dvillacinda.canteragt.support.TestDataFactory;
import com.dvillacinda.canteragt.training_session.enums.TrainingSessionStatus;
import com.dvillacinda.canteragt.auth.service.KeycloakUserService;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end scenario of the Sprint 3 sessions module over HTTP against PostgreSQL, with the real security chain
 * and access policy (JWT subject → users.keycloak_id; only Keycloak's admin API is mocked).
 * Requires the isolated {@code postgres-test} database (see docs/testing.md).
 */
@SpringBootTest
@AutoConfigureMockMvc
class TrainingSessionLifecyclePostgresIntegrationTest {
    private static final ZoneId ZONE = ZoneId.of("America/Guatemala");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private Clock clock;
    @MockitoBean private KeycloakUserService keycloakUserService;

    private TestDataFactory factory;
    private Fixture fx;

    @BeforeEach
    void setUp() {
        factory = new TestDataFactory(entityManager);
        LocalDate today = LocalDate.now(clock);
        fx = inTransaction(() -> {
            AcademyEntity academy = factory.academy("E2E");
            AcademyCategoryEntity category = factory.academyCategory(academy, "E2E", today);
            AcademyCategoryEntity otherCategory = factory.academyCategory(academy, "E2E_OTHER", today);
            CoachEntity head = factory.currentCoach("head", "Head", category, CoachRoleCode.HEAD_COACH, today);
            CoachEntity assistant =
                    factory.currentCoach("assistant", "Assistant", category, CoachRoleCode.ASSISTANT, today);
            CoachEntity otherHead =
                    factory.currentCoach("other-head", "OtherHead", otherCategory, CoachRoleCode.HEAD_COACH, today);
            PlayerEntity p1 = factory.currentPlayer("p1", "Uno", category, today);
            PlayerEntity p2 = factory.currentPlayer("p2", "Dos", category, today);
            PlayerEntity p3 = factory.currentPlayer("p3", "Tres", category, today);
            PlayerEntity expired = factory.player("expired", "Vencido");
            factory.playerAssignment(expired, category, PlayerAssignmentStatus.ACTIVE,
                    today.minusDays(60), today.minusDays(1));
            PlayerEntity foreign = factory.currentPlayer("foreign", "Ajeno", otherCategory, today);

            AcademyEntity academyB = factory.academy("E2E-B");
            AcademyCategoryEntity categoryB = factory.academyCategory(academyB, "E2E_B", today);
            CoachEntity headB = factory.currentCoach("head-b", "HeadB", categoryB, CoachRoleCode.HEAD_COACH, today);
            entityManager.flush();
            return new Fixture(academy.getAcademyId(), category.getAcademyCategoryId(),
                    otherCategory.getAcademyCategoryId(), head.getCoachId(), assistant.getCoachId(),
                    otherHead.getCoachId(), p1.getPlayerId(), p2.getPlayerId(), p3.getPlayerId(),
                    expired.getPlayerId(), foreign.getPlayerId(),
                    academyB.getAcademyId(), categoryB.getAcademyCategoryId(), headB.getCoachId());
        });
    }

    /** Guide T3.7 points 1–3: full HEAD flow plus role and scope denials, all through HTTP. */
    @Test
    void headCoachRunsTheWholeSessionLifecycleAndOthersAreDenied() throws Exception {
        // 1. HEAD creates the session; the creator becomes LEAD
        JsonNode created = data(perform(post("/api/v1/academy-categories/{id}/training-sessions", fx.category()),
                head(), json("""
                        {"name":"Entrenamiento técnico","session_date":"%s","notes":"Pase y recepción"}
                        """.formatted(tomorrowAt16())), 201));
        UUID sessionId = UUID.fromString(created.path("session_id").asString());
        assertEquals("PLANNED", created.path("status").asString());
        assertEquals(fx.head().toString(), created.path("created_by_coach_id").asString());
        assertTrue(created.path("session_date").asString().endsWith("-06:00"));
        JsonNode staff = data(perform(get("/api/v1/training-sessions/{id}/staff", sessionId), head(), null, 200));
        assertEquals("LEAD", staff.get(0).path("participation_type").asString());

        // 2. roster: three current players; expired and other-category players are rejected as a whole batch
        JsonNode added = data(perform(post("/api/v1/training-sessions/{id}/players", sessionId), head(),
                json(playerIds(fx.p1(), fx.p2(), fx.p3())), 201));
        assertEquals(3, added.size());
        JsonNode rejectedExpired = body(perform(post("/api/v1/training-sessions/{id}/players", sessionId), head(),
                json(playerIds(fx.expired())), 400));
        assertEquals("NO_CURRENT_ASSIGNMENT_IN_CATEGORY",
                rejectedExpired.path("fieldErrors").path(fx.expired().toString()).asString());
        JsonNode rejectedForeign = body(perform(post("/api/v1/training-sessions/{id}/players", sessionId), head(),
                json(playerIds(fx.foreign())), 400));
        assertEquals("NO_CURRENT_ASSIGNMENT_IN_CATEGORY",
                rejectedForeign.path("fieldErrors").path(fx.foreign().toString()).asString());

        // staff: assistant joins
        perform(post("/api/v1/training-sessions/{id}/staff", sessionId), head(), json("""
                {"coach_id":"%s","participation_type":"ASSISTANT"}""".formatted(fx.assistant())), 201);

        // 3. denials: ASSISTANT (not HEAD) → 403 on management, 200 on read; other-category coach → 404; PLAYER → 403
        perform(post("/api/v1/academy-categories/{id}/training-sessions", fx.category()), assistant(), json("""
                {"name":"No permitido","session_date":"%s"}""".formatted(tomorrowAt16())), 403);
        perform(post("/api/v1/training-sessions/{id}/start", sessionId), assistant(), null, 403);
        perform(get("/api/v1/training-sessions/{id}", sessionId), assistant(), null, 200);
        perform(get("/api/v1/training-sessions/{id}", sessionId), otherHead(), null, 404);
        perform(post("/api/v1/training-sessions/{id}/start", sessionId), otherHead(), null, 404);
        perform(get("/api/v1/training-sessions/{id}/players", sessionId), player(), null, 403);

        // start → attendance → complete with PENDING (409 listing it) → complete
        JsonNode started = data(perform(post("/api/v1/training-sessions/{id}/start", sessionId), head(), null, 200));
        assertEquals("IN_PROGRESS", started.path("status").asString());
        attendance(sessionId, fx.p1(), "PRESENT");
        attendance(sessionId, fx.p2(), "LATE");
        JsonNode pending = body(perform(post("/api/v1/training-sessions/{id}/complete", sessionId), head(), null, 409));
        assertTrue(pending.path("message").asString().contains(fx.p3().toString()));
        assertFalse(pending.path("message").asString().contains(fx.p1().toString()));
        attendance(sessionId, fx.p3(), "JUSTIFIED");
        JsonNode completed = data(perform(post("/api/v1/training-sessions/{id}/complete", sessionId), head(), null, 200));
        assertEquals("COMPLETED", completed.path("status").asString());
        assertFalse(completed.path("ended_at").isNull());

        // closed: edits are rejected and attendance is frozen
        perform(patch("/api/v1/training-sessions/{id}", sessionId), head(), json("{\"name\":\"Tarde\"}"), 409);
        perform(patch("/api/v1/training-sessions/{id}/players/{p}/attendance", sessionId, fx.p1()), head(),
                json("{\"attendance_status\":\"ABSENT\"}"), 409);
        JsonNode roster = data(perform(get("/api/v1/training-sessions/{id}/players", sessionId), head(), null, 200));
        assertEquals(Set.of("PRESENT", "LATE", "JUSTIFIED"), statuses(roster));
        assertFalse(roster.get(0).has("email"));
    }

    /** Guide T3.7 point 4: concurrent start over HTTP → one 200 and one 409. */
    @Test
    void concurrentStartOverHttpYieldsOne200AndOne409() throws Exception {
        UUID sessionId = createSession("Concurrent start", tomorrowAt16());

        Callable<Integer> start = () -> mockMvc.perform(withAcademy(
                        post("/api/v1/training-sessions/{id}/start", sessionId), fx.academy()).with(head()))
                .andReturn().getResponse().getStatus();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Integer> statuses = new ArrayList<>();
        try {
            for (Future<Integer> future : executor.invokeAll(List.of(start, start), 30, TimeUnit.SECONDS)) {
                statuses.add(future.get());
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(List.of(200, 409), statuses.stream().sorted().toList());
        JsonNode session = data(perform(get("/api/v1/training-sessions/{id}", sessionId), head(), null, 200));
        assertEquals("IN_PROGRESS", session.path("status").asString());
        assertEquals(1, session.path("version").asInt());
    }

    /** Guide T3.7 point 6: paginated listing honours filters and never crosses academies. */
    @Test
    void listingIsPaginatedFilteredAndIsolatedBetweenAcademies() throws Exception {
        LocalDateTime base = LocalDateTime.now(clock).plusDays(2).withHour(16).withMinute(0).withSecond(0).withNano(0);
        inTransaction(() -> {
            factory.session(fx.category(), fx.head(), null, "A1", base, TrainingSessionStatus.PLANNED, null, null);
            factory.session(fx.category(), fx.head(), null, "A2", base.plusDays(1), TrainingSessionStatus.PLANNED,
                    null, null);
            factory.session(fx.category(), fx.head(), null, "A3", base.plusDays(2), TrainingSessionStatus.IN_PROGRESS,
                    base.plusDays(2), null);
            factory.session(fx.categoryB(), fx.headB(), null, "B1", base, TrainingSessionStatus.PLANNED, null, null);
            return null;
        });
        String listA = "/api/v1/academy-categories/{id}/training-sessions";

        JsonNode page = data(perform(get(listA, fx.category()).param("size", "2"), head(), null, 200));
        assertEquals(3, page.path("totalElements").asInt(page.path("page").path("totalElements").asInt()));
        assertEquals(2, page.path("content").size());
        assertEquals("A3", page.path("content").get(0).path("name").asString(), "default order: session_date desc");

        JsonNode planned = data(perform(get(listA, fx.category()).param("status", "PLANNED"), head(), null, 200));
        assertEquals(Set.of("A1", "A2"), names(planned.path("content")));

        JsonNode window = data(perform(get(listA, fx.category())
                .param("from", iso(base.plusDays(1))).param("to", iso(base.plusDays(1))), head(), null, 200));
        assertEquals(Set.of("A2"), names(window.path("content")));

        perform(get(listA, fx.category()).param("size", "51"), head(), null, 400);

        // academy B only sees its own session; neither side can reach the other with any header combination
        JsonNode listB = data(performIn(get(listA, fx.categoryB()), fx.academyB(), headB(), 200));
        assertEquals(Set.of("B1"), names(listB.path("content")));
        performIn(get(listA, fx.category()), fx.academyB(), headB(), 404);
        performIn(get(listA, fx.category()), fx.academy(), headB(), 404);
        performIn(get(listA, fx.categoryB()), fx.academyB(), head(), 404);
    }

    /** Closure criterion "OpenAPI reviewed": every session route is published with a summary. */
    @Test
    void openApiPublishesEverySessionRouteWithASummary() throws Exception {
        JsonNode paths = objectMapper.readTree(mockMvc.perform(get("/api-docs"))  // springdoc.api-docs.path in application-dev.yaml
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("paths");
        String session = "/api/v1/training-sessions/{sessionId}";
        List<String[]> routes = List.of(
                new String[] { "/api/v1/academy-categories/{academyCategoryId}/training-sessions", "post" },
                new String[] { "/api/v1/academy-categories/{academyCategoryId}/training-sessions", "get" },
                new String[] { session, "get" },
                new String[] { session, "patch" },
                new String[] { session + "/start", "post" },
                new String[] { session + "/complete", "post" },
                new String[] { session + "/cancel", "post" },
                new String[] { session + "/players", "get" },
                new String[] { session + "/players", "post" },
                new String[] { session + "/players/{playerId}", "delete" },
                new String[] { session + "/players/{playerId}/attendance", "patch" },
                new String[] { session + "/staff", "get" },
                new String[] { session + "/staff", "post" },
                new String[] { session + "/staff/{coachId}", "delete" });

        for (String[] route : routes) {
            JsonNode operation = paths.path(route[0]).path(route[1]);
            assertFalse(operation.isMissingNode(), route[1] + " " + route[0] + " missing from OpenAPI");
            assertFalse(operation.path("summary").asString().isBlank(), route[1] + " " + route[0] + " has no summary");
        }
    }

    // ---- helpers --------------------------------------------------------------------------------------------

    private UUID createSession(String name, String date) throws Exception {
        JsonNode created = data(perform(post("/api/v1/academy-categories/{id}/training-sessions", fx.category()),
                head(), json("{\"name\":\"%s\",\"session_date\":\"%s\"}".formatted(name, date)), 201));
        return UUID.fromString(created.path("session_id").asString());
    }

    private void attendance(UUID sessionId, UUID playerId, String value) throws Exception {
        JsonNode updated = data(perform(patch("/api/v1/training-sessions/{id}/players/{p}/attendance",
                sessionId, playerId), head(), json("{\"attendance_status\":\"%s\"}".formatted(value)), 200));
        assertEquals(value, updated.path("attendance_status").asString());
    }

    private String perform(MockHttpServletRequestBuilder request, JwtRequestPostProcessor auth, String body,
            int expectedStatus) throws Exception {
        return performIn(request, fx.academy(), auth, body, expectedStatus);
    }

    private String performIn(MockHttpServletRequestBuilder request, UUID academyId, JwtRequestPostProcessor auth,
            int expectedStatus) throws Exception {
        return performIn(request, academyId, auth, null, expectedStatus);
    }

    private String performIn(MockHttpServletRequestBuilder request, UUID academyId, JwtRequestPostProcessor auth,
            String body, int expectedStatus) throws Exception {
        MockHttpServletRequestBuilder builder = withAcademy(request, academyId).with(auth);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(builder).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
    }

    private static MockHttpServletRequestBuilder withAcademy(MockHttpServletRequestBuilder request, UUID academyId) {
        return request.header("X-Academy-Id", academyId);
    }

    private JsonNode body(String json) {
        return objectMapper.readTree(json);
    }

    private JsonNode data(String json) {
        return body(json).path("data");
    }

    private static String json(String value) {
        return value.strip();
    }

    private static String playerIds(UUID... ids) {
        List<String> quoted = new ArrayList<>();
        for (UUID id : ids) {
            quoted.add("\"" + id + "\"");
        }
        return "{\"player_ids\":[" + String.join(",", quoted) + "]}";
    }

    private static Set<String> statuses(JsonNode roster) {
        Set<String> values = new java.util.HashSet<>();
        roster.forEach(entry -> values.add(entry.path("attendance_status").asString()));
        return values;
    }

    private static Set<String> names(JsonNode sessions) {
        Set<String> values = new java.util.HashSet<>();
        sessions.forEach(entry -> values.add(entry.path("name").asString()));
        return values;
    }

    private String tomorrowAt16() {
        return iso(LocalDateTime.now(clock).plusDays(1).withHour(16).withMinute(0).withSecond(0).withNano(0));
    }

    private static String iso(LocalDateTime local) {
        return OffsetDateTime.of(local, ZONE.getRules().getOffset(local)).toString();
    }

    private JwtRequestPostProcessor head() {
        return coachJwt("head");
    }

    private JwtRequestPostProcessor assistant() {
        return coachJwt("assistant");
    }

    private JwtRequestPostProcessor otherHead() {
        return coachJwt("other-head");
    }

    private JwtRequestPostProcessor headB() {
        return coachJwt("head-b");
    }

    private JwtRequestPostProcessor player() {
        return jwt().jwt(token -> token.subject(factory.subject("p1")))
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_PLAYER")));
    }

    private JwtRequestPostProcessor coachJwt(String handle) {
        return jwt().jwt(token -> token.subject(factory.subject(handle)))
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_COACH")));
    }

    private <T> T inTransaction(Supplier<T> supplier) {
        return transactionTemplate.execute(ignored -> supplier.get());
    }

    private record Fixture(UUID academy, UUID category, UUID otherCategory, UUID head, UUID assistant,
            UUID otherHead, UUID p1, UUID p2, UUID p3, UUID expired, UUID foreign,
            UUID academyB, UUID categoryB, UUID headB) { }
}

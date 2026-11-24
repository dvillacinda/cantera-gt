package com.dvillacinda.canteragt.training_session.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.dvillacinda.canteragt.academy.entity.AcademyEntity;
import com.dvillacinda.canteragt.academy_admin.entity.AcademyAdminEntity;
import com.dvillacinda.canteragt.academy_category.entity.AcademyCategoryEntity;
import com.dvillacinda.canteragt.coach.entity.CoachEntity;
import com.dvillacinda.canteragt.coach_role.enums.CoachRoleCode;
import com.dvillacinda.canteragt.player.entity.PlayerEntity;
import com.dvillacinda.canteragt.shared.enums.Status;
import com.dvillacinda.canteragt.support.TestDataFactory;
import com.dvillacinda.canteragt.training_session.dto.SessionPlayerResponse;
import com.dvillacinda.canteragt.training_session.entity.SessionPlayerEntity;
import com.dvillacinda.canteragt.training_session.entity.TrainingSessionEntity;
import com.dvillacinda.canteragt.training_session.enums.AttendanceStatus;
import com.dvillacinda.canteragt.training_session.enums.TrainingSessionStatus;
import com.dvillacinda.canteragt.training_session.service.SessionEvaluationEventService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Players still to observe in an event (T4.6) against PostgreSQL, over HTTP with the real security chain and the real
 * {@code TrainingSessionAccessPolicy}. Scenario of the sprint guide: 20 convened players, 3 not present, 2 with an
 * evaluation in the event and 1 with only a voided one; plus one player evaluated in another event of the session.
 * Also checks scope (404), the order and constant queries (no N+1).
 * Requires the isolated {@code postgres-test} database (see docs/testing.md).
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
class SessionEventPendingPlayersPostgresIntegrationTest {
    private static final String TEMPLATE = "JUEGO_REDUCIDO";
    /** Deliberately unsorted; two pending players share a last name so the first name decides. */
    private static final List<String[]> NAMES = List.of(
            new String[] { "Zoe", "Ramirez" }, new String[] { "Fer", "Castillo" }, new String[] { "Luis", "Garcia" },
            new String[] { "Gabi", "Ortiz" }, new String[] { "Carlos", "Mendez" }, new String[] { "Diego", "Alvarez" },
            new String[] { "Eva", "Torres" }, new String[] { "Bea", "Lopez" }, new String[] { "Ana", "Lopez" },
            new String[] { "Hugo", "Perez" }, new String[] { "Ines", "Santos" }, new String[] { "Juan", "Vargas" },
            new String[] { "Kevin", "Morales" }, new String[] { "Lia", "Herrera" }, new String[] { "Mario", "Diaz" },
            new String[] { "Nora", "Cruz" }, new String[] { "Oscar", "Flores" }, new String[] { "Pia", "Reyes" },
            new String[] { "Raul", "Juarez" }, new String[] { "Sara", "Bonilla" });

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SessionEvaluationEventService eventService;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Clock clock;

    private TestDataFactory factory;
    private UUID templateId;
    private Fixture fx;

    @BeforeEach
    void setUp() {
        templateId = jdbc.queryForObject(
                "SELECT template_id FROM evaluation_templates WHERE code = ? ORDER BY version DESC LIMIT 1",
                UUID.class, TEMPLATE);
        fx = createFixture();
    }

    @Test
    void twentyPlayerScenarioListsTheFifteenStillToObserveInOrder() throws Exception {
        // players 0-2 are not present (ABSENT, JUSTIFIED, PENDING); the rest are PRESENT or LATE
        UUID event = event(fx.session(), 1);
        UUID otherEvent = event(fx.session(), 2);
        evaluation(fx.session(), event, fx.players().get(3), "COMPLETED");
        evaluation(fx.session(), event, fx.players().get(4), "COMPLETED");
        evaluation(fx.session(), event, fx.players().get(5), "VOIDED");        // only voided: pending again
        evaluation(fx.session(), otherEvent, fx.players().get(6), "COMPLETED"); // another event: still pending here
        evaluation(fx.session(), event, fx.players().get(1), "COMPLETED");      // not present: never listed

        JsonNode data = data(pending(event, fx.academyId(), coachJwt("head")).andExpect(status().isOk()));

        List<UUID> expected = new ArrayList<>();
        for (int i = 3; i < NAMES.size(); i++) {
            if (i != 3 && i != 4) {
                expected.add(fx.players().get(i));
            }
        }
        expected.sort(Comparator.comparing((UUID id) -> lastName(id)).thenComparing(this::firstName));
        assertEquals(15, data.size());
        List<UUID> actual = new ArrayList<>();
        data.forEach(node -> actual.add(UUID.fromString(node.path("player_id").asString())));
        assertEquals(expected, actual);
        assertTrue(actual.contains(fx.players().get(5)), "a player with only voided evaluations is pending");
        assertTrue(actual.contains(fx.players().get(6)), "an evaluation in another event does not count");
        // Bea and Ana Lopez were convened in that order: the first name breaks the tie
        assertEquals(indexOf(data, "Ana") + 1, indexOf(data, "Bea"));
        // same fields as the roster, nothing sensitive
        JsonNode first = data.get(0);
        assertEquals(List.of("session_player_id", "player_id", "first_name", "last_name", "principal_position",
                "attendance_status", "updated_at"), fieldNames(first));
        assertEquals("GK", first.path("principal_position").asString());
    }

    @Test
    void everyPlayerIsPendingInAFreshEventAndNobodyOnceAllAreObserved() throws Exception {
        UUID event = event(fx.session(), 1);
        assertEquals(17, data(pending(event, fx.academyId(), coachJwt("head"))).size());

        for (int i = 3; i < NAMES.size(); i++) {
            evaluation(fx.session(), event, fx.players().get(i), "COMPLETED");
        }
        assertEquals(0, data(pending(event, fx.academyId(), coachJwt("head")).andExpect(status().isOk())).size());
    }

    @Test
    void readAccessDecidesWhoSeesTheListAndOutOfScopeIs404() throws Exception {
        UUID event = event(fx.session(), 1);

        pending(event, fx.academyId(), coachJwt("head")).andExpect(status().isOk());
        pending(event, fx.academyId(), coachJwt("fitness")).andExpect(status().isOk());   // read access, no data entry
        pending(event, fx.academyId(), adminJwt("admin")).andExpect(status().isOk());
        pending(event, fx.otherAcademyId(), coachJwt("foreign")).andExpect(status().isNotFound());
        pending(event, fx.academyId(), coachJwt("foreign")).andExpect(status().isNotFound());
        pending(event, fx.otherAcademyId(), adminJwt("other-admin")).andExpect(status().isNotFound());
        pending(UUID.randomUUID(), fx.academyId(), coachJwt("head")).andExpect(status().isNotFound());
    }

    @Test
    void closedEventsStillAnswer() throws Exception {
        UUID event = event(fx.session(), 1);
        evaluation(fx.session(), event, fx.players().get(3), "COMPLETED");
        jdbc.update("UPDATE session_evaluation_events SET status = 'COMPLETED', ended_at = started_at + interval "
                + "'1 minute' WHERE session_event_id = ?", event);

        assertEquals(16, data(pending(event, fx.academyId(), coachJwt("head")).andExpect(status().isOk())).size());
    }

    @Test
    void queriesStayConstantRegardlessOfTheNumberOfPlayers() {
        UUID big = event(fx.session(), 1);
        UUID small = event(fx.smallSession(), 1);
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        statistics.clear();
        List<SessionPlayerResponse> many = eventService.pendingPlayers(big, fx.academyId(), authentication("head"));
        long manyStatements = statistics.getPrepareStatementCount();
        long manyFetches = statistics.getEntityFetchCount();

        statistics.clear();
        List<SessionPlayerResponse> few = eventService.pendingPlayers(small, fx.academyId(), authentication("head"));
        long fewStatements = statistics.getPrepareStatementCount();

        assertEquals(17, many.size());
        assertEquals(2, few.size());
        assertEquals(fewStatements, manyStatements, "statements must not grow with the number of players");
        assertEquals(0, manyFetches, "lazy associations were fetched one by one (N+1)");
    }

    // ---- helpers ------------------------------------------------------------------------------------------------

    private ResultActions pending(UUID event, UUID academyId, JwtRequestPostProcessor jwt) throws Exception {
        return mockMvc.perform(get("/api/v1/evaluation-events/{id}/pending-players", event)
                .header("X-Academy-Id", academyId).with(jwt));
    }

    private JsonNode data(ResultActions result) throws Exception {
        result.andExpect(jsonPath("$.success").value(true));
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).path("data");
    }

    private static int indexOf(JsonNode data, String firstName) {
        for (int i = 0; i < data.size(); i++) {
            if (firstName.equals(data.get(i).path("first_name").asString())) {
                return i;
            }
        }
        return -1;
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.propertyNames().forEach(names::add);
        return names;
    }

    private String lastName(UUID player) {
        return fx.names().get(player)[1];
    }

    private String firstName(UUID player) {
        return fx.names().get(player)[0];
    }

    private UUID event(UUID session, int blockOrder) {
        return jdbc.queryForObject("""
                INSERT INTO session_evaluation_events (session_id, template_id, block_order, status, started_at)
                VALUES (?, ?, ?, 'IN_PROGRESS', now()) RETURNING session_event_id""",
                UUID.class, session, templateId, blockOrder);
    }

    private void evaluation(UUID session, UUID event, UUID player, String status) {
        boolean voided = "VOIDED".equals(status);
        jdbc.update("""
                INSERT INTO evaluations (academy_category_id, session_id, session_event_id, player_id, template_id,
                                         evaluator_coach_id, evaluation_date, status, created_at, updated_at,
                                         voided_at, voided_by_coach_id, void_reason)
                VALUES (?, ?, ?, ?, ?, ?, now(), ?, now(), now(), ?, ?, ?)""",
                fx.categoryId(), session, event, player, templateId, fx.head(), status,
                voided ? java.sql.Timestamp.valueOf(LocalDateTime.now(clock)) : null,
                voided ? fx.head() : null, voided ? "Wrong player" : null);
    }

    private JwtRequestPostProcessor coachJwt(String handle) {
        return jwt().jwt(token -> token.subject(factory.subject(handle)))
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_COACH")));
    }

    private JwtRequestPostProcessor adminJwt(String handle) {
        return jwt().jwt(token -> token.subject(factory.subject(handle)))
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_ACADEMY_ADMIN")));
    }

    private JwtAuthenticationToken authentication(String handle) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(factory.subject(handle)).build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_COACH")));
    }

    private Fixture createFixture() {
        factory = new TestDataFactory(entityManager);
        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        return inTransaction(() -> {
            AcademyEntity academy = factory.academy("Pending");
            AcademyEntity otherAcademy = factory.academy("Pending other");
            AcademyCategoryEntity category = factory.academyCategory(academy, "PENDING_IT", today);
            AcademyCategoryEntity otherCategory = factory.academyCategory(otherAcademy, "PENDING_IT_OTHER", today);
            CoachEntity head = factory.currentCoach("head", "Head", category, CoachRoleCode.HEAD_COACH, today);
            factory.currentCoach("fitness", "Fitness", category, CoachRoleCode.FITNESS_COACH, today);
            factory.currentCoach("foreign", "Foreign", otherCategory, CoachRoleCode.HEAD_COACH, today);
            entityManager.persist(AcademyAdminEntity.builder().user(factory.user("admin", "Admin", "Academy"))
                    .academy(academy).status(Status.ACTIVE).build());
            entityManager.persist(AcademyAdminEntity.builder().user(factory.user("other-admin", "Admin", "Other"))
                    .academy(otherAcademy).status(Status.ACTIVE).build());

            TrainingSessionEntity session = factory.session(category.getAcademyCategoryId(), head.getCoachId(), null,
                    "Pending session", now, TrainingSessionStatus.IN_PROGRESS, now, null);
            List<AttendanceStatus> notPresent = List.of(AttendanceStatus.ABSENT, AttendanceStatus.JUSTIFIED,
                    AttendanceStatus.PENDING);
            List<UUID> players = new ArrayList<>();
            Map<UUID, String[]> names = new LinkedHashMap<>();
            for (int i = 0; i < NAMES.size(); i++) {
                AttendanceStatus attendance = i < 3 ? notPresent.get(i)
                        : i % 4 == 0 ? AttendanceStatus.LATE : AttendanceStatus.PRESENT;
                PlayerEntity player = convened(session, "p" + i, NAMES.get(i), attendance, category, today);
                players.add(player.getPlayerId());
                names.put(player.getPlayerId(), NAMES.get(i));
            }

            TrainingSessionEntity smallSession = factory.session(category.getAcademyCategoryId(), head.getCoachId(),
                    null, "Pending small session", now, TrainingSessionStatus.IN_PROGRESS, now, null);
            convened(smallSession, "s1", new String[] { "Uno", "Small" }, AttendanceStatus.PRESENT, category, today);
            convened(smallSession, "s2", new String[] { "Dos", "Small" }, AttendanceStatus.LATE, category, today);
            entityManager.flush();
            return new Fixture(academy.getAcademyId(), otherAcademy.getAcademyId(),
                    category.getAcademyCategoryId(), head.getCoachId(), session.getSessionId(),
                    smallSession.getSessionId(), players, names);
        });
    }

    private PlayerEntity convened(TrainingSessionEntity session, String handle, String[] name,
            AttendanceStatus attendance, AcademyCategoryEntity category, LocalDate today) {
        PlayerEntity player = factory.currentPlayer(handle, name[0], category, today);
        player.getUser().setLastName(name[1]);
        entityManager.persist(SessionPlayerEntity.builder().trainingSession(session).player(player)
                .attendanceStatus(attendance).build());
        return player;
    }

    private <T> T inTransaction(Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }

    private record Fixture(UUID academyId, UUID otherAcademyId, UUID categoryId, UUID head, UUID session,
            UUID smallSession, List<UUID> players, Map<UUID, String[]> names) {
    }
}

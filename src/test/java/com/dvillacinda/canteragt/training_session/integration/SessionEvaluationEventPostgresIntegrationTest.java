package com.dvillacinda.canteragt.training_session.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.dvillacinda.canteragt.academy.entity.AcademyEntity;
import com.dvillacinda.canteragt.academy_category.entity.AcademyCategoryEntity;
import com.dvillacinda.canteragt.coach.entity.CoachEntity;
import com.dvillacinda.canteragt.shared.exception.ConflictException;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;
import com.dvillacinda.canteragt.support.TestDataFactory;
import com.dvillacinda.canteragt.training_session.dto.SessionEvaluationEventCreateRequest;
import com.dvillacinda.canteragt.training_session.dto.SessionEvaluationEventResponse;
import com.dvillacinda.canteragt.training_session.dto.SessionEventMetricsUpdateRequest;
import com.dvillacinda.canteragt.training_session.enums.SessionEvaluationEventStatus;
import com.dvillacinda.canteragt.training_session.enums.TrainingSessionStatus;
import com.dvillacinda.canteragt.training_session.service.SessionEvaluationEventService;
import com.dvillacinda.canteragt.training_session.service.TrainingSessionAccessPolicy;
import com.dvillacinda.canteragt.training_session.service.TrainingSessionService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

/**
 * Evaluation events against PostgreSQL (T4.3): mapping (Hibernate validate), server-assigned block order, default
 * metrics, metric replacement under V3's deferred "at most 3" trigger, event lifecycle and its coupling with the
 * session (E9 and the PLANNED-event cascade of T4.3-D5), concurrency under {@code TrainingSessionLock}, academy
 * isolation and constant queries when listing. The access policy is mocked (covered by its own tests).
 * Requires the isolated {@code postgres-test} database (see docs/testing.md).
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class SessionEvaluationEventPostgresIntegrationTest {
    private static final String SMALL_SIDED_GAME = "JUEGO_REDUCIDO";   // 6 scored metrics, 3 active by default
    private static final String FINISHING = "FINALIZACION";

    @Autowired private SessionEvaluationEventService eventService;
    @Autowired private TrainingSessionService sessionService;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Clock clock;

    @MockitoBean private TrainingSessionAccessPolicy accessPolicy;

    private Fixture fx;
    private UUID game;
    private List<UUID> gameScored;
    private List<UUID> gameDefaults;

    @BeforeEach
    void setUp() {
        fx = createFixture();
        game = templateId(SMALL_SIDED_GAME);
        gameScored = jdbc.queryForList("""
                SELECT criterion_id FROM evaluation_criteria
                 WHERE template_id = ? AND is_scored ORDER BY display_order""", UUID.class, game);
        gameDefaults = jdbc.queryForList("""
                SELECT criterion_id FROM evaluation_criteria
                 WHERE template_id = ? AND is_scored AND is_active_by_default ORDER BY display_order""",
                UUID.class, game);
    }

    @Test
    void createAssignsConsecutiveBlockOrdersAndLoadsTheDefaultMetrics() {
        UUID session = newSession(TrainingSessionStatus.PLANNED);

        SessionEvaluationEventResponse firstEvent = create(session, game, null);
        SessionEvaluationEventResponse secondEvent = create(session, templateId(FINISHING), null);

        assertEquals(1, firstEvent.blockOrder());
        assertEquals(2, secondEvent.blockOrder());
        assertEquals(SessionEvaluationEventStatus.PLANNED, firstEvent.status());
        assertEquals(gameDefaults, firstEvent.metrics().stream().map(m -> m.criterionId()).toList());
        assertEquals(Set.copyOf(gameDefaults), metricsInDb(firstEvent.sessionEventId()));
        assertTrue(firstEvent.createdAt() != null && firstEvent.updatedAt() != null);
    }

    /** E2 precondition: an event can activate metrics that are not the template's defaults. */
    @Test
    void replaceMetricsKeepsSharedMetricsAndSwapsTheRestUnderTheThreeMetricLimit() {
        UUID session = newSession(TrainingSessionStatus.PLANNED);
        UUID event = create(session, game, null).sessionEventId();
        List<UUID> nonDefaults = gameScored.stream().filter(id -> !gameDefaults.contains(id)).toList();
        // keeps one default and adds two non-defaults: the event never holds more than 3 rows at once
        List<UUID> wanted = List.of(gameDefaults.getFirst(), nonDefaults.get(0), nonDefaults.get(1));

        SessionEvaluationEventResponse updated = eventService.replaceMetrics(event, fx.academyId(),
                new SessionEventMetricsUpdateRequest(wanted), null);

        assertEquals(Set.copyOf(wanted), metricsInDb(event));
        assertEquals(Set.copyOf(wanted), updated.metrics().stream().map(m -> m.criterionId()).collect(Collectors.toSet()));
    }

    @Test
    void eventLifecycleFollowsItsSessionAndClosedEventsAreImmutable() {
        UUID session = newSession(TrainingSessionStatus.PLANNED);
        UUID event = create(session, game, null).sessionEventId();

        assertThrows(ConflictException.class, () -> eventService.start(event, fx.academyId(), null),
                "an event cannot start before its session");
        sessionService.start(session, fx.academyId(), null);

        SessionEvaluationEventResponse started = eventService.start(event, fx.academyId(), null);
        assertEquals(SessionEvaluationEventStatus.IN_PROGRESS, started.status());
        assertThrows(ConflictException.class, () -> eventService.replaceMetrics(event, fx.academyId(),
                new SessionEventMetricsUpdateRequest(List.of(gameScored.getLast())), null));

        // E9: neither complete nor cancel the session while an event is IN_PROGRESS
        assertThrows(ConflictException.class, () -> sessionService.complete(session, fx.academyId(), null));
        assertThrows(ConflictException.class, () -> sessionService.cancel(session, fx.academyId(), null));
        assertEquals("IN_PROGRESS", sessionStatus(session));

        SessionEvaluationEventResponse completed = eventService.complete(event, fx.academyId(), null);
        assertEquals(SessionEvaluationEventStatus.COMPLETED, completed.status());
        assertTrue(!completed.endedAt().isBefore(completed.startedAt()));
        assertThrows(ConflictException.class, () -> eventService.cancel(event, fx.academyId(), null));
        assertThrows(ConflictException.class, () -> eventService.start(event, fx.academyId(), null));

        sessionService.complete(session, fx.academyId(), null);
        assertEquals("COMPLETED", sessionStatus(session));
        assertThrows(ConflictException.class, () -> create(session, game, null));
    }

    /** T4.3-D5: cancelling the session cancels its PLANNED events and leaves the closed ones untouched. */
    @Test
    void cancellingTheSessionCancelsOnlyItsPlannedEvents() {
        UUID session = newSession(TrainingSessionStatus.PLANNED);
        UUID done = create(session, game, null).sessionEventId();
        UUID dropped = create(session, game, null).sessionEventId();
        UUID pending = create(session, game, null).sessionEventId();
        sessionService.start(session, fx.academyId(), null);
        eventService.start(done, fx.academyId(), null);
        eventService.complete(done, fx.academyId(), null);
        eventService.cancel(dropped, fx.academyId(), null);

        sessionService.cancel(session, fx.academyId(), null);

        assertEquals("COMPLETED", eventStatus(done));
        assertEquals("CANCELLED", eventStatus(dropped));
        assertEquals("CANCELLED", eventStatus(pending));
        assertEquals(null, jdbc.queryForObject(
                "SELECT started_at FROM session_evaluation_events WHERE session_event_id = ?",
                LocalDateTime.class, pending));
    }

    @Test
    void anEventWithNonVoidedEvaluationsCannotBeCancelled() {
        UUID session = newSession(TrainingSessionStatus.IN_PROGRESS);
        UUID event = create(session, game, null).sessionEventId();
        eventService.start(event, fx.academyId(), null);
        UUID evaluation = evaluation(session, event, "COMPLETED");

        assertThrows(ConflictException.class, () -> eventService.cancel(event, fx.academyId(), null));
        assertEquals("IN_PROGRESS", eventStatus(event));

        jdbc.update("""
                UPDATE evaluations SET status = 'VOIDED', voided_at = now(), voided_by_coach_id = ?,
                       void_reason = 'test' WHERE evaluation_id = ?""", fx.coach(), evaluation);
        SessionEvaluationEventResponse cancelled = eventService.cancel(event, fx.academyId(), null);
        assertEquals(SessionEvaluationEventStatus.CANCELLED, cancelled.status());
        assertTrue(cancelled.endedAt() != null, "a started event keeps start and end when cancelled (V9 CHECK)");
    }

    @Test
    void eventsOfAnotherAcademyAreNotFound() {
        UUID session = newSession(TrainingSessionStatus.PLANNED);
        UUID event = create(session, game, null).sessionEventId();

        assertThrows(NotFoundException.class, () -> eventService.get(event, fx.otherAcademyId(), null));
        assertThrows(NotFoundException.class, () -> eventService.start(event, fx.otherAcademyId(), null));
        assertThrows(NotFoundException.class, () -> eventService.list(session, fx.otherAcademyId(), null));
        assertThrows(NotFoundException.class, () -> eventService.create(session, fx.otherAcademyId(),
                new SessionEvaluationEventCreateRequest(game, null), null));
    }

    /** Without the session lock both requests read the same max(block_order) and one fails on the unique key. */
    @Test
    void concurrentCreationsGetDistinctBlockOrders() throws Exception {
        UUID session = newSession(TrainingSessionStatus.PLANNED);
        List<Object> outcomes = concurrently(
                () -> create(session, game, null),
                () -> create(session, game, null));

        assertTrue(outcomes.stream().allMatch(SessionEvaluationEventResponse.class::isInstance), outcomes.toString());
        assertEquals(Set.of(1, 2), outcomes.stream().map(SessionEvaluationEventResponse.class::cast)
                .map(SessionEvaluationEventResponse::blockOrder).collect(Collectors.toSet()));
    }

    /** Without the session lock both requests see PLANNED and both "start" the same event. */
    @Test
    void concurrentStartsOfTheSameEventLetExactlyOneWin() throws Exception {
        UUID session = newSession(TrainingSessionStatus.IN_PROGRESS);
        UUID event = create(session, game, null).sessionEventId();
        List<Object> outcomes = concurrently(
                () -> eventService.start(event, fx.academyId(), null),
                () -> eventService.start(event, fx.academyId(), null));

        assertEquals(1, outcomes.stream().filter(SessionEvaluationEventResponse.class::isInstance).count(),
                outcomes.toString());
        assertInstanceOf(ConflictException.class,
                outcomes.stream().filter(o -> !(o instanceof SessionEvaluationEventResponse)).findFirst().orElseThrow());
        assertEquals("IN_PROGRESS", eventStatus(event));
    }

    @Test
    void listingIssuesConstantQueriesRegardlessOfTheNumberOfEvents() {
        UUID session = newSession(TrainingSessionStatus.PLANNED);
        for (int i = 0; i < 4; i++) {
            create(session, game, null);
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        List<SessionEvaluationEventResponse> events = eventService.list(session, fx.academyId(), null);

        assertEquals(List.of(1, 2, 3, 4), events.stream().map(SessionEvaluationEventResponse::blockOrder).toList());
        events.forEach(event -> assertEquals(3, event.metrics().size()));
        // session lookup + events with their template + metrics of all events with their criterion
        assertTrue(statistics.getPrepareStatementCount() <= 3,
                "expected at most 3 statements but got " + statistics.getPrepareStatementCount());
        assertEquals(0, statistics.getEntityFetchCount(), "lazy associations were fetched one by one (N+1)");
    }

    // ---- helpers ------------------------------------------------------------------------------------------------

    private SessionEvaluationEventResponse create(UUID session, UUID template, List<UUID> metrics) {
        return eventService.create(session, fx.academyId(),
                new SessionEvaluationEventCreateRequest(template, metrics), null);
    }

    /** Runs both calls at once; the mocked authorization step holds them until both have entered the service. */
    private List<Object> concurrently(Callable<Object> first, Callable<Object> second) throws Exception {
        CyclicBarrier bothInside = new CyclicBarrier(2);
        doAnswer(invocation -> {
            bothInside.await(10, TimeUnit.SECONDS);
            return null;
        }).when(accessPolicy).requireManageAccess(any(), any(), any());

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

    private Set<UUID> metricsInDb(UUID event) {
        return Set.copyOf(jdbc.queryForList(
                "SELECT criterion_id FROM session_event_metrics WHERE session_event_id = ?", UUID.class, event));
    }

    private String eventStatus(UUID event) {
        return jdbc.queryForObject("SELECT status FROM session_evaluation_events WHERE session_event_id = ?",
                String.class, event);
    }

    private String sessionStatus(UUID session) {
        return jdbc.queryForObject("SELECT status FROM training_sessions WHERE session_id = ?", String.class, session);
    }

    private UUID templateId(String code) {
        return jdbc.queryForObject(
                "SELECT template_id FROM evaluation_templates WHERE code = ? ORDER BY version DESC LIMIT 1",
                UUID.class, code);
    }

    private UUID evaluation(UUID session, UUID event, String status) {
        return jdbc.queryForObject("""
                INSERT INTO evaluations (academy_category_id, session_id, session_event_id, player_id, template_id,
                                         evaluator_coach_id, evaluation_date, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, now(), ?, now(), now()) RETURNING evaluation_id""",
                UUID.class, fx.academyCategoryId(), session, event, fx.player(), game, fx.coach(), status);
    }

    private UUID newSession(TrainingSessionStatus status) {
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        return inTransaction(() -> new TestDataFactory(entityManager).session(fx.academyCategoryId(), fx.coach(), null,
                "Events IT session", now, status,
                status == TrainingSessionStatus.IN_PROGRESS ? now : null, null).getSessionId());
    }

    private Fixture createFixture() {
        return inTransaction(() -> {
            TestDataFactory factory = new TestDataFactory(entityManager);
            LocalDate today = LocalDate.now(clock);
            AcademyEntity academy = factory.academy("Events");
            AcademyEntity otherAcademy = factory.academy("Events other");
            AcademyCategoryEntity category = factory.academyCategory(academy, "EVENTS_IT", today);
            CoachEntity coach = factory.coach("events-head", "Head");
            UUID player = factory.player("events-player", "Ana").getPlayerId();
            entityManager.flush();
            return new Fixture(academy.getAcademyId(), otherAcademy.getAcademyId(),
                    category.getAcademyCategoryId(), coach.getCoachId(), player);
        });
    }

    private <T> T inTransaction(Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }

    private record Fixture(UUID academyId, UUID otherAcademyId, UUID academyCategoryId, UUID coach, UUID player) {
    }
}

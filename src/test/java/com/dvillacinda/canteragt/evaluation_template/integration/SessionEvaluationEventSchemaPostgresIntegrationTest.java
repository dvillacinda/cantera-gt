package com.dvillacinda.canteragt.evaluation_template.integration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.dvillacinda.canteragt.academy.entity.AcademyEntity;
import com.dvillacinda.canteragt.academy_category.entity.AcademyCategoryEntity;
import com.dvillacinda.canteragt.coach.entity.CoachEntity;
import com.dvillacinda.canteragt.support.TestDataFactory;
import com.dvillacinda.canteragt.training_session.enums.TrainingSessionStatus;

import jakarta.persistence.EntityManager;

/**
 * T4.2: audit of the V3 and V9 schema for session evaluation events against a real PostgreSQL ({@code postgres-test}).
 *
 * <p>There are no JPA entities for events yet, so rows are written with autocommit JDBC: every statement commits on
 * its own, which also fires V3's DEFERRABLE INITIALLY DEFERRED constraint triggers immediately.
 *
 * <p>The gaps the audit found in V3 (R-2, event timestamps, block order) are closed by V9 and tested as rejections.
 * Tests named {@code gap…} document rules that stay in the service by design (DP-2.2, DP-2.3): the database accepts
 * those rows.
 */
@SpringBootTest
class SessionEvaluationEventSchemaPostgresIntegrationTest {

    private static final String SMALL_SIDED_GAME = "JUEGO_REDUCIDO";            // 6 scored metrics
    private static final String DRIBBLING = "CONDUCCION_CAMBIOS_DIRECCION";     // has a non-scored component
    private static final String FINISHING = "FINALIZACION";                     // any other template

    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Clock clock;

    private Fixture fx;
    private UUID game;
    private List<UUID> gameMetrics;

    @BeforeEach
    void setUp() {
        fx = createFixture();
        game = templateId(SMALL_SIDED_GAME);
        gameMetrics = scoredCriteria(game);
        assertTrue(gameMetrics.size() >= 4, SMALL_SIDED_GAME + " needs at least 4 scored metrics for this audit");
    }

    // ---- rules V3 enforces ------------------------------------------------------------------------------------

    @Test
    void blockOrderIsUniquePerSessionOnly() {
        event(fx.sessionA(), game, 1);
        assertRejected("session_evaluation_events_order_uq", () -> event(fx.sessionA(), game, 1));
        assertDoesNotThrow(() -> event(fx.sessionB(), game, 1), "the same block order is allowed in another session");
    }

    @Test
    void eventStatusIsRestrictedToTheLifecycleValues() {
        assertRejected("session_evaluation_events_status_check", () -> jdbc.update("""
                INSERT INTO session_evaluation_events (session_id, template_id, block_order, status)
                VALUES (?, ?, 1, 'DONE')""", fx.sessionA(), game));
    }

    @Test
    void eventAcceptsAtMostThreeScoredMetricsOfItsOwnTemplate() {
        UUID event = event(fx.sessionA(), game, 1);
        for (UUID metric : gameMetrics.subList(0, 3)) {
            metric(event, game, metric);
        }
        assertEquals(3, metricCount(event));
        assertRejected("Un evento admite como", () -> metric(event, game, gameMetrics.get(3)));
        assertEquals(3, metricCount(event), "the fourth metric was rolled back");

        UUID finishing = templateId(FINISHING);
        UUID other = event(fx.sessionA(), game, 2);
        UUID foreignMetric = scoredCriteria(finishing).get(0);
        assertRejected("session_event_metrics_criterion_fk", () -> metric(other, game, foreignMetric));
        assertRejected("session_event_metrics_event_fk", () -> metric(other, finishing, foreignMetric));
    }

    @Test
    void nonScoredComponentCannotBeActivatedOnItsOwn() {
        UUID dribbling = templateId(DRIBBLING);
        UUID component = jdbc.queryForObject("""
                SELECT criterion_id FROM evaluation_criteria
                 WHERE template_id = ? AND NOT is_scored ORDER BY display_order LIMIT 1""", UUID.class, dribbling);
        UUID event = event(fx.sessionA(), dribbling, 1);
        assertRejected("sin score", () -> metric(event, dribbling, component));
    }

    @Test
    void evaluationMustUseTheTemplateOfItsEvent() {
        UUID event = event(fx.sessionA(), game, 1);
        UUID finishing = templateId(FINISHING);
        assertRejected("evaluations_event_template_fk",
                () -> evaluation(fx.categoryA(), fx.sessionA(), event, fx.player(), finishing, "COMPLETED"));
    }

    @Test
    void playerIsEvaluatedOncePerEventUnlessTheEvaluationIsVoided() {
        UUID event = event(fx.sessionA(), game, 1);
        UUID first = evaluation(fx.categoryA(), fx.sessionA(), event, fx.player(), game, "COMPLETED");
        assertRejected("evaluations_event_player_uq",
                () -> evaluation(fx.categoryA(), fx.sessionA(), event, fx.player(), game, "DRAFT"));
        assertDoesNotThrow(() -> evaluation(fx.categoryA(), fx.sessionA(), event, fx.player2(), game, "COMPLETED"),
                "another player in the same event is fine");

        jdbc.update("""
                UPDATE evaluations SET status = 'VOIDED', voided_at = now(), voided_by_coach_id = ?,
                       void_reason = 'Wrong player' WHERE evaluation_id = ?""", fx.coach(), first);
        assertDoesNotThrow(() -> evaluation(fx.categoryA(), fx.sessionA(), event, fx.player(), game, "COMPLETED"),
                "a voided evaluation frees the unique index");
    }

    @Test
    void voidedStatusRequiresTimestampAndReasonAndNothingElseMayCarryThem() {
        UUID event = event(fx.sessionA(), game, 1);
        UUID evaluation = evaluation(fx.categoryA(), fx.sessionA(), event, fx.player(), game, "COMPLETED");

        assertRejected("evaluations_void_check", () -> jdbc.update(
                "UPDATE evaluations SET status = 'VOIDED', voided_at = now() WHERE evaluation_id = ?", evaluation));
        assertRejected("evaluations_void_check", () -> jdbc.update(
                "UPDATE evaluations SET status = 'VOIDED', void_reason = 'x' WHERE evaluation_id = ?", evaluation));
        assertRejected("evaluations_void_check", () -> jdbc.update(
                "UPDATE evaluations SET voided_at = now(), void_reason = 'x' WHERE evaluation_id = ?", evaluation));
    }

    @Test
    void scoredResultMustBeOneOfTheEventActiveMetrics() {
        UUID event = event(fx.sessionA(), game, 1);
        metric(event, game, gameMetrics.get(0));
        UUID evaluation = evaluation(fx.categoryA(), fx.sessionA(), event, fx.player(), game, "COMPLETED");

        assertDoesNotThrow(() -> result(evaluation, gameMetrics.get(0)));
        assertRejected("activa en el evento", () -> result(evaluation, gameMetrics.get(1)));
        UUID foreign = scoredCriteria(templateId(FINISHING)).get(0);
        assertRejected("no pertenece a la plantilla", () -> result(evaluation, foreign));
    }

    // ---- closed by V9 (T4.2 audit found these gaps in V3) -----------------------------------------------------

    /** R-2: an evaluation tied to an event belongs to that event's session. */
    @Test
    void evaluationSessionMustBeTheSessionOfItsEvent() {
        UUID eventInA = event(fx.sessionA(), game, 1);
        assertRejected("evaluations_event_session_fk",
                () -> evaluation(fx.categoryA(), fx.sessionB(), eventInA, fx.player(), game, "COMPLETED"));
        assertRejected("evaluations_event_requires_session_check",
                () -> evaluation(fx.categoryA(), null, eventInA, fx.player(), game, "COMPLETED"));
        assertDoesNotThrow(() -> evaluation(fx.categoryA(), fx.sessionA(), eventInA, fx.player(), game, "COMPLETED"));
    }

    /** R-2 variant: evaluations.academy_category_id matches the category of its session. */
    @Test
    void evaluationCategoryMustBeTheCategoryOfItsSession() {
        UUID event = event(fx.sessionA(), game, 1);
        assertRejected("evaluations_session_category_fk",
                () -> evaluation(fx.categoryB(), fx.sessionA(), event, fx.player(), game, "COMPLETED"));
        assertDoesNotThrow(() -> evaluation(fx.categoryB(), null, null, fx.player(), game, "COMPLETED"),
                "an evaluation without session (standalone POST) is still allowed");
    }

    /** Same timestamp rules that V8 gave training_sessions. */
    @Test
    void eventTimestampsMustMatchTheEventStatus() {
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        assertRejected("session_evaluation_events_in_progress_times_check", () -> jdbc.update("""
                INSERT INTO session_evaluation_events (session_id, template_id, block_order, status)
                VALUES (?, ?, 1, 'IN_PROGRESS')""", fx.sessionA(), game));
        assertRejected("session_evaluation_events_completed_times_check", () -> jdbc.update("""
                INSERT INTO session_evaluation_events (session_id, template_id, block_order, status, started_at, ended_at)
                VALUES (?, ?, 2, 'COMPLETED', ?, ?)""", fx.sessionA(), game, now, now.minusHours(1)));
        assertRejected("session_evaluation_events_planned_times_check", () -> jdbc.update("""
                INSERT INTO session_evaluation_events (session_id, template_id, block_order, status, started_at)
                VALUES (?, ?, 3, 'PLANNED', ?)""", fx.sessionA(), game, now));
        assertRejected("session_evaluation_events_cancelled_times_check", () -> jdbc.update("""
                INSERT INTO session_evaluation_events (session_id, template_id, block_order, status, ended_at)
                VALUES (?, ?, 4, 'CANCELLED', ?)""", fx.sessionA(), game, now));

        assertDoesNotThrow(() -> jdbc.update("""
                INSERT INTO session_evaluation_events (session_id, template_id, block_order, status, started_at)
                VALUES (?, ?, 5, 'IN_PROGRESS', ?)""", fx.sessionA(), game, now));
        assertDoesNotThrow(() -> jdbc.update("""
                INSERT INTO session_evaluation_events (session_id, template_id, block_order, status, started_at, ended_at)
                VALUES (?, ?, 6, 'COMPLETED', ?, ?)""", fx.sessionA(), game, now.minusHours(1), now));
        assertDoesNotThrow(() -> jdbc.update("""
                INSERT INTO session_evaluation_events (session_id, template_id, block_order, status)
                VALUES (?, ?, 7, 'CANCELLED')""", fx.sessionA(), game), "cancelled before it started");
    }

    @Test
    void blockOrderMustBePositive() {
        assertRejected("session_evaluation_events_block_order_check", () -> event(fx.sessionA(), game, 0));
        assertRejected("session_evaluation_events_block_order_check", () -> event(fx.sessionA(), game, -1));
    }

    // ---- service rules by design (DP-2.2 / DP-2.3): accepted by the database -----------------------------------

    /** The result trigger only runs on evaluation_results; removing an event metric later is not checked. */
    @Test
    void gapActiveMetricWithRecordedResultsCanBeRemoved() {
        UUID event = event(fx.sessionA(), game, 1);
        metric(event, game, gameMetrics.get(0));
        UUID evaluation = evaluation(fx.categoryA(), fx.sessionA(), event, fx.player(), game, "COMPLETED");
        result(evaluation, gameMetrics.get(0));

        assertEquals(1, jdbc.update("DELETE FROM session_event_metrics WHERE session_event_id = ? AND criterion_id = ?",
                event, gameMetrics.get(0)));
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM evaluation_results WHERE evaluation_id = ?", Integer.class, evaluation));
    }

    /** Evaluations can be attached to an event of a closed session: the session/event state is a service rule. */
    @Test
    void gapSessionAndEventStatusAreNotCheckedForEvaluations() {
        UUID event = jdbc.queryForObject("""
                INSERT INTO session_evaluation_events (session_id, template_id, block_order, status)
                VALUES (?, ?, 1, 'CANCELLED') RETURNING session_event_id""", UUID.class, fx.sessionClosed(), game);
        assertDoesNotThrow(() -> evaluation(fx.categoryA(), fx.sessionClosed(), event, fx.player(), game, "COMPLETED"));
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private void assertRejected(String expectedFragment, Runnable statement) {
        DataIntegrityViolationException ex = assertThrows(DataIntegrityViolationException.class, statement::run);
        String message = String.valueOf(ex.getMostSpecificCause().getMessage());
        assertTrue(message.contains(expectedFragment),
                "expected a rejection mentioning '" + expectedFragment + "' but got: " + message);
    }

    private UUID templateId(String code) {
        return jdbc.queryForObject(
                "SELECT template_id FROM evaluation_templates WHERE code = ? ORDER BY version DESC LIMIT 1",
                UUID.class, code);
    }

    private List<UUID> scoredCriteria(UUID templateId) {
        return jdbc.queryForList("""
                SELECT criterion_id FROM evaluation_criteria
                 WHERE template_id = ? AND is_scored ORDER BY display_order""", UUID.class, templateId);
    }

    private UUID event(UUID sessionId, UUID templateId, int blockOrder) {
        return jdbc.queryForObject("""
                INSERT INTO session_evaluation_events (session_id, template_id, block_order)
                VALUES (?, ?, ?) RETURNING session_event_id""", UUID.class, sessionId, templateId, blockOrder);
    }

    private void metric(UUID eventId, UUID templateId, UUID criterionId) {
        jdbc.update("INSERT INTO session_event_metrics (session_event_id, template_id, criterion_id) VALUES (?, ?, ?)",
                eventId, templateId, criterionId);
    }

    private int metricCount(UUID eventId) {
        return jdbc.queryForObject("SELECT count(*) FROM session_event_metrics WHERE session_event_id = ?",
                Integer.class, eventId);
    }

    private UUID evaluation(UUID categoryId, UUID sessionId, UUID eventId, UUID playerId, UUID templateId,
            String status) {
        return jdbc.queryForObject("""
                INSERT INTO evaluations (academy_category_id, session_id, session_event_id, player_id, template_id,
                                         evaluator_coach_id, evaluation_date, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, now(), ?, now(), now()) RETURNING evaluation_id""",
                UUID.class, categoryId, sessionId, eventId, playerId, templateId, fx.coach(), status);
    }

    private void result(UUID evaluationId, UUID criterionId) {
        jdbc.update("""
                INSERT INTO evaluation_results (evaluation_id, criterion_id, raw_value, created_at, updated_at)
                VALUES (?, ?, 1.0, now(), now())""", evaluationId, criterionId);
    }

    private Fixture createFixture() {
        return inTransaction(() -> {
            TestDataFactory factory = new TestDataFactory(entityManager);
            LocalDate today = LocalDate.now(clock);
            LocalDateTime now = LocalDateTime.now(clock).withNano(0);
            AcademyEntity academy = factory.academy("Audit");
            AcademyCategoryEntity categoryA = factory.academyCategory(academy, "AuditA", today);
            AcademyCategoryEntity categoryB = factory.academyCategory(academy, "AuditB", today);
            CoachEntity coach = factory.coach("auditcoach", "Audit");
            UUID categoryId = categoryA.getAcademyCategoryId();
            UUID sessionA = factory.session(categoryId, coach.getCoachId(), null, "Audit A", now,
                    TrainingSessionStatus.IN_PROGRESS, now, null).getSessionId();
            UUID sessionB = factory.session(categoryId, coach.getCoachId(), null, "Audit B", now.plusDays(1),
                    TrainingSessionStatus.PLANNED, null, null).getSessionId();
            UUID closed = factory.session(categoryId, coach.getCoachId(), null, "Audit closed", now.minusDays(1),
                    TrainingSessionStatus.CANCELLED, null, null).getSessionId();
            UUID player = factory.player("auditp1", "Ana").getPlayerId();
            UUID player2 = factory.player("auditp2", "Beto").getPlayerId();
            entityManager.flush();
            return new Fixture(categoryId, categoryB.getAcademyCategoryId(), coach.getCoachId(), player, player2,
                    sessionA, sessionB, closed);
        });
    }

    private <T> T inTransaction(Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }

    private record Fixture(UUID categoryA, UUID categoryB, UUID coach, UUID player, UUID player2, UUID sessionA,
            UUID sessionB, UUID sessionClosed) {
    }
}

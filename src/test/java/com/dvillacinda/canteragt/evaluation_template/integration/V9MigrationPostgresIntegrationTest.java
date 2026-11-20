package com.dvillacinda.canteragt.evaluation_template.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * T4.2: V9 applied on top of a database that already holds V1–V8 data.
 *
 * <p>Each test migrates its own throwaway schema to V8 with Flyway, inserts rows, migrates to the latest version,
 * and drops the schema. The empty-database case is covered by every Spring Boot test, because {@code postgres-test}
 * starts empty and Flyway applies V1–V9 on context startup.
 */
@SpringBootTest
class V9MigrationPostgresIntegrationTest {

    private static final List<String> V9_CONSTRAINTS = List.of(
            "session_evaluation_events_event_session_uq",
            "evaluations_event_session_fk",
            "evaluations_event_requires_session_check",
            "training_sessions_session_category_uq",
            "evaluations_session_category_fk",
            "session_evaluation_events_block_order_check",
            "session_evaluation_events_planned_times_check",
            "session_evaluation_events_in_progress_times_check",
            "session_evaluation_events_completed_times_check",
            "session_evaluation_events_cancelled_times_check");

    @Autowired private DataSource dataSource;

    @Test
    void v9KeepsCompatibleV8DataAndAddsItsConstraints() throws SQLException {
        inV8Schema((schema, jdbc) -> {
            Data data = insertCompatibleData(jdbc);

            flyway(schema, null).migrate();

            assertEquals("9", jdbc.queryForObject(
                    "SELECT max(version::int)::text FROM flyway_schema_history WHERE success", String.class));
            assertEquals(3, count(jdbc, "session_evaluation_events"));
            assertEquals(3, count(jdbc, "evaluations"));
            assertEquals(data.sessionA(), jdbc.queryForObject(
                    "SELECT session_id FROM evaluations WHERE session_event_id = ?", UUID.class, data.eventA()));
            List<String> present = jdbc.queryForList("""
                    SELECT c.conname FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                     WHERE n.nspname = ?""", String.class, schema);
            for (String constraint : V9_CONSTRAINTS) {
                assertTrue(present.contains(constraint), constraint + " missing after V9");
            }
        });
    }

    @Test
    void v9AbortsWhenAnEvaluationSessionDoesNotMatchItsEvent() throws SQLException {
        assertV9Aborts("evaluations contains rows", (jdbc, data) -> jdbc.update("""
                UPDATE evaluations SET session_id = ? WHERE session_event_id = ?""", data.sessionB(), data.eventA()));
    }

    @Test
    void v9AbortsWhenAnEvaluationCategoryDoesNotMatchItsSession() throws SQLException {
        assertV9Aborts("evaluations contains rows", (jdbc, data) -> jdbc.update("""
                UPDATE evaluations SET academy_category_id = ? WHERE session_event_id = ?""",
                data.categoryB(), data.eventA()));
    }

    @Test
    void v9AbortsWhenEventTimestampsContradictTheirStatus() throws SQLException {
        assertV9Aborts("session_evaluation_events contains rows", (jdbc, data) -> jdbc.update("""
                UPDATE session_evaluation_events SET status = 'IN_PROGRESS' WHERE session_event_id = ?""",
                data.eventA()));
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private interface SchemaWork {
        void run(String schema, JdbcTemplate jdbc) throws SQLException;
    }

    private interface DataChange {
        void apply(JdbcTemplate jdbc, Data data);
    }

    private void assertV9Aborts(String expectedMessage, DataChange incompatible) throws SQLException {
        inV8Schema((schema, jdbc) -> {
            Data data = insertCompatibleData(jdbc);
            incompatible.apply(jdbc, data);

            FlywayException ex = assertThrows(FlywayException.class, () -> flyway(schema, null).migrate());
            assertTrue(String.valueOf(ex.getMessage()).contains("V9 aborted: " + expectedMessage),
                    "unexpected failure: " + ex.getMessage());
            assertEquals("8", jdbc.queryForObject(
                    "SELECT max(version::int)::text FROM flyway_schema_history WHERE success", String.class),
                    "the failed V9 left the schema at V8");
        });
    }

    /** Migrates a fresh schema to V8, runs {@code work} on a connection bound to it, then drops the schema. */
    private void inV8Schema(SchemaWork work) throws SQLException {
        String schema = "v9_audit_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (Connection connection = dataSource.getConnection()) {
            JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            try {
                flyway(schema, "8").migrate();
                jdbc.execute("SET search_path TO " + schema);
                work.run(schema, jdbc);
            } finally {
                jdbc.execute("SET search_path TO public");
                jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    private Flyway flyway(String schema, String target) {
        var configuration = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private record Data(UUID categoryA, UUID categoryB, UUID sessionA, UUID sessionB, UUID eventA) {
    }

    /** V1–V8 rows that satisfy V9: events in each status and evaluations with event, with session only, and alone. */
    private Data insertCompatibleData(JdbcTemplate jdbc) {
        UUID academy = id(jdbc, "INSERT INTO academies (name, status, created_at, updated_at) "
                + "VALUES ('V9 academy', 'ACTIVE', now(), now()) RETURNING academy_id");
        UUID season = id(jdbc, "INSERT INTO seasons (name, start_date, end_date, status, created_at, updated_at) "
                + "VALUES ('V9 season', current_date - 30, current_date + 300, 'ACTIVE', now(), now()) "
                + "RETURNING season_id");
        // Categories are not seeded by any migration, so the throwaway schema needs its own
        UUID category = id(jdbc, "INSERT INTO categories (name, code, min_age, max_age, status, created_at, updated_at) "
                + "VALUES ('V9 Sub13', 'V9_SUB13', 12, 13, 'ACTIVE', now(), now()) RETURNING category_id");
        UUID categoryA = academyCategory(jdbc, academy, category, season);
        UUID seasonB = id(jdbc, "INSERT INTO seasons (name, start_date, end_date, status, created_at, updated_at) "
                + "VALUES ('V9 season B', current_date - 30, current_date + 300, 'ACTIVE', now(), now()) "
                + "RETURNING season_id");
        UUID categoryB = academyCategory(jdbc, academy, category, seasonB);

        UUID coach = id(jdbc, "INSERT INTO coaches (user_id, created_at, updated_at) VALUES (?, now(), now()) "
                + "RETURNING coach_id", user(jdbc, "coach"));
        UUID player = id(jdbc, """
                INSERT INTO players (user_id, birth_date, sex, principal_position_id, created_at, updated_at)
                VALUES (?, DATE '2013-01-01', 'OTHER', (SELECT position_id FROM positions ORDER BY code LIMIT 1),
                        now(), now()) RETURNING player_id""", user(jdbc, "player"));
        UUID template = id(jdbc, "SELECT template_id FROM evaluation_templates WHERE code = 'JUEGO_REDUCIDO' "
                + "ORDER BY version DESC LIMIT 1");

        UUID sessionA = id(jdbc, """
                INSERT INTO training_sessions (academy_category_id, created_by_coach_id, name, session_date, status,
                                               started_at, created_at, updated_at)
                VALUES (?, ?, 'V9 A', now(), 'IN_PROGRESS', now(), now(), now()) RETURNING session_id""",
                categoryA, coach);
        UUID sessionB = id(jdbc, """
                INSERT INTO training_sessions (academy_category_id, created_by_coach_id, name, session_date, status,
                                               created_at, updated_at)
                VALUES (?, ?, 'V9 B', now() + interval '1 day', 'PLANNED', now(), now()) RETURNING session_id""",
                categoryA, coach);

        UUID eventA = id(jdbc, "INSERT INTO session_evaluation_events (session_id, template_id, block_order) "
                + "VALUES (?, ?, 1) RETURNING session_event_id", sessionA, template);
        jdbc.update("""
                INSERT INTO session_evaluation_events (session_id, template_id, block_order, status, started_at, ended_at)
                VALUES (?, ?, 2, 'COMPLETED', now() - interval '1 hour', now())""", sessionA, template);
        jdbc.update("""
                INSERT INTO session_evaluation_events (session_id, template_id, block_order, status)
                VALUES (?, ?, 3, 'CANCELLED')""", sessionA, template);

        evaluation(jdbc, categoryA, sessionA, eventA, player, template, coach);
        evaluation(jdbc, categoryA, sessionA, null, player, template, coach);
        evaluation(jdbc, categoryB, null, null, player, template, coach);
        return new Data(categoryA, categoryB, sessionA, sessionB, eventA);
    }

    private UUID academyCategory(JdbcTemplate jdbc, UUID academy, UUID category, UUID season) {
        return id(jdbc, """
                INSERT INTO academy_categories (academy_id, category_id, season_id, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', now(), now()) RETURNING academy_category_id""", academy, category, season);
    }

    private UUID user(JdbcTemplate jdbc, String handle) {
        String unique = handle + "_" + UUID.randomUUID().toString().substring(0, 8);
        return id(jdbc, """
                INSERT INTO users (keycloak_id, username, email, status, first_name, last_name, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', 'V9', 'Audit', now(), now()) RETURNING user_id""",
                UUID.randomUUID().toString(), unique, unique + "@example.test");
    }

    private void evaluation(JdbcTemplate jdbc, UUID category, UUID session, UUID event, UUID player, UUID template,
            UUID coach) {
        jdbc.update("""
                INSERT INTO evaluations (academy_category_id, session_id, session_event_id, player_id, template_id,
                                         evaluator_coach_id, evaluation_date, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, now(), 'COMPLETED', now(), now())""",
                category, session, event, player, template, coach);
    }

    private int count(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private UUID id(JdbcTemplate jdbc, String sql, Object... args) {
        return jdbc.queryForObject(sql, UUID.class, args);
    }
}

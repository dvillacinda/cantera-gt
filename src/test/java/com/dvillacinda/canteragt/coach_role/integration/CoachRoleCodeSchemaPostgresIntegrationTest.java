package com.dvillacinda.canteragt.coach_role.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.dvillacinda.canteragt.coach_role.entity.CoachRoleEntity;
import com.dvillacinda.canteragt.coach_role.enums.CoachRoleCode;

import jakarta.persistence.EntityManager;

/**
 * Guards that {@link CoachRoleCode} matches the coach_roles seed and CHECK constraint (V6). A mismatch made
 * Hibernate unable to map seeded rows (the enum once said ASSISTANT_COACH while the database says ASSISTANT).
 * Requires the isolated {@code postgres-test} database (see docs/testing.md).
 */
@SpringBootTest
class CoachRoleCodeSchemaPostgresIntegrationTest {
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactionTemplate;

    private static final Set<String> ENUM_CODES =
            Arrays.stream(CoachRoleCode.values()).map(code -> code.name()).collect(Collectors.toSet());

    @Test
    void enumMatchesTheSeededCoachRoles() {
        Set<String> seeded = Set.copyOf(jdbcTemplate.queryForList("SELECT code FROM coach_roles", String.class));

        assertEquals(ENUM_CODES, seeded);
    }

    @Test
    void everySeededRoleMapsToTheEnumThroughJpa() {
        Set<CoachRoleCode> loaded = transactionTemplate.execute(status -> entityManager
                .createQuery("select r from CoachRoleEntity r", CoachRoleEntity.class)
                .getResultList().stream().map(entity -> entity.getCoachRoleCode()).collect(Collectors.toSet()));

        assertEquals(Set.of(CoachRoleCode.values()), loaded);
    }

    @Test
    void checkConstraintAcceptsExactlyTheEnumValues() {
        String constraint = jdbcTemplate.queryForObject("""
                SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c
                WHERE c.conname = 'coach_roles_code_check'
                """, String.class);
        for (String code : ENUM_CODES) {
            assertEquals(true, constraint.contains("'" + code + "'"), code + " missing in " + constraint);
        }
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO coach_roles (coach_role_id, code, name) VALUES (?, 'ASSISTANT_COACH', 'Legacy')",
                UUID.randomUUID()));
    }
}

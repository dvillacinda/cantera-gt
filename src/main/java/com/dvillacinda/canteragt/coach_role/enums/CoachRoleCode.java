package com.dvillacinda.canteragt.coach_role.enums;

/**
 * Role of a coach inside an academy category ({@code coach_assignments.coach_role_id}).
 *
 * <p>Must match exactly the {@code coach_roles_code_check} constraint and the seed in
 * {@code V6__seed_positions_and_coach_roles.sql}; {@code CoachRoleCodeSchemaPostgresIntegrationTest} guards it.
 * Not to be confused with the Keycloak realm roles (SYSTEM_ADMIN, ACADEMY_ADMIN, COACH, PLAYER, ASSISTANT) nor with
 * {@code SessionParticipationType} (LEAD/ASSISTANT/SUPPORT), which is the role inside a single training session.
 */
public enum CoachRoleCode {
    /** Director técnico: manages the category (sessions, roster, staff, evaluations). */
    HEAD_COACH,
    /** Asistente técnico: supports the head coach; read-only on session management. */
    ASSISTANT,
    /** Preparador físico: physical preparation and physical tests; read-only on session management. */
    FITNESS_COACH;
}

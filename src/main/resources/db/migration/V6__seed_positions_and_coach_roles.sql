-- =====================================================================
-- V6: catálogo de posiciones y roles contextuales del entrenador
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Posiciones
-- ---------------------------------------------------------------------
-- positions.code y positions.line deben coincidir con los enums
-- PositionCode y PositionLine. Agregar una posición nueva requiere
-- actualizar el enum y estos CHECK en una nueva migración.

ALTER TABLE "positions"
    ADD CONSTRAINT positions_code_check
        CHECK ("code" IN ('GK', 'CB', 'LB', 'RB', 'CDM', 'CM', 'CAM', 'LW', 'RW', 'ST')),
    ADD CONSTRAINT positions_line_check
        CHECK ("line" IN ('GOALKEEPER', 'DEFENSE', 'MIDFIELD', 'FORWARD'));

-- ON CONFLICT: posiciones ya creadas desde la API conservan su position_id
-- (y sus jugadores), pero se normalizan nombre y línea.
INSERT INTO "positions" ("code", "name", "line") VALUES
    ('GK',  'Portero',                  'GOALKEEPER'),
    ('CB',  'Defensa central',          'DEFENSE'),
    ('LB',  'Lateral izquierdo',        'DEFENSE'),
    ('RB',  'Lateral derecho',          'DEFENSE'),
    ('CDM', 'Mediocampista defensivo',  'MIDFIELD'),
    ('CM',  'Mediocampista central',    'MIDFIELD'),
    ('CAM', 'Mediocampista ofensivo',   'MIDFIELD'),
    ('LW',  'Extremo izquierdo',        'FORWARD'),
    ('RW',  'Extremo derecho',          'FORWARD'),
    ('ST',  'Delantero centro',         'FORWARD')
ON CONFLICT ("code") DO UPDATE
    SET "name" = EXCLUDED."name",
        "line" = EXCLUDED."line";

-- ---------------------------------------------------------------------
-- 2. roles -> coach_roles
-- ---------------------------------------------------------------------
-- No son roles globales (esos viven en Keycloak: SYSTEM_ADMIN,
-- ACADEMY_ADMIN, COACH, PLAYER) sino el rol de un entrenador dentro de
-- una categoría (coach_assignments), que define qué acciones puede tomar.
-- coach_roles.code debe coincidir con el enum CoachRoleCode.

ALTER TABLE "roles" RENAME TO "coach_roles";
ALTER TABLE "coach_roles" RENAME COLUMN "role_id" TO "coach_role_id";
ALTER TABLE "coach_roles" RENAME CONSTRAINT "roles_pkey" TO "coach_roles_pkey";
ALTER TABLE "coach_roles" RENAME CONSTRAINT "roles_code_key" TO "coach_roles_code_key";

ALTER TABLE "coach_assignments" RENAME COLUMN "role_id" TO "coach_role_id";
ALTER TABLE "coach_assignments"
    RENAME CONSTRAINT "coach_assignments_role_id_fkey" TO "coach_assignments_coach_role_id_fkey";
ALTER INDEX IF EXISTS "coach_assignments_academy_category_id_role_id_status_idx"
    RENAME TO "coach_assignments_academy_category_id_coach_role_id_status_idx";

ALTER TABLE "coach_roles"
    ADD CONSTRAINT coach_roles_code_check
        CHECK ("code" IN ('HEAD_COACH', 'ASSISTANT', 'FITNESS_COACH'));

COMMENT ON TABLE "coach_roles" IS 'Role of a coach inside an academy category (used in coach_assignments.coach_role_id); defines which actions the coach can take. Global roles live exclusively in Keycloak.';
COMMENT ON COLUMN "coach_roles"."code" IS 'HEAD_COACH, ASSISTANT, FITNESS_COACH';

INSERT INTO "coach_roles" ("code", "name", "description") VALUES
    ('HEAD_COACH',    'Director técnico',   'Responsable principal de la categoría: planifica sesiones y evaluaciones'),
    ('ASSISTANT',     'Asistente técnico',  'Apoya al director técnico en sesiones y evaluaciones'),
    ('FITNESS_COACH', 'Preparador físico',  'Responsable de la preparación física y de las pruebas físicas')
ON CONFLICT ("code") DO UPDATE
    SET "name"        = EXCLUDED."name",
        "description" = EXCLUDED."description";

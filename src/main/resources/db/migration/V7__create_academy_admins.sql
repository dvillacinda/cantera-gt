-- =====================================================================
-- V7: alcance de los administradores de academia
-- =====================================================================
-- Keycloak define QUÉ puede hacer un usuario (rol global ACADEMY_ADMIN);
-- esta tabla define SOBRE QUÉ academias puede hacerlo. Un ACADEMY_ADMIN
-- sin filas ACTIVE aquí no administra ninguna academia.
-- Un usuario puede administrar varias academias (N:M).
-- ---------------------------------------------------------------------

CREATE TABLE "academy_admins" (
  "academy_admin_id" uuid        DEFAULT uuid_generate_v7() PRIMARY KEY,
  "user_id"          uuid        NOT NULL,
  "academy_id"       uuid        NOT NULL,
  "status"           varchar(20) NOT NULL,
  "created_at"       timestamp   NOT NULL DEFAULT now(),
  "updated_at"       timestamp   NOT NULL DEFAULT now(),
  CONSTRAINT academy_admins_user_fk
    FOREIGN KEY ("user_id") REFERENCES "users" ("user_id"),
  CONSTRAINT academy_admins_academy_fk
    FOREIGN KEY ("academy_id") REFERENCES "academies" ("academy_id"),
  CONSTRAINT academy_admins_user_academy_uq UNIQUE ("user_id", "academy_id"),
  CONSTRAINT academy_admins_status_check CHECK ("status" IN ('ACTIVE', 'INACTIVE'))
);

-- El UNIQUE (user_id, academy_id) ya cubre la búsqueda por usuario.
CREATE INDEX academy_admins_academy_status_idx ON "academy_admins" ("academy_id", "status");

COMMENT ON TABLE "academy_admins" IS 'Academies each ACADEMY_ADMIN (Keycloak global role) can manage. The Keycloak role grants the permission; this table grants the scope.';
COMMENT ON COLUMN "academy_admins"."status" IS 'ACTIVE, INACTIVE';

-- UUID v7 (time-ordered). PostgreSQL 17 has no native uuidv7(); it arrives in PG 18.
-- Named uuid_generate_v7 to avoid clashing with the future built-in. After upgrading to
-- PG 18 you can switch the column defaults to uuidv7() and drop this function.
CREATE OR REPLACE FUNCTION uuid_generate_v7() RETURNS uuid AS $$
  SELECT encode(
    set_bit(
      set_bit(
        overlay(uuid_send(gen_random_uuid())
                placing substring(int8send(floor(extract(epoch FROM clock_timestamp()) * 1000)::bigint) FROM 3)
                FROM 1 FOR 6),
        52, 1),
      53, 1),
    'hex')::uuid;
$$ LANGUAGE sql VOLATILE;

CREATE TABLE "users" (
  "user_id" UUID DEFAULT uuid_generate_v7() PRIMARY KEY,
  "keycloak_id" varchar(36) UNIQUE NOT NULL,
  "username" varchar(100) UNIQUE NOT NULL,
  "email" varchar(254) UNIQUE NOT NULL,
  "status" varchar(20) NOT NULL,
  "created_at" TIMESTAMP NOT NULL,
  "updated_at" TIMESTAMP NOT NULL
);

CREATE TABLE "positions" (
  "position_id" UUID DEFAULT uuid_generate_v7() PRIMARY KEY,
  "code" varchar(30) UNIQUE NOT NULL,
  "name" varchar(100) NOT NULL,
  "line" varchar(20) NOT NULL
);

CREATE TABLE "players" (
  "player_id" UUID DEFAULT uuid_generate_v7() PRIMARY KEY,
  "user_id" uuid UNIQUE NOT NULL,
  "first_name" varchar(100) NOT NULL,
  "last_name" varchar(100) NOT NULL,
  "birth_date" date NOT NULL,
  "sex" varchar(20) NOT NULL,
  "principal_position_id" uuid NOT NULL,
  "created_at" TIMESTAMP NOT NULL,
  "updated_at" TIMESTAMP NOT NULL
);

CREATE TABLE "player_secondary_positions" (
  "player_id" uuid NOT NULL,
  "position_id" uuid NOT NULL,
  PRIMARY KEY ("player_id", "position_id")
);

CREATE TABLE "coaches" (
  "coach_id" UUID DEFAULT uuid_generate_v7() PRIMARY KEY,
  "user_id" uuid UNIQUE NOT NULL,
  "first_name" varchar(100) NOT NULL,
  "last_name" varchar(100) NOT NULL,
  "created_at" TIMESTAMP NOT NULL,
  "updated_at" TIMESTAMP NOT NULL
);

CREATE TABLE "roles" (
  "role_id" UUID DEFAULT uuid_generate_v7() PRIMARY KEY,
  "code" varchar(50) UNIQUE NOT NULL,
  "name" varchar(100) NOT NULL,
  "description" varchar(255)
);

CREATE TABLE "academies" (
  "academy_id" UUID DEFAULT uuid_generate_v7() PRIMARY KEY,
  "name" varchar(150) NOT NULL,
  "status" varchar(20) NOT NULL,
  "created_at" TIMESTAMP NOT NULL,
  "updated_at" TIMESTAMP NOT NULL
);

CREATE TABLE "academy_locations" (
  "location_id" UUID DEFAULT uuid_generate_v7() PRIMARY KEY,
  "academy_id" uuid NOT NULL,
  "name" varchar(100) NOT NULL,
  "address" varchar(255),
  "status" varchar(20) NOT NULL,
  "created_at" TIMESTAMP NOT NULL,
  "updated_at" TIMESTAMP NOT NULL
);

CREATE TABLE "categories" (
  "category_id" UUID DEFAULT uuid_generate_v7() PRIMARY KEY,
  "name" varchar(100) UNIQUE NOT NULL,
  "age_limit" int,
  "status" varchar(20) NOT NULL,
  "created_at" TIMESTAMP NOT NULL,
  "updated_at" TIMESTAMP NOT NULL
);

CREATE TABLE "seasons" (
  "season_id" UUID DEFAULT uuid_generate_v7() PRIMARY KEY,
  "name" varchar(50) UNIQUE NOT NULL,
  "start_date" date NOT NULL,
  "end_date" date NOT NULL,
  "status" varchar(20) NOT NULL,
  "created_at" TIMESTAMP NOT NULL,
  "updated_at" TIMESTAMP NOT NULL
);

CREATE TABLE "academy_categories" (
  "academy_category_id" UUID DEFAULT uuid_generate_v7() PRIMARY KEY,
  "academy_id" uuid NOT NULL,
  "category_id" uuid NOT NULL,
  "season_id" uuid NOT NULL,
  "status" varchar(20) NOT NULL,
  "created_at" TIMESTAMP NOT NULL,
  "updated_at" TIMESTAMP NOT NULL
);

CREATE TABLE "player_assignments" (
  "player_assignment_id" UUID DEFAULT uuid_generate_v7() PRIMARY KEY,
  "player_id" uuid NOT NULL,
  "academy_category_id" uuid NOT NULL,
  "start_date" date NOT NULL,
  "end_date" date,
  "status" varchar(20) NOT NULL,
  "created_at" TIMESTAMP NOT NULL,
  "updated_at" TIMESTAMP NOT NULL
);

CREATE TABLE "coach_assignments" (
  "coach_assignment_id" UUID DEFAULT uuid_generate_v7() PRIMARY KEY,
  "coach_id" uuid NOT NULL,
  "academy_category_id" uuid NOT NULL,
  "role_id" uuid NOT NULL,
  "start_date" date NOT NULL,
  "end_date" date,
  "status" varchar(20) NOT NULL,
  "created_at" TIMESTAMP NOT NULL,
  "updated_at" TIMESTAMP NOT NULL
);

CREATE TABLE "training_sessions" (
  "session_id" UUID DEFAULT uuid_generate_v7() PRIMARY KEY,
  "academy_category_id" uuid NOT NULL,
  "location_id" uuid,
  "created_by_coach_id" uuid NOT NULL,
  "name" varchar(150) NOT NULL,
  "session_date" TIMESTAMP NOT NULL,
  "status" varchar(20) NOT NULL,
  "notes" text,
  "started_at" TIMESTAMP,
  "ended_at" TIMESTAMP,
  "created_at" TIMESTAMP NOT NULL,
  "updated_at" TIMESTAMP NOT NULL
);

CREATE TABLE "session_players" (
  "session_player_id" UUID DEFAULT uuid_generate_v7() PRIMARY KEY,
  "session_id" uuid NOT NULL,
  "player_id" uuid NOT NULL,
  "attendance_status" varchar(20) NOT NULL,
  "created_at" TIMESTAMP NOT NULL,
  "updated_at" TIMESTAMP NOT NULL
);

CREATE TABLE "session_staff" (
  "session_staff_id" UUID DEFAULT uuid_generate_v7() PRIMARY KEY,
  "session_id" uuid NOT NULL,
  "coach_id" uuid NOT NULL,
  "participation_type" varchar(30) NOT NULL,
  "created_at" TIMESTAMP NOT NULL
);

CREATE TABLE "evaluation_templates" (
  "template_id" UUID DEFAULT uuid_generate_v7() PRIMARY KEY,
  "name" varchar(150) NOT NULL,
  "category_id" uuid,
  "position_code" varchar(30),
  "version" int NOT NULL,
  "status" varchar(20) NOT NULL,
  "description" varchar(255),
  "created_at" TIMESTAMP NOT NULL,
  "updated_at" TIMESTAMP NOT NULL
);

CREATE TABLE "evaluation_criteria" (
  "criterion_id" UUID DEFAULT uuid_generate_v7() PRIMARY KEY,
  "template_id" uuid NOT NULL,
  "dimension_code" varchar(30) NOT NULL,
  "code" varchar(60) NOT NULL,
  "name" varchar(120) NOT NULL,
  "description" varchar(255),
  "weight" decimal(6,3) NOT NULL,
  "min_score" decimal(5,2) NOT NULL,
  "max_score" decimal(5,2) NOT NULL,
  "display_order" int NOT NULL,
  "created_at" TIMESTAMP NOT NULL,
  "updated_at" TIMESTAMP NOT NULL
);

CREATE TABLE "evaluations" (
  "evaluation_id" UUID DEFAULT uuid_generate_v7() PRIMARY KEY,
  "academy_category_id" uuid NOT NULL,
  "session_id" uuid,
  "player_id" uuid NOT NULL,
  "template_id" uuid NOT NULL,
  "evaluator_coach_id" uuid NOT NULL,
  "evaluation_date" TIMESTAMP NOT NULL,
  "overall_score" decimal(6,2),
  "growth_score" decimal(6,2),
  "strengths" text,
  "improvement_areas" text,
  "technical_comment" text,
  "physical_comment" text,
  "tactical_comment" text,
  "mental_comment" text,
  "status" varchar(20) NOT NULL,
  "created_at" TIMESTAMP NOT NULL,
  "updated_at" TIMESTAMP NOT NULL
);

CREATE TABLE "evaluation_results" (
  "evaluation_result_id" UUID DEFAULT uuid_generate_v7() PRIMARY KEY,
  "evaluation_id" uuid NOT NULL,
  "criterion_id" uuid NOT NULL,
  "score" decimal(5,2) NOT NULL,
  "comment" varchar(500),
  "created_at" TIMESTAMP NOT NULL,
  "updated_at" TIMESTAMP NOT NULL
);

CREATE TABLE "player_statistics" (
  "player_statistics_id" UUID DEFAULT uuid_generate_v7() PRIMARY KEY,
  "academy_category_id" uuid NOT NULL,
  "player_id" uuid NOT NULL,
  "period_start" date NOT NULL,
  "period_end" date NOT NULL,
  "matches" int NOT NULL DEFAULT 0,
  "minutes" int NOT NULL DEFAULT 0,
  "goals" int NOT NULL DEFAULT 0,
  "assists" int NOT NULL DEFAULT 0,
  "yellow_cards" int NOT NULL DEFAULT 0,
  "red_cards" int NOT NULL DEFAULT 0,
  "created_at" TIMESTAMP NOT NULL,
  "updated_at" TIMESTAMP NOT NULL
);

CREATE TABLE "audit_logs" (
  "audit_log_id" UUID DEFAULT uuid_generate_v7() PRIMARY KEY,
  "user_id" uuid,
  "academy_id" uuid,
  "academy_category_id" uuid,
  "action" varchar(80) NOT NULL,
  "entity_type" varchar(80) NOT NULL,
  "entity_id" uuid,
  "created_at" TIMESTAMP NOT NULL,
  "metadata" json
);

CREATE INDEX ON "players" ("principal_position_id");

CREATE INDEX ON "player_secondary_positions" ("position_id");

CREATE UNIQUE INDEX ON "academy_locations" ("academy_id", "name");

CREATE UNIQUE INDEX ON "academy_categories" ("academy_id", "category_id", "season_id");

CREATE INDEX ON "academy_categories" ("academy_id", "season_id");

CREATE INDEX ON "academy_categories" ("category_id", "season_id");

CREATE INDEX ON "player_assignments" ("academy_category_id", "status");

CREATE INDEX ON "player_assignments" ("player_id", "status");

CREATE INDEX ON "player_assignments" ("player_id", "academy_category_id");

CREATE INDEX ON "player_assignments" ("player_id", "start_date");

CREATE INDEX ON "coach_assignments" ("coach_id", "academy_category_id", "status");

CREATE INDEX ON "coach_assignments" ("academy_category_id", "status");

CREATE INDEX ON "coach_assignments" ("academy_category_id", "role_id", "status");

CREATE INDEX ON "coach_assignments" ("coach_id", "status");

CREATE INDEX ON "training_sessions" ("academy_category_id", "session_date");

CREATE INDEX ON "training_sessions" ("academy_category_id", "status", "session_date");

CREATE INDEX ON "training_sessions" ("created_by_coach_id", "session_date");

CREATE UNIQUE INDEX ON "session_players" ("session_id", "player_id");

CREATE INDEX ON "session_players" ("player_id", "session_id");

CREATE UNIQUE INDEX ON "session_staff" ("session_id", "coach_id");

CREATE INDEX ON "session_staff" ("coach_id", "session_id");

CREATE INDEX ON "evaluation_templates" ("category_id", "position_code", "status");

CREATE UNIQUE INDEX ON "evaluation_templates" ("name", "version");

CREATE INDEX ON "evaluation_criteria" ("template_id", "display_order");

CREATE UNIQUE INDEX ON "evaluation_criteria" ("template_id", "code");

CREATE INDEX ON "evaluations" ("player_id", "evaluation_date");

CREATE INDEX ON "evaluations" ("academy_category_id", "evaluation_date");

CREATE INDEX ON "evaluations" ("session_id");

CREATE INDEX ON "evaluations" ("evaluator_coach_id", "evaluation_date");

CREATE UNIQUE INDEX ON "evaluation_results" ("evaluation_id", "criterion_id");

CREATE INDEX ON "evaluation_results" ("criterion_id");

CREATE INDEX ON "player_statistics" ("player_id", "period_start", "period_end");

CREATE INDEX ON "player_statistics" ("academy_category_id", "period_start", "period_end");

CREATE UNIQUE INDEX ON "player_statistics" ("player_id", "academy_category_id", "period_start", "period_end");

CREATE INDEX ON "audit_logs" ("user_id", "created_at");

CREATE INDEX ON "audit_logs" ("academy_category_id", "created_at");

CREATE INDEX ON "audit_logs" ("entity_type", "entity_id", "created_at");

COMMENT ON COLUMN "users"."keycloak_id" IS 'JWT sub from Keycloak';

COMMENT ON COLUMN "users"."username" IS 'preferred_username from Keycloak';

COMMENT ON COLUMN "users"."status" IS 'ACTIVE, INACTIVE, LOCKED, PENDING';

COMMENT ON TABLE "roles" IS 'Contextual roles only (used in coach_assignments.role_id). Global/administrative roles live exclusively in Keycloak; there is no user_roles table.';

COMMENT ON COLUMN "players"."user_id" IS 'Every player must have an app account';

COMMENT ON COLUMN "players"."sex" IS 'Controlled value; avoid storing derived age';

COMMENT ON COLUMN "players"."principal_position_id" IS 'Main position of the player; references positions.';

COMMENT ON TABLE "player_secondary_positions" IS 'Secondary positions of a player (0..N). A secondary position should differ from players.principal_position_id.';

COMMENT ON TABLE "positions" IS 'Catalog of football positions.';

COMMENT ON COLUMN "positions"."code" IS 'Stable position code, e.g. GK, CB, LB, RB, CDM, CM, CAM, LW, RW, ST';

COMMENT ON COLUMN "positions"."line" IS 'GOALKEEPER, DEFENSE, MIDFIELD, FORWARD';

COMMENT ON COLUMN "roles"."code" IS 'Contextual coach roles, e.g. HEAD_COACH, ASSISTANT, FITNESS_COACH. Not global roles (those come from Keycloak).';

COMMENT ON COLUMN "academies"."status" IS 'ACTIVE, INACTIVE';

COMMENT ON COLUMN "academy_locations"."status" IS 'ACTIVE, INACTIVE';

COMMENT ON COLUMN "categories"."status" IS 'ACTIVE, INACTIVE';

COMMENT ON COLUMN "seasons"."status" IS 'PLANNED, ACTIVE, CLOSED';

COMMENT ON TABLE "academy_categories" IS 'Operational scope: one academy + one category + one season.';

COMMENT ON COLUMN "academy_categories"."status" IS 'PLANNED, ACTIVE, CLOSED';

COMMENT ON TABLE "player_assignments" IS 'Preserves longitudinal history when a player changes category, academy or season.';

COMMENT ON COLUMN "player_assignments"."status" IS 'ACTIVE, INACTIVE, TRANSFERRED, COMPLETED';

COMMENT ON TABLE "coach_assignments" IS 'A coach can have different roles in different academy/category/season contexts.';

COMMENT ON COLUMN "coach_assignments"."status" IS 'ACTIVE, INACTIVE, ENDED';

COMMENT ON COLUMN "training_sessions"."status" IS 'PLANNED, IN_PROGRESS, COMPLETED, CANCELLED';

COMMENT ON COLUMN "session_players"."attendance_status" IS 'PRESENT, ABSENT, JUSTIFIED, LATE';

COMMENT ON TABLE "session_staff" IS 'Controls who can participate in an active session without changing the coach''s permanent role.';

COMMENT ON COLUMN "session_staff"."participation_type" IS 'LEAD, ASSISTANT, SUPPORT';

COMMENT ON TABLE "evaluation_templates" IS 'MVP templates are system-defined. DTs cannot create or modify templates.';

COMMENT ON COLUMN "evaluation_templates"."status" IS 'DRAFT, ACTIVE, RETIRED';

COMMENT ON COLUMN "evaluation_criteria"."dimension_code" IS 'TECHNICAL, PHYSICAL, TACTICAL, MENTAL';

COMMENT ON TABLE "evaluations" IS 'Stores the template/version used so historical evaluations remain stable.';

COMMENT ON COLUMN "evaluations"."status" IS 'DRAFT, COMPLETED, ARCHIVED';

COMMENT ON TABLE "player_statistics" IS 'MVP keeps statistics simple. Future event-level match statistics can be added without breaking the core model.';

COMMENT ON TABLE "audit_logs" IS 'Recommended for Phase 2 to trace changes to evaluations, players, sessions and permissions.';

ALTER TABLE "players" ADD FOREIGN KEY ("user_id") REFERENCES "users" ("user_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "coaches" ADD FOREIGN KEY ("user_id") REFERENCES "users" ("user_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "players" ADD FOREIGN KEY ("principal_position_id") REFERENCES "positions" ("position_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "player_secondary_positions" ADD FOREIGN KEY ("player_id") REFERENCES "players" ("player_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "player_secondary_positions" ADD FOREIGN KEY ("position_id") REFERENCES "positions" ("position_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "evaluation_templates" ADD FOREIGN KEY ("position_code") REFERENCES "positions" ("code") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "role_permissions" ADD FOREIGN KEY ("role_id") REFERENCES "roles" ("role_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "role_permissions" ADD FOREIGN KEY ("permission_id") REFERENCES "permissions" ("permission_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "academy_locations" ADD FOREIGN KEY ("academy_id") REFERENCES "academies" ("academy_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "academy_categories" ADD FOREIGN KEY ("academy_id") REFERENCES "academies" ("academy_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "academy_categories" ADD FOREIGN KEY ("category_id") REFERENCES "categories" ("category_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "academy_categories" ADD FOREIGN KEY ("season_id") REFERENCES "seasons" ("season_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "player_assignments" ADD FOREIGN KEY ("player_id") REFERENCES "players" ("player_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "player_assignments" ADD FOREIGN KEY ("academy_category_id") REFERENCES "academy_categories" ("academy_category_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "coach_assignments" ADD FOREIGN KEY ("coach_id") REFERENCES "coaches" ("coach_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "coach_assignments" ADD FOREIGN KEY ("academy_category_id") REFERENCES "academy_categories" ("academy_category_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "coach_assignments" ADD FOREIGN KEY ("role_id") REFERENCES "roles" ("role_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "training_sessions" ADD FOREIGN KEY ("academy_category_id") REFERENCES "academy_categories" ("academy_category_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "training_sessions" ADD FOREIGN KEY ("location_id") REFERENCES "academy_locations" ("location_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "training_sessions" ADD FOREIGN KEY ("created_by_coach_id") REFERENCES "coaches" ("coach_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "session_players" ADD FOREIGN KEY ("session_id") REFERENCES "training_sessions" ("session_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "session_players" ADD FOREIGN KEY ("player_id") REFERENCES "players" ("player_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "session_staff" ADD FOREIGN KEY ("session_id") REFERENCES "training_sessions" ("session_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "session_staff" ADD FOREIGN KEY ("coach_id") REFERENCES "coaches" ("coach_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "evaluation_templates" ADD FOREIGN KEY ("category_id") REFERENCES "categories" ("category_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "evaluation_criteria" ADD FOREIGN KEY ("template_id") REFERENCES "evaluation_templates" ("template_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "evaluations" ADD FOREIGN KEY ("academy_category_id") REFERENCES "academy_categories" ("academy_category_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "evaluations" ADD FOREIGN KEY ("session_id") REFERENCES "training_sessions" ("session_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "evaluations" ADD FOREIGN KEY ("player_id") REFERENCES "players" ("player_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "evaluations" ADD FOREIGN KEY ("template_id") REFERENCES "evaluation_templates" ("template_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "evaluations" ADD FOREIGN KEY ("evaluator_coach_id") REFERENCES "coaches" ("coach_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "evaluation_results" ADD FOREIGN KEY ("evaluation_id") REFERENCES "evaluations" ("evaluation_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "evaluation_results" ADD FOREIGN KEY ("criterion_id") REFERENCES "evaluation_criteria" ("criterion_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "player_statistics" ADD FOREIGN KEY ("academy_category_id") REFERENCES "academy_categories" ("academy_category_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "player_statistics" ADD FOREIGN KEY ("player_id") REFERENCES "players" ("player_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "audit_logs" ADD FOREIGN KEY ("user_id") REFERENCES "users" ("user_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "audit_logs" ADD FOREIGN KEY ("academy_id") REFERENCES "academies" ("academy_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "audit_logs" ADD FOREIGN KEY ("academy_category_id") REFERENCES "academy_categories" ("academy_category_id") DEFERRABLE INITIALLY IMMEDIATE;
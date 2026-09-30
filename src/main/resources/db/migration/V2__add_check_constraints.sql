ALTER TABLE "users"
ADD CONSTRAINT users_status_check
CHECK ("status" IN ('ACTIVE', 'INACTIVE', 'LOCKED', 'PENDING'));

ALTER TABLE "academies"
ADD CONSTRAINT academies_status_check
CHECK ("status" IN ('ACTIVE', 'INACTIVE'));

ALTER TABLE "academy_locations"
ADD CONSTRAINT academy_locations_status_check
CHECK ("status" IN ('ACTIVE', 'INACTIVE'));

ALTER TABLE "categories"
ADD CONSTRAINT categories_status_check
CHECK ("status" IN ('ACTIVE', 'INACTIVE'));

ALTER TABLE "seasons"
ADD CONSTRAINT seasons_status_check
CHECK ("status" IN ('PLANNED', 'ACTIVE', 'CLOSED'));

ALTER TABLE "academy_categories"
ADD CONSTRAINT academy_categories_status_check
CHECK ("status" IN ('PLANNED', 'ACTIVE', 'CLOSED'));

ALTER TABLE "player_assignments"
ADD CONSTRAINT player_assignments_status_check
CHECK ("status" IN ('ACTIVE', 'INACTIVE', 'TRANSFERRED', 'COMPLETED'));

ALTER TABLE "coach_assignments"
ADD CONSTRAINT coach_assignments_status_check
CHECK ("status" IN ('ACTIVE', 'INACTIVE', 'ENDED'));

ALTER TABLE "training_sessions"
ADD CONSTRAINT training_sessions_status_check
CHECK ("status" IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'));

ALTER TABLE "session_players"
ADD CONSTRAINT session_players_attendance_status_check
CHECK ("attendance_status" IN ('PRESENT', 'ABSENT', 'JUSTIFIED', 'LATE'));

ALTER TABLE "session_staff"
ADD CONSTRAINT session_staff_participation_type_check
CHECK ("participation_type" IN ('LEAD', 'ASSISTANT', 'SUPPORT'));

ALTER TABLE "evaluation_templates"
ADD CONSTRAINT evaluation_templates_status_check
CHECK ("status" IN ('DRAFT', 'ACTIVE', 'RETIRED'));

ALTER TABLE "evaluation_criteria"
ADD CONSTRAINT evaluation_criteria_dimension_code_check
CHECK ("dimension_code" IN ('TECHNICAL', 'PHYSICAL', 'TACTICAL', 'MENTAL'));

ALTER TABLE "evaluations"
ADD CONSTRAINT evaluations_status_check
CHECK ("status" IN ('DRAFT', 'COMPLETED', 'ARCHIVED'));

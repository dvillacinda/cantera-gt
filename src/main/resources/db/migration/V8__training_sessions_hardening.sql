-- Validate existing timestamps before adding stricter constraints. Do not infer
-- historical start/end times from created_at or updated_at.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM "training_sessions"
    WHERE ("status" = 'IN_PROGRESS'
           AND ("started_at" IS NULL OR "ended_at" IS NOT NULL))
       OR ("status" = 'COMPLETED'
           AND ("started_at" IS NULL OR "ended_at" IS NULL
                OR "ended_at" < "started_at"))
       OR ("status" = 'PLANNED'
           AND ("started_at" IS NOT NULL OR "ended_at" IS NOT NULL))
       OR ("status" = 'CANCELLED'
           AND (
             ("started_at" IS NULL AND "ended_at" IS NOT NULL)
             OR ("started_at" IS NOT NULL
                 AND ("ended_at" IS NULL OR "ended_at" < "started_at"))
           ))
  ) THEN
    RAISE EXCEPTION
      'V8 aborted: training_sessions contains rows incompatible with timestamp constraints';
  END IF;
END $$;

ALTER TABLE "training_sessions"
  ADD COLUMN IF NOT EXISTS "version" bigint NOT NULL DEFAULT 0;

ALTER TABLE "session_players"
  ALTER COLUMN "attendance_status" SET DEFAULT 'PENDING';

ALTER TABLE "session_players"
  DROP CONSTRAINT IF EXISTS "session_players_attendance_status_check";

ALTER TABLE "session_players"
  ADD CONSTRAINT "session_players_attendance_status_check"
  CHECK ("attendance_status" IN ('PENDING', 'PRESENT', 'ABSENT', 'JUSTIFIED', 'LATE'));

ALTER TABLE "training_sessions"
  DROP CONSTRAINT IF EXISTS "training_sessions_in_progress_times_check";

ALTER TABLE "training_sessions"
  ADD CONSTRAINT "training_sessions_in_progress_times_check"
  CHECK ("status" <> 'IN_PROGRESS'
         OR ("started_at" IS NOT NULL AND "ended_at" IS NULL));

ALTER TABLE "training_sessions"
  DROP CONSTRAINT IF EXISTS "training_sessions_completed_times_check";

ALTER TABLE "training_sessions"
  ADD CONSTRAINT "training_sessions_completed_times_check"
  CHECK ("status" <> 'COMPLETED'
         OR ("started_at" IS NOT NULL
             AND "ended_at" IS NOT NULL
             AND "ended_at" >= "started_at"));

ALTER TABLE "training_sessions"
  DROP CONSTRAINT IF EXISTS "training_sessions_planned_times_check";

ALTER TABLE "training_sessions"
  ADD CONSTRAINT "training_sessions_planned_times_check"
  CHECK ("status" <> 'PLANNED'
         OR ("started_at" IS NULL AND "ended_at" IS NULL));

ALTER TABLE "training_sessions"
  DROP CONSTRAINT IF EXISTS "training_sessions_cancelled_times_check";

ALTER TABLE "training_sessions"
  ADD CONSTRAINT "training_sessions_cancelled_times_check"
  CHECK ("status" <> 'CANCELLED'
         OR (("started_at" IS NULL AND "ended_at" IS NULL)
             OR ("started_at" IS NOT NULL
                 AND "ended_at" IS NOT NULL
                 AND "ended_at" >= "started_at")));

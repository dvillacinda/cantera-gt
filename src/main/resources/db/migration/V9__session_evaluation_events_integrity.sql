-- V9: integrity for session evaluation events (Sprint 4, T4.2; ADR-0004, DP-2.3).
--
-- The T4.2 audit (SessionEvaluationEventSchemaPostgresIntegrationTest) showed that V3 accepts:
--   * R-2: an evaluation whose session_id differs from its event's session, or that has an event and no session;
--   * an evaluation whose academy_category_id differs from its session's category;
--   * event timestamps that contradict the event status (training_sessions got these CHECKs in V8);
--   * a block_order of zero or below.
-- This migration closes those gaps. It never fixes data silently: if a row is incompatible, it aborts.

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM "evaluations" e
    LEFT JOIN "session_evaluation_events" ev ON ev."session_event_id" = e."session_event_id"
    LEFT JOIN "training_sessions" s ON s."session_id" = e."session_id"
    WHERE (e."session_event_id" IS NOT NULL
           AND (e."session_id" IS NULL OR e."session_id" <> ev."session_id"))
       OR (e."session_id" IS NOT NULL AND e."academy_category_id" <> s."academy_category_id")
  ) THEN
    RAISE EXCEPTION
      'V9 aborted: evaluations contains rows whose session or category does not match their event or session';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM "session_evaluation_events"
    WHERE "block_order" <= 0
       OR ("status" = 'PLANNED'
           AND ("started_at" IS NOT NULL OR "ended_at" IS NOT NULL))
       OR ("status" = 'IN_PROGRESS'
           AND ("started_at" IS NULL OR "ended_at" IS NOT NULL))
       OR ("status" = 'COMPLETED'
           AND ("started_at" IS NULL OR "ended_at" IS NULL OR "ended_at" < "started_at"))
       OR ("status" = 'CANCELLED'
           AND (("started_at" IS NULL AND "ended_at" IS NOT NULL)
                OR ("started_at" IS NOT NULL
                    AND ("ended_at" IS NULL OR "ended_at" < "started_at"))))
  ) THEN
    RAISE EXCEPTION
      'V9 aborted: session_evaluation_events contains rows incompatible with block order or timestamp constraints';
  END IF;
END $$;

-- ---------------------------------------------------------------------
-- 1. R-2: an evaluation tied to an event belongs to that event's session
-- ---------------------------------------------------------------------
ALTER TABLE "session_evaluation_events"
  DROP CONSTRAINT IF EXISTS "session_evaluation_events_event_session_uq";
ALTER TABLE "session_evaluation_events"
  ADD CONSTRAINT "session_evaluation_events_event_session_uq" UNIQUE ("session_event_id", "session_id");

ALTER TABLE "evaluations"
  DROP CONSTRAINT IF EXISTS "evaluations_event_session_fk";
ALTER TABLE "evaluations"
  ADD CONSTRAINT "evaluations_event_session_fk"
  FOREIGN KEY ("session_event_id", "session_id")
  REFERENCES "session_evaluation_events" ("session_event_id", "session_id");

-- A composite FK is not checked when one of its columns is NULL: an event requires a session
ALTER TABLE "evaluations"
  DROP CONSTRAINT IF EXISTS "evaluations_event_requires_session_check";
ALTER TABLE "evaluations"
  ADD CONSTRAINT "evaluations_event_requires_session_check"
  CHECK ("session_event_id" IS NULL OR "session_id" IS NOT NULL);

-- ---------------------------------------------------------------------
-- 2. An evaluation tied to a session belongs to that session's category
-- ---------------------------------------------------------------------
ALTER TABLE "training_sessions"
  DROP CONSTRAINT IF EXISTS "training_sessions_session_category_uq";
ALTER TABLE "training_sessions"
  ADD CONSTRAINT "training_sessions_session_category_uq" UNIQUE ("session_id", "academy_category_id");

ALTER TABLE "evaluations"
  DROP CONSTRAINT IF EXISTS "evaluations_session_category_fk";
ALTER TABLE "evaluations"
  ADD CONSTRAINT "evaluations_session_category_fk"
  FOREIGN KEY ("session_id", "academy_category_id")
  REFERENCES "training_sessions" ("session_id", "academy_category_id");

-- ---------------------------------------------------------------------
-- 3. Event block order and timestamps (same rules as training_sessions in V8)
-- ---------------------------------------------------------------------
ALTER TABLE "session_evaluation_events"
  DROP CONSTRAINT IF EXISTS "session_evaluation_events_block_order_check";
ALTER TABLE "session_evaluation_events"
  ADD CONSTRAINT "session_evaluation_events_block_order_check" CHECK ("block_order" > 0);

ALTER TABLE "session_evaluation_events"
  DROP CONSTRAINT IF EXISTS "session_evaluation_events_planned_times_check";
ALTER TABLE "session_evaluation_events"
  ADD CONSTRAINT "session_evaluation_events_planned_times_check"
  CHECK ("status" <> 'PLANNED'
         OR ("started_at" IS NULL AND "ended_at" IS NULL));

ALTER TABLE "session_evaluation_events"
  DROP CONSTRAINT IF EXISTS "session_evaluation_events_in_progress_times_check";
ALTER TABLE "session_evaluation_events"
  ADD CONSTRAINT "session_evaluation_events_in_progress_times_check"
  CHECK ("status" <> 'IN_PROGRESS'
         OR ("started_at" IS NOT NULL AND "ended_at" IS NULL));

ALTER TABLE "session_evaluation_events"
  DROP CONSTRAINT IF EXISTS "session_evaluation_events_completed_times_check";
ALTER TABLE "session_evaluation_events"
  ADD CONSTRAINT "session_evaluation_events_completed_times_check"
  CHECK ("status" <> 'COMPLETED'
         OR ("started_at" IS NOT NULL
             AND "ended_at" IS NOT NULL
             AND "ended_at" >= "started_at"));

ALTER TABLE "session_evaluation_events"
  DROP CONSTRAINT IF EXISTS "session_evaluation_events_cancelled_times_check";
ALTER TABLE "session_evaluation_events"
  ADD CONSTRAINT "session_evaluation_events_cancelled_times_check"
  CHECK ("status" <> 'CANCELLED'
         OR (("started_at" IS NULL AND "ended_at" IS NULL)
             OR ("started_at" IS NOT NULL
                 AND "ended_at" IS NOT NULL
                 AND "ended_at" >= "started_at")));

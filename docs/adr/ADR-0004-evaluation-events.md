# ADR-0004: Session evaluation events

- **Status:** Draft. Decisions approved on 2026-11-20 (DP-2, "APRUEBO DEFAULTS"). The ADR is published as Accepted
  in T4.7, once the implementation and its tests exist.
- **Implementation:** pending (T4.3 events and metrics, T4.4 evaluation link, T4.5 voiding, T4.6 rotation)

## Context

Sprint 3 delivered training sessions, roster, attendance and session staff. Sprint 4 adds evaluation events: blocks
inside a session where coaches record evidence for the convened players. Facts verified in the repository on
2026-11-20:

- V3 already has `session_evaluation_events` (`block_order` unique per session, status `PLANNED/IN_PROGRESS/COMPLETED/
  CANCELLED`, **no `version` column**) and `session_event_metrics` (composite FKs that prevent mixing templates, and a
  trigger that allows at most 3 active metrics per event).
- `evaluations` has `session_event_id` (composite FK with `template_id`), `voided_at`, `voided_by_coach_id`,
  `void_reason`, the `evaluations_void_check` constraint, and the partial unique index `evaluations_event_player_uq`
  (`session_event_id`, `player_id`) `WHERE status <> 'VOIDED'`. A trigger requires every scored result to belong to
  the event's active metrics.
- There are no JPA entities for events or event metrics.
- `TrainingSessionAccessPolicy.requireDataEntryAccess` exists but has no callers. It allows `SYSTEM_ADMIN` and
  `ACADEMY_ADMIN` in scope, the category's current `HEAD_COACH` in any session state, and session staff (`LEAD`,
  `ASSISTANT`, `SUPPORT`) only while the session is `IN_PROGRESS`.
- `EvaluationService.create` requires a result for every `activeByDefault && scored` criterion (R-1), which blocks
  events that activate other metrics.
- `evaluations.session_id` (V1) and `evaluations.session_event_id` (V3) are not tied together by any visible
  constraint (R-2, to be confirmed in T4.2).
- `EvaluationService.get` only returns an evaluation to the coach who created it (R-5).

## Decision

1. **Data entry and the standalone POST (DP-2.1, option B).**
   - `POST /api/v1/players/{playerId}/evaluations` stays. Without `session_event_id` it keeps its current behaviour;
     it is the mandatory regression.
   - With `session_event_id`, the event rules below apply and access is checked with `requireDataEntryAccess`.
   - A `FITNESS_COACH` who is not in `session_staff` cannot enter data.
   - `SUPPORT` staff can enter data, because the existing policy allows it. This is an assumption to confirm with the
     pilot head coach.
2. **Event lifecycle (DP-2.2, option B).**
   - Events are created and edited while `PLANNED`, in a `PLANNED` or `IN_PROGRESS` session.
   - An event can start only while its session is `IN_PROGRESS`.
   - Transitions: `PLANNED → IN_PROGRESS → COMPLETED` and `PLANNED | IN_PROGRESS → CANCELLED`. Closed events are
     immutable.
   - Completing or cancelling a session that has `IN_PROGRESS` events returns 409.
   - Cancelling an event that has non-voided evaluations returns 409: they must be voided first.
   - Assumption to confirm in T4.3: cancelling a session cancels its `PLANNED` events, which have no evidence.
3. **Evaluation ↔ event link (DP-2.3, options B + C).**
   - Validity: session `IN_PROGRESS`, event `IN_PROGRESS`, player convened with attendance `PRESENT` or `LATE`, same
     template as the event.
   - The required metrics are **the event's metrics**, not the template's `activeByDefault` set (fixes R-1).
   - The server derives `session_id` from the event; the client does not send it.
   - Frequency, validity and group-size issues are returned as warnings in the response and do not block.
   - Database coherence between `evaluations.session_id` and the event's session (R-2) is enforced in V9 only if
     T4.2 shows the gap is real. V9 is tested on an empty database and on one with V1–V8 data.
4. **Voiding and read access (DP-2.4, option B; partial D-05).**
   - The creator, the category's current `HEAD_COACH` and admins in scope can read an evaluation.
   - The creator and the category's current `HEAD_COACH` can void it. A reason is required (at most 500 characters)
     and the author comes from the JWT.
   - Assumption: an admin without a coach profile can read but not void, because `voided_by_coach_id` requires a
     coach.
   - A voided evaluation frees the unique index, so the player can be evaluated again in that event.
   - Collective visibility across the category is decided in S6.
5. **Concurrency (DP-2.5, option A).**
   - Every mutation of events, and of evaluations tied to an event, takes `TrainingSessionLock` after authorization
     and inside the transaction.
   - `evaluations_event_player_uq` stays as the safety net, translated to 409.
   - No `version` column is added to events unless T4.2 or T4.7 shows races the lock does not cover.
6. **Process (DP-2.6, option A).** Work continues on `feature/sprint-4` (it contains `f55a6b3`, the snake_case fix),
   with one commit per task, no push without approval, and a PR to `main` at the end.
7. **Out of scope.** D-01 (the 10-template catalogue against the V4 seed) is postponed. If it later needs a seed
   change, its migration number must be coordinated with V9.

## Consequences

- Event endpoints follow the session conventions in ADR-0001: snake_case, 404 out of scope, 403 for an
  insufficient role within scope, and 409 for state conflicts.
- The lock serializes writes per session. With 20–30 players this is expected to be acceptable (Roadmap
  assumption); T4.7 measures it.
- The standalone evaluation POST keeps two paths until a later decision deprecates it (S7 cleanup).

## Review conditions

| Decision | Revisit if |
|---|---|
| DP-2.1 | The pilot head coach says `SUPPORT` only observes: exclude it in the service. |
| DP-2.2 | Coaches need to cancel events with evidence without voiding one by one: batch voiding (S5). |
| DP-2.3 | T4.2 shows an existing constraint already prevents R-2: no V9, service only. |
| DP-2.4 | Product defines D-05 before S6. |
| DP-2.5 | T4.7 shows noticeable contention: per-event lock or a `version` column (V9). |
| DP-2.6 | Smaller per-task PRs are needed. |

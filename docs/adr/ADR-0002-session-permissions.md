# ADR-0002: Training session permissions

- **Status:** Accepted, 2026-11-09 (DP-1.1 / D-03). Extended 2026-11-18 by the T3.5 and T3.6 decision gates.
- **Implementation:** `TrainingSessionAccessPolicy`, `TrainingSessionLock`, `SessionRosterService`,
  `SessionStaffService`.

## Context

Several kinds of coach work inside one academy category: the head coach, assistants and fitness coaches. Academy
administrators and system administrators also need to act. We had to decide who may create and run a session, who
may only read, and how session participation relates to the coach's permanent role.

## Decision

1. **Two role layers.** Each layer has its own enum.

   | Layer | Enum | Values | Lives in | Scope |
   |---|---|---|---|---|
   | Category role | `CoachRoleCode` | `HEAD_COACH`, `ASSISTANT`, `FITNESS_COACH` | `coach_assignments` | Permanent in the category; must match V6 |
   | Session role | `SessionParticipationType` | `LEAD`, `ASSISTANT`, `SUPPORT` | `session_staff` | Per session |

2. **Who can manage.** Management means create, edit, start, complete, cancel, roster, attendance and staff. It is
   allowed to:
   - `SYSTEM_ADMIN`;
   - `ACADEMY_ADMIN` of the selected academy;
   - a `COACH` with a **current** `HEAD_COACH` assignment (status `ACTIVE`, `start_date ≤ today ≤ end_date`,
     evaluated with the Guatemala `Clock`) in the session's category.
3. **Read-only access.** Other coaches with a current assignment in the category (`ASSISTANT`, `FITNESS_COACH`) can
   read sessions, rosters and staff. They get **403** on management routes.
4. **No access.**
   - A coach without a current assignment in the category, or from another academy, gets **404**.
   - A `PLAYER` gets **403** on every staff route.
5. **Staff membership.**
   - The session creator is registered as `LEAD` in the same transaction as the create (T3.6-B).
   - A session has one `LEAD` at most (T3.6-A). The application enforces this under the session row lock, with no
     database constraint.
   - A `LEAD` cannot be removed while the session is `IN_PROGRESS`, but can be replaced while it is `PLANNED`
     (T3.6-C).
   - A staff member must hold a current assignment in the category.
   - Data entry by staff (`requireDataEntryAccess`: session staff while `IN_PROGRESS`) is defined but not used until
     Sprint 4.
6. **Roster rules.**
   - Players need a current assignment in the category.
   - A batch call is atomic.
   - A player can be removed only while their attendance is `PENDING` (T3.5-B).
   - The `all_active` shortcut is available (T3.5-A).
7. **Order of checks.**
   - First load the resource scoped to the selected academy (out of scope → 404).
   - Then authorize (403).
   - Then take the session row lock and check state rules (409).

## Consequences

- Assistants cannot run sessions in Sprint 3. If product wants delegation, the matrix must change here first.
- Sessions created before T3.6 have no `LEAD` row. There is no production data, so no backfill is needed.
- `CoachRoleCode` must stay in sync with the `coach_roles` seed and its CHECK constraint.
  `CoachRoleCodeSchemaPostgresIntegrationTest` enforces it. The earlier `ASSISTANT_COACH` value never matched the
  database.

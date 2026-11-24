# Training sessions API (Sprints 3–4)

This API covers training sessions: their lifecycle, roster and attendance, the coaches taking part, and the
evaluation events (blocks) run inside a session.

Every route requires a Keycloak bearer token and the `X-Academy-Id` header. All JSON property names use
`snake_case`. Date-times are ISO-8601 with an offset, and the server stores and returns them in academy local time,
`America/Guatemala` (UTC−6). See [ADR-0003](adr/ADR-0003-time-zone-and-clock.md).

Successful responses use the common envelope `{ "success", "status", "message", "data" }`. Error responses use
`{ "status", "error", "message", "field_errors"? }`. Route conventions are in
[ADR-0001](adr/ADR-0001-rest-conventions.md).

## Who can do what

The full rules are in [ADR-0002](adr/ADR-0002-session-permissions.md). In short:

| Caller | Read sessions, roster, staff | Manage (create, edit, lifecycle, roster, attendance, staff) |
|---|---|---|
| `SYSTEM_ADMIN` | yes (existing academy) | yes |
| `ACADEMY_ADMIN` of the selected academy | yes | yes |
| `COACH` with a current `HEAD_COACH` assignment in the category | yes | yes |
| `COACH` with another current assignment in the category (`ASSISTANT`, `FITNESS_COACH`) | yes | no: **403** |
| `COACH` without a current assignment in the category, or another academy | no: **404** | no: **404** |
| `PLAYER` | no: **403** | no: **403** |

A missing or malformed `X-Academy-Id` returns 400. A resource outside the selected academy returns 404, never 403,
so the API does not reveal that the resource exists.

## Sessions

| Method | Path | Success | Main errors |
|---|---|---|---|
| POST | `/api/v1/academy-categories/{academyCategoryId}/training-sessions` | 201 | 400 validation or date window · 403 · 404 · 409 category not `ACTIVE` |
| GET | `/api/v1/academy-categories/{academyCategoryId}/training-sessions?status=&from=&to=&page=&size=&sort=` | 200 page | 400 `size>50`, `from>to` or unsupported `sort` · 404 |
| GET | `/api/v1/training-sessions/{sessionId}` | 200 | 404 |
| PATCH | `/api/v1/training-sessions/{sessionId}` | 200 | 400 · 403 · 404 · 409 not `PLANNED` |
| POST | `/api/v1/training-sessions/{sessionId}/start` | 200 | 409 invalid transition or concurrent change |
| POST | `/api/v1/training-sessions/{sessionId}/complete` | 200 | 409 not `IN_PROGRESS`, players still `PENDING` (their ids are listed), or an evaluation event `IN_PROGRESS` |
| POST | `/api/v1/training-sessions/{sessionId}/cancel` | 200 | 409 already `COMPLETED` or `CANCELLED`, or an evaluation event `IN_PROGRESS` |

**Create body**

```json
{ "name": "Entrenamiento técnico martes", "session_date": "2026-11-24T16:00:00-06:00",
  "location_id": null, "notes": "Foco: pase y recepción" }
```

- **`name`:** 1–150 characters. **`notes`:** up to 2000 characters.
- **`session_date`:** no more than `app.sessions.max-backdate-days` (default 7) in the past and no more than
  `app.sessions.max-future-days` (default 365) ahead.
- **`location_id`:** optional. If sent, it must be an `ACTIVE` location of the same academy.
- **Creator:** `created_by_coach_id` always comes from the JWT and is never read from the body. The creator is also
  registered as the session's `LEAD` staff member.

**PATCH** accepts any subset of the create fields, and only while the session is `PLANNED`. A `null` field means "no
change", so PATCH cannot clear `location_id` or `notes`.

**Lifecycle**

```text
PLANNED ──start──► IN_PROGRESS ──complete──► COMPLETED
   │                    │
   └──────cancel────────┴──────────────────► CANCELLED
```

- `start` sets `started_at`.
- `complete` sets `ended_at`, and requires that no player is still `PENDING` and no evaluation event is
  `IN_PROGRESS`. It cancels the session's `PLANNED` events, which were never run.
- `cancel` sets `ended_at` only if the session had started. It requires that no evaluation event is `IN_PROGRESS`,
  and it cancels the session's `PLANNED` events, which have no evidence yet.
- `COMPLETED` and `CANCELLED` are final. Every other transition returns 409.
- **Concurrency:** each session carries a `version`. If two requests change the same session at once, one wins and
  the other gets 409.

**Listing**

- **Pagination:** `page` is 0-based and `size` defaults to 20, with a maximum of 50.
- **Sort:** defaults to `session_date,desc`. Allowed sort fields are `session_date`, `status`, `name` and
  `created_at` (snake_case only; `sessionDate` is rejected with 400).
- **Page body:** `data` is `{ "content": [...], "page": { "size", "number", "total_elements", "total_pages" } }`.
- **Filters:** `status`, plus `from` and `to` (ISO-8601 with offset).
- **Scope:** results are always restricted to the path's academy category, inside the query itself.

## Roster and attendance

| Method | Path | Success | Main errors |
|---|---|---|---|
| GET | `/api/v1/training-sessions/{sessionId}/players` | 200 | 404 |
| POST | `/api/v1/training-sessions/{sessionId}/players` | 201 with the players added | 400 invalid batch · 409 duplicate or closed session |
| DELETE | `/api/v1/training-sessions/{sessionId}/players/{playerId}` | 204 | 404 not called · 409 closed session or attendance already recorded |
| PATCH | `/api/v1/training-sessions/{sessionId}/players/{playerId}/attendance` | 200 | 400 `PENDING` or unknown value · 409 session not `IN_PROGRESS` |

**Calling players.** Send exactly one of these two forms:
- `{"player_ids": [ … ]}`, with 1–60 ids;
- `{"all_active": true}`, which calls every player with a current assignment in the category who is not yet in the
  session. Repeating it is safe.

Every player must have a current `ACTIVE` assignment in the session's category, checked against today's date in
Guatemala. Expired, future, transferred and other-category assignments are rejected.

**Batch rejection.** The batch is atomic: if any id is invalid, no player is added. The response lists each rejected
id with its reason in `field_errors`:

```json
{ "status": 400, "error": "Bad Request",
  "message": "Batch rejected: no player was added. See rejected player ids",
  "field_errors": { "0193…": "NO_CURRENT_ASSIGNMENT_IN_CATEGORY" } }
```

| Reason | Status |
|---|---|
| `NO_CURRENT_ASSIGNMENT_IN_CATEGORY` | 400 |
| `DUPLICATED_IN_REQUEST` | 400 |
| `ALREADY_IN_SESSION` | 409, only when it is the sole problem |

**Roster changes and attendance.**
- Players can be added or removed while the session is `PLANNED` or `IN_PROGRESS`.
- A player can be removed only while their attendance is still `PENDING`.
- New players start as `PENDING`.
- Attendance (`PRESENT`, `ABSENT`, `JUSTIFIED`, `LATE`) can be recorded only while `IN_PROGRESS`, and cannot be set
  back to `PENDING`.

**Roster entry fields:** `session_player_id`, `player_id`, `first_name`, `last_name`, `principal_position`,
`attendance_status` and `updated_at`. The response never includes email, username or Keycloak id.

## Session staff

| Method | Path | Success | Main errors |
|---|---|---|---|
| GET | `/api/v1/training-sessions/{sessionId}/staff` | 200, `LEAD` first | 404 |
| POST | `/api/v1/training-sessions/{sessionId}/staff` | 201 | 400 coach without a current assignment in the category · 409 duplicate coach, second `LEAD` or closed session |
| DELETE | `/api/v1/training-sessions/{sessionId}/staff/{coachId}` | 204 | 404 not in staff · 409 closed session, or `LEAD` while `IN_PROGRESS` |

The POST body is `{"coach_id": "…", "participation_type": "LEAD" | "ASSISTANT" | "SUPPORT"}`, and a session can have
only one `LEAD`.

The participation type is the coach's role **in this session**. It is independent of the coach's permanent role in
the category (`CoachRoleCode`: `HEAD_COACH`, `ASSISTANT`, `FITNESS_COACH`). Staff membership will enable data entry
by assistants in Sprint 4. It grants nothing yet.

**Staff entry fields:** `session_staff_id`, `coach_id`, `first_name`, `last_name`, `participation_type` and
`created_at`.

## Evaluation events (Sprint 4)

An evaluation event is a block inside a session where coaches apply one official template to the convened players.
The decisions are in [ADR-0004](adr/ADR-0004-evaluation-events.md). Creating and changing events requires the
**manage** access in the table above; reading requires the read access.

| Method | Path | Success | Main errors |
|---|---|---|---|
| POST | `/api/v1/training-sessions/{sessionId}/evaluation-events` | 201 | 400 template not official and `ACTIVE`, or invalid metrics · 403 · 404 · 409 session closed |
| GET | `/api/v1/training-sessions/{sessionId}/evaluation-events` | 200, in block order | 404 |
| GET | `/api/v1/evaluation-events/{eventId}` | 200 | 404 |
| PUT | `/api/v1/evaluation-events/{eventId}/metrics` | 200 | 400 invalid metrics · 409 event not `PLANNED` or session closed |
| POST | `/api/v1/evaluation-events/{eventId}/start` | 200 | 409 event not `PLANNED`, or session not `IN_PROGRESS` |
| POST | `/api/v1/evaluation-events/{eventId}/complete` | 200 | 409 event not `IN_PROGRESS` |
| POST | `/api/v1/evaluation-events/{eventId}/cancel` | 200 | 409 event already closed, or it has evaluations that are not voided |

**Create body**

```json
{ "template_id": "<official ACTIVE template>", "metric_criterion_ids": ["<uuid>", "<uuid>"] }
```

- **`block_order`:** assigned by the server as the next number in the session (1, 2, 3…). The client cannot send it.
  Cancelled events keep their number, and numbers are never reused.
- **`metric_criterion_ids`:** optional. Without it, the event gets the template's metrics marked
  `is_active_by_default`. With it, 1 to 3 **scored** metrics of the same template, without duplicates. Penalty
  components (`is_scored = false`) cannot be activated: they go with their main metric.
- **Template:** must be official and `ACTIVE`. If a template is retired later, its existing events keep working.

**Changing metrics.** `PUT …/metrics` with `{"metric_criterion_ids": [ … ]}` replaces the whole set. It is allowed
only while the event is `PLANNED`, so no results can exist yet. To change the template, cancel the event and create
another one.

**Lifecycle**

```text
PLANNED ──start──► IN_PROGRESS ──complete──► COMPLETED
   │                    │
   └──────cancel────────┴──────────────────► CANCELLED
```

- `start` sets `started_at`. The session must be `IN_PROGRESS`, and the event needs at least one metric.
- `complete` sets `ended_at`.
- `cancel` sets `ended_at` only if the event had started. It returns 409 while the event has evaluations that are not
  voided: void them first.
- `COMPLETED` and `CANCELLED` are final.

**Event fields:** `session_event_id`, `session_id`, `template_id`, `template_code`, `template_name`, `block_order`,
`status`, `started_at`, `ended_at`, `created_at`, `updated_at` and `metrics` (`criterion_id`, `code`, `name`, `unit`,
`display_order`).

## Integrity guarantees

Every roster, attendance, staff or evaluation event change takes a row lock on its session (`SELECT … FOR UPDATE`)
and increments the session's version (`TrainingSessionLock`). This has four effects:
- concurrent changes to the same session run one after another instead of failing;
- a `complete` that races a roster change returns 409, so a session can never be closed with a `PENDING` player;
- the single-`LEAD` rule holds under concurrency, even though no database constraint enforces it;
- two events created at once get different `block_order` values, and two `start` calls on the same event leave one
  200 and one 409. Completing or cancelling the session while an event starts also returns 409.

The database adds its own checks (V3 and V9): at most 3 active metrics per event, metrics only from the event's
template, `block_order` unique per session and greater than zero, and event timestamps that match the status.

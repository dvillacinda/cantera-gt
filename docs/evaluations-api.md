# Evaluation API contract (CAN-003 MVP)

All JSON property names use `snake_case`. Authentication uses the Keycloak bearer token.

## Official templates

| Method | Path | Behavior |
| --- | --- | --- |
| GET | `/api/v1/evaluation-templates` | Lists `ACTIVE`, official templates and their dimensions and criteria. |
| GET | `/api/v1/evaluation-templates/{templateId}` | Returns one active official template and its dimensions, metric definitions, scale bounds, and scale levels. |

Legacy GET paths (`/get-all-templates` and `/get-template-by-id/{templateId}`) remain aliases for compatibility. Template administration is not exposed by the application. The database migration also blocks updates/deletes to official templates and criteria.

## Player self-service

`GET /api/v1/players/me` requires `PLAYER` and resolves the player from the validated JWT `sub` and the local user link. It accepts no player identifier. Its response contains the player's own name, email, birth date, sex, and position data; it omits local user ID, Keycloak subject, account status, academy membership, assignments, and evaluation data. No player self-edit endpoint is provided. Academy and assignment data remain read-only through the existing `/api/v1/player-assignments/get-my-*` routes.

## Coach administration

Creating or deleting a coach account, changing global profile names, and changing global account status require `SYSTEM_ADMIN`. Academy administrators manage coach-to-academy/category assignments through `/api/v1/coach-assignments/**`; each operation verifies the active administrator assignment and scopes the assignment ID to that academy. The general user update/status routes also reject academy administrators when the target user is a coach, preventing a route-level bypass of the global account policy. Coach profile reads remain academy-scoped.

## Record and retrieve an evaluation

| Method | Path | Behavior |
| --- | --- | --- |
| POST | `/api/v1/players/{playerId}/evaluations` | Records a completed evaluation. COACH role required, plus current coach and player assignments in the same academy category. With `session_event_id`, the event rules below apply instead. |
| GET | `/api/v1/players/{playerId}/evaluations/{evaluationId}` | Retrieves an evaluation created by the authenticated coach. |

Staff profile routes under `/api/v1/players/**` and `/api/v1/coaches/**` that address a resource by ID require
`X-Academy-Id`. The backend resolves the active academy and category assignments for each request. A coach must
currently share the player's academy category; an academy administrator must administer the selected academy and
the target must have a current assignment there. Out-of-scope profiles return 404.

There is no player self-profile route today. `PLAYER` cannot call the staff player routes. Adding a self-service route
and deciding its readable/editable fields requires a product decision. Coaches cannot edit their profile through the
coach API. Existing academy-administrator coach mutations can change the coach's global first/last name, global account
status, or delete the global coach/user record after a current assignment is found in the selected academy. A coach
assigned to multiple academies is therefore affected globally. The code does not define whether these global changes
belong to an academy administrator, only a system administrator, or should become academy-local data; this decision
is pending, so the API's current mutation permissions are documented rather than changed here.

Evaluation reads are limited to the coach who created the evaluation. Other coaches receive 404 even if they share
the academy or category; this avoids exposing player data through evaluation IDs. If academy-wide evaluator access is
desired, it needs an explicit policy decision.

The current official catalog has no criterion with `laterality_mode = REQUIRED`. Some criteria use `OPTIONAL`, while
others use `NONE`. The create API validates a supplied side for each result but does not require a LEFT/RIGHT pair.
No bilateral-pair rule can be verified against the official catalog until a template defines such a requirement.

Example request:

```json
{
  "template_id": "00000000-0000-0000-0000-000000000001",
  "template_version": 1,
  "results": [
    {
      "criterion_id": "00000000-0000-0000-0000-000000000002",
      "raw_value": 1.82,
      "laterality": "NONE",
      "observations": [
        { "sequence": 1, "value": 1.91, "valid": true, "note": "Primer intento" },
        { "sequence": 2, "value": 1.82, "valid": true }
      ]
    }
  ],
  "strengths": "Buena aceleración inicial",
  "improvement_areas": "Mantener la técnica al final",
  "general_comment": ""
}
```

The evaluator is resolved from the authenticated JWT and cannot be supplied by the client. The template ID and version must identify the current active official version; criteria must belong to that template. Default active criteria are required. Metric-specific raw evidence, scale bounds, required comments, laterality, and observation payloads are validated. Evaluation, results, and observations are saved in one transaction. The template reference preserves the version used; official template and criterion rows are immutable in the MVP.

Without `session_event_id`, the rules above apply unchanged. `X-Academy-Id` is ignored and is not required. Every
response now includes `session_id`, `session_event_id` and `warnings`; outside an event they are `null`, `null` and an
empty list.

## Evaluations in an evaluation event (Sprint 4)

Sending `session_event_id` in the same POST records the evaluation inside an evaluation event of a training session
(see [sessions-api.md](sessions-api.md) and [ADR-0004](adr/ADR-0004-evaluation-events.md)). The rules are checked in
this order:

| # | Rule | Status if broken |
|---|---|---|
| 1 | `X-Academy-Id` is sent | 400 |
| 2 | The event exists in that academy | 404 |
| 3 | The caller may enter data: the category's current `HEAD_COACH`, or session staff (`LEAD`, `ASSISTANT`, `SUPPORT`) while the session is `IN_PROGRESS` | 403, or 404 without a current assignment in the category |
| 4 | The session and the event are `IN_PROGRESS` | 409 |
| 5 | The player exists | 404 |
| 6 | The player is convened in the session with attendance `PRESENT` or `LATE` | 409 |
| 7 | The player has no evaluation in this event that is not `VOIDED` | 409 |
| 8 | `template_id` and `template_version` are the event's template | 400 |
| 9 | There is a result for each of the **event's active metrics**, and no scored result outside them. Non-scored components are accepted. | 400 |

Access is checked before the session state, so session staff get 403, not 409, when the session is not in progress.
The route still requires the `COACH` role, and the evaluator must have a coach profile. An administrator therefore
needs both to enter data.

**What the server sets.**
- `session_id` and the category come from the event and its session.
- The template is the event's, even if it was retired after the event was created.
- The evaluation date uses the server clock in academy time.
- Concurrent requests for the same player and event are serialized by the session lock, so exactly one is recorded and
  the other gets 409.

**Warnings** never block the request:

| Code | When |
|---|---|
| `FREQUENCY_TOO_SOON` | The player's last non-voided evaluation with the same template is younger than the template's `frequency_min_days` |
| `PREVIOUS_STILL_VALID` | That evaluation is still valid (`valid_until` is today or later) |
| `GROUP_TOO_SMALL` | Fewer players are `PRESENT` or `LATE` in the session than the template's `group_size_min` (assumption pending confirmation with the pilot head coach) |

```json
{ "template_id": "…", "template_version": 1, "session_event_id": "…",
  "results": [ { "criterion_id": "…", "level": 2 }, { "criterion_id": "…", "level": 3 } ] }
```

**Known debt (R-11).** Outside an event, a coach who asks for an existing player without a current assignment gets 403,
while an unknown player gets 404, so the difference reveals that the player exists. The event path returns 404
uniformly. Fixing the standalone path changes a public behavior and is planned for later (T4.8 or Sprint 5).

## Current scope and verification

The focused command is documented in [testing.md](testing.md). PostgreSQL coverage verifies all ten seeded templates and HTTP persistence on a disposable migrated database. Overall and Growth scoring remain outside the MVP until their functional definitions are approved. The scope of changes to a coach's global profile or account status when that coach has assignments in multiple academies also remains a product decision; ID based access is still checked against the selected academy.

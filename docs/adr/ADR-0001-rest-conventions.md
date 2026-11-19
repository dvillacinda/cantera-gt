# ADR-0001: REST conventions for new modules

- **Status:** Accepted, 2026-11-09 (DP-1.4 / D-10; defaults approved)
- **Applies to:** modules created from Sprint 3 on (training sessions first). Legacy routes are left as they are
  until the clean-up in Sprint 7.

## Context

Older controllers use verb-style paths (`/get-player-by-id/{id}`, `/create-…`), and the scoping rules vary between
them. The new sessions module needed one predictable convention before the Flutter client generates code from
OpenAPI.

## Decision

1. **Plural noun resources.** Use HTTP verbs, not verbs in the path. A resource is nested under its owner only when
   the owner defines its scope.
   - `POST /api/v1/academy-categories/{id}/training-sessions` creates a session.
   - `GET /api/v1/training-sessions/{id}` reads one.
   - Lifecycle transitions are sub-resource actions: `POST /training-sessions/{id}/start`, `/complete`, `/cancel`.
2. **Academy context.** Every staff route requires the `X-Academy-Id` header, and the server validates it against
   the resource's academy on every request.
   - Missing or malformed header: **400**.
   - Resource outside the selected academy, or outside the caller's category assignment: **404**, never 403.
3. **Status codes.**

   | Code | When |
   |---|---|
   | 201 | create |
   | 200 | read, update, transition |
   | 204 | delete |
   | 400 | validation or invalid batch |
   | 401 | no or invalid token |
   | 403 | role insufficient inside the caller's scope |
   | 404 | out of scope or not found |
   | 409 | state or concurrency conflict, or duplicate |

4. **Bodies.**
   - JSON is `snake_case` (`@JsonNaming(SnakeCaseStrategy)` on record DTOs). The API never exposes JPA entities.
   - Success responses use the existing `ApiResponse` envelope; errors use `ErrorResponse`.
   - When a batch is rejected, the rejected ids and their reasons go in `fieldErrors`.
5. **Pagination.** Use Spring `Pageable`: `page` is 0-based, `size` defaults to 20 with a maximum of 50, and only
   whitelisted sort fields are allowed. Each list has its own default sort (sessions use `session_date,desc`).
   Roster and staff lists are not paginated, since a category has about 20–30 players.
6. **Server-owned fields.**
   - The server never accepts identity or audit fields from the body (`created_by_coach_id`, `version`,
     timestamps).
   - Optimistic `version` is returned so clients can show it, but conflicts are resolved server-side and returned
     as 409.

## Consequences

- New routes are predictable, and their OpenAPI output is generator-friendly. The sessions E2E test checks that all
  14 session routes are published with a summary.
- Using 404 for out-of-scope resources hides whether a resource exists. Clients must not read a 404 as "deleted".
- Legacy verb-style routes still exist. Removing or aliasing them is deferred to Sprint 7 (D-10), before the v1 API
  is frozen.

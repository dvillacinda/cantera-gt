# Security and persistence tests

Run the focused HTTP authorization and Keycloak compensation tests from the repository root:

```powershell
mvn "-Dtest=EvaluationTemplateSecurityHttpTest,PlayerCoachSecurityHttpTest,EvaluationServiceTest,AcademyAccessServiceTest,UserServiceTest" test
```

`EvaluationTemplateSecurityHttpTest` sends requests through MockMvc, Spring Security's servlet filter chain,
and the evaluation template controller. It covers missing and invalid bearer tokens, the ACADEMY_ADMIN and
COACH roles, and denial of PLAYER. The service is mocked so these tests focus on authentication and method
authorization.

`PlayerCoachSecurityHttpTest` covers missing-token authentication, role denial, academy header propagation, and
consistent not-found handling for inaccessible player and coach resources. These MVC slice tests mock their services,
so they exercise the HTTP boundary; assignment decisions are covered by `AcademyAccessServiceTest` and the PostgreSQL
HTTP integration test. Invalid bearer token behavior is covered by `EvaluationTemplateSecurityHttpTest`.

`EvaluationServiceTest` verifies denial across academy scope and rejects stale template versions before persistence.

`UserServiceTest` verifies the Keycloak compensation path when the database insert fails, and verifies the
reverse synchronization path when a database update fails. The AcademyAccessService tests cover unit-level
current player/coach assignment scope, category matching, academy administrator scope, and nonexistent academy
rejection for SYSTEM_ADMIN.

`KeycloakJwtIntegrationTest` obtains a client-credentials token from the configured Keycloak realm and validates
its signature and issuer with Spring's production `JwtDecoder` and role converter. It runs when
`KEYCLOAK_ISSUER_URL`, `KEYCLOAK_ADMIN_CLIENT_ID`, and `KEYCLOAK_ADMIN_CLIENT_SECRET` are present; otherwise
JUnit marks only that external integration test skipped. It does not create users or modify realm state.

`EvaluationHttpPostgresIntegrationTest` runs against the configured PostgreSQL database. Use the isolated,
disposable `postgres-test` Compose service described in [dev-environment.md](dev-environment.md); the application applies Flyway migrations
and the test inserts coach/player fixtures, evaluates a seeded template over MockMvc, verifies persisted evaluation,
result, and observation rows, and checks later retrieval, cross-scope denials, invalid versions, missing required
criteria, foreign criteria, invalid metric values/observations, and missing resources without partial inserts. Its
`jwt()` request postprocessor tests the Spring authorization/controller/service path; the separate Keycloak test
verifies a real token's signature.

The PostgreSQL HTTP integration also checks future, inactive, and expired coach assignments, category isolation,
creator-only evaluation reads (same and cross academy), and generic 404 responses without evaluation data. It verifies
that the `PASE_RECEPCION` `OPTIONAL` criterion persists and returns both LEFT and RIGHT. The official catalog query
confirms there are no `REQUIRED` criteria, so bilateral-pair rejection remains untestable without a business rule.
It also verifies `/api/v1/players/me` resolves only the JWT subject's profile and omits internal IDs and administrative
fields, and checks academy-admin denial on global coach/user mutations plus academy-scoped assignment updates.

```powershell
docker compose --profile test up -d postgres-test
```

Load `.env` into the Maven process, then override the application database variables so the integration test uses port 5433 and `cantera_gt_test`:

```powershell
Get-Content .env | ForEach-Object {
    if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$' -and $_ -notmatch '^\s*#') {
        Set-Item "Env:$($matches[1])" $matches[2].Trim('"', "'")
    }
}
$env:BACKEND_URL = "localhost:5433"
$env:POSTGRES_DB = "cantera_gt_test"
$env:POSTGRES_USER = "cantera_gt_test"
$env:POSTGRES_PASSWORD = "cantera_gt_test"
mvn "-Dtest=CanteragtApplicationTests,EvaluationHttpPostgresIntegrationTest,KeycloakJwtIntegrationTest" test
```

`java-dotenv` only loads `.env` from `CanteragtApplication.main()`; it does not expose those values to Maven tests.
The command above makes them available to the test process without changing application code. The Keycloak test
uses the configured realm and confidential client and is skipped when its three required environment variables
are absent. The focused MVC and unit command does not need external services.

## Training sessions (Sprint 3)

`TrainingSessionServiceTest` covers the session business rules with a fixed `Clock`. `lifecycleTransitionTable`
is a parameterized table that runs every status (`PLANNED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`) against
every action (start, complete, cancel, edit). It asserts that each invalid transition returns 409 and never
writes to the database. `TrainingSessionRequestValidationTest` checks Bean Validation and `snake_case` naming.
It also checks that a strict mapper rejects `created_by_coach_id` and camelCase fields. `ApiNamingConventionTest`
checks that every controller body (requests, responses and nested types) is snake_case and that its OpenAPI schema
uses the same names; it needs no services.

`TrainingSessionSecurityHttpTest` is an MVC slice test with a mocked service, so it covers only the HTTP layer.
For all five management routes it checks:
- 401 without a token, and 403 for PLAYER before the service runs;
- 403 for a coach who is not HEAD_COACH, and 404 when the `X-Academy-Id` header names another academy, both
  with the standard error body;
- 400 when the header is missing or is not a UUID.

The access decisions themselves are covered by `TrainingSessionAccessPolicyTest`.

`TrainingSessionSchemaPostgresIntegrationTest` checks the V8 constraints, uniqueness, and the repository search.
`TrainingSessionServicePostgresIntegrationTest` runs the service against PostgreSQL and checks two things:
- **Concurrent start:** two threads start the same session after both have loaded version 0. One wins and the
  other gets `ConflictException` (HTTP 409).
- **No N+1 in the listing:** a listing issues at most 2 SQL statements (page and count) with no entity fetches
  per row, measured with Hibernate `Statistics`.

Both PostgreSQL tests must run against `postgres-test`, using the same environment overrides as the evaluation
integration test above.

```powershell
mvn "-Dtest=TrainingSession*Test" test
```

### Session roster and attendance

`SessionRosterServiceTest` uses a fixed `Clock` set to Guatemala local time. It covers these rules:
- only players with a current assignment in the same category can be called;
- a batch is rejected as a whole and every rejected id gets a reason;
- a player already in the session gets 409, never 500;
- the `all_active` shortcut;
- roster changes only while `PLANNED` or `IN_PROGRESS`;
- a player can be removed only while attendance is still `PENDING`;
- attendance can be recorded only while `IN_PROGRESS`.

It also checks that the session row is locked before any roster check.

`SessionRosterSecurityHttpTest` covers the HTTP boundary:
- 401 without a token, 403 for PLAYER and for a coach who is not HEAD_COACH, and 404 for another academy;
- batches over 60 rejected with 400 before the service runs;
- the error body for a rejected batch, which lists ids in `field_errors` (id → reason);
- that the roster body contains no email or Keycloak id.

`SessionRosterPostgresIntegrationTest` runs against `postgres-test` with real dates. It checks that expired,
future, transferred, and other-category assignments are rejected. It also proves three things on the database:
- **Whole-batch rollback:** after a rejected batch there are 0 rows and the session version is unchanged.
- **Duplicates:** adding a player twice returns 409.
- **No completion with `PENDING` players:** completing with a `PENDING` player returns 409 and lists that player.
  If a player is being added concurrently, `complete` fails with 409 instead of closing the session. This is
  the race the session row lock and version bump protect against.

### Session staff

`SessionStaffServiceTest` covers the staff rules with a fixed `Clock`:
- a coach needs a current assignment in the session's category;
- a session has only one LEAD, and the session row is locked before that check;
- a duplicate coach returns 409, never 500;
- staff changes are allowed only while `PLANNED` or `IN_PROGRESS`;
- a LEAD cannot be removed while `IN_PROGRESS`, but can be while `PLANNED`.

`TrainingSessionServiceTest` checks that the creator of a session is registered as LEAD.

`SessionStaffSecurityHttpTest` covers the HTTP boundary: 401, 403 for PLAYER and for a coach who is not
HEAD_COACH, 404 for another academy, body validation, and that the response contains no email or Keycloak id.

`SessionStaffPostgresIntegrationTest` runs against `postgres-test` and checks:
- the creator is registered as LEAD when the session is created;
- expired, ended, future, and other-category coach assignments are rejected;
- a duplicate coach and a second LEAD both return 409;
- two concurrent LEAD requests leave exactly one LEAD;
- the LEAD removal rules;
- that `session_staff` has `created_at` but no `updated_at`.

No database constraint enforces a single LEAD. The concurrent test is what proves the session row lock keeps
the rule.

`CoachRoleCodeSchemaPostgresIntegrationTest` checks that the `CoachRoleCode` enum matches the `coach_roles`
seed and the `coach_roles_code_check` constraint from V6. It also loads every role through JPA. The test exists
because the enum once said `ASSISTANT_COACH` while the database stored `ASSISTANT`, so seeded rows could not be
mapped. `TrainingSessionLockTest` covers the shared session row lock used by the roster and staff services.

### End-to-end sessions scenario

`TrainingSessionLifecyclePostgresIntegrationTest` runs the Sprint 3 scenario over HTTP against `postgres-test`. It
uses the **real** security chain and `TrainingSessionAccessPolicy`: the JWT `sub` is matched to `users.keycloak_id`,
and only Keycloak's admin API is mocked. It covers:
- **Full HEAD_COACH flow:** create (the creator becomes LEAD), call 3 players, reject an expired player and an
  other-category player, add an ASSISTANT to the staff, start, record attendance, a 409 when completing with a
  `PENDING` player, complete, and a 409 when editing afterwards.
- **Denials:** ASSISTANT gets 403 on management and 200 on read, a coach of another category gets 404, and PLAYER
  gets 403.
- **Concurrent `start` over HTTP:** exactly one 200 and one 409.
- **Listing:** pagination, `status` and `from`/`to` filters, and isolation between two academies, with every header
  combination returning 404.
- **OpenAPI:** all 14 session routes are published at `/api-docs` (dev profile) with a summary.

The regression check for `POST /players/{id}/evaluations` is `EvaluationHttpPostgresIntegrationTest`, which runs in
the same suite.

## Evaluation events (Sprint 4)

### Schema audit and V9 (T4.2)

`SessionEvaluationEventSchemaPostgresIntegrationTest` writes rows with plain JDBC, because it audits the database
rules on their own. It checks that the database rejects:
- a duplicate `block_order` in a session, and a `block_order` of zero or below;
- an unknown event status, and event timestamps that do not match the status;
- a metric from another template, a non-scored component activated on its own, and a fourth active metric;
- an evaluation that uses another template than its event's, or a scored result outside the event's metrics;
- a second evaluation of the same player in an event, unless the first one is `VOIDED`;
- an inconsistent `VOIDED` state (`evaluations_void_check`);
- an evaluation whose session is not its event's session (R-2), or whose category is not its session's category.

Two tests named `gap…` document rules that stay in the service by design: the database accepts those rows.

`V9MigrationPostgresIntegrationTest` applies V9 on a separate schema that already holds V1–V8 data. It checks that
compatible data survives and the constraints are added, and that V9 aborts, without fixing data, in three
incompatible cases.

### Events and metrics (T4.3)

- `SessionEvaluationEventServiceTest`: the event lifecycle as a parameterized table (status × action, where a
  rejected transition never flushes). Also covers the server-assigned `block_order`, the default metrics, invalid
  metric selections (400), official `ACTIVE` templates only, the session state checks, and the order authorize →
  lock → re-read → validate.
- `TrainingSessionServiceTest`: a session with an event `IN_PROGRESS` cannot be completed or cancelled (E9), and
  completing or cancelling a session cancels its `PLANNED` events.
- `SessionEvaluationEventSecurityHttpTest`: for every route, 401 without a token, 403 for `PLAYER` and for a coach
  without manage access, 404 for another academy, and 400 without `X-Academy-Id`. Also checks body validation
  (missing template, `block_order` sent by the client, camelCase, 0 or 4 metrics) and the snake_case response.
- `SessionEvaluationEventRequestValidationTest`: Bean Validation and snake_case of the two requests.
- `SessionEvaluationEventPostgresIntegrationTest` runs against `postgres-test`. It checks the entity mapping against
  V3 and V9 (Hibernate `validate`), consecutive block orders, default metrics, metric replacement under the "at most
  3" trigger, the lifecycle coupled with the session, the cascade to `PLANNED` events, the 409 on cancelling an event
  with non-voided evaluations, academy isolation, and constant queries when listing (Hibernate `Statistics`).

The two concurrency tests (two creations at once and two `start` calls on the same event) were validated by
mutation. With `TrainingSessionLock` removed from the service, both fail: the second creation hits the unique
`block_order` key, and both `start` calls succeed.

### Evaluations in an event (T4.4)

- `SessionEventEvaluationContextServiceTest` checks the context rules:
  - a missing header returns 400, and an unknown event returns 404 before authorization;
  - the order is authorize → lock → re-read → state checks;
  - a data entry denial (E5) is not masked by the state checks;
  - the session and the event must both be `IN_PROGRESS`;
  - attendance is checked for every value: only `PRESENT` and `LATE` pass;
  - the player must be convened, and a second evaluation that is not voided returns 409;
  - the three warnings, both present and absent.
- `EvaluationServiceEventTest` checks the event path of `EvaluationService`:
  - only the event's metrics are required (E2, R-1), and a scored result outside them returns 400;
  - non-scored components are accepted;
  - the template and version must be the event's;
  - session and category come from the event;
  - warnings are returned;
  - without an event, the context service is never used.
- `PlayerEvaluationSecurityHttpTest` covers the HTTP boundary of the POST:
  - 401 without a token;
  - 403 for `PLAYER` and for admins without the `COACH` role (R-4);
  - the header and `session_event_id` reach the service;
  - the snake_case response with `warnings`;
  - the standalone POST works without the header (E7).
- `EvaluationEventPostgresIntegrationTest` runs over HTTP against `postgres-test`, with the real security chain and
  the real `TrainingSessionAccessPolicy`. It covers:
  - E1 and E2;
  - the data entry matrix: head coach, staff `ASSISTANT`, `ASSISTANT` outside the staff (E6), `FITNESS_COACH`
    outside the staff, staff with the session `PLANNED` (E5), and a coach of another academy;
  - E3;
  - the event state, academy and template checks;
  - the duplicate and re-evaluation after voiding (E8);
  - the warnings;
  - the concurrent duplicate (E4);
  - the R-11 behavior of the standalone POST, kept as debt.

The concurrent duplicate test was validated by mutation. With `TrainingSessionLock` removed from
`SessionEventEvaluationContextService`, the losing request reaches the unique index `evaluations_event_player_uq`
instead of the service's 409, and the test fails. `EvaluationHttpPostgresIntegrationTest` (E7) is unchanged and still
passes.

### Voiding and reading evaluations (T4.5)

- `EvaluationAccessPolicyTest` checks who can do what:
  - the creator and the current `HEAD_COACH` of the category can void;
  - an admin in scope can only read, and an admin out of scope gets nothing;
  - a coach is never treated as an admin;
  - an unknown user or a missing JWT gets nothing.
- `EvaluationVoidServiceTest` checks voiding:
  - the reason is trimmed, the author comes from the JWT and the time from the clock;
  - a second void is 409 and keeps the first audit;
  - 404 out of scope, 403 with read-only access, 404 for another player's path;
  - an inactive account cannot void, and a blank or overlong reason is 400;
  - an evaluation of a session is locked and re-read before its status is checked, and a standalone one needs no
    lock.
- `PlayerEvaluationSecurityHttpTest` adds:
  - admins can call the read route and `PLAYER` cannot;
  - only `COACH` can void, and a token is required;
  - body validation, including that `voided_by_coach_id` cannot be sent;
  - the audit fields in snake_case, and 409 on a second void.
- `EvaluationEventPostgresIntegrationTest` adds, over HTTP with the real policies:
  - the head coach voids what the assistant recorded, the evidence is kept, a second void is 409, and the player is
    evaluated again (E8);
  - the read matrix: creator, head coach and academy admin get 200; another assistant, the fitness coach, a coach of
    another academy, an admin of another academy and another player's path get 404; an admin who tries to void gets
    403;
  - an event can be cancelled only after all its evaluations are voided, and voiding still works after the event
    and the session are closed.

`EvaluationHttpPostgresIntegrationTest` changed one assertion, as approved in T4.5-D1: a current `HEAD_COACH` of the
category now reads a colleague's evaluation (200 instead of 404). Every other 404 in that test is unchanged.

### Players still to observe (T4.6)

- `SessionEvaluationEventServiceTest` adds:
  - read access is checked before the query, in every event state, and no lock is taken;
  - only `PRESENT` and `LATE` are requested;
  - an empty list, and 404 before authorization out of the academy.
- `SessionEvaluationEventSecurityHttpTest` adds the route to the 401, `PLAYER` 403, 404 and missing-header 400 cases,
  and 200 in snake_case for `SYSTEM_ADMIN`, `ACADEMY_ADMIN` and `COACH`, without `email`.
- `SessionEventPendingPlayersPostgresIntegrationTest`, over HTTP with the real policy:
  - the guide's scenario: 20 convened players, 3 not present (`ABSENT`, `JUSTIFIED`, `PENDING`), 2 evaluated in the
    event and 1 with only a voided evaluation. The result is 15 players in last-name and first-name order, with the
    voided one included. A player evaluated in another event of the session is still pending;
  - nobody is left once everyone is evaluated, and a closed event still answers;
  - the head coach, the fitness coach and the academy admin get 200; a coach or an admin of another academy, and an
    unknown event, get 404;
  - the number of statements is the same for 17 and 2 players, with no lazy fetches (no N+1).

The query was validated by mutation. Counting voided evaluations as observations, or ordering by player id, makes the
scenario test fail.

### Test data factory

`support/TestDataFactory` builds PostgreSQL fixtures: academy, academy category with its season, location, users,
coaches and players, their assignments, and sessions. Every unique column carries a random per-factory suffix, so
tests need no cleanup. `subject(handle)` returns the user's Keycloak id, to use as the JWT subject. The sessions
integration tests use it. `EvaluationHttpPostgresIntegrationTest` keeps its own fixture on purpose, because it is the
regression evidence for evaluations.

Run the whole suite against `postgres-test`. It needs no `@Disabled` and skips no tests. `KeycloakJwtIntegrationTest`
skips itself only when the three Keycloak variables are missing.

```powershell
mvn -o test   # 256 tests at the end of Sprint 3; 281 after T4.2; 371 after T4.3; 411 after T4.4; 433 after T4.5; 453 after T4.6
```

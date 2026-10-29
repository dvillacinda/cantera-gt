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

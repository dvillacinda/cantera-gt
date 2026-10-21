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

`EvaluationHttpPostgresIntegrationTest` runs against the configured PostgreSQL database. Use a disposable,
isolated database with the normal application environment variables set; the application applies Flyway migrations
and the test inserts coach/player fixtures, evaluates a seeded template over MockMvc, verifies persisted evaluation,
result, and observation rows, and checks later retrieval, cross-scope denials, invalid versions, missing required
criteria, foreign criteria, invalid metric values/observations, and missing resources without partial inserts. Its
`jwt()` request postprocessor tests the Spring authorization/controller/service path; the separate Keycloak test
verifies a real token's signature.

```powershell
mvn "-Dtest=CanteragtApplicationTests,EvaluationHttpPostgresIntegrationTest,KeycloakJwtIntegrationTest" test
```

Testcontainers is not configured, and Docker is unavailable in this environment (the Docker engine pipe is
inaccessible). The PostgreSQL integration test uses a disposable local PostgreSQL database instead. A CI job that
provisions an isolated database and Keycloak realm remains future work. The focused MVC and unit command does not
need external services.

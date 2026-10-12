# Security and persistence tests

Run the focused HTTP authorization and Keycloak compensation tests from the repository root:

```powershell
mvn "-Dtest=EvaluationTemplateSecurityHttpTest,EvaluationServiceTest,AcademyAccessServiceTest,UserServiceTest" test
```

`EvaluationTemplateSecurityHttpTest` sends requests through MockMvc, Spring Security's servlet filter chain,
and the evaluation template controller. It covers missing and invalid bearer tokens, the ACADEMY_ADMIN and
COACH roles, and denial of PLAYER. The service is mocked so these tests focus on authentication and method
authorization.

`EvaluationServiceTest` verifies denial across academy scope and rejects stale template versions before persistence.

`UserServiceTest` verifies the Keycloak compensation path when the database insert fails, and verifies the
reverse synchronization path when a database update fails. The AcademyAccessService tests cover unit-level
assignment scope decisions.

PostgreSQL/Testcontainers coverage is not configured. Docker is unavailable in this environment (the Docker
engine pipe is inaccessible), so this change does not introduce a container-backed test suite. The existing
full-context `CanteragtApplicationTests` also requires development Keycloak and database environment settings;
the focused command above avoids external services. CAN-002 still needs a PostgreSQL-backed suite and CI job
before it can be considered closed.

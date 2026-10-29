# CanteraGt

## Local development

Copy `.env.example` to `.env`, configure local credentials and the Keycloak service client, then follow
[docs/dev-environment.md](docs/dev-environment.md) to start Docker Compose and run integration tests against the
isolated, disposable PostgreSQL test service.

## Keycloak user provisioning (MVP)

User accounts created through the Coach and Player endpoints are provisioned in Keycloak first. The API takes the new account's ID from the `Location` header returned by Keycloak and stores it as `users.keycloak_id`. The authenticated caller remains identified separately by the JWT `sub` claim.

Configure `KEYCLOAK_SERVER_URL`, `KEYCLOAK_ISSUER_URL`, `KEYCLOAK_REALM`, `KEYCLOAK_ADMIN_CLIENT_ID`, and `KEYCLOAK_ADMIN_CLIENT_SECRET` in each environment. `KEYCLOAK_ISSUER_URL` must be the realm issuer URL used to validate JWT signatures and issuer claims (for example, `http://localhost:8181/realms/<realm>` in local development); it is intentionally required and has no insecure fallback. The confidential service client must have service accounts enabled and permission to manage users in the target realm. New accounts are created enabled with the `UPDATE_PASSWORD` required action; configure Keycloak SMTP and realm email settings if users should receive password setup emails. CanteraGT does not create or store passwords.

The MVP currently has no creation path for an Academy Admin: the generic user creation endpoint was removed because it did not distinguish or authorize a target type. Academy Admin provisioning needs an explicit application flow. The Coach and Player create endpoints enforce `ACADEMY_ADMIN` and `ACADEMY_ADMIN`/`COACH` authorities respectively. Coach/player membership is resolved through the assignment entities.

## Academy administrator scope

`GET /api/v1/auth/me` returns the authenticated user's active academy assignments in `academies`. Requests to `/api/v1/users/**`, `/api/v1/academy-category/**`, and staff player/coach profile routes by ID must include the selected UUID as `X-Academy-Id`. The API validates this selector against the authenticated subject's active academy assignment on every request; possession of the UUID alone does not grant access. `SYSTEM_ADMIN` can select any existing academy. Coach access to players additionally requires a current assignment to the player's academy category. Out-of-scope player and coach profiles return 404.

Categories and seasons are shared catalogs, not academy-owned records. Academy administrators can read them, while only `SYSTEM_ADMIN` can create, update, change status, or delete catalog entries. Academy-specific category-season associations are created and read within the validated academy context.

# CanteraGt

## Keycloak user provisioning (MVP)

User accounts created through the Coach and Player endpoints are provisioned in Keycloak first. The API takes the new account's ID from the `Location` header returned by Keycloak and stores it as `users.keycloak_id`. The authenticated caller remains identified separately by the JWT `sub` claim.

Configure `KEYCLOAK_SERVER_URL`, `KEYCLOAK_REALM`, `KEYCLOAK_ADMIN_CLIENT_ID`, and `KEYCLOAK_ADMIN_CLIENT_SECRET` in each environment. The confidential service client must have service accounts enabled and permission to manage users in the target realm. New accounts are created enabled with the `UPDATE_PASSWORD` required action; configure Keycloak SMTP and realm email settings if users should receive password setup emails. CanteraGT does not create or store passwords.

The MVP currently has no creation path for an Academy Admin: the generic user creation endpoint was removed because it did not distinguish or authorize a target type. Academy Admin provisioning needs an explicit application flow. The Coach and Player create endpoints enforce `ACADEMY_ADMIN` and `ACADEMY_ADMIN`/`COACH` authorities respectively. Coach/player academy membership is not modeled yet, so academy administrators cannot manage those users through the generic user endpoints until that relation is implemented.

## Academy administrator scope

`GET /api/v1/auth/me` returns the authenticated user's active academy assignments in `academies`. Requests to `/api/v1/users/**` and `/api/v1/academy-category/**` must include the selected UUID as `X-Academy-Id`. The API validates this selector against the authenticated Keycloak subject's active `academy_admins` assignment on every request; possession of the UUID alone does not grant access. `SYSTEM_ADMIN` can select any existing academy. Player/coach resource endpoints still need tenant checks once their academy assignment relation is implemented.

Categories and seasons are shared catalogs, not academy-owned records. Academy administrators can read them, while only `SYSTEM_ADMIN` can create, update, change status, or delete catalog entries. Academy-specific category-season associations are created and read within the validated academy context.

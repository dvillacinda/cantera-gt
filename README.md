# CanteraGt

## Keycloak user provisioning (MVP)

User accounts created through the Coach and Player endpoints are provisioned in Keycloak first. The API takes the new account's ID from the `Location` header returned by Keycloak and stores it as `users.keycloak_id`. The authenticated caller remains identified separately by the JWT `sub` claim.

Configure `KEYCLOAK_SERVER_URL`, `KEYCLOAK_REALM`, `KEYCLOAK_ADMIN_CLIENT_ID`, and `KEYCLOAK_ADMIN_CLIENT_SECRET` in each environment. The confidential service client must have service accounts enabled and permission to manage users in the target realm. New accounts are created enabled with the `UPDATE_PASSWORD` required action; configure Keycloak SMTP and realm email settings if users should receive password setup emails. CanteraGT does not create or store passwords.

The MVP currently has no creation path for an Academy Admin: the generic user creation endpoint was removed because it did not distinguish or authorize a target type. Academy Admin provisioning needs an explicit application flow. The Coach and Player create endpoints enforce `ACADEMY_ADMIN` and `ACADEMY_ADMIN`/`COACH` authorities respectively. Academy membership and tenant scoping are not modeled on user accounts yet.

# ADR-0003: Time zone and Clock

- **Status:** Accepted, 2026-11-09 (DP-1.3, option B; the `ClockConfig` change was approved with the DP-1
  defaults)
- **Implementation:** `ClockConfig`, `app.time-zone`, `TrainingSessionMapper`, `SessionPlayerMapper`,
  `SessionStaffMapper`

## Context

`ClockConfig` returned `Clock.systemDefaultZone()`, so "today" depended on the server's time zone. For example, an
assignment that ends today could be treated as expired on a UTC server after 18:00 in Guatemala.

Sessions are local events. A 16:00 practice is 16:00 at the academy, wherever the server runs. The database columns
are `TIMESTAMP` without a time zone.

## Decision

1. **One configured zone.**
   - `app.time-zone` defaults to `America/Guatemala` (UTC−6, no daylight saving time).
   - `ClockConfig` exposes `Clock.system(ZoneId.of(app.time-zone))`.
   - Every "today" or "now" calculation goes through the injected `Clock`: assignment validity, backdate windows,
     `started_at` and `ended_at`.
2. **Storage.** `session_date`, `started_at`, `ended_at`, `created_at` and `updated_at` are stored as academy local
   time (`LocalDateTime`).
3. **API.**
   - The API accepts any ISO-8601 offset and converts it to academy local time
     (`atZoneSameInstant(zone).toLocalDateTime()`).
   - It always returns ISO-8601 **with** the academy offset (`-06:00`).
4. **Date window for `session_date`.** `today − app.sessions.max-backdate-days` (7) ≤ date ≤
   `today + app.sessions.max-future-days` (365), both measured in the academy zone.
5. **Tests.** Unit tests use `Clock.fixed(…)` in Guatemala, including instants after 18:00 local, which are already
   the next day in UTC. Integration tests use the application `Clock`.

## Consequences

- Behavior no longer depends on the host time zone, and tests can control "today" precisely.
- This design supports one zone per deployment. Academies in several time zones would need a per-academy zone
  column and a migration. That is out of scope for the MVP.
- Clients must send an offset. A naive date-time without one is rejected by Jackson with 400.

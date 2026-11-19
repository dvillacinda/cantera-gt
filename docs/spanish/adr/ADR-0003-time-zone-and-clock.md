# ADR-0003: Zona horaria y Clock

- **Estado:** Aceptada, 2026-11-09 (DP-1.3, opción B; el cambio de `ClockConfig` se aprobó con los valores predeterminados DP-1)
- **Implementación:** `ClockConfig`, `app.time-zone`, `TrainingSessionMapper`, `SessionPlayerMapper`, `SessionStaffMapper`

## Contexto

`ClockConfig` devolvía `Clock.systemDefaultZone()`, por lo que «hoy» dependía de la zona horaria del servidor. Por ejemplo, una asignación que termina hoy podía tratarse como vencida en un servidor UTC después de las 18:00 en Guatemala.

Las sesiones son eventos locales. Un entrenamiento a las 16:00 es a las 16:00 en la academia, independientemente de dónde se ejecute el servidor. Las columnas de la base de datos son `TIMESTAMP` sin zona horaria.

## Decisión

1. **Una zona configurada.**
   - `app.time-zone` tiene como valor predeterminado `America/Guatemala` (UTC−6, sin horario de verano).
   - `ClockConfig` expone `Clock.system(ZoneId.of(app.time-zone))`.
   - Todos los cálculos de «hoy» o «ahora» usan el `Clock` inyectado: vigencia de asignaciones, intervalos de fechas anteriores, `started_at` y `ended_at`.
2. **Almacenamiento.** `session_date`, `started_at`, `ended_at`, `created_at` y `updated_at` se almacenan como hora local de la academia (`LocalDateTime`).
3. **API.**
   - La API acepta cualquier desplazamiento ISO-8601 y lo convierte a la hora local de la academia (`atZoneSameInstant(zone).toLocalDateTime()`).
   - Siempre devuelve ISO-8601 **con** el desplazamiento de la academia (`-06:00`).
4. **Intervalo de fechas para `session_date`.** `today − app.sessions.max-backdate-days` (7) ≤ fecha ≤ `today + app.sessions.max-future-days` (365); ambos límites se miden en la zona de la academia.
5. **Pruebas.** Las pruebas unitarias usan `Clock.fixed(…)` en Guatemala, incluso para instantes posteriores a las 18:00 locales, que ya corresponden al día siguiente en UTC. Las pruebas de integración usan el `Clock` de la aplicación.

## Consecuencias

- El comportamiento ya no depende de la zona horaria del equipo anfitrión, y las pruebas pueden controlar «hoy» con precisión.
- Este diseño admite una zona por despliegue. Las academias de varias zonas horarias necesitarían una columna de zona por academia y una migración. Esto queda fuera del MVP.
- Los clientes deben enviar un desplazamiento. Jackson rechaza con 400 las fechas y horas sin uno.

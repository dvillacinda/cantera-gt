# API de sesiones de entrenamiento (Sprint 3)

Esta API cubre las sesiones de entrenamiento: su ciclo de vida, la lista de participantes y la asistencia, y los entrenadores que participan.

Todas las rutas requieren un token bearer de Keycloak y el encabezado `X-Academy-Id`. Todos los nombres de propiedades JSON usan `snake_case`. Las fechas y horas usan ISO-8601 con desplazamiento, y el servidor las almacena y devuelve en la hora local de la academia, `America/Guatemala` (UTC−6). Consulte [ADR-0003](adr/ADR-0003-time-zone-and-clock.md).

Las respuestas correctas usan el sobre común `{ "success", "status", "message", "data" }`. Las respuestas de error usan `{ "status", "error", "message", "fieldErrors"? }`. Las convenciones de rutas se describen en [ADR-0001](adr/ADR-0001-rest-conventions.md).

## Quién puede hacer qué

Las reglas completas están en [ADR-0002](adr/ADR-0002-session-permissions.md). En resumen:

| Solicitante | Consultar sesiones, lista y personal | Administrar (crear, editar, ciclo de vida, lista, asistencia y personal) |
|---|---|---|
| `SYSTEM_ADMIN` | sí (academia existente) | sí |
| `ACADEMY_ADMIN` de la academia seleccionada | sí | sí |
| `COACH` con una asignación vigente de `HEAD_COACH` en la categoría | sí | sí |
| `COACH` con otra asignación vigente en la categoría (`ASSISTANT`, `FITNESS_COACH`) | sí | no: **403** |
| `COACH` sin asignación vigente en la categoría, o de otra academia | no: **404** | no: **404** |
| `PLAYER` | no: **403** | no: **403** |

Si falta `X-Academy-Id` o tiene un formato no válido, se devuelve 400. Un recurso fuera de la academia seleccionada devuelve 404, nunca 403, para que la API no revele que el recurso existe.

## Sesiones

| Método | Ruta | Respuesta correcta | Errores principales |
|---|---|---|---|
| POST | `/api/v1/academy-categories/{academyCategoryId}/training-sessions` | 201 | 400 validación o intervalo de fechas · 403 · 404 · 409 categoría no `ACTIVE` |
| GET | `/api/v1/academy-categories/{academyCategoryId}/training-sessions?status=&from=&to=&page=&size=&sort=` | 200 página | 400 `size>50`, `from>to` o `sort` no admitido · 404 |
| GET | `/api/v1/training-sessions/{sessionId}` | 200 | 404 |
| PATCH | `/api/v1/training-sessions/{sessionId}` | 200 | 400 · 403 · 404 · 409 no está `PLANNED` |
| POST | `/api/v1/training-sessions/{sessionId}/start` | 200 | 409 transición no válida o cambio concurrente |
| POST | `/api/v1/training-sessions/{sessionId}/complete` | 200 | 409 no está `IN_PROGRESS` o hay jugadores `PENDING` (se enumeran sus ID) |
| POST | `/api/v1/training-sessions/{sessionId}/cancel` | 200 | 409 ya está `COMPLETED` o `CANCELLED` |

**Cuerpo de creación**

```json
{ "name": "Entrenamiento técnico martes", "session_date": "2026-11-24T16:00:00-06:00",
  "location_id": null, "notes": "Foco: pase y recepción" }
```

- **`name`:** de 1 a 150 caracteres. **`notes`:** hasta 2000 caracteres.
- **`session_date`:** no puede estar más de `app.sessions.max-backdate-days` (valor predeterminado: 7) en el pasado ni más de `app.sessions.max-future-days` (valor predeterminado: 365) en el futuro.
- **`location_id`:** opcional. Si se envía, debe ser una ubicación `ACTIVE` de la misma academia.
- **Creador:** `created_by_coach_id` siempre se obtiene del JWT y nunca se lee del cuerpo. El creador también se registra como miembro del personal `LEAD` de la sesión.

**PATCH** acepta cualquier subconjunto de los campos de creación, solo mientras la sesión está `PLANNED`. Un campo con valor `null` significa «sin cambios», por lo que PATCH no puede borrar `location_id` ni `notes`.

**Ciclo de vida**

```text
PLANNED ──start──► IN_PROGRESS ──complete──► COMPLETED
   │                    │
   └──────cancel────────┴──────────────────► CANCELLED
```

- `start` establece `started_at`.
- `complete` establece `ended_at` y requiere que ningún jugador siga en `PENDING`.
- `cancel` establece `ended_at` solo si la sesión había comenzado.
- `COMPLETED` y `CANCELLED` son estados finales. Cualquier otra transición devuelve 409.
- **Concurrencia:** cada sesión tiene un `version`. Si dos solicitudes modifican la misma sesión al mismo tiempo, una gana y la otra recibe 409.

**Listado**

- **Paginación:** `page` empieza en 0 y `size` tiene un valor predeterminado de 20 y un máximo de 50.
- **Orden:** el valor predeterminado es `session_date,desc`. Los campos de orden permitidos son `session_date`, `status`, `name` y `created_at`.
- **Filtros:** `status`, además de `from` y `to` (ISO-8601 con desplazamiento).
- **Ámbito:** los resultados siempre se limitan a la categoría de academia de la ruta, directamente en la consulta.

## Lista de participantes y asistencia

| Método | Ruta | Respuesta correcta | Errores principales |
|---|---|---|---|
| GET | `/api/v1/training-sessions/{sessionId}/players` | 200 | 404 |
| POST | `/api/v1/training-sessions/{sessionId}/players` | 201 con los jugadores agregados | 400 lote no válido · 409 duplicado o sesión cerrada |
| DELETE | `/api/v1/training-sessions/{sessionId}/players/{playerId}` | 204 | 404 no convocado · 409 sesión cerrada o asistencia ya registrada |
| PATCH | `/api/v1/training-sessions/{sessionId}/players/{playerId}/attendance` | 200 | 400 `PENDING` o valor desconocido · 409 sesión no está `IN_PROGRESS` |

**Convocatoria de jugadores.** Envíe exactamente una de estas dos formas:
- `{"player_ids": [ … ]}`, con entre 1 y 60 ID;
- `{"all_active": true}`, que convoca a todos los jugadores con una asignación vigente en la categoría que todavía no estén en la sesión. Se puede repetir sin riesgo.

Cada jugador debe tener una asignación `ACTIVE` vigente en la categoría de la sesión, verificada con la fecha actual de Guatemala. Se rechazan las asignaciones vencidas, futuras, transferidas y de otras categorías.

**Rechazo de lotes.** El lote es atómico: si algún ID no es válido, no se agrega ningún jugador. La respuesta enumera cada ID rechazado con su motivo en `fieldErrors`:

```json
{ "status": 400, "error": "Bad Request",
  "message": "Batch rejected: no player was added. See rejected player ids",
  "fieldErrors": { "0193…": "NO_CURRENT_ASSIGNMENT_IN_CATEGORY" } }
```

| Motivo | Estado |
|---|---|
| `NO_CURRENT_ASSIGNMENT_IN_CATEGORY` | 400 |
| `DUPLICATED_IN_REQUEST` | 400 |
| `ALREADY_IN_SESSION` | 409, solo cuando es el único problema |

**Cambios a la lista y asistencia.**
- Se pueden agregar o retirar jugadores mientras la sesión está `PLANNED` o `IN_PROGRESS`.
- Un jugador solo se puede retirar mientras su asistencia siga en `PENDING`.
- Los jugadores nuevos comienzan en `PENDING`.
- La asistencia (`PRESENT`, `ABSENT`, `JUSTIFIED`, `LATE`) solo se puede registrar mientras la sesión está `IN_PROGRESS` y no se puede volver a establecer en `PENDING`.

**Campos de la entrada de la lista:** `session_player_id`, `player_id`, `first_name`, `last_name`, `principal_position`, `attendance_status` y `updated_at`. La respuesta nunca incluye correo electrónico, nombre de usuario ni ID de Keycloak.

## Personal de la sesión

| Método | Ruta | Respuesta correcta | Errores principales |
|---|---|---|---|
| GET | `/api/v1/training-sessions/{sessionId}/staff` | 200, primero `LEAD` | 404 |
| POST | `/api/v1/training-sessions/{sessionId}/staff` | 201 | 400 entrenador sin asignación vigente en la categoría · 409 entrenador duplicado, segundo `LEAD` o sesión cerrada |
| DELETE | `/api/v1/training-sessions/{sessionId}/staff/{coachId}` | 204 | 404 no es miembro del personal · 409 sesión cerrada o `LEAD` mientras está `IN_PROGRESS` |

El cuerpo POST es `{"coach_id": "…", "participation_type": "LEAD" | "ASSISTANT" | "SUPPORT"}` y una sesión solo puede tener un `LEAD`.

El tipo de participación es el rol del entrenador **en esta sesión**. Es independiente del rol permanente del entrenador en la categoría (`CoachRoleCode`: `HEAD_COACH`, `ASSISTANT`, `FITNESS_COACH`). La pertenencia al personal permitirá que los asistentes ingresen datos en Sprint 4. Por ahora no concede nada.

**Campos de la entrada de personal:** `session_staff_id`, `coach_id`, `first_name`, `last_name`, `participation_type` y `created_at`.

## Garantías de integridad

Cada cambio en la lista, la asistencia o el personal bloquea la fila de la sesión (`SELECT … FOR UPDATE`) e incrementa su versión (`TrainingSessionLock`). Esto tiene tres efectos:
- los cambios concurrentes a la misma sesión se ejecutan uno tras otro en lugar de fallar;
- una operación `complete` que coincida con un cambio a la lista devuelve 409, por lo que una sesión nunca se puede cerrar si tiene un jugador `PENDING`;
- la regla de un solo `LEAD` se mantiene con concurrencia, aunque ninguna restricción de la base de datos la imponga.

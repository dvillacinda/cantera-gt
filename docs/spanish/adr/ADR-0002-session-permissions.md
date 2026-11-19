# ADR-0002: Permisos de sesiones de entrenamiento

- **Estado:** Aceptada, 2026-11-09 (DP-1.1 / D-03). Ampliada el 2026-11-18 con los puntos de decisión T3.5 y T3.6.
- **Implementación:** `TrainingSessionAccessPolicy`, `TrainingSessionLock`, `SessionRosterService`, `SessionStaffService`.

## Contexto

En una misma categoría de academia trabajan varios tipos de entrenador: el entrenador principal, asistentes y preparadores físicos. Los administradores de academia y de sistema también necesitan actuar. Era necesario decidir quién puede crear y dirigir una sesión, quién solo puede consultarla y cómo se relaciona la participación en una sesión con el rol permanente del entrenador.

## Decisión

1. **Dos capas de roles.** Cada capa tiene su propio enum.

   | Capa | Enum | Valores | Se almacena en | Ámbito |
   |---|---|---|---|---|
   | Rol de categoría | `CoachRoleCode` | `HEAD_COACH`, `ASSISTANT`, `FITNESS_COACH` | `coach_assignments` | Permanente en la categoría; debe coincidir con V6 |
   | Rol de sesión | `SessionParticipationType` | `LEAD`, `ASSISTANT`, `SUPPORT` | `session_staff` | Por sesión |

2. **Quién puede administrar.** Administrar significa crear, editar, iniciar, completar y cancelar sesiones, así como gestionar la lista, la asistencia y el personal. Se permite a:
   - `SYSTEM_ADMIN`;
   - `ACADEMY_ADMIN` de la academia seleccionada;
   - un `COACH` con una asignación **vigente** de `HEAD_COACH` (estado `ACTIVE`, `start_date ≤ today ≤ end_date`, evaluada con el `Clock` de Guatemala) en la categoría de la sesión.
3. **Acceso de solo lectura.** Otros entrenadores con una asignación vigente en la categoría (`ASSISTANT`, `FITNESS_COACH`) pueden consultar sesiones, listas y personal. Reciben **403** en las rutas de administración.
4. **Sin acceso.**
   - Un entrenador sin asignación vigente en la categoría o de otra academia recibe **404**.
   - `PLAYER` recibe **403** en todas las rutas del personal.
5. **Pertenencia al personal.**
   - El creador de la sesión se registra como `LEAD` en la misma transacción de creación (T3.6-B).
   - Una sesión puede tener como máximo un `LEAD` (T3.6-A). La aplicación lo impone bloqueando la fila de la sesión; no hay una restricción de base de datos.
   - No se puede retirar a un `LEAD` mientras la sesión está `IN_PROGRESS`, pero sí se puede reemplazar mientras está `PLANNED` (T3.6-C).
   - Un miembro del personal debe tener una asignación vigente en la categoría.
   - El ingreso de datos por parte del personal (`requireDataEntryAccess`: personal de la sesión mientras está `IN_PROGRESS`) está definido, pero no se usará hasta Sprint 4.
6. **Reglas de la lista.**
   - Los jugadores necesitan una asignación vigente en la categoría.
   - La convocatoria por lotes es atómica.
   - Solo se puede retirar a un jugador mientras su asistencia esté en `PENDING` (T3.5-B).
   - Está disponible el atajo `all_active` (T3.5-A).
7. **Orden de las comprobaciones.**
   - Primero se carga el recurso dentro del ámbito de la academia seleccionada (fuera de ámbito → 404).
   - Después se autoriza (403).
   - Luego se bloquea la fila de la sesión y se comprueban las reglas de estado (409).

## Consecuencias

- Los asistentes no pueden dirigir sesiones en Sprint 3. Si el producto requiere delegación, primero debe cambiarse aquí la matriz.
- Las sesiones creadas antes de T3.6 no tienen una fila `LEAD`. No hay datos de producción, así que no hace falta una migración retroactiva.
- `CoachRoleCode` debe mantenerse sincronizado con los datos iniciales de `coach_roles` y su restricción CHECK. `CoachRoleCodeSchemaPostgresIntegrationTest` lo verifica. El valor anterior `ASSISTANT_COACH` nunca coincidió con la base de datos.

# ADR-0001: Convenciones REST para módulos nuevos

- **Estado:** Aceptada, 2026-11-09 (DP-1.4 / D-10; valores predeterminados aprobados)
- **Aplicación:** módulos creados a partir de Sprint 3 (primero, sesiones de entrenamiento). Las rutas heredadas se mantienen sin cambios hasta la limpieza de Sprint 7.

## Contexto

Los controladores antiguos usan rutas con verbos (`/get-player-by-id/{id}`, `/create-…`) y sus reglas de ámbito varían. El nuevo módulo de sesiones necesitaba una convención predecible antes de que el cliente Flutter generara código a partir de OpenAPI.

## Decisión

1. **Recursos con sustantivos en plural.** Utilice verbos HTTP, no verbos en la ruta. Un recurso se anida bajo su propietario solo cuando este define su ámbito.
   - `POST /api/v1/academy-categories/{id}/training-sessions` crea una sesión.
   - `GET /api/v1/training-sessions/{id}` consulta una sesión.
   - Las transiciones del ciclo de vida son acciones de subrecurso: `POST /training-sessions/{id}/start`, `/complete`, `/cancel`.
2. **Contexto de academia.** Todas las rutas del personal requieren el encabezado `X-Academy-Id`, que el servidor valida en cada solicitud con respecto a la academia del recurso.
   - Si falta el encabezado o tiene un formato no válido: **400**.
   - Si el recurso está fuera de la academia seleccionada o de la asignación del solicitante a la categoría: **404**, nunca 403.
3. **Códigos de estado.**

   | Código | Cuándo |
   |---|---|
   | 201 | creación |
   | 200 | consulta, actualización, transición |
   | 204 | eliminación |
   | 400 | validación o lote no válido |
   | 401 | token ausente o no válido |
   | 403 | rol insuficiente dentro del ámbito del solicitante |
   | 404 | fuera de ámbito o no encontrado |
   | 409 | conflicto de estado o concurrencia, o duplicado |

4. **Cuerpos.**
   - JSON usa `snake_case` (`@JsonNaming(SnakeCaseStrategy)` en los DTO de tipo record, incluidos los anidados). La API nunca expone entidades JPA. Las claves de validación en `field_errors` usan los mismos nombres (`player_ids`, `results[0].raw_value`).
   - La API usa Jackson 3 (`tools.jackson`), pero swagger-core sigue usando Jackson 2 para inspeccionar los modelos. `Jackson3NamingIntrospector` (registrado en `OpenApiConfig`) traduce `@JsonNaming`, de modo que Swagger muestra los nombres reales. `ApiNamingConventionTest` verifica el cuerpo de cada controlador y su esquema OpenAPI.
   - Los cuerpos son estrictos (`spring.jackson.deserialization.fail-on-unknown-properties: true`): un campo desconocido, como `locationId` en camelCase o el campo controlado por el servidor `created_by_coach_id`, devuelve 400 `Malformed request` con `field_errors: { "locationId": "Unknown field; use 'location_id' (snake_case)" }` en lugar de ignorarse.
   - Las respuestas correctas usan el sobre existente `ApiResponse`; los errores usan `ErrorResponse`.
   - Cuando se rechaza un lote, los ID rechazados y sus motivos se incluyen en `field_errors`.
5. **Paginación.** Utilice Spring `Pageable`: `page` empieza en 0, `size` tiene un valor predeterminado de 20 y un máximo de 50, y solo se permiten campos de orden incluidos en una lista autorizada. Cada lista tiene su propio orden predeterminado (las sesiones usan `session_date,desc`). Las listas de participantes y de personal no se paginan, porque una categoría tiene aproximadamente entre 20 y 30 jugadores.
6. **Campos controlados por el servidor.**
   - El servidor nunca acepta del cuerpo campos de identidad o auditoría (`created_by_coach_id`, `version`, marcas de tiempo).
   - Se devuelve la `version` optimista para que los clientes puedan mostrarla, pero el servidor resuelve los conflictos y devuelve 409.

## Consecuencias

- Las rutas nuevas son predecibles y su salida OpenAPI es adecuada para generadores. La prueba E2E de sesiones comprueba que las 14 rutas de sesiones se publiquen con un resumen.
- Usar 404 para recursos fuera de ámbito oculta si un recurso existe. Los clientes no deben interpretar un 404 como «eliminado».
- Las rutas heredadas con verbos siguen existiendo. Su eliminación o sustitución por alias se aplaza hasta Sprint 7 (D-10), antes de congelar la API v1.

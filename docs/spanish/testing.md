# Pruebas de seguridad y persistencia

Ejecute desde la raíz del repositorio las pruebas específicas de autorización HTTP y compensación de Keycloak:

```powershell
mvn "-Dtest=EvaluationTemplateSecurityHttpTest,PlayerCoachSecurityHttpTest,EvaluationServiceTest,AcademyAccessServiceTest,UserServiceTest" test
```

`EvaluationTemplateSecurityHttpTest` envía solicitudes mediante MockMvc, la cadena de filtros de servlet de Spring Security y el controlador de plantillas de evaluación. Cubre la ausencia de tokens bearer y los tokens no válidos, los roles `ACADEMY_ADMIN` y `COACH`, y la denegación de `PLAYER`. El servicio está simulado, por lo que estas pruebas se centran en la autenticación y la autorización por método.

`PlayerCoachSecurityHttpTest` cubre la autenticación cuando falta el token, la denegación por rol, la propagación del encabezado de academia y el manejo uniforme de recursos inaccesibles de jugadores y entrenadores como no encontrados. Estas pruebas de porción MVC simulan sus servicios, por lo que ejercitan el límite HTTP; las decisiones sobre asignaciones se cubren con `AcademyAccessServiceTest` y la prueba de integración HTTP con PostgreSQL. El comportamiento ante tokens bearer no válidos se cubre en `EvaluationTemplateSecurityHttpTest`.

`EvaluationServiceTest` verifica la denegación fuera del ámbito de la academia y rechaza versiones obsoletas de plantillas antes de la persistencia.

`UserServiceTest` verifica la ruta de compensación de Keycloak cuando falla la inserción en la base de datos, así como la ruta de sincronización inversa cuando falla una actualización de la base de datos. Las pruebas de `AcademyAccessService` cubren a nivel unitario el ámbito de las asignaciones vigentes de jugadores y entrenadores, la coincidencia de categoría, el ámbito del administrador de academia y el rechazo de academias inexistentes para `SYSTEM_ADMIN`.

`KeycloakJwtIntegrationTest` obtiene un token de credenciales de cliente del realm configurado de Keycloak y valida su firma y emisor con el `JwtDecoder` y el convertidor de roles de producción de Spring. Se ejecuta cuando están presentes `KEYCLOAK_ISSUER_URL`, `KEYCLOAK_ADMIN_CLIENT_ID` y `KEYCLOAK_ADMIN_CLIENT_SECRET`; de lo contrario, JUnit omite únicamente esa prueba de integración externa. No crea usuarios ni modifica el estado del realm.

`EvaluationHttpPostgresIntegrationTest` se ejecuta contra la base de datos PostgreSQL configurada. Utilice el servicio de Compose aislado y desechable `postgres-test`, descrito en [dev-environment.md](dev-environment.md); la aplicación aplica las migraciones de Flyway y la prueba inserta datos de entrenadores y jugadores, evalúa una plantilla precargada mediante MockMvc, verifica las filas persistidas de evaluación, resultado y observación, y comprueba la recuperación posterior, las denegaciones entre ámbitos, las versiones no válidas, los criterios requeridos faltantes, los criterios ajenos, los valores de métricas y observaciones no válidos, y los recursos inexistentes sin inserciones parciales. Su procesador de solicitudes `jwt()` prueba la ruta de autorización/controlador/servicio de Spring; la prueba independiente de Keycloak verifica la firma de un token real.

La integración HTTP con PostgreSQL también verifica las asignaciones futuras, inactivas y vencidas de entrenadores, el aislamiento por categoría, las consultas de evaluaciones limitadas a su creador (en la misma academia y entre academias) y las respuestas 404 genéricas sin datos de evaluación. Comprueba que el criterio `PASE_RECEPCION` `OPTIONAL` se persiste y devuelve tanto LEFT como RIGHT. La consulta del catálogo oficial confirma que no hay criterios `REQUIRED`, por lo que no es posible probar el rechazo de pares bilaterales sin una regla de negocio. También verifica que `/api/v1/players/me` resuelve únicamente el perfil del sujeto del JWT y omite los ID internos y los campos administrativos, y comprueba la denegación a administradores de academia para mutaciones globales de entrenadores/usuarios, además de las actualizaciones de asignaciones limitadas a la academia.

```powershell
docker compose --profile test up -d postgres-test
```

Cargue `.env` en el proceso de Maven y, luego, reemplace las variables de la base de datos de la aplicación para que la prueba de integración use el puerto 5433 y `cantera_gt_test`:

```powershell
Get-Content .env | ForEach-Object {
    if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$' -and $_ -notmatch '^\s*#') {
        Set-Item "Env:$($matches[1])" $matches[2].Trim('"', "'")
    }
}
$env:BACKEND_URL = "localhost:5433"
$env:POSTGRES_DB = "cantera_gt_test"
$env:POSTGRES_USER = "cantera_gt_test"
$env:POSTGRES_PASSWORD = "cantera_gt_test"
mvn "-Dtest=CanteragtApplicationTests,EvaluationHttpPostgresIntegrationTest,KeycloakJwtIntegrationTest" test
```

`java-dotenv` solo carga `.env` desde `CanteragtApplication.main()`; no expone esos valores a las pruebas de Maven. El comando anterior los hace disponibles para el proceso de pruebas sin cambiar el código de la aplicación. La prueba de Keycloak usa el realm y el cliente confidencial configurados, y se omite si faltan sus tres variables de entorno requeridas. El comando específico para MVC y pruebas unitarias no necesita servicios externos.

## Sesiones de entrenamiento (Sprint 3)

`TrainingSessionServiceTest` cubre las reglas de negocio de sesiones con un `Clock` fijo. `lifecycleTransitionTable` es una tabla parametrizada que prueba cada estado (`PLANNED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`) con cada acción (iniciar, completar, cancelar, editar). Comprueba que cada transición no válida devuelve 409 y nunca escribe en la base de datos. `TrainingSessionRequestValidationTest` comprueba la validación de Bean Validation y el uso de nombres `snake_case`. También comprueba que un mapper estricto rechace `created_by_coach_id` y los campos en camelCase. `ApiNamingConventionTest` verifica que el cuerpo de cada controlador (requests, responses y tipos anidados) esté en `snake_case` y que su esquema OpenAPI use los mismos nombres; no necesita servicios.

`TrainingSessionSecurityHttpTest` es una prueba de porción MVC con un servicio simulado, por lo que solo cubre la capa HTTP. Para las cinco rutas de administración comprueba:
- 401 sin token y 403 para `PLAYER` antes de que se ejecute el servicio;
- 403 para un entrenador que no es `HEAD_COACH` y 404 cuando el encabezado `X-Academy-Id` indica otra academia; ambas respuestas usan el cuerpo de error estándar;
- 400 cuando falta el encabezado o no es un UUID.

Las decisiones de acceso propiamente dichas se cubren con `TrainingSessionAccessPolicyTest`.

`TrainingSessionSchemaPostgresIntegrationTest` comprueba las restricciones de V8, la unicidad y la búsqueda del repositorio. `TrainingSessionServicePostgresIntegrationTest` ejecuta el servicio contra PostgreSQL y comprueba dos aspectos:
- **Inicio concurrente:** dos hilos inician la misma sesión después de haber cargado ambos la versión 0. Una solicitud gana y la otra recibe `ConflictException` (HTTP 409).
- **Sin N+1 en el listado:** un listado ejecuta como máximo 2 sentencias SQL (página y conteo), sin consultas de entidades por fila, medido con `Statistics` de Hibernate.

Ambas pruebas de PostgreSQL deben ejecutarse contra `postgres-test`, usando las mismas sustituciones de variables de entorno que la prueba de integración de evaluaciones anterior.

```powershell
mvn "-Dtest=TrainingSession*Test" test
```

### Lista de participantes y asistencia de la sesión

`SessionRosterServiceTest` usa un `Clock` fijo con la hora local de Guatemala. Cubre estas reglas:
- solo se puede convocar a jugadores con una asignación vigente en la misma categoría;
- se rechaza el lote completo y cada ID rechazado recibe un motivo;
- un jugador que ya está en la sesión genera 409, nunca 500;
- el atajo `all_active`;
- los cambios a la lista solo se permiten mientras la sesión está `PLANNED` o `IN_PROGRESS`;
- un jugador solo se puede retirar mientras su asistencia siga en `PENDING`;
- la asistencia solo se puede registrar mientras la sesión está `IN_PROGRESS`.

También comprueba que se bloquee la fila de la sesión antes de cualquier verificación de la lista.

`SessionRosterSecurityHttpTest` cubre el límite HTTP:
- 401 sin token, 403 para `PLAYER` y para un entrenador que no es `HEAD_COACH`, y 404 para otra academia;
- los lotes de más de 60 se rechazan con 400 antes de que se ejecute el servicio;
- el cuerpo de error de un lote rechazado, que enumera los ID en `field_errors` (ID → motivo);
- que el cuerpo de la lista no contenga correo electrónico ni ID de Keycloak.

`SessionRosterPostgresIntegrationTest` se ejecuta contra `postgres-test` con fechas reales. Comprueba que se rechacen las asignaciones vencidas, futuras, transferidas y de otras categorías. También demuestra tres aspectos en la base de datos:
- **Reversión del lote completo:** tras rechazar un lote, hay 0 filas y la versión de la sesión no cambia.
- **Duplicados:** agregar un jugador dos veces devuelve 409.
- **No se completa con jugadores `PENDING`:** completar con un jugador `PENDING` devuelve 409 y lo enumera. Si se está agregando un jugador de forma concurrente, `complete` falla con 409 en lugar de cerrar la sesión. Esta es la condición de carrera que protegen el bloqueo de la fila de la sesión y el incremento de versión.

### Personal de la sesión

`SessionStaffServiceTest` cubre las reglas del personal con un `Clock` fijo:
- un entrenador necesita una asignación vigente en la categoría de la sesión;
- una sesión solo puede tener un `LEAD`, y la fila de la sesión se bloquea antes de esa comprobación;
- un entrenador duplicado devuelve 409, nunca 500;
- los cambios de personal solo se permiten mientras la sesión está `PLANNED` o `IN_PROGRESS`;
- no se puede retirar un `LEAD` mientras la sesión está `IN_PROGRESS`, pero sí mientras está `PLANNED`.

`TrainingSessionServiceTest` comprueba que el creador de una sesión se registre como `LEAD`.

`SessionStaffSecurityHttpTest` cubre el límite HTTP: 401, 403 para `PLAYER` y para un entrenador que no es `HEAD_COACH`, 404 para otra academia, validación del cuerpo y que la respuesta no contenga correo electrónico ni ID de Keycloak.

`SessionStaffPostgresIntegrationTest` se ejecuta contra `postgres-test` y comprueba:
- que el creador se registre como `LEAD` al crear la sesión;
- que se rechacen las asignaciones de entrenadores vencidas, finalizadas, futuras y de otras categorías;
- que un entrenador duplicado y un segundo `LEAD` devuelvan 409;
- que dos solicitudes concurrentes para `LEAD` dejen exactamente un `LEAD`;
- las reglas para retirar al `LEAD`;
- que `session_staff` tenga `created_at` pero no `updated_at`.

Ninguna restricción de la base de datos impone que haya un solo `LEAD`. La prueba concurrente demuestra que el bloqueo de la fila de la sesión garantiza esa regla.

`CoachRoleCodeSchemaPostgresIntegrationTest` comprueba que el enum `CoachRoleCode` coincida con los datos iniciales de `coach_roles` y la restricción `coach_roles_code_check` de V6. También carga cada rol mediante JPA. Esta prueba existe porque el enum indicaba `ASSISTANT_COACH` mientras la base de datos guardaba `ASSISTANT`, por lo que no se podían mapear las filas precargadas. `TrainingSessionLockTest` cubre el bloqueo compartido de la fila de la sesión que usan los servicios de lista y personal.

### Escenario de sesiones de extremo a extremo

`TrainingSessionLifecyclePostgresIntegrationTest` ejecuta el escenario de Sprint 3 mediante HTTP contra `postgres-test`. Usa la cadena **real** de seguridad y `TrainingSessionAccessPolicy`: el `sub` del JWT se compara con `users.keycloak_id` y solo se simula la API de administración de Keycloak. Cubre:
- **Flujo completo de `HEAD_COACH`:** crear (el creador se convierte en `LEAD`), convocar a 3 jugadores, rechazar a un jugador con asignación vencida y a otro de una categoría distinta, agregar un `ASSISTANT` al personal, iniciar, registrar la asistencia, recibir 409 al intentar completar con un jugador `PENDING`, completar y recibir 409 al intentar editar después.
- **Denegaciones:** `ASSISTANT` recibe 403 al administrar y 200 al leer; un entrenador de otra categoría recibe 404 y `PLAYER`, 403.
- **`start` concurrente por HTTP:** exactamente un 200 y un 409.
- **Listado:** paginación, filtros `status` y `from`/`to`, y aislamiento entre dos academias; todas las combinaciones de encabezados devuelven 404.
- **OpenAPI:** las 14 rutas de sesiones se publican en `/api-docs` (perfil dev) con un resumen.

La comprobación de regresión para `POST /players/{id}/evaluations` es `EvaluationHttpPostgresIntegrationTest`, que se ejecuta en la misma suite.

## Eventos de evaluación (Sprint 4)

### Auditoría del esquema y V9 (T4.2)

`SessionEvaluationEventSchemaPostgresIntegrationTest` escribe filas con JDBC directo, porque audita por sí solas las reglas de la base de datos. Comprueba que la base de datos rechace:
- un `block_order` duplicado en una sesión y un `block_order` igual o menor que cero;
- un estado de evento desconocido y marcas de tiempo del evento que no coincidan con su estado;
- una métrica de otra plantilla, un componente sin puntuación activado por sí solo y una cuarta métrica activa;
- una evaluación que use una plantilla distinta de la de su evento, o un resultado puntuado fuera de las métricas del evento;
- una segunda evaluación del mismo jugador en un evento, salvo que la primera esté `VOIDED`;
- un estado `VOIDED` incoherente (`evaluations_void_check`);
- una evaluación cuya sesión no sea la de su evento (R-2) o cuya categoría no sea la de su sesión.

Dos pruebas llamadas `gap…` documentan reglas que se mantienen en el servicio por diseño: la base de datos acepta esas filas.

`V9MigrationPostgresIntegrationTest` aplica V9 sobre un esquema aparte que ya contiene datos de V1–V8. Comprueba que los datos compatibles se conserven y que se agreguen las restricciones, y que V9 se detenga, sin corregir datos, en tres casos incompatibles.

### Eventos y métricas (T4.3)

- `SessionEvaluationEventServiceTest`: el ciclo de vida del evento como tabla parametrizada (estado × acción; una transición rechazada nunca hace flush). También cubre el `block_order` asignado por el servidor, las métricas predeterminadas, las selecciones de métricas no válidas (400), que solo se acepten plantillas oficiales `ACTIVE`, las verificaciones del estado de la sesión y el orden autorizar → bloquear → releer → validar.
- `TrainingSessionServiceTest`: una sesión con un evento `IN_PROGRESS` no se puede completar ni cancelar (E9), y completar o cancelar una sesión cancela sus eventos `PLANNED`.
- `SessionEvaluationEventSecurityHttpTest`: para cada ruta, 401 sin token, 403 para `PLAYER` y para un entrenador sin acceso de administración, 404 para otra academia y 400 sin `X-Academy-Id`. También comprueba la validación del cuerpo (plantilla faltante, `block_order` enviado por el cliente, camelCase, 0 o 4 métricas) y la respuesta en snake_case.
- `SessionEvaluationEventRequestValidationTest`: Bean Validation y snake_case de las dos solicitudes.
- `SessionEvaluationEventPostgresIntegrationTest` se ejecuta contra `postgres-test`. Comprueba el mapeo de las entidades contra V3 y V9 (`validate` de Hibernate), los números de bloque consecutivos, las métricas predeterminadas, el reemplazo de métricas bajo el trigger de «como máximo 3», el ciclo de vida acoplado a la sesión, la cascada a los eventos `PLANNED`, el 409 al cancelar un evento con evaluaciones no anuladas, el aislamiento entre academias y que el listado use un número constante de consultas (`Statistics` de Hibernate).

Las dos pruebas de concurrencia (dos creaciones a la vez y dos llamadas `start` al mismo evento) se validaron por mutación. Al quitar `TrainingSessionLock` del servicio, ambas fallan: la segunda creación choca con la clave única de `block_order` y las dos llamadas `start` tienen éxito.

### Evaluaciones en un evento (T4.4)

- `SessionEventEvaluationContextServiceTest` comprueba las reglas de contexto:
  - si falta el encabezado se devuelve 400, y un evento desconocido devuelve 404 antes de autorizar;
  - el orden es autorizar → bloquear → releer → verificar estados;
  - una denegación de registro de datos (E5) no queda oculta por las verificaciones de estado;
  - la sesión y el evento deben estar `IN_PROGRESS`;
  - la asistencia se comprueba para cada valor: solo pasan `PRESENT` y `LATE`;
  - el jugador debe estar convocado, y una segunda evaluación no anulada devuelve 409;
  - las tres advertencias, tanto cuando aparecen como cuando no.
- `EvaluationServiceEventTest` comprueba la ruta con evento de `EvaluationService`:
  - solo se exigen las métricas del evento (E2, R-1), y un resultado con puntuación fuera de ellas devuelve 400;
  - se aceptan los componentes sin puntuación;
  - la plantilla y su versión deben ser las del evento;
  - la sesión y la categoría se toman del evento;
  - se devuelven las advertencias;
  - sin evento, nunca se usa el servicio de contexto.
- `PlayerEvaluationSecurityHttpTest` cubre la frontera HTTP del POST:
  - 401 sin token;
  - 403 para `PLAYER` y para administradores sin el rol `COACH` (R-4);
  - el encabezado y `session_event_id` llegan al servicio;
  - la respuesta en snake_case con `warnings`;
  - el POST sin evento funciona sin el encabezado (E7).
- `EvaluationEventPostgresIntegrationTest` se ejecuta por HTTP contra `postgres-test`, con la cadena de seguridad real y la `TrainingSessionAccessPolicy` real. Cubre:
  - E1 y E2;
  - la matriz de registro de datos: DT, `ASSISTANT` del personal, `ASSISTANT` fuera del personal (E6), `FITNESS_COACH` fuera del personal, personal con la sesión `PLANNED` (E5) y entrenador de otra academia;
  - E3;
  - las verificaciones de estado del evento, de academia y de plantilla;
  - el duplicado y la nueva evaluación tras anular (E8);
  - las advertencias;
  - el duplicado concurrente (E4);
  - el comportamiento R-11 del POST sin evento, que se mantiene como deuda.

La prueba del duplicado concurrente se validó por mutación. Al quitar `TrainingSessionLock` de `SessionEventEvaluationContextService`, la solicitud perdedora llega al índice único `evaluations_event_player_uq` en lugar del 409 del servicio, y la prueba falla. `EvaluationHttpPostgresIntegrationTest` (E7) no se modificó y sigue pasando.

### Anulación y lectura de evaluaciones (T4.5)

- `EvaluationAccessPolicyTest` comprueba quién puede hacer qué:
  - el creador y el `HEAD_COACH` vigente de la categoría pueden anular;
  - un administrador del alcance solo puede leer, y uno fuera del alcance no obtiene nada;
  - un entrenador nunca se trata como administrador;
  - un usuario desconocido o sin JWT no obtiene nada.
- `EvaluationVoidServiceTest` comprueba la anulación:
  - el motivo se recorta, el autor sale del JWT y la fecha del reloj;
  - una segunda anulación devuelve 409 y conserva la primera auditoría;
  - 404 fuera del alcance, 403 con acceso de solo lectura, 404 con la ruta de otro jugador;
  - una cuenta inactiva no puede anular, y un motivo vacío o demasiado largo devuelve 400;
  - una evaluación de una sesión se bloquea y se relee antes de verificar su estado, y una evaluación suelta no necesita bloqueo.
- `PlayerEvaluationSecurityHttpTest` agrega:
  - los administradores pueden llamar a la ruta de lectura y `PLAYER` no;
  - solo `COACH` puede anular, y se requiere token;
  - la validación del cuerpo, incluido que no se puede enviar `voided_by_coach_id`;
  - los campos de auditoría en snake_case, y 409 en una segunda anulación.
- `EvaluationEventPostgresIntegrationTest` agrega, por HTTP y con las políticas reales:
  - el DT anula lo que registró el asistente, la evidencia se conserva, una segunda anulación devuelve 409 y el jugador se vuelve a evaluar (E8);
  - la matriz de lectura: el creador, el DT y el administrador de la academia reciben 200; otro asistente, el preparador físico, un entrenador de otra academia, un administrador de otra academia y la ruta de otro jugador reciben 404; un administrador que intenta anular recibe 403;
  - un evento solo se puede cancelar cuando todas sus evaluaciones están anuladas, y la anulación sigue funcionando después de cerrar el evento y la sesión.

`EvaluationHttpPostgresIntegrationTest` cambió una aserción, como se aprobó en T4.5-D1: un `HEAD_COACH` vigente de la categoría ahora lee la evaluación de un colega (200 en lugar de 404). Los demás 404 de esa prueba no cambiaron.

### Jugadores pendientes de observar (T4.6)

- `SessionEvaluationEventServiceTest` agrega:
  - el acceso de consulta se verifica antes de la consulta, en todos los estados del evento, y no se toma bloqueo;
  - solo se piden `PRESENT` y `LATE`;
  - la lista vacía, y 404 antes de autorizar fuera de la academia.
- `SessionEvaluationEventSecurityHttpTest` agrega la ruta a los casos 401, 403 de `PLAYER`, 404 y 400 sin encabezado, y 200 en snake_case para `SYSTEM_ADMIN`, `ACADEMY_ADMIN` y `COACH`, sin `email`.
- `SessionEventPendingPlayersPostgresIntegrationTest`, por HTTP y con la política real:
  - el escenario de la guía: 20 convocados, 3 no presentes (`ABSENT`, `JUSTIFIED`, `PENDING`), 2 evaluados en el evento y 1 solo con una evaluación anulada. El resultado son 15 jugadores ordenados por apellido y nombre, incluido el anulado. Un jugador evaluado en otro evento de la sesión sigue pendiente;
  - no queda nadie cuando todos están evaluados, y un evento cerrado sigue respondiendo;
  - el DT, el preparador físico y el administrador de la academia reciben 200; un entrenador o un administrador de otra academia, y un evento desconocido, reciben 404;
  - el número de sentencias es el mismo con 17 y con 2 jugadores, sin cargas diferidas (sin N+1).

La consulta se validó por mutación. Contar las evaluaciones anuladas como observaciones, u ordenar por ID de jugador, hace fallar la prueba del escenario.

### Fábrica de datos de prueba

`support/TestDataFactory` crea datos de PostgreSQL para las pruebas: academia, categoría de academia con su temporada, ubicación, usuarios, entrenadores y jugadores, sus asignaciones y sesiones. Cada columna única lleva un sufijo aleatorio por fábrica, por lo que las pruebas no necesitan limpieza. `subject(handle)` devuelve el ID de Keycloak del usuario para utilizarlo como sujeto del JWT. Las pruebas de integración de sesiones lo usan. `EvaluationHttpPostgresIntegrationTest` conserva sus propios datos de prueba deliberadamente, porque constituye la evidencia de regresión para las evaluaciones.

Ejecute la suite completa contra `postgres-test`. No necesita `@Disabled` ni omite pruebas. `KeycloakJwtIntegrationTest` solo se omite cuando faltan las tres variables de Keycloak.

```powershell
mvn -o test   # 256 pruebas al final de Sprint 3; 281 tras T4.2; 371 tras T4.3; 411 tras T4.4; 433 tras T4.5; 453 tras T4.6
```

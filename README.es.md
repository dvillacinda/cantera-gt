# CanteraGT

**Idiomas:** [English](README.md) | [Español](README.es.md)

## Desarrollo local

Copia `.env.example` a `.env`, configura las credenciales locales y el cliente de servicio de Keycloak, y luego sigue
[docs/spanish/dev-environment.md](docs/spanish/dev-environment.md) para iniciar Docker Compose y ejecutar las pruebas de
integración contra el servicio PostgreSQL de pruebas aislado y desechable.

## Sesiones de entrenamiento (Sprint 3)

Los entrenadores principales planifican y dirigen sesiones de entrenamiento dentro de la categoría de su academia.
Una sesión puede crearse, editarse mientras esté planificada, iniciarse, completarse o cancelarse. Los entrenadores
convocan jugadores, registran su asistencia y otros entrenadores pueden formar parte del staff de la sesión.
Todas las rutas requieren el encabezado `X-Academy-Id`.

- [API de sesiones](docs/spanish/sessions-api.md): rutas, reglas y códigos de error.
- ADR:
  - [ADR-0001](docs/spanish/adr/ADR-0001-rest-conventions.md): convenciones REST.
  - [ADR-0002](docs/spanish/adr/ADR-0002-session-permissions.md): permisos de sesión.
  - [ADR-0003](docs/spanish/adr/ADR-0003-time-zone-and-clock.md): zona horaria y `Clock`.
- [Pruebas](docs/spanish/testing.md): cómo ejecutar las pruebas unitarias, HTTP de seguridad y de integración con
  PostgreSQL.

## Aprovisionamiento de usuarios en Keycloak (MVP)

Las cuentas creadas mediante los endpoints de Entrenadores y Jugadores se aprovisionan primero en Keycloak.
La API obtiene el identificador nuevo desde el encabezado `Location` devuelto por Keycloak y lo guarda como
`users.keycloak_id`. El llamador autenticado sigue identificado por separado mediante el claim JWT `sub`.

Configura `KEYCLOAK_SERVER_URL`, `KEYCLOAK_ISSUER_URL`, `KEYCLOAK_REALM`, `KEYCLOAK_ADMIN_CLIENT_ID` y
`KEYCLOAK_ADMIN_CLIENT_SECRET` en cada entorno. `KEYCLOAK_ISSUER_URL` debe ser la URL emisora del realm utilizada
para validar la firma JWT y los claims del emisor (por ejemplo,
`http://localhost:8181/realms/<realm>` en desarrollo local). Es obligatoria y no tiene un fallback inseguro.
El cliente confidencial debe tener habilitados los service accounts y permiso para gestionar usuarios en el realm
objetivo. Las cuentas nuevas se crean habilitadas con la acción requerida `UPDATE_PASSWORD`; configura SMTP y
correo electrónico del realm si los usuarios deben recibir correos para establecer su contraseña. CanteraGT no
crea ni almacena contraseñas.

El MVP todavía no tiene una ruta para crear Administradores de Academia: el endpoint genérico fue eliminado porque
no distinguía ni autorizaba el tipo objetivo. El aprovisionamiento de administradores requiere un flujo explícito
de aplicación. Los endpoints de creación de Entrenadores y Jugadores exigen las autoridades `ACADEMY_ADMIN` y
`ACADEMY_ADMIN`/`COACH`, respectivamente. La pertenencia de entrenadores y jugadores se resuelve mediante las
entidades de asignación.

## Alcance del administrador de academia

`GET /api/v1/auth/me` devuelve las asignaciones activas del usuario autenticado en `academies`. Las solicitudes a
`/api/v1/users/**`, `/api/v1/academy-category/**` y las rutas de perfiles de jugadores o entrenadores por ID deben
incluir el UUID seleccionado como `X-Academy-Id`. La API valida este selector contra la asignación activa del sujeto
autenticado en cada solicitud; solo poseer el UUID no concede acceso. `SYSTEM_ADMIN` puede seleccionar cualquier
academia existente. El acceso de un entrenador a jugadores además requiere una asignación vigente a la categoría
de la academia del jugador. Los perfiles fuera de alcance devuelven 404.

Las categorías y temporadas son catálogos compartidos, no registros propiedad de una academia. Los administradores
de academia pueden leerlos, mientras que solo `SYSTEM_ADMIN` puede crear, actualizar, cambiar el estado o eliminar
entradas del catálogo. Las asociaciones entre categorías y temporadas específicas de una academia se crean y leen
dentro del contexto de academia validado.

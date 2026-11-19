# Contrato de la API de evaluaciones (CAN-003 MVP)

Todos los nombres de propiedades JSON usan `snake_case`. La autenticación utiliza el token bearer de Keycloak.

## Plantillas oficiales

| Método | Ruta | Comportamiento |
| --- | --- | --- |
| GET | `/api/v1/evaluation-templates` | Enumera las plantillas oficiales `ACTIVE` y sus dimensiones y criterios. |
| GET | `/api/v1/evaluation-templates/{templateId}` | Devuelve una plantilla oficial activa con sus dimensiones, definiciones de métricas, límites de escala y niveles de escala. |

Las rutas GET heredadas (`/get-all-templates` y `/get-template-by-id/{templateId}`) siguen disponibles como alias por compatibilidad. La aplicación no expone la administración de plantillas. La migración de la base de datos también bloquea las actualizaciones y eliminaciones de plantillas y criterios oficiales.

## Autoservicio para jugadores

`GET /api/v1/players/me` requiere el rol `PLAYER` y obtiene al jugador a partir del `sub` del JWT validado y del vínculo con el usuario local. No acepta ningún identificador de jugador. La respuesta contiene el nombre, correo electrónico, fecha de nacimiento, sexo y datos de posición del propio jugador; omite el ID de usuario local, el sujeto de Keycloak, el estado de la cuenta, la pertenencia a una academia, las asignaciones y los datos de evaluación. No se ofrece una ruta para que el jugador edite sus propios datos. Los datos de academias y asignaciones siguen siendo de solo lectura mediante las rutas existentes `/api/v1/player-assignments/get-my-*`.

## Administración de entrenadores

Crear o eliminar una cuenta de entrenador, cambiar los nombres de su perfil global y cambiar el estado global de su cuenta requiere `SYSTEM_ADMIN`. Los administradores de academia gestionan las asignaciones de entrenadores a academias y categorías mediante `/api/v1/coach-assignments/**`; cada operación verifica la asignación activa del administrador y limita el ID de asignación a esa academia. Las rutas generales de actualización y estado de usuarios también rechazan a los administradores de academia cuando el usuario objetivo es un entrenador, lo que impide eludir por ruta la política de cuentas globales. La lectura de perfiles de entrenadores sigue limitada a la academia.

## Registrar y consultar una evaluación

| Método | Ruta | Comportamiento |
| --- | --- | --- |
| POST | `/api/v1/players/{playerId}/evaluations` | Registra una evaluación completada. Requiere el rol `COACH`, además de asignaciones vigentes del entrenador y del jugador en la misma categoría de academia. |
| GET | `/api/v1/players/{playerId}/evaluations/{evaluationId}` | Recupera una evaluación creada por el entrenador autenticado. |

Las rutas de perfiles del personal bajo `/api/v1/players/**` y `/api/v1/coaches/**` que acceden a un recurso por ID requieren `X-Academy-Id`. El backend resuelve las asignaciones activas de academia y categoría en cada solicitud. El entrenador debe compartir actualmente la categoría de academia del jugador; un administrador de academia debe administrar la academia seleccionada y el objetivo debe tener allí una asignación vigente. Los perfiles fuera del ámbito devuelven 404.

Actualmente no existe una ruta de perfil propio para jugadores. `PLAYER` no puede acceder a las rutas de personal para jugadores. Agregar una ruta de autoservicio y decidir qué campos se pueden consultar o editar requiere una decisión de producto. Los entrenadores no pueden editar su perfil mediante la API de entrenadores. Las mutaciones existentes de entrenadores por parte de administradores de academia pueden cambiar el nombre y apellido globales del entrenador, el estado global de su cuenta o eliminar el registro global del entrenador/usuario después de encontrar una asignación vigente en la academia seleccionada. Por tanto, un entrenador asignado a varias academias se ve afectado globalmente. El código no define si estos cambios globales corresponden a un administrador de academia, solo a un administrador de sistema o si deberían convertirse en datos propios de cada academia; esta decisión está pendiente, así que aquí se documentan los permisos actuales de mutación de la API, sin modificarlos.

Solo el entrenador que creó una evaluación puede consultarla. Los demás entrenadores reciben 404 aunque compartan la academia o categoría; así se evita exponer datos del jugador mediante los ID de evaluación. Si se desea que los evaluadores de toda la academia tengan acceso, se necesita una decisión de política explícita.

El catálogo oficial actual no contiene criterios con `laterality_mode = REQUIRED`. Algunos criterios usan `OPTIONAL` y otros, `NONE`. La API de creación valida el lado proporcionado para cada resultado, pero no exige un par LEFT/RIGHT. No se puede verificar una regla de pares bilaterales en el catálogo oficial hasta que una plantilla defina ese requisito.

Ejemplo de solicitud:

```json
{
  "template_id": "00000000-0000-0000-0000-000000000001",
  "template_version": 1,
  "results": [
    {
      "criterion_id": "00000000-0000-0000-0000-000000000002",
      "raw_value": 1.82,
      "laterality": "NONE",
      "observations": [
        { "sequence": 1, "value": 1.91, "valid": true, "note": "Primer intento" },
        { "sequence": 2, "value": 1.82, "valid": true }
      ]
    }
  ],
  "strengths": "Buena aceleración inicial",
  "improvement_areas": "Mantener la técnica al final",
  "general_comment": ""
}
```

El evaluador se obtiene del JWT autenticado y el cliente no puede proporcionarlo. El ID y la versión de la plantilla deben identificar la versión oficial activa actual; los criterios deben pertenecer a esa plantilla. Los criterios activos predeterminados son obligatorios. Se validan las pruebas sin procesar específicas de cada métrica, los límites de escala, los comentarios requeridos, la lateralidad y los datos de las observaciones. La evaluación, los resultados y las observaciones se guardan en una sola transacción. La referencia a la plantilla conserva la versión utilizada; en el MVP, las filas de plantillas y criterios oficiales son inmutables.

## Alcance actual y verificación

El comando específico está documentado en [testing.md](testing.md). Las pruebas de PostgreSQL verifican las diez plantillas precargadas y la persistencia HTTP en una base de datos temporal con las migraciones aplicadas. La puntuación general y de crecimiento queda fuera del MVP hasta que se aprueben sus definiciones funcionales. El alcance de los cambios al perfil global o al estado de cuenta de un entrenador con asignaciones en varias academias también sigue siendo una decisión de producto; el acceso por ID se sigue comprobando con respecto a la academia seleccionada.

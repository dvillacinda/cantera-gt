# Entorno local de desarrollo

## Requisitos

- Docker Desktop con Compose v2.
- Java 21 y Maven 3.9 o Maven Wrapper.
- Un export sanitizado del realm `canteragt-security-realm` en `docker/keycloak/realms/`.

## Configuración inicial

1. Copia `.env.example` a `.env` y cambia los valores de ejemplo por credenciales locales. `.env` está excluido de Git.
2. En Keycloak, configura el cliente confidencial indicado por `KEYCLOAK_ADMIN_CLIENT_ID`, habilita el service account y asígnale los permisos mínimos para gestionar usuarios. El export contiene `REPLACE_WITH_KEYCLOAK_CLIENT_SECRET` como marcador: después de importar el realm, genera un secreto local y coloca el mismo valor en `.env`.
3. Inicia la aplicación y Keycloak:

   ```powershell
   docker compose up -d
   ```

4. Para verificar los servicios, consulta `docker compose ps`. PostgreSQL debe aparecer saludable y Keycloak debe responder en `http://localhost:8181/realms/$env:KEYCLOAK_REALM/.well-known/openid-configuration`.

La aplicación usa PostgreSQL en el puerto 5432 y Keycloak en el 8181. La base de datos de Keycloak vive en el servicio interno `keycloak-postgres` y no publica un puerto del host.

## Base de datos aislada para pruebas

El perfil `test` inicia `postgres-test` en el puerto 5433. Su almacenamiento es `tmpfs`, así que sus datos son efímeros y desaparecen al detener o recrear el contenedor. No conectes datos importantes a este servicio.

```powershell
docker compose --profile test up -d postgres-test
docker compose ps postgres-test
docker compose exec -T postgres-test pg_isready -U cantera_gt_test -d cantera_gt_test
```

Antes de ejecutar la integración PostgreSQL, carga las variables de `.env` al proceso Maven y sobrescribe la conexión de aplicación para apuntar al servicio de prueba:

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

`java-dotenv` se ejecuta desde `CanteragtApplication.main()` y no carga `.env` cuando Maven inicia los tests. Por eso las variables deben estar en el entorno del proceso de Maven. La prueba `KeycloakJwtIntegrationTest` usa las variables de Keycloak de `.env` y requiere que el realm y el cliente de servicio estén configurados.

## Problemas frecuentes

- **Docker no responde:** verifica Docker Desktop y vuelve a ejecutar `docker info` y `docker compose ps`. Si una prueba dependiente de servicios falla por conexión, sigue el Docker-Gate de `docs/LLMs/CanteraGT_Sprint3_Guia_LLM.md` y no continúes con pruebas dependientes hasta recuperar los servicios.
- **Variables faltantes:** confirma que `.env` existe y que su formato es `NOMBRE=valor`; no imprimas ni compartas secretos al diagnosticar.
- **Realm no existe:** confirma que el JSON sanitizado está en `docker/keycloak/realms/` y revisa los logs de Keycloak con `docker compose logs --tail=100 keycloak`. Keycloak no vuelve a importar un realm que ya existe en su base.
- **La integración se conecta a la BD incorrecta:** comprueba que `BACKEND_URL`, `POSTGRES_DB`, `POSTGRES_USER` y `POSTGRES_PASSWORD` están sobrescritos para `postgres-test` en el proceso Maven.

## Datos y apagado

La base `postgres` usa el volumen persistente `canteragt_postgres_data`; Keycloak usa `keycloak_postgres_data`. No uses `docker compose down -v` en este entorno. La base de pruebas es efímera por diseño y no debe contener datos que deban conservarse.

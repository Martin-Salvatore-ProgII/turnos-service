# Pruebas del servicio de turnos

## Requisito: Docker en ejecución

Las pruebas usan PostgreSQL real, no una base en memoria (ADR-0045). Al correrlas, Testcontainers levanta un contenedor `postgres:18` descartable, le aplica las migraciones de Flyway y lo elimina al terminar.

Por eso **Docker tiene que estar corriendo** en la máquina. No hace falta levantar nada a mano ni definir variables de entorno: el contenedor y la conexión los maneja el propio test. Si Docker no está disponible, las pruebas que levantan el contexto de Spring fallan al iniciar.

La primera ejecución descarga la imagen de PostgreSQL y tarda más.

## Cómo ejecutarlas

```
./gradlew test
```

El reporte queda en `build/reports/tests/test/index.html`.

Para correr una sola clase:

```
./gradlew test --tests '*ArchitectureTest'
```

## Qué hay

| Archivo | Para qué | Necesita Docker |
| --- | --- | --- |
| `TurnosApplicationTests` | Verifica que la aplicación arranca completa contra PostgreSQL, con Flyway | Sí |
| `ArchitectureTest` | Verifica la regla de dependencias de la arquitectura hexagonal (ADR-0053) | No |
| `TestcontainersConfiguration` | Declara el contenedor de PostgreSQL que usan las pruebas. Se importa con `@Import` en cada prueba que necesite base | — |
| `TestJwtKeysConfiguration` | Genera en memoria un par de claves RSA para firmar el JWT durante las pruebas. No hay ninguna clave en el repositorio | — |
| `TestTurnosApplication` | No es una prueba: levanta la aplicación con una base descartable para probarla a mano | Sí |

## Pruebas de usuarios y seguridad

Las pruebas siguen la estructura de paquetes del código: cada clase se prueba en su capa.

| Qué se quiere comprobar | Dónde | Necesita Docker |
| --- | --- | --- |
| El recorrido completo: registrarse, iniciar sesión, usar el token, y el acceso del administrador | `user/UserAuthenticationFlowTest` | Sí |
| Reglas de acceso: rutas públicas, sin token, token vencido o falsificado, rutas administrativas, CORS | `shared/infrastructure/config/SecurityConfigTest` | Sí |
| Reglas de negocio del registro, el inicio de sesión y el administrador | `user/application/usecases/*Test` | No |
| Validaciones del registro y códigos de error HTTP | `user/infrastructure/web/controller/UserControllerTest` | No |
| Guardado y lectura de usuarios en PostgreSQL | `user/infrastructure/persistence/adapter/JpaUserRepositoryAdapterTest` | Sí |
| Hash de contraseñas y contenido y firma del JWT | `user/infrastructure/security/adapter/*Test` | No |
| Esquema y restricciones de la base | `DatabaseMigrationTest` | Sí |

## Levantar la aplicación con una base descartable

```
./gradlew bootTestRun
```

Arranca el servicio contra un PostgreSQL de Testcontainers, sin configurar `DB_URL`, `DB_USER` ni `DB_PASSWORD`. Los datos se pierden al detenerlo.

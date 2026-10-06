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

## Levantar la aplicación con una base descartable

```
./gradlew bootTestRun
```

Arranca el servicio contra un PostgreSQL de Testcontainers, sin configurar `DB_URL`, `DB_USER` ni `DB_PASSWORD`. Los datos se pierden al detenerlo.

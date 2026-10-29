# Pruebas del servicio de turnos

## Requisito: Docker en ejecución

Las pruebas usan PostgreSQL real, no una base en memoria (ADR-0045), y una cátedra y un catálogo simulados con WireMock: ninguna prueba habla con la cátedra ni con el catálogo reales (ADR-0044). Al correrlas, Testcontainers levanta los contenedores `postgres:18` y `wiremock/wiremock` descartables y los elimina al terminar.

Por eso **Docker tiene que estar corriendo** en la máquina. No hace falta levantar nada a mano ni definir variables de entorno: el contenedor y la conexión los maneja el propio test. Si Docker no está disponible, las pruebas que levantan el contexto de Spring fallan al iniciar.

La primera ejecución descarga las imágenes y tarda más.

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
| `TestCatedraConfiguration` | Le da a la aplicación una cátedra falsa que responde la integración en estado `PROVISIONED`. Toda prueba con `@SpringBootTest` la importa, porque sin la integración el servicio no arranca | — |
| `TestCatalogConfiguration` | Le da a la aplicación un catálogo falso, sin respuestas programadas. Toda prueba con `@SpringBootTest` la importa, porque sin la URL del catálogo el contexto no arranca | — |
| `FakeServer` | Arma un contenedor de WireMock y le programa respuestas por su API de administración (incluidas demoras, cortes de conexión y escenarios). Lo usan la cátedra y el catálogo simulados | — |
| `FakeCatedra` | Lo propio de la cátedra simulada: el JWT técnico de prueba y la respuesta de la integración, en `resources/catedra/integration.json` | — |
| `FakeCatalog` | Lo propio del catálogo simulado: la ruta de la agenda y respuestas reales del catálogo, en `resources/catalog/` (la agenda de la 204, la de la 206 deshabilitada y el 404 `PROFESSIONAL_NOT_FOUND`) | — |
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
| Todo 400 responde `VALIDATION_ERROR`, también los que arma Spring (JSON mal formado, tipo inválido, parámetro que falta) | `shared/infrastructure/web/advice/GlobalExceptionHandlerTest` | No |

## Pruebas de la integración con la cátedra

| Qué se quiere comprobar | Dónde | Necesita Docker |
| --- | --- | --- |
| El reintento común: un intento más ante falta de respuesta o error del servicio, nunca ante un 4xx, sin la dirección en el mensaje | `shared/infrastructure/rest/RemoteReadRetryTest` | No |
| Toda lectura a la cátedra: JWT rechazado, respuesta inesperada o ilegible, cátedra lenta, caída o con error, exactamente un reintento, sin la dirección en los mensajes | `shared/infrastructure/rest/CatedraReadExecutorTest` | Sí |
| Integración con la cátedra al arrancar: campos, JWT técnico, `PROVISIONED` u otro estado, sin secretos al imprimirla | `shared/infrastructure/config/CatedraIntegrationConfigTest` | Sí |

## Pruebas del cliente del catálogo

| Qué se quiere comprobar | Dónde | Necesita Docker |
| --- | --- | --- |
| La agenda real de un profesional, uno deshabilitado, horas `HH:mm`, campos desconocidos, el JWT del usuario reenviado, profesional inexistente, 404 sin código, token rechazado, catálogo no listo, lento, caído o con respuesta ilegible, sin la dirección en los mensajes | `catalog/infrastructure/rest/adapter/CatalogServiceAdapterTest` | Sí |
| `503 CATALOG_UNAVAILABLE` y el 401 cuando el catálogo rechaza el token | `shared/infrastructure/web/advice/GlobalExceptionHandlerTest` | No |
| Una feature solo usa el `domain` de otra (ADR-0062) | `ArchitectureTest` | No |

## Levantar la aplicación con una base descartable

```
./gradlew bootTestRun
```

Arranca el servicio contra un PostgreSQL de Testcontainers y la cátedra y el catálogo simulados, sin configurar nada. Los datos se pierden al detenerlo.

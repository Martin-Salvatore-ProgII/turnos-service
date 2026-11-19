# CLAUDE.md

Guía para agentes de IA que trabajan en este repositorio. Es corta a propósito: lo que vale para todo el sistema está en el `CLAUDE.md` de la carpeta que contiene a los repos y en el repositorio `docs` (constitución, ADR, contratos, `guia-git.md`). Acá va solo lo propio de este repo y lo que no se deduce leyendo el código.

## Antes de escribir código

1. Leer `docs/constitucion.md` y los ADR que toque la issue.
2. Cargar la skill `/hexagonal` y seguirla. No reconstruir el patrón de memoria.
3. Plantearle al alumno las decisiones abiertas, con una recomendación y su porqué, y esperar su respuesta. El alumno defiende cada decisión en una evaluación oral.

## Comandos

| Para | Comando |
| --- | --- |
| Compilar y correr todas las pruebas | `./gradlew build` |
| Correr una clase de prueba | `./gradlew test --tests '*NombreDeLaClase'` |
| Levantar el servicio con una base descartable | `./gradlew bootTestRun` |
| Levantar el sistema completo en Docker | `docker compose up --build` |
| Levantar solo este servicio y su base | `docker compose up --build turnos-service` |

Las pruebas necesitan Docker en ejecución (PostgreSQL real y la cátedra simulada con WireMock, con Testcontainers). Toda prueba con `@SpringBootTest` importa `TestCatedraConfiguration` y `TestCatalogConfiguration`: sin la integración o sin la URL del catálogo, el contexto no arranca. El README explica la configuración (`.env`, clave del JWT, administrador).

## Qué hay construido

- **Feature `user`:** registro (`POST /api/register`), inicio de sesión (`POST /api/authenticate`) y el administrador inicial, creado al arrancar. Es la feature de referencia para las siguientes.
- **Feature `catalog`:** la agenda vigente de un profesional, pedida al catálogo (`CatalogClientPort` y `CatalogServiceAdapter`). No tiene casos de uso: la disponibilidad y la reserva usan su puerto de salida (ADR-0062).
- **Feature `availability`:** `GET /api/availability` y el cálculo de la disponibilidad de CU-03 (`GetAvailabilityUseCaseImpl` y la fachada `AvailabilityService`), la regla que corta un horario en slots (`ScheduleSlots`, en el dominio porque la reserva también la usa) y el puerto de ocupaciones con su adaptador (`CatedraOccupancyAdapter`). "Hoy" y "ahora" son los de `AgendaZone` (ADR-0016), con el `Clock` inyectado. Una ocupación bloquea solo el slot que empieza a su hora.
- **`shared/infrastructure/config`:** `JwtConfig` (claves, firma y validación), `SecurityConfig` (reglas de acceso y CORS), `JpaAuditingConfig`, y la integración con la cátedra: `CatedraRestClientConfig` (cliente con el JWT técnico y timeouts) y `CatedraIntegrationConfig` (pide la integración al arrancar; sin ella no arranca, ADR-0005). `CatalogRestClientConfig` arma el cliente del catálogo, que reenvía el JWT del usuario tomado del `SecurityContext` (ADR-0034).
- **`shared/infrastructure/rest`:** `RemoteReadRetry`, el reintento de toda lectura a la cátedra o al catálogo, y `CatedraReadExecutor`, que le suma los errores propios de la cátedra (ADR-0029, ADR-0063). `RemoteCommandRetry`, el de las operaciones con efectos seguras de repetir (confirmación, teléfono y cancelación), con espera creciente configurable. `ProblemDto` lee el `code` de un `problem+json`. `NoRetryRequestFactory` es la conexión de todo cliente REST (sin reintentos propios) y `HttpTimeouts` clasifica sus timeouts.
- **`shared/infrastructure/web`:** `GlobalExceptionHandler`, la validación `@MaxBytes` y la paginación de las listas (`PaginationHeaders`, `@MaxPageSize`).
- **Feature `reservation` (M4, M5 y M6 terminadas):** el proceso de reserva y su máquina de estados. `ReservationTrigger` tiene una constante por fila de la tabla de transiciones de `docs/arq/maquina-estados.md`, y `ReservationProcess.apply` solo cambia el estado si el disparador vale desde el actual (ADR-0023). El proceso lleva `version` con `@Version`: un cambio sobre una lectura vieja no se guarda y el adaptador lanza `ReservationProcessConflictException` (ADR-0068). `ReservationProcessTest` prueba las 140 combinaciones contra una tabla escrita a mano: si cambia una transición, cambian el documento y esa tabla. `StartReservationUseCaseImpl` valida el turno contra la agenda vigente (reusa `AvailabilityHorizon` y `ScheduleSlots.ofDay`), guarda el proceso en `STARTED`, pide el hold con `HoldPort` (`CatedraHoldAdapter`, un solo intento: toda falla deja el proceso `FAILED`, ADR-0028 y ADR-0069) y lo confirma (con `RemoteCommandRetry`; `RESERVATION_PROCESS_ALREADY_CONFIRMED` es éxito y sin respuesta queda en `HELD`, ADR-0029). Antes de consultar al catálogo rechaza a un usuario con una reserva en curso (`ACTIVE_RESERVATION_EXISTS`); la garantía ante pedidos simultáneos es el índice de `V3`, que el adaptador traduce a ese mismo error (ADR-0070, `ActiveReservationConcurrencyTest`). `POST /api/reservations` y `GET /api/reservations/{id}` (fachada `ReservationService`, `ReservationController`); el usuario sale siempre del JWT (`Authentication.getName()`), y una reserva ajena se filtra en la consulta misma (`findByIdAndUserId`), así que responde igual que una inexistente.
- **Reserva por Kafka (M5):** `ReservationEventListener` (adaptador de entrada, sin lógica) lee el topic de acciones, interpreta el mensaje con `ReservationEventMessageMapper` y llama a la fachada; reintenta 3 veces y confirma el offset siempre al final (ADR-0031). `HandleReservationEventUseCaseImpl` es el único caso de uso de los eventos de la cátedra: dentro de una transacción busca el proceso por `reservationProcessId`, registra el `eventId` (`processed_event`, `INSERT ... ON CONFLICT DO NOTHING`, ADR-0070) y aplica el disparador de la máquina de estados; si algo falla, el registro se deshace y la copia reentregada no se toma por duplicada. `SubmitPhoneUseCaseImpl` normaliza y valida el teléfono (`PhoneNumber`, ADR-0027), publica con `AdditionalInformationPublisherPort` (`KafkaAdditionalInformationAdapter`, con `RemoteCommandRetry` y el mismo `eventId`) y recién entonces pasa a `PHONE_SUBMITTED`. `ReconcileReservationProcessesUseCaseImpl` y `AppointmentPort` (`CatedraAppointmentAdapter`) resuelven los procesos colgados; `ReservationReconciliationScheduler` los dispara al arrancar y cada intervalo.
- **Mis reservas (M6):** `GET /api/reservations` (`ListReservationsUseCaseImpl`) lista las del usuario desde la base, con el dueño en la consulta misma (`findByUserId`), paginado con `PageQuery` y `PageResult` y las cabeceras de `shared/infrastructure/web/pagination`. `POST /api/reservations/{id}/cancel` (`CancelReservationUseCaseImpl`, con las reglas en `ReservationCanceller`): ya `CANCELLED` es éxito sin llamar a la cátedra; si no está `CONFIRMED` o el turno ya empezó (`ReservationProcess.appointmentHasStarted`, ADR-0071), `RESERVATION_NOT_CANCELLABLE`; si no, `AppointmentPort.cancelAppointment` (`CatedraAppointmentAdapter`, con `RemoteCommandRetry`) y con el 200 pasa a `CANCELLED` con `cancelledBy` = login del dueño (ADR-0026). `AppointmentCancelled` por Kafka cancela un `CONFIRMED` con `cancelledBy` = `ReservationProcess.CANCELLED_BY_CATEDRA`; después de la cancelación del dueño no tiene transición.
- **Una respuesta REST y un evento pueden cruzarse** (CU-04, 8a): `StartReservationUseCaseImpl`, `SubmitPhoneUseCaseImpl` y `CancelReservationUseCaseImpl`, ante un `ReservationProcessConflictException` al guardar, releen el proceso y solo aplican su cambio si todavía tiene transición. El evento manda; la respuesta tardía no retrocede nada.
- **Migraciones:** `V1` con `app_user`, `authority` y `app_user_authority`; `V2` con `reservation_process`; `V3` con el índice único parcial que deja un solo proceso activo por usuario; `V4` con `processed_event`.

- **Administración (M7):** `AdminReservationController` en `/api/admin/reservations`, dentro de la feature `reservation` y con la misma fachada; el rol lo exige `SecurityConfig` por ruta (ADR-0037). `GET` (`ListAllReservationsUseCaseImpl`) filtra con `ReservationSearchCriteria` y `ReservationProcessSpecifications` (un `Specification` por filtro, como la búsqueda del catálogo). El dueño es de la feature `user`: el login del filtro se resuelve a id y los dueños de la página se leen en una sola consulta (`UserRepository.findAllById`), sin JOIN (ADR-0064). La respuesta es la de una reserva más los datos de soporte (`AdminReservationResponse`, con `@JsonUnwrapped`). `GET /{id}` (`GetAnyReservationUseCaseImpl`) busca sin filtro por dueño: esa diferencia es la operación administrativa, en otro caso de uso y otra ruta, nunca un "if admin". `POST /{id}/cancel` (`CancelAnyReservationUseCaseImpl`) exige motivo (lo valida el pedido) y registra en `cancelledBy` el login del administrador, sin columna nueva. Las reglas de la cancelación (ya cancelada, estado, turno empezado, pedido a la cátedra, cruce con un evento) están en `ReservationCanceller`, una pieza de `application/usecases` sin puerto que usan las dos cancelaciones: el administrador cancela con las mismas reglas que el dueño. `AdminAuthorizationTest` es la evidencia de autorización: 403 de un usuario común en las tres rutas, y por las rutas de usuario el administrador es un usuario más.

- **HTTPS (M8):** perfil `tls` (`application-tls.yaml`), que el Compose prende siempre: el servidor usa el SSL bundle `server` y el cliente del catálogo confía solo en el bundle `catalog` (el mismo certificado autofirmado), con `NoRetryRequestFactory` y verificación del host. El certificado lo genera `generar-certificado.sh` en `secrets/` de los dos repos. Sin el perfil, el servicio queda en HTTP: todas las pruebas corren así salvo `TlsTest`, que usa el certificado de prueba de `src/test/resources/tls`.

## Lo propio de este servicio

- **Este servicio emite el JWT de usuario** y también lo valida. Recibe solo la clave privada; la pública se calcula a partir de ella. El contenido del token (`sub` con el login, `auth` con la lista de roles) es un contrato con el catálogo: está en `docs/arq/seguridad.md` y no se cambia sin cambiarlo allá.
- **El hasheo de contraseñas y la emisión del token son puertos de salida** (`PasswordHasherPort`, `TokenIssuerPort`). Los casos de uso no importan Spring Security.
- **Cómo informa un rechazo un caso de uso** (ADR-0059): con un solo motivo, devuelve vacío y la fachada lanza la excepción; con varios, lanza la excepción de la feature con su código (`UserException`, `AvailabilityException`). `GlobalExceptionHandler` traduce el código a HTTP.
- **Las contraseñas tienen un máximo de 72 bytes** (ADR-0057), validado con `@MaxBytes`. Los DTO que llevan una contraseña no tienen `@ToString`.
- **Los campos de auditoría los completa Spring Data** (ADR-0058), no los casos de uso. Sin usuario autenticado, el autor es `system`.
- **El administrador no se registra desde la app** (ADR-0061): lo crea `AdminUserInitializer` al arrancar, y solo es administrador la cuenta con el login y la contraseña configurados.
- **Un adaptador de entrada puede no ser REST.** `AdminUserInitializer` es el ejemplo: sin lógica, llama a la fachada. Los consumidores de Kafka siguen la misma forma.
- **El `compose.yaml` de este repo es el del sistema completo** e incluye al del catálogo. Necesita `../catalogo-service` y un `.env` en cada repo. Las variables de este `.env` llevan el prefijo `TURNOS_` porque una variable repetida pisaría el valor del catálogo.
- **Las reglas de "como máximo uno" las garantiza la base** (ADR-0070): una restricción o un índice único, con su nombre, que el adaptador reconoce y traduce al error funcional. Una consulta previa es solo un atajo. Para probar la concurrencia de verdad, ver `ActiveReservationConcurrencyTest` (sin transacción alrededor del test, y los datos se borran a mano).
- **Este servicio no guarda una copia del catálogo** (Constitución P-05): consulta al servicio de catálogo reenviando el JWT del usuario (ADR-0034).

## Flujo de trabajo con Git

El detalle está en `docs/guia-git.md`. En resumen:

- **El agente** crea la rama de la issue solo en local, desde `develop` actualizada: `git checkout -b <tipo>/<N>-<descripcion>`. No escribe nada en GitHub. Escribe los archivos y corre `./gradlew build`.
- **El alumno** revisa, commitea, pushea, abre el pull request a `develop` y mergea. El agente no commitea, no pushea commits ni mergea, y no crea ni cierra issues.
- Al terminar, el agente entrega un bloque de comandos listo para pegar (`git add` por commit, `git commit -m`, `git push`, `gh pr create --base develop`, `gh pr merge --merge --delete-branch`, `gh issue close`). Mensajes en español, con Conventional Commits, sin atribuciones a herramientas. Cada comando en una sola línea, sin `\` de continuación.
- Antes de crear la rama de la issue siguiente, verificar con `git fetch` que el pull request anterior ya está en `origin/develop`.
- Al cerrar la última issue de una milestone se crea la rama `milestone/<n>-<nombre>` y el pull request a `main` sale de ahí, con revisión del profesor. Nunca de `develop`.
- Una decisión que afecta al otro servicio se propone para `docs` (ADR), con permiso del alumno antes de escribirla.

## Convenciones del código

- Indentación con tabs.
- Identificadores y mensajes de error en inglés; comentarios y documentación en español.
- **Comentarios de decisión:** donde se toma una decisión que alguien podría cuestionar, dos o tres líneas con el porqué, en ese lugar. No se comenta lo obvio.
- Cada paquete nuevo lleva su `package-info.java` (ADR-0055).
- Cada dependencia entra al build en la issue que la usa por primera vez.
- Las pruebas de cada pieza entran en la misma issue que la pieza.

## Particularidades técnicas

Spring Boot 4, Spring Security 7 y Jackson 3 cambiaron cosas respecto de versiones anteriores. Lo que ya costó descubrir:

**Dependencias.** Los starters tienen nombres nuevos (`spring-boot-starter-webmvc`, `spring-boot-starter-security-oauth2-resource-server`) y cada uno tiene su starter de test (`...-webmvc-test`, `...-data-jpa-test`). Ante la duda, pedirle la lista a Spring Initializr en lugar de adivinar.

**Paquetes de las anotaciones de prueba.**

| Anotación | Paquete |
| --- | --- |
| `@WebMvcTest`, `@AutoConfigureMockMvc` | `org.springframework.boot.webmvc.test.autoconfigure` |
| `@DataJpaTest` | `org.springframework.boot.data.jpa.test.autoconfigure` |
| `TestEntityManager` | `org.springframework.boot.jpa.test.autoconfigure` |
| `@AutoConfigureTestDatabase` | `org.springframework.boot.jdbc.test.autoconfigure` |
| `@MockitoBean` | `org.springframework.test.context.bean.override.mockito` |

**Cómo se arma cada tipo de prueba.**

- **Controller:** `@WebMvcTest` con `@AutoConfigureMockMvc(addFilters = false)`. Sin eso, la seguridad responde 401 antes de llegar al controller. Las reglas de acceso se prueban aparte.
- **Pruebas que dependen de "hoy":** importar `TestClockConfiguration` (un `Clock` fijo y `@Primary`). Nunca `LocalDate.now()` ni la hora de la máquina en una prueba; el JWT se sigue validando con la hora del sistema.
- **Seguridad y recorridos completos:** `@SpringBootTest` + `@AutoConfigureMockMvc`, importando `TestcontainersConfiguration`, `TestJwtKeysConfiguration`, `TestCatedraConfiguration` y `TestCatalogConfiguration`. Sin las tres últimas, el contexto no arranca: falta la clave del JWT, la integración con la cátedra o la URL del catálogo.
- **Adaptador de persistencia:** `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` + `@Import` de `TestcontainersConfiguration`, el adaptador y su mapper. Para leer de verdad de PostgreSQL hay que hacer `flush()` y `clear()` antes: sin eso JPA responde desde memoria, y los errores de restricciones de una colección no aparecen.
- **Casos de uso y fachada:** JUnit y Mockito, sin Spring.

**Jackson 3.** Un campo primitivo (`boolean`, `int`) que no viene en el JSON hace fallar el pedido con 400. Un campo opcional de un DTO de entrada va con su tipo envoltorio (`Boolean`, `Integer`).

**Spring Security 7.**

- Agrega por su cuenta la autoridad `FACTOR_BEARER` a todo pedido autenticado con token. No viene del JWT; al verificar roles en una prueba, usar `contains` y no una comparación exacta.
- Los 401 y 403 que genera la seguridad no tienen cuerpo: traen el encabezado `WWW-Authenticate`. Es lo acordado en el contrato.
- Una ruta inexistente sin token da 401, no 404, porque todo está protegido por defecto.

**Errores.** `GlobalExceptionHandler` tiene un manejador genérico de `Exception` que responde 500. La excepción de cada feature necesita su propio manejador ahí, o termina como 500. `ProblemDetail` agrega solo el campo `instance`; el `code`, el `path` y los `fieldErrors` del contrato se agregan con `setProperty`.

**Base de datos.** El esquema lo define Flyway; Hibernate solo valida (`ddl-auto: validate`). Una migración ya mergeada no se edita: el cambio va en la siguiente (`V2__...`). Los nombres de columnas se derivan solos de los campos de la entity (`createdAt` es `created_at`).

**WireMock y HTTP/2.** WireMock acepta pasar a HTTP/2 sobre HTTP y la cátedra real no (probado con `curl --http2`). En ese modo, los `POST` del cliente de Java se cortan (`RST_STREAM`). La cátedra simulada (`FakeCatedra.newContainerLikeTheCatedra`, y la de `CatedraHoldAdapterTest`) arranca con `FakeCatedra.HTTP_1_ONLY`, que es como se comporta la cátedra real.

**Cliente HTTP.** Los clientes REST usan `NoRetryRequestFactory` (Apache HttpClient 5 con `disableAutomaticRetries()`). Ni `HttpURLConnection` ni el cliente de `java.net.http` sirven: los dos repiten un GET cuando se corta la conexión (el segundo, con HTTP/1.1, que es lo que habla la cátedra real) y no se puede apagar en un solo cliente. WireMock habla HTTP/2 y no deja ver ese reintento, por eso `NoHiddenRetryTest` usa un socket que corta la conexión y cuenta cuántas veces llega el cliente.

**Docker.** Si se cambia código y se prueba con Compose, hay que pasar `--build`, o corre la imagen vieja.

**Terminal.** La shell es zsh: una variable con espacios no se separa en palabras como en bash. En un script que encadena pasos, cortar ante el primer error (`set -e` o `&&`): un paso que falla no tiene que dejar seguir a uno que cierra o borra algo.

**Kafka en las pruebas.** Un solo contenedor `apache/kafka` (`FakeKafka`) para toda la ejecución, no uno por contexto. `TestCatedraConfiguration` anuncia su dirección en la integración falsa y le da a cada contexto su propio consumer group: contextos con el mismo grupo se repartirían la partición y un evento podría llegar al contexto equivocado. Como el consumidor lee desde el principio (`earliest`), un mock compartido por toda la clase ve también los eventos de las otras pruebas: verificar siempre por el `reservationProcessId` propio. `src/test/resources/application.properties` apaga la reconciliación (`reconciliation-enabled=false`): la prueba que la necesita llama a la fachada o la activa con `properties`.

**Kafka 4.** `linger.ms` vale 5 por defecto y `delivery.timeout.ms` tiene que ser mayor o igual que `linger.ms + request.timeout.ms`: el productor fija `linger.ms=0`. `KafkaTemplate.send` lanza `org.springframework.kafka.KafkaException` de forma sincrónica cuando no puede ni armar el envío (distinta de la `KafkaException` del cliente, que llega dentro del futuro): hay que atrapar las dos.

**Terminal con zsh.** `$VAR:s` es un modificador de zsh (`git show "$R:src/..."` falla): usar `"${R}:src/..."`.

## Secretos

- `.env` y `secrets/` están fuera de Git. No se commitean ni se muestran.
- En la carpeta que contiene a los repos hay dos archivos de la cuenta técnica de la cátedra, `cuenta-tecnica.json` y `cuenta-tecnica-respuesta.json`. **No abrirlos, no mencionarlos con `@` y no copiar su contenido:** tienen la contraseña, el JWT técnico y la contraseña de Redis. Si hace falta pasar un valor a un `.env`, se hace con un script que no lo imprima, avisándole antes al alumno.
- `snapshot.json`, en esa misma carpeta, sí se puede leer: es una copia del catálogo de la cátedra, con datos ficticios.
- La dirección y los puertos del servidor de la cátedra no se escriben en el código, en la documentación ni en los mensajes de commit.

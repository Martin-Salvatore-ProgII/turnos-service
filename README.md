# turnos-service

Servicio de turnos y reservas del proyecto integrador 2026. Registra a los usuarios finales, arma la disponibilidad de turnos y maneja el proceso de reserva contra el servicio de la cátedra.

Es uno de los tres repositorios de la entrega, junto con `catalogo-service` y `app-kmp`. Los requisitos, la arquitectura, los contratos y las decisiones (ADR) están en el repositorio [`docs`](https://github.com/Martin-Salvatore-ProgII/docs).

## Estado

Registro e inicio de sesión de usuarios finales (`POST /api/register` y `POST /api/authenticate`), sobre el esqueleto del servicio. Todo lo demás exige un JWT de usuario válido, y las rutas `/api/admin/**` exigen además `ROLE_ADMIN`. Al arrancar obtiene de la cátedra la configuración de la integración; sin ella no arranca. Disponibilidad de turnos de un profesional para una fecha (`GET /api/availability?professionalId=&date=`), armada con su agenda vigente (pedida al catálogo reenviando el JWT del usuario) y las ocupaciones de la cátedra. Inicio de una reserva (`POST /api/reservations`, con el profesional, la fecha y la hora): valida el turno con la agenda vigente, crea el hold en la cátedra y lo confirma, y responde la reserva en el estado en que quedó. Consulta de una reserva propia (`GET /api/reservations/{id}`), la que la app repite mientras el proceso avanza; la de otro usuario responde 404. Reserva por Kafka: el servicio consume el topic de acciones de la cátedra (pedido de teléfono, rechazo y eventos finales), recibe el teléfono por `POST /api/reservations/{id}/phone` y lo publica en el topic de teléfono, y una reconciliación periódica cierra los procesos que quedaron colgados. Listado de las reservas propias (`GET /api/reservations`, paginado de la más reciente a la más antigua y con filtro opcional por estado), respondido desde la base local aunque la cátedra o el catálogo no estén. Cancelación de una reserva propia confirmada (`POST /api/reservations/{id}/cancel`, con un motivo opcional): con el `200` de la cátedra queda cancelada, cancelar una ya cancelada responde éxito sin cambios, y un turno que ya empezó no se cancela (ADR-0071). El evento `AppointmentCancelled` también cancela una reserva confirmada. La reserva de otro usuario responde 404 en todas las operaciones. El administrador lista las reservas de todos los usuarios (`GET /api/admin/reservations`, paginado, con filtros opcionales por login del dueño, estado y rango de fechas del turno), con el dueño, el `reservationProcessId` y quién la canceló, y ve el detalle de cualquiera (`GET /api/admin/reservations/{id}`).

## Requisitos

- **Docker** con Docker Compose. Alcanza para levantar el sistema y para correr las pruebas.
- **Java 25**, solo para compilar o correr las pruebas fuera de Docker. Gradle no hace falta instalarlo: se usa el wrapper (`./gradlew`).
- **Acceso a la red de la cátedra**, para levantar el servicio: al arrancar le pide la configuración de la integración, y sin respuesta no arranca. Las pruebas no lo necesitan.
- **`catalogo-service` clonado al lado de este repo.** El `compose.yaml` de este repo levanta el sistema completo e incluye al del catálogo:

```
carpeta-del-proyecto/
├── catalogo-service/
└── turnos-service/
```

## Configuración

La configuración sale de variables de entorno. Para Docker Compose se leen de un archivo `.env`, que no se commitea. Hace falta uno en cada repo:

```
cp .env.example .env
cp ../catalogo-service/.env.example ../catalogo-service/.env
```

Variables de este repo:

| Variable | Para qué | Valor de ejemplo |
| --- | --- | --- |
| `TURNOS_DB_NAME` | Nombre de la base de turnos | `turnos` |
| `TURNOS_DB_USER` | Usuario de la base | `turnos` |
| `TURNOS_DB_PASSWORD` | Clave de la base. Cambiarla | `cambiar-esta-clave` |
| `TURNOS_PORT` | Puerto del servicio en la máquina | `8080` |
| `TURNOS_DB_PORT` | Puerto de PostgreSQL en la máquina | `5434` |
| `TURNOS_CATEDRA_API_URL` | URL base de la API de la cátedra. La misma que en el catálogo | La que indica la cátedra |
| `TURNOS_CATEDRA_TECHNICAL_TOKEN` | JWT técnico de la cuenta del proyecto ante la cátedra. Es un secreto. El mismo que en el catálogo | El `id_token` del registro de la cuenta técnica |
| `TURNOS_ADMIN_LOGIN` | Login del administrador inicial. Opcional | vacío |
| `TURNOS_ADMIN_PASSWORD` | Contraseña del administrador inicial, de 4 caracteres a 72 bytes. Opcional | vacío |
| `TURNOS_ADMIN_EMAIL` | Email del administrador inicial. Opcional | vacío |

Las variables llevan el prefijo `TURNOS_` para no repetir nombres con las del catálogo: al incluir su Compose, una variable repetida en este `.env` pisaría el valor del catálogo. Las del catálogo están documentadas en su README.

El servicio recibe la conexión en `DB_URL`, `DB_USER` y `DB_PASSWORD`; el Compose las arma a partir de las de arriba. La URL del catálogo (`CATALOG_API_URL`) también la fija el Compose, con la dirección interna del contenedor del catálogo; fuera de Compose hay que definirla. No hay valores por defecto: si falta alguna, el servicio no arranca.

### Integración con la cátedra

En el `.env` van solo la URL base y el JWT técnico. Kafka y sus topics no se configuran: al arrancar, el servicio los pide con `GET /api/student/integration` y los guarda solo en memoria (ADR-0005). Si la cátedra no responde (con un reintento), rechaza el JWT o la integración no está `PROVISIONED`, el servicio no arranca y lo informa en el log, sin mostrar valores.

Los timeouts hacia la cátedra tienen un valor inicial de 2 segundos para conectar y 5 para la respuesta (ADR-0029). Se pueden cambiar con `TURNOS_CATEDRA_CONNECT_TIMEOUT` y `TURNOS_CATEDRA_READ_TIMEOUT` (por ejemplo, `3s`).

La confirmación inicial de un hold y la cancelación de una reserva se reintentan hasta 2 veces si la cátedra no responde, esperando 500 ms y después 1 s (ADR-0029): las dos son seguras de repetir. Crear un hold nunca se reintenta (ADR-0028). Se pueden cambiar con `TURNOS_COMMAND_RETRY_RETRIES` y `TURNOS_COMMAND_RETRY_INITIAL_BACKOFF`.

### Kafka de la cátedra

Los servidores, el consumer group y los topics también salen de la integración que se pide al arrancar (ADR-0005): no hay propiedades `spring.kafka.*` ni topics en el `.env`.

- **Consumidor** del topic de acciones. Confirma el offset recién después de procesar el evento, también si el evento no se puede usar o falla tras los reintentos (ADR-0031). Sin offset guardado empieza por el principio del topic (`earliest`), a diferencia del catálogo: un evento de reserva perdido solo se recupera releyéndolo, y los repetidos se descartan por su `eventId` (ADR-0008).
- **Productor** del topic de teléfono: `acks=all`, sin reintentos propios (`retries=0`) y sin idempotencia del productor. Los únicos reintentos son los de ADR-0029. Todas sus esperas valen lo mismo que el timeout de respuesta de la cátedra (`TURNOS_CATEDRA_READ_TIMEOUT`).
- **Eventos procesados:** cada `eventId` se registra en `processed_event` junto con el cambio del proceso, en una sola transacción. Se conservan 7 días (`TURNOS_RESERVATION_PROCESSED_EVENT_RETENTION`) y se purgan en cada ciclo de la reconciliación.
- **Reintentos al procesar un evento:** 3, sin espera entre ellos (`TURNOS_RESERVATION_EVENT_RETRIES`). Agotados, se sigue y la reconciliación corrige el estado.
- **Reconciliación** (CU-05, ADR-0030): corre al arrancar y cada 60 segundos (`TURNOS_RESERVATION_RECONCILIATION_INTERVAL`), con 30 segundos de margen sobre el vencimiento (`TURNOS_RESERVATION_RECONCILIATION_MARGIN`). `TURNOS_RESERVATION_RECONCILIATION_ENABLED=false` la apaga. Consulta `GET /api/appointments` de la cátedra solo para los procesos con el teléfono enviado y para las reservas confirmadas con turno futuro.

La Kafka real de la cátedra no pide autenticación (se probó la conexión, la lectura de offsets y la forma de la integración). Al escribir esto, la cátedra real no publicaba nada en ningún topic, ni siquiera el pedido de teléfono tras una confirmación aceptada con `202`: el flujo completo está probado contra un Kafka simulado.

### Listas paginadas

`GET /api/reservations` recibe `page` (desde 0) y `size`, y devuelve el total en `X-Total-Count` y los enlaces a las páginas vecinas en `Link`, como la cátedra (contratos.md). El tamaño por defecto es 20 y el máximo 100 (RNF-09); se pueden cambiar con `TURNOS_PAGINATION_DEFAULT_SIZE` y `TURNOS_PAGINATION_MAX_SIZE`.

### Ocupaciones de la cátedra

Para armar la disponibilidad, turnos le pide a la cátedra las ocupaciones del profesional para la fecha consultada, un solo día por pedido (REF §8, ADR-0015). Si la cátedra no responde (con un reintento) o responde algo inesperado, turnos responde `503 INTEGRATION_UNAVAILABLE` y no muestra ningún slot.

### Servicio de catálogo

Turnos no guarda una copia del catálogo: antes de cada operación que necesita datos vigentes, le pide al catálogo la agenda del profesional (ADR-0017), reenviando el JWT del usuario que hizo el pedido (ADR-0034). Si el catálogo no responde (con un reintento), responde con un error o todavía no tiene una versión local, turnos responde `503 CATALOG_UNAVAILABLE`. Los timeouts son los mismos que hacia la cátedra y se pueden cambiar con `TURNOS_CATALOG_CONNECT_TIMEOUT` y `TURNOS_CATALOG_READ_TIMEOUT`.

### Clave del JWT de usuario

Este servicio firma el JWT de los usuarios con una clave privada RSA, que se lee de `secrets/jwt-private.pem`. La carpeta `secrets/` no se commitea. La clave se genera una sola vez:

```
mkdir -p secrets
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out secrets/jwt-private.pem
```

Docker Compose monta esa carpeta en el contenedor en solo lectura. Sin ese archivo, el servicio no arranca.

La clave pública no se guarda aparte: el servicio la calcula a partir de la privada. Cambiar la clave invalida todos los tokens ya emitidos.

### Administrador

El administrador no se registra desde la app: lo crea el propio servicio al arrancar, con las credenciales indicadas en `TURNOS_ADMIN_LOGIN`, `TURNOS_ADMIN_PASSWORD` y `TURNOS_ADMIN_EMAIL`. No hay credenciales de administrador en el repositorio. Después inicia sesión como cualquier usuario, y además de `ROLE_ADMIN` tiene `ROLE_USER`.

- Los tres valores van juntos. Si falta alguno, el servicio no arranca y lo indica en el log.
- Con los tres vacíos no hay administrador: si existía uno, pierde el rol y queda como usuario común.
- La configuración manda: al arrancar, el único administrador es la cuenta con ese login **y** esa contraseña.
- El registro es público, así que alguien podría haber registrado antes ese mismo login. En ese caso la cuenta no recibe el rol, porque no tiene la contraseña configurada, y el servicio lo avisa en el log: hay que elegir otro login.
- Un token emitido antes de un cambio conserva sus roles hasta que vence.

## Arranque

### El sistema completo

```
docker compose up --build
```

Construye las dos imágenes y levanta cuatro contenedores: `turnos-service` con `turnos-db`, y `catalogo-service` con `catalogo-db`. Cada servicio tiene su propia base PostgreSQL 18, con su usuario y su volumen.

Para verificar que los dos servicios están en pie:

```
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
```

Cada uno tiene que responder `{"status":"UP"}` (con el detalle de los grupos de salud).

Para detenerlo:

```
docker compose down
```

Agregando `-v` se borran también los volúmenes con los datos de las bases.

### Solo el servicio de turnos

```
docker compose up --build turnos-service
```

Levanta `turnos-service` y `turnos-db`, sin el catálogo. Igual hace falta tener `catalogo-service` clonado al lado, con su `.env`.

### Sin Docker Compose

Para probar el servicio a mano sin configurar nada, con una base descartable y una cátedra simulada (no hace falta la red de la cátedra):

```
./gradlew bootTestRun
```

Necesita Docker en ejecución; los datos se pierden al detenerlo.

## Pruebas

```
./gradlew test
```

Las pruebas usan PostgreSQL real y una cátedra y un catálogo simulados con WireMock, todos en contenedores de Testcontainers, así que **Docker tiene que estar corriendo**. Ninguna prueba habla con la cátedra ni con el catálogo reales. No hay que levantar nada a mano ni definir variables. El detalle de qué prueba cada cosa está en [`src/test/README.md`](src/test/README.md).

Las mismas pruebas corren en GitHub Actions en cada pull request hacia `develop` o `main` (`.github/workflows/ci.yml`).

## Estructura

El código sigue la arquitectura hexagonal de la cátedra: un paquete por feature con las capas `domain`, `application` e `infrastructure`, y un paquete `shared` para lo transversal. Cada paquete tiene un `package-info.java` que explica qué va ahí. La regla de dependencias entre capas, y que una feature solo use el `domain` de otra (ADR-0062), las verifica `ArchitectureTest`.

```
com.example.turnos
├── user/          feature de usuarios finales
├── catalog/       la agenda vigente pedida al servicio de catálogo (sin casos de uso: la usan otras features por su puerto)
├── availability/  disponibilidad de turnos: el cálculo, los slots y el cliente de ocupaciones de la cátedra
├── reservation/   el proceso de reserva: su tabla, sus estados, el inicio de la reserva (un proceso activo por usuario), el listado y la cancelación de las reservas propias, el cliente de holds, reservas y cancelación de la cátedra, el consumidor del topic de acciones, el productor del teléfono y la reconciliación
└── shared/        configuración (cliente e integración de la cátedra, seguridad) y manejo de errores comunes
```

### Piezas de `shared` y quién las usa

Lo que sirve a más de una feature se lleva a `shared` en cuanto aparece el segundo uso (ADR-0063).

| Pieza | Qué hace | La usan |
| --- | --- | --- |
| `config/CatedraRestClientConfig` | Cliente HTTP de la cátedra, con el JWT técnico y los timeouts | Integración al arrancar, `availability` (ocupaciones), `reservation` (holds, reservas y cancelación) |
| `rest/RemoteReadRetry` | El reintento de toda lectura a un servicio externo: un intento más si no responde o falla de su lado (ADR-0029), con mensajes sin su dirección | `CatedraReadExecutor`, `catalog` |
| `rest/RemoteCommandRetry` | El reintento de las operaciones con efectos seguras de repetir: hasta 2 reintentos con espera creciente (ADR-0029), configurables. También repite una falla pasajera que no es HTTP (`TransientRemoteFailureException`) | `reservation` (confirmación inicial, publicación del teléfono y cancelación) |
| `rest/CatedraReadExecutor` | Lectura a la cátedra: el reintento de `RemoteReadRetry` y los errores propios de la cátedra | Integración al arrancar, `availability` (ocupaciones), `reservation` (reservas de la cátedra) |
| `rest/ProblemDto` | Lee el `code` de un error `problem+json` del catálogo o de la cátedra | `catalog`, `reservation` (holds y cancelación) |
| `rest/NoRetryRequestFactory` | La conexión HTTP de los clientes de la cátedra y del catálogo: timeouts configurados y sin reintentos propios (ADR-0029) | `CatedraRestClientConfig`, `CatalogRestClientConfig` |
| `rest/HttpTimeouts` | Distingue el timeout de respuesta (la operación pudo hacerse) del de conexión (nunca salió) | `reservation` (holds) |
| `config/CatalogRestClientConfig` | Cliente HTTP del catálogo, con los timeouts y el JWT del usuario reenviado (ADR-0034) | `catalog` |
| `config/CatedraIntegrationConfig` | Pide la integración al arrancar; sin ella, el servicio no arranca (ADR-0005) | `config/KafkaConfig` |
| `config/KafkaConfig` | Consumidor y productor de Kafka con los servidores y el grupo de la integración (ADR-0005, ADR-0029, ADR-0031) | `reservation` (consumidor de acciones, productor del teléfono) |
| `config/SchedulingConfig` | Activa `@Scheduled` | `reservation` (reconciliación) |
| `config/JwtConfig`, `config/SecurityConfig` | Emisión y validación del JWT de usuario, y reglas de acceso | Todos los endpoints |
| `web/advice/GlobalExceptionHandler` | Errores `problem+json`; todo 400 sin código propio es `VALIDATION_ERROR` | Todos los endpoints, `reservation` (`INVALID_PHONE_NUMBER`, `PHONE_NOT_REQUESTED`, `RESERVATION_NOT_CANCELLABLE`, `503 INTEGRATION_UNAVAILABLE`), `catalog` (`503 CATALOG_UNAVAILABLE` y el 401 de un token rechazado), `availability` (`DATE_OUT_OF_RANGE`, `PROFESSIONAL_DISABLED`, `PROFESSIONAL_NOT_FOUND`, `503 INTEGRATION_UNAVAILABLE`) |
| `config/ClockConfig` | La hora como `Clock` inyectado, para poder fijarla en las pruebas | `availability`, `reservation` |
| `config/AvailabilityConfig` | Pasa al dominio el horizonte de fechas configurable (ADR-0065, RNF-09) | `availability`, `reservation` |
| `web/validation/MaxBytes` | Límite de largo en bytes de un campo de texto (ADR-0057) | `user` (contraseña) |
| `web/pagination/PaginationHeaders`, `web/pagination/MaxPageSize`, `config/PaginationConfig` | Las cabeceras `X-Total-Count` y `Link` de una lista y el tamaño de página máximo configurable (contratos.md, RNF-09) | `reservation` (listado) |

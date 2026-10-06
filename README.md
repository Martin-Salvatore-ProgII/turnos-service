# turnos-service

Servicio de turnos y reservas del proyecto integrador 2026. Registra a los usuarios finales, arma la disponibilidad de turnos y maneja el proceso de reserva contra el servicio de la cátedra.

Es uno de los tres repositorios de la entrega, junto con `catalogo-service` y `app-kmp`. Los requisitos, la arquitectura, los contratos y las decisiones (ADR) están en el repositorio [`docs`](https://github.com/Martin-Salvatore-ProgII/docs).

## Estado

Esqueleto del servicio: arranca, se conecta a su base PostgreSQL, ejecuta las migraciones de Flyway y responde los errores en `application/problem+json`. Todavía no tiene endpoints de negocio, usuarios ni integración con la cátedra.

## Requisitos

- **Docker** con Docker Compose. Alcanza para levantar el sistema y para correr las pruebas.
- **Java 25**, solo para compilar o correr las pruebas fuera de Docker. Gradle no hace falta instalarlo: se usa el wrapper (`./gradlew`).
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

Las variables llevan el prefijo `TURNOS_` para no repetir nombres con las del catálogo: al incluir su Compose, una variable repetida en este `.env` pisaría el valor del catálogo. Las del catálogo están documentadas en su README.

El servicio recibe la conexión en `DB_URL`, `DB_USER` y `DB_PASSWORD`; el Compose las arma a partir de las de arriba. No hay valores por defecto: si falta alguna, el servicio no arranca.

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

Para probar el servicio a mano sin configurar nada, con una base descartable:

```
./gradlew bootTestRun
```

Necesita Docker en ejecución; los datos se pierden al detenerlo.

## Pruebas

```
./gradlew test
```

Las pruebas usan PostgreSQL real con Testcontainers, así que **Docker tiene que estar corriendo**. No hay que levantar nada a mano ni definir variables. El detalle de qué prueba cada cosa está en [`src/test/README.md`](src/test/README.md).

Las mismas pruebas corren en GitHub Actions en cada pull request hacia `develop` o `main` (`.github/workflows/ci.yml`).

## Estructura

El código sigue la arquitectura hexagonal de la cátedra: un paquete por feature con las capas `domain`, `application` e `infrastructure`, y un paquete `shared` para lo transversal. Cada paquete tiene un `package-info.java` que explica qué va ahí. La regla de dependencias entre capas la verifica `ArchitectureTest`.

```
com.example.turnos
├── user/          feature de usuarios finales
└── shared/        configuración, seguridad y manejo de errores comunes
```

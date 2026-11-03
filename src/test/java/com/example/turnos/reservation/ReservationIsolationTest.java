package com.example.turnos.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.example.turnos.FakeCatalog;
import com.example.turnos.FakeCatedra;
import com.example.turnos.FakeServer;
import com.example.turnos.TestCatalogConfiguration;
import com.example.turnos.TestCatedraConfiguration;
import com.example.turnos.TestClockConfiguration;
import com.example.turnos.TestJwtKeysConfiguration;
import com.example.turnos.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;

/**
 * Evidencia "consulta y cancelación propia sin acceso cruzado" (ENUNCIADO §11, HU-08, HU-09, RNF-03): dos
 * usuarios registrados de verdad, con la seguridad activa; uno intenta ver, listar y cancelar la reserva
 * del otro y recibe lo mismo que si no existiera. El usuario sale siempre del JWT: ninguna operación
 * recibe un usuario del cliente. Cada prueba usa usuarios nuevos: el contexto y la base se comparten.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestJwtKeysConfiguration.class, TestCatedraConfiguration.class,
		TestCatalogConfiguration.class, TestClockConfiguration.class })
class ReservationIsolationTest {

	private static final String NEXT_MONDAY = "2026-11-09";
	private static final String HOLDS_PATH = "/api/appointment-holds";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private GenericContainer<?> catalogContainer;

	@Autowired
	private GenericContainer<?> catedraContainer;

	@Autowired
	private JdbcClient jdbcClient;

	@BeforeEach
	void setUp() {
		FakeServer.reset(catalogContainer);
		FakeServer.reset(catedraContainer);
		FakeServer.stub(catalogContainer,
				FakeCatalog.agendaMapping(204, 200, FakeServer.readResource(FakeCatalog.AGENDA_204)).toString());
	}

	// HU-08, criterios 1 y 3: cada uno ve solo las suyas, y la lista sale de la base local, sin pedirle
	// nada al catálogo ni a la cátedra (ADR-0025).
	@Test
	void eachUserListsOnlyTheirOwnReservations() throws Exception {
		String owner = newUserToken();
		String intruder = newUserToken();
		String ownerReservation = confirmedReservation(owner, "11:20");
		String intruderReservation = confirmedReservation(intruder, "12:00");
		FakeServer.reset(catalogContainer);
		FakeServer.reset(catedraContainer);

		listReservations(owner, "")
				.andExpect(status().isOk())
				.andExpect(header().string("X-Total-Count", "1"))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(ownerReservation));
		// Ni con el filtro por estado aparece la del otro.
		listReservations(intruder, "?status=CONFIRMED")
				.andExpect(status().isOk())
				.andExpect(header().string("X-Total-Count", "1"))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(intruderReservation));
		assertThat(FakeServer.countRequestsToPath(catalogContainer, "GET", FakeCatalog.agendaPath(204))).isZero();
		assertThat(FakeServer.countRequestsToPath(catedraContainer, "GET", FakeCatedra.APPOINTMENTS_PATH)).isZero();
	}

	@Test
	void aUserWithoutReservationsGetsAnEmptyList() throws Exception {
		confirmedReservation(newUserToken(), "11:20");

		listReservations(newUserToken(), "")
				.andExpect(status().isOk())
				.andExpect(header().string("X-Total-Count", "0"))
				.andExpect(jsonPath("$", hasSize(0)));
	}

	// HU-08, criterio 2: ver la reserva ajena da el mismo 404 que una que no existe.
	@Test
	void seeingTheReservationOfAnotherUserIsLikeOneThatDoesNotExist() throws Exception {
		String owner = newUserToken();
		String intruder = newUserToken();
		String id = confirmedReservation(owner, "11:20");

		getReservation(intruder, id)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
		getReservation(intruder, UUID.randomUUID().toString())
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
	}

	// HU-09, criterio 4: cancelar la reserva ajena da el mismo 404, no se le pide nada a la cátedra y la
	// reserva del dueño sigue confirmada.
	@Test
	void cancellingTheReservationOfAnotherUserIsLikeOneThatDoesNotExistAndChangesNothing() throws Exception {
		String owner = newUserToken();
		String intruder = newUserToken();
		String id = confirmedReservation(owner, "11:20");

		cancelReservation(intruder, id)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
		cancelReservation(intruder, UUID.randomUUID().toString())
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));

		getReservation(owner, id)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CONFIRMED"));
		assertThat(FakeServer.countRequests(catedraContainer, "POST", cancelUrlOf(id))).isZero();
	}

	@Test
	void withoutTokenNothingIsListedOrCancelled() throws Exception {
		mockMvc.perform(get("/api/reservations")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/reservations/" + UUID.randomUUID() + "/cancel"))
				.andExpect(status().isUnauthorized());
	}

	// Una reserva iniciada por la API y llevada a CONFIRMED con SQL: la confirmación por Kafka ya la prueba
	// ReservationEventFlowTest, y acá solo interesa quién puede verla y cancelarla.
	private String confirmedReservation(String token, String startTime) throws Exception {
		String holdId = "hold-" + UUID.randomUUID();
		FakeServer.stub(catedraContainer, FakeCatedra.postMapping(HOLDS_PATH, 201, """
				{ "holdId": "%s", "reservationProcessId": "process-%s", "status": "HELD",
				  "expiresAt": "2026-11-02T15:40:00Z" }
				""".formatted(holdId, holdId)).toString());
		FakeServer.stub(catedraContainer, FakeCatedra.postMapping(FakeCatedra.confirmUrl(holdId), 202, """
				{ "reservationProcessId": "process-%s", "status": "WAITING_FOR_PHONE",
				  "expiresAt": "2026-11-02T15:41:00Z" }
				""".formatted(holdId)).toString());
		String response = mockMvc.perform(post("/api/reservations")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"professionalId\":204,\"date\":\"%s\",\"startTime\":\"%s\"}".formatted(NEXT_MONDAY,
								startTime)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(response, "$.id");
		jdbcClient.sql("UPDATE reservation_process SET status = 'CONFIRMED', confirmed_at = now() WHERE id = :id")
				.param("id", UUID.fromString(id)).update();
		return id;
	}

	private String cancelUrlOf(String id) {
		String reservationProcessId = jdbcClient
				.sql("SELECT reservation_process_id FROM reservation_process WHERE id = :id")
				.param("id", UUID.fromString(id)).query(String.class).single();
		return "/api/appointments/" + reservationProcessId + "/cancel";
	}

	private ResultActions listReservations(String token, String query) throws Exception {
		return mockMvc.perform(get("/api/reservations" + query).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private ResultActions getReservation(String token, String id) throws Exception {
		return mockMvc.perform(get("/api/reservations/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private ResultActions cancelReservation(String token, String id) throws Exception {
		return mockMvc.perform(post("/api/reservations/" + id + "/cancel")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private String newUserToken() throws Exception {
		String login = "u" + UUID.randomUUID().toString().substring(0, 8);
		mockMvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content("""
						{"login":"%s","password":"clave-segura","firstName":"Juan","lastName":"Perez",
						 "email":"%s@example.com","langKey":"es"}
						""".formatted(login, login)))
				.andExpect(status().isCreated());
		String response = mockMvc.perform(post("/api/authenticate").contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"%s\",\"password\":\"clave-segura\"}".formatted(login)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.id_token");
	}

}

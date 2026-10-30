package com.example.turnos.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;

/**
 * Recorrido de CU-04 hasta la confirmación inicial, con la aplicación entera y la seguridad activa: el
 * usuario se registra e inicia sesión de verdad, y el catálogo y la cátedra son falsos (WireMock). Las
 * respuestas de la cátedra son las que dio la real con probar-reserva-catedra.sh. "Hoy" es el lunes
 * 2026-11-02 a las 12:30 de Argentina ({@link TestClockConfiguration}); Laura Medina (204) atiende los
 * lunes de 11 a 15 en slots de 20 minutos. Cada prueba usa usuarios nuevos: el contexto se comparte.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestJwtKeysConfiguration.class, TestCatedraConfiguration.class,
		TestCatalogConfiguration.class, TestClockConfiguration.class })
class ReservationFlowTest {

	private static final String NEXT_MONDAY = "2026-11-09";
	private static final String HOLDS_PATH = "/api/appointment-holds";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private GenericContainer<?> catalogContainer;

	@Autowired
	private GenericContainer<?> catedraContainer;

	@BeforeEach
	void setUp() {
		FakeServer.reset(catalogContainer);
		FakeServer.reset(catedraContainer);
		FakeServer.stub(catalogContainer,
				FakeCatalog.agendaMapping(204, 200, FakeServer.readResource(FakeCatalog.AGENDA_204)).toString());
	}

	@Test
	void startsTheReservationAndLeavesItAwaitingThePhoneRequest() throws Exception {
		String token = newUserToken();
		String holdId = stubHoldAndConfirmation();

		String id = JsonPath.read(startReservation(token, 204, NEXT_MONDAY, "11:20")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("AWAITING_REQUEST"))
				.andExpect(jsonPath("$.professionalId").value(204))
				.andExpect(jsonPath("$.professionalFirstName").value("Laura"))
				.andExpect(jsonPath("$.professionalLastName").value("Medina"))
				.andExpect(jsonPath("$.date").value(NEXT_MONDAY))
				.andExpect(jsonPath("$.startTime").value("11:20:00"))
				.andExpect(jsonPath("$.endTime").value("11:40:00"))
				.andExpect(jsonPath("$.expiresAt").value("2026-11-02T15:41:00Z"))
				.andExpect(jsonPath("$.failureReason").doesNotExist())
				.andExpect(jsonPath("$.createdAt").exists())
				// Los identificadores de la cátedra no se exponen a la app (contratos.md).
				.andExpect(jsonPath("$.holdId").doesNotExist())
				.andExpect(jsonPath("$.reservationProcessId").doesNotExist())
				.andReturn().getResponse().getContentAsString(), "$.id");

		// La app sigue el proceso por su id (ADR-0021).
		getReservation(token, id)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.status").value("AWAITING_REQUEST"));
		assertThat(FakeServer.countRequests(catedraContainer, "POST", FakeCatedra.confirmUrl(holdId))).isEqualTo(1);
	}

	@Test
	void aReservationOfAnotherUserRespondsLikeOneThatDoesNotExist() throws Exception {
		String owner = newUserToken();
		String intruder = newUserToken();
		stubHoldAndConfirmation();
		String id = JsonPath.read(startReservation(owner, 204, NEXT_MONDAY, "11:20")
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(), "$.id");

		// HU-08, criterio 2: el mismo 404 que para un id que no existe.
		getReservation(intruder, id)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
		getReservation(intruder, UUID.randomUUID().toString())
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
	}

	@Test
	void aSecondReservationWhileOneIsInProgressIsRejected() throws Exception {
		String token = newUserToken();
		stubHoldAndConfirmation();
		startReservation(token, 204, NEXT_MONDAY, "11:20").andExpect(status().isCreated());

		startReservation(token, 204, NEXT_MONDAY, "12:00")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ACTIVE_RESERVATION_EXISTS"));
		// Rechazada antes de pedir un segundo hold (CU-04, 2a).
		assertThat(FakeServer.countRequests(catedraContainer, "POST", HOLDS_PATH)).isEqualTo(1);
	}

	@Test
	void aSlotTakenByAnotherEndsFailedAndTheUserCanTryAgain() throws Exception {
		String token = newUserToken();
		FakeServer.stub(catedraContainer, FakeCatedra.postMapping(HOLDS_PATH, 409, """
				{ "status": 409, "code": "SLOT_ALREADY_HELD", "detail": "Slot already held" }
				""").toString());

		// ADR-0069: la reserva existe y llega con su motivo, como un 201.
		startReservation(token, 204, NEXT_MONDAY, "11:20")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("FAILED"))
				.andExpect(jsonPath("$.failureReason").value("SLOT_ALREADY_HELD"));

		// Un proceso fallido no cuenta como reserva en curso.
		FakeServer.reset(catedraContainer);
		stubHoldAndConfirmation();
		startReservation(token, 204, NEXT_MONDAY, "12:00")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("AWAITING_REQUEST"));
	}

	@Test
	void rejectsASlotThatIsNotInTheAgendaWithoutCallingTheCatedra() throws Exception {
		startReservation(newUserToken(), 204, NEXT_MONDAY, "11:30")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_SLOT"));
		assertThat(FakeServer.countRequests(catedraContainer, "POST", HOLDS_PATH)).isZero();
	}

	@Test
	void doesNotStartTheReservationWhenTheCatalogIsUnavailable() throws Exception {
		FakeServer.reset(catalogContainer);
		FakeServer.stub(catalogContainer, FakeCatalog.agendaMapping(204, 503, "").toString());

		startReservation(newUserToken(), 204, NEXT_MONDAY, "11:20")
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"));
		assertThat(FakeServer.countRequests(catedraContainer, "POST", HOLDS_PATH)).isZero();
	}

	@Test
	void rejectsRequestsWithoutToken() throws Exception {
		mockMvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON)
						.content(reservationBody(204, NEXT_MONDAY, "11:20")))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/reservations/" + UUID.randomUUID()))
				.andExpect(status().isUnauthorized());
		assertThat(FakeServer.countRequests(catedraContainer, "POST", HOLDS_PATH)).isZero();
	}

	// Las respuestas de la cátedra real (REF §9, §10): el hold con 10 minutos de vigencia y la
	// confirmación con el vencimiento del proceso. Los identificadores son nuevos en cada llamada: las
	// pruebas comparten la base, y reservationProcessId es único (como en la cátedra).
	private String stubHoldAndConfirmation() {
		String holdId = "hold-" + UUID.randomUUID();
		FakeServer.stub(catedraContainer, FakeCatedra.postMapping(HOLDS_PATH, 201, """
				{ "holdId": "%s", "reservationProcessId": "process-%s", "status": "HELD",
				  "expiresAt": "2026-11-02T15:40:00Z" }
				""".formatted(holdId, holdId)).toString());
		FakeServer.stub(catedraContainer, FakeCatedra.postMapping(FakeCatedra.confirmUrl(holdId), 202, """
				{ "reservationProcessId": "process-%s", "status": "WAITING_FOR_PHONE",
				  "expiresAt": "2026-11-02T15:41:00Z" }
				""".formatted(holdId)).toString());
		return holdId;
	}

	private ResultActions startReservation(String token, long professionalId, String date, String startTime)
			throws Exception {
		return mockMvc.perform(post("/api/reservations")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(reservationBody(professionalId, date, startTime)));
	}

	private ResultActions getReservation(String token, String id) throws Exception {
		return mockMvc.perform(get("/api/reservations/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private static String reservationBody(long professionalId, String date, String startTime) {
		return """
				{"professionalId":%d,"date":"%s","startTime":"%s"}
				""".formatted(professionalId, date, startTime);
	}

	// Un usuario nuevo, registrado e iniciado de verdad: el caso de uso lo busca en la base por el login
	// del JWT.
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

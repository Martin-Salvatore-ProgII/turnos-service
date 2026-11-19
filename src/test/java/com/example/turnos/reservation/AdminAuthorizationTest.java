package com.example.turnos.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Base64;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;

/**
 * Evidencia "rechazo de accesos no autorizados" para las operaciones administrativas (ENUNCIADO §9,
 * §11; HU-10, criterios 1 a 6): con la aplicación entera, la seguridad activa y usuarios registrados de
 * verdad. Un usuario común recibe 403 en las tres rutas de /api/admin/** y no cambia nada; el
 * administrador lista, ve y cancela reservas ajenas; y la existencia del rol no abre las rutas de usuario:
 * ahí el administrador es un usuario más (ADR-0037).
 *
 * <p>El administrador se configura como lo haría el {@code .env}: el servicio lo crea al arrancar
 * (ADR-0061). Cada prueba usa usuarios nuevos: el contexto y la base se comparten.
 */
@SpringBootTest(properties = {
		"turnos.security.admin.login=admin",
		"turnos.security.admin.password=clave-de-admin",
		"turnos.security.admin.email=admin@example.com" })
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestJwtKeysConfiguration.class, TestCatedraConfiguration.class,
		TestCatalogConfiguration.class, TestClockConfiguration.class })
class AdminAuthorizationTest {

	private static final String NEXT_MONDAY = "2026-11-09";
	private static final String HOLDS_PATH = "/api/appointment-holds";
	private static final String REASON = "{\"reason\":\"Pedido por soporte\"}";

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

	// HU-10, criterio 5: 403 en las tres operaciones, también sobre una reserva propia, y nada cambia.
	@Test
	void aCommonUserIsForbiddenInEveryAdminRouteAndNothingChanges() throws Exception {
		String user = newUserToken();
		String id = confirmedReservation(user, "11:20");

		as(user, get("/api/admin/reservations")).andExpect(status().isForbidden());
		as(user, get("/api/admin/reservations/" + id)).andExpect(status().isForbidden());
		as(user, post("/api/admin/reservations/" + id + "/cancel").contentType(MediaType.APPLICATION_JSON)
				.content(REASON)).andExpect(status().isForbidden());

		as(user, get("/api/reservations/" + id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CONFIRMED"));
		assertThat(FakeServer.countRequests(catedraContainer, "POST", cancelUrlOf(id))).isZero();
	}

	@Test
	void withoutTokenEveryAdminRouteIsUnauthorized() throws Exception {
		String id = UUID.randomUUID().toString();
		mockMvc.perform(get("/api/admin/reservations")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/admin/reservations/" + id)).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/admin/reservations/" + id + "/cancel").contentType(MediaType.APPLICATION_JSON)
				.content(REASON)).andExpect(status().isUnauthorized());
	}

	// HU-10, criterios 1 y 2: las reservas de todos, con su dueño; con el filtro, solo las de ese login.
	@Test
	void theAdministratorListsAndSeesTheReservationsOfEveryUser() throws Exception {
		String ana = newUserToken();
		String luis = newUserToken();
		String anasReservation = confirmedReservation(ana, "11:20");
		String luisReservation = confirmedReservation(luis, "12:00");
		String admin = adminToken();

		as(admin, get("/api/admin/reservations?size=100"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItems(anasReservation, luisReservation)));
		as(admin, get("/api/admin/reservations?ownerLogin=" + loginOf(ana)))
				.andExpect(status().isOk())
				.andExpect(header().string("X-Total-Count", "1"))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(anasReservation))
				.andExpect(jsonPath("$[0].owner.login").value(loginOf(ana)));
		as(admin, get("/api/admin/reservations/" + luisReservation))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.owner.login").value(loginOf(luis)))
				.andExpect(jsonPath("$.reservationProcessId").isNotEmpty());
	}

	// HU-10, criterios 3 y 4: sin motivo no se cancela; con motivo, queda cancelada a nombre del
	// administrador, y el dueño la ve cancelada.
	@Test
	void theAdministratorCancelsTheReservationOfAnotherUserOnlyWithAReason() throws Exception {
		String owner = newUserToken();
		String id = confirmedReservation(owner, "11:20");
		String cancelUrl = cancelUrlOf(id);
		FakeServer.stub(catedraContainer, FakeCatedra.postMapping(cancelUrl, 200, """
				{ "reservationId": 8251, "status": "CANCELLED", "cancelledAt": "2026-11-02T15:35:00Z" }
				""").toString());
		String admin = adminToken();

		as(admin, post("/api/admin/reservations/" + id + "/cancel").contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		assertThat(FakeServer.countRequests(catedraContainer, "POST", cancelUrl)).isZero();

		as(admin, post("/api/admin/reservations/" + id + "/cancel").contentType(MediaType.APPLICATION_JSON)
				.content(REASON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"))
				.andExpect(jsonPath("$.cancellationReason").value("Pedido por soporte"))
				.andExpect(jsonPath("$.cancelledBy").value("admin"))
				.andExpect(jsonPath("$.owner.login").value(loginOf(owner)));
		as(owner, get("/api/reservations/" + id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));
		assertThat(FakeServer.countRequests(catedraContainer, "POST", cancelUrl)).isEqualTo(1);
	}

	// HU-10, criterio 6: las rutas de usuario no miran el rol. Por ahí, el administrador tampoco ve ni
	// cancela reservas ajenas: para eso están las rutas administrativas.
	@Test
	void theUserRoutesDoNotOpenForTheAdministrator() throws Exception {
		String owner = newUserToken();
		String id = confirmedReservation(owner, "11:20");
		String admin = adminToken();

		as(admin, get("/api/reservations/" + id))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
		as(admin, post("/api/reservations/" + id + "/cancel"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
		as(admin, get("/api/reservations?size=100"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == '" + id + "')]", hasSize(0)));
		assertThat(FakeServer.countRequests(catedraContainer, "POST", cancelUrlOf(id))).isZero();
	}

	// Una reserva iniciada por la API y llevada a CONFIRMED con SQL, como en ReservationIsolationTest: la
	// confirmación por Kafka ya la prueba ReservationEventFlowTest.
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
		String response = as(token, post("/api/reservations").contentType(MediaType.APPLICATION_JSON)
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

	private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	// El login va en sub del JWT (arq/seguridad.md).
	private static String loginOf(String token) {
		String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
		return JsonPath.read(payload, "$.sub");
	}

	private String adminToken() throws Exception {
		return logIn("admin", "clave-de-admin");
	}

	private String newUserToken() throws Exception {
		String login = "u" + UUID.randomUUID().toString().substring(0, 8);
		mockMvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content("""
						{"login":"%s","password":"clave-segura","firstName":"Juan","lastName":"Perez",
						 "email":"%s@example.com","langKey":"es"}
						""".formatted(login, login)))
				.andExpect(status().isCreated());
		return logIn(login, "clave-segura");
	}

	private String logIn(String login, String password) throws Exception {
		String response = mockMvc.perform(post("/api/authenticate").contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(login, password)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.id_token");
	}

}

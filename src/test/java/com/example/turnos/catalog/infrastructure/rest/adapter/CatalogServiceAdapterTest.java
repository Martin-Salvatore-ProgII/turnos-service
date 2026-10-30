package com.example.turnos.catalog.infrastructure.rest.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;

import com.example.turnos.FakeCatalog;
import com.example.turnos.FakeServer;
import com.example.turnos.catalog.application.exception.CatalogTokenRejectedException;
import com.example.turnos.catalog.application.exception.CatalogUnavailableException;
import com.example.turnos.catalog.domain.model.ProfessionalAgenda;
import com.example.turnos.catalog.domain.model.WeeklySchedule;
import com.example.turnos.catalog.infrastructure.rest.mapper.ProfessionalAgendaMapper;
import com.example.turnos.shared.infrastructure.config.CatalogProperties;
import com.example.turnos.shared.infrastructure.config.CatalogRestClientConfig;
import com.example.turnos.shared.infrastructure.rest.RemoteReadRetry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.node.ObjectNode;

// El cliente del catálogo contra un catálogo falso (WireMock) que responde lo mismo que el real: las
// respuestas de la 204, la 206 y el 404 se copiaron de GET /api/professionals/{id}/agenda. Sin
// Spring: el cliente se arma a mano, como en producción. El JWT del usuario se pone en el
// SecurityContext, como lo deja Spring Security al validar el pedido.
class CatalogServiceAdapterTest {

	private static final String USER_TOKEN = "user-jwt-de-prueba";

	private static final GenericContainer<?> catalog = FakeServer.newContainer();

	@BeforeAll
	static void startCatalog() {
		catalog.start();
	}

	@AfterAll
	static void stopCatalog() {
		catalog.stop();
	}

	@BeforeEach
	void setUp() {
		FakeServer.reset(catalog);
		Jwt jwt = Jwt.withTokenValue(USER_TOKEN).header("alg", "RS256").subject("user").build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
	}

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void mapsTheRealAgendaOfAProfessional() {
		stubAgenda(204, 200, FakeServer.readResource(FakeCatalog.AGENDA_204));

		ProfessionalAgenda agenda = adapter().getProfessionalAgenda(204L).orElseThrow();

		assertThat(agenda.getId()).isEqualTo(204L);
		assertThat(agenda.getFirstName()).isEqualTo("Laura");
		assertThat(agenda.getLastName()).isEqualTo("Medina");
		assertThat(agenda.getCategory().getId()).isEqualTo(102L);
		assertThat(agenda.getCategory().getName()).isEqualTo("Pediatria");
		assertThat(agenda.isEnabled()).isTrue();
		assertThat(agenda.getCatalogVersion()).isEqualTo(1L);
		assertThat(agenda.getWeeklySchedules()).hasSize(2);
		assertSchedule(agenda.getWeeklySchedules().get(0), 306L, DayOfWeek.MONDAY, "11:00", "15:00", 20);
		assertSchedule(agenda.getWeeklySchedules().get(1), 307L, DayOfWeek.FRIDAY, "12:00", "16:00", 20);
	}

	@Test
	void returnsADisabledProfessionalAsTheCatalogSendsIt() {
		// Qué hacer con un profesional deshabilitado lo decide quien usa la agenda (contratos.md).
		stubAgenda(206, 200, FakeServer.readResource(FakeCatalog.AGENDA_206));

		ProfessionalAgenda agenda = adapter().getProfessionalAgenda(206L).orElseThrow();

		assertThat(agenda.isEnabled()).isFalse();
		assertThat(agenda.getWeeklySchedules()).isEmpty();
	}

	@Test
	void forwardsTheUserTokenAsBearer() {
		// El catálogo falso solo responde si llega el encabezado exacto; si no, contesta 404 sin code.
		ObjectNode mapping = FakeCatalog.agendaMapping(204, 200, FakeServer.readResource(FakeCatalog.AGENDA_204));
		((ObjectNode) mapping.get("request")).putObject("headers")
				.putObject("Authorization")
				.put("equalTo", "Bearer " + USER_TOKEN);
		FakeServer.stub(catalog, mapping.toString());

		assertThat(adapter().getProfessionalAgenda(204L)).isPresent();
	}

	@Test
	void acceptsTimesWithoutSecondsAndIgnoresUnknownFields() {
		stubAgenda(204, 200, """
				{ "id": 204, "firstName": "Laura", "lastName": "Medina", "category": { "id": 102, "name": "Pediatria" },
				  "enabled": true, "catalogVersion": 1, "campoNuevo": "x",
				  "weeklySchedules": [ { "id": 306, "dayOfWeek": "MONDAY", "startTime": "11:00", "endTime": "15:00",
				    "slotDurationMinutes": 20, "otroCampo": 1 } ] }
				""");

		ProfessionalAgenda agenda = adapter().getProfessionalAgenda(204L).orElseThrow();

		assertSchedule(agenda.getWeeklySchedules().get(0), 306L, DayOfWeek.MONDAY, "11:00", "15:00", 20);
	}

	@Test
	void returnsEmptyWhenTheProfessionalDoesNotExist() {
		stubAgenda(9999, 404, FakeServer.readResource(FakeCatalog.PROFESSIONAL_NOT_FOUND));

		assertThat(adapter().getProfessionalAgenda(9999L)).isEmpty();
		assertThat(requests(9999)).isEqualTo(1);
	}

	@Test
	void doesNotTakeA404WithoutTheCodeAsAMissingProfessional() {
		// Una ruta equivocada también da 404, pero no prueba que el profesional no exista.
		stubAgenda(204, 404, "");

		assertThatThrownBy(() -> adapter().getProfessionalAgenda(204L))
				.isInstanceOf(CatalogUnavailableException.class)
				.hasMessage("Unexpected response from the catalog: HTTP 404");
		assertThat(requests(204)).isEqualTo(1);
	}

	@Test
	void reportsTheRejectedTokenWithoutRetrying() {
		stubAgenda(204, 401, "");

		assertThatThrownBy(() -> adapter().getProfessionalAgenda(204L))
				.isInstanceOf(CatalogTokenRejectedException.class);
		assertThat(requests(204)).isEqualTo(1);
	}

	@Test
	void retriesOnceAndFailsWhenTheCatalogIsNotReady() {
		stubAgenda(204, 503, """
				{ "status": 503, "code": "CATALOG_NOT_READY", "detail": "Catalog not ready" }
				""");

		assertThatThrownBy(() -> adapter().getProfessionalAgenda(204L))
				.isInstanceOf(CatalogUnavailableException.class)
				.hasMessage("Catalog service unavailable (HTTP 503)");
		assertThat(requests(204)).isEqualTo(2);
	}

	@Test
	void retriesOnceAndFailsWhenTheCatalogDoesNotAnswerInTime() {
		ObjectNode mapping = FakeCatalog.agendaMapping(204, 200, FakeServer.readResource(FakeCatalog.AGENDA_204));
		((ObjectNode) mapping.get("response")).put("fixedDelayMilliseconds", 1500);
		FakeServer.stub(catalog, mapping.toString());

		assertThatThrownBy(() -> adapter().getProfessionalAgenda(204L))
				.isInstanceOf(CatalogUnavailableException.class)
				.hasMessageStartingWith("Catalog service unavailable");
		assertThat(requests(204)).isEqualTo(2);
	}

	@Test
	void failsWhenTheBodyCannotBeRead() {
		stubAgenda(204, 200, "no es json");

		assertThatThrownBy(() -> adapter().getProfessionalAgenda(204L))
				.isInstanceOf(CatalogUnavailableException.class)
				.hasMessage("Unexpected response from the catalog: unreadable body");
	}

	@Test
	void failsWhenNothingAnswersAtTheCatalogAddressWithoutShowingIt() throws IOException {
		int closedPort;
		try (ServerSocket socket = new ServerSocket(0)) {
			closedPort = socket.getLocalPort();
		}
		String address = "localhost:" + closedPort;

		assertThatThrownBy(() -> adapter("http://" + address).getProfessionalAgenda(204L))
				.isInstanceOf(CatalogUnavailableException.class)
				.hasMessageStartingWith("Catalog service unavailable")
				.hasMessageNotContaining(address);
	}

	@Test
	void doesNotCallTheCatalogWithoutAUserToken() {
		SecurityContextHolder.clearContext();

		assertThatThrownBy(() -> adapter().getProfessionalAgenda(204L))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("No user token to forward to the catalog");
		assertThat(requests(204)).isZero();
	}

	private static void assertSchedule(WeeklySchedule schedule, Long id, DayOfWeek day, String start, String end,
			int slotDurationMinutes) {
		assertThat(schedule.getId()).isEqualTo(id);
		assertThat(schedule.getDayOfWeek()).isEqualTo(day);
		assertThat(schedule.getStartTime()).isEqualTo(LocalTime.parse(start));
		assertThat(schedule.getEndTime()).isEqualTo(LocalTime.parse(end));
		assertThat(schedule.getSlotDurationMinutes()).isEqualTo(slotDurationMinutes);
	}

	private static void stubAgenda(long professionalId, int status, String body) {
		FakeServer.stub(catalog, FakeCatalog.agendaMapping(professionalId, status, body).toString());
	}

	private static int requests(long professionalId) {
		return FakeServer.countRequests(catalog, "GET", FakeCatalog.agendaPath(professionalId));
	}

	private static CatalogServiceAdapter adapter() {
		return adapter(FakeServer.baseUrl(catalog));
	}

	// Timeouts cortos para que la prueba de demora no tarde.
	private static CatalogServiceAdapter adapter(String baseUrl) {
		CatalogProperties properties = new CatalogProperties(baseUrl, Duration.ofSeconds(1), Duration.ofMillis(500));
		return new CatalogServiceAdapter(new CatalogRestClientConfig().catalogRestClient(properties),
				new RemoteReadRetry(), new ProfessionalAgendaMapper());
	}

}

package com.example.turnos.availability.infrastructure.rest.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.example.turnos.FakeCatedra;
import com.example.turnos.FakeServer;
import com.example.turnos.availability.application.exception.IntegrationUnavailableException;
import com.example.turnos.availability.domain.model.Occupancy;
import com.example.turnos.availability.domain.model.OccupancyStatus;
import com.example.turnos.availability.infrastructure.rest.mapper.OccupancyMapper;
import com.example.turnos.shared.infrastructure.config.CatedraProperties;
import com.example.turnos.shared.infrastructure.config.CatedraRestClientConfig;
import com.example.turnos.shared.infrastructure.rest.CatedraReadExecutor;
import com.example.turnos.shared.infrastructure.rest.RemoteReadRetry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.node.ObjectNode;

// Lo propio del cliente de ocupaciones: el pedido de un solo día, los campos de la REF §8 y que sin
// la lista no se siga. El reintento, los timeouts y los mensajes de falla son los de toda lectura a
// la cátedra y se prueban en CatedraReadExecutorTest (ADR-0063). Sin Spring, contra una cátedra
// falsa (WireMock). La cátedra real no tenía ocupaciones al escribir esta prueba: las respuestas con
// datos son el ejemplo de la REF, y la vacía es la que devolvió la cátedra.
class CatedraOccupancyAdapterTest {

	private static final GenericContainer<?> catedra = FakeServer.newContainer();

	@BeforeAll
	static void startCatedra() {
		catedra.start();
	}

	@AfterAll
	static void stopCatedra() {
		catedra.stop();
	}

	@BeforeEach
	void resetCatedra() {
		FakeServer.reset(catedra);
	}

	@Test
	void mapsTheOccupanciesOfTheReference() {
		stub(15, "2026-09-14", 200, FakeServer.readResource(FakeCatedra.OCCUPANCIES_REF));

		List<Occupancy> occupancies = adapter().getOccupancies(15L, LocalDate.parse("2026-09-14"));

		assertThat(occupancies).hasSize(2);
		Occupancy confirmed = occupancies.get(0);
		assertThat(confirmed.getDate()).isEqualTo(LocalDate.parse("2026-09-14"));
		assertThat(confirmed.getStartTime()).isEqualTo(LocalTime.parse("09:00"));
		assertThat(confirmed.getStatus()).isEqualTo(OccupancyStatus.CONFIRMED);
		assertThat(confirmed.getExpiresAt()).isNull();
		Occupancy held = occupancies.get(1);
		assertThat(held.getStartTime()).isEqualTo(LocalTime.parse("09:30"));
		assertThat(held.getStatus()).isEqualTo(OccupancyStatus.HELD);
		assertThat(held.getExpiresAt()).isEqualTo(Instant.parse("2026-09-10T14:44:50Z"));
	}

	@Test
	void asksForASingleDayWithTheTechnicalToken() {
		// La cátedra falsa solo responde a esa URL exacta (from = to) y con el encabezado exacto.
		ObjectNode mapping = FakeCatedra.occupanciesMapping(204, "2026-11-02", 200, emptyResponse(204, "2026-11-02"));
		((ObjectNode) mapping.get("request")).putObject("headers")
				.putObject("Authorization")
				.put("equalTo", "Bearer " + FakeCatedra.TECHNICAL_TOKEN);
		FakeServer.stub(catedra, mapping.toString());

		assertThat(adapter().getOccupancies(204L, LocalDate.parse("2026-11-02"))).isEmpty();
		assertThat(requests(204, "2026-11-02")).isEqualTo(1);
	}

	@Test
	void acceptsTimesWithoutSecondsAndIgnoresUnknownFields() {
		stub(204, "2026-11-02", 200, """
				{ "professionalId": 204, "from": "2026-11-02", "to": "2026-11-02", "campoNuevo": true,
				  "occupancies": [ { "date": "2026-11-02", "startTime": "11:20", "status": "CONFIRMED",
				    "expiresAt": null, "otroCampo": 1 } ] }
				""");

		List<Occupancy> occupancies = adapter().getOccupancies(204L, LocalDate.parse("2026-11-02"));

		assertThat(occupancies).singleElement()
				.satisfies(occupancy -> assertThat(occupancy.getStartTime()).isEqualTo(LocalTime.parse("11:20")));
	}

	@Test
	void failsWhenTheListIsMissing() {
		// Sin la lista no se sabe qué está ocupado: no se puede ofrecer ningún slot (CU-03 4a).
		stub(204, "2026-11-02", 200, """
				{ "professionalId": 204, "from": "2026-11-02", "to": "2026-11-02" }
				""");

		assertThatThrownBy(() -> adapter().getOccupancies(204L, LocalDate.parse("2026-11-02")))
				.isInstanceOf(IntegrationUnavailableException.class)
				.hasMessage("Unexpected response from the catedra: no occupancies");
	}

	@Test
	void failsWithAnUnknownStatus() {
		stub(204, "2026-11-02", 200, """
				{ "professionalId": 204, "from": "2026-11-02", "to": "2026-11-02",
				  "occupancies": [ { "date": "2026-11-02", "startTime": "11:20:00", "status": "OTRO" } ] }
				""");

		assertThatThrownBy(() -> adapter().getOccupancies(204L, LocalDate.parse("2026-11-02")))
				.isInstanceOf(IntegrationUnavailableException.class)
				.hasMessage("Unexpected response from the catedra: unreadable body");
	}

	@Test
	void doesNotRetryARejectedRequest() {
		// El código que devuelve la cátedra real a un rango invertido. Turnos valida la fecha antes, así
		// que sería un error propio: no se repite y se informa como servicio no disponible.
		stub(204, "2026-11-02", 400, """
				{ "status": 400, "code": "INVALID_DATE_RANGE", "detail": "to must not be before from" }
				""");

		assertThatThrownBy(() -> adapter().getOccupancies(204L, LocalDate.parse("2026-11-02")))
				.isInstanceOf(IntegrationUnavailableException.class)
				.hasMessage("Unexpected response from the catedra: HTTP 400");
		assertThat(requests(204, "2026-11-02")).isEqualTo(1);
	}

	@Test
	void retriesOnceAndFailsWhenTheCatedraFails() {
		stub(204, "2026-11-02", 503, "");

		assertThatThrownBy(() -> adapter().getOccupancies(204L, LocalDate.parse("2026-11-02")))
				.isInstanceOf(IntegrationUnavailableException.class)
				.hasMessage("Catedra service unavailable (HTTP 503)");
		assertThat(requests(204, "2026-11-02")).isEqualTo(2);
	}

	// La forma de la respuesta vacía que devolvió la cátedra real.
	private static String emptyResponse(long professionalId, String date) {
		return """
				{"professionalId":%d,"from":"%s","to":"%s","occupancies":[]}
				""".formatted(professionalId, date, date);
	}

	private static void stub(long professionalId, String date, int status, String body) {
		FakeServer.stub(catedra, FakeCatedra.occupanciesMapping(professionalId, date, status, body).toString());
	}

	private static int requests(long professionalId, String date) {
		return FakeServer.countRequests(catedra, "GET", FakeCatedra.occupanciesUrl(professionalId, date));
	}

	private static CatedraOccupancyAdapter adapter() {
		CatedraProperties properties = new CatedraProperties(FakeServer.baseUrl(catedra), FakeCatedra.TECHNICAL_TOKEN,
				Duration.ofSeconds(1), Duration.ofMillis(500));
		return new CatedraOccupancyAdapter(new CatedraRestClientConfig().catedraRestClient(properties),
				new CatedraReadExecutor(new RemoteReadRetry()), new OccupancyMapper());
	}

}

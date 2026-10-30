package com.example.turnos.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.example.turnos.FakeCatalog;
import com.example.turnos.FakeCatedra;
import com.example.turnos.FakeServer;
import com.example.turnos.TestCatalogConfiguration;
import com.example.turnos.TestCatedraConfiguration;
import com.example.turnos.TestClockConfiguration;
import com.example.turnos.TestJwtKeysConfiguration;
import com.example.turnos.TestcontainersConfiguration;
import com.example.turnos.shared.infrastructure.config.JwtConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.node.ObjectNode;

/**
 * Recorrido completo de CU-03 con la aplicación entera y la seguridad activa: el catálogo y la cátedra
 * son falsos (WireMock) y responden con datos reales (la agenda de la 204 y la 206 se copiaron del
 * catálogo). "Hoy" es el lunes 2026-11-02 a las 12:30 de Argentina ({@link TestClockConfiguration}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestJwtKeysConfiguration.class, TestCatedraConfiguration.class,
		TestCatalogConfiguration.class, TestClockConfiguration.class })
class AvailabilityFlowTest {

	private static final String NEXT_MONDAY = "2026-11-09";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtEncoder jwtEncoder;

	@Autowired
	private GenericContainer<?> catalogContainer;

	@Autowired
	private GenericContainer<?> catedraContainer;

	private String token;

	// La integración ya se pidió al arrancar: borrar las respuestas de la cátedra falsa no la afecta.
	@BeforeEach
	void setUp() {
		FakeServer.reset(catalogContainer);
		FakeServer.reset(catedraContainer);
		token = userToken();
	}

	@Test
	void offersTheFreeSlotsForwardingTheUserTokenToTheCatalog() throws Exception {
		// El catálogo falso solo responde si le llega el mismo JWT que recibió turnos (ADR-0034).
		ObjectNode agenda = FakeCatalog.agendaMapping(204, 200, FakeServer.readResource(FakeCatalog.AGENDA_204));
		((ObjectNode) agenda.get("request")).putObject("headers").putObject("Authorization").put("equalTo", "Bearer " + token);
		FakeServer.stub(catalogContainer, agenda.toString());
		stubOccupancies(204, NEXT_MONDAY, 200, """
				{ "professionalId": 204, "from": "2026-11-09", "to": "2026-11-09", "occupancies": [
				  { "date": "2026-11-09", "startTime": "11:20:00", "status": "CONFIRMED", "expiresAt": null },
				  { "date": "2026-11-09", "startTime": "14:00:00", "status": "HELD", "expiresAt": "2026-11-02T15:40:00Z" } ] }
				""");

		availability(204, NEXT_MONDAY)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.professionalId").value(204))
				.andExpect(jsonPath("$.professionalFirstName").value("Laura"))
				.andExpect(jsonPath("$.professionalLastName").value("Medina"))
				.andExpect(jsonPath("$.date").value(NEXT_MONDAY))
				.andExpect(jsonPath("$.catalogVersion").value(1))
				.andExpect(jsonPath("$.slots", hasSize(10)))
				.andExpect(jsonPath("$.slots[0].startTime").value("11:00:00"))
				.andExpect(jsonPath("$.slots[0].endTime").value("11:20:00"))
				.andExpect(jsonPath("$.slots[1].startTime").value("11:40:00"))
				.andExpect(jsonPath("$.slots[9].startTime").value("14:40:00"));
	}

	@Test
	void todayOffersOnlyTheSlotsThatHaveNotStarted() throws Exception {
		stubAgenda(204, 200, FakeServer.readResource(FakeCatalog.AGENDA_204));
		stubOccupancies(204, "2026-11-02", 200, emptyOccupancies(204, "2026-11-02"));

		availability(204, "2026-11-02")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.slots", hasSize(7)))
				.andExpect(jsonPath("$.slots[0].startTime").value("12:40:00"));
	}

	@Test
	void rejectsARequestWithoutTokenWithoutCallingAnyone() throws Exception {
		mockMvc.perform(get("/api/availability").param("professionalId", "204").param("date", NEXT_MONDAY))
				.andExpect(status().isUnauthorized());
		assertThat(FakeServer.countRequests(catalogContainer, "GET", FakeCatalog.agendaPath(204))).isZero();
	}

	@Test
	void rejectsADateAfterTheHorizonWithoutCallingAnyone() throws Exception {
		availability(204, "2027-01-02")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("DATE_OUT_OF_RANGE"));
		assertThat(FakeServer.countRequests(catalogContainer, "GET", FakeCatalog.agendaPath(204))).isZero();
	}

	@Test
	void rejectsAMissingProfessional() throws Exception {
		stubAgenda(9999, 404, FakeServer.readResource(FakeCatalog.PROFESSIONAL_NOT_FOUND));

		availability(9999, NEXT_MONDAY)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PROFESSIONAL_NOT_FOUND"));
	}

	@Test
	void rejectsADisabledProfessionalWithoutAskingTheCatedra() throws Exception {
		stubAgenda(206, 200, FakeServer.readResource(FakeCatalog.AGENDA_206));

		availability(206, NEXT_MONDAY)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PROFESSIONAL_DISABLED"));
		assertThat(FakeServer.countRequests(catedraContainer, "GET", FakeCatedra.occupanciesUrl(206, NEXT_MONDAY))).isZero();
	}

	@Test
	void failsWhenTheCatalogIsNotReady() throws Exception {
		stubAgenda(204, 503, """
				{ "status": 503, "code": "CATALOG_NOT_READY" }
				""");

		availability(204, NEXT_MONDAY)
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"));
	}

	@Test
	void failsWhenTheCatedraDoesNotGiveTheOccupancies() throws Exception {
		stubAgenda(204, 200, FakeServer.readResource(FakeCatalog.AGENDA_204));
		stubOccupancies(204, NEXT_MONDAY, 503, "");

		availability(204, NEXT_MONDAY)
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("INTEGRATION_UNAVAILABLE"))
				.andExpect(jsonPath("$.slots").doesNotExist());
	}

	private ResultActions availability(long professionalId, String date) throws Exception {
		return mockMvc.perform(get("/api/availability")
				.param("professionalId", String.valueOf(professionalId))
				.param("date", date)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private void stubAgenda(long professionalId, int status, String body) {
		FakeServer.stub(catalogContainer, FakeCatalog.agendaMapping(professionalId, status, body).toString());
	}

	private void stubOccupancies(long professionalId, String date, int status, String body) {
		FakeServer.stub(catedraContainer, FakeCatedra.occupanciesMapping(professionalId, date, status, body).toString());
	}

	private static String emptyOccupancies(long professionalId, String date) {
		return """
				{"professionalId":%d,"from":"%s","to":"%s","occupancies":[]}
				""".formatted(professionalId, date, date);
	}

	// El JWT se valida con la hora del sistema, no con el reloj fijo de la prueba.
	private String userToken() {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject("laura.paciente")
				.claim(JwtConfig.AUTHORITIES_CLAIM, List.of("ROLE_USER"))
				.issuedAt(now)
				.expiresAt(now.plus(Duration.ofHours(1)))
				.build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
				.getTokenValue();
	}

}

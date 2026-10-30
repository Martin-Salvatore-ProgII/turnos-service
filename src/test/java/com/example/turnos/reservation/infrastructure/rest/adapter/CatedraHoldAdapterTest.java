package com.example.turnos.reservation.infrastructure.rest.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import com.example.turnos.FakeCatedra;
import com.example.turnos.FakeServer;
import com.example.turnos.reservation.domain.model.HoldResult;
import com.example.turnos.reservation.domain.model.ReservationFailureReason;
import com.example.turnos.reservation.infrastructure.rest.mapper.HoldMapper;
import com.example.turnos.shared.infrastructure.config.CatedraProperties;
import com.example.turnos.shared.infrastructure.config.CatedraRestClientConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

// El cliente del hold contra una cátedra falsa (WireMock), sin Spring. Las respuestas son las que dio
// la cátedra real con ./probar-reserva-catedra.sh (status, code y forma), con los valores de la REF §9.
// El timeout de respuesta es corto para que la prueba de demora no tarde.
class CatedraHoldAdapterTest {

	// Solo HTTP/1.1, como la cátedra real: WireMock acepta pasar a HTTP/2, y en ese modo corta los POST
	// del cliente de Java. La cátedra real no pasa a HTTP/2 (probado con curl --http2).
	private static final GenericContainer<?> catedra = FakeServer.newContainer().withCommand("--disable-http2-plain");
	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final LocalDate DATE = LocalDate.parse("2026-11-30");
	private static final LocalTime START = LocalTime.parse("12:00");
	private static final UUID PATIENT = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");

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
	void returnsTheCreatedHold() {
		stub(201, """
				{ "holdId": "hold-6c0c2f41", "reservationProcessId": "process-a92b47d0", "status": "HELD",
				  "expiresAt": "2026-09-10T14:44:50Z", "campoNuevo": 1 }
				""", 0);

		HoldResult result = createHold(adapter());

		assertThat(result.isCreated()).isTrue();
		assertThat(result.getHold().getHoldId()).isEqualTo("hold-6c0c2f41");
		assertThat(result.getHold().getReservationProcessId()).isEqualTo("process-a92b47d0");
		assertThat(result.getHold().getExpiresAt()).isEqualTo(Instant.parse("2026-09-10T14:44:50Z"));
	}

	@Test
	void sendsTheFieldsOfTheReferenceWithTheTechnicalToken() {
		// La cátedra falsa solo responde 201 si el cuerpo y el encabezado son exactamente estos.
		ObjectNode mapping = mapping(201, """
				{ "holdId": "hold-1", "reservationProcessId": "process-1", "status": "HELD",
				  "expiresAt": "2026-09-10T14:44:50Z" }
				""", 0);
		ObjectNode request = (ObjectNode) mapping.get("request");
		request.putObject("headers").putObject("Authorization").put("equalTo", "Bearer " + FakeCatedra.TECHNICAL_TOKEN);
		request.putArray("bodyPatterns").addObject().put("equalToJson", """
				{ "professionalId": 204, "date": "2026-11-30", "startTime": "12:00:00",
				  "externalPatientId": "0f8fad5b-d9cb-469f-a165-70867728950e" }
				""");
		FakeServer.stub(catedra, mapping.toString());

		assertThat(createHold(adapter()).isCreated()).isTrue();
	}

	@ParameterizedTest
	@CsvSource({ "409, SLOT_ALREADY_HELD", "409, SLOT_ALREADY_RESERVED", "400, INVALID_SLOT",
			"400, PROFESSIONAL_DISABLED", "404, PROFESSIONAL_NOT_FOUND" })
	void aRejectionKeepsTheCodeOfTheCatedraAsTheReason(int status, String code) {
		stub(status, problem(status, code), 0);

		HoldResult result = createHold(adapter());

		assertThat(result.isCreated()).isFalse();
		assertThat(result.getFailureReason()).isEqualTo(code);
	}

	@ParameterizedTest
	@CsvSource({ "400, VALIDATION_ERROR", "403, STUDENT_INTEGRATION_NOT_FOUND" })
	void anErrorThatIsNotAboutTheSlotIsUnavailable(int status, String code) {
		// Un pedido mal armado o la integración rechazada son errores nuestros, no del turno (ADR-0069).
		stub(status, problem(status, code), 0);

		assertUnavailable(createHold(adapter()));
	}

	@Test
	void anErrorWithoutCodeIsUnavailable() {
		// Los 401 de Spring Security de la cátedra pueden no traer cuerpo (REF §13).
		stub(401, "", 0);

		assertUnavailable(createHold(adapter()));
	}

	@Test
	void aServerErrorIsUnavailableAndIsNotRetried() {
		stub(503, "", 0);

		assertUnavailable(createHold(adapter()));
		assertThat(requests()).isEqualTo(1);
	}

	@Test
	void aResponseWithoutIdentifiersIsUnavailable() {
		stub(201, """
				{ "status": "HELD", "expiresAt": "2026-09-10T14:44:50Z" }
				""", 0);

		assertUnavailable(createHold(adapter()));
	}

	@Test
	void aResponseThatTakesTooLongIsATimeoutAndIsNotRetried() {
		// ADR-0028: el pedido llegó y no se sabe si el hold se creó.
		stub(201, """
				{ "holdId": "hold-1", "reservationProcessId": "process-1", "status": "HELD",
				  "expiresAt": "2026-09-10T14:44:50Z" }
				""", 1500);

		HoldResult result = createHold(adapter());

		assertThat(result.getFailureReason()).isEqualTo(ReservationFailureReason.INTEGRATION_TIMEOUT);
		assertThat(requests()).isEqualTo(1);
	}

	@Test
	void aCatedraThatRefusesTheConnectionIsUnavailable() {
		// Nada escucha en el puerto 1: la conexión se rechaza y el pedido nunca sale.
		CatedraHoldAdapter unreachable = adapter("http://localhost:1");

		assertUnavailable(createHold(unreachable));
	}

	private static void assertUnavailable(HoldResult result) {
		assertThat(result.isCreated()).isFalse();
		assertThat(result.getFailureReason()).isEqualTo(ReservationFailureReason.INTEGRATION_UNAVAILABLE);
	}

	private static HoldResult createHold(CatedraHoldAdapter adapter) {
		return adapter.createHold(204L, DATE, START, PATIENT);
	}

	// La forma de los errores que devolvió la cátedra real (application/problem+json).
	private static String problem(int status, String code) {
		return """
				{ "type": "about:blank", "title": "Error", "status": %d, "detail": "detail", "instance": "%s",
				  "message": "error", "path": "%s", "code": "%s" }
				""".formatted(status, CatedraHoldAdapter.HOLDS_PATH, CatedraHoldAdapter.HOLDS_PATH, code);
	}

	private static void stub(int status, String body, int delayMillis) {
		FakeServer.stub(catedra, mapping(status, body, delayMillis).toString());
	}

	private static ObjectNode mapping(int status, String body, int delayMillis) {
		ObjectNode mapping = JSON.createObjectNode();
		mapping.putObject("request").put("method", "POST").put("url", CatedraHoldAdapter.HOLDS_PATH);
		ObjectNode response = mapping.putObject("response").put("status", status);
		response.putObject("headers").put("Content-Type", status < 300 ? "application/json" : "application/problem+json");
		response.put("body", body);
		if (delayMillis > 0) {
			response.put("fixedDelayMilliseconds", delayMillis);
		}
		return mapping;
	}

	private static int requests() {
		return FakeServer.countRequests(catedra, "POST", CatedraHoldAdapter.HOLDS_PATH);
	}

	private static CatedraHoldAdapter adapter() {
		return adapter(FakeServer.baseUrl(catedra));
	}

	private static CatedraHoldAdapter adapter(String baseUrl) {
		CatedraProperties properties = new CatedraProperties(baseUrl, FakeCatedra.TECHNICAL_TOKEN,
				Duration.ofSeconds(1), Duration.ofMillis(500));
		return new CatedraHoldAdapter(new CatedraRestClientConfig().catedraRestClient(properties), new HoldMapper());
	}

}

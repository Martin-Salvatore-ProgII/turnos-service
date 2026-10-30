package com.example.turnos.reservation.infrastructure.rest.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import com.example.turnos.FakeCatedra;
import com.example.turnos.FakeServer;
import com.example.turnos.reservation.domain.model.ConfirmationResult;
import com.example.turnos.reservation.domain.model.HoldResult;
import com.example.turnos.reservation.domain.model.ReservationFailureReason;
import com.example.turnos.reservation.infrastructure.rest.mapper.HoldMapper;
import com.example.turnos.shared.infrastructure.config.CatedraProperties;
import com.example.turnos.shared.infrastructure.config.CommandRetryProperties;
import com.example.turnos.shared.infrastructure.config.CatedraRestClientConfig;
import com.example.turnos.shared.infrastructure.rest.RemoteCommandRetry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

// El cliente del hold y de su confirmación contra una cátedra falsa (WireMock), sin Spring. Las respuestas son las que dio
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
	private static final String CONFIRM_URL = "/api/appointment-holds/hold-1/confirm";
	private static final String ACCEPTED_BODY = """
			{ "reservationProcessId": "process-1", "status": "WAITING_FOR_PHONE", "expiresAt": "2026-09-10T14:44:50Z" }
			""";

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

	@Test
	void anAcceptedConfirmationReturnsTheNewExpiration() {
		// La cátedra falsa solo responde 202 si el cuerpo y el encabezado son los de la REF §10.
		ObjectNode mapping = mapping("POST", CONFIRM_URL, 202, ACCEPTED_BODY, 0);
		ObjectNode request = (ObjectNode) mapping.get("request");
		request.putObject("headers").putObject("Authorization").put("equalTo", "Bearer " + FakeCatedra.TECHNICAL_TOKEN);
		request.putArray("bodyPatterns").addObject().put("equalToJson", """
				{ "externalPatientId": "0f8fad5b-d9cb-469f-a165-70867728950e", "patientFirstName": "Juan",
				  "patientLastName": "Perez" }
				""");
		FakeServer.stub(catedra, mapping.toString());

		ConfirmationResult result = confirmHold(adapter());

		assertThat(result.getOutcome()).isEqualTo(ConfirmationResult.Outcome.ACCEPTED);
		assertThat(result.getExpiresAt()).isEqualTo(Instant.parse("2026-09-10T14:44:50Z"));
	}

	@Test
	void anAlreadyConfirmedProcessIsAccepted() {
		// Es la respuesta a una confirmación repetida: prueba que la anterior llegó (ADR-0029).
		stubConfirmation(409, problem(409, "RESERVATION_PROCESS_ALREADY_CONFIRMED"), 0);

		ConfirmationResult result = confirmHold(adapter());

		assertThat(result.getOutcome()).isEqualTo(ConfirmationResult.Outcome.ACCEPTED);
		assertThat(result.getExpiresAt()).isNull();
	}

	@Test
	void anExpiredHoldIsExpired() {
		stubConfirmation(404, problem(404, "HOLD_EXPIRED"), 0);

		ConfirmationResult result = confirmHold(adapter());

		assertThat(result.getOutcome()).isEqualTo(ConfirmationResult.Outcome.EXPIRED);
		assertThat(result.getFailureReason()).isEqualTo("HOLD_EXPIRED");
	}

	@ParameterizedTest
	@CsvSource({ "404, HOLD_NOT_FOUND", "400, EXTERNAL_PATIENT_MISMATCH", "409, SLOT_ALREADY_RESERVED",
			"400, INVALID_SLOT", "404, PROFESSIONAL_NOT_FOUND" })
	void aRejectedConfirmationKeepsTheCodeOfTheCatedra(int status, String code) {
		stubConfirmation(status, problem(status, code), 0);

		ConfirmationResult result = confirmHold(adapter());

		assertThat(result.getOutcome()).isEqualTo(ConfirmationResult.Outcome.REJECTED);
		assertThat(result.getFailureReason()).isEqualTo(code);
		assertThat(requests("POST", CONFIRM_URL)).isEqualTo(1);
	}

	@ParameterizedTest
	@CsvSource({ "400, VALIDATION_ERROR", "403, HOLD_OWNER_MISMATCH", "403, STUDENT_INTEGRATION_NOT_FOUND" })
	void aConfirmationErrorThatIsNotAboutTheHoldIsUnavailable(int status, String code) {
		stubConfirmation(status, problem(status, code), 0);

		ConfirmationResult result = confirmHold(adapter());

		assertThat(result.getOutcome()).isEqualTo(ConfirmationResult.Outcome.REJECTED);
		assertThat(result.getFailureReason()).isEqualTo(ReservationFailureReason.INTEGRATION_UNAVAILABLE);
	}

	@Test
	void aConfirmationWithoutAnswerIsTriedThreeTimes() {
		stubConfirmation(503, "", 0);

		ConfirmationResult result = confirmHold(adapter());

		assertThat(result.getOutcome()).isEqualTo(ConfirmationResult.Outcome.NO_RESPONSE);
		assertThat(requests("POST", CONFIRM_URL)).isEqualTo(3);
	}

	@Test
	void aConfirmationRecoversWhenTheCatedraAnswersOnARetry() {
		// Escenario de WireMock: el primer pedido falla del lado de la cátedra y el segundo responde 202.
		stubScenario(503, "", 0, ACCEPTED_BODY);

		ConfirmationResult result = confirmHold(adapter());

		assertThat(result.getOutcome()).isEqualTo(ConfirmationResult.Outcome.ACCEPTED);
		assertThat(requests("POST", CONFIRM_URL)).isEqualTo(2);
	}

	@Test
	void aConfirmationWhoseAnswerWasLostIsAcceptedOnTheRetry() {
		// El caso del ADR-0029: el primer pedido llega pero la respuesta tarda más que el timeout; al
		// repetirlo, la cátedra responde ALREADY_CONFIRMED.
		stubScenario(202, ACCEPTED_BODY, 1500, null);

		ConfirmationResult result = confirmHold(adapter());

		assertThat(result.getOutcome()).isEqualTo(ConfirmationResult.Outcome.ACCEPTED);
		assertThat(requests("POST", CONFIRM_URL)).isEqualTo(2);
	}

	private static ConfirmationResult confirmHold(CatedraHoldAdapter adapter) {
		return adapter.confirmHold("hold-1", PATIENT, "Juan", "Perez");
	}

	private static void stubConfirmation(int status, String body, int delayMillis) {
		FakeServer.stub(catedra, mapping("POST", CONFIRM_URL, status, body, delayMillis).toString());
	}

	// El primer pedido responde con status, cuerpo y demora dados; los siguientes, 202 con secondBody, o
	// ALREADY_CONFIRMED si secondBody es null.
	private static void stubScenario(int status, String body, int delayMillis, String secondBody) {
		ObjectNode first = mapping("POST", CONFIRM_URL, status, body, delayMillis);
		first.put("scenarioName", "confirmacion").put("requiredScenarioState", "Started")
				.put("newScenarioState", "segundo");
		FakeServer.stub(catedra, first.toString());
		ObjectNode second = secondBody == null
				? mapping("POST", CONFIRM_URL, 409, problem(409, "RESERVATION_PROCESS_ALREADY_CONFIRMED"), 0)
				: mapping("POST", CONFIRM_URL, 202, secondBody, 0);
		second.put("scenarioName", "confirmacion").put("requiredScenarioState", "segundo");
		FakeServer.stub(catedra, second.toString());
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
		return mapping("POST", CatedraHoldAdapter.HOLDS_PATH, status, body, delayMillis);
	}

	private static ObjectNode mapping(String method, String url, int status, String body, int delayMillis) {
		ObjectNode mapping = JSON.createObjectNode();
		mapping.putObject("request").put("method", method).put("url", url);
		ObjectNode response = mapping.putObject("response").put("status", status);
		response.putObject("headers").put("Content-Type", status < 300 ? "application/json" : "application/problem+json");
		response.put("body", body);
		if (delayMillis > 0) {
			response.put("fixedDelayMilliseconds", delayMillis);
		}
		return mapping;
	}

	private static int requests() {
		return requests("POST", CatedraHoldAdapter.HOLDS_PATH);
	}

	private static int requests(String method, String url) {
		return FakeServer.countRequests(catedra, method, url);
	}

	private static CatedraHoldAdapter adapter() {
		return adapter(FakeServer.baseUrl(catedra));
	}

	private static CatedraHoldAdapter adapter(String baseUrl) {
		CatedraProperties properties = new CatedraProperties(baseUrl, FakeCatedra.TECHNICAL_TOKEN,
				Duration.ofSeconds(1), Duration.ofMillis(500));
		// Esperas de pocos milisegundos entre reintentos, para que las pruebas no tarden.
		RemoteCommandRetry retry = new RemoteCommandRetry(new CommandRetryProperties(2, Duration.ofMillis(10)));
		return new CatedraHoldAdapter(new CatedraRestClientConfig().catedraRestClient(properties), retry,
				new HoldMapper());
	}

}

package com.example.turnos.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
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

import com.example.turnos.FakeCatalog;
import com.example.turnos.FakeCatedra;
import com.example.turnos.FakeKafka;
import com.example.turnos.FakeServer;
import com.example.turnos.TestCatalogConfiguration;
import com.example.turnos.TestCatedraConfiguration;
import com.example.turnos.TestClockConfiguration;
import com.example.turnos.TestJwtKeysConfiguration;
import com.example.turnos.TestcontainersConfiguration;
import com.example.turnos.reservation.application.service.ReservationService;
import com.example.turnos.shared.infrastructure.config.CatedraIntegration;
import com.jayway.jsonpath.JsonPath;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * La reserva por Kafka de punta a punta (CU-04 desde el paso 8, CU-05; ENUNCIADO §11), con la aplicación
 * entera: PostgreSQL, un Kafka real, la seguridad activa y la cátedra y el catálogo simulados. El usuario
 * se registra e inicia sesión de verdad. Los eventos de la cátedra se publican en el topic de acciones con
 * el formato de la REF §15 y lo que turnos publica se lee del topic de teléfono. "Ahora" es el lunes
 * 2026-11-02 a las 12:30 de Argentina ({@link TestClockConfiguration}); el contexto y la base se comparten,
 * así que cada prueba usa su propio usuario y su propio proceso.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestJwtKeysConfiguration.class, TestCatedraConfiguration.class,
		TestCatalogConfiguration.class, TestClockConfiguration.class })
class ReservationEventFlowTest {

	private static final String NEXT_MONDAY = "2026-11-09";
	private static final String HOLDS_PATH = "/api/appointment-holds";
	private static final Duration WAIT = FakeKafka.CONSUME_TIMEOUT;
	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private GenericContainer<?> catalogContainer;

	@Autowired
	private GenericContainer<?> catedraContainer;

	@Autowired
	private CatedraIntegration integration;

	@Autowired
	private JdbcClient jdbcClient;

	@Autowired
	private ReservationService reservationService;

	@BeforeEach
	void setUp() {
		FakeServer.reset(catalogContainer);
		FakeServer.reset(catedraContainer);
		FakeServer.stub(catalogContainer,
				FakeCatalog.agendaMapping(204, 200, FakeServer.readResource(FakeCatalog.AGENDA_204)).toString());
	}

	// El camino feliz de la evidencia "reserva confirmada vía REST + Kafka" (ENUNCIADO §11).
	@Test
	void aReservationIsConfirmedThroughRestAndKafka() throws Exception {
		User user = newUser();
		String processId = stubHoldAndConfirmation("2026-11-02T15:41:00Z");
		String id = startReservation(user);
		assertStatus(user, id, "AWAITING_REQUEST");

		// Llega el pedido de teléfono de la cátedra: la app lo ve al consultar (ADR-0021).
		publish(processId, requested("request-event-" + id, processId, "2026-11-02T15:41:00Z"));
		awaitStatus(user, id, "AWAITING_PHONE");
		getReservation(user, id)
				.andExpect(jsonPath("$.message").value("Ingrese un numero de telefono para continuar."))
				.andExpect(jsonPath("$.expiresAt").value("2026-11-02T15:41:00Z"));

		// El usuario manda el teléfono con separadores: se publica normalizado, con el requestEventId del pedido
		// y la key del proceso (REF §15.5).
		submitPhone(user, id, "+54 9 261 555-1234").andExpect(status().isAccepted())
				.andExpect(jsonPath("$.status").value("PHONE_SUBMITTED"));
		List<ConsumerRecord<String, String>> sent = FakeKafka.recordsWithKey(phoneTopic(), processId, 1, WAIT);
		assertThat(sent).hasSize(1);
		JsonNode message = JSON.readTree(sent.get(0).value());
		assertThat(message.get("eventType").asString()).isEqualTo("AdditionalInformationSubmitted");
		assertThat(message.get("requestEventId").asString()).isEqualTo("request-event-" + id);
		assertThat(message.get("information").get("phoneNumber").asString()).isEqualTo("+5492615551234");
		assertThat(message.get("groupId").asString()).isEqualTo(integration.groupId());

		publish(processId, confirmed("confirmation-event-" + id, processId, 8251));
		awaitStatus(user, id, "CONFIRMED");
		assertThat(jdbcClient.sql("SELECT reservation_id FROM reservation_process WHERE id = :id::uuid")
				.param("id", id).query(Long.class).single()).isEqualTo(8251L);
	}

	// Evidencia "reserva que expira sin completar la información" (ENUNCIADO §11, CU-04 11b).
	@Test
	void aReservationThatExpiresWithoutThePhoneEndsExpiredByTheEventOfTheCatedra() throws Exception {
		User user = newUser();
		String processId = stubHoldAndConfirmation("2026-11-02T15:41:00Z");
		String id = startReservation(user);
		publish(processId, requested("request-event-" + id, processId, "2026-11-02T15:41:00Z"));
		awaitStatus(user, id, "AWAITING_PHONE");

		// El usuario no ingresa el teléfono (ENUNCIADO §11): la cátedra informa el vencimiento.
		publish(processId, event("process-event-" + id, "AppointmentProcessExpired", processId, "\"reason\":\"HOLD_EXPIRED\""));

		awaitStatus(user, id, "EXPIRED");
		getReservation(user, id).andExpect(jsonPath("$.failureReason").value("HOLD_EXPIRED"));
		// Vencida, ya no acepta un teléfono (CU-04, 10b), y el usuario puede reservar de nuevo.
		submitPhone(user, id, "+5492615551234").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PHONE_NOT_REQUESTED"));
		assertThat(FakeKafka.recordsWithKey(phoneTopic(), processId, 1, Duration.ofSeconds(2))).isEmpty();
	}

	@Test
	void aRejectedPhoneIsAskedAgainAndTheSecondSubmissionHasANewEventId() throws Exception {
		User user = newUser();
		String processId = stubHoldAndConfirmation("2026-11-02T15:41:00Z");
		String id = startReservation(user);
		publish(processId, requested("request-event-" + id, processId, "2026-11-02T15:41:00Z"));
		awaitStatus(user, id, "AWAITING_PHONE");
		submitPhone(user, id, "2615551234").andExpect(status().isAccepted());

		publish(processId, event("rejection-event-" + id, "AdditionalInformationRejected", processId,
				"\"reason\":\"INVALID_PHONE_NUMBER\",\"message\":\"El telefono ingresado no es valido.\","
						+ "\"expiresAt\":\"2026-11-02T15:41:00Z\""));

		awaitStatus(user, id, "AWAITING_PHONE");
		getReservation(user, id)
				.andExpect(jsonPath("$.message").value("El telefono ingresado no es valido."))
				.andExpect(jsonPath("$.failureReason").value("INVALID_PHONE_NUMBER"));
		submitPhone(user, id, "+5492615551234").andExpect(status().isAccepted());

		List<ConsumerRecord<String, String>> sent = FakeKafka.recordsWithKey(phoneTopic(), processId, 2, WAIT);
		assertThat(sent).hasSize(2);
		String first = JSON.readTree(sent.get(0).value()).get("eventId").asString();
		String second = JSON.readTree(sent.get(1).value()).get("eventId").asString();
		assertThat(second).isNotEqualTo(first);
		// Los dos responden al mismo pedido (REF §15.7).
		assertThat(JSON.readTree(sent.get(1).value()).get("requestEventId").asString())
				.isEqualTo("request-event-" + id);
		getReservation(user, id).andExpect(jsonPath("$.status").value("PHONE_SUBMITTED"));
	}

	// Evidencia "manejo idempotente de un mensaje duplicado" (ENUNCIADO §11, REF §18.1).
	@Test
	void aDuplicatedEventIsProcessedOnceAndALateOneDoesNotReopenTheProcess() throws Exception {
		User user = newUser();
		String processId = stubHoldAndConfirmation("2026-11-02T15:41:00Z");
		String id = startReservation(user);
		publish(processId, requested("request-event-" + id, processId, "2026-11-02T15:41:00Z"));
		awaitStatus(user, id, "AWAITING_PHONE");
		submitPhone(user, id, "2615551234").andExpect(status().isAccepted());

		String confirmation = confirmed("confirmation-event-" + id, processId, 8251);
		publish(processId, confirmation);
		publish(processId, confirmation);
		// Un vencimiento tardío con otro eventId: no es un duplicado, pero un proceso confirmado no se reabre.
		publish(processId, event("late-expiration-" + id, "AppointmentProcessExpired", processId, "\"reason\":\"HOLD_EXPIRED\""));

		awaitEventRegistered("late-expiration-" + id);
		assertStatus(user, id, "CONFIRMED");
		assertThat(jdbcClient.sql("SELECT count(*) FROM processed_event WHERE event_id = :id")
				.param("id", "confirmation-event-" + id).query(Long.class).single()).isEqualTo(1L);
		getReservation(user, id).andExpect(jsonPath("$.failureReason").doesNotExist());
	}

	// REF §15.10: una reserva cancelada del lado de la cátedra (o cuyo 200 se perdió) queda cancelada por el
	// evento, y la copia repetida se procesa una sola vez. Contra el Kafka simulado: la cátedra real todavía
	// no publica eventos para este grupo.
	@Test
	void aConfirmedReservationIsCancelledByTheEventOfTheCatedraOnlyOnce() throws Exception {
		User user = newUser();
		String processId = stubHoldAndConfirmation("2026-11-02T15:41:00Z");
		String id = confirmThroughKafka(user, processId);

		String cancellation = cancelled("cancellation-event-" + id, processId, "\"Cambio solicitado\"");
		publish(processId, cancellation);
		publish(processId, cancellation);

		awaitStatus(user, id, "CANCELLED");
		getReservation(user, id)
				.andExpect(jsonPath("$.cancellationReason").value("Cambio solicitado"))
				.andExpect(jsonPath("$.cancelledAt").value("2026-11-02T15:30:00Z"));
		assertThat(jdbcClient.sql("SELECT cancelled_by FROM reservation_process WHERE id = :id::uuid")
				.param("id", id).query(String.class).single()).isEqualTo("CATEDRA");
		assertThat(jdbcClient.sql("SELECT count(*) FROM processed_event WHERE event_id = :id")
				.param("id", "cancellation-event-" + id).query(Long.class).single()).isEqualTo(1L);
	}

	// ADR-0026: con el 200 la reserva ya quedó cancelada por el dueño; el evento que la cátedra publica
	// después es un duplicado funcional y no cambia nada.
	@Test
	void theEventAfterTheCancellationOfTheOwnerChangesNothing() throws Exception {
		User user = newUser();
		String processId = stubHoldAndConfirmation("2026-11-02T15:41:00Z");
		String id = confirmThroughKafka(user, processId);
		FakeServer.stub(catedraContainer, FakeCatedra.postMapping("/api/appointments/" + processId + "/cancel", 200, """
				{ "reservationId": 8251, "reservationProcessId": "%s", "status": "CANCELLED",
				  "cancelledAt": "2026-11-02T15:31:00Z" }
				""".formatted(processId)).toString());
		mockMvc.perform(post("/api/reservations/" + id + "/cancel")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"reason\":\"No puedo ir\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));

		publish(processId, cancelled("cancellation-event-" + id, processId, "null"));

		awaitEventRegistered("cancellation-event-" + id);
		getReservation(user, id)
				.andExpect(jsonPath("$.status").value("CANCELLED"))
				.andExpect(jsonPath("$.cancellationReason").value("No puedo ir"))
				.andExpect(jsonPath("$.cancelledAt").value("2026-11-02T15:31:00Z"));
		assertThat(jdbcClient.sql("SELECT cancelled_by FROM reservation_process WHERE id = :id::uuid")
				.param("id", id).query(String.class).single()).isEqualTo(user.login());
	}

	@Test
	void anEventOfAProcessThatDoesNotExistIsIgnoredAndTheNextOneStillArrives() throws Exception {
		User user = newUser();
		String processId = stubHoldAndConfirmation("2026-11-02T15:41:00Z");
		String id = startReservation(user);

		publish("process-que-no-existe", confirmed("confirmation-ghost-" + id, "process-que-no-existe", 1));
		publish(processId, requested("request-event-" + id, processId, "2026-11-02T15:41:00Z"));

		awaitStatus(user, id, "AWAITING_PHONE");
	}

	// HU-08/HU-09, criterio de acceso cruzado, para la operación nueva de esta milestone.
	@Test
	void anotherUserCannotSendThePhoneOfAReservation() throws Exception {
		User owner = newUser();
		User intruder = newUser();
		String processId = stubHoldAndConfirmation("2026-11-02T15:41:00Z");
		String id = startReservation(owner);
		publish(processId, requested("request-event-" + id, processId, "2026-11-02T15:41:00Z"));
		awaitStatus(owner, id, "AWAITING_PHONE");

		submitPhone(intruder, id, "+5492615551234").andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
		mockMvc.perform(post("/api/reservations/" + id + "/phone").contentType(MediaType.APPLICATION_JSON)
				.content("{\"phoneNumber\":\"+5492615551234\"}")).andExpect(status().isUnauthorized());

		getReservation(owner, id).andExpect(jsonPath("$.status").value("AWAITING_PHONE"));
		assertThat(FakeKafka.recordsWithKey(phoneTopic(), processId, 1, Duration.ofSeconds(2))).isEmpty();
	}

	@Test
	void anInvalidPhoneIsRejectedWithoutPublishing() throws Exception {
		User user = newUser();
		String processId = stubHoldAndConfirmation("2026-11-02T15:41:00Z");
		String id = startReservation(user);
		publish(processId, requested("request-event-" + id, processId, "2026-11-02T15:41:00Z"));
		awaitStatus(user, id, "AWAITING_PHONE");

		submitPhone(user, id, "12345").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PHONE_NUMBER"));

		getReservation(user, id).andExpect(jsonPath("$.status").value("AWAITING_PHONE"));
		assertThat(FakeKafka.recordsWithKey(phoneTopic(), processId, 1, Duration.ofSeconds(2))).isEmpty();
	}

	// CU-05, evento perdido: la cátedra nunca avisó el vencimiento y la reconciliación cierra el proceso.
	@Test
	void reconciliationExpiresAProcessWhoseExpirationEventNeverArrived() throws Exception {
		User user = newUser();
		String processId = stubHoldAndConfirmation("2026-11-02T15:00:00Z");
		String id = startReservation(user);
		publish(processId, requested("request-event-" + id, processId, "2026-11-02T15:00:00Z"));
		awaitStatus(user, id, "AWAITING_PHONE");

		reservationService.reconcileReservationProcesses(Duration.ofSeconds(30));

		assertStatus(user, id, "EXPIRED");
		getReservation(user, id).andExpect(jsonPath("$.failureReason").value("HOLD_EXPIRED"));
	}

	// CU-05, paso 4: con el teléfono enviado y el AppointmentConfirmed perdido, la cátedra dice cómo terminó.
	@Test
	void reconciliationConfirmsASubmittedProcessWhoseConfirmationEventNeverArrived() throws Exception {
		User user = newUser();
		String processId = stubHoldAndConfirmation("2026-11-02T15:00:00Z");
		String id = startReservation(user);
		publish(processId, requested("request-event-" + id, processId, "2026-11-02T15:00:00Z"));
		awaitStatus(user, id, "AWAITING_PHONE");
		submitPhone(user, id, "+5492615551234").andExpect(status().isAccepted());
		stubAppointments(user, """
				[ { "reservationId": 8251, "reservationProcessId": "%s", "status": "CONFIRMED",
				    "confirmedAt": "2026-11-02T15:01:00Z" } ]
				""".formatted(processId));

		reservationService.reconcileReservationProcesses(Duration.ofSeconds(30));

		assertStatus(user, id, "CONFIRMED");
		getReservation(user, id).andExpect(jsonPath("$.confirmedAt").value("2026-11-02T15:01:00Z"));
	}

	// ---- ayudas ----

	private record User(String login, String token) {
	}

	private String phoneTopic() {
		return integration.kafkaAppointmentPhoneTopic();
	}

	private void publish(String key, String json) {
		FakeKafka.publish(integration.kafkaAppointmentActionsTopic(), key, json);
	}

	// Los identificadores son nuevos en cada llamada: las pruebas comparten la base y reservationProcessId es
	// único (como en la cátedra).
	private String stubHoldAndConfirmation(String expiresAt) {
		String holdId = "hold-" + UUID.randomUUID();
		FakeServer.stub(catedraContainer, FakeCatedra.postMapping(HOLDS_PATH, 201, """
				{ "holdId": "%s", "reservationProcessId": "process-%s", "status": "HELD", "expiresAt": "%s" }
				""".formatted(holdId, holdId, expiresAt)).toString());
		FakeServer.stub(catedraContainer, FakeCatedra.postMapping(FakeCatedra.confirmUrl(holdId), 202, """
				{ "reservationProcessId": "process-%s", "status": "WAITING_FOR_PHONE", "expiresAt": "%s" }
				""".formatted(holdId, expiresAt)).toString());
		return "process-" + holdId;
	}

	private void stubAppointments(User user, String body) {
		String patient = jdbcClient.sql("SELECT external_patient_id::text FROM app_user WHERE login = :login")
				.param("login", user.login()).query(String.class).single();
		FakeServer.stub(catedraContainer, FakeCatedra.appointmentsMapping(patient, 0, 200, body, 1).toString());
	}

	private String startReservation(User user) throws Exception {
		String response = mockMvc.perform(post("/api/reservations")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"professionalId\":204,\"date\":\"%s\",\"startTime\":\"11:20\"}".formatted(NEXT_MONDAY)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.id");
	}

	// El camino feliz completo hasta CONFIRMED, para las pruebas que siguen después de la confirmación.
	private String confirmThroughKafka(User user, String processId) throws Exception {
		String id = startReservation(user);
		publish(processId, requested("request-event-" + id, processId, "2026-11-02T15:41:00Z"));
		awaitStatus(user, id, "AWAITING_PHONE");
		submitPhone(user, id, "2615551234").andExpect(status().isAccepted());
		publish(processId, confirmed("confirmation-event-" + id, processId, 8251));
		awaitStatus(user, id, "CONFIRMED");
		return id;
	}

	private ResultActions getReservation(User user, String id) throws Exception {
		return mockMvc.perform(get("/api/reservations/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()));
	}

	private ResultActions submitPhone(User user, String id, String phone) throws Exception {
		return mockMvc.perform(post("/api/reservations/" + id + "/phone")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"phoneNumber\":\"%s\"}".formatted(phone)));
	}

	private void assertStatus(User user, String id, String expected) throws Exception {
		getReservation(user, id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value(expected));
	}

	// La app consulta el estado hasta que cambia (ADR-0021); el consumidor corre en otro hilo.
	private void awaitStatus(User user, String id, String expected) {
		await().atMost(WAIT).pollInterval(Duration.ofMillis(250)).untilAsserted(() -> assertStatus(user, id, expected));
	}

	private void awaitEventRegistered(String eventId) {
		await().atMost(WAIT).pollInterval(Duration.ofMillis(250)).untilAsserted(() -> assertThat(jdbcClient
				.sql("SELECT count(*) FROM processed_event WHERE event_id = :id").param("id", eventId)
				.query(Long.class).single()).isEqualTo(1L));
	}

	private static String requested(String eventId, String processId, String expiresAt) {
		return event(eventId, "AdditionalInformationRequested", processId,
				"\"holdId\":\"hold-x\",\"requiredField\":\"PHONE_NUMBER\","
						+ "\"message\":\"Ingrese un numero de telefono para continuar.\",\"expiresAt\":\"" + expiresAt
						+ "\"");
	}

	private static String confirmed(String eventId, String processId, long reservationId) {
		return event(eventId, "AppointmentConfirmed", processId, "\"reservationId\":" + reservationId
				+ ",\"professionalId\":204,\"date\":\"" + NEXT_MONDAY + "\",\"startTime\":\"11:20\",\"status\":\"CONFIRMED\"");
	}

	// reason va como JSON: un texto entre comillas o null (REF §15.10).
	private static String cancelled(String eventId, String processId, String reason) {
		return event(eventId, "AppointmentCancelled", processId, "\"reservationId\":8251,\"professionalId\":204,"
				+ "\"date\":\"" + NEXT_MONDAY + "\",\"startTime\":\"11:20\",\"status\":\"CANCELLED\",\"reason\":" + reason);
	}

	private static String event(String eventId, String eventType, String processId, String extraFields) {
		return "{\"eventId\":\"" + eventId + "\",\"eventType\":\"" + eventType + "\",\"groupId\":\"g\","
				+ "\"reservationProcessId\":\"" + processId + "\"," + extraFields
				+ ",\"occurredAt\":\"2026-11-02T15:30:00Z\",\"schemaVersion\":1}";
	}

	private String newLogin() {
		return "u" + UUID.randomUUID().toString().substring(0, 8);
	}

	// Un usuario nuevo, registrado e iniciado de verdad: los casos de uso lo buscan en la base por el login del JWT.
	private User newUser() throws Exception {
		String login = newLogin();
		mockMvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content("""
						{"login":"%s","password":"clave-segura","firstName":"Juan","lastName":"Perez",
						 "email":"%s@example.com","langKey":"es"}
						""".formatted(login, login)))
				.andExpect(status().isCreated());
		String response = mockMvc.perform(post("/api/authenticate").contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"%s\",\"password\":\"clave-segura\"}".formatted(login)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return new User(login, JsonPath.read(response, "$.id_token"));
	}

}

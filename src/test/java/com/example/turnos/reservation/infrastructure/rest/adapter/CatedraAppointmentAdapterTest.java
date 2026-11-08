package com.example.turnos.reservation.infrastructure.rest.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

import com.example.turnos.FakeCatedra;
import com.example.turnos.FakeServer;
import com.example.turnos.reservation.application.exception.ReservationException;
import com.example.turnos.reservation.domain.model.CatedraAppointment;
import com.example.turnos.reservation.domain.model.CatedraAppointmentStatus;
import com.example.turnos.reservation.infrastructure.rest.mapper.AppointmentMapper;
import com.example.turnos.shared.infrastructure.config.CatedraProperties;
import com.example.turnos.shared.infrastructure.config.CatedraRestClientConfig;
import com.example.turnos.shared.infrastructure.rest.CatedraReadExecutor;
import com.example.turnos.shared.infrastructure.rest.RemoteReadRetry;

// Lo propio del cliente de reservas: el filtro por paciente, la paginación con X-Total-Count, los campos de la
// REF §11 y que una falla se informe como cátedra no disponible. El reintento y los mensajes de falla son
// los de toda lectura (CatedraReadExecutorTest). Sin Spring, contra una cátedra falsa (WireMock). La forma
// de la respuesta es la que devolvió la cátedra real con probar-catedra.sh.
class CatedraAppointmentAdapterTest {

	private static final GenericContainer<?> catedra = FakeServer.newContainer();
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
	void mapsTheAppointmentsOfTheReferenceAndIgnoresTheRestOfTheFields() {
		stub(0, 200, """
				[ { "reservationId": 8251, "reservationProcessId": "process-a92b47d0", "externalPatientId": "x",
				    "appointmentDate": "2026-09-14", "startTime": "09:30:00", "endTime": "10:00:00",
				    "professionalId": 15, "professionalFirstName": "Ana", "professionalLastName": "Gomez",
				    "patientFirstName": "Juan", "patientLastName": "Perez", "patientPhone": "+5492615551234",
				    "status": "CONFIRMED", "createdAt": "2026-09-10T14:35:00Z", "confirmedAt": "2026-09-10T14:36:00Z",
				    "cancelledAt": null, "cancellationReason": null, "campoNuevo": 1 },
				  { "reservationId": 8252, "reservationProcessId": "process-2", "status": "CANCELLED",
				    "cancelledAt": "2026-09-11T15:10:00Z", "cancellationReason": "Cambio solicitado" } ]
				""", 2);

		List<CatedraAppointment> appointments = adapter().findAppointments(PATIENT);

		assertThat(appointments).hasSize(2);
		CatedraAppointment confirmed = appointments.get(0);
		assertThat(confirmed.getReservationProcessId()).isEqualTo("process-a92b47d0");
		assertThat(confirmed.getReservationId()).isEqualTo(8251L);
		assertThat(confirmed.getStatus()).isEqualTo(CatedraAppointmentStatus.CONFIRMED);
		assertThat(confirmed.getConfirmedAt()).isEqualTo(Instant.parse("2026-09-10T14:36:00Z"));
		CatedraAppointment cancelled = appointments.get(1);
		assertThat(cancelled.getStatus()).isEqualTo(CatedraAppointmentStatus.CANCELLED);
		assertThat(cancelled.getCancelledAt()).isEqualTo(Instant.parse("2026-09-11T15:10:00Z"));
		assertThat(cancelled.getCancellationReason()).isEqualTo("Cambio solicitado");
	}

	@Test
	void filtersByThePatientAndSendsTheTechnicalToken() {
		// La cátedra falsa solo responde con ese paciente exacto y ese encabezado: si el pedido no filtra, no hay respuesta.
		var mapping = FakeCatedra.appointmentsMapping(PATIENT.toString(), 0, 200, "[]", 0);
		((tools.jackson.databind.node.ObjectNode) mapping.get("request")).putObject("headers")
				.putObject("Authorization").put("equalTo", "Bearer " + FakeCatedra.TECHNICAL_TOKEN);
		FakeServer.stub(catedra, mapping.toString());

		assertThat(adapter().findAppointments(PATIENT)).isEmpty();
		assertThat(FakeServer.countRequestsToPath(catedra, "GET", FakeCatedra.APPOINTMENTS_PATH)).isEqualTo(1);
	}

	@Test
	void readsEveryPageUntilItHasTheTotal() {
		stub(0, 200, page(1, 100), 150);
		stub(1, 200, page(101, 150), 150);

		assertThat(adapter().findAppointments(PATIENT)).hasSize(150);
		assertThat(FakeServer.countRequestsToPath(catedra, "GET", FakeCatedra.APPOINTMENTS_PATH)).isEqualTo(2);
	}

	@Test
	void anUnknownStatusIsNotAnErrorAndDoesNotHideTheOthers() {
		stub(0, 200, """
				[ { "reservationProcessId": "p-1", "status": "ALGO_NUEVO" },
				  { "reservationProcessId": "p-2", "status": "FAILED" } ]
				""", 2);

		List<CatedraAppointment> appointments = adapter().findAppointments(PATIENT);

		assertThat(appointments).extracting(CatedraAppointment::getStatus)
				.containsExactly(CatedraAppointmentStatus.UNKNOWN, CatedraAppointmentStatus.FAILED);
	}

	@Test
	void reportsTheCatedraAsUnavailableWhenItFails() {
		stub(0, 503, "", 0);

		assertThatThrownBy(() -> adapter().findAppointments(PATIENT))
				.isInstanceOfSatisfying(ReservationException.class,
						e -> assertThat(e.getCode()).isEqualTo(ReservationException.Code.INTEGRATION_UNAVAILABLE));
		// Una lectura: el pedido y un reintento (ADR-0029).
		assertThat(FakeServer.countRequestsToPath(catedra, "GET", FakeCatedra.APPOINTMENTS_PATH)).isEqualTo(2);
	}

	@Test
	void reportsTheCatedraAsUnavailableWhenItRejectsTheToken() {
		stub(0, 401, "", 0);

		assertThatThrownBy(() -> adapter().findAppointments(PATIENT))
				.isInstanceOfSatisfying(ReservationException.class, e -> assertThat(e.getMessage())
						.isEqualTo("The catedra rejected the technical JWT"));
	}

	private static String page(int from, int to) {
		StringBuilder json = new StringBuilder("[");
		for (int id = from; id <= to; id++) {
			json.append(id == from ? "" : ",").append("{\"reservationId\":").append(id)
					.append(",\"reservationProcessId\":\"process-").append(id).append("\",\"status\":\"CONFIRMED\"}");
		}
		return json.append("]").toString();
	}

	private static void stub(int page, int status, String body, int total) {
		FakeServer.stub(catedra, FakeCatedra.appointmentsMapping(PATIENT.toString(), page, status, body, total).toString());
	}

	private static CatedraAppointmentAdapter adapter() {
		CatedraProperties properties = new CatedraProperties(FakeServer.baseUrl(catedra), FakeCatedra.TECHNICAL_TOKEN,
				Duration.ofSeconds(1), Duration.ofMillis(500));
		return new CatedraAppointmentAdapter(new CatedraRestClientConfig().catedraRestClient(properties),
				new CatedraReadExecutor(new RemoteReadRetry()), new AppointmentMapper());
	}

}

package com.example.turnos.reservation.infrastructure.kafka.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.example.turnos.reservation.domain.model.ReservationEvent;
import com.example.turnos.reservation.domain.model.ReservationEventType;

import tools.jackson.databind.json.JsonMapper;

// Los ejemplos de mensajes son los de la REF §15.4 a §15.9.
class ReservationEventMessageMapperTest {

	private final ReservationEventMessageMapper mapper = new ReservationEventMessageMapper(JsonMapper.builder().build());

	@Test
	void readsAnAdditionalInformationRequested() {
		Optional<ReservationEvent> event = mapper.toDomain("""
				{"eventId":"request-event-8a31","eventType":"AdditionalInformationRequested",
				"reservationProcessId":"process-a92b47d0","holdId":"hold-6c0c2f41","requiredField":"PHONE_NUMBER",
				"message":"Ingrese un numero de telefono para continuar.","expiresAt":"2026-09-10T14:44:50Z",
				"occurredAt":"2026-09-10T14:35:10Z","schemaVersion":1}
				""");

		assertThat(event).isPresent();
		assertThat(event.get().getEventId()).isEqualTo("request-event-8a31");
		assertThat(event.get().getType()).isEqualTo(ReservationEventType.INFORMATION_REQUESTED);
		assertThat(event.get().getReservationProcessId()).isEqualTo("process-a92b47d0");
		assertThat(event.get().getMessage()).isEqualTo("Ingrese un numero de telefono para continuar.");
		assertThat(event.get().getExpiresAt()).isEqualTo(Instant.parse("2026-09-10T14:44:50Z"));
	}

	@Test
	void readsAnAppointmentConfirmedWithItsReservationId() {
		Optional<ReservationEvent> event = mapper.toDomain("""
				{"eventId":"confirmation-event-7d10","eventType":"AppointmentConfirmed","groupId":"g",
				"reservationProcessId":"process-a92b47d0","holdId":"h","requestEventId":"r","reservationId":8251,
				"professionalId":15,"date":"2026-09-14","startTime":"09:30","status":"CONFIRMED",
				"occurredAt":"2026-09-10T14:36:01Z","schemaVersion":1}
				""");

		assertThat(event).isPresent();
		assertThat(event.get().getType()).isEqualTo(ReservationEventType.APPOINTMENT_CONFIRMED);
		assertThat(event.get().getReservationId()).isEqualTo(8251L);
	}

	@ParameterizedTest
	@CsvSource({ "AdditionalInformationRejected,INFORMATION_REJECTED", "AppointmentProcessExpired,PROCESS_EXPIRED",
			"AppointmentProcessInvalid,PROCESS_INVALID" })
	void readsTheOtherEventTypes(String eventType, ReservationEventType expected) {
		Optional<ReservationEvent> event = mapper.toDomain("{\"eventId\":\"e-1\",\"eventType\":\"" + eventType
				+ "\",\"reservationProcessId\":\"p-1\",\"reason\":\"MOTIVO\",\"schemaVersion\":1}");

		assertThat(event).isPresent();
		assertThat(event.get().getType()).isEqualTo(expected);
		assertThat(event.get().getReason()).isEqualTo("MOTIVO");
	}

	// El ejemplo de la REF §15.10. reason puede venir en null.
	@Test
	void readsAnAppointmentCancelledWithoutReason() {
		Optional<ReservationEvent> event = mapper.toDomain("""
				{"eventId":"cancellation-event-7d14","eventType":"AppointmentCancelled","groupId":"g",
				"reservationProcessId":"process-a92b47d0","holdId":"h","requestEventId":"r","reservationId":8251,
				"professionalId":15,"date":"2026-09-14","startTime":"09:30","status":"CANCELLED","reason":null,
				"occurredAt":"2026-09-11T15:10:00Z","schemaVersion":1}
				""");

		assertThat(event).isPresent();
		assertThat(event.get().getType()).isEqualTo(ReservationEventType.APPOINTMENT_CANCELLED);
		assertThat(event.get().getReason()).isNull();
		assertThat(event.get().getOccurredAt()).isEqualTo(Instant.parse("2026-09-11T15:10:00Z"));
	}

	@Test
	void ignoresUnknownFieldsCompatibleWithV1() {
		Optional<ReservationEvent> event = mapper.toDomain("""
				{"eventId":"e-1","eventType":"AppointmentProcessExpired","reservationProcessId":"p-1",
				"campoNuevo":{"a":1},"schemaVersion":1}
				""");

		assertThat(event).isPresent();
	}

	@Test
	void discardsAnUnknownEventType() {
		assertThat(mapper.toDomain("{\"eventId\":\"e-1\",\"eventType\":\"AlgoNuevo\","
				+ "\"reservationProcessId\":\"p-1\",\"schemaVersion\":1}")).isEmpty();
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			esto no es JSON
			{"eventType":"AppointmentConfirmed","reservationProcessId":"p-1"}
			{"eventId":"e-1","reservationProcessId":"p-1"}
			{"eventId":"e-1","eventType":"AppointmentConfirmed"}
			{"eventId":"e-1","eventType":"AppointmentConfirmed","reservationProcessId":"p-1","schemaVersion":2}
			{"eventId":"e-1","eventType":"AdditionalInformationRequested","reservationProcessId":"p-1","expiresAt":"ayer"}
			""")
	void discardsAMessageThatCannotBeUsed(String json) {
		assertThat(mapper.toDomain(json)).isEmpty();
	}

	@Test
	void discardsAnEmptyMessage() {
		assertThat(mapper.toDomain(null)).isEmpty();
	}

}

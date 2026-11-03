package com.example.turnos.reservation.infrastructure.kafka.mapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.example.turnos.reservation.domain.model.ReservationEvent;
import com.example.turnos.reservation.domain.model.ReservationEventType;
import com.example.turnos.reservation.infrastructure.kafka.dto.ReservationEventMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Convierte el texto de un mensaje del topic de acciones en un evento de dominio. Devuelve vacío, y
 * registra el motivo, si el mensaje no se puede usar: formato inválido, tipo desconocido o un dato
 * obligatorio ausente (ADR-0031). Nunca lanza: un mensaje problemático no tiene que detener el consumo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationEventMessageMapper {

	private static final int SUPPORTED_SCHEMA_VERSION = 1;

	private final JsonMapper jsonMapper;

	public Optional<ReservationEvent> toDomain(String json) {
		ReservationEventMessage message;
		try {
			message = json == null ? null : jsonMapper.readValue(json, ReservationEventMessage.class);
		} catch (JacksonException e) {
			log.warn("Reservation event discarded: it is not valid JSON");
			return Optional.empty();
		}
		if (message == null) {
			log.warn("Reservation event discarded: it is empty");
			return Optional.empty();
		}
		if (isBlank(message.eventId()) || isBlank(message.eventType()) || isBlank(message.reservationProcessId())) {
			log.warn("Reservation event discarded: eventId, eventType or reservationProcessId is missing");
			return Optional.empty();
		}
		if (message.schemaVersion() != null && message.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
			log.warn("Event {} discarded: unsupported schemaVersion {}", message.eventId(), message.schemaVersion());
			return Optional.empty();
		}
		ReservationEventType type = typeOf(message.eventType());
		if (type == null) {
			// Un tipo nuevo de la cátedra que este servicio todavía no conoce (ADR-0031).
			log.info("Event {} ignored: eventType {} is not handled", message.eventId(), message.eventType());
			return Optional.empty();
		}
		try {
			return Optional.of(ReservationEvent.builder()
					.eventId(message.eventId())
					.type(type)
					.reservationProcessId(message.reservationProcessId())
					.message(message.message())
					.expiresAt(instantOrNull(message.expiresAt()))
					.reason(message.reason())
					.reservationId(message.reservationId())
					.occurredAt(instantOrNull(message.occurredAt()))
					.build());
		} catch (DateTimeParseException e) {
			log.warn("Event {} discarded: a date is not ISO-8601", message.eventId());
			return Optional.empty();
		}
	}

	private static ReservationEventType typeOf(String eventType) {
		return switch (eventType) {
			case "AdditionalInformationRequested" -> ReservationEventType.INFORMATION_REQUESTED;
			case "AdditionalInformationRejected" -> ReservationEventType.INFORMATION_REJECTED;
			case "AppointmentConfirmed" -> ReservationEventType.APPOINTMENT_CONFIRMED;
			case "AppointmentProcessExpired" -> ReservationEventType.PROCESS_EXPIRED;
			case "AppointmentProcessInvalid" -> ReservationEventType.PROCESS_INVALID;
			case "AppointmentCancelled" -> ReservationEventType.APPOINTMENT_CANCELLED;
			default -> null;
		};
	}

	private static Instant instantOrNull(String value) {
		return isBlank(value) ? null : Instant.parse(value);
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

}

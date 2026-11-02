package com.example.turnos.reservation.infrastructure.kafka.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Un evento del topic de acciones tal como lo publica la cátedra (REF §15.4 a §15.10). Reúne los campos
 * de todos los tipos y ignora los que no conoce: los consumidores toleran campos nuevos de la v1
 * (REF §15.1). Los instantes quedan como texto para que un valor mal formado lo rechace el mapper y no
 * el deserializador.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReservationEventMessage(
		String eventId,
		String eventType,
		String reservationProcessId,
		String message,
		String expiresAt,
		String reason,
		Long reservationId,
		String occurredAt,
		Integer schemaVersion) {
}

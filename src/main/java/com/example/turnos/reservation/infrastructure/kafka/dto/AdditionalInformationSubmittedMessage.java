package com.example.turnos.reservation.infrastructure.kafka.dto;

/**
 * El mensaje que la cátedra espera en el topic de teléfono (REF §15.5). Todos los campos son
 * obligatorios, y {@code eventType} y {@code schemaVersion} tienen que valer exactamente lo que valen acá.
 */
public record AdditionalInformationSubmittedMessage(
		String eventId,
		String eventType,
		String groupId,
		String reservationProcessId,
		String holdId,
		String requestEventId,
		Information information,
		String occurredAt,
		int schemaVersion) {

	public record Information(String phoneNumber) {
	}

}

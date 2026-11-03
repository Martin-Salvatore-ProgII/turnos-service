package com.example.turnos.reservation.domain.model;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Lo que se le envía a la cátedra como respuesta al pedido de teléfono (REF §15.5). Todos los campos son obligatorios. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhoneSubmission {
	// Nuevo en cada envío del usuario y el mismo en los reintentos de ese envío (ADR-0029).
	private String eventId;
	private String reservationProcessId;
	private String holdId;
	// El eventId del pedido al que se responde. Si no coincide, la cátedra invalida el proceso (REF §15.4).
	private String requestEventId;
	// Ya normalizado.
	private String phoneNumber;
	private Instant occurredAt;
}

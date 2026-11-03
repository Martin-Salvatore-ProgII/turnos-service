package com.example.turnos.reservation.domain.model;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un evento de la cátedra sobre un proceso de reserva, ya interpretado. Reúne los campos de todos los
 * tipos: cada tipo usa solo los suyos y el resto queda en {@code null} (REF §15).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationEvent {
	// Clave de idempotencia: un evento con el mismo eventId se procesa una sola vez (ADR-0008).
	private String eventId;
	private ReservationEventType type;
	private String reservationProcessId;
	// Pedido de teléfono y rechazo: lo que la cátedra le dice al usuario y hasta cuándo vale el proceso.
	private String message;
	private Instant expiresAt;
	// Rechazo, vencimiento, invalidez y cancelación: el motivo que informa la cátedra (en la cancelación
	// puede faltar).
	private String reason;
	// Confirmación: el identificador de la reserva en la cátedra.
	private Long reservationId;
	private Instant occurredAt;
}

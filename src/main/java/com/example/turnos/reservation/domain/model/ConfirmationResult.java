package com.example.turnos.reservation.domain.model;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Cómo terminó el pedido de confirmación inicial de un hold (REF §10). Cada resultado lleva a una
 * transición distinta del proceso (CU-04, paso 6 y sus alternativos).
 */
@Getter
@AllArgsConstructor
public class ConfirmationResult {

	public enum Outcome {
		// La cátedra aceptó la confirmación (202), ahora o en un intento anterior cuya respuesta se perdió.
		ACCEPTED,
		// La cátedra respondió HOLD_EXPIRED.
		EXPIRED,
		// La cátedra rechazó la confirmación por otro motivo.
		REJECTED,
		// No hubo respuesta después de los reintentos: no se sabe si la cátedra la recibió.
		NO_RESPONSE
	}

	private final Outcome outcome;
	// Nuevo vencimiento del proceso. Puede faltar en ACCEPTED: la respuesta de una confirmación repetida
	// no lo trae, y sigue valiendo el del hold.
	private final Instant expiresAt;
	// Motivo de EXPIRED o REJECTED: el code de la cátedra o uno de ReservationFailureReason.
	private final String failureReason;

	public static ConfirmationResult accepted(Instant expiresAt) {
		return new ConfirmationResult(Outcome.ACCEPTED, expiresAt, null);
	}

	public static ConfirmationResult expired(String failureReason) {
		return new ConfirmationResult(Outcome.EXPIRED, null, failureReason);
	}

	public static ConfirmationResult rejected(String failureReason) {
		return new ConfirmationResult(Outcome.REJECTED, null, failureReason);
	}

	public static ConfirmationResult noResponse() {
		return new ConfirmationResult(Outcome.NO_RESPONSE, null, null);
	}

}

package com.example.turnos.reservation.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Cómo terminó el pedido de un hold: creado, o no creado con un motivo. Un hold no creado no es una
 * excepción: es un resultado esperado que deja el proceso en FAILED con ese motivo (CU-04, 5a y 5b).
 */
@Getter
@AllArgsConstructor
public class HoldResult {

	private final Hold hold;
	// El code de la cátedra (SLOT_ALREADY_HELD, INVALID_SLOT, ...) o uno de ReservationFailureReason.
	private final String failureReason;

	public static HoldResult created(Hold hold) {
		return new HoldResult(hold, null);
	}

	public static HoldResult failed(String failureReason) {
		return new HoldResult(null, failureReason);
	}

	public boolean isCreated() {
		return hold != null;
	}

}

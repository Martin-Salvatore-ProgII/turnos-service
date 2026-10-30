package com.example.turnos.reservation.application.exception;

import java.util.UUID;

/**
 * El proceso cambió en la base entre que se leyó y se quiso guardar (ADR-0068). Quien la recibe
 * vuelve a leer el proceso y aplica el disparador sobre el estado actual.
 */
public class ReservationProcessConflictException extends RuntimeException {

	public ReservationProcessConflictException(UUID id) {
		super("Reservation process " + id + " was modified concurrently");
	}

}

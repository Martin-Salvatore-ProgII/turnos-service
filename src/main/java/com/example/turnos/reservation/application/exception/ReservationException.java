package com.example.turnos.reservation.application.exception;

import lombok.Getter;

@Getter
public class ReservationException extends RuntimeException {

	// El código funcional del contrato: con él decide el cliente, no con el texto del mensaje.
	public enum Code {
		DATE_OUT_OF_RANGE,
		PROFESSIONAL_NOT_FOUND,
		PROFESSIONAL_DISABLED,
		INVALID_SLOT,
		ACTIVE_RESERVATION_EXISTS,
		RESERVATION_NOT_FOUND
	}

	private final Code code;

	public ReservationException(Code code, String message) {
		super(message);
		this.code = code;
	}

}

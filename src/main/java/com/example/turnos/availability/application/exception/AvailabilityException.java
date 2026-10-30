package com.example.turnos.availability.application.exception;

import lombok.Getter;

@Getter
public class AvailabilityException extends RuntimeException {

	// El código funcional del contrato: con él decide el cliente, no con el texto del mensaje.
	public enum Code {
		DATE_OUT_OF_RANGE,
		PROFESSIONAL_NOT_FOUND,
		PROFESSIONAL_DISABLED
	}

	private final Code code;

	public AvailabilityException(Code code, String message) {
		super(message);
		this.code = code;
	}

}

package com.example.turnos.user.application.exception;

import lombok.Getter;

@Getter
public class UserException extends RuntimeException {

	// El código funcional del contrato: con él decide el cliente, no con el texto del mensaje.
	public enum Code {
		USERNAME_ALREADY_EXISTS,
		EMAIL_ALREADY_EXISTS
	}

	private final Code code;

	public UserException(Code code, String message) {
		super(message);
		this.code = code;
	}

}

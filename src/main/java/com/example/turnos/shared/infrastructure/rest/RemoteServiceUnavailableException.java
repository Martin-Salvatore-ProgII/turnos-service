package com.example.turnos.shared.infrastructure.rest;

/**
 * Un servicio externo no respondió después del reintento. El mensaje es seguro para logs: dice el
 * servicio y el tipo de falla, nunca su dirección.
 */
public class RemoteServiceUnavailableException extends RuntimeException {

	public RemoteServiceUnavailableException(String message) {
		super(message);
	}

}

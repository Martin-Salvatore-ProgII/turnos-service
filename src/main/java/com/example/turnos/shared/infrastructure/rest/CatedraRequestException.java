package com.example.turnos.shared.infrastructure.rest;

/**
 * Un pedido a la cátedra falló. El mensaje es seguro para logs y para la respuesta al
 * cliente: dice el tipo de falla, nunca la dirección de la cátedra ni el JWT.
 */
public class CatedraRequestException extends RuntimeException {

	public CatedraRequestException(String message) {
		super(message);
	}

}

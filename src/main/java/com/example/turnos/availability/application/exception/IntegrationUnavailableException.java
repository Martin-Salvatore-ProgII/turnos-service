package com.example.turnos.availability.application.exception;

/**
 * La cátedra no entregó las ocupaciones. Sin ellas no se muestran slots, porque no se pueden
 * descontar los ocupados (CU-03 4a). El mensaje no muestra la dirección de la cátedra.
 */
public class IntegrationUnavailableException extends RuntimeException {

	public IntegrationUnavailableException(String message) {
		super(message);
	}

}

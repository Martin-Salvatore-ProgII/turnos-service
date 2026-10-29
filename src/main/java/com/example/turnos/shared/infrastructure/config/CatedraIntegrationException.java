package com.example.turnos.shared.infrastructure.config;

/**
 * La cátedra entregó la integración, pero no está lista para usarse. Corta el arranque del
 * servicio (ADR-0005).
 */
public class CatedraIntegrationException extends RuntimeException {

	public CatedraIntegrationException(String message) {
		super(message);
	}

}

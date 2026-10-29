package com.example.turnos.catalog.application.exception;

/**
 * El catálogo rechazó el JWT del usuario que turnos le reenvió. Pasa si el token vence entre la
 * llamada de la app y la del catálogo (ADR-0034).
 */
public class CatalogTokenRejectedException extends RuntimeException {

	public CatalogTokenRejectedException() {
		super("The catalog rejected the user token");
	}

}

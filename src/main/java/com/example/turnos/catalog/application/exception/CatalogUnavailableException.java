package com.example.turnos.catalog.application.exception;

/**
 * El catálogo no respondió, respondió con un error o todavía no tiene una versión local. Sin sus
 * datos vigentes no se inicia la operación (CU-03 3c, ENUNCIADO §8). El mensaje no muestra la
 * dirección del catálogo.
 */
public class CatalogUnavailableException extends RuntimeException {

	public CatalogUnavailableException(String message) {
		super(message);
	}

}

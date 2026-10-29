package com.example.turnos.shared.infrastructure.rest;

import java.util.function.Supplier;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

/**
 * Ejecuta una lectura a la API de la cátedra con las reglas comunes a todas: el reintento de
 * {@link RemoteReadRetry} y una {@link CatedraRequestException} con un mensaje seguro ante cualquier
 * falla. Es la lectura de todo cliente REST hacia la cátedra: el pedido de la integración al arrancar
 * y, más adelante, las ocupaciones (ADR-0063).
 */
@Component
@RequiredArgsConstructor
public class CatedraReadExecutor {

	private static final String SERVICE_NAME = "Catedra";

	private final RemoteReadRetry remoteReadRetry;

	public <T> T read(Supplier<T> request) {
		try {
			return remoteReadRetry.read(SERVICE_NAME, () -> readOnce(request));
		} catch (RemoteServiceUnavailableException e) {
			throw new CatedraRequestException(e.getMessage());
		}
	}

	// Un 4xx no se reintenta: repetir el mismo pedido da la misma respuesta. Los errores se
	// distinguen por el status, nunca por el texto del cuerpo (REF §13).
	private <T> T readOnce(Supplier<T> request) {
		try {
			T body = request.get();
			if (body == null) {
				throw new CatedraRequestException("Unexpected response from the catedra: empty body");
			}
			return body;
		} catch (HttpClientErrorException e) {
			if (e.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED) || e.getStatusCode().isSameCodeAs(HttpStatus.FORBIDDEN)) {
				throw new CatedraRequestException("The catedra rejected the technical JWT");
			}
			throw new CatedraRequestException("Unexpected response from the catedra: HTTP " + e.getStatusCode().value());
		} catch (ResourceAccessException | HttpServerErrorException e) {
			// Los decide RemoteReadRetry: se reintentan.
			throw e;
		} catch (RestClientException e) {
			throw new CatedraRequestException("Unexpected response from the catedra: unreadable body");
		}
	}

}

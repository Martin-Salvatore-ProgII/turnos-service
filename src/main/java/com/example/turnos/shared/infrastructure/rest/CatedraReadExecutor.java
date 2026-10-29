package com.example.turnos.shared.infrastructure.rest;

import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

/**
 * Ejecuta una lectura a la API de la cátedra con las reglas comunes a todas: un intento más si la
 * cátedra no responde (ADR-0029) y una {@link CatedraRequestException} con un mensaje seguro ante
 * cualquier falla. Es la lectura de todo cliente REST hacia la cátedra: el pedido de la integración al arrancar
 * y, más adelante, las ocupaciones (ADR-0063).
 */
@Slf4j
@Component
public class CatedraReadExecutor {

	// Es una lectura: un intento más (ADR-0029). Un corte breve no alcanza para dar la cátedra
	// por caída.
	private static final int MAX_ATTEMPTS = 2;

	public <T> T read(Supplier<T> request) {
		for (int attempt = 1; ; attempt++) {
			try {
				return readOnce(request);
			} catch (ResourceAccessException | HttpServerErrorException e) {
				// Sin conexión, timeout o error del lado de la cátedra. Se informa solo el tipo de
				// falla: el mensaje original incluye la URL de la cátedra, que no va a los logs.
				String reason = e instanceof HttpServerErrorException serverError
						? "HTTP " + serverError.getStatusCode().value()
						: e.getMostSpecificCause().getClass().getSimpleName();
				if (attempt == MAX_ATTEMPTS) {
					throw new CatedraRequestException("Catedra service unavailable (" + reason + ")");
				}
				log.warn("Catedra service unavailable ({}), attempt {} of {}", reason, attempt, MAX_ATTEMPTS);
			}
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
			throw e;
		} catch (RestClientException e) {
			throw new CatedraRequestException("Unexpected response from the catedra: unreadable body");
		}
	}

}

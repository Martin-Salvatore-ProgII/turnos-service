package com.example.turnos.shared.infrastructure.rest;

import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * El reintento de toda lectura a un servicio externo, la cátedra o el catálogo: un intento más si el
 * servicio no responde o falla de su lado (ADR-0029). Lo comparten {@link CatedraReadExecutor} y el
 * cliente del catálogo (ADR-0063); cada uno traduce después las respuestas propias de su servicio.
 */
@Slf4j
@Component
public class RemoteReadRetry {

	// Es una lectura: un intento más (ADR-0029). Un corte breve no alcanza para dar el servicio por
	// caído.
	private static final int MAX_ATTEMPTS = 2;

	public <T> T read(String serviceName, Supplier<T> request) {
		for (int attempt = 1; ; attempt++) {
			try {
				return request.get();
			} catch (ResourceAccessException | HttpServerErrorException e) {
				// Sin conexión, timeout o error del lado del servicio. Se informa solo el tipo de falla:
				// el mensaje original incluye la URL del servicio, que no va a los logs.
				String reason = e instanceof HttpServerErrorException serverError
						? "HTTP " + serverError.getStatusCode().value()
						: e.getMostSpecificCause().getClass().getSimpleName();
				if (attempt == MAX_ATTEMPTS) {
					throw new RemoteServiceUnavailableException(serviceName + " service unavailable (" + reason + ")");
				}
				log.warn("{} service unavailable ({}), attempt {} of {}", serviceName, reason, attempt, MAX_ATTEMPTS);
			}
		}
	}

}

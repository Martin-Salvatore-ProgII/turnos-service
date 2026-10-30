package com.example.turnos.shared.infrastructure.rest;

import java.time.Duration;
import java.util.function.Supplier;

import com.example.turnos.shared.infrastructure.config.CommandRetryProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * El reintento de las operaciones con efectos que son seguras de repetir, porque el otro lado reconoce
 * lo ya hecho: la confirmación inicial y, más adelante, la cancelación (ADR-0029). Es el gemelo de
 * {@link RemoteReadRetry}, con más intentos y una espera creciente entre ellos. Qué operación se
 * reintenta lo decide quien la usa: crear un hold, por ejemplo, nunca pasa por acá (ADR-0028).
 */
@Slf4j
@Component
@EnableConfigurationProperties(CommandRetryProperties.class)
public class RemoteCommandRetry {

	private final int retries;
	private final Duration initialBackoff;

	public RemoteCommandRetry(CommandRetryProperties properties) {
		this.retries = properties.retries();
		this.initialBackoff = properties.initialBackoff();
	}

	// Solo se repite si no hubo respuesta o el otro lado falló (sin conexión, timeout, 5xx). Un 4xx es
	// una respuesta: sigue de largo y la interpreta quien llamó. Cada reintento queda en el log con la
	// operación, que incluye su identificador (ADR-0029); nunca la URL del servicio.
	public <T> T execute(String operation, Supplier<T> command) {
		Duration backoff = initialBackoff;
		for (int attempt = 1; ; attempt++) {
			try {
				return command.get();
			} catch (ResourceAccessException | HttpServerErrorException e) {
				String reason = e instanceof HttpServerErrorException serverError
						? "HTTP " + serverError.getStatusCode().value()
						: e.getMostSpecificCause().getClass().getSimpleName();
				if (attempt > retries) {
					throw new RemoteServiceUnavailableException(operation + " failed after " + attempt
							+ " attempts (" + reason + ")");
				}
				log.warn("{} failed ({}), retry {} of {} in {} ms", operation, reason, attempt, retries,
						backoff.toMillis());
				sleep(backoff, operation);
				backoff = backoff.multipliedBy(2);
			}
		}
	}

	private static void sleep(Duration backoff, String operation) {
		try {
			Thread.sleep(backoff);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RemoteServiceUnavailableException(operation + " interrupted while waiting to retry");
		}
	}

}

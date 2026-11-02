package com.example.turnos.shared.infrastructure.rest;

import org.springframework.core.NestedRuntimeException;

/**
 * Una falla pasajera del otro lado que no es HTTP (por ejemplo, el broker de Kafka no confirmó a
 * tiempo): {@link RemoteCommandRetry} la trata como un corte o un 5xx y repite la operación.
 */
public class TransientRemoteFailureException extends NestedRuntimeException {

	public TransientRemoteFailureException(String message, Throwable cause) {
		super(message, cause);
	}

}

package com.example.turnos.shared.infrastructure.rest;

import java.net.SocketTimeoutException;

import org.apache.hc.client5.http.ConnectTimeoutException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Qué tipo de timeout sufrió un pedido, sin que quien lo pregunta conozca la librería HTTP que usa
 * {@link NoRetryRequestFactory}.
 */
public final class HttpTimeouts {

	private HttpTimeouts() {
	}

	// Solo el timeout de respuesta deja la duda de si la operación se hizo: el pedido llegó y no se
	// supo el resultado. Un timeout de conexión es como no tener red: el pedido nunca salió.
	public static boolean isResponseTimeout(ResourceAccessException e) {
		for (Throwable cause = e; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConnectTimeoutException) {
				return false;
			}
			if (cause instanceof SocketTimeoutException) {
				return true;
			}
		}
		return false;
	}

}

package com.example.turnos.shared.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;

import org.apache.hc.client5.http.ConnectTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

// Solo el timeout de respuesta deja la duda de si la operación se hizo (ADR-0028).
class HttpTimeoutsTest {

	@Test
	void aReadTimeoutIsAResponseTimeout() {
		ResourceAccessException e = new ResourceAccessException("I/O error", new SocketTimeoutException("Read timed out"));

		assertThat(HttpTimeouts.isResponseTimeout(e)).isTrue();
	}

	@Test
	void aConnectTimeoutIsNotAResponseTimeout() {
		ResourceAccessException e = new ResourceAccessException("I/O error",
				new ConnectTimeoutException("connect timed out"));

		assertThat(HttpTimeouts.isResponseTimeout(e)).isFalse();
	}

	@Test
	void otherFailuresAreNotAResponseTimeout() {
		ResourceAccessException e = new ResourceAccessException("I/O error", new ConnectException("refused"));

		assertThat(HttpTimeouts.isResponseTimeout(e)).isFalse();
	}

	@Test
	void aTimeoutNestedInOtherCausesIsFound() {
		ResourceAccessException e = new ResourceAccessException("I/O error",
				new IOException("wrapper", new SocketTimeoutException("Read timed out")));

		assertThat(HttpTimeouts.isResponseTimeout(e)).isTrue();
	}

	@Test
	void aTimeoutFromTheRealClientIsAResponseTimeout() {
		// El cliente de verdad, contra un servidor que acepta y no responde: se corta por el timeout
		// de lectura y se clasifica como timeout de respuesta.
		try (ServerSocket silent = new ServerSocket(0)) {
			Thread acceptor = new Thread(() -> {
				try (Socket socket = silent.accept()) {
					Thread.sleep(3_000);
				}
				catch (Exception ex) {
					// Termina al cerrarse el servidor.
				}
			});
			acceptor.setDaemon(true);
			acceptor.start();
			var client = RestClient.builder()
					.baseUrl("http://localhost:" + silent.getLocalPort())
					.requestFactory(NoRetryRequestFactory.create(Duration.ofSeconds(1), Duration.ofMillis(300)))
					.build();

			try {
				client.get().uri("/x").retrieve().body(String.class);
				fail("Se esperaba un timeout");
			}
			catch (ResourceAccessException e) {
				assertThat(HttpTimeouts.isResponseTimeout(e)).isTrue();
			}
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}

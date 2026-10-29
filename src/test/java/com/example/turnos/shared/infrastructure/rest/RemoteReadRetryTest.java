package com.example.turnos.shared.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

// El reintento común a toda lectura a un servicio externo (ADR-0029, ADR-0063), sin red: cada
// pedido es un Supplier que falla como fallaría el RestClient. Las respuestas propias de la cátedra
// y del catálogo se prueban en CatedraReadExecutorTest y CatalogServiceAdapterTest.
class RemoteReadRetryTest {

	private final RemoteReadRetry retry = new RemoteReadRetry();

	private final AtomicInteger attempts = new AtomicInteger();

	@Test
	void returnsTheResultOfTheFirstAttempt() {
		assertThat(retry.read("Catalog", counting(() -> "ok"))).isEqualTo("ok");
		assertThat(attempts).hasValue(1);
	}

	@Test
	void retriesOnceAndFailsWhenTheServiceDoesNotAnswer() {
		assertThatThrownBy(() -> retry.read("Catalog", counting(() -> {
			throw new ResourceAccessException("timeout", new HttpTimeoutException("request timed out"));
		})))
				.isInstanceOf(RemoteServiceUnavailableException.class)
				.hasMessage("Catalog service unavailable (HttpTimeoutException)");
		assertThat(attempts).hasValue(2);
	}

	@Test
	void retriesOnceAndFailsOnAServerError() {
		assertThatThrownBy(() -> retry.read("Catalog", counting(() -> {
			throw HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", HttpHeaders.EMPTY, null, null);
		})))
				.isInstanceOf(RemoteServiceUnavailableException.class)
				.hasMessage("Catalog service unavailable (HTTP 503)");
		assertThat(attempts).hasValue(2);
	}

	@Test
	void recoversWhenOnlyTheFirstAttemptFails() {
		String result = retry.read("Catalog", counting(() -> {
			if (attempts.get() == 1) {
				throw new ResourceAccessException("reset", new ConnectException("Connection reset"));
			}
			return "ok";
		}));

		assertThat(result).isEqualTo("ok");
		assertThat(attempts).hasValue(2);
	}

	@Test
	void doesNotRetryAClientError() {
		// Un 4xx no se repite: lo traduce quien conoce el servicio.
		assertThatThrownBy(() -> retry.read("Catalog", counting(() -> {
			throw HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, null, null);
		})))
				.isInstanceOf(HttpClientErrorException.class);
		assertThat(attempts).hasValue(1);
	}

	@Test
	void doesNotShowTheAddressOfTheService() {
		// El mensaje original del RestClient incluye la URL del pedido.
		String address = "10.1.2.3:8081";

		assertThatThrownBy(() -> retry.read("Catalog", () -> {
			throw new ResourceAccessException("I/O error on GET request for \"http://" + address + "/api\"",
					new ConnectException("Connection refused"));
		}))
				.hasMessage("Catalog service unavailable (ConnectException)")
				.hasNoCause();
	}

	private <T> Supplier<T> counting(Supplier<T> request) {
		return () -> {
			attempts.incrementAndGet();
			return request.get();
		};
	}

}

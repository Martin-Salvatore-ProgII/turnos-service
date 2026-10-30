package com.example.turnos.shared.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.example.turnos.shared.infrastructure.config.CommandRetryProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

// El reintento de las operaciones seguras de repetir (ADR-0029), sin red: cada pedido es un Supplier
// que falla como fallaría el RestClient. Las esperas son de pocos milisegundos para que la prueba no
// tarde; lo que se prueba es cuántas veces se intenta y que la espera crezca.
class RemoteCommandRetryTest {

	private final RemoteCommandRetry retry = new RemoteCommandRetry(
			new CommandRetryProperties(2, Duration.ofMillis(20)));

	private final AtomicInteger attempts = new AtomicInteger();

	@Test
	void returnsTheResultOfTheFirstAttempt() {
		assertThat(retry.execute("Confirmation of hold-1", counting(() -> "ok"))).isEqualTo("ok");
		assertThat(attempts).hasValue(1);
	}

	@Test
	void triesThreeTimesAndFailsWhenTheServiceNeverAnswers() {
		assertThatThrownBy(() -> retry.execute("Confirmation of hold-1", counting(() -> {
			throw new ResourceAccessException("timeout", new HttpTimeoutException("request timed out"));
		})))
				.isInstanceOf(RemoteServiceUnavailableException.class)
				.hasMessage("Confirmation of hold-1 failed after 3 attempts (HttpTimeoutException)");
		assertThat(attempts).hasValue(3);
	}

	@Test
	void retriesAServerError() {
		assertThatThrownBy(() -> retry.execute("Confirmation of hold-1", counting(() -> {
			throw HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", HttpHeaders.EMPTY, null, null);
		})))
				.isInstanceOf(RemoteServiceUnavailableException.class)
				.hasMessage("Confirmation of hold-1 failed after 3 attempts (HTTP 503)");
		assertThat(attempts).hasValue(3);
	}

	@Test
	void recoversWhenALaterAttemptAnswers() {
		String result = retry.execute("Confirmation of hold-1", counting(() -> {
			if (attempts.get() < 3) {
				throw new ResourceAccessException("reset", new ConnectException("Connection reset"));
			}
			return "ok";
		}));

		assertThat(result).isEqualTo("ok");
		assertThat(attempts).hasValue(3);
	}

	@Test
	void doesNotRetryAnAnswerFromTheService() {
		// Un 4xx es una respuesta: repetir el pedido daría la misma. La interpreta quien llamó.
		assertThatThrownBy(() -> retry.execute("Confirmation of hold-1", counting(() -> {
			throw HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict", HttpHeaders.EMPTY, null, null);
		})))
				.isInstanceOf(HttpClientErrorException.class);
		assertThat(attempts).hasValue(1);
	}

	@Test
	void waitsLongerBeforeEachRetry() {
		// 20 ms y después 40 ms: al menos 60 ms en total entre los tres intentos.
		long start = System.nanoTime();
		assertThatThrownBy(() -> retry.execute("Confirmation of hold-1", counting(() -> {
			throw new ResourceAccessException("timeout");
		}))).isInstanceOf(RemoteServiceUnavailableException.class);

		assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(60));
	}

	@Test
	void withoutRetriesTriesOnlyOnce() {
		RemoteCommandRetry noRetry = new RemoteCommandRetry(new CommandRetryProperties(0, Duration.ofMillis(20)));

		assertThatThrownBy(() -> noRetry.execute("Confirmation of hold-1", counting(() -> {
			throw new ResourceAccessException("timeout");
		}))).isInstanceOf(RemoteServiceUnavailableException.class);
		assertThat(attempts).hasValue(1);
	}

	private <T> Supplier<T> counting(Supplier<T> request) {
		return () -> {
			attempts.incrementAndGet();
			return request.get();
		};
	}

}

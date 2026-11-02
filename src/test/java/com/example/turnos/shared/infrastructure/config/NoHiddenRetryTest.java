package com.example.turnos.shared.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import com.example.turnos.FakeCatedra;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

// Los únicos reintentos son los del ADR-0029: ningún cliente HTTP puede repetir un pedido por su
// cuenta. La cátedra real habla solo HTTP/1.1, y ahí el cliente de java.net.http repite un GET
// cortado. WireMock habla HTTP/2 y no lo deja ver, así que acá el servidor es un socket que
// acepta la conexión y la corta, y se cuentan las conexiones que llegan (una por pedido).
class NoHiddenRetryTest {

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);
	private static final Duration READ_TIMEOUT = Duration.ofMillis(500);

	private ServerSocket server;
	private final AtomicInteger connections = new AtomicInteger();

	@BeforeEach
	void startCuttingServer() throws IOException {
		server = new ServerSocket(0);
		Thread acceptor = new Thread(() -> {
			try {
				while (true) {
					try (Socket socket = server.accept()) {
						connections.incrementAndGet();
						socket.getInputStream().read();
					}
				}
			}
			catch (IOException e) {
				// El servidor se cerró al terminar la prueba.
			}
		});
		acceptor.setDaemon(true);
		acceptor.start();
	}

	@AfterEach
	void stopServer() throws IOException {
		server.close();
		SecurityContextHolder.clearContext();
	}

	@Test
	void theCatedraClientDoesNotRepeatAGetWhenTheConnectionIsCut() {
		CatedraProperties properties = new CatedraProperties(baseUrl(), FakeCatedra.TECHNICAL_TOKEN,
				CONNECT_TIMEOUT, READ_TIMEOUT);
		RestClient client = new CatedraRestClientConfig().catedraRestClient(properties);

		assertThatThrownBy(() -> client.get().uri("/api/any-read").retrieve().body(String.class))
				.isInstanceOf(ResourceAccessException.class);
		assertThat(connections.get()).isEqualTo(1);
	}

	@Test
	void theCatalogClientDoesNotRepeatAGetWhenTheConnectionIsCut() {
		Jwt jwt = Jwt.withTokenValue("user-token").header("alg", "none").claim("sub", "user").build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
		CatalogProperties properties = new CatalogProperties(baseUrl(), CONNECT_TIMEOUT, READ_TIMEOUT);
		RestClient client = new CatalogRestClientConfig().catalogRestClient(properties);

		assertThatThrownBy(() -> client.get().uri("/api/any-read").retrieve().body(String.class))
				.isInstanceOf(ResourceAccessException.class);
		assertThat(connections.get()).isEqualTo(1);
	}

	@Test
	void theReadTimeoutStillCutsAServerThatNeverAnswers() throws IOException {
		// Un servidor que acepta la conexión y no responde: el timeout de lectura tiene que cortar.
		try (ServerSocket silent = new ServerSocket(0)) {
			Thread acceptor = new Thread(() -> {
				try (Socket socket = silent.accept()) {
					Thread.sleep(5_000);
				}
				catch (Exception e) {
					// Termina al cerrarse el servidor.
				}
			});
			acceptor.setDaemon(true);
			acceptor.start();
			CatedraProperties properties = new CatedraProperties("http://localhost:" + silent.getLocalPort(),
					FakeCatedra.TECHNICAL_TOKEN, CONNECT_TIMEOUT, READ_TIMEOUT);
			RestClient client = new CatedraRestClientConfig().catedraRestClient(properties);

			long start = System.nanoTime();
			assertThatThrownBy(() -> client.get().uri("/api/any-read").retrieve().body(String.class))
					.isInstanceOf(ResourceAccessException.class);
			assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
		}
	}

	private String baseUrl() {
		return "http://localhost:" + server.getLocalPort();
	}

}

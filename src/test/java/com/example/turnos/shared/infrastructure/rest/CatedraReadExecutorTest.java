package com.example.turnos.shared.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Map;

import com.example.turnos.FakeCatedra;
import com.example.turnos.shared.infrastructure.config.CatedraProperties;
import com.example.turnos.shared.infrastructure.config.CatedraRestClientConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;

// Las reglas comunes a toda lectura a la cátedra, probadas una sola vez (ADR-0063). Sin Spring:
// el cliente se arma a mano, como en producción, contra una cátedra falsa (WireMock). Los
// timeouts son cortos para que las pruebas de demoras no tarden.
class CatedraReadExecutorTest {

	private static final String PATH = "/api/any-read";
	private static final String OK_RESPONSE = "\"status\": 200, \"headers\": { \"Content-Type\": \"application/json\" }, \"jsonBody\": { \"value\": \"ok\" }";

	private static final GenericContainer<?> catedra = FakeCatedra.newContainer();

	private final CatedraReadExecutor executor = new CatedraReadExecutor();

	@BeforeAll
	static void startCatedra() {
		catedra.start();
	}

	@AfterAll
	static void stopCatedra() {
		catedra.stop();
	}

	@BeforeEach
	void resetCatedra() {
		FakeCatedra.reset(catedra);
	}

	@Test
	void returnsTheBodyWhenTheCatedraAnswers() {
		FakeCatedra.stub(catedra, respondingWith(OK_RESPONSE));

		assertThat(read(client())).containsEntry("value", "ok");
		assertThat(requests()).isEqualTo(1);
	}

	@Test
	void doesNotRetryWhenTheTechnicalTokenIsRejected() {
		FakeCatedra.stub(catedra, respondingWith("\"status\": 401"));

		assertThatThrownBy(() -> read(client()))
				.isInstanceOf(CatedraRequestException.class)
				.hasMessage("The catedra rejected the technical JWT");
		assertThat(requests()).isEqualTo(1);
	}

	@Test
	void doesNotRetryOnAnUnexpectedResponse() {
		FakeCatedra.stub(catedra, respondingWith("\"status\": 404"));

		assertThatThrownBy(() -> read(client()))
				.isInstanceOf(CatedraRequestException.class)
				.hasMessage("Unexpected response from the catedra: HTTP 404");
		assertThat(requests()).isEqualTo(1);
	}

	@Test
	void failsWhenTheBodyCannotBeRead() {
		FakeCatedra.stub(catedra, respondingWith(
				"\"status\": 200, \"headers\": { \"Content-Type\": \"application/json\" }, \"body\": \"no es json\""));

		assertThatThrownBy(() -> read(client()))
				.isInstanceOf(CatedraRequestException.class)
				.hasMessage("Unexpected response from the catedra: unreadable body");
	}

	@Test
	void failsWhenThereIsNoBody() {
		FakeCatedra.stub(catedra, respondingWith("\"status\": 200"));

		assertThatThrownBy(() -> read(client()))
				.isInstanceOf(CatedraRequestException.class)
				.hasMessage("Unexpected response from the catedra: empty body");
	}

	@Test
	void retriesOnceAndFailsWhenTheCatedraDoesNotAnswerInTime() {
		FakeCatedra.stub(catedra, respondingWith(OK_RESPONSE + ", \"fixedDelayMilliseconds\": 1500"));

		assertThatThrownBy(() -> read(client()))
				.isInstanceOf(CatedraRequestException.class)
				.hasMessageStartingWith("Catedra service unavailable");
		assertThat(requests()).isEqualTo(2);
	}

	@Test
	void retriesOnceAndFailsWhenTheCatedraAnswersWithAServerError() {
		FakeCatedra.stub(catedra, respondingWith("\"status\": 503"));

		assertThatThrownBy(() -> read(client()))
				.isInstanceOf(CatedraRequestException.class)
				.hasMessage("Catedra service unavailable (HTTP 503)");
		assertThat(requests()).isEqualTo(2);
	}

	@Test
	void makesExactlyTwoAttemptsWhenTheConnectionIsAlwaysCut() {
		// Exactamente dos: si el cliente HTTP reintentara por su cuenta, llegarían más pedidos de los
		// que decide el ADR-0029, y sin dejar rastro en el log.
		FakeCatedra.stub(catedra, respondingWith("\"fault\": \"CONNECTION_RESET_BY_PEER\""));

		assertThatThrownBy(() -> read(client()))
				.isInstanceOf(CatedraRequestException.class)
				.hasMessageStartingWith("Catedra service unavailable");
		assertThat(requests()).isEqualTo(2);
	}

	@Test
	void recoversWhenOnlyTheFirstAttemptFails() {
		// Escenario de WireMock: el primer pedido corta la conexión y el segundo responde bien.
		FakeCatedra.stub(catedra, """
				{
					"scenarioName": "corte-breve",
					"requiredScenarioState": "Started",
					"newScenarioState": "recuperada",
					"request": { "method": "GET", "url": "%s" },
					"response": { "fault": "CONNECTION_RESET_BY_PEER" }
				}
				""".formatted(PATH));
		FakeCatedra.stub(catedra, """
				{
					"scenarioName": "corte-breve",
					"requiredScenarioState": "recuperada",
					"request": { "method": "GET", "url": "%s" },
					"response": { %s }
				}
				""".formatted(PATH, OK_RESPONSE));

		assertThat(read(client())).containsEntry("value", "ok");
		assertThat(requests()).isEqualTo(2);
	}

	@Test
	void failsWhenNothingAnswersAtTheCatedraAddressWithoutShowingIt() throws IOException {
		int closedPort;
		try (ServerSocket socket = new ServerSocket(0)) {
			closedPort = socket.getLocalPort();
		}
		String address = "localhost:" + closedPort;

		assertThatThrownBy(() -> read(client("http://" + address)))
				.isInstanceOf(CatedraRequestException.class)
				.hasMessageStartingWith("Catedra service unavailable")
				.hasMessageNotContaining(address)
				.hasNoCause();
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> read(RestClient client) {
		return executor.read(() -> client.get().uri(PATH).retrieve().body(Map.class));
	}

	private int requests() {
		return FakeCatedra.countRequests(catedra, "GET", PATH);
	}

	private RestClient client() {
		return client(FakeCatedra.baseUrl(catedra));
	}

	private static RestClient client(String baseUrl) {
		CatedraProperties properties = new CatedraProperties(baseUrl, FakeCatedra.TECHNICAL_TOKEN,
				Duration.ofSeconds(1), Duration.ofMillis(500));
		return new CatedraRestClientConfig().catedraRestClient(properties);
	}

	private static String respondingWith(String response) {
		return """
				{ "request": { "method": "GET", "url": "%s" }, "response": { %s } }
				""".formatted(PATH, response);
	}

}

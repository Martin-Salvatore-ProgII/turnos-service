package com.example.turnos.shared.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import com.example.turnos.FakeCatedra;
import com.example.turnos.shared.infrastructure.rest.CatedraReadExecutor;
import com.example.turnos.shared.infrastructure.rest.CatedraRequestException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

// Lo propio de la integración: qué campos lee, el JWT técnico y el estado PROVISIONED. El
// reintento y los mensajes de falla son los de toda lectura a la cátedra y se prueban en
// CatedraReadExecutorTest (ADR-0063). Sin Spring, contra una cátedra falsa (WireMock).
class CatedraIntegrationConfigTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final GenericContainer<?> catedra = FakeCatedra.newContainer();

	private final CatedraIntegrationConfig config = new CatedraIntegrationConfig();

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
	void loadsTheIntegrationWhenItIsProvisioned() {
		FakeCatedra.stub(catedra, integrationMapping().toString());

		CatedraIntegration integration = load();

		assertThat(integration.provisioningStatus()).isEqualTo("PROVISIONED");
		assertThat(integration.groupId()).isEqualTo("proyecto-de-prueba");
		assertThat(integration.kafkaBootstrapServers()).isEqualTo("kafka.catedra.internal:9092");
		assertThat(integration.kafkaConsumerGroupId()).isEqualTo("alumnos-proyecto-de-prueba");
		assertThat(integration.kafkaAppointmentActionsTopic()).isEqualTo("alumnos.turnos.acciones.proyecto-de-prueba");
		assertThat(integration.kafkaAppointmentPhoneTopic()).isEqualTo("catedra.turnos.telefono.proyecto-de-prueba");
	}

	@Test
	void sendsTheTechnicalTokenAsBearer() {
		// La cátedra falsa solo responde si llega el encabezado exacto; si no, contesta 404.
		ObjectNode mapping = integrationMapping();
		((ObjectNode) mapping.get("request")).putObject("headers")
				.putObject("Authorization")
				.put("equalTo", "Bearer " + FakeCatedra.TECHNICAL_TOKEN);
		FakeCatedra.stub(catedra, mapping.toString());

		assertThat(load().provisioningStatus()).isEqualTo("PROVISIONED");
	}

	@Test
	void failsWhenTheIntegrationIsNotProvisioned() {
		ObjectNode mapping = integrationMapping();
		((ObjectNode) mapping.get("response").get("jsonBody")).put("provisioningStatus", "PENDING");
		FakeCatedra.stub(catedra, mapping.toString());

		assertThatThrownBy(this::load)
				.isInstanceOf(CatedraIntegrationException.class)
				.hasMessage("Catedra integration is in state PENDING");
	}

	@Test
	void failsWithTheCommonMessageWhenTheCatedraFails() {
		FakeCatedra.stub(catedra, """
				{ "request": { "method": "GET", "url": "%s" }, "response": { "status": 401 } }
				""".formatted(CatedraIntegrationConfig.INTEGRATION_PATH));

		assertThatThrownBy(this::load)
				.isInstanceOf(CatedraRequestException.class)
				.hasMessage("The catedra rejected the technical JWT");
	}

	@Test
	void doesNotExposeSecretsWhenPrinted() {
		CatedraIntegration integration = new CatedraIntegration("grupo", "kafka-host:9092", "consumer-group",
				"actions-topic", "phone-topic", "PROVISIONED");
		CatedraProperties properties = new CatedraProperties("http://catedra-host:1234", "technical-jwt",
				Duration.ofSeconds(2), Duration.ofSeconds(5));

		assertThat(integration.toString())
				.doesNotContain("grupo", "kafka-host", "consumer-group", "actions-topic", "phone-topic");
		assertThat(properties.toString()).doesNotContain("technical-jwt", "catedra-host");
	}

	private CatedraIntegration load() {
		CatedraProperties properties = new CatedraProperties(FakeCatedra.baseUrl(catedra), FakeCatedra.TECHNICAL_TOKEN,
				Duration.ofSeconds(1), Duration.ofMillis(500));
		RestClient client = new CatedraRestClientConfig().catedraRestClient(properties);
		return config.catedraIntegration(client, new CatedraReadExecutor());
	}

	// La respuesta PROVISIONED de la REF §5.3, la misma que usa la aplicación completa en las pruebas.
	private static ObjectNode integrationMapping() {
		return (ObjectNode) JSON.readTree(FakeCatedra.readResource(FakeCatedra.INTEGRATION_MAPPING));
	}

}

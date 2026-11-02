package com.example.turnos;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Lo propio de la cátedra simulada (ADR-0044): el JWT técnico de las pruebas y la respuesta de la
 * integración y de las ocupaciones. El contenedor y la programación de respuestas son los de {@link FakeServer}.
 */
public final class FakeCatedra {

	public static final String TECHNICAL_TOKEN = "test-technical-token";

	/** Respuesta de {@code GET /api/student/integration} en estado PROVISIONED, con el formato de la REF §5.3. */
	public static final String INTEGRATION_MAPPING = "catedra/integration.json";

	/** El ejemplo de ocupaciones de la REF §8: una CONFIRMED y una HELD del profesional 15 el 2026-09-14. */
	public static final String OCCUPANCIES_REF = "catedra/occupancies-ref.json";

	/** Opción de WireMock para responder solo HTTP/1.1, como la cátedra real. */
	public static final String HTTP_1_ONLY = "--disable-http2-plain";

	/** La ruta de la confirmación inicial de un hold (REF §10). */
	public static String confirmUrl(String holdId) {
		return "/api/appointment-holds/" + holdId + "/confirm";
	}

	/** Respuesta a un POST con el status y el cuerpo dados (tal cual, como texto). */
	public static ObjectNode postMapping(String url, int status, String body) {
		ObjectNode mapping = JSON.createObjectNode();
		mapping.putObject("request").put("method", "POST").put("url", url);
		ObjectNode response = mapping.putObject("response").put("status", status);
		response.putObject("headers").put("Content-Type", status < 300 ? "application/json" : "application/problem+json");
		response.put("body", body);
		return mapping;
	}

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private FakeCatedra() {
	}

	/** La URL exacta del pedido de ocupaciones de un día, con los parámetros en el orden en que se envían. */
	public static String occupanciesUrl(long professionalId, String date) {
		return "/api/appointment-occupancies?professionalId=" + professionalId + "&from=" + date + "&to=" + date;
	}

	/** Respuesta de las ocupaciones de un día con el status y el cuerpo dados (tal cual, como texto). */
	public static ObjectNode occupanciesMapping(long professionalId, String date, int status, String body) {
		ObjectNode mapping = JSON.createObjectNode();
		mapping.putObject("request").put("method", "GET").put("url", occupanciesUrl(professionalId, date));
		ObjectNode response = mapping.putObject("response").put("status", status);
		response.putObject("headers").put("Content-Type", "application/json");
		response.put("body", body);
		return mapping;
	}

	/**
	 * Contenedor que ya arranca respondiendo como la cátedra real: la integración en estado PROVISIONED
	 * y solo HTTP/1.1. WireMock acepta pasar a HTTP/2 y la cátedra real no (probado con
	 * {@code curl --http2}); en ese modo, los POST del cliente de Java se cortan.
	 */
	public static GenericContainer<?> newContainerLikeTheCatedra() {
		return FakeServer.newContainer()
				.withCommand(HTTP_1_ONLY)
				.withCopyFileToContainer(MountableFile.forClasspathResource(INTEGRATION_MAPPING),
						FakeServer.MAPPINGS_DIR + "integration.json");
	}

	/**
	 * Lo mismo, pero con la integración anunciando el Kafka y el consumer group dados: para las pruebas
	 * que levantan la aplicación entera, donde el consumidor se conecta de verdad a esa dirección.
	 */
	public static GenericContainer<?> newContainerLikeTheCatedra(String kafkaBootstrapServers, String consumerGroupId) {
		return FakeServer.newContainer()
				.withCommand(HTTP_1_ONLY)
				.withCopyToContainer(Transferable.of(integrationMapping(kafkaBootstrapServers, consumerGroupId)),
						FakeServer.MAPPINGS_DIR + "integration.json");
	}

	/** La respuesta de la integración (resources/catedra/integration.json) con el Kafka y el grupo dados. */
	public static String integrationMapping(String kafkaBootstrapServers, String consumerGroupId) {
		try (InputStream resource = FakeCatedra.class.getClassLoader().getResourceAsStream(INTEGRATION_MAPPING)) {
			ObjectNode mapping = (ObjectNode) JSON.readTree(resource);
			ObjectNode integration = (ObjectNode) mapping.get("response").get("jsonBody");
			integration.put("kafkaBootstrapServers", kafkaBootstrapServers).put("kafkaConsumerGroupId", consumerGroupId);
			return mapping.toString();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}

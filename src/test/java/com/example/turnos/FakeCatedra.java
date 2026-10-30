package com.example.turnos;

import org.testcontainers.containers.GenericContainer;
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

	/** Contenedor que ya arranca respondiendo como la cátedra real: la integración en estado PROVISIONED. */
	public static GenericContainer<?> newContainerLikeTheCatedra() {
		return FakeServer.newContainer()
				.withCopyFileToContainer(MountableFile.forClasspathResource(INTEGRATION_MAPPING),
						FakeServer.MAPPINGS_DIR + "integration.json");
	}

}
